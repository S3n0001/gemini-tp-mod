# 🚀 Quick Start - Get Running in 5 Minutes!

## Step 1: Get Gemini API Key (2 minutes)
1. Visit: https://ai.google.dev
2. Click "Get API Key" → Sign in → Create key
3. Copy the key

## Step 2: Set Environment Variable (1 minute)

**Windows - Choose ONE method:**

**Method A - GUI (Recommended):**
- Press `Win + R`, type `sysdm.cpl`, press Enter
- Advanced → Environment Variables → User Variables → New
- Name: `GEMINI_API_KEY`
- Value: (paste your key)
- OK all dialogs → **Restart computer**

**Method B - PowerShell (Quick):**
```powershell
[System.Environment]::SetEnvironmentVariable('GEMINI_API_KEY', 'YOUR_KEY_HERE', 'User')
```
Then **restart PowerShell**.

## Step 3: Build Mod (2 minutes)

Open PowerShell in this folder:
```powershell
cd "c:\Users\Sailo Otomo\Downloads\AI"
.\gradlew.bat build
```

If you get an error about gradle-wrapper.jar, just run it again - it will auto-download.

Output: `build/libs/gemini-tp-mod-1.0.0.jar` ✓

## Step 4: Install Mod

1. Install Fabric Loader: https://fabricmc.net/use/installer/
   - Select Minecraft 1.20.4
   - Click Install

2. Download Fabric API: https://modrinth.com/mod/fabric-api
   - Get version for 1.20.4

3. Copy JARs to `.minecraft/mods/`:
   - `fabric-api-xxx.jar`
   - `gemini-tp-mod-1.0.0.jar`

4. Launch Minecraft with Fabric profile

## Step 5: Test It! 🎮

In-game:
1. Die somewhere
2. Respawn
3. Type: `Hey AI, can you teleport me to my death?`
4. Follow the AI's conversation!

## Example Conversation

```
You: Hey I died, can I get tped to my death?
AI: Sure! But I have to use one of your daily 5 TPs per hour, is that okay?
You: Sure!
AI: [Teleports you] ✓ Done! You have 4 teleports remaining this hour.
```

## Troubleshooting

**"GEMINI_API_KEY not set" error?**
→ Restart your computer after setting the variable

**AI doesn't respond?**
→ Include "hey" or "ai" in your message
→ Check server logs for errors

**Build fails?**
→ Make sure Java 17+ is installed: `java -version`
→ Run: `.\gradlew.bat clean build`

## That's It! 🎉

For detailed docs, see `README.md`

---

**Pro Tip**: The AI is conversational! Try asking:
- "How many teleports do I have left?"
- "Can you help me get back to where I died?"
- "I need a TP to my death location please"
