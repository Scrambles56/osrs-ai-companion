package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class DiaryCompletionEventHandlerTest
{
	private DiaryCompletionEventHandler handler;
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
		when(config.celebrateDiaryCompletions()).thenReturn(true);
		when(contextBuilder.getPlayerName()).thenReturn("Scrambles56");

		// All diaries start incomplete
		when(client.getVarbitValue(anyInt())).thenReturn(0);

		handler = new DiaryCompletionEventHandler(client, claudeClient, contextBuilder, config, () -> null);
	}

	@Test
	public void testNoCelebrationDuringCacheInit()
	{
		handler.onGameStateChanged(gameStateChanged(GameState.LOGGED_IN));

		// Even if a varbit looks like it changed during init, no celebration
		when(client.getVarbitValue(Varbits.DIARY_LUMBRIDGE_EASY)).thenReturn(1);
		handler.onGameTick(new GameTick()); // init tick

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testDiaryCompletionDetectedOnGameTick()
	{
		simulateLoginAndTick();

		// Lumbridge Easy diary completed
		when(client.getVarbitValue(Varbits.DIARY_LUMBRIDGE_EASY)).thenReturn(1);
		handler.onVarbitChanged(new VarbitChanged());
		handler.onGameTick(new GameTick());

		verify(claudeClient, times(1)).sendMessage(contains("Lumbridge"), any());
		verify(claudeClient, times(1)).sendMessage(contains("Easy"), any());
	}

	@Test
	public void testHardDiaryCompletionDetected()
	{
		simulateLoginAndTick();

		when(client.getVarbitValue(Varbits.DIARY_ARDOUGNE_HARD)).thenReturn(1);
		handler.onVarbitChanged(new VarbitChanged());
		handler.onGameTick(new GameTick());

		verify(claudeClient, times(1)).sendMessage(contains("Ardougne"), any());
		verify(claudeClient, times(1)).sendMessage(contains("Hard"), any());
	}

	@Test
	public void testNoFalseCelebrationWhenAlreadyComplete()
	{
		// Diary starts as already complete in cache
		when(client.getVarbitValue(Varbits.DIARY_LUMBRIDGE_EASY)).thenReturn(1);
		simulateLoginAndTick();

		// Still complete — no 0→1 transition
		handler.onVarbitChanged(new VarbitChanged());
		handler.onGameTick(new GameTick());

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testNoCelebrationWithoutVarbitChanged()
	{
		simulateLoginAndTick();

		// Tick without varbit change — nothing should happen
		handler.onGameTick(new GameTick());

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testCacheClearedOnLoginScreen()
	{
		simulateLoginAndTick();

		handler.onGameStateChanged(gameStateChanged(GameState.LOGIN_SCREEN));
		handler.onVarbitChanged(new VarbitChanged());
		handler.onGameTick(new GameTick());

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testNoMessageWhenDisabled()
	{
		when(config.celebrateDiaryCompletions()).thenReturn(false);
		simulateLoginAndTick();

		when(client.getVarbitValue(Varbits.DIARY_LUMBRIDGE_EASY)).thenReturn(1);
		handler.onVarbitChanged(new VarbitChanged());
		handler.onGameTick(new GameTick());

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private void simulateLoginAndTick()
	{
		handler.onGameStateChanged(gameStateChanged(GameState.LOGGED_IN));
		handler.onGameTick(new GameTick()); // init tick
	}

	private static GameStateChanged gameStateChanged(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}
}
