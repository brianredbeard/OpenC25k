# GitHub Actions CI/CD Documentation

This directory contains the GitHub Actions workflow configurations for the OpenC25k project.

## Overview

The CI/CD pipeline is designed to provide comprehensive automated testing, code quality checks, security scanning, and build automation - following best practices from the Rust ecosystem adapted for Android/Kotlin development.

## Workflows

### Main CI Pipeline (`ci.yml`)

**Triggers:**
- Push to `main` or `develop` branches
- Pull requests to `main` or `develop` branches
- Nightly at 2:00 AM UTC (to catch dependency regressions)

**Jobs:**

1. **Lint and Format Check** (~2 minutes)
   - Android Lint for code quality issues
   - Detekt for static analysis
   - KtLint for code formatting
   - Fails fast to block bad code early

2. **Test Matrix** (~15-20 minutes)
   - Tests across multiple API levels (31, 33, 34)
   - Tests both flavors (fdroid, googlePlay)
   - Unit tests with JVM/Robolectric
   - Instrumented tests on Android emulator
   - Uploads test reports as artifacts

3. **Security Audit** (~3 minutes)
   - OWASP Dependency Check for vulnerabilities
   - Hardcoded secret scanning
   - License compliance checking
   - Uploads security reports

4. **Build APKs** (~5 minutes)
   - Builds all 4 variants (fdroid/googlePlay × debug/release)
   - Generates build metadata (size, version)
   - Uploads APKs with 30-day retention

5. **Code Coverage** (~8 minutes)
   - Generates JaCoCo coverage reports
   - Uploads to Codecov (if configured)
   - Creates HTML/XML reports

6. **CI Summary** (~1 minute)
   - Aggregates all job results
   - Provides overall pass/fail status
   - Links to all artifacts and reports

**Total Runtime:** ~20-25 minutes (jobs run in parallel)

## Dependabot Configuration

Auto-updates are configured for:
- **GitHub Actions** - Weekly on Mondays
- **Gradle Dependencies** - Monthly on Mondays, grouped by category:
  - `androidx.*` packages
  - `kotlin` packages
  - Testing packages (junit, mockito, etc.)

## Local Development Commands

Run the same checks locally before pushing:

```bash
# Code formatting check
./gradlew ktlintCheck

# Auto-format code
./gradlew ktlintFormat

# Static analysis
./gradlew detekt

# Run all lints (both flavors)
./gradlew lintFdroidDebug lintGooglePlayDebug

# Run unit tests
./gradlew testFdroidDebugUnitTest

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedDebugAndroidTest

# Generate code coverage report
./gradlew jacocoTestReport

# Security vulnerability scan
./gradlew dependencyCheckAnalyze

# Build all APKs
./gradlew assembleDebug assembleRelease
```

## Configuration Files

- **`detekt.yml`** - Detekt static analysis rules
- **`owasp-suppression.xml`** - OWASP Dependency Check suppressions
- **`.github/dependabot.yml`** - Dependency update configuration
- **`app/build.gradle.kts`** - Gradle plugin configurations

## Gradle Plugins Added

1. **Detekt** (v1.23.4) - Static code analysis
   - Configuration: `detekt.yml`
   - Task: `./gradlew detekt`

2. **KtLint** (v12.1.0) - Code formatting
   - Android Kotlin style guide
   - Task: `./gradlew ktlintCheck`

3. **OWASP Dependency Check** (v9.0.9) - Security scanning
   - CVE database checking
   - Task: `./gradlew dependencyCheckAnalyze`

4. **JaCoCo** (v0.8.11) - Code coverage
   - XML/HTML reports
   - Task: `./gradlew jacocoTestReport`

## Artifacts

The CI pipeline uploads the following artifacts (retention varies):

- **Lint Reports** (30 days) - Android Lint, Detekt, KtLint results
- **Test Results** (30 days) - Unit and instrumented test reports
- **Security Reports** (30 days) - OWASP vulnerability scans
- **APK Builds** (30 days) - All build variants
- **Coverage Reports** (30 days) - JaCoCo code coverage

## Branch Protection

Recommended branch protection rules for `main`:

- ✅ Require status checks to pass before merging
  - `Lint and Format Check`
  - `Test Matrix`
  - `Security Audit`
- ✅ Require branches to be up to date before merging
- ✅ Require linear history
- ✅ Include administrators

## Troubleshooting

### CI Failing on Lint
```bash
# Run locally to see exact issues
./gradlew ktlintCheck detekt lintFdroidDebug
```

### CI Failing on Tests
```bash
# Run specific test suite
./gradlew testFdroidDebugUnitTest --tests "*.YourTestClass"

# Run with more details
./gradlew test --info
```

### Security Vulnerabilities Found
1. Review the OWASP report in CI artifacts
2. Update vulnerable dependencies if possible
3. If false positive, add to `owasp-suppression.xml`
4. Document suppression reason in XML comments

### Emulator Tests Timing Out
- Check emulator logs in CI output
- Verify tests don't have long timeouts
- Consider splitting large test suites

## Future Enhancements

Potential additions to the CI pipeline:

- [ ] Performance benchmarking
- [ ] Screenshot testing with differ
- [ ] APK size tracking over time
- [ ] Automatic PR labeling
- [ ] Deploy to F-Droid on release
- [ ] Crashlytics/Firebase integration
- [ ] Automated release notes generation

## Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Gradle Best Practices](https://docs.gradle.org/current/userguide/performance.html)
- [Android CI/CD Guide](https://developer.android.com/studio/projects/continuous-integration)
- [Detekt Documentation](https://detekt.dev/)
- [KtLint Style Guide](https://pinterest.github.io/ktlint/)
