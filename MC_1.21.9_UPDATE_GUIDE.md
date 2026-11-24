# GeminiTP Minecraft 1.21.9 Update Guide

## Configuration Files - COMPLETED ✅
1. ✅ gradle.properties - Updated to MC 1.21.9
2. ✅ build.gradle - Updated Loom to 1.11-SNAPSHOT
3. ✅ gradle-wrapper.properties - Updated to Gradle 8.14
4. ✅ fabric.mod.json - Updated dependencies

## API Changes Required

### 1. ServerPlayerEntity.getWorld() → Use getCommandSource().getWorld()
**Files Affected:**
- DeathLocationTracker.java:17
- WaypointManager.java:39
- TeleportExecutor.java:31
- GeminiChatHandler.java:360
- LocationSaver.java:35
- TeleportConfirmation.java:25

**Change:**
```java
// OLD:
String dimension = player.getWorld().getRegistryKey().getValue().toString();

// NEW:
String dimension = player.getCommandSource().getWorld().getRegistryKey().getValue().toString();
```

### 2. ServerPlayerEntity.getPos() → Use new Vec3d(getX(), getY(), getZ())
**Files Affected:**
- DeathLocationTracker.java:16
- GeminiTpMod.java:75

**Change:**
```java
// OLD:
Vec3d pos = player.getPos();

// NEW:
Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
```

### 3. ClickEvent is Abstract → Use ClickEvent.RunCommand
**Files Affected:**
- FunctionExecutor.java:295, 304
- TeleportApprovalHandler.java:103, 112, 134, 143

**Change:**
```java
// OLD:
.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/command"))

// NEW:
.withClickEvent(new ClickEvent.RunCommand("/command"))
```

### 4. HoverEvent is Abstract → Use HoverEvent.ShowText
**Files Affected:**
- FunctionExecutor.java:296, 305
- TeleportApprovalHandler.java:104, 113, 135, 144

**Change:**
```java
// OLD:
.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("text")))

// NEW:
.withHoverEvent(new HoverEvent.ShowText(Text.literal("text")))
```

### 5. player.teleport() Signature Changed
**Files Affected:**
- TeleportExecutor.java:32, 36

**OLD Signature:**
```java
player.teleport(targetLevel, x, y, z, player.getYaw(), player.getPitch());
```

**NEW Signature (one of these):**
```java
// Option 1: With empty position flags set
player.teleport(targetLevel, x, y, z, java.util.Collections.emptySet(), player.getYaw(), player.getPitch(), false);

// Option 2: Using TeleportTarget
player.teleportTo(new TeleportTarget(targetLevel, new Vec3d(x, y, z), Vec3d.ZERO, player.getYaw(), player.getPitch(), TeleportTarget.NO_OP));
```

## Quick Fix Script
You can use find-replace in your IDE:

1. Find: `player.getWorld()`  
   Replace: `player.getCommandSource().getWorld()`

2. Find: `target.getWorld()`  
   Replace: `target.getCommandSource().getWorld()`

3. Find: `player.getPos()`  
   Replace: `new Vec3d(player.getX(), player.getY(), player.getZ())`

3. Find: `new ClickEvent(ClickEvent.Action.RUN_COMMAND,`  
   Replace: `new ClickEvent.RunCommand(`

4. Find: `new HoverEvent(HoverEvent.Action.SHOW_TEXT,`  
   Replace: `new HoverEvent.ShowText(`

5. For teleport(), manually update to:
   ```java
   player.teleport(targetLevel, x, y, z, java.util.Collections.emptySet(), player.getYaw(), player.getPitch(), false);
   ```

## File-by-File Checklist

- [ ] DeathLocationTracker.java - getPos(), getWorld()
- [ ] WaypointManager.java - getWorld()
- [ ] TeleportExecutor.java - getWorld(), teleport()
- [ ] GeminiChatHandler.java - getWorld()
- [ ] LocationSaver.java - getWorld()
- [ ] GeminiTpMod.java - getPos()
- [ ] TeleportConfirmation.java - getWorld()
- [ ] FunctionExecutor.java - ClickEvent, HoverEvent (4 changes)
- [ ] TeleportApprovalHandler.java - ClickEvent, HoverEvent (6 changes)

## Build Command
```bash
gradlew.bat clean build
```

## Verification
After fixing, the build should complete successfully and produce:
- bernard-ai-mod-1.0.0.jar

## Notes
- getWorld() was removed from ServerPlayerEntity in MC 1.21.9
- Use getServerWorld() instead which returns ServerWorld
- getPos() still exists on Entity but may need manual position building
- ClickEvent and HoverEvent are now abstract - use their inner classes
- teleport() method signature added Set<PositionFlag> and boolean parameters
