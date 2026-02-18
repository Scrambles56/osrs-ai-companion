package com.osrsaicompanion;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osrsaicompanion.tools.ClaudeTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ClaudeClient
{
	private static final String API_URL = "https://api.anthropic.com/v1/messages";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final int MAX_HISTORY_CHARS = 32_000;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final OsrsAiCompanionConfig config;
	private final PlayerContextBuilder contextBuilder;
	private final ClaudeTools claudeTools;
	private final ClientThread clientThread;

	private final List<JsonObject> conversationHistory = Collections.synchronizedList(new ArrayList<>());
	public volatile boolean apiCallInProgress = false;

	public void sendMessage(String userPrompt, AiCompanionPanel panel)
	{
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", withTimestamp(userPrompt));
		conversationHistory.add(userMessage);

		// buildSystemPrompt() calls Quest.getState() which requires the client thread
		clientThread.invokeLater(() -> callApi(panel));
	}

	public void clearHistory()
	{
		conversationHistory.clear();
	}

	// Must be called on the client thread (buildSystemPrompt needs it)
	public void callApi(AiCompanionPanel panel)
	{
		apiCallInProgress = true;

		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("model", config.model().getModelId());
		requestBody.addProperty("max_tokens", config.maxTokens());
		requestBody.addProperty("system", contextBuilder.buildSystemPrompt());
		requestBody.add("tools", ClaudeTools.buildToolDefinitions());

		JsonArray messages = new JsonArray();
		synchronized (conversationHistory)
		{
			for (JsonObject msg : conversationHistory)
			{
				messages.add(msg);
			}
		}
		requestBody.add("messages", messages);

		enqueueRequest(requestBody, panel);
	}

	private void enqueueRequest(JsonObject requestBody, AiCompanionPanel panel)
	{
		Request request = new Request.Builder()
			.url(API_URL)
			.header("Content-Type", "application/json")
			.header("x-api-key", config.apiKey())
			.header("anthropic-version", "2023-06-01")
			.post(RequestBody.create(JSON, gson.toJson(requestBody)))
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				apiCallInProgress = false;
				log.error("Claude API call failed", e);
				SwingUtilities.invokeLater(() -> {
					if (panel != null)
					{
						panel.appendErrorMessage("Error: Failed to reach Claude API");
					}
				});
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful())
					{
						apiCallInProgress = false;
						String errorBody = body != null ? body.string() : "Unknown error";
						log.error("Claude API error {}: {}", response.code(), errorBody);
						String errorMsg = "Error: Claude API returned " + response.code();
						SwingUtilities.invokeLater(() -> {
							if (panel != null)
							{
								panel.appendErrorMessage(errorMsg);
							}
						});
						return;
					}

					if (body == null)
					{
						apiCallInProgress = false;
						SwingUtilities.invokeLater(() -> {
							if (panel != null)
							{
								panel.appendErrorMessage("Error: Empty response from Claude");
							}
						});
						return;
					}

					handleResponse(body.string(), requestBody, panel);
				}
			}
		});
	}

	private void handleResponse(String responseJson, JsonObject requestBody, AiCompanionPanel panel)
	{
		JsonObject response;
		try
		{
			response = gson.fromJson(responseJson, JsonObject.class);
		}
		catch (Exception e)
		{
			apiCallInProgress = false;
			log.error("Failed to parse Claude response JSON", e);
			SwingUtilities.invokeLater(() -> {
				if (panel != null)
				{
					panel.appendErrorMessage("Error: Could not parse Claude's response");
				}
			});
			return;
		}

		String stopReason = response.has("stop_reason") ? response.get("stop_reason").getAsString() : "";
		JsonArray contentBlocks = response.has("content") ? response.getAsJsonArray("content") : new JsonArray();

		if ("tool_use".equals(stopReason))
		{
			// Claude wants to call a tool — record its response in history, execute the tool
			// on the client thread, then continue the conversation
			JsonObject assistantMessage = new JsonObject();
			assistantMessage.addProperty("role", "assistant");
			assistantMessage.add("content", contentBlocks);
			conversationHistory.add(assistantMessage);

			// Find all tool_use blocks and execute them
			JsonArray toolResults = new JsonArray();
			for (JsonElement el : contentBlocks)
			{
				JsonObject block = el.getAsJsonObject();
				if (!"tool_use".equals(block.get("type").getAsString()))
				{
					continue;
				}
				String toolUseId = block.get("id").getAsString();
				String toolName = block.get("name").getAsString();

				// Tool execution must happen on the client thread (varbit reads)
				clientThread.invokeLater(() -> {
					String toolResult = executeTool(toolName);

					JsonObject resultContent = new JsonObject();
					resultContent.addProperty("type", "tool_result");
					resultContent.addProperty("tool_use_id", toolUseId);
					resultContent.addProperty("content", toolResult);

					toolResults.add(resultContent);

					JsonObject toolResultMessage = new JsonObject();
					toolResultMessage.addProperty("role", "user");
					toolResultMessage.add("content", toolResults);
					conversationHistory.add(toolResultMessage);

					// Re-call the API with the tool result, reusing same system prompt etc.
					enqueueRequest(requestBody, panel);
				});
			}
		}
		else
		{
			// Normal text response
			String claudeText = extractText(contentBlocks);
			if (claudeText != null && !claudeText.isEmpty())
			{
				JsonObject assistantMessage = new JsonObject();
				assistantMessage.addProperty("role", "assistant");
				// Store with timestamp in history for Claude's temporal awareness,
				// but display the raw text without the timestamp prefix.
				assistantMessage.addProperty("content", withTimestamp(claudeText));
				conversationHistory.add(assistantMessage);
				trimHistoryIfNeeded();
				apiCallInProgress = false;

				SwingUtilities.invokeLater(() -> {
					if (panel != null)
					{
						panel.appendClaudeMessage(claudeText);
					}
				});
			}
			else
			{
				apiCallInProgress = false;
				SwingUtilities.invokeLater(() -> {
					if (panel != null)
					{
						panel.appendErrorMessage("Error: Could not parse Claude's response");
					}
				});
			}
		}
	}

	private String executeTool(String toolName)
	{
		return claudeTools.execute(toolName);
	}

	private static String extractText(JsonArray contentBlocks)
	{
		for (JsonElement el : contentBlocks)
		{
			JsonObject block = el.getAsJsonObject();
			if ("text".equals(block.get("type").getAsString()))
			{
				return block.get("text").getAsString();
			}
		}
		return null;
	}

	private void trimHistoryIfNeeded()
	{
		synchronized (conversationHistory)
		{
			int total = conversationHistory.stream()
				.mapToInt(m -> contentLength(m))
				.sum();
			while (total > MAX_HISTORY_CHARS && conversationHistory.size() > 2)
			{
				total -= contentLength(conversationHistory.remove(0));
			}
		}
	}

	private static int contentLength(JsonObject message)
	{
		JsonElement content = message.get("content");
		if (content == null)
		{
			return 0;
		}
		if (content.isJsonPrimitive())
		{
			return content.getAsString().length();
		}
		// JsonArray (tool_use / tool_result turns) — use serialised length as approximation
		return content.toString().length();
	}

	private static String withTimestamp(String content)
	{
		return "[" + Instant.now().toString() + "] " + content;
	}
}
