# Gemini TP Mod - AI-Powered Death Teleportation

A Minecraft Fabric mod that integrates Google's Gemini AI to provide conversational death teleportation for players.

> **API key**: the mod reads your key from the `GEMINI_API_KEY` environment variable at runtime — it is never hardcoded in source. See `.env.example` for the variable name, and the "Set Environment Variable" step below for how to set it on your OS. The server throws a clear startup error if the variable is missing.

## Features

- 🤖 **AI-Powered Chat**: Natural language conversations with Gemini AI
- 📍 **Death Location Tracking**: Automatically tracks where players die
- 🚀 **Smart Teleportation**: AI can teleport players to their death locations using function calling
- ⏱️ **Rate Limiting**: 5 free teleports per hour per player
- 💬 **Conversational Interface**: No commands needed - just chat naturally!

## Example Usage

```
Player: Hey I died, can I get tped to my death?
AI: Sure! But I have to use one of your daily 5 TPs per hour, is that okay?
Player: Sure!
AI: [AI teleports player and confirms]
```

## Prerequisites

- **Minecraft 1.21.11**
- **Java 21 or higher**
- **Fabric Loader 0.18.1+**
- **Fabric API**
- **Google Gemini API Key** (free at https://ai.google.dev)

## Installation

### 1. Get a Gemini API Key

1. Visit [Google AI Studio](https://ai.google.dev)
2. Sign in with your Google account
3. Click "Get API Key"
4. Copy your API key

### 2. Set Environment Variable

**Windows (PowerShell):**
```powershell
[System.Environment]::SetEnvironmentVariable('GEMINI_API_KEY', 'your-api-key-here', 'User')
```

**Windows (Command Prompt):**
```cmd
setx GEMINI_API_KEY "your-api-key-here"
```

**Linux/Mac:**
```bash
export GEMINI_API_KEY="your-api-key-here"
# Add to ~/.bashrc or ~/.zshrc to make it permanent
echo 'export GEMINI_API_KEY="your-api-key-here"' >> ~/.bashrc
```

**Important**: Restart your terminal/IDE after setting the environment variable!

### 3. Build the Mod

```bash
# Clone or download this repository
cd gemini-tp-mod

# Build the mod
./gradlew build
# On Windows: gradlew.bat build
```

The compiled mod JAR will be in `build/libs/gemini-tp-mod-1.0.0.jar`

### 4. Install to Minecraft

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.11
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11
3. Copy both the Fabric API JAR and the Gemini TP Mod JAR to your `.minecraft/mods` folder
4. Launch Minecraft with the Fabric profile

## How It Works

### Death Tracking
When a player dies, their death location (coordinates and dimension) is automatically saved.

### AI Chat Handler
The mod listens to chat messages and sends relevant ones to Gemini AI with function calling capabilities.

### Function Calling
Gemini has access to two functions:
- `teleport_to_death`: Teleports the player to their last death location
- `check_teleports_remaining`: Checks how many TPs the player has left this hour

### Rate Limiting
Each player gets 5 teleports per hour. The counter resets every hour.

## Development

### Project Structure
```
gemini-tp-mod/
├── src/main/java/com/example/geminitp/
│   ├── GeminiTpMod.java           # Main mod entry point
│   ├── DeathLocationTracker.java  # Tracks player death locations
│   ├── GeminiChatHandler.java     # Handles AI integration
│   └── RateLimiter.java           # Manages teleport limits
├── src/main/resources/
│   └── fabric.mod.json            # Mod metadata
├── build.gradle                    # Build configuration
└── gradle.properties              # Gradle properties
```

### Dependencies

- **Fabric API**: Core Fabric API for events and utilities
- **OkHttp**: HTTP client for Gemini API calls
- **Gson**: JSON parsing and serialization
- **Google Generative AI**: (Optional, currently using direct HTTP calls)

### Testing

1. Start Minecraft with the mod installed
2. Create a new world or join a server
3. Die somewhere memorable
4. Respawn and type in chat: "Hey I died, can you teleport me back?"
5. The AI should respond and offer to teleport you

## Configuration

Currently, the mod uses these hardcoded settings:
- **Max teleports per hour**: 5
- **AI Model**: gemini-2.0-flash-exp
- **Trigger words**: "ai", "hey" (or being in an active conversation)

To modify these, edit the relevant constants in the source files.

## Troubleshooting

### "GEMINI_API_KEY environment variable not set!"
- Make sure you've set the environment variable
- Restart your terminal/IDE after setting it
- On Windows, you may need to restart your computer

### AI doesn't respond to chat messages
- Make sure you're including "ai" or "hey" in your message to trigger the AI
- Check the server logs for errors
- Verify your API key is valid

### Build errors
- Ensure you're using Java 21 or higher: `java -version`
- Run `./gradlew clean build` to clean and rebuild

### Teleport doesn't work
- Make sure you've died at least once before requesting a teleport
- Check if you have teleports remaining (ask the AI: "how many teleports do I have left?")
- Look for errors in the server logs

## API Costs

Gemini API offers a generous free tier:
- **Free tier**: 15 requests per minute, 1,500 requests per day
- This mod uses minimal tokens per request
- Typical usage should stay well within free limits

For pricing details, visit: https://ai.google.dev/pricing

## Contributing

Feel free to submit issues and pull requests!

## License

MIT License - Feel free to modify and distribute

## Credits

- Built with [Fabric](https://fabricmc.net/)
- Powered by [Google Gemini AI](https://ai.google.dev)
- Created for Minecraft 1.21.11

## Future Ideas

- [ ] Configurable teleport limits
- [ ] Multiple death location history
- [ ] Team/shared teleport pools
- [ ] AI personality customization
- [ ] Economy integration (pay for extra TPs)
- [ ] Dimension-aware teleportation
- [ ] GUI for teleport history

---

**Note**: This mod sends chat messages to Google's Gemini API. Only use it if you're comfortable with that. Messages are not stored by the mod itself.
