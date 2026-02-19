package com.osrsaicompanion;

import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import com.osrsaicompanion.handlers.BossKillEventHandler;
import com.osrsaicompanion.handlers.CollectionLogEventHandler;
import com.osrsaicompanion.handlers.DeathEventHandler;
import com.osrsaicompanion.handlers.DiaryCompletionEventHandler;
import com.osrsaicompanion.handlers.LevelUpEventHandler;
import com.osrsaicompanion.handlers.LootDropEventHandler;
import com.osrsaicompanion.handlers.QuestCompleteEventHandler;
import com.osrsaicompanion.tools.ClaudeTools;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.api.Client;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
	name = "AI Companion",
	description = "Chat with Claude AI in-game — get contextual help, level-up celebrations, and more",
	tags = {"claude", "ai", "chat", "anthropic", "assistant", "companion"}
)
public class OsrsAiCompanionPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar clientToolbar;
	@Inject private OsrsAiCompanionConfig config;
	@Inject private ItemManager itemManager;
	@Inject private Gson gson;
	@Inject private OkHttpClient httpClient;
	@Inject private ConfigManager configManager;
	@Inject private EventBus eventBus;

	private AiCompanionPanel panel;
	private NavigationButton navigationButton;
	private ClaudeClient claudeClient;
	private LevelUpEventHandler levelUpEventHandler;
	private QuestCompleteEventHandler questCompleteEventHandler;
	private DiaryCompletionEventHandler diaryCompletionEventHandler;
	private DeathEventHandler deathEventHandler;
	private BossKillEventHandler bossKillEventHandler;
	private CollectionLogEventHandler collectionLogEventHandler;
	private LootDropEventHandler lootDropEventHandler;

	@Override
	protected void startUp() throws Exception
	{
		PlayerContextBuilder contextBuilder = new PlayerContextBuilder(client, itemManager, config);
		ClaudeTools claudeTools = new ClaudeTools(client, httpClient, gson, itemManager);
		claudeClient = new ClaudeClient(httpClient, gson, config, contextBuilder, claudeTools, clientThread);

		panel = new AiCompanionPanel(this);
		BufferedImage icon = ImageUtil.loadImageResource(OsrsAiCompanionPlugin.class, "icon.png");
		navigationButton = NavigationButton.builder()
			.tooltip("AI Companion")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		levelUpEventHandler = new LevelUpEventHandler(claudeClient, contextBuilder, config, () -> panel);
		eventBus.register(levelUpEventHandler);

		questCompleteEventHandler = new QuestCompleteEventHandler(client, claudeClient, contextBuilder, config, () -> panel);
		eventBus.register(questCompleteEventHandler);

		diaryCompletionEventHandler = new DiaryCompletionEventHandler(client, claudeClient, contextBuilder, config, () -> panel);
		eventBus.register(diaryCompletionEventHandler);

		deathEventHandler = new DeathEventHandler(claudeClient, contextBuilder, config, () -> panel);
		eventBus.register(deathEventHandler);

		bossKillEventHandler = new BossKillEventHandler(claudeClient, contextBuilder, config, () -> panel);
		eventBus.register(bossKillEventHandler);

		collectionLogEventHandler = new CollectionLogEventHandler(claudeClient, contextBuilder, config, () -> panel);
		eventBus.register(collectionLogEventHandler);

		lootDropEventHandler = new LootDropEventHandler(client, itemManager, claudeClient, contextBuilder, config, () -> panel);
		eventBus.register(lootDropEventHandler);

		log.info("AI Companion plugin started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(levelUpEventHandler);
		levelUpEventHandler.clearCache();
		levelUpEventHandler = null;

		eventBus.unregister(questCompleteEventHandler);
		questCompleteEventHandler.clearCache();
		questCompleteEventHandler = null;

		eventBus.unregister(diaryCompletionEventHandler);
		diaryCompletionEventHandler.clearCache();
		diaryCompletionEventHandler = null;

		eventBus.unregister(deathEventHandler);
		deathEventHandler = null;

		eventBus.unregister(bossKillEventHandler);
		bossKillEventHandler = null;

		eventBus.unregister(collectionLogEventHandler);
		collectionLogEventHandler = null;

		eventBus.unregister(lootDropEventHandler);
		lootDropEventHandler = null;

		clientToolbar.removeNavigation(navigationButton);
		panel = null;
		navigationButton = null;

		claudeClient.clearHistory();
		claudeClient = null;

		log.info("AI Companion plugin stopped");
	}

	public void sendMessage(String userPrompt)
	{
		String apiKey = config.apiKey();
		if (apiKey == null || apiKey.isEmpty())
		{
			SwingUtilities.invokeLater(() -> {
				if (panel != null)
				{
					panel.appendErrorMessage("Please set your Claude API key in the plugin settings");
					panel.setInputEnabled(true);
				}
			});
			return;
		}

		if (panel != null)
		{
			panel.appendThinkingIndicator();
		}

		claudeClient.sendMessage(userPrompt, panel);
	}

	public void clearHistory()
	{
		claudeClient.clearHistory();
	}

	public void saveGoal(String goal)
	{
		configManager.setConfiguration("osrsaicompanion", "playerGoal", goal);
	}

	public String getGoal()
	{
		return config.playerGoal();
	}

	@Provides
	OsrsAiCompanionConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsAiCompanionConfig.class);
	}
}
