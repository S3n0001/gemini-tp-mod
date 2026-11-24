# Quick Setup Guide

## 1. Get Gemini API Key

1. Go to https://ai.google.dev
2. Click "Get API Key"
3. Sign in and create an API key
4. **Copy the API key** (you'll need it in the next step)

## 2. Set Environment Variable

### Windows (Easiest Method)

1. Press `Windows + R`
2. Type `sysdm.cpl` and press Enter
3. Go to "Advanced" tab → "Environment Variables"
4. Under "User variables", click "New"
5. Variable name: `GEMINI_API_KEY`
6. Variable value: (paste your API key)
7. Click OK on all dialogs
8. **Restart your computer** (or at least restart any terminals/IDEs)

### Windows (PowerShell - Quick Method)

```powershell
[System.Environment]::SetEnvironmentVariable('GEMINI_API_KEY', 'YOUR_API_KEY_HERE', 'User')
```

Then restart PowerShell/terminal.

## 3. Build the Mod

Open PowerShell in the mod directory and run:

```powershell
.\gradlew.bat build
```

The mod will be in: `build/libs/gemini-tp-mod-1.0.0.jar`

## 4. Install to Minecraft

1. **Install Fabric Loader**:
   - Download from: https://fabricmc.net/use/installer/
   - Run installer, select Minecraft 1.20.4, click Install

2. **Download Fabric API**:
   - Download from: https://modrinth.com/mod/fabric-api/versions
   - Get version for Minecraft 1.20.4

3. **Copy Mods**:
   - Open `.minecraft/mods` folder
   - Copy `fabric-api-x.x.x.jar` there
   - Copy `gemini-tp-mod-1.0.0.jar` there

4. **Launch Minecraft**:
   - Open Minecraft Launcher
   - Select "Fabric" profile
   - Click Play

## 5. Test It!

1. Create a new world
2. Die somewhere (jump off a cliff, fight a mob, etc.)
3. Respawn
4. Type in chat: `Hey I died, can you teleport me back?`
5. The AI should respond!

## Troubleshooting

**API Key Not Found Error?**
- Did you restart your terminal/IDE after setting the environment variable?
- Try setting it via System Properties (Method 1 above)
- Restart your computer

**Build Errors?**
- Make sure you have Java 17 or higher: `java -version`
- Try: `.\gradlew.bat clean build`

**AI Not Responding?**
- Check server logs for errors
- Make sure you said "hey" or "ai" in your message
- Verify your API key is correct

**Can't Build - Gradle Issues?**
- Make sure you have internet connection (Gradle needs to download dependencies)
- Try deleting `.gradle` folder and rebuilding

## Need Help?

Check the full README.md for detailed information!
