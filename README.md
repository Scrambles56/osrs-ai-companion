# OSRS Claude

Chat with Claude AI directly in Old School RuneScape using the `!claude` command.

## Features

- Send messages to Claude AI by typing `!claude <your message>` in public chat
- Responses appear as local game messages (only you can see them)
- Configurable response length via batch number setting
- Support for Claude Haiku 4.5, Claude Sonnet 4.5, and Claude Opus 4.5 models

## Setup

### 1. Get an Anthropic API Key

1. Go to [console.anthropic.com](https://console.anthropic.com)
2. Create an account or sign in
3. Navigate to API Keys and create a new key
4. Copy the key (starts with `sk-ant-`)

### 2. Install the Plugin

For development/testing:
```bash
./gradlew run
```

### 3. Configure the Plugin

1. Open RuneLite settings (wrench icon)
2. Find "Claude Chat" in the plugin list
3. Enter your API key in the "Claude API Key" field
4. Adjust "Response Batches" to control max response length (200 chars per batch)
5. Select your preferred Claude model

## Usage

In any public chat, type:
```
!claude What is the best way to train mining?
```

Claude will respond in your chat window with helpful information.

## Settings

| Setting | Description | Default |
|---------|-------------|---------|
| Claude API Key | Your Anthropic API key | (empty) |
| Response Batches | Number of 200-char message batches (1-10) | 3 |
| Claude Model | Which Claude model to use | Claude Sonnet 4.5 |

## Building

Requires Java 11:

```bash
# macOS with Homebrew
brew install openjdk@11
export JAVA_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"

# Build
./gradlew build

# Run in development mode
./gradlew run
```

## Notes

- Responses are **local only** - other players cannot see Claude's messages
- Each message batch is limited to 200 characters (OSRS game message limit)
- Claude is instructed to keep responses concise to fit within the character limit
- API costs apply based on your Anthropic account

## License

BSD 2-Clause License
