# CLAUDE.md

## Project Overview

**OSRS AI Companion** is a RuneLite plugin that integrates Claude AI as a context-aware in-game companion for Old School RuneScape.

### Key Features
- **Side panel chat** (`AiCompanionPanel`) — full conversation UI built into the RuneLite sidebar
- **Level-up celebrations** — automatically detects skill level-ups via `StatChanged` events and triggers a personalised Claude message
- **Rich player context** — every API call includes a system prompt with the player's skills, stats, HP, prayer, run energy, inventory, equipped items, location, quests, slayer task, achievement diaries, and bank contents
- **Persistent goal** — player sets a free-text goal (e.g. "get 70 Attack") stored in RuneLite config; injected into every system prompt
- **Conversation history** — maintained in a `Collections.synchronizedList`, trimmed to 32,000 characters

### Package & Class Structure
- Package: `com.osrsaicompanion`
- `OsrsAiCompanionPlugin` — main plugin class, handles events, builds system prompt, calls Claude API
- `OsrsAiCompanionConfig` — RuneLite config interface (API key, model, max tokens, player goal)
- `AiCompanionPanel` — `PluginPanel` subclass, HTML-based chat UI
- `AiModel` — enum of available Claude models (Haiku, Sonnet, Opus)

### API Integration
- Calls `https://api.anthropic.com/v1/messages` using the injected `OkHttpClient`
- Uses `anthropic-version: 2023-06-01` header
- Model and max tokens are user-configurable

## Build Instructions

This is a RuneLite plugin that requires Java 11 to build. The local machine has Java 25 as default which is incompatible.

**Build command:**
```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@11/11.0.30/libexec/openjdk.jdk/Contents/Home ./gradlew build
```

**Run in dev mode:**
```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@11/11.0.30/libexec/openjdk.jdk/Contents/Home ./gradlew run
```

## Plugin Hub Requirements

- Never create new `OkHttpClient` instances — always use `@Inject private OkHttpClient httpClient;`
- The injected client is managed by RuneLite and shared across plugins
- All client thread-sensitive calls (e.g. `client.getRealSkillLevel()`) must run via `clientThread.invokeLater()`
- All UI updates must run on the EDT via `SwingUtilities.invokeLater()`

## Thread Model

RuneLite has three threads that matter for this plugin:

| Thread | What runs there | How to get there |
|---|---|---|
| **Client thread** | Game logic, varbit reads, `Quest.getState()`, `buildSystemPrompt()` | `clientThread.invokeLater(...)` |
| **EDT** (Event Dispatch Thread) | All Swing UI updates | `SwingUtilities.invokeLater(...)` |
| **OkHttp callback thread** | `onResponse` / `onFailure` callbacks | (arrives here automatically) |

### Rules

**Never call these from the EDT or OkHttp thread:**
- `client.getVarbitValue()`, `client.getVarpValue()`, `client.getRealSkillLevel()`
- `Quest.getState()` (also runs a client script — see script reentrancy below)
- `buildSystemPrompt()` — calls all of the above internally

**Never update Swing components from the client thread or OkHttp thread** — always dispatch to the EDT.

**`ClaudeClient.sendMessage()` is the safe entry point from any thread.** It adds the message to history and then calls `clientThread.invokeLater(() -> callApi(panel))`, which means `buildSystemPrompt()` always runs on the client thread before the OkHttp request is enqueued.

### API call thread flow

```
Any thread (EDT, event handler, OkHttp)
  → claudeClient.sendMessage()          // adds to history, safe on any thread
    → clientThread.invokeLater()
      → callApi()                        // buildSystemPrompt() runs here (client thread)
        → httpClient.newCall().enqueue() // OkHttp sends to its own thread pool
          → onResponse()                 // OkHttp callback thread
            → [tool use?] clientThread.invokeLater() → execute tool → enqueueRequest()
            → SwingUtilities.invokeLater() → panel.appendClaudeMessage()
```

### Tool use threading

When Claude responds with `stop_reason: "tool_use"`, tool execution (varbit reads) must happen on the client thread. The flow is:

1. OkHttp `onResponse` detects `tool_use`
2. Dispatches to `clientThread.invokeLater()` to execute the tool
3. Adds the `tool_result` message to history
4. Calls `enqueueRequest()` again from the client thread to continue the conversation

## Safe Event Handler Pattern for Login

A common bug is triggering false celebrations/events on login because game state events fire before the server has finished syncing all data.

### The problem

- `GameStateChanged(LOGGED_IN)` fires early — client state is not fully synced yet
- Calling `client.getRealSkillLevel()` or `Quest.getState()` here returns stale/zero values
- The real values then arrive via `StatChanged` / `VarbitChanged`, which look like changes from the stale baseline — triggering false positives

### Additional constraint: scripts are not reentrant

`Quest.getState()` internally runs a client script. Calling it inside a `VarbitChanged` handler (which also fires during script execution) causes an `AssertionError: scripts are not reentrant` and can hang the client connection. Never call `Quest.getState()` from `VarbitChanged`.

### The solution: defer everything to the event stream itself

**For `StatChanged`-based handlers (e.g. skill level-ups):**

Use a `needsCacheInit` flag. When `LOGGED_IN` fires, set the flag. In `onStatChanged`, if the flag is set, absorb incoming events silently into the cache. Once all skills have been seen, clear the flag and set `cacheReady = true`. This guarantees the cache baseline matches exactly what the event stream considers current — no `getRealSkillLevel()` needed.

```java
private volatile boolean cacheReady = false;
private volatile boolean needsCacheInit = false;

@Subscribe
public void onGameStateChanged(GameStateChanged event) {
    if (event.getGameState() == GameState.LOGGED_IN) { needsCacheInit = true; }
    else if (event.getGameState() == GameState.LOGIN_SCREEN) { clearCache(); }
}

@Subscribe
public void onStatChanged(StatChanged event) {
    if (needsCacheInit) {
        skillLevelCache.put(skill, newLevel);
        if (skillLevelCache.size() >= Skill.values().length - 1) { // -1 for OVERALL
            needsCacheInit = false;
            cacheReady = true;
        }
        return;
    }
    if (!cacheReady) return;
    // ... normal level-up detection
}
```

**For `VarbitChanged`-based handlers (e.g. quest completion):**

Use both a `needsCacheInit` flag and a `questVarbitChanged` dirty flag. Never call `Quest.getState()` from `VarbitChanged` or `GameStateChanged`. Do all `Quest.getState()` calls in `onGameTick`, where scripts are safe to run.

```java
private volatile boolean cacheReady = false;
private volatile boolean needsCacheInit = false;
private volatile boolean questVarbitChanged = false;

@Subscribe
public void onGameStateChanged(GameStateChanged event) {
    if (event.getGameState() == GameState.LOGGED_IN) { needsCacheInit = true; }
    else if (event.getGameState() == GameState.LOGIN_SCREEN) { clearCache(); }
}

@Subscribe
public void onVarbitChanged(VarbitChanged event) {
    if (cacheReady) { questVarbitChanged = true; } // flag only — no Quest.getState() here
}

@Subscribe
public void onGameTick(GameTick event) {
    if (needsCacheInit) {
        needsCacheInit = false;
        for (Quest quest : Quest.values()) {
            try { questStateCache.put(quest, quest.getState(client)); } catch (Exception e) { }
        }
        cacheReady = true;
        return;
    }
    if (!cacheReady || !questVarbitChanged) return;
    questVarbitChanged = false;
    // ... normal quest completion detection
}
```
