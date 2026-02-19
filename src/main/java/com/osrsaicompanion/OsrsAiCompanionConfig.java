package com.osrsaicompanion;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("osrsaicompanion")
public interface OsrsAiCompanionConfig extends Config
{
	// -------------------------------------------------------------------------
	// API Settings
	// -------------------------------------------------------------------------

	@ConfigSection(
		name = "API Settings",
		description = "Claude API configuration",
		position = 0
	)
	String apiSection = "api";

	@ConfigItem(
		keyName = "apiKey",
		name = "Claude API Key",
		description = "Your Anthropic API key for Claude",
		secret = true,
		position = 1,
		section = apiSection
	)
	default String apiKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "model",
		name = "Model",
		description = "Which Claude model to use",
		position = 2,
		section = apiSection
	)
	default AiModel model()
	{
		return AiModel.HAIKU;
	}

	@ConfigItem(
		keyName = "maxTokens",
		name = "Max Tokens",
		description = "Maximum number of tokens in Claude's response",
		position = 3,
		section = apiSection
	)
	@Range(min = 256, max = 4096)
	default int maxTokens()
	{
		return 1024;
	}

	@ConfigItem(
		keyName = "companionTone",
		name = "Companion Tone",
		description = "The personality Claude adopts when responding",
		position = 4,
		section = apiSection
	)
	default CompanionTone companionTone()
	{
		return CompanionTone.NONE;
	}

	@ConfigItem(
		keyName = "playerGoal",
		name = "Player Goal",
		description = "Your current OSRS goal — Claude will always keep this in mind",
		position = 5,
		section = apiSection
	)
	default String playerGoal()
	{
		return "";
	}

	// -------------------------------------------------------------------------
	// Event Toggles
	// -------------------------------------------------------------------------

	@ConfigSection(
		name = "Event Celebrations",
		description = "Choose which in-game events Claude reacts to",
		position = 10
	)
	String eventsSection = "events";

	@ConfigItem(
		keyName = "celebrateLevelUps",
		name = "Level-ups",
		description = "Claude reacts when you gain a level",
		position = 11,
		section = eventsSection
	)
	default boolean celebrateLevelUps()
	{
		return true;
	}

	@ConfigItem(
		keyName = "celebrateXpMilestones",
		name = "XP milestones (post-99)",
		description = "Claude reacts every 50m XP after reaching level 99",
		position = 12,
		section = eventsSection
	)
	default boolean celebrateXpMilestones()
	{
		return true;
	}

	@ConfigItem(
		keyName = "celebrateQuestCompletions",
		name = "Quest completions",
		description = "Claude reacts when you complete a quest",
		position = 13,
		section = eventsSection
	)
	default boolean celebrateQuestCompletions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "celebrateDiaryCompletions",
		name = "Diary completions",
		description = "Claude reacts when you complete an achievement diary tier",
		position = 14,
		section = eventsSection
	)
	default boolean celebrateDiaryCompletions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "celebrateBossKills",
		name = "Boss kill milestones",
		description = "Claude reacts at boss kill count milestones (1, 50, 100, 250, ...)",
		position = 15,
		section = eventsSection
	)
	default boolean celebrateBossKills()
	{
		return true;
	}

	@ConfigItem(
		keyName = "celebrateCollectionLog",
		name = "Collection log entries",
		description = "Claude reacts when you add a new item to your collection log",
		position = 16,
		section = eventsSection
	)
	default boolean celebrateCollectionLog()
	{
		return true;
	}

	@ConfigItem(
		keyName = "commiserateDeath",
		name = "Deaths",
		description = "Claude commiserates when you die",
		position = 17,
		section = eventsSection
	)
	default boolean commiserateDeath()
	{
		return true;
	}

	// -------------------------------------------------------------------------
	// Loot Settings
	// -------------------------------------------------------------------------

	@ConfigSection(
		name = "Loot Alerts",
		description = "Configure valuable drop reactions",
		position = 20
	)
	String lootSection = "loot";

	@ConfigItem(
		keyName = "celebrateLoot",
		name = "Valuable drops",
		description = "Claude reacts when you receive a valuable drop",
		position = 21,
		section = lootSection
	)
	default boolean celebrateLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lootValueThreshold",
		name = "Loot alert threshold (gp)",
		description = "Minimum total drop value to trigger a Claude reaction",
		position = 22,
		section = lootSection
	)
	@Range(min = 0, max = 10_000_000)
	default int lootValueThreshold()
	{
		return 100_000;
	}
}
