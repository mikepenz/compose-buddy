#!/usr/bin/env bash
#
# device-preview.sh — Build, install, and launch on-device Compose preview
#
# Usage:
#   ./scripts/device-preview.sh [preview-fqn] [options]
#
# Examples:
#   ./scripts/device-preview.sh                                          # list available previews
#   ./scripts/device-preview.sh com.example.sample.PreviewsKt.SimpleBoxPreview
#   ./scripts/device-preview.sh com.example.sample.PreviewsKt.SimpleBoxPreview --connect
#   ./scripts/device-preview.sh com.example.sample.PreviewsKt.SimpleBoxPreview --connect --format=inspector
#   ./scripts/device-preview.sh com.example.sample.PreviewsKt.SimpleBoxPreview --connect --output=./out
#
# Options:
#   --list          List available previews and exit
#   --connect       After launching, connect and stream frames
#   --format=FMT    Output format for --connect: json (default), inspector, png
#   --output=DIR    Capture one frame, write JSON + PNG to DIR, and exit
#   --port=PORT     WebSocket port (default: 7890)
#   --device=SERIAL ADB device serial (optional)
#   --skip-build    Skip the build step (use existing APK)
#   --skip-install  Skip both build and install steps

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SAMPLE_DIR="$ROOT_DIR/sample"

PORT=7890
FORMAT="json"
CONNECT=false
LIST=false
SKIP_BUILD=false
SKIP_INSTALL=false
OUTPUT_DIR=""
DEVICE_SERIAL=""
PREVIEW_FQN=""

# Parse arguments
for arg in "$@"; do
    case "$arg" in
        --list)          LIST=true ;;
        --connect)       CONNECT=true ;;
        --format=*)      FORMAT="${arg#--format=}" ;;
        --port=*)        PORT="${arg#--port=}" ;;
        --device=*)      DEVICE_SERIAL="${arg#--device=}" ;;
        --output=*)      OUTPUT_DIR="${arg#--output=}"; CONNECT=true ;;
        --skip-build)    SKIP_BUILD=true ;;
        --skip-install)  SKIP_BUILD=true; SKIP_INSTALL=true ;;
        --*)             echo "Unknown option: $arg"; exit 1 ;;
        *)               PREVIEW_FQN="$arg" ;;
    esac
done

ADB_ARGS=()
if [ -n "$DEVICE_SERIAL" ]; then
    ADB_ARGS+=(-s "$DEVICE_SERIAL")
fi

# List mode
if [ "$LIST" = true ] || [ -z "$PREVIEW_FQN" ]; then
    echo "==> Listing available previews..."
    cd "$SAMPLE_DIR"
    ./gradlew :app:buddyPreviewList --quiet 2>/dev/null | grep -v "^$"
    if [ -z "$PREVIEW_FQN" ] && [ "$LIST" != true ]; then
        echo ""
        echo "Usage: $0 <preview-fqn> [--connect] [--format=json|inspector|png]"
    fi
    exit 0
fi

# Build
if [ "$SKIP_BUILD" = false ]; then
    echo "==> Building buddyDebug APK..."
    cd "$SAMPLE_DIR"
    ./gradlew :app:assembleBuddyDebug --quiet
    echo "    Build complete."
fi

# Install
if [ "$SKIP_INSTALL" = false ]; then
    APK="$SAMPLE_DIR/app/build/outputs/apk/buddyDebug/app-buddyDebug.apk"
    if [ ! -f "$APK" ]; then
        echo "ERROR: APK not found at $APK"
        echo "       Run without --skip-build first."
        exit 1
    fi
    echo "==> Installing APK..."
    adb "${ADB_ARGS[@]}" install -r "$APK"
fi

# Forward port and launch
echo "==> Forwarding port $PORT..."
adb "${ADB_ARGS[@]}" forward "tcp:$PORT" "tcp:$PORT"

echo "==> Launching preview: $PREVIEW_FQN"
adb "${ADB_ARGS[@]}" shell am start \
    -n "com.example.sample/dev.mikepenz.composebuddy.device.BuddyPreviewActivity" \
    -e preview "$PREVIEW_FQN" \
    --ei port "$PORT"

echo "    Preview launched on device."
echo "    WebSocket available at ws://localhost:$PORT"

# Connect
if [ "$CONNECT" = true ]; then
    echo ""
    sleep 1
    cd "$ROOT_DIR"

    if [ -n "$OUTPUT_DIR" ]; then
        echo "==> Capturing frame to $OUTPUT_DIR..."
        ./gradlew :compose-buddy-cli:run --quiet \
            --args="device connect --port=$PORT --output=$OUTPUT_DIR"
    else
        echo "==> Connecting (format=$FORMAT)..."
        ./gradlew :compose-buddy-cli:run --quiet \
            --args="device connect --port=$PORT --format=$FORMAT"
    fi
fi
