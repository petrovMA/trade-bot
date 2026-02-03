#!/bin/bash
# Full deploy: build locally → push to server → restart containers.
#
# This is the recommended way to deploy when the server has limited resources.
# All heavy build operations happen on your local machine.
#
# Usage:
#   ./deploy/images_full_deploy.sh              # Interactive mode (choose at each step)
#   ./deploy/images_full_deploy.sh frontend     # Deploy only frontend (all steps)
#   ./deploy/images_full_deploy.sh backend      # Deploy only backend (all steps)
#   ./deploy/images_full_deploy.sh all          # Deploy all (all steps)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Interactive menu function
select_target() {
    local step_name="$1"
    echo "" >&2
    echo "What do you want to $step_name?" >&2
    echo "  1) frontend   - Frontend only" >&2
    echo "  2) backend    - Backend only" >&2
    echo "  3) all        - Both (default)" >&2
    echo "  0) skip       - Skip this step" >&2
    echo "" >&2
    read -p "Select [0/1/2/3]: " -n 1 choice
    echo "" >&2
    case "$choice" in
        0) echo "skip" ;;
        1) echo "frontend" ;;
        2) echo "backend" ;;
        3|"") echo "all" ;;
        *) echo "all" ;;
    esac
}

# What to deploy: from argument or interactive mode
DEPLOY_MODE="${1:-interactive}"

echo "================================================"
echo "FULL DEPLOY: LOCAL BUILD → SERVER"
echo "================================================"
echo "Mode: $DEPLOY_MODE"
echo ""
echo "This script will:"
echo "  1. Build Docker image(s) locally"
echo "  2. Save to .tar.gz file(s)"
echo "  3. Push to server via rsync"
echo "  4. Load image(s) on server"
echo "  5. Restart container(s)"
echo ""

if [ "$DEPLOY_MODE" == "interactive" ]; then
    # ========== STEP 1: BUILD ==========
    BUILD_TARGET=$(select_target "build")

    if [ "$BUILD_TARGET" != "skip" ]; then
        echo ""
        echo -e "${BLUE}========== STEP 1: BUILD LOCALLY ==========${NC}"
        /bin/bash "$SCRIPT_DIR/images_1_build_local.sh" "$BUILD_TARGET"

        echo ""
        read -p "Build completed. Continue to push? [Y/n] " -n 1 -r
        echo ""
        if [[ ! ${REPLY:-} =~ ^[Yy]$ ]] && [[ -n ${REPLY:-} ]]; then
            echo "Stopped by user"
            exit 0
        fi
    else
        echo -e "${YELLOW}>>> Skipping build step${NC}"
    fi

    # ========== STEP 2: PUSH ==========
    PUSH_TARGET=$(select_target "push to server")

    if [ "$PUSH_TARGET" != "skip" ]; then
        echo ""
        echo -e "${BLUE}========== STEP 2: PUSH TO SERVER ==========${NC}"
        /bin/bash "$SCRIPT_DIR/images_2_push_to_server.sh" "$PUSH_TARGET"

        echo ""
        read -p "Push completed. Continue to restart containers? [Y/n] " -n 1 -r
        echo ""
        if [[ ! ${REPLY:-} =~ ^[Yy]$ ]] && [[ -n ${REPLY:-} ]]; then
            echo "Stopped by user"
            exit 0
        fi
    else
        echo -e "${YELLOW}>>> Skipping push step${NC}"
    fi

    # ========== STEP 3: RESTART ==========
    RESTART_TARGET=$(select_target "restart")

    if [ "$RESTART_TARGET" != "skip" ]; then
        echo ""
        echo -e "${BLUE}========== STEP 3: RESTART CONTAINERS ==========${NC}"
        /bin/bash "$SCRIPT_DIR/images_3_restart_containers.sh" "$RESTART_TARGET"
    else
        echo -e "${YELLOW}>>> Skipping restart step${NC}"
    fi

else
    # Non-interactive mode: use the same target for all steps
    DEPLOY_TARGET="$DEPLOY_MODE"

    # Validate target
    case "$DEPLOY_TARGET" in
        frontend|backend|all) ;;
        *)
            echo -e "${RED}Unknown target: $DEPLOY_TARGET${NC}"
            echo "Usage: $0 [frontend|backend|all]"
            exit 1
            ;;
    esac

    # Step 1: Build locally
    echo -e "${BLUE}========== STEP 1: BUILD LOCALLY ==========${NC}"
    /bin/bash "$SCRIPT_DIR/images_1_build_local.sh" "$DEPLOY_TARGET"

    echo ""
    read -p "Build completed. Continue to push to server? [Y/n] " -n 1 -r
    echo ""
    if [[ ! ${REPLY:-} =~ ^[Yy]$ ]] && [[ -n ${REPLY:-} ]]; then
        echo "Stopped by user"
        exit 0
    fi

    # Step 2: Push to server
    echo ""
    echo -e "${BLUE}========== STEP 2: PUSH TO SERVER ==========${NC}"
    /bin/bash "$SCRIPT_DIR/images_2_push_to_server.sh" "$DEPLOY_TARGET"

    echo ""
    read -p "Push completed. Continue to restart containers? [Y/n] " -n 1 -r
    echo ""
    if [[ ! ${REPLY:-} =~ ^[Yy]$ ]] && [[ -n ${REPLY:-} ]]; then
        echo "Stopped by user"
        exit 0
    fi

    # Step 3: Restart containers
    echo ""
    echo -e "${BLUE}========== STEP 3: RESTART CONTAINERS ==========${NC}"
    /bin/bash "$SCRIPT_DIR/images_3_restart_containers.sh" "$DEPLOY_TARGET"
fi

echo ""
echo "================================================"
echo -e "${GREEN}FULL DEPLOY COMPLETED!${NC}"
echo "================================================"
echo ""
