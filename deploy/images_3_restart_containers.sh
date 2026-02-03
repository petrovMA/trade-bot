#!/bin/bash
# Restart containers on server using pre-loaded images.
# Images must be pushed first with images_2_push_to_server.sh
#
# This script does NOT build images - it uses images already loaded on server.
#
# Usage:
#   ./deploy/images_3_restart_containers.sh              # Restart all (frontend + backend)
#   ./deploy/images_3_restart_containers.sh frontend     # Restart only frontend
#   ./deploy/images_3_restart_containers.sh backend      # Restart only backend

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Server settings (edit if needed)
SERVER="tradebotalphahorizon.duckdns.org"
REMOTE_PATH="/home/asus/trade-bot"
COMPOSE_FILE="$REMOTE_PATH/docker-compose.prod.yml"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Interactive menu function
select_target() {
    echo "" >&2
    echo "What do you want to restart?" >&2
    echo "  1) frontend   - Restart frontend only" >&2
    echo "  2) backend    - Restart backend only" >&2
    echo "  3) all        - Restart all (default)" >&2
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

# What to restart: from argument or interactive menu
if [ -n "${1:-}" ]; then
    RESTART_TARGET="$1"
else
    RESTART_TARGET=$(select_target)
fi

echo "================================================"
echo "RESTART CONTAINERS ON SERVER"
echo "================================================"
echo "Server:       $SERVER"
echo "Compose file: $COMPOSE_FILE"
echo "Target:       $RESTART_TARGET"
echo ""

# Sync compose file to server
sync_compose_file() {
    echo -e "${BLUE}>>> Syncing docker-compose.prod.yml...${NC}"
    rsync -avz "$PROJECT_ROOT/docker-compose.prod.yml" "$SERVER:$REMOTE_PATH/"
    echo -e "${GREEN}✓ docker-compose.prod.yml synced${NC}"
}

# Sync nginx config first (optional but keeps configs up to date)
sync_nginx_config() {
    echo -e "${BLUE}>>> Syncing nginx configuration...${NC}"
    rsync -avz --delete "$PROJECT_ROOT/frontend/nginx/" "$SERVER:$REMOTE_PATH/frontend/nginx/"
    echo -e "${GREEN}✓ Nginx config synced${NC}"
}


restart_frontend() {
    echo -e "${YELLOW}>>> Stopping frontend container...${NC}"
    ssh "$SERVER" "docker compose -f '$COMPOSE_FILE' stop frontend || true"

    echo -e "${YELLOW}>>> Removing old frontend container...${NC}"
    ssh "$SERVER" "docker compose -f '$COMPOSE_FILE' rm -f frontend || true"

    echo -e "${YELLOW}>>> Starting frontend with new image...${NC}"
    # Use --no-build to prevent building, just use existing image
    ssh "$SERVER" "docker compose -f '$COMPOSE_FILE' up -d --no-build frontend"

    echo -e "${GREEN}✓ Frontend restarted${NC}"
}

restart_backend() {
    echo -e "${YELLOW}>>> Stopping backend container...${NC}"
    ssh "$SERVER" "docker compose -f '$COMPOSE_FILE' stop trade-bot || true"

    echo -e "${YELLOW}>>> Removing old backend container...${NC}"
    ssh "$SERVER" "docker compose -f '$COMPOSE_FILE' rm -f trade-bot || true"

    echo -e "${YELLOW}>>> Starting backend with new image...${NC}"
    # Use --no-build to prevent building, just use existing image
    ssh "$SERVER" "docker compose -f '$COMPOSE_FILE' up -d --no-build trade-bot"

    echo -e "${GREEN}✓ Backend restarted${NC}"
}

# Wait for backend container to be healthy before nginx reload
wait_for_backend_healthy() {
    echo -e "${YELLOW}>>> Waiting for backend to be healthy (may take ~60s)...${NC}"
    ssh "$SERVER" "
        for i in \$(seq 1 30); do
            status=\$(docker inspect trade-bot-backend --format='{{.State.Health.Status}}' 2>/dev/null || echo 'not_found')
            if [ \"\$status\" = 'healthy' ]; then
                echo 'Backend is healthy!'
                exit 0
            fi
            echo -n '.'
            sleep 3
        done
        echo ''
        echo 'Warning: Backend health check timeout, proceeding anyway'
    "
}

# Reload nginx to update DNS cache for new container IPs
# This is required because nginx caches DNS at startup and won't see new IPs otherwise
reload_nginx() {
    echo -e "${YELLOW}>>> Reloading nginx to update DNS cache...${NC}"
    ssh "$SERVER" "docker exec alpha-horizon-nginx-prod nginx -s reload 2>/dev/null || echo 'nginx reload skipped (container not running)'"
    echo -e "${GREEN}✓ Nginx reloaded${NC}"
}

# Always sync required files first
sync_compose_file
echo ""
sync_nginx_config
echo ""

case "$RESTART_TARGET" in
    frontend)
        restart_frontend
        echo ""
        reload_nginx
        ;;
    backend)
        restart_backend
        echo ""
        wait_for_backend_healthy
        echo ""
        reload_nginx
        ;;
    all)
        restart_frontend
        echo ""
        restart_backend
        echo ""
        wait_for_backend_healthy
        echo ""
        reload_nginx
        ;;
    *)
        echo -e "${RED}Unknown target: $RESTART_TARGET${NC}"
        echo "Usage: $0 [frontend|backend|all]"
        exit 1
        ;;
esac

echo ""
echo "================================================"
echo -e "${GREEN}RESTART COMPLETED${NC}"
echo "================================================"
echo ""
echo "Container status:"
ssh "$SERVER" "docker compose -f '$COMPOSE_FILE' ps"
echo ""
echo "Check logs if needed:"
echo "  ssh $SERVER \"docker compose -f $COMPOSE_FILE logs -f --tail=50 frontend\""
echo "  ssh $SERVER \"docker compose -f $COMPOSE_FILE logs -f --tail=50 trade-bot\""
