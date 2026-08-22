#!/bin/bash
# Build and package The Isle Linux Desktop release
# Usage: ./scripts/build-release.sh [version]
# Output: /tmp/the-isle-release/the-isle-<version>.tar.gz

set -euo pipefail

VERSION="${1:-$(date +%Y%m%d)}"
APP_NAME="the-isle"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="/tmp/$APP_NAME-release"
RELEASE_DIR="$OUTPUT_DIR/$APP_NAME-$VERSION"
ARCHIVE="$OUTPUT_DIR/$APP_NAME-$VERSION.tar.gz"

echo "=== The Isle Release Packager ==="
echo "Version: $VERSION"
echo ""

# Step 1: Build Flutter release
echo "[1/3] Building Flutter release..."
cd "$PROJECT_DIR/apps/isle_app"
flutter build linux --release 2>&1 | tail -3
echo "  ✅ Build complete"

# Step 2: Assemble release directory
echo "[2/3] Assembling release package..."
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

# Copy bundle
cp -a build/linux/x64/release/bundle/* "$RELEASE_DIR/"
chmod +x "$RELEASE_DIR/isle_app"

# Copy icon
mkdir -p "$RELEASE_DIR/icons"
cp linux/assets/icon.svg "$RELEASE_DIR/icons/$APP_NAME.svg" 2>/dev/null || true

# Copy install script
cp "$PROJECT_DIR/apps/isle_app/install.sh" "$RELEASE_DIR/"
chmod +x "$RELEASE_DIR/install.sh"

# Create README
cat > "$RELEASE_DIR/README.txt" << README_EOF
The Isle — Saint Mary Liberty Island
======================================
Version: $VERSION

Silver-backed private sovereign network desktop client.

Quick Install:
  sudo ./install.sh

Or run directly:
  ./isle_app --server http://YOUR_SERVER:8080 --pubkey YOUR_PUBKEY

Keyboard Shortcuts:
  Ctrl+1-6  Switch tabs (Dashboard/Wallet/Vault/Market/POS/Radio)
  Ctrl+R    Reconnect
  Ctrl+Q    Quit
  Ctrl+,    Settings

Requirements:
  - Linux desktop (GTK3)
  - libgtk-3-0, liblzma-dev, libayatana-appindicator3-dev

Documentation:
  See docs/ directory in the source repository or
  visit http://YOUR_SERVER:8080/docs

Website: https://stmaria.org
README_EOF

# Create .desktop file in the package
cat > "$RELEASE_DIR/$APP_NAME.desktop" << DESKTOP_EOF
[Desktop Entry]
Type=Application
Name=The Isle
Comment=Saint Mary Liberty Island — Silver-Backed Private Sovereign Network
Exec=$APP_NAME/isle_app %F
Icon=$APP_NAME
Terminal=false
Categories=Finance;Office;
StartupNotify=true
Keywords=silver;crypto;wallet;payment;
DESKTOP_EOF

echo "  ✅ Package assembled at $RELEASE_DIR"

# Step 3: Create tar.gz archive
echo "[3/3] Creating archive..."
cd "$OUTPUT_DIR"
tar -czf "$ARCHIVE" "$APP_NAME-$VERSION/"
echo "  ✅ Archive created: $ARCHIVE"
echo ""

# Summary
echo "=== Release Summary ==="
echo "Archive: $ARCHIVE"
SIZE=$(du -sh "$ARCHIVE" | cut -f1)
echo "Size:    $SIZE"
echo ""
echo "To distribute:"
echo "  scp $ARCHIVE user@host:/path/to/www/"
echo ""
echo "To install:"
echo "  tar -xzf $ARCHIVE"
echo "  cd $APP_NAME-$VERSION"
echo "  sudo ./install.sh"
