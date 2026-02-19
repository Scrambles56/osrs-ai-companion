package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;

import javax.swing.SwingUtilities;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class LevelUpEventHandler
{
	private static final int MAX_LEVEL = 99;
	private static final long XP_MILESTONE_INTERVAL = 50_000_000L;

	private final ClaudeClient claudeClient;
	private final PlayerContextBuilder contextBuilder;
	private final OsrsAiCompanionConfig config;
	private final Supplier<AiCompanionPanel> panelSupplier;

	private final Map<Skill, Integer> skillLevelCache = new EnumMap<>(Skill.class);
	private final Map<Skill, Long> skillXpCache = new EnumMap<>(Skill.class);
	// cacheReady is set only after all skills have been seen via StatChanged on login,
	// so we never compare against a stale getRealSkillLevel() value from LOGGED_IN.
	private volatile boolean cacheReady = false;
	private volatile boolean needsCacheInit = false;
	private volatile boolean welcomeSent = false;

	private void sendWelcomeMessage()
	{
		AiCompanionPanel panel = panelSupplier.get();
		claudeClient.sendMessage("I just logged in. Welcome me back and ask what I want to work on today.", panel);
	}

	public void clearCache()
	{
		skillLevelCache.clear();
		skillXpCache.clear();
		cacheReady = false;
		needsCacheInit = false;
		welcomeSent = false;
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
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		if (skill == Skill.OVERALL)
		{
			return;
		}

		int newLevel = event.getLevel();
		long newXp = event.getXp();

		if (needsCacheInit)
		{
			// Absorb the initial flood of StatChanged events on login into the cache
			// without triggering any celebrations. Once all skills are seen, mark ready.
			skillLevelCache.put(skill, newLevel);
			skillXpCache.put(skill, newXp);
			if (skillLevelCache.size() >= Skill.values().length - 1) // -1 for OVERALL
			{
				needsCacheInit = false;
				cacheReady = true;
				if (!welcomeSent)
				{
					welcomeSent = true;
					sendWelcomeMessage();
				}
			}
			return;
		}

		if (!cacheReady)
		{
			return;
		}

		Integer cachedLevel = skillLevelCache.get(skill);
		Long cachedXp = skillXpCache.get(skill);

		skillLevelCache.put(skill, newLevel);
		skillXpCache.put(skill, newXp);

		if (cachedLevel != null && newLevel > cachedLevel)
		{
			if (!config.celebrateLevelUps() || claudeClient.apiCallInProgress)
			{
				return;
			}

			String skillName = contextBuilder.formatSkillName(skill.getName());
			String prompt = "I just levelled up " + skillName + " to " + newLevel + "! Please congratulate me.";
			AiCompanionPanel panel = panelSupplier.get();
			String playerName = contextBuilder.getPlayerName();

			SwingUtilities.invokeLater(() -> {
				if (panel != null)
				{
					panel.appendEventMessage(playerName + " achieved level " + newLevel + " " + skillName);
				}
			});

			claudeClient.sendMessage(prompt, panel);
		}
		else if (newLevel == MAX_LEVEL && cachedXp != null && newXp > cachedXp)
		{
			// Check for 50m XP milestones post-99
			long previousMilestone = (cachedXp / XP_MILESTONE_INTERVAL) * XP_MILESTONE_INTERVAL;
			long newMilestone = (newXp / XP_MILESTONE_INTERVAL) * XP_MILESTONE_INTERVAL;

			if (newMilestone > previousMilestone && newMilestone > 0)
			{
				if (!config.celebrateXpMilestones() || claudeClient.apiCallInProgress)
				{
					return;
				}

				long milestoneMillions = newMilestone / 1_000_000;
				String skillName = contextBuilder.formatSkillName(skill.getName());
				String prompt = "I just hit " + milestoneMillions + "m XP in " + skillName
					+ "! Please congratulate me on this milestone.";
				AiCompanionPanel panel = panelSupplier.get();
				String playerName = contextBuilder.getPlayerName();

				SwingUtilities.invokeLater(() -> {
					if (panel != null)
					{
						panel.appendEventMessage(playerName + " reached " + milestoneMillions + "m " + skillName + " XP");
					}
				});

				claudeClient.sendMessage(prompt, panel);
			}
		}
	}
}
