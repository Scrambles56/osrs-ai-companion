package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BossKillEventHandlerTest
{
	private BossKillEventHandler handler;
	private ClaudeClient claudeClient;
	private OsrsAiCompanionConfig config;
	private PlayerContextBuilder contextBuilder;

	@Before
	public void setUp()
	{
		claudeClient = mock(ClaudeClient.class);
		config = mock(OsrsAiCompanionConfig.class);
		contextBuilder = mock(PlayerContextBuilder.class);
		when(config.celebrateBossKills()).thenReturn(true);
		when(contextBuilder.getPlayerName()).thenReturn("Scrambles56");

		handler = new BossKillEventHandler(claudeClient, contextBuilder, config, () -> null);
	}

	@Test
	public void testFirstKillTriggersMessage()
	{
		handler.onChatMessage(chatMessage("Your Zulrah kill count is: 1."));

		verify(claudeClient, times(1)).sendMessage(contains("first time"), any());
	}

	@Test
	public void testMilestoneKillTriggersMessage()
	{
		handler.onChatMessage(chatMessage("Your Zulrah kill count is: 100."));

		verify(claudeClient, times(1)).sendMessage(contains("100"), any());
	}

	@Test
	public void testNonMilestoneKillIgnored()
	{
		handler.onChatMessage(chatMessage("Your Zulrah kill count is: 42."));

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testAllMilestonesRecognised()
	{
		int[] milestones = {1, 50, 100, 250, 500, 1000, 2000, 5000};
		for (int kc : milestones)
		{
			handler.onChatMessage(chatMessage("Your Zulrah kill count is: " + kc + "."));
		}

		verify(claudeClient, times(milestones.length)).sendMessage(any(), any());
	}

	@Test
	public void testBossNameExtracted()
	{
		handler.onChatMessage(chatMessage("Your Chambers of Xeric kill count is: 50."));

		verify(claudeClient, times(1)).sendMessage(contains("Chambers of Xeric"), any());
	}

	@Test
	public void testNonGameMessageIgnored()
	{
		ChatMessage event = new ChatMessage(null, ChatMessageType.PUBLICCHAT, "", "Your Zulrah kill count is: 1.", "", 0);
		handler.onChatMessage(event);

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testNoMessageWhenApiCallInProgress() throws Exception
	{
		var field = ClaudeClient.class.getDeclaredField("apiCallInProgress");
		field.setAccessible(true);
		field.set(claudeClient, true);

		handler.onChatMessage(chatMessage("Your Zulrah kill count is: 1."));

		verify(claudeClient, never()).sendMessage(any(), any());
	}

	@Test
	public void testNoMessageWhenDisabled()
	{
		when(config.celebrateBossKills()).thenReturn(false);
		handler.onChatMessage(chatMessage("Your Zulrah kill count is: 1."));

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
