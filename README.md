# Auto Profiles

An Android application for LineageOS that automatically switches system profiles based on user-defined rules and triggers.

## Features

- **Profile Management**: Create and manage multiple device profiles
- **Trigger System**: Automate profile switching with multiple trigger types:
  - Time-based triggers (specific times and days)
  - Cell tower-based triggers (location-based)
  - Calendar event triggers
  - Recurring day patterns (weekdays, weekends, custom)
- **Rule Management**: Define rules that link triggers to profiles
- **Background Monitoring**: Continuous trigger monitoring with minimal battery impact
- **Notifications**: Get notified when profiles are activated

## Architecture

### Core Components

- **ProfileManagerService**: Manages profile activation and system integration
- **TriggerMonitorService**: Monitors various triggers
- **Receivers**: Handle broadcast events for triggers
- **Database Layer**: Room-based persistence for profiles, rules, and triggers

### Data Models

- **Profile**: Represents a device profile configuration
- **Rule**: Links one or more triggers to a profile
- **Trigger**: Represents a condition that should activate a profile

## LineageOS Profile API

The app uses the LineageOS SDK `ProfileManager` API to:

```java
// Get instance
ProfileManager pm = ProfileManager.getInstance(context);

// Set active profile by UUID
pm.setActiveProfile(profileUuid);

// Get active profile
Profile activeProfile = pm.getActiveProfile();

// Manage profiles
pm.addProfile(profile);
pm.updateProfile(profile);
pm.removeProfile(profile);
```

### Key API Methods

- `setActiveProfile(UUID profileUuid)` - Activate a profile
- `getActiveProfile()` - Get currently active profile
- `addProfile(Profile profile)` - Add new profile
- `updateProfile(Profile profile)` - Update existing profile
- `removeProfile(Profile profile)` - Remove profile

### Permissions Required

Add to `AndroidManifest.xml`:

```xml
<uses-permission android:name="lineageos.permission.MANAGE_REMOTE_PREFERENCES" />
```

## Project Structure

```
AutoProfiles/
├── app/src/main/
│   ├── java/com/mine/autoprofiles/
│   │   ├── models/
│   │   │   ├── Profile.java
│   │   │   ├── Rule.java
│   │   │   └── Trigger.java
│   │   ├── database/
│   │   │   ├── AppDatabase.java
│   │   │   ├── ProfileDao.java
│   │   │   ├── RuleDao.java
│   │   │   └── TriggerDao.java
│   │   ├── services/
│   │   │   ├── ProfileManagerService.java
│   │   │   └─��� TriggerMonitorService.java
│   │   ├── receivers/
│   │   │   ├── BootCompletedReceiver.java
│   │   │   ├── CellTowerReceiver.java
│   │   │   └── ScheduleReceiver.java
│   │   └── ui/
│   │       └── MainActivity.java
│   └── res/
│       ├── layout/
│       ├── drawable/
│       └── values/
└── build.gradle
```

## Next Steps

1. **Implement UI Fragments**:
   - ProfileListFragment: Display and manage profiles
   - RuleListFragment: Display and manage rules
   - SettingsFragment: Configure app behavior

2. **Implement Trigger Engines**:
   - TimeBasedTriggerEngine
   - CellTowerTriggerEngine
   - CalendarTriggerEngine
   - RecurringTriggerEngine

3. **Implement Profile Integration**:
   - LineageOS ProfileManager API integration
   - Profile settings synchronization

4. **Implement Background Services**:
   - Continuous trigger monitoring
   - Battery-optimized task scheduling
   - Notification system

## Building

```bash
./gradlew build
./gradlew installDebug
```

## Testing

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## License

GNU General Public License v3.0
