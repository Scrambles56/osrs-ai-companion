package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class QuestCompleteEventHandlerTest
{
	private QuestCompleteEventHandler handler;
	private ClaudeClient claudeClient;
	private OsrsAiCompanionConfig config;
	private PlayerContextBuilder contextBuilder;
	private Client client;

	@Before
	public void setUp()
	{
		claudeClient = mock(ClaudeClient.class);
		config = mock(OsrsAiCompanionConfig.class);
		contextBuilder = mock(PlayerContextBuilder.class);
		client = mock(Client.class);
		when(config.celebrateQuestCompletions()).thenReturn(true);
		when(contextBuilder.getPlayerName()).thenReturn("Scrambles56");

		handler = new QuestCompleteEventHandler(client, claudeClient, contextBuilder, config, () -> null);
	}

	@Test
	public void testVarbitChangedDoesNotSetReadyFlag()
	{
		// Before login, varbitChanged should stay false when onVarbitChanged fires
		handler.onVarbitChanged(new VarbitChanged());

		assertFalse("questVarbitChanged should not be set before cache is ready", getVarbitChangedFlag());
	}

	@Test
	public void testVarbitChangedSetsFlagWhenCacheReady() throws Exception
	{
		simulateLoginAndTick();

		handler.onVarbitChanged(new VarbitChanged());

		assertTrue("questVarbitChanged should be set after cache is ready", getVarbitChangedFlag());
	}

	@Test
	public void testNoCelebrationWithoutVarbitChangedFlag()
	{
		simulateLoginAndTick();

		// Tick without any varbit change — flag is false, no quest check
		handler.onGameTick(new GameTick());

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testFlagClearedAfterGameTick()
	{
		simulateLoginAndTick();

		handler.onVarbitChanged(new VarbitChanged());
		assertTrue(getVarbitChangedFlag());

		handler.onGameTick(new GameTick());
		assertFalse("questVarbitChanged should be cleared after game tick", getVarbitChangedFlag());
	}

	@Test
	public void testCacheClearedOnLoginScreen()
	{
		simulateLoginAndTick();

		handler.onGameStateChanged(gameStateChanged(GameState.LOGIN_SCREEN));

		assertFalse("cacheReady should be false after logout", getCacheReadyFlag());
		assertFalse("needsCacheInit should be false after logout", getNeedsCacheInitFlag());

		// VarbitChanged after logout should not set the flag
		handler.onVarbitChanged(new VarbitChanged());
		assertFalse("questVarbitChanged should not be set after logout", getVarbitChangedFlag());
	}

	@Test
	public void testNeedsCacheInitSetOnLogin()
	{
		handler.onGameStateChanged(gameStateChanged(GameState.LOGGED_IN));

		assertTrue("needsCacheInit should be set on login", getNeedsCacheInitFlag());
	}

	@Test
	public void testCacheInitClearedAfterFirstTick()
	{
		handler.onGameStateChanged(gameStateChanged(GameState.LOGGED_IN));
		handler.onGameTick(new GameTick());

		assertFalse("needsCacheInit should be cleared after first tick", getNeedsCacheInitFlag());
		assertTrue("cacheReady should be set after first tick", getCacheReadyFlag());
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private void simulateLoginAndTick()
	{
		handler.onGameStateChanged(gameStateChanged(GameState.LOGGED_IN));
		handler.onGameTick(new GameTick());
	}

	private static GameStateChanged gameStateChanged(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}

	private boolean getVarbitChangedFlag()
	{
		return getBooleanField("questVarbitChanged");
	}

	private boolean getCacheReadyFlag()
	{
		return getBooleanField("cacheReady");
	}

	private boolean getNeedsCacheInitFlag()
	{
		return getBooleanField("needsCacheInit");
	}

	private boolean getBooleanField(String name)
	{
		try
		{
			Field field = QuestCompleteEventHandler.class.getDeclaredField(name);
			field.setAccessible(true);
			return (boolean) field.get(handler);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
