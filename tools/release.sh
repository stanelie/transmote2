#!/bin/bash
#
# Cut a Transmote release: bump both platforms, build the Android APK, archive
# and upload the iOS build, hand it to TestFlight, tag, and publish the GitHub
# release.
#
#   tools/release.sh 1.9 --notes notes.md
#   tools/release.sh 1.9 --notes notes.md --dry-run
#
# Steps can be skipped to resume a half-finished release:
#   --skip-bump --skip-android --skip-ios --skip-testflight --skip-github
#
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$PWD
CONF=tools/release.conf
PLIST=iosApp/iosApp/Info.plist
GRADLE=app/build.gradle.kts
WORK=${TMPDIR:-/tmp}/transmote-release
BUILT_APK=app/build/outputs/apk/debug/app-debug.apk

VERSION=${1:-}
shift || true
NOTES=""; DRY=0
SKIP_BUMP=0; SKIP_ANDROID=0; SKIP_IOS=0; SKIP_TF=0; SKIP_GH=0
while [ $# -gt 0 ]; do
  case $1 in
    --notes) NOTES=$2; shift 2 ;;
    --dry-run) DRY=1; shift ;;
    --skip-bump) SKIP_BUMP=1; shift ;;
    --skip-android) SKIP_ANDROID=1; shift ;;
    --skip-ios) SKIP_IOS=1; shift ;;
    --skip-testflight) SKIP_TF=1; shift ;;
    --skip-github) SKIP_GH=1; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

[ -n "$VERSION" ] || { echo "usage: tools/release.sh <version> --notes <file>" >&2; exit 2; }
[ -f "$CONF" ] || { echo "missing $CONF (see $CONF.example)" >&2; exit 2; }
set -a; . "./$CONF"; set +a

# What gets attached to the GitHub release: app name and version in the file name.
RELEASE_APK=app/build/outputs/apk/debug/Transmote-$VERSION.apk

say() { printf '\n== %s\n' "$*"; }
run() { if [ "$DRY" = 1 ]; then echo "+ $*"; else "$@"; fi; }

# The build number is whatever the current one is, plus one. Apple rejects a
# duplicate, and Android needs a monotonic versionCode, so they stay in step.
CURRENT_CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE" | head -1)
BUILD=$((CURRENT_CODE + 1))
say "releasing $VERSION as build $BUILD (was code $CURRENT_CODE)"

if [ "$SKIP_BUMP" = 0 ]; then
  [ -z "$(git status --porcelain)" ] || { echo "working tree is dirty" >&2; exit 1; }
  say "bumping $GRADLE and $PLIST"
  if [ "$DRY" = 0 ]; then
    sed -i '' "s/versionCode = $CURRENT_CODE/versionCode = $BUILD/" "$GRADLE"
    sed -i '' "s/versionName = \".*\"/versionName = \"$VERSION\"/" "$GRADLE"
    # Not PlistBuddy: it rewrites the file and silently drops the XML
    # comments that explain why some of these keys are here.
    python3 -I tools/setplist.py "$PLIST" CFBundleShortVersionString "$VERSION"
    python3 -I tools/setplist.py "$PLIST" CFBundleVersion "$BUILD"
    plutil -lint "$PLIST" >/dev/null
    git add "$GRADLE" "$PLIST"
    git commit -qm "Bump to $VERSION (versionCode $BUILD) for both platforms"
  fi
fi

if [ "$SKIP_ANDROID" = 0 ]; then
  say "building the Android APK"
  run ./gradlew :app:assembleDebug
  APK=$BUILT_APK
  if [ "$DRY" = 0 ]; then
    # Gradle reports success for an up-to-date task, so confirm the APK on
    # disk is really the version we just asked for.
    AAPT=$(ls -d "$HOME"/Library/Android/sdk/build-tools/*/ | tail -1)aapt2
    BADGING=$("$AAPT" dump badging "$APK")
    echo "$BADGING" | head -1
    case $BADGING in
      *"versionCode='$BUILD'"*) ;;
      *) echo "APK is not build $BUILD" >&2; exit 1 ;;
    esac
    cp "$APK" "$RELEASE_APK"
    echo "release asset: $RELEASE_APK"
  fi
fi

if [ "$SKIP_IOS" = 0 ]; then
  AUTH=(-allowProvisioningUpdates
        -authenticationKeyPath "$HOME/.appstoreconnect/private_keys/AuthKey_$ASC_KEY_ID.p8"
        -authenticationKeyID "$ASC_KEY_ID"
        -authenticationKeyIssuerID "$ASC_ISSUER_ID")
  run mkdir -p "$WORK"
  run rm -rf "$WORK/Transmote.xcarchive" "$WORK/export"

  say "archiving (the Release Kotlin framework link takes a while)"
  run xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -configuration Release -destination 'generic/platform=iOS' \
    -archivePath "$WORK/Transmote.xcarchive" archive "${AUTH[@]}"

  say "exporting"
  run xcodebuild -exportArchive -archivePath "$WORK/Transmote.xcarchive" \
    -exportOptionsPlist iosApp/ExportOptions.plist -exportPath "$WORK/export" \
    "${AUTH[@]}"

  say "uploading to App Store Connect"
  if [ "$DRY" = 0 ]; then
    # altool exits 0 even when the upload fails, so the log is the only
    # trustworthy signal.
    set +e
    xcrun altool --upload-app --type ios --file "$WORK/export/Transmote.ipa" \
      --apiKey "$ASC_KEY_ID" --apiIssuer "$ASC_ISSUER_ID" 2>&1 | tee "$WORK/upload.log"
    set -e
    grep -q "UPLOAD SUCCEEDED" "$WORK/upload.log" \
      || { echo "upload failed - see $WORK/upload.log" >&2; exit 1; }
  else
    echo "+ xcrun altool --upload-app ..."
  fi
fi

if [ "$SKIP_TF" = 0 ]; then
  say "waiting for Apple to finish processing build $BUILD"
  run python3 -I tools/asc.py wait-for-build --build "$BUILD"
  say "handing build $BUILD to the TestFlight group"
  run python3 -I tools/asc.py attach --build "$BUILD"
fi

if [ "$SKIP_GH" = 0 ]; then
  say "tagging and publishing v$VERSION"
  [ -n "$NOTES" ] || { echo "--notes <file> is required to publish" >&2; exit 2; }
  # When resuming with --skip-android the named copy may not exist yet.
  if [ "$DRY" = 0 ] && [ ! -f "$RELEASE_APK" ]; then
    AAPT=$(ls -d "$HOME"/Library/Android/sdk/build-tools/*/ | tail -1)aapt2
    case $("$AAPT" dump badging "$BUILT_APK") in
      *"versionName='$VERSION'"*) cp "$BUILT_APK" "$RELEASE_APK" ;;
      *) echo "$BUILT_APK is not version $VERSION" >&2; exit 1 ;;
    esac
  fi
  run git tag -a "v$VERSION" -m "v$VERSION"
  run git push origin HEAD --tags
  run gh release create "v$VERSION" "$RELEASE_APK" \
    --title "v$VERSION" --notes-file "$NOTES"
fi

say "done: $VERSION (build $BUILD)"
