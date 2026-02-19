package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LevelUpEventHandlerTest
{
	private LevelUpEventHandler handler;
	private ClaudeClient claudeClient;
	private OsrsAiCompanionConfig config;
	private PlayerContextBuilder contextBuilder;

	@Before
	public void setUp()
	{
		claudeClient = mock(ClaudeClient.class);
		config = mock(OsrsAiCompanionConfig.class);
		contextBuilder = mock(PlayerContextBuilder.class);
		when(config.celebrateLevelUps()).thenReturn(true);
		when(config.celebrateXpMilestones()).thenReturn(true);
		when(contextBuilder.formatSkillName(any())).thenReturn("Attack");
		when(contextBuilder.getPlayerName()).thenReturn("Scrambles56");

		handler = new LevelUpEventHandler(claudeClient, contextBuilder, config, () -> null);
	}

	@Test
	public void testNoMessageSentDuringCacheInit()
	{
		// Simulate LOGGED_IN — sets needsCacheInit flag
		handler.onGameStateChanged(gameStateChanged(GameState.LOGGED_IN));

		// Send StatChanged events for all skills — these should be absorbed silently
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL) continue;
			handler.onStatChanged(statChanged(skill, 70, 737_627));
		}

		// No messages should have been sent during cache init
		verify(claudeClient, never()).sendMessage(contains("levelled up"), any());
	}

	@Test
	public void testLevelUpAfterCacheReady()
	{
		simulateLogin(Skill.ATTACK, 69);

		// Now level up Attack to 70
		handler.onStatChanged(statChanged(Skill.ATTACK, 70, 737_627));

		verify(claudeClient, times(1)).sendMessage(contains("Attack"), any());
	}

	@Test
	public void testNoLevelUpMessageWhenLevelUnchanged()
	{
		simulateLogin(Skill.ATTACK, 70);

		// Same level — XP gain only, no level change
		handler.onStatChanged(statChanged(Skill.ATTACK, 70, 800_000));

		verify(claudeClient, never()).sendMessage(contains("levelled up"), any());
	}

	@Test
	public void testXpMilestoneAt50m()
	{
		simulateLogin(Skill.ATTACK, 99, 49_000_000L);

		// Cross the 50m milestone
		handler.onStatChanged(statChanged(Skill.ATTACK, 99, 51_000_000L));

		verify(claudeClient, times(1)).sendMessage(contains("50m"), any());
	}

	@Test
	public void testNoXpMilestoneBelow50m()
	{
		simulateLogin(Skill.ATTACK, 99, 10_000_000L);

		// XP gain but not crossing 50m — only the welcome message should have been sent (during login)
		reset(claudeClient);
		handler.onStatChanged(statChanged(Skill.ATTACK, 99, 20_000_000L));

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testCacheClearedOnLoginScreen()
	{
		simulateLogin(Skill.ATTACK, 70);

		// Logout
		handler.onGameStateChanged(gameStateChanged(GameState.LOGIN_SCREEN));

		// Login again — should need re-init, no immediate celebrations
		handler.onGameStateChanged(gameStateChanged(GameState.LOGGED_IN));
		handler.onStatChanged(statChanged(Skill.ATTACK, 71, 900_000));

		// Still in init phase — no celebration
		verify(claudeClient, never()).sendMessage(contains("levelled up"), any());
	}

	@Test
	public void testNoMessageWhenApiCallInProgress()
	{
		// Use a separate handler with a real ClaudeClient stub that has apiCallInProgress=true
		ClaudeClient busyClient = new BusyClaudeClient();
		LevelUpEventHandler busyHandler = new LevelUpEventHandler(busyClient, contextBuilder, config, () -> null);

		simulateLoginWith(busyHandler, Skill.ATTACK, 69);
		busyHandler.onStatChanged(statChanged(Skill.ATTACK, 70, 737_627));

		// No message should have been sent beyond the welcome message
		assertEquals("Should have sent only the welcome message", 1, ((BusyClaudeClient) busyClient).messageCount);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private void simulateLogin(Skill watchedSkill, int level)
	{
		simulateLogin(watchedSkill, level, 737_627L);
	}

	private void simulateLogin(Skill watchedSkill, int level, long xp)
	{
		simulateLoginWith(handler, watchedSkill, level, xp);
	}

	private void simulateLoginWith(LevelUpEventHandler h, Skill watchedSkill, int level)
	{
		simulateLoginWith(h, watchedSkill, level, 737_627L);
	}

	private void simulateLoginWith(LevelUpEventHandler h, Skill watchedSkill, int level, long xp)
	{
		h.onGameStateChanged(gameStateChanged(GameState.LOGGED_IN));
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL) continue;
			int lvl = skill == watchedSkill ? level : 1;
			long skillXp = skill == watchedSkill ? xp : 0L;
			h.onStatChanged(statChanged(skill, lvl, skillXp));
		}
	}

	/** Stub ClaudeClient that always reports apiCallInProgress=true after the welcome message */
	private static class BusyClaudeClient extends ClaudeClient
	{
		int messageCount = 0;

		BusyClaudeClient()
		{
			super(null, new com.google.gson.Gson(), null, null, null, null);
			apiCallInProgress = true;
		}

		@Override
		public void sendMessage(String prompt, AiCompanionPanel panel)
		{
			messageCount++;
			// First call is the welcome message — allow it and keep busy
		}
	}

	private static GameStateChanged gameStateChanged(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}

	private static StatChanged statChanged(Skill skill, int level, long xp)
	{
		return new StatChanged(skill, (int) xp, level, level);
	}
}
