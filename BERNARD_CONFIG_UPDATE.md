# GeminiTP Configuration Update - Bernard Changes

**Date:** 2025-11-25  
**Author:** s3n (Sailo Otomo)

## Summary

Updated teleport and waypoint limits based on player usage data and feedback.

## Changes Made

### 1. Max Waypoint Count: 10 → 30
**Rationale:** Players have been hitting the waypoint limit frequently. Even admins have experienced this limitation. The increase to 30 waypoints provides more flexibility for all players.

**Files Modified:**
- `WaypointManager.java` - Line 17: `MAX_WAYPOINTS` constant
- `SystemPromptBuilder.java` - Lines 167, 247: Updated display text

### 2. Player TP Count: 25/h → 10/h
**Rationale:** Usage analytics show the average player only teleports about 1.85 times per hour. Even high-end players rarely exceed 8-10 teleports per hour. This reduction has minimal impact on actual gameplay while helping balance server resources.

**Files Modified:**
- `RateLimiter.java` - Line 19: `MAX_PLAYER_TELEPORTS_PER_HOUR` constant
- `SystemPromptBuilder.java` - Lines 165, 229: Updated display text

### 3. Waypoint TP Count: 10/h → 15/h
**Rationale:** There has been a huge surge in waypoint teleports over the past week. The average player now uses 6.42 waypoint teleports per hour, more than triple the average for Player TPs. To accommodate the increased max waypoint count and actual usage patterns, we are increasing the waypoint TP limit while reducing Player TPs.

**Files Modified:**
- `RateLimiter.java` - Line 20: `MAX_LOCATION_TELEPORTS_PER_HOUR` constant
- `SystemPromptBuilder.java` - Lines 166, 230: Updated display text

## Technical Details

### Configuration Constants Updated:
```java
// WaypointManager.java
private static final int MAX_WAYPOINTS = 30; // was 10

// RateLimiter.java
private static final int MAX_PLAYER_TELEPORTS_PER_HOUR = 10; // was 25
private static final int MAX_LOCATION_TELEPORTS_PER_HOUR = 15; // was 10
```

### AI System Prompt Updates:
The `SystemPromptBuilder.java` was updated in two sections:
1. **Function description section** - Updated the limits shown to Bernard (the AI)
2. **Player status display section** - Updated the limits shown in player statistics

## Build Status

✅ **Build Successful** - All changes compiled without errors.

## Next Steps

1. Deploy the updated mod to the server
2. Monitor usage patterns over the next week
3. Gather player feedback on the new limits
4. Adjust if necessary based on data and feedback

## Usage Statistics Reference

- **Player TP**: Average 1.85/hour, High-end: 8-10/hour
- **Waypoint TP**: Average 6.42/hour (3x Player TP usage)
- **Waypoint Slots**: Frequent limit hits with max of 10

---

*These changes align with player behavior while maintaining server balance and resource management.*
