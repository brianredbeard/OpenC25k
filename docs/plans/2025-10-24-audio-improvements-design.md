# Audio Improvements Design - Phase 2

**Date:** 2025-10-24
**Status:** Approved
**Priority:** High (List 1 F-Droid compatible features)

## Overview

Enhance the audio experience of OpenC25K with four key improvements that align with highly-rated features from existing C25K apps. All features are F-Droid compatible with zero anti-features.

## Goals

1. **Music ducking** - Automatically lower music volume during voice announcements
2. **Halfway notification** - Audio cue at workout midpoint for motivation
3. **Enhanced voice cues** - Richer TTS announcements with timing and encouragement
4. **Configurable sounds** - Let users customize notification sounds

## Current State Analysis

### Existing Components

**Beeper** (`app/src/main/java/se/wmuth/openc25k/both/Beeper.kt`)
- MediaPlayer-based audio playback
- Loads beep.mp3 from res/raw
- Uses USAGE_ALARM audio attribute
- Supports single and multiple beeps
- Volume configurable

**RunAnnouncer** (`app/src/main/java/se/wmuth/openc25k/track/RunAnnouncer.kt`)
- TextToSpeech-based announcements
- Announces interval changes ("Now walking")
- Announces completion
- Locale-aware with English fallback
- Has stub for time announcements (not currently used)

**RunTimer** (`app/src/main/java/se/wmuth/openc25k/track/RunTimer.kt`)
- Manages interval timing
- Tracks total elapsed time (secondsPassed)
- Tracks total duration (totSeconds)
- Provides callbacks: tick(), nextInterval(), finishRun()

**TrackActivity** (`app/src/main/java/se/wmuth/openc25k/TrackActivity.kt`)
- Orchestrates audio components
- Calls beeper on interval changes (1 beep walk, 2 beeps jog)
- Calls announcer for TTS
- No halfway notification
- No audio focus management

### Current Gaps

1. **No AudioFocus management** - Music plays at full volume during announcements
2. **No halfway notification** - Users don't get midpoint motivation
3. **Limited TTS content** - Only basic interval names, no timing or encouragement
4. **Single beep sound** - No user choice for notification sounds

## Detailed Design

### 1. Music Ducking via AudioFocus

**Component:** New `AudioFocusManager` class

**Location:** `app/src/main/java/se/wmuth/openc25k/audio/AudioFocusManager.kt`

**Architecture:**
```kotlin
class AudioFocusManager(context: Context) {
    private val audioManager: AudioManager
    private val focusRequest: AudioFocusRequest (API 26+) or legacy

    fun requestFocus(durationHint: Int): Boolean
    fun abandonFocus()
}
```

**Integration Points:**
- `Beeper.beep()` - Request focus before playing, abandon when done
- `RunAnnouncer.announce()` - Request focus before speaking, abandon when done

**Focus Type:** `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`
- Short interruption
- Other apps can continue at reduced volume
- Standard for notifications/announcements

**AudioAttributes Update:**
- Beeper: Change from USAGE_ALARM to USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
- More appropriate for exercise coaching
- Better behavior with Do Not Disturb

**Benefits:**
- Music automatically ducks during announcements
- Works with all music apps (Spotify, YouTube Music, etc.)
- Standard Android behavior users expect

### 2. Halfway Notification

**Component:** Extend `RunTimer`

**Implementation:**
```kotlin
// In RunTimer
private var halfwayAnnounced: Boolean = false
private val halfwayPoint: Int = totSeconds / 2

// In tick()
if (!halfwayAnnounced && secondsPassed >= halfwayPoint) {
    parent.onHalfway()
    halfwayAnnounced = true
}
```

**Callback Addition:**
```kotlin
interface RunTimerListener {
    fun finishRun()
    fun nextInterval()
    fun tick()
    fun onHalfway()  // NEW
}
```

**TrackActivity Implementation:**
```kotlin
override fun onHalfway() {
    announcer?.announce("You're halfway there! Keep it up!")
    beeper?.beep()  // Single celebratory beep
    // Optional: Show toast or update UI
}
```

**Edge Cases:**
- Only announce once per run
- Skip if run is very short (<2 minutes)
- Handle resume after pause correctly

**User Research Support:**
- "Halfway notification" consistently praised in reviews
- Provides motivation at critical psychological point
- Zero implementation complexity

### 3. Enhanced Voice Cues

**Component:** Expand `RunAnnouncer`

**New Announcement Methods:**
```kotlin
// Richer interval announcements
fun announceIntervalWithDuration(title: String, durationSeconds: Int)
// "Now walking. Ninety seconds."

fun announceIntervalProgress(remainingSeconds: Int)
// "Thirty seconds remaining"

fun announceMotivation(message: String)
// "Great job! Keep going!"

fun announceWorkoutStart(totalDuration: String)
// "Starting your workout. Total time: twenty minutes."
```

**Announcement Strategy:**

**At Interval Start:**
- Current: "Now walking"
- Enhanced: "Now walking. Ninety seconds."

**During Interval:**
- Halfway through interval: "Halfway through this interval"
- 30 seconds remaining: "Thirty seconds remaining"
- 10 seconds: "Ten seconds"

**At Milestones:**
- Halfway: "You're halfway there! Keep it up!"
- Completion: "Run completed! Great job! You did it!"

**Configuration:**
- Add DataStore preferences for announcement verbosity
- Options: Minimal, Standard, Verbose
- Minimal: Only interval changes
- Standard: Interval + duration
- Verbose: All announcements including countdowns

**TTS Optimization:**
- Use shorter phrases for better responsiveness
- Convert seconds to natural speech ("ninety" not "nine zero")
- Add slight delays between announcements to avoid overwhelming

### 4. Configurable Sounds

**Component:** Expand `Beeper` + new sound files

**Sound Type Enum:**
```kotlin
enum class SoundType(val fileName: String, val displayName: String) {
    BEEP("beep.mp3", "Beep"),
    CHIME("chime.mp3", "Chime"),
    BELL("bell.mp3", "Bell"),
    WHISTLE("whistle.mp3", "Whistle")
}
```

**Beeper Modifications:**
```kotlin
class Beeper(
    context: Context,
    volume: Float,
    soundType: SoundType = SoundType.BEEP
) {
    init {
        val resourceId = context.resources.getIdentifier(
            soundType.fileName.substringBefore("."),
            "raw",
            context.packageName
        )
        // Load selected sound file
    }
}
```

**DataStore Integration:**
```kotlin
// In SettingsRepository
val selectedSoundType: Flow<SoundType>
suspend fun setSoundType(soundType: SoundType)
```

**UI Addition:**
- Add "Sound Type" option to settings menu
- Show dialog with sound type options
- Play preview when user taps option
- Persist selection to DataStore

**Sound File Requirements:**
- Short duration (0.5-1 second)
- Clear and distinct
- Pleasant but not jarring
- Open license (CC0 or similar)

**Asset Sources:**
- freesound.org (CC0 sounds)
- OpenGameArt.org
- Or record custom sounds

**File Locations:**
```
app/src/main/res/raw/
├── beep.mp3      (existing)
├── chime.mp3     (new)
├── bell.mp3      (new)
└── whistle.mp3   (new)
```

## Technical Architecture

### New Directory Structure
```
app/src/main/java/se/wmuth/openc25k/
├── audio/
│   ├── AudioFocusManager.kt        (NEW)
│   └── SoundType.kt                (NEW)
├── both/
│   └── Beeper.kt                   (MODIFIED)
├── track/
│   ├── RunAnnouncer.kt             (MODIFIED)
│   └── RunTimer.kt                 (MODIFIED)
├── data/
│   └── repository/
│       └── SettingsRepository.kt   (MODIFIED - add sound type)
└── TrackActivity.kt                (MODIFIED)
```

### Data Flow

**Interval Change:**
1. RunTimer detects interval completion
2. Calls nextInterval() callback
3. TrackActivity:
   - Requests AudioFocus
   - Plays beep via Beeper (with selected sound)
   - Announces via RunAnnouncer (enhanced message)
   - Abandons AudioFocus when done
   - Updates UI

**Halfway Point:**
1. RunTimer detects halfway (secondsPassed >= totSeconds/2)
2. Calls onHalfway() callback (NEW)
3. TrackActivity:
   - Requests AudioFocus
   - Plays single beep
   - Announces "You're halfway there!"
   - Abandons AudioFocus
   - Optional UI update

**Time Announcements:**
1. RunTimer tick() detects milestone (30s, 10s remaining)
2. Calls tick() with milestone flag (OR new onMilestone callback)
3. TrackActivity:
   - Requests AudioFocus
   - Announces remaining time
   - Abandons AudioFocus

## Implementation Phases

### Phase 2.1: AudioFocus Foundation
1. Create AudioFocusManager
2. Integrate with Beeper
3. Integrate with RunAnnouncer
4. Test music ducking with various apps

### Phase 2.2: Halfway Notification
1. Extend RunTimer with halfway detection
2. Add onHalfway() callback
3. Implement TrackActivity handler
4. Add strings for halfway announcement
5. Test with various workout lengths

### Phase 2.3: Enhanced Voice Cues
1. Add announcement methods to RunAnnouncer
2. Create helper for duration formatting ("ninety seconds")
3. Integrate interval duration announcements
4. Add countdown announcements (30s, 10s)
5. Add motivational messages
6. Add verbosity preference to DataStore
7. Add settings UI for verbosity
8. Test TTS with various locales

### Phase 2.4: Configurable Sounds
1. Create SoundType enum
2. Find/create sound files (chime, bell, whistle)
3. Add sound files to res/raw/
4. Modify Beeper to accept SoundType
5. Add sound type preference to DataStore
6. Create sound selection dialog
7. Add preview functionality
8. Update MainActivity settings menu

## Testing Strategy

### Manual Testing
- Test music ducking with Spotify, YouTube Music
- Test halfway notification at various workout lengths
- Test enhanced announcements in different languages
- Test all sound types
- Test with TTS enabled/disabled
- Test with sound enabled/disabled

### Edge Cases
- Very short workouts (< 2 minutes) - skip halfway
- Paused workouts - don't re-announce halfway on resume
- TTS not available - graceful degradation
- Audio focus denied - still play but log warning
- Custom locale - fallback to English

### Accessibility
- Ensure TalkBack compatibility maintained
- Test with screen reader enabled
- Verify announcements don't conflict with TalkBack

## F-Droid Compatibility

All features are F-Droid compatible:
- ✅ No network connections
- ✅ No tracking or analytics
- ✅ All dependencies are FOSS (Android SDK only)
- ✅ Audio files will use open licenses
- ✅ No NonFreeNet, NonFreeDep, or Tracking anti-features

## Success Metrics

- Music ducking works with popular music apps
- Halfway notification provides motivation boost
- Enhanced announcements are clear and helpful
- Users can customize sounds to preference
- Zero new anti-features for F-Droid build
- No performance degradation
- Battery impact remains minimal

## Future Enhancements (Out of Scope)

- Custom coach voices (recorded audio packs)
- Background music during workouts
- Heart rate zone announcements (requires sensors)
- GPS pace announcements (requires GPS implementation)
- Playlist integration (would require third-party integration)

## References

- [Android AudioFocus Documentation](https://developer.android.com/media/optimize/audio-focus)
- [TextToSpeech Best Practices](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- User reviews highlighting halfway notifications: List 1 feature research
- Music ducking as "most praised feature" in C25K apps
