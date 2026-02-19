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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

	private static final int MAX_TOOL_ROUNDS = 10;

	private final List<JsonObject> conversationHistory = Collections.synchronizedList(new ArrayList<>());
	private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor();
	public volatile boolean apiCallInProgress = false;

	// Tracks tool results that were already returned as not-found/not-tradeable this turn.
	// Key: "toolName:inputKey=inputValue", value: the cached result string.
	// Cleared at the start of each new user message so it doesn't persist across turns.
	private final java.util.concurrent.ConcurrentHashMap<String, String> toolResultCache = new java.util.concurrent.ConcurrentHashMap<>();

	public void sendMessage(String userPrompt, AiCompanionPanel panel)
	{
		toolResultCache.clear();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", withTimestamp(userPrompt));
		conversationHistory.add(userMessage);

		// buildSlowSystemPrompt() calls Quest.getState() which requires the client thread
		clientThread.invokeLater(() -> callApi(panel));
	}

	public void clearHistory()
	{
		conversationHistory.clear();
	}

	// Must be called on the client thread (buildSlowSystemPrompt needs it for Quest.getState())
	public void callApi(AiCompanionPanel panel)
	{
		apiCallInProgress = true;

		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("model", config.model().getModelId());
		requestBody.addProperty("max_tokens", config.maxTokens());
		JsonObject cacheControl = new JsonObject();
		cacheControl.addProperty("type", "ephemeral");

		JsonObject slowBlock = new JsonObject();
		slowBlock.addProperty("type", "text");
		slowBlock.addProperty("text", contextBuilder.buildSlowSystemPrompt());
		slowBlock.add("cache_control", cacheControl);

		JsonObject fastBlock = new JsonObject();
		fastBlock.addProperty("type", "text");
		fastBlock.addProperty("text", contextBuilder.buildFastSystemPrompt());

		JsonArray systemBlocks = new JsonArray();
		systemBlocks.add(slowBlock);
		systemBlocks.add(fastBlock);

		requestBody.add("system", systemBlocks);

		// Mark the last tool definition with cache_control so the entire tools array
		// is cached — it's large and completely static between requests.
		JsonArray tools = ClaudeTools.buildToolDefinitions();
		JsonObject lastTool = tools.get(tools.size() - 1).getAsJsonObject();
		JsonObject toolCacheControl = new JsonObject();
		toolCacheControl.addProperty("type", "ephemeral");
		lastTool.add("cache_control", toolCacheControl);
		requestBody.add("tools", tools);

		log.info("[AI] Sending request (model={}, history={} messages)", config.model().getModelId(), conversationHistory.size());
		enqueueRequest(requestBody, panel, 0, 0);
	}


	// Builds the messages array, placing a cache_control breakpoint on the second-to-last
	// message so the full conversation history up to that point gets cached between turns.
	// Only messages with plain string content are wrapped; array-content messages (tool
	// use / tool result turns) are passed through unchanged.
	private JsonArray buildMessagesWithCache()
	{
		synchronized (conversationHistory)
		{
			JsonArray messages = new JsonArray();
			int size = conversationHistory.size();
			// We cache up to (but not including) the final user message, so we need at
			// least 2 messages for the breakpoint to make sense.
			int cacheIndex = size - 2;

			for (int i = 0; i < size; i++)
			{
				JsonObject msg = conversationHistory.get(i);
				if (i == cacheIndex && msg.get("content").isJsonPrimitive())
				{
					// Wrap the plain-string content in an array block with cache_control
					JsonObject textBlock = new JsonObject();
					textBlock.addProperty("type", "text");
					textBlock.addProperty("text", msg.get("content").getAsString());

					JsonObject cc = new JsonObject();
					cc.addProperty("type", "ephemeral");
					textBlock.add("cache_control", cc);

					JsonArray contentArray = new JsonArray();
					contentArray.add(textBlock);

					JsonObject cachedMsg = new JsonObject();
					cachedMsg.addProperty("role", msg.get("role").getAsString());
					cachedMsg.add("content", contentArray);
					messages.add(cachedMsg);
				}
				else
				{
					messages.add(msg);
				}
			}
			return messages;
		}
	}

	private void enqueueRequest(JsonObject requestBody, AiCompanionPanel panel, int toolRound, int retryCount)
	{
		// Rebuild the messages array from conversationHistory on every call so that
		// tool results added between rounds are included in the next request.
		requestBody.add("messages", buildMessagesWithCache());
		log.info("[AI] enqueueRequest toolRound={} retryCount={} history={}", toolRound, retryCount, conversationHistory.size());

		if (toolRound >= MAX_TOOL_ROUNDS)
		{
			apiCallInProgress = false;
			log.warn("[AI] Aborting after {} tool rounds to prevent infinite loop", MAX_TOOL_ROUNDS);
			SwingUtilities.invokeLater(() -> {
				if (panel != null)
				{
					panel.appendErrorMessage("Error: Too many tool calls — stopped to prevent a loop");
				}
			});
			return;
		}

		if (retryCount >= MAX_TOOL_ROUNDS)
		{
			apiCallInProgress = false;
			log.warn("[AI] Aborting after {} retries", MAX_TOOL_ROUNDS);
			SwingUtilities.invokeLater(() -> {
				if (panel != null)
				{
					panel.appendErrorMessage("Error: Rate limit hit repeatedly — please wait a minute and try again");
				}
			});
			return;
		}

		Request request = new Request.Builder()
			.url(API_URL)
			.header("Content-Type", "application/json")
			.header("x-api-key", config.apiKey())
			.header("anthropic-version", "2023-06-01")
			.header("anthropic-beta", "prompt-caching-2024-07-31")
			.post(RequestBody.create(JSON, gson.toJson(requestBody)))
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.error("[AI] API call failed (retry {})", retryCount + 1, e);
				enqueueRequest(requestBody, panel, toolRound, retryCount + 1);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (response.code() == 429)
					{
						String retryAfterHeader = response.header("retry-after");
						long delaySecs = 60; // safe fallback
						if (retryAfterHeader != null)
						{
							try { delaySecs = Long.parseLong(retryAfterHeader.trim()); }
							catch (NumberFormatException ignored) { }
						}

						final long waitSecs = delaySecs;
						log.warn("[AI] Rate limited (429). Retrying in {}s (retry {}/{})", waitSecs, retryCount + 1, MAX_TOOL_ROUNDS);
						SwingUtilities.invokeLater(() -> {
							if (panel != null)
							{
								panel.appendErrorMessage("Rate limited — retrying in " + waitSecs + "s...");
							}
						});
						retryScheduler.schedule(
							() -> enqueueRequest(requestBody, panel, toolRound, retryCount + 1),
							waitSecs, TimeUnit.SECONDS
						);
						return;
					}

					if (!response.isSuccessful())
					{
						apiCallInProgress = false;
						String errorBody = body != null ? body.string() : "Unknown error";
						log.error("[AI] API error {}: {}", response.code(), errorBody);
						SwingUtilities.invokeLater(() -> {
							if (panel != null)
							{
								panel.appendErrorMessage("Error: Claude API returned " + response.code());
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

					handleResponse(body.string(), requestBody, panel, toolRound, retryCount);
				}
			}
		});
	}

	private void handleResponse(String responseJson, JsonObject requestBody, AiCompanionPanel panel, int toolRound, int retryCount)
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
		String responseModel = response.has("model") ? response.get("model").getAsString() : null;
		JsonArray contentBlocks = response.has("content") ? response.getAsJsonArray("content") : new JsonArray();

		if ("tool_use".equals(stopReason))
		{
			// Claude wants to call tools — record its response in history, execute all tools
			// in parallel, then fire a single continuation once every result is ready.
			log.info("[AI] stop_reason=tool_use (toolRound={})", toolRound);
			JsonObject assistantMessage = new JsonObject();
			assistantMessage.addProperty("role", "assistant");
			assistantMessage.add("content", contentBlocks);
			conversationHistory.add(assistantMessage);

			// Collect all tool_use blocks first so we know the total count
			List<JsonObject> toolUseBlocks = new ArrayList<>();
			for (JsonElement el : contentBlocks)
			{
				JsonObject block = el.getAsJsonObject();
				if ("tool_use".equals(block.get("type").getAsString()))
				{
					toolUseBlocks.add(block);
				}
			}

			// Pre-allocate result slots — each thread writes to its own index, no locking needed.
			// Only the last thread to finish triggers the single API continuation.
			int toolCount = toolUseBlocks.size();
			log.info("[AI] Executing {} tool(s) in parallel: {}", toolCount,
				toolUseBlocks.stream().map(b -> {
					String name = b.get("name").getAsString();
					JsonObject input = b.has("input") ? b.getAsJsonObject("input") : null;
					if (input != null && input.has("item_name")) return name + "(" + input.get("item_name").getAsString() + ")";
					if (input != null && input.has("query"))     return name + "(" + input.get("query").getAsString() + ")";
					return name;
				}).collect(java.util.stream.Collectors.joining(", ")));
			JsonObject[] resultSlots = new JsonObject[toolCount];
			AtomicInteger remaining = new AtomicInteger(toolCount);

			for (int i = 0; i < toolCount; i++)
			{
				final int slot = i;
				JsonObject block = toolUseBlocks.get(slot);
				String toolUseId = block.get("id").getAsString();
				String toolName = block.get("name").getAsString();
				JsonObject toolInput = block.has("input") ? block.getAsJsonObject("input") : null;

				Runnable task = () -> {
					String toolResult = executeTool(toolName, toolInput);
					String logKey = toolInput != null && toolInput.has("item_name") ? toolInput.get("item_name").getAsString() :
						toolInput != null && toolInput.has("query") ? toolInput.get("query").getAsString() : "";
					log.info("[AI] Tool result for {}({}): [{}]", toolName, logKey,
						toolResult.replace("\n", "\\n"));
					JsonObject resultContent = new JsonObject();
					resultContent.addProperty("type", "tool_result");
					resultContent.addProperty("tool_use_id", toolUseId);
					resultContent.addProperty("content", toolResult);
					boolean isError = toolResult.startsWith("Error:") || toolResult.startsWith("GE price lookup failed") || toolResult.startsWith("Wiki search failed") || toolResult.startsWith("Unknown tool");
					if (isError)
					{
						resultContent.addProperty("is_error", true);
					}
					resultSlots[slot] = resultContent;

					// Only the last tool to finish triggers the API continuation
					if (remaining.decrementAndGet() == 0)
					{
						JsonArray toolResults = new JsonArray();
						for (JsonObject r : resultSlots)
						{
							toolResults.add(r);
						}
						JsonObject toolResultMessage = new JsonObject();
						toolResultMessage.addProperty("role", "user");
						toolResultMessage.add("content", toolResults);
						conversationHistory.add(toolResultMessage);
						enqueueRequest(requestBody, panel, toolRound + 1, 0);
					}
				};

				// HTTP tools run on OkHttp's thread pool; varbit tools on the client thread
				if ("search_wiki".equals(toolName) || "get_ge_price".equals(toolName))
				{
					httpClient.dispatcher().executorService().execute(task);
				}
				else
				{
					clientThread.invokeLater(task);
				}
			}
		}
		else
		{
			// Normal text response
			log.info("[AI] stop_reason={} (toolRound={}), content blocks: {}", stopReason, toolRound, contentBlocks);
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

				final String finalModel = responseModel;
				SwingUtilities.invokeLater(() -> {
					if (panel != null)
					{
						panel.appendClaudeMessage(claudeText, finalModel);
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

	/**
	 * Returns a copy of requestBody with tool_choice set to {type: "none"}, which
	 * forces the model to produce a text response without calling any more tools.
	 * Used after the first tool round to break looping behaviour.
	 */
	private JsonObject requestBodyWithNoTools(JsonObject requestBody)
	{
		JsonObject copy = requestBody.deepCopy();
		JsonObject toolChoice = new JsonObject();
		toolChoice.addProperty("type", "none");
		copy.add("tool_choice", toolChoice);
		return copy;
	}

	private String executeTool(String toolName, JsonObject input)
	{
		return claudeTools.execute(toolName, input);
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
		return "<t:" + Instant.now().toString() + "> " + content;
	}
}
