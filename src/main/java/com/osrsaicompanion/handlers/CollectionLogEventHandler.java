package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

import javax.swing.SwingUtilities;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class CollectionLogEventHandler
{
	private static final String COLLECTION_LOG_PREFIX = "New item added to your collection log: ";

	private final ClaudeClient claudeClient;
	private final PlayerContextBuilder contextBuilder;
	private final OsrsAiCompanionConfig config;
	private final Supplier<AiCompanionPanel> panelSupplier;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage();
		if (!message.startsWith(COLLECTION_LOG_PREFIX))
		{
			return;
		}

		if (!config.celebrateCollectionLog() || claudeClient.apiCallInProgress)
		{
			return;
		}

		String itemName = message.substring(COLLECTION_LOG_PREFIX.length());

		String playerName = contextBuilder.getPlayerName();
		String prompt = "I just got a new collection log entry: " + itemName
			+ "! React to this new unique drop.";
		String eventText = playerName + " added " + itemName + " to their collection log!";

		AiCompanionPanel panel = panelSupplier.get();
		SwingUtilities.invokeLater(() -> {
			if (panel != null)
			{
				panel.appendEventMessage(eventText);
			}
		});

		claudeClient.sendMessage(prompt, panel);
	}
}
