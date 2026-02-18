package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class LootDropEventHandler
{
	private static final int LOOT_COLLECTION_TICKS = 3;

	private final Client client;
	private final ItemManager itemManager;
	private final ClaudeClient claudeClient;
	private final PlayerContextBuilder contextBuilder;
	private final OsrsAiCompanionConfig config;
	private final Supplier<AiCompanionPanel> panelSupplier;

	// Tracks the most recently killed NPC and loot collection state
	private String pendingNpcName;
	private WorldPoint pendingNpcLocation;
	private int ticksRemaining;
	private final List<LootItem> pendingLoot = new ArrayList<>();

	private static class LootItem
	{
		final int id;
		final int quantity;
		final long totalValue;
		final String name;

		LootItem(int id, int quantity, long totalValue, String name)
		{
			this.id = id;
			this.quantity = quantity;
			this.totalValue = totalValue;
			this.name = name;
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) actor;
		pendingNpcName = npc.getName();
		pendingNpcLocation = npc.getWorldLocation();
		pendingLoot.clear();
		ticksRemaining = LOOT_COLLECTION_TICKS;
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		if (pendingNpcName == null || ticksRemaining <= 0)
		{
			return;
		}

		TileItem item = event.getItem();
		if (item.getOwnership() != TileItem.OWNERSHIP_SELF)
		{
			return;
		}

		// Only collect items that spawned near the NPC's death location
		WorldPoint itemLocation = WorldPoint.fromLocal(client, event.getTile().getLocalLocation());
		if (pendingNpcLocation != null && itemLocation.distanceTo(pendingNpcLocation) > 5)
		{
			return;
		}

		int itemId = item.getId();
		int quantity = item.getQuantity();
		int priceEach;
		try
		{
			priceEach = itemManager.getItemPrice(itemId);
		}
		catch (Exception e)
		{
			return;
		}

		long totalValue = (long) priceEach * quantity;
		if (totalValue <= 0)
		{
			return;
		}

		String name;
		try
		{
			name = itemManager.getItemComposition(itemId).getName();
		}
		catch (Exception e)
		{
			return;
		}

		pendingLoot.add(new LootItem(itemId, quantity, totalValue, name));
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (pendingNpcName == null || ticksRemaining <= 0)
		{
			return;
		}

		ticksRemaining--;
		if (ticksRemaining > 0)
		{
			return;
		}

		// Ticks elapsed — evaluate the loot
		String npcName = pendingNpcName;
		List<LootItem> loot = new ArrayList<>(pendingLoot);
		pendingNpcName = null;
		pendingLoot.clear();

		long totalValue = loot.stream().mapToLong(l -> l.totalValue).sum();
		int threshold = config.lootValueThreshold();

		if (totalValue < threshold)
		{
			return;
		}

		if (!config.celebrateLoot() || claudeClient.apiCallInProgress)
		{
			return;
		}

		StringBuilder itemList = new StringBuilder();
		for (int i = 0; i < loot.size(); i++)
		{
			LootItem l = loot.get(i);
			itemList.append(l.quantity > 1 ? l.quantity + "x " : "").append(l.name)
				.append(" (").append(String.format("%,d", l.totalValue)).append(" gp)");
			if (i < loot.size() - 1)
			{
				itemList.append(", ");
			}
		}

		String prompt = "I just killed " + npcName + " and got a notable drop: " + itemList
			+ ". Total value: " + String.format("%,d", totalValue) + " gp. React to this drop!";

		String playerName = contextBuilder.getPlayerName();
		String eventText = playerName + " received a drop from " + npcName
			+ ": " + String.format("%,d", totalValue) + " gp";

		AiCompanionPanel panel = panelSupplier.get();
		SwingUtilities.invokeLater(() -> {
			if (panel != null)
			{
				panel.appendEventMessage(eventText);
			}
		});

		claudeClient.sendMessage(prompt, panel);
	}
}
