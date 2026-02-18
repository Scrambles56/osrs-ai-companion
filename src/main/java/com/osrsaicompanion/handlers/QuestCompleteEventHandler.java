package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;

import javax.swing.SwingUtilities;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class QuestCompleteEventHandler
{
	private final Client client;
	private final ClaudeClient claudeClient;
	private final PlayerContextBuilder contextBuilder;
	private final OsrsAiCompanionConfig config;
	private final Supplier<AiCompanionPanel> panelSupplier;

	private final Map<Quest, QuestState> questStateCache = new EnumMap<>(Quest.class);
	private volatile boolean cacheReady = false;
	private volatile boolean questVarbitChanged = false;
	private volatile boolean needsCacheInit = false;

	public void clearCache()
	{
		questStateCache.clear();
		cacheReady = false;
		questVarbitChanged = false;
		needsCacheInit = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			needsCacheInit = true;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			clearCache();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// Quest.getState() runs a client script and cannot be called inside a VarbitChanged
		// handler (scripts are not reentrant). Just flag that something changed and check on
		// the next game tick when it is safe to call getState().
		if (cacheReady)
		{
			questVarbitChanged = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (needsCacheInit)
		{
			needsCacheInit = false;
			for (Quest quest : Quest.values())
			{
				try
				{
					questStateCache.put(quest, quest.getState(client));
				}
				catch (Exception e)
				{
					log.debug("Could not get initial state for quest: {}", quest.getName());
				}
			}
			cacheReady = true;
			return;
		}

		if (!cacheReady || !questVarbitChanged)
		{
			return;
		}
		questVarbitChanged = false;

		for (Quest quest : Quest.values())
		{
			QuestState cachedState = questStateCache.get(quest);
			if (cachedState == QuestState.FINISHED)
			{
				continue;
			}

			QuestState currentState;
			try
			{
				currentState = quest.getState(client);
			}
			catch (Exception e)
			{
				continue;
			}

			if (currentState == QuestState.FINISHED)
			{
				questStateCache.put(quest, QuestState.FINISHED);

				if (!config.celebrateQuestCompletions() || claudeClient.apiCallInProgress)
				{
					continue;
				}

				String prompt = "I just completed the quest \"" + quest.getName() + "\"! "
					+ "Please congratulate me, and based on your knowledge of OSRS, "
					+ "mention any notable quests this now unlocks as a prerequisite.";

				AiCompanionPanel panel = panelSupplier.get();
				String playerName = contextBuilder.getPlayerName();
				SwingUtilities.invokeLater(() -> {
					if (panel != null)
					{
						panel.appendEventMessage(playerName + " completed " + quest.getName());
					}
				});
				claudeClient.sendMessage(prompt, panel);
				return;
			}
			else if (currentState != cachedState)
			{
				questStateCache.put(quest, currentState);
			}
		}
	}
}
