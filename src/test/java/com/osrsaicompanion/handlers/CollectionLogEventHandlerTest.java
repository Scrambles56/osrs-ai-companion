package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class CollectionLogEventHandlerTest
{
	private CollectionLogEventHandler handler;
	private ClaudeClient claudeClient;
	private OsrsAiCompanionConfig config;
	private PlayerContextBuilder contextBuilder;

	@Before
	public void setUp()
	{
		claudeClient = mock(ClaudeClient.class);
		config = mock(OsrsAiCompanionConfig.class);
		contextBuilder = mock(PlayerContextBuilder.class);
		when(config.celebrateCollectionLog()).thenReturn(true);
		when(contextBuilder.getPlayerName()).thenReturn("Scrambles56");

		handler = new CollectionLogEventHandler(claudeClient, contextBuilder, config, () -> null);
	}

	@Test
	public void testCollectionLogEntryTriggersMessage()
	{
		handler.onChatMessage(chatMessage("New item added to your collection log: Abyssal whip"));

		verify(claudeClient, times(1)).sendMessage(contains("Abyssal whip"), any());
	}

	@Test
	public void testItemNameExtractedCorrectly()
	{
		handler.onChatMessage(chatMessage("New item added to your collection log: Twisted bow"));

		verify(claudeClient, times(1)).sendMessage(contains("Twisted bow"), any());
	}

	@Test
	public void testUnrelatedMessageIgnored()
	{
		handler.onChatMessage(chatMessage("You have completed a lap of the Gnome Stronghold agility course."));

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testNonGameMessageIgnored()
	{
		ChatMessage event = new ChatMessage(null, ChatMessageType.PUBLICCHAT, "",
			"New item added to your collection log: Abyssal whip", "", 0);
		handler.onChatMessage(event);

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testNoMessageWhenDisabled()
	{
		when(config.celebrateCollectionLog()).thenReturn(false);
		handler.onChatMessage(chatMessage("New item added to your collection log: Abyssal whip"));

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testNoMessageWhenApiCallInProgress() throws Exception
	{
		var field = ClaudeClient.class.getDeclaredField("apiCallInProgress");
		field.setAccessible(true);
		field.set(claudeClient, true);

		handler.onChatMessage(chatMessage("New item added to your collection log: Abyssal whip"));

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
