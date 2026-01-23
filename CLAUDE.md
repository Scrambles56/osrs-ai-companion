# CLAUDE.md

## Build Instructions

This is a RuneLite plugin that requires Java 11 to build. The local machine has Java 25 as default which is incompatible.

**Build command:**
```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@11/11.0.30/libexec/openjdk.jdk/Contents/Home ./gradlew build
```

## Plugin Hub Requirements

- Never create new `OkHttpClient` instances - always use `@Inject private OkHttpClient httpClient;`
- The injected client is managed by RuneLite and shared across plugins
