# NexusChat Release Configuration

## Keystore Generation
```bash
keytool -genkey -v \
  -keystore nexuschat.keystore \
  -alias nexuschat \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=NexusChat, OU=Dev, O=NexusChat, L=Unknown, ST=Unknown, C=US"
```

## Release Build Command
```bash
./gradlew assembleRelease \
  -PKEYSTORE_PATH=nexuschat.keystore \
  -PKEYSTORE_PASS=$KEYSTORE_PASS \
  -PKEY_ALIAS=nexuschat \
  -PKEY_PASS=$KEY_PASS
```

## ProGuard Verification
```bash
# Check that critical classes are not obfuscated
./gradlew lintRelease
./gradlew check
```

## Release Checklist
- [ ] Update versionCode and versionName in app/build.gradle
- [ ] Update CHANGELOG.md
- [ ] Generate keystore (store securely!)
- [ ] Run full test suite: `./gradlew testDebugUnitTest connectedAndroidTest`
- [ ] Run lint: `./gradlew lintRelease`
- [ ] Build release APK: `./gradlew assembleRelease`
- [ ] Test APK on device/emulator
- [ ] Generate SHA256 checksum
- [ ] Create GitHub Release with artifacts
- [ ] Publish to F-Droid (optional)
- [ ] Announce release

## Signing Config (app/build.gradle)
```groovy
signingConfigs {
    release {
        storeFile     file(project.findProperty("KEYSTORE_PATH") ?: "nexuschat.keystore")
        storePassword project.findProperty("KEYSTORE_PASS") ?: ""
        keyAlias      project.findProperty("KEY_ALIAS")     ?: "nexuschat"
        keyPassword   project.findProperty("KEY_PASS")      ?: ""
    }
}
```

## APK Output Locations
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## APK Size Limits
- Debug: ~70 MB (includes debug symbols)
- Release: ~45 MB (minified, stripped)

## F-Droid Submission
```yaml
# fdroid/metadata.yml
Categories:
  - Connectivity
  - Security
  - System
License: GPL-3.0-only
WebSite: https://github.com/nexuschat/nexuschat
SourceCode: https://github.com/nexuschat/nexuschat
IssueTracker: https://github.com/nexuschat/nexuschat/issues
```

## Keystore Backup (CRITICAL!)
```bash
# Backup keystore to multiple secure locations
cp nexuschat.keystore /secure/backup/location/
cp nexuschat.keystore /another/secure/location/

# Store passwords in password manager
# Document: alias=nexuschat, key algorithm=RSA, size=4096
```

## Emergency Recovery
If keystore is lost:
1. App updates will be impossible
2. Users must uninstall and reinstall
3. All existing data encrypted with old keys becomes inaccessible
4. MUST generate new keys and rebuild trust network