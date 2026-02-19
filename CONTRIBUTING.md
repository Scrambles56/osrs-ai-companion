# Contributing

## Prerequisites

The plugin requires **Java 11** to build. RuneLite is not compatible with newer JDK versions.

### Installing Java 11

**macOS (Homebrew)**
```bash
brew install openjdk@11
```

**Windows (Scoop)**
```bash
scoop bucket add java
scoop install openjdk11
```

**Windows (manual)**

Download and install from [Adoptium](https://adoptium.net/temurin/releases/?version=11).

**Linux (apt)**
```bash
sudo apt install openjdk-11-jdk
```

**Linux (dnf)**
```bash
sudo dnf install java-11-openjdk-devel
```

---

## Building

### macOS / Linux

```bash
JAVA_HOME=$(java_home -v 11 2>/dev/null || echo "/usr/lib/jvm/java-11-openjdk") ./gradlew build
```

Or if you know the exact path:
```bash
JAVA_HOME=/path/to/java-11 ./gradlew build
```

### Windows (Command Prompt)
```cmd
set JAVA_HOME=C:\path\to\java-11
gradlew.bat build
```

### Windows (PowerShell)
```powershell
$env:JAVA_HOME = "C:\path\to\java-11"
.\gradlew.bat build
```

---

## Running in development mode

Launches a full RuneLite client with the plugin loaded for live testing.

### macOS / Linux
```bash
JAVA_HOME=/path/to/java-11 ./gradlew run
```

### Windows
```cmd
set JAVA_HOME=C:\path\to\java-11
gradlew.bat run
```

---

## Configuration for testing

1. Launch via `./gradlew run`
2. Open RuneLite settings → find **AI Companion**
3. Enter your Anthropic API key (get one at [console.anthropic.com](https://console.anthropic.com))
4. Log into OSRS as normal

---

## Notes

- Never create new `OkHttpClient` instances — always use the injected `@Inject private OkHttpClient httpClient`
- UI updates must be dispatched to the EDT via `SwingUtilities.invokeLater()`
- Game state reads (`client.getVarbitValue()`, `Quest.getState()`, etc.) must run on the client thread via `clientThread.invokeLater()`
- See `CLAUDE.md` for full architecture documentation
