#!/bin/bash
# Build Docker images LOCALLY for frontend and backend.
# This allows building on powerful local machine instead of the server.
#
# Usage:
#   ./deploy/images_1_build_local.sh              # Build all images
#   ./deploy/images_1_build_local.sh frontend     # Build only frontend
#   ./deploy/images_1_build_local.sh backend      # Build only backend

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Image names (must match docker-compose.prod.yml)
FRONTEND_IMAGE="trade-bot-frontend:latest"
BACKEND_IMAGE="trade-bot-backend:latest"

# Output directory for .tar files
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
    echo "What do you want to build?" >&2
    echo "  1) frontend   - Build frontend only" >&2
    echo "  2) backend    - Build backend only" >&2
    echo "  3) all        - Build both (default)" >&2
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

# What to build: from argument or interactive menu
if [ -n "${1:-}" ]; then
    BUILD_TARGET="$1"
else
    BUILD_TARGET=$(select_target)
fi

echo "================================================"
echo "LOCAL IMAGE BUILD"
echo "================================================"
echo "Project root: $PROJECT_ROOT"
echo "Images dir:   $IMAGES_DIR"
echo "Target:       $BUILD_TARGET"
echo ""

# Create images directory
mkdir -p "$IMAGES_DIR"

build_frontend() {
    echo -e "${BLUE}>>> Building frontend image...${NC}"
    echo "Context: $PROJECT_ROOT/frontend"
    echo "Dockerfile: $PROJECT_ROOT/frontend/nginx/Dockerfile.ssl"

    docker build \
        --tag "$FRONTEND_IMAGE" \
        --file "$PROJECT_ROOT/frontend/nginx/Dockerfile.ssl" \
        "$PROJECT_ROOT/frontend"

    echo -e "${YELLOW}>>> Saving frontend image to tar...${NC}"
    docker save "$FRONTEND_IMAGE" | gzip > "$IMAGES_DIR/frontend.tar.gz"

    local size=$(du -h "$IMAGES_DIR/frontend.tar.gz" | cut -f1)
    echo -e "${GREEN}✓ Frontend image saved: $IMAGES_DIR/frontend.tar.gz ($size)${NC}"
}

build_backend() {
    echo -e "${BLUE}>>> Building backend image...${NC}"
    echo "Context: $PROJECT_ROOT"
    echo "Dockerfile: $PROJECT_ROOT/Dockerfile.backend"

    docker build \
        --tag "$BACKEND_IMAGE" \
        --file "$PROJECT_ROOT/Dockerfile.backend" \
        "$PROJECT_ROOT"

    echo -e "${YELLOW}>>> Saving backend image to tar...${NC}"
    docker save "$BACKEND_IMAGE" | gzip > "$IMAGES_DIR/backend.tar.gz"

    local size=$(du -h "$IMAGES_DIR/backend.tar.gz" | cut -f1)
    echo -e "${GREEN}✓ Backend image saved: $IMAGES_DIR/backend.tar.gz ($size)${NC}"
}

case "$BUILD_TARGET" in
    frontend)
        build_frontend
        ;;
    backend)
        build_backend
        ;;
    all)
        build_frontend
        echo ""
        build_backend
        ;;
    *)
        echo -e "${RED}Unknown target: $BUILD_TARGET${NC}"
        echo "Usage: $0 [frontend|backend|all]"
        exit 1
        ;;
esac

echo ""
echo "================================================"
echo -e "${GREEN}BUILD COMPLETED${NC}"
echo "================================================"
echo ""
echo "Images saved to: $IMAGES_DIR/"
ls -lh "$IMAGES_DIR/"
echo ""
echo "Next step: ./deploy/images_2_push_to_server.sh"
