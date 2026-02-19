# OSRS AI Companion

A RuneLite plugin that brings Claude AI into Old School RuneScape as a context-aware companion. It knows who you are, where you are, and what you're doing — and reacts accordingly.

## Features

### Side Panel Chat
A dedicated chat panel in the RuneLite sidebar for full conversations with Claude. Maintains conversation history (up to 32,000 characters) so Claude remembers what you've discussed in the session.

Set a persistent **player goal** (e.g. "get 70 Attack for Whip") that Claude always keeps in mind, even between sessions.

### Automatic Event Reactions
Claude reacts to in-game milestones without you having to ask:

| Event | What Claude does |
|---|---|
| **Level-up** | Congratulates you with a personalised message based on your current situation |
| **XP milestone** | Celebrates every 50m XP gained after reaching level 99 |
| **Quest completion** | Congratulates you and mentions notable quests now unlocked as prerequisites |
| **Achievement diary tier** | Congratulates you and highlights notable rewards |
| **Boss kill milestone** | Reacts at 1, 50, 100, 250, 500, 1,000, 2,000 and 5,000 kills |
| **Collection log entry** | Celebrates new unique drops, clue rewards, and anything else that logs |
| **Valuable drop** | Reacts to drops above a configurable GP threshold |
| **Death** | Commiserates |
| **Login** | Welcomes you back and asks what you want to work on |

All event reactions can be individually toggled in the plugin settings.

### Rich Player Context
Every message Claude receives includes a full snapshot of your current state:
- All skill levels and XP
- HP, prayer, run energy
- Inventory and equipped items
- Current location
- Completed and in-progress quests
- Slayer task
- Achievement diary completion
- Bank contents (when open)
- Your set goal

### Tool Use
Claude can request additional detail on demand rather than loading everything every time:
- **Achievement diary status** — full per-region, per-tier breakdown including individual task progress
- **Combat achievement status** — tier completion across all six CA tiers
- **OSRS Wiki search** — looks up accurate, up-to-date game information (quest requirements, item stats, training methods, etc.)
- **Grand Exchange prices** — fetches live buy/sell prices for any item by name

## Setup

### 1. Get an Anthropic API Key

1. Go to [console.anthropic.com](https://console.anthropic.com)
2. Create an account or sign in
3. Navigate to API Keys and create a new key
4. Copy the key (starts with `sk-ant-`)

### 2. Install the Plugin

For development/testing:
```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@11/11.0.30/libexec/openjdk.jdk/Contents/Home ./gradlew run
```

### 3. Configure the Plugin

1. Open RuneLite settings (wrench icon)
2. Find "AI Companion" in the plugin list
3. Enter your API key in the **Claude API Key** field
4. Optionally set your current in-game goal
5. Choose your preferred Claude model and adjust other settings as desired

## Settings

### API Settings
| Setting | Description | Default |
|---|---|---|
| Claude API Key | Your Anthropic API key | (empty) |
| Model | Claude model to use (Haiku / Sonnet / Opus) | Haiku |
| Max Tokens | Maximum response length | 1024 |
| Companion Tone | Personality Claude adopts (None, Wise Old Man, Drunken Dwarf, Proud Dad, Bob, Zamorak Zealot) | None |
| Player Goal | Persistent goal Claude always keeps in mind | (empty) |

### Event Celebrations
Individual toggles for each event type — level-ups, XP milestones, quest completions, diary completions, boss kill milestones, collection log entries, and deaths.

### Loot Alerts
| Setting | Description | Default |
|---|---|---|
| Valuable drops | Enable/disable drop reactions | On |
| Loot alert threshold | Minimum drop value to trigger a reaction | 100,000 gp |

## Building

See [CONTRIBUTING.md](CONTRIBUTING.md) for build instructions across macOS, Windows, and Linux.

## Notes

- All responses are **local only** — other players cannot see them
- API costs apply based on your Anthropic account usage
- The plugin uses RuneLite's shared HTTP client as required by the Plugin Hub

## License

BSD 2-Clause License — original work by [Zodomo](https://github.com/Zodomo), modified and extended by [Scrambles56](https://github.com/Scrambles56).
