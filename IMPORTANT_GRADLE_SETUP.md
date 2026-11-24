# Important: Gradle Wrapper JAR Missing

The `gradle-wrapper.jar` file is missing because it's a binary file that cannot be generated as text.

## Option 1: Auto-download (Recommended - Easiest)

When you first run `gradlew.bat build`, Gradle will automatically download the wrapper JAR for you. Just run:

```powershell
.\gradlew.bat build
```

If the wrapper jar is missing, Gradle will download it automatically on first run.

## Option 2: Generate it manually

If you have Gradle installed on your system:

```powershell
gradle wrapper --gradle-version 8.5
```

This will create the `gradle/wrapper/gradle-wrapper.jar` file.

## Option 3: Download manually

1. Download from: https://repo1.maven.org/maven2/org/gradle/gradle-wrapper/8.5/gradle-wrapper-8.5.jar
2. Rename to: `gradle-wrapper.jar`
3. Place in: `gradle/wrapper/gradle-wrapper.jar`

## After Getting the Wrapper

Once you have the wrapper jar (via any method above), you can build normally:

```powershell
.\gradlew.bat build
```

The compiled mod will be in `build/libs/gemini-tp-mod-1.0.0.jar`

---

**Note**: The Gradle wrapper is a small bootstrapper that downloads the actual Gradle distribution. It ensures everyone uses the same Gradle version regardless of what's installed on their system.
