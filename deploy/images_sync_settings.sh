#!/bin/bash
# Sync exchangeBots/ config files to server (settings.json, spike_aggregation.conf, etc.)
# Can be run independently without restarting containers.
#
# Note: settings.json files on the server are owned by root (written by Docker),
#       so rsync uploads to a temp dir first, then sudo-moves to the final location.
#
# Usage:
#   ./deploy/images_sync_settings.sh           # Sync all bots (interactive confirm)
#   ./deploy/images_sync_settings.sh --all     # Sync all bots (no prompt)
#   ./deploy/images_sync_settings.sh --bot REACT_USDT_GRID          # Sync one bot
#   ./deploy/images_sync_settings.sh --diff                         # Show diff only
#   ./deploy/images_sync_settings.sh --diff --bot REACT_USDT_GRID   # Diff one bot

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

SERVER="tradebotalphahorizon.duckdns.org"
REMOTE_PATH="/home/asus/trade-bot"
SUDO_PASS="81726354azSX"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Parse arguments
MODE="interactive"   # interactive | all | bot | diff
BOT_NAME=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --all)   MODE="all" ;;
        --diff)  MODE="diff" ;;
        --bot)   MODE="bot"; BOT_NAME="${2:-}"; shift ;;
        *) echo -e "${RED}Unknown argument: $1${NC}"; exit 1 ;;
    esac
    shift
done

# ─── Diff helpers ────────────────────────────────────────────────────────────

show_diff_for_bot() {
    local bot="$1"
    local local_file="$PROJECT_ROOT/exchangeBots/$bot/settings.json"
    local server_content

    if [ ! -f "$local_file" ]; then
        echo -e "${YELLOW}  ⚠  $bot — only on server (no local file)${NC}"
        return
    fi

    server_content=$(ssh "$SERVER" "cat '$REMOTE_PATH/exchangeBots/$bot/settings.json' 2>/dev/null || echo ''" 2>/dev/null)

    local server_norm local_norm
    server_norm=$(echo "$server_content" | python3 -c "import sys,json; d=sys.stdin.read().strip(); print(json.dumps(json.loads(d), sort_keys=True, indent=2) if d else '')" 2>/dev/null || echo "")
    local_norm=$(python3 -c "import sys,json; print(json.dumps(json.load(open('$local_file')), sort_keys=True, indent=2))" 2>/dev/null || echo "")

    if [ "$server_norm" = "$local_norm" ]; then
        echo -e "${GREEN}  ✅ $bot — identical${NC}"
    elif [ -z "$server_norm" ]; then
        echo -e "${YELLOW}  ⚠  $bot — server file is empty / missing${NC}"
        echo "     Local has $(echo "$local_norm" | wc -l) lines"
    else
        echo -e "${RED}  ❌ $bot — differs:${NC}"
        diff <(echo "$server_norm") <(echo "$local_norm") \
            | sed 's/^/     /' | head -30
    fi
}

show_diff_all() {
    echo -e "${BLUE}>>> Comparing exchangeBots/ local ↔ server...${NC}"
    echo ""

    # Bots only on server
    local server_bots
    server_bots=$(ssh "$SERVER" "ls '$REMOTE_PATH/exchangeBots/'" 2>/dev/null)
    while IFS= read -r bot; do
        [ -z "$bot" ] && continue
        if [ ! -d "$PROJECT_ROOT/exchangeBots/$bot" ]; then
            echo -e "${YELLOW}  ⚠  $bot — only on server (no local dir)${NC}"
        fi
    done <<< "$server_bots"

    # Local bots
    for bot_dir in "$PROJECT_ROOT/exchangeBots"/*/; do
        local bot
        bot=$(basename "$bot_dir")
        [[ "$bot" == "emulate" ]] && continue
        [ ! -f "$bot_dir/settings.json" ] && continue
        show_diff_for_bot "$bot"
    done
    echo ""
}

# ─── Sync helpers ─────────────────────────────────────────────────────────────

sync_all() {
    echo -e "${BLUE}>>> Uploading exchangeBots/ to server temp dir...${NC}"
    rsync -avz --exclude='*.log' --exclude='emulate/' \
        "$PROJECT_ROOT/exchangeBots/" \
        "$SERVER:$REMOTE_PATH/exchangeBots.tmp/"

    echo -e "${YELLOW}>>> Moving files into place (sudo)...${NC}"
    ssh "$SERVER" "echo '$SUDO_PASS' | sudo -S bash -c '
        cp -r /home/asus/trade-bot/exchangeBots.tmp/. /home/asus/trade-bot/exchangeBots/
        rm -rf /home/asus/trade-bot/exchangeBots.tmp
    '"
    echo -e "${GREEN}✓ All exchangeBots/ synced${NC}"
}

sync_bot() {
    local bot="$1"
    local local_dir="$PROJECT_ROOT/exchangeBots/$bot"

    if [ ! -d "$local_dir" ]; then
        echo -e "${RED}ERROR: Local bot dir not found: $local_dir${NC}"
        exit 1
    fi

    echo -e "${BLUE}>>> Uploading exchangeBots/$bot/ to server temp dir...${NC}"
    ssh "$SERVER" "mkdir -p '$REMOTE_PATH/exchangeBots.tmp/$bot'"
    rsync -avz --exclude='*.log' \
        "$local_dir/" \
        "$SERVER:$REMOTE_PATH/exchangeBots.tmp/$bot/"

    echo -e "${YELLOW}>>> Moving files into place (sudo)...${NC}"
    ssh "$SERVER" "echo '$SUDO_PASS' | sudo -S bash -c '
        mkdir -p /home/asus/trade-bot/exchangeBots/$bot
        cp -r /home/asus/trade-bot/exchangeBots.tmp/$bot/. /home/asus/trade-bot/exchangeBots/$bot/
        rm -rf /home/asus/trade-bot/exchangeBots.tmp
    '"
    echo -e "${GREEN}✓ $bot synced${NC}"
}

# ─── Main ─────────────────────────────────────────────────────────────────────

echo "================================================"
echo "SYNC EXCHANGEBOTS/ SETTINGS TO SERVER"
echo "================================================"
echo "Server: $SERVER"
echo ""

case "$MODE" in
    diff)
        if [ -n "$BOT_NAME" ]; then
            show_diff_for_bot "$BOT_NAME"
        else
            show_diff_all
        fi
        ;;

    all)
        show_diff_all
        sync_all
        ;;

    bot)
        if [ -z "$BOT_NAME" ]; then
            echo -e "${RED}ERROR: --bot requires a bot name${NC}"
            echo "Usage: $0 --bot REACT_USDT_GRID"
            exit 1
        fi
        show_diff_for_bot "$BOT_NAME"
        echo ""
        sync_bot "$BOT_NAME"
        ;;

    interactive)
        show_diff_all

        echo "What do you want to sync?"
        echo "  1) all    - Sync all bots"
        echo "  2) one    - Sync a specific bot"
        echo "  0) cancel - Do nothing"
        echo ""
        read -p "Select [0/1/2]: " -n 1 choice
        echo ""

        case "${choice:-0}" in
            1)
                sync_all
                ;;
            2)
                read -p "Bot name: " BOT_NAME
                echo ""
                sync_bot "$BOT_NAME"
                ;;
            *)
                echo "Cancelled."
                exit 0
                ;;
        esac
        ;;
esac

echo ""
echo "================================================"
echo -e "${GREEN}DONE${NC}"
echo "================================================"
