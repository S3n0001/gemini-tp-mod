# Gemini TP Mod - Features Summary

## Overview
This Minecraft Fabric mod integrates an AI assistant (Zuriago) that helps players with teleportation while enforcing combat restrictions and rate limits.

## Core Features Implemented

### 1. Combat Tag System
**Location**: `CombatTagManager.java`

When a player takes any damage, they receive a combat tag that prevents teleportation:
- **Player damage**: 60 seconds combat tag
- **Mob damage**: 15 seconds combat tag  
- **Environment damage**: 5 seconds combat tag

**Restrictions**:
- Players in combat CANNOT use any teleport
- Players in combat CANNOT approve teleport requests
- Players CANNOT teleport to someone who is in combat
- Combat tags are cleared on death

### 2. Death Teleport System
**Limit**: 5 death TPs per hour

**How it works**:
- When a player dies, their death location is automatically recorded
- Player can request to teleport back to their death location via AI
- Once used, that death location is consumed and cannot be reused
- Player must die again to get a new death TP

**Commands**:
- `/ai tp me to my death` or `@ai tp me to my death`
- `/tpconfirm yes` to confirm the teleport
- `/tpconfirm no` to cancel

**Restrictions**:
- Cannot be used while in combat
- Can only be used once per death

### 3. Player Teleport System
**Limit**: 20 player TPs per hour

**How it works**:
- Player A requests to teleport to Player B via AI
- Player B receives a notification with accept/deny options
- Multiple requests can be queued simultaneously
- Requests expire after 15 minutes if not responded to
- Each request is independent and tracked separately

**Commands**:
- Requester: `/ai tp me to [PlayerName]` or `@ai tp me to [PlayerName]`
- Target: `/tpapprove accept` to approve the oldest request
- Target: `/tpapprove deny` to deny the oldest request
- Requester: `/tpconfirm yes` after approval to execute teleport

**Restrictions**:
- Cannot be used if requester is in combat
- Cannot be used if target player is in combat
- Target cannot approve requests while in combat
- Requests are processed in queue order (oldest first)

**Queue System**:
- Player can have multiple outgoing requests to different players
- Each target player has their own queue of incoming requests
- No "No pending teleport request" errors when handling multiple requests
- Queue position is shown to both requester and target

### 4. Special Teleport System
**Limit**: 3 special TPs per hour

**How it works**:
- Player can record their current location via AI (FREE - no TP cost)
- Only one location can be saved at a time
- New recording overwrites the previous saved location
- Player can then teleport to their saved location

**Commands**:
- Recording: `/ai record my current location` or `@ai can you record my location?`
- Teleporting: `/ai tp me to my saved location` or `@ai special tp`
- Confirmation: `/tpconfirm yes` to execute teleport

**AI Confirmation Flow**:
1. Player: "Can you record my current location?"
2. AI: "Sure, I can do that. It will record at X, Y, Z in dimension [name], overriding your previous location of X, Y, Z in [dimension]. Is that fine?"
3. Player: "Yes that's fine"
4. AI: "Alright, that's done!"

**Restrictions**:
- Recording is FREE and can be done anytime (even in combat)
- Teleporting requires not being in combat
- Only one location can be saved per player

### 5. AI System Prompt
**Location**: `GeminiChatHandler.java` - `buildSystemPrompt()` method

The AI has access to:
- **All players on the server** with their names
- **Combat status** of each player (in combat or not, time remaining, cause)
- **Current player's teleport usage** (remaining TPs for each type)
- **Death location status** (available, used, or none)
- **Saved location status** (coordinates and dimension, or none)
- **Tool descriptions** with detailed rules and restrictions

The AI knows:
- How to use each teleport tool (DEATH_TP, PLAYER_TP, SPECIAL_TP, RECORD_LOCATION)
- When to deny requests (combat, no TPs remaining, death location already used)
- How to format responses in JSON for the system to parse
- How to handle edge cases (multiple requests, expired requests, etc.)

### 6. Rate Limiting System
**Location**: `RateLimiter.java`

Tracks teleport usage per player with hourly limits:
- Death TPs: 5 per hour
- Player TPs: 20 per hour
- Special TPs: 3 per hour

**Features**:
- Automatic hourly reset
- Persistent storage (survives server restarts)
- Per-player tracking using UUID
- Saved to `config/gemini-tp-data.json`

### 7. Data Persistence
All player data is saved and persists across server restarts:
- **Teleport usage**: `config/gemini-tp-data.json`
- **Saved locations**: `config/gemini-saved-locations.json`
- Death locations are tracked in memory (reset on server restart)

## Commands Summary

### Player Commands
- `/ai [message]` - Private AI communication (doesn't show in chat)
- `@ai [message]` - Public AI communication (shows in chat)
- `/tpconfirm yes` - Confirm a pending teleport
- `/tpconfirm no` - Cancel a pending teleport
- `/tpapprove accept` - Accept the oldest teleport request
- `/tpapprove deny` - Deny the oldest teleport request

### Example Usage
```
/ai tp me to my death
/ai tp me to Steve
/ai record my current location
/ai tp me to my saved location
@ai how many teleports do I have left?
```

## Technical Implementation

### Event Handlers
- **Damage Event**: Tags players in combat when they take damage
- **Death Event**: Records death location and clears combat tag
- **Chat Event**: Processes @ai messages
- **Server Lifecycle**: Initializes/shuts down systems

### Teleport Confirmation Flow
1. AI receives request and validates (combat, limits, etc.)
2. System creates pending teleport/request
3. Player confirms with `/tpconfirm yes` or target approves with `/tpapprove accept`
4. System validates again (combat status may have changed)
5. Teleport executes and usage is recorded

### Multi-Request Handling
The system properly handles multiple simultaneous requests:
- Each request is stored with unique requester-target pair
- Requests are processed independently
- No conflicts when accepting multiple requests
- Proper cleanup of expired requests (15 minutes)

## Configuration
- API Key: Hardcoded in `GeminiChatHandler.java` (line 69)
- API Endpoint: Gemini Flash Lite Latest
- Timeout: 30 seconds for API calls
- Thread Pool: 4 threads for async processing

## Files Modified/Created
- `GeminiTpMod.java` - Main mod initialization
- `GeminiChatHandler.java` - AI integration and system prompt
- `CombatTagManager.java` - Combat tag tracking
- `DeathLocationTracker.java` - Death location tracking
- `LocationSaver.java` - Saved location management
- `TeleportConfirmation.java` - Pending teleport confirmations
- `TeleportRequest.java` - Player-to-player request queue
- `RateLimiter.java` - Hourly teleport limits
- `TeleportCommand.java` - Command registration

## Notes
- Creative and spectator mode players are not tagged in combat
- All teleports require confirmation to prevent accidental usage
- The AI maintains a formal, direct tone as specified
- System is fully asynchronous to prevent server lag
