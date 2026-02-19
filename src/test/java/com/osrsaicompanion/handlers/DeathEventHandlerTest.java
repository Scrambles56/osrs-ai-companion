package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class DeathEventHandlerTest
{
	private DeathEventHandler handler;
	private ClaudeClient claudeClient;
	private OsrsAiCompanionConfig config;
	private PlayerContextBuilder contextBuilder;

	@Before
	public void setUp()
	{
		claudeClient = mock(ClaudeClient.class);
		config = mock(OsrsAiCompanionConfig.class);
		contextBuilder = mock(PlayerContextBuilder.class);
		when(config.commiserateDeath()).thenReturn(true);
		when(contextBuilder.getPlayerName()).thenReturn("Scrambles56");

		handler = new DeathEventHandler(claudeClient, contextBuilder, config, () -> null);
	}

	@Test
	public void testDeathMessageTriggersCommiseration()
	{
		handler.onChatMessage(chatMessage("Oh dear, you are dead!"));

		verify(claudeClient, times(1)).sendMessage(contains("died"), any());
	}

	@Test
	public void testOtherGameMessageIgnored()
	{
		handler.onChatMessage(chatMessage("Welcome to Old School RuneScape."));

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testNonGameMessageIgnored()
	{
		ChatMessage event = new ChatMessage(null, ChatMessageType.PUBLICCHAT, "", "Oh dear, you are dead!", "", 0);
		handler.onChatMessage(event);

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testNoMessageWhenDisabled()
	{
		when(config.commiserateDeath()).thenReturn(false);
		handler.onChatMessage(chatMessage("Oh dear, you are dead!"));

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testNoMessageWhenApiCallInProgress() throws Exception
	{
		var field = ClaudeClient.class.getDeclaredField("apiCallInProgress");
		field.setAccessible(true);
		field.set(claudeClient, true);

		handler.onChatMessage(chatMessage("Oh dear, you are dead!"));

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static ChatMessage chatMessage(String message)
	{
		return new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", message, "", 0);
	}
}
