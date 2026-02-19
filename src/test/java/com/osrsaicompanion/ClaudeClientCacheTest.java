package com.osrsaicompanion;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.osrsaicompanion.tools.ClaudeTools;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ClaudeClientCacheTest
{
	private ClaudeClient claudeClient;

	@Before
	public void setUp()
	{
		OkHttpClientStub httpClient = new OkHttpClientStub();
		Gson gson = new Gson();
		OsrsAiCompanionConfig config = mock(OsrsAiCompanionConfig.class);
		when(config.model()).thenReturn(AiModel.HAIKU);
		when(config.maxTokens()).thenReturn(1024);
		when(config.playerGoal()).thenReturn("");
		when(config.companionTone()).thenReturn(CompanionTone.NONE);

		Client client = mock(Client.class);
		ItemManager itemManager = mock(ItemManager.class);
		PlayerContextBuilder contextBuilder = mock(PlayerContextBuilder.class);
		when(contextBuilder.buildSlowSystemPrompt()).thenReturn("slow prompt");
		when(contextBuilder.buildFastSystemPrompt()).thenReturn("fast prompt");

		ClaudeTools claudeTools = new ClaudeTools(client, null, gson, itemManager);
		ClientThread clientThread = mock(ClientThread.class);

		claudeClient = new ClaudeClient(
			null, gson, config, contextBuilder, claudeTools, clientThread
		);
	}

	@Test
	public void testNoCacheBreakpointWithFewerThanTwoMessages() throws Exception
	{
		// With 0 or 1 messages there's nothing to cache — no message should have cache_control
		addMessage("user", "hello");

		JsonArray messages = invokeBuilMessagesWithCache();
		assertEquals(1, messages.size());

		JsonObject msg = messages.get(0).getAsJsonObject();
		// Content should still be a plain string (no wrapping)
		assertTrue(msg.get("content").isJsonPrimitive());
	}

	@Test
	public void testCacheBreakpointOnSecondToLastMessage() throws Exception
	{
		addMessage("user", "first message");
		addMessage("assistant", "first response");
		addMessage("user", "second message");

		JsonArray messages = invokeBuilMessagesWithCache();
		assertEquals(3, messages.size());

		// Second-to-last (index 1) should have cache_control
		JsonObject cached = messages.get(1).getAsJsonObject();
		JsonArray content = cached.getAsJsonArray("content");
		assertNotNull(content);
		JsonObject block = content.get(0).getAsJsonObject();
		assertEquals("text", block.get("type").getAsString());
		assertNotNull(block.get("cache_control"));
		assertEquals("ephemeral", block.getAsJsonObject("cache_control").get("type").getAsString());

		// Last message should NOT have cache_control and should be a plain string
		JsonObject last = messages.get(2).getAsJsonObject();
		assertTrue(last.get("content").isJsonPrimitive());
	}

	@Test
	public void testCacheBreakpointMovesWithConversation() throws Exception
	{
		addMessage("user", "msg1");
		addMessage("assistant", "msg2");
		addMessage("user", "msg3");
		addMessage("assistant", "msg4");
		addMessage("user", "msg5");

		JsonArray messages = invokeBuilMessagesWithCache();
		assertEquals(5, messages.size());

		// Only index 3 (second-to-last) should be cached
		for (int i = 0; i < 5; i++)
		{
			JsonObject msg = messages.get(i).getAsJsonObject();
			if (i == 3)
			{
				assertTrue("index " + i + " should have array content",
					msg.get("content").isJsonArray());
				JsonObject block = msg.getAsJsonArray("content").get(0).getAsJsonObject();
				assertNotNull("index " + i + " should have cache_control", block.get("cache_control"));
			}
			else
			{
				assertTrue("index " + i + " should have primitive content",
					msg.get("content").isJsonPrimitive());
			}
		}
	}

	@Test
	public void testArrayContentMessagesPassedThrough() throws Exception
	{
		// Tool result messages have array content — they should never be wrapped
		addMessage("user", "ask about diary");

		JsonObject assistantMsg = new JsonObject();
		assistantMsg.addProperty("role", "assistant");
		JsonArray toolUseContent = new JsonArray();
		JsonObject toolUseBlock = new JsonObject();
		toolUseBlock.addProperty("type", "tool_use");
		toolUseBlock.addProperty("id", "tool_123");
		toolUseBlock.addProperty("name", "get_achievement_diary_status");
		toolUseContent.add(toolUseBlock);
		assistantMsg.add("content", toolUseContent);
		addRawMessage(assistantMsg);

		addMessage("user", "latest user message");

		JsonArray messages = invokeBuilMessagesWithCache();
		assertEquals(3, messages.size());

		// The tool_use message at index 1 already has array content — should be passed through unchanged
		JsonObject toolMsg = messages.get(1).getAsJsonObject();
		assertTrue(toolMsg.get("content").isJsonArray());
		assertEquals("tool_use",
			toolMsg.getAsJsonArray("content").get(0).getAsJsonObject().get("type").getAsString());
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private void addMessage(String role, String content) throws Exception
	{
		JsonObject msg = new JsonObject();
		msg.addProperty("role", role);
		msg.addProperty("content", content);
		getHistory().add(msg);
	}

	private void addRawMessage(JsonObject msg) throws Exception
	{
		getHistory().add(msg);
	}

	@SuppressWarnings("unchecked")
	private List<JsonObject> getHistory() throws Exception
	{
		var field = ClaudeClient.class.getDeclaredField("conversationHistory");
		field.setAccessible(true);
		return (List<JsonObject>) field.get(claudeClient);
	}

	private JsonArray invokeBuilMessagesWithCache() throws Exception
	{
		Method method = ClaudeClient.class.getDeclaredMethod("buildMessagesWithCache");
		method.setAccessible(true);
		return (JsonArray) method.invoke(claudeClient);
	}

	// Minimal stub — we never actually make HTTP calls in these tests
	private static class OkHttpClientStub extends okhttp3.OkHttpClient
	{
	}
}
