# AI Function Call Leak Prevention

## Problem
The AI assistant sometimes leaks internal tracking information to users, such as:
- `[Called function: teleport_to_player]`
- `[System: Player teleport to X completed successfully]`
- `[Function X result: SUCCESS - message]`

This happens because the AI sees these bracketed messages in its conversation history and sometimes echoes them back to users.

## Root Causes

### 1. Conversation History Formatting
**File:** `ConversationHistory.java`
- The `formatHistoryForPrompt()` method adds bracketed tracking info for the AI's context
- This is necessary for the AI to understand what happened, but it must NEVER be repeated to users

### 2. System Completion Messages
**File:** `GeminiChatHandler.java` (line ~373)
- When a player teleport completes, a bracketed system message is added to history
- Example: `[INTERNAL: System: Player teleport to Steve completed successfully]`
- The AI needs this to know the teleport finished, but must not echo it

### 3. Function Call Tracking
**File:** `ConversationHistory.java`
- Tool calls and results are tracked with bracketed notation
- Example: `[INTERNAL: Called function: teleport_to_death with args: {...}]`
- Example: `[INTERNAL: Function teleport_to_death result: SUCCESS - Teleported to death location]`

## Solutions Implemented

### 1. Enhanced System Prompt Warnings
**File:** `SystemPromptBuilder.java`

Added explicit warnings with examples:
```
CRITICAL - Function Call Tracking & Communication:
- Your conversation history contains bracketed tracking info like '[Called function:]', '[System:]', '[Function X result:]'
- This bracketed information is INTERNAL CONTEXT ONLY - it's for your understanding, NOT for the player
- NEVER EVER mention, echo, repeat, or reference these brackets in your responses
- NEVER say things like:
  ❌ 'I see in the system notes...'
  ❌ 'The history shows...'
  ❌ 'According to the function result...'
  ❌ '[Called function: teleport_to_player]' (don't repeat this!)
  ❌ '[System: Player teleport completed]' (don't repeat this!)
- Instead, speak naturally based on what you learned:
  ✓ 'I sent the request to Steve!'
  ✓ 'You're all set, teleported!'
  ✓ 'Looks like you're out of death TPs for now.'
- The player should NEVER see any text in square brackets from you
```

### 2. Conversation History Header Warnings
**File:** `ConversationHistory.java`

Updated the history header to be more explicit:
```
=== CONVERSATION HISTORY (INTERNAL CONTEXT - NEVER SHOW BRACKETS TO PLAYER!) ===
⚠️ WARNING: All text in [brackets] below is INTERNAL TRACKING ONLY
⚠️ DO NOT echo, mention, or reference ANY bracketed text in your responses to the player
⚠️ The player should NEVER see square brackets in your messages
```

### 3. Prefixed Internal Messages
**File:** `ConversationHistory.java` & `GeminiChatHandler.java`

All internal tracking messages now start with `[INTERNAL:` to make them more obvious:
- `[INTERNAL: Called function: X]`
- `[INTERNAL: Function X result: SUCCESS]`
- `[INTERNAL: System: Player teleport to X completed successfully]`

Each line also has a reminder: `← DO NOT REPEAT THIS BRACKET TEXT TO PLAYER`

### 4. Specific Teleport State Warnings
**File:** `SystemPromptBuilder.java`

Added specific guidance about the teleport completion message:
```
- If you see '[INTERNAL: System: Player teleport to X completed successfully]' in history, THEN it's done
  ⚠️ BUT NEVER say this bracketed text to the player! Just say 'You're teleported!' or similar
```

## Edge Cases to Watch

### Edge Case 1: AI Mimicking Format
**Risk:** AI might see the bracketed format and think it's part of its response style
**Prevention:** Multiple warnings that brackets are INTERNAL ONLY and should NEVER appear in responses

### Edge Case 2: AI Referencing History
**Risk:** AI might say "I see in the system notes that..." or "According to the history..."
**Prevention:** Explicit examples of what NOT to say, with ❌ markers

### Edge Case 3: Function Result Echo
**Risk:** After calling a function, AI might repeat the exact result message
**Prevention:** Instructions to speak naturally based on the result, not echo it verbatim

### Edge Case 4: Completion Message Leak
**Risk:** The `[INTERNAL: System: Player teleport completed]` message is particularly prone to leaking
**Prevention:** 
- Prefixed with `[INTERNAL:`
- Specific warning in teleport state section
- Comment in code explaining it's internal only

### Edge Case 5: Multi-Turn Conversations
**Risk:** In long conversations, AI might forget the warnings and leak info
**Prevention:** Warnings are repeated in multiple sections of the prompt

## Testing Checklist

To verify the fixes work, test these scenarios:

1. **Death Teleport**
   - Ask AI to teleport to death
   - Verify response doesn't contain `[Called function:` or `[Function result:`
   - Should say something like "Alright, teleporting you to your death location!"

2. **Player Teleport Request**
   - Ask AI to teleport to another player
   - Verify response doesn't contain bracketed text
   - Should say something like "I sent the request to Steve!"

3. **Player Teleport Completion**
   - Complete a player teleport (both approvals)
   - Ask AI a follow-up question
   - Verify AI doesn't say `[System: Player teleport completed]`
   - Should naturally know the teleport is done

4. **Waypoint Operations**
   - Create, rename, delete waypoints
   - Verify no bracketed function call info leaks
   - Should speak naturally about the operations

5. **Error Cases**
   - Try teleporting while in combat
   - Try teleporting with no TPs remaining
   - Verify error messages don't leak internal tracking

6. **Long Conversations**
   - Have a 10+ message conversation
   - Verify AI doesn't start leaking brackets after many turns
   - History warnings should persist

## Monitoring

Watch for these patterns in AI responses (these indicate leaks):
- Any text in square brackets `[...]`
- Phrases like "I see in the system notes"
- Phrases like "According to the function result"
- Phrases like "The history shows"
- Direct echoing of function names like "teleport_to_player"
- Direct echoing of result messages

## Future Improvements

1. **Post-Processing Filter**: Add a filter that strips any `[...]` from AI responses before sending to players
2. **Response Validation**: Check AI responses for bracketed text and regenerate if found
3. **Separate Context**: Keep internal tracking completely separate from conversation history shown to AI
4. **Structured Responses**: Use a more structured format that separates internal state from user-facing messages
