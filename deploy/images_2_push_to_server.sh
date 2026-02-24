#!/bin/bash
# Push pre-built Docker images to server and load them.
# Images must be built first with images_1_build_local.sh
#
# Usage:
#   ./deploy/images_2_push_to_server.sh              # Push all images
#   ./deploy/images_2_push_to_server.sh frontend     # Push only frontend
#   ./deploy/images_2_push_to_server.sh backend      # Push only backend

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Server settings (edit if needed)
SERVER="tradebotalphahorizon.duckdns.org"
REMOTE_PATH="/home/asus/trade-bot"
REMOTE_IMAGES_DIR="$REMOTE_PATH/deploy/images"

# Local images directory
IMAGES_DIR="$PROJECT_ROOT/deploy/images"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Interactive menu function
select_target() {
    echo "" >&2
    echo "What do you want to push?" >&2
    echo "  1) frontend   - Push frontend only" >&2
    echo "  2) backend    - Push backend only" >&2
    echo "  3) all        - Push both (default)" >&2
    echo "" >&2
    read -p "Select [1/2/3]: " -n 1 choice
    echo "" >&2
    case "$choice" in
        1) echo "frontend" ;;
        2) echo "backend" ;;
        3|"") echo "all" ;;
        *) echo "all" ;;
    esac
}

# What to push: from argument or interactive menu
if [ -n "${1:-}" ]; then
    PUSH_TARGET="$1"
else
    PUSH_TARGET=$(select_target)
fi

echo "================================================"
echo "PUSH IMAGES TO SERVER"
echo "================================================"
echo "Server:      $SERVER"
echo "Remote path: $REMOTE_IMAGES_DIR"
echo "Local path:  $IMAGES_DIR"
echo "Target:      $PUSH_TARGET"
echo ""

# Check if images exist
check_image() {
    local image_file="$1"
    if [ ! -f "$image_file" ]; then
        echo -e "${RED}ERROR: Image file not found: $image_file${NC}"
        echo "Run ./deploy/images_1_build_local.sh first"
        exit 1
    fi
}

push_and_load() {
    local image_file="$1"
    local image_name="$2"

    echo -e "${BLUE}>>> Pushing $image_name...${NC}"

    # Get file size for progress info
    local size=$(du -h "$image_file" | cut -f1)
    echo "File: $image_file ($size)"

    # Ensure remote directory exists
    ssh "$SERVER" "mkdir -p '$REMOTE_IMAGES_DIR'"

    # Upload with progress
    echo -e "${YELLOW}>>> Uploading...${NC}"
    rsync -avz --progress "$image_file" "$SERVER:$REMOTE_IMAGES_DIR/"

    # Load image on server
    echo -e "${YELLOW}>>> Loading image on server...${NC}"
    local remote_file="$REMOTE_IMAGES_DIR/$(basename "$image_file")"
    ssh "$SERVER" "gunzip -c '$remote_file' | docker load"

    echo -e "${GREEN}✓ $image_name loaded on server${NC}"
}

case "$PUSH_TARGET" in
    frontend)
        check_image "$IMAGES_DIR/frontend.tar.gz"
        push_and_load "$IMAGES_DIR/frontend.tar.gz" "frontend"
        ;;
    backend)
        check_image "$IMAGES_DIR/backend.tar.gz"
        push_and_load "$IMAGES_DIR/backend.tar.gz" "backend"
        ;;
    all)
        check_image "$IMAGES_DIR/frontend.tar.gz"
        check_image "$IMAGES_DIR/backend.tar.gz"
        push_and_load "$IMAGES_DIR/frontend.tar.gz" "frontend"
        echo ""
        push_and_load "$IMAGES_DIR/backend.tar.gz" "backend"
        ;;
    *)
        echo -e "${RED}Unknown target: $PUSH_TARGET${NC}"
        echo "Usage: $0 [frontend|backend|all]"
        exit 1
        ;;
esac

echo ""
echo "================================================"
echo -e "${GREEN}PUSH COMPLETED${NC}"
echo "================================================"
echo ""
echo "Images loaded on server. Verify with:"
echo "  ssh $SERVER 'docker images | grep trade-bot'"
echo ""
echo "Next step: ./deploy/images_3_restart_containers.sh"
