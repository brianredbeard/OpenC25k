# Changelog

All notable changes to OpenC25k will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Comprehensive GitHub Actions CI/CD pipeline with 6 automated jobs
  - Lint and format checking (KtLint, Detekt, Android Lint)
  - Test matrix across 3 API levels (31, 33, 34) and 2 flavors
  - Security scanning with OWASP Dependency Check
  - Automated APK builds for all 4 variants
  - Code coverage reporting with JaCoCo
  - CI summary dashboard
- Dependabot configuration for automated dependency updates
  - Weekly GitHub Actions updates
  - Monthly Gradle dependency updates with grouping
- Static analysis with Detekt for Kotlin code quality
- Code formatting enforcement with KtLint
- Security vulnerability scanning with OWASP Dependency Check
- Code coverage tracking with JaCoCo
- Comprehensive CI/CD documentation
  - `.github/README.md` - Detailed workflow documentation
  - `CI_QUICKSTART.md` - Quick reference guide
- Nightly CI builds to catch dependency regressions
- Localization support for 9 additional languages (63 strings each, 567 total)
  - Mandarin Chinese (Simplified)
  - Spanish
  - Portuguese
  - French
  - German
  - Russian
  - Japanese
  - Indonesian
  - Arabic (RTL support)
- Progress tracking header on main screen
  - Current week and day display
  - Total runs completed counter
  - Last run date with relative time
- Quick resume FAB to start recommended next run
- Active run banner when service is running
  - Shows current run name
  - Displays current interval and remaining time
  - Tap to return to active run
- Run completion dialog with statistics and encouragement
  - Total time and distance
  - Average pace
  - Calories burned estimate
  - Contextual motivational messages
- Phone call auto-pause feature
  - Automatically pauses run when phone call detected
  - Supports both legacy (API < 31) and modern (API 31+) telephony APIs
  - Requires READ_PHONE_STATE permission (optional)
- Comprehensive test coverage (315+ tests)
  - Unit tests with Robolectric
  - Instrumented tests on Android
  - Tests for all new features

### Changed
- Upgraded build tools and dependencies
  - Android Gradle Plugin to 8.13.0
  - Kotlin to 2.1.0
  - Gradle to 9.1.0
- Improved notification handling with audio focus awareness
- Enhanced progress tracking with timestamp recording
- Refactored run tracking to use foreground service
- Improved audio announcement system with RunAnnouncer
- Updated string resources to support internationalization
  - Progress header strings with placeholders
  - Banner strings with time formatting
  - Sound type descriptions

### Fixed
- All Android Lint warnings in MainActivity
  - Removed redundant TextView qualifiers
  - Fixed RecyclerView efficiency (notifyItemChanged vs notifyDataSetChanged)
  - Fixed string concatenation in setText (using string resources)
  - Documented intentional notifyDataSetChanged usage with @SuppressLint
- Deprecated PhoneStateListener warnings
  - Added @file:Suppress("DEPRECATION") for backward compatibility
  - Maintains support for Android < 12 while using modern APIs
- Detekt configuration issues
  - Fixed JVM target mismatch (set to 11 to match Kotlin)
  - Updated deprecated rule names (ComplexMethod → CyclomaticComplexMethod)
  - Disabled compiler-enforced rules (DuplicateCaseInWhenExpression, etc.)
  - Fixed excludedFunctions format (string → YAML array)

### Developer Experience
- Added pre-commit command reference: `./gradlew ktlintCheck detekt lintFdroidDebug testFdroidDebugUnitTest`
- Automated code formatting: `./gradlew ktlintFormat`
- Security scanning: `./gradlew dependencyCheckAnalyze`
- Coverage reports: `./gradlew jacocoTestReport`
- Comprehensive troubleshooting documentation

## [1.0.0] - 2025-10-25

### Added
- Initial release of OpenC25k
- Couch to 5K training program (9 weeks, 27 workouts)
- Two product flavors: F-Droid and Google Play
- Customizable audio cues
  - Multiple sound types (beep, bells, chimes, whistles, silent)
  - Volume control
  - Text-to-speech announcements
  - Vibration support
- Run tracking with intervals
  - Visual progress indicators
  - Real-time countdown timers
  - Halfway announcements
- Progress persistence with DataStore
- Audio focus management
- Swedish localization (in addition to English)

[Unreleased]: https://github.com/wmuth/OpenC25k/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/wmuth/OpenC25k/releases/tag/v1.0.0
