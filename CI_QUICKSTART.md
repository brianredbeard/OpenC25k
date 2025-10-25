# CI/CD Quick Start Guide

## 🚀 Quick Commands

### Before Committing
```bash
# Run all code quality checks (what CI will run)
./gradlew ktlintCheck detekt lintFdroidDebug

# Auto-fix formatting issues
./gradlew ktlintFormat

# Run tests
./gradlew testFdroidDebugUnitTest
```

### Fix Common Issues
```bash
# Kotlin formatting violations
./gradlew ktlintFormat

# Detekt warnings
./gradlew detekt
# Review: app/build/reports/detekt/detekt.html

# Android Lint warnings
./gradlew lintFdroidDebug
# Review: app/build/reports/lint-results-fdroidDebug.html
```

### Security & Coverage
```bash
# Check for vulnerabilities (takes ~5 minutes first run)
./gradlew dependencyCheckAnalyze
# Review: build/reports/dependency-check-report.html

# Generate code coverage
./gradlew testFdroidDebugUnitTest jacocoTestReport
# Review: app/build/reports/jacoco/testFdroidDebugUnitTest/html/index.html
```

## 📊 CI Pipeline Jobs

| Job | Purpose | Runtime | Artifacts |
|-----|---------|---------|-----------|
| **Lint** | Code quality checks | ~2 min | Lint reports |
| **Test** | Unit + instrumented tests | ~15-20 min | Test results |
| **Security** | Vulnerability scanning | ~3 min | Security reports |
| **Build** | APK generation | ~5 min | APK files |
| **Coverage** | Code coverage analysis | ~8 min | Coverage reports |
| **Summary** | Aggregate results | ~1 min | Status table |

## 🔧 Plugin Tasks

### Detekt (Static Analysis)
```bash
./gradlew detekt                 # Run analysis
./gradlew detektBaseline         # Create baseline to suppress existing issues
```

### KtLint (Formatting)
```bash
./gradlew ktlintCheck            # Check formatting
./gradlew ktlintFormat           # Auto-fix formatting
./gradlew ktlintGenerateBaseline # Create baseline
```

### OWASP (Security)
```bash
./gradlew dependencyCheckAnalyze # Scan dependencies
./gradlew dependencyCheckUpdate  # Update CVE database
./gradlew dependencyCheckPurge   # Clear cache
```

### JaCoCo (Coverage)
```bash
./gradlew jacocoTestReport       # Generate coverage
```

## 🎯 Workflow Triggers

The CI pipeline runs automatically on:
- ✅ Push to `main` or `develop` branches
- ✅ Pull requests to `main` or `develop`
- ✅ Nightly at 2 AM UTC (dependency regression testing)

## 📁 Generated Artifacts

After CI runs, download from Actions tab:
- `lint-reports` - All linting results (HTML)
- `test-results-api{X}-{flavor}` - Test reports per configuration
- `security-reports` - OWASP vulnerability scans
- `apk-{flavor}-{buildType}` - Built APK files
- `coverage-report` - Code coverage HTML/XML

## 🔍 Troubleshooting

### "KtLint formatting errors"
```bash
./gradlew ktlintFormat
git add -u
git commit --amend --no-edit
```

### "Detekt issues found"
Option 1: Fix the code issues
```bash
./gradlew detekt
# Review HTML report and fix issues
```

Option 2: Create baseline (for existing code)
```bash
./gradlew detektBaseline
git add detekt-baseline.xml
```

### "Tests failing in CI but pass locally"
- Check API level differences
- Review emulator logs in CI output
- Test flakiness - consider adding `@FlakyTest` annotation

### "Security vulnerabilities found"
1. Download security-reports artifact
2. Review dependency-check-report.html
3. Update vulnerable dependencies OR suppress false positives in `owasp-suppression.xml`

### "Build timeout"
- Check Gradle daemon not disabled
- Verify cache is working
- Consider splitting large test suites

## 🎨 Status Badges

Add to your README.md:
```markdown
![CI](https://github.com/YOUR_USERNAME/OpenC25k/workflows/CI/badge.svg)
[![codecov](https://codecov.io/gh/YOUR_USERNAME/OpenC25k/branch/main/graph/badge.svg)](https://codecov.io/gh/YOUR_USERNAME/OpenC25k)
```

## 📚 Configuration Files

| File | Purpose |
|------|---------|
| `.github/workflows/ci.yml` | Main CI pipeline definition |
| `.github/dependabot.yml` | Automated dependency updates |
| `detekt.yml` | Detekt rules configuration |
| `owasp-suppression.xml` | Security scan suppressions |
| `app/build.gradle.kts` | Gradle plugins & tasks |

## 🔐 Required Secrets (Optional)

For full functionality, add these GitHub secrets:
- `CODECOV_TOKEN` - For code coverage reporting (get from codecov.io)

## ⚙️ Branch Protection

Recommended settings for `main` branch:
1. Go to Settings → Branches → Add rule
2. Pattern: `main`
3. Check: ✅ Require status checks to pass
4. Select: `Lint and Format Check`, `Test`, `Security Audit`
5. Check: ✅ Require branches to be up to date
6. Save

## 📞 Getting Help

- Check `.github/README.md` for detailed documentation
- Review specific job logs in Actions tab
- Look for `::error::` or `::warning::` in logs for specific issues
