# Button Format Test Guide

## Issue
Custom button format `["Yes"=ACCEPT]` was not working after leak prevention fixes.

## Root Causes
1. **Smart Quotes**: AI was generating smart quotes (`"` `"`) instead of straight quotes (`"`)
2. **Whitespace Normalization**: Potentially affecting button pattern matching
3. **Pattern Matching**: Regex wasn't flexible enough to handle quote variations

## Fixes Applied

### 1. Quote Normalization (GeminiChatHandler.java)
Added quote conversion BEFORE filtering:
```java
message = message.replace(""", "\"").replace(""", "\"")
                 .replace("'", "'").replace("'", "'");
```

This converts:
- `"` (smart left quote) → `"` (straight quote)
- `"` (smart right quote) → `"` (straight quote)  
- `'` (smart left single) → `'` (straight single)
- `'` (smart right single) → `'` (straight single)

### 2. Enhanced Pattern Matching (ClickableTextParser.java)
Updated regex to accept BOTH quote types:
```java
Pattern.compile("\\[[\"\\u201C]([^\"\\u201D]+)[\"\\u201D]=(ACCEPT|DENY)\\]");
```

Now matches:
- `["text"=ACCEPT]` (straight quotes)
- `["text"=ACCEPT]` (smart quotes)
- `["text"=ACCEPT]` (mixed quotes)

### 3. Processing Order
```
1. Normalize quotes   ← FIRST (ensures button patterns are preserved)
2. Filter internal text  ← SECOND (removes leaks)
3. Parse buttons      ← THIRD (creates clickable elements)
```

## Test Cases

### Test 1: Basic Custom Button
**AI says**: `Want to teleport? ["Yes please"=ACCEPT] ["No thanks"=DENY]`

**Expected**: Two clickable buttons appear:
- `[Yes please]` (green, clickable)
- `[No thanks]` (red, clickable)

### Test 2: Simple Button Format
**AI says**: `Ready to go? [ACCEPT] [DENY]`

**Expected**: Two clickable buttons appear:
- `[ACCEPT]` (green, clickable)
- `[DENY]` (red, clickable)

### Test 3: Smart Quotes
**AI says**: `Should I proceed? ["Sure"=ACCEPT] ["Nope"=DENY]`
(note: the quotes here are smart quotes)

**Expected**: Still works! Buttons appear correctly.

### Test 4: Mixed Content
**AI says**: `I'll send the request to Steve ["OK"=ACCEPT] or cancel it ["Cancel"=DENY]?`

**Expected**: Text flows naturally with two clickable buttons embedded.

## Debugging

If buttons still don't work, uncomment the debug logging in `ClickableTextParser.java`:

```java
// Change this (around line 71-74):
// if (message.contains("=") && message.contains("ACCEPT")) {
//     GeminiTpMod.LOGGER.info("Button pattern check - Message: {}", message);
//     GeminiTpMod.LOGGER.info("Buttons found: {}", buttons.size());
// }

// To this:
if (message.contains("=") && message.contains("ACCEPT")) {
    GeminiTpMod.LOGGER.info("Button pattern check - Message: {}", message);
    GeminiTpMod.LOGGER.info("Buttons found: {}", buttons.size());
}
```

Then check the server logs when buttons should appear.

## Verification Steps

1. **Rebuild the mod** with the updated code
2. **Deploy** to the server
3. **Test** by asking the AI:
   - "Can you give me options with custom buttons?"
   - "Teleport me to spawn with yes/no buttons"
4. **Verify** buttons are:
   - Visible
   - Clickable
   - Colored correctly (green for ACCEPT, red for DENY)
   - One-click only (disabled after clicking)

## Files Modified
- `GeminiChatHandler.java` - Added quote normalization
- `ClickableTextParser.java` - Enhanced pattern to accept smart quotes
