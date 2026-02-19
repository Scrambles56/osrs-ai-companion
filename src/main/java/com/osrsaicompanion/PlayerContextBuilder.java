package com.osrsaicompanion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class PlayerContextBuilder
{
	private final Client client;
	private final ItemManager itemManager;
	private final OsrsAiCompanionConfig config;

	public String buildSlowSystemPrompt()
	{
		String playerName = getPlayerName();
		Player localPlayer = client.getLocalPlayer();
		int combatLevel = localPlayer != null ? localPlayer.getCombatLevel() : 0;

		int totalLevel = 0;
		long totalXp = 0;
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			totalLevel += client.getRealSkillLevel(skill);
			totalXp += client.getSkillExperience(skill);
		}

		StringBuilder sb = new StringBuilder();
		sb.append("You are Claude, an AI assistant responding to ").append(playerName).append(" in Old School RuneScape. ");

		CompanionTone tone = config.companionTone();
		if (tone != null && tone.getSystemPrompt() != null)
		{
			sb.append(tone.getSystemPrompt()).append(" ");
		}

		sb.append("Combat level: ").append(combatLevel).append(". ");
		sb.append("Total level: ").append(totalLevel).append(". ");
		sb.append("Total XP: ").append(String.format("%,d", totalXp)).append(". ");
		sb.append("Skill breakdown: ").append(getSkillBreakdown()).append(". ");
		sb.append("Completed quests: ").append(emptyOr(getQuestsByState(QuestState.FINISHED), "None")).append(". ");
		sb.append("Quests in progress: ").append(emptyOr(getQuestsByState(QuestState.IN_PROGRESS), "None")).append(". ");
		sb.append("Achievement diaries: ").append(emptyOr(getAchievementDiaryStatus(), "None completed")).append(". ");

		String goal = config.playerGoal();
		if (goal != null && !goal.trim().isEmpty())
		{
			sb.append("Player's current goal: ").append(goal.trim()).append(". ");
		}

		sb.append("Be concise, helpful, and friendly. Do not use emojis. ");
		sb.append("When you need to call the same tool multiple times for different inputs (e.g. pricing several items), call ALL of them in parallel in a single response — never call them one at a time across multiple rounds. ");
		sb.append("If a get_ge_price tool call returns a not-found or not-tradeable result, accept it immediately and do not retry that item — answer the player using whatever prices you did receive. ");
		sb.append("Each message is prefixed with a timestamp tag like <t:2026-01-01T12:00:00Z>. ");
		sb.append("Use these timestamps internally to be temporally aware — notice how long ago the player last levelled up, how quickly they are progressing, or how long they have been playing. ");
		sb.append("IMPORTANT: Never include timestamp tags or any date/time strings in your replies. Never echo back anything inside <t:...> tags. ");
		sb.append("If you reference time, use only natural language like 'about 20 minutes ago', 'just now', or 'earlier this session'. ");
		sb.append("When the player levels up, keep your congratulation brief and make it personal by referencing their current situation — ");
		sb.append("such as their HP, location, what they're doing, or their goal — rather than giving a generic response.");

		return sb.toString();
	}

	public String buildFastSystemPrompt()
	{
		int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
		int currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
		int maxPrayer = client.getRealSkillLevel(Skill.PRAYER);
		double runEnergy = client.getEnergy() / 100.0;

		StringBuilder sb = new StringBuilder();
		sb.append("Current live state: ");
		sb.append("HP: ").append(currentHp).append("/").append(maxHp).append(". ");
		sb.append("Prayer: ").append(currentPrayer).append("/").append(maxPrayer).append(". ");
		sb.append("Run energy: ").append(String.format("%.1f", runEnergy)).append("%. ");
		sb.append("Coins: ").append(String.format("%,d", getCoins())).append(" gp. ");
		sb.append("Inventory: ").append(emptyOr(getInventoryItems(), "Empty")).append(". ");
		sb.append("Equipped: ").append(emptyOr(getEquippedItems(), "Nothing")).append(". ");
		sb.append("Location: ").append(getPlayerLocation()).append(". ");
		sb.append("Slayer task: ").append(getSlayerTask()).append(". ");
		sb.append("Bank: ").append(getBankContents()).append(".");

		return sb.toString();
	}

	private long getCoins()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null)
		{
			return 0;
		}
		for (Item item : inventory.getItems())
		{
			if (item.getId() == 995) // Coins
			{
				return item.getQuantity();
			}
		}
		return 0;
	}

	public String formatSkillName(String name)
	{
		if (name == null || name.isEmpty())
		{
			return name;
		}
		return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
	}

	public String getPlayerName()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && localPlayer.getName() != null)
		{
			return localPlayer.getName();
		}
		return "a player";
	}

	private String getSkillBreakdown()
	{
		Skill[] skills = {
			Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.PRAYER,
			Skill.MAGIC, Skill.RUNECRAFT, Skill.HITPOINTS, Skill.CRAFTING, Skill.MINING,
			Skill.SMITHING, Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING,
			Skill.AGILITY, Skill.HERBLORE, Skill.THIEVING, Skill.FLETCHING, Skill.SLAYER,
			Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING
		};

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < skills.length; i++)
		{
			Skill skill = skills[i];
			sb.append(formatSkillName(skill.getName()))
				.append(" - ").append(client.getRealSkillLevel(skill))
				.append(" (").append(String.format("%,d", client.getSkillExperience(skill))).append(" XP)");
			if (i < skills.length - 1)
			{
				sb.append(", ");
			}
		}
		return sb.toString();
	}

	private String getQuestsByState(QuestState targetState)
	{
		List<String> quests = new ArrayList<>();
		for (Quest quest : Quest.values())
		{
			try
			{
				if (quest.getState(client) == targetState)
				{
					quests.add(quest.getName());
				}
			}
			catch (Exception e)
			{
				log.debug("Could not get state for quest: {}", quest.getName());
			}
		}
		return String.join(", ", quests);
	}

	private String getInventoryItems()
	{
		return itemsFromContainer(InventoryID.INVENTORY, Integer.MAX_VALUE);
	}

	private String getEquippedItems()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return "";
		}
		List<String> items = new ArrayList<>();
		for (Item item : equipment.getItems())
		{
			if (item.getId() != -1 && item.getQuantity() > 0)
			{
				String name = getItemName(item.getId());
				if (name != null && !name.isEmpty())
				{
					items.add(name);
				}
			}
		}
		return String.join(", ", items);
	}

	private String getBankContents()
	{
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null)
		{
			return "Bank not open";
		}
		String contents = itemsFromContainer(InventoryID.BANK, 50);
		return contents.isEmpty() ? "Empty" : contents;
	}

	private String itemsFromContainer(InventoryID id, int limit)
	{
		ItemContainer container = client.getItemContainer(id);
		if (container == null)
		{
			return "";
		}
		List<String> items = new ArrayList<>();
		int count = 0;
		for (Item item : container.getItems())
		{
			if (count >= limit)
			{
				break;
			}
			if (item.getId() != -1 && item.getQuantity() > 0)
			{
				String name = getItemName(item.getId());
				if (name != null && !name.isEmpty())
				{
					items.add(item.getQuantity() > 1 ? name + " x" + item.getQuantity() : name);
					count++;
				}
			}
		}
		return String.join(", ", items);
	}

	private String getItemName(int itemId)
	{
		try
		{
			ItemComposition composition = itemManager.getItemComposition(itemId);
			if (composition != null)
			{
				return composition.getName();
			}
		}
		catch (Exception e)
		{
			log.debug("Could not get name for item ID: {}", itemId);
		}
		return null;
	}

	private String getPlayerLocation()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return "Unknown";
		}
		WorldPoint location = localPlayer.getWorldLocation();
		if (location == null)
		{
			return "Unknown";
		}
		int regionId = location.getRegionID();
		String areaName = getAreaName(regionId);
		String coords = "(" + location.getX() + ", " + location.getY() + ", " + location.getPlane() + ")";
		return areaName != null ? areaName + " " + coords : "Region " + regionId + " " + coords;
	}

	private String getSlayerTask()
	{
		int taskSize = client.getVarpValue(VarPlayer.SLAYER_TASK_SIZE);
		return taskSize > 0 ? taskSize + " remaining" : "No slayer task";
	}

	private String getAchievementDiaryStatus()
	{
		StringBuilder sb = new StringBuilder();
		appendDiary(sb, "Ardougne", Varbits.DIARY_ARDOUGNE_EASY, Varbits.DIARY_ARDOUGNE_MEDIUM, Varbits.DIARY_ARDOUGNE_HARD, Varbits.DIARY_ARDOUGNE_ELITE);
		appendDiary(sb, "Desert", Varbits.DIARY_DESERT_EASY, Varbits.DIARY_DESERT_MEDIUM, Varbits.DIARY_DESERT_HARD, Varbits.DIARY_DESERT_ELITE);
		appendDiary(sb, "Falador", Varbits.DIARY_FALADOR_EASY, Varbits.DIARY_FALADOR_MEDIUM, Varbits.DIARY_FALADOR_HARD, Varbits.DIARY_FALADOR_ELITE);
		appendDiary(sb, "Fremennik", Varbits.DIARY_FREMENNIK_EASY, Varbits.DIARY_FREMENNIK_MEDIUM, Varbits.DIARY_FREMENNIK_HARD, Varbits.DIARY_FREMENNIK_ELITE);
		appendDiary(sb, "Kandarin", Varbits.DIARY_KANDARIN_EASY, Varbits.DIARY_KANDARIN_MEDIUM, Varbits.DIARY_KANDARIN_HARD, Varbits.DIARY_KANDARIN_ELITE);
		appendDiary(sb, "Karamja", Varbits.DIARY_KARAMJA_EASY, Varbits.DIARY_KARAMJA_MEDIUM, Varbits.DIARY_KARAMJA_HARD, Varbits.DIARY_KARAMJA_ELITE);
		appendDiary(sb, "Kourend", Varbits.DIARY_KOUREND_EASY, Varbits.DIARY_KOUREND_MEDIUM, Varbits.DIARY_KOUREND_HARD, Varbits.DIARY_KOUREND_ELITE);
		appendDiary(sb, "Lumbridge", Varbits.DIARY_LUMBRIDGE_EASY, Varbits.DIARY_LUMBRIDGE_MEDIUM, Varbits.DIARY_LUMBRIDGE_HARD, Varbits.DIARY_LUMBRIDGE_ELITE);
		appendDiary(sb, "Morytania", Varbits.DIARY_MORYTANIA_EASY, Varbits.DIARY_MORYTANIA_MEDIUM, Varbits.DIARY_MORYTANIA_HARD, Varbits.DIARY_MORYTANIA_ELITE);
		appendDiary(sb, "Varrock", Varbits.DIARY_VARROCK_EASY, Varbits.DIARY_VARROCK_MEDIUM, Varbits.DIARY_VARROCK_HARD, Varbits.DIARY_VARROCK_ELITE);
		appendDiary(sb, "Western", Varbits.DIARY_WESTERN_EASY, Varbits.DIARY_WESTERN_MEDIUM, Varbits.DIARY_WESTERN_HARD, Varbits.DIARY_WESTERN_ELITE);
		appendDiary(sb, "Wilderness", Varbits.DIARY_WILDERNESS_EASY, Varbits.DIARY_WILDERNESS_MEDIUM, Varbits.DIARY_WILDERNESS_HARD, Varbits.DIARY_WILDERNESS_ELITE);
		return sb.toString();
	}

	private void appendDiary(StringBuilder sb, String name, int easy, int medium, int hard, int elite)
	{
		String tier = null;
		if (client.getVarbitValue(elite) == 1) tier = "Elite";
		else if (client.getVarbitValue(hard) == 1) tier = "Hard";
		else if (client.getVarbitValue(medium) == 1) tier = "Medium";
		else if (client.getVarbitValue(easy) == 1) tier = "Easy";

		if (tier != null)
		{
			if (sb.length() > 0) sb.append(", ");
			sb.append(name).append(": ").append(tier);
		}
	}


	private String getAreaName(int regionId)
	{
		Map<Integer, String> regionNames = new HashMap<>();

		// Major Cities
		regionNames.put(12850, "Lumbridge"); regionNames.put(12851, "Lumbridge");
		regionNames.put(12593, "Lumbridge Swamp"); regionNames.put(12849, "Lumbridge");
		regionNames.put(12853, "Varrock"); regionNames.put(12854, "Varrock");
		regionNames.put(12597, "Varrock"); regionNames.put(12598, "Varrock"); regionNames.put(12852, "Varrock");
		regionNames.put(11828, "Falador"); regionNames.put(11827, "Falador");
		regionNames.put(11829, "Falador"); regionNames.put(12084, "Falador");
		regionNames.put(10806, "Camelot"); regionNames.put(10807, "Camelot");
		regionNames.put(10805, "Seers' Village");
		regionNames.put(10549, "Catherby"); regionNames.put(10548, "Catherby");
		regionNames.put(10804, "Ranging Guild");
		regionNames.put(11310, "Ardougne"); regionNames.put(10547, "Ardougne");
		regionNames.put(10290, "Ardougne"); regionNames.put(10291, "Ardougne");
		regionNames.put(10292, "Yanille"); regionNames.put(10032, "Yanille");
		regionNames.put(11571, "Burthorpe"); regionNames.put(11319, "Taverley");
		regionNames.put(11575, "Warriors' Guild");

		// Wilderness & PvP
		regionNames.put(12342, "Edgeville"); regionNames.put(12341, "Edgeville");
		regionNames.put(12343, "Barbarian Village");
		regionNames.put(12088, "Draynor Village"); regionNames.put(12338, "Draynor Village");
		regionNames.put(12089, "Draynor Manor");
		regionNames.put(12603, "Wilderness"); regionNames.put(12604, "Wilderness");
		regionNames.put(12605, "Wilderness"); regionNames.put(12859, "Wilderness");
		regionNames.put(12860, "Wilderness"); regionNames.put(13115, "Wilderness");
		regionNames.put(13116, "Wilderness"); regionNames.put(13117, "Wilderness");
		regionNames.put(13371, "Wilderness"); regionNames.put(13372, "Wilderness");
		regionNames.put(13373, "Wilderness");

		// Desert & Al Kharid
		regionNames.put(13105, "Al Kharid"); regionNames.put(13106, "Al Kharid");
		regionNames.put(13361, "Shantay Pass"); regionNames.put(13107, "Duel Arena");
		regionNames.put(12848, "Al Kharid Mine");

		// Gnome Areas
		regionNames.put(9781, "Tree Gnome Stronghold"); regionNames.put(9782, "Tree Gnome Stronghold");
		regionNames.put(9525, "Tree Gnome Village");

		// Morytania
		regionNames.put(13622, "Canifis"); regionNames.put(13878, "Port Phasmatys");
		regionNames.put(14646, "Barrows"); regionNames.put(14647, "Barrows");
		regionNames.put(13875, "Slayer Tower");

		// Kandarin
		regionNames.put(10293, "Fight Caves"); regionNames.put(11062, "Fishing Guild");
		regionNames.put(10294, "Port Khazard");

		// Fremennik
		regionNames.put(10553, "Rellekka"); regionNames.put(10297, "Rellekka");
		regionNames.put(9275, "Waterbirth Island");

		// Zeah (Kourend)
		regionNames.put(6457, "Hosidius"); regionNames.put(6713, "Hosidius");
		regionNames.put(6972, "Shayzien"); regionNames.put(7228, "Lovakengj");
		regionNames.put(6459, "Port Piscarilius"); regionNames.put(6715, "Port Piscarilius");
		regionNames.put(6971, "Arceuus"); regionNames.put(7227, "Arceuus");

		// Dungeons
		regionNames.put(12698, "Stronghold of Security"); regionNames.put(12954, "Stronghold of Security");
		regionNames.put(11416, "Taverley Dungeon"); regionNames.put(11417, "Taverley Dungeon");
		regionNames.put(11672, "Taverley Dungeon"); regionNames.put(11673, "Taverley Dungeon");
		regionNames.put(12693, "Edgeville Dungeon");
		regionNames.put(10388, "Brimhaven Dungeon"); regionNames.put(10644, "Brimhaven Dungeon");

		// Islands
		regionNames.put(11057, "Entrana");
		regionNames.put(10537, "Karamja"); regionNames.put(10536, "Karamja");
		regionNames.put(11054, "Crandor"); regionNames.put(14638, "Mos Le'Harmless");

		// Banks
		regionNames.put(12596, "Grand Exchange");

		// Tutorial Island
		regionNames.put(12336, "Tutorial Island"); regionNames.put(12335, "Tutorial Island");
		regionNames.put(12592, "Tutorial Island");

		return regionNames.get(regionId);
	}

	private static String emptyOr(String value, String fallback)
	{
		return value.isEmpty() ? fallback : value;
	}
}
