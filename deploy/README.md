# Deployment Verification Guide

Quick reference for checking deployment status and logs on the server.

## Server Info
- **Server:** `tradebotalphahorizon.duckdns.org`
- **Remote path:** `/home/asus/trade-bot`
- **Compose file:** `docker-compose.prod.yml`

## Check Container Status

```bash
ssh tradebotalphahorizon.duckdns.org "cd /home/asus/trade-bot && docker compose -f docker-compose.prod.yml ps"
```

```bash
ssh tradebotalphahorizon.duckdns.org "docker ps"
```

Expected output: both `frontend` and `trade-bot` should be "Up" (running).

## View Logs

### Backend (trade-bot)

Last 50 lines:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs --tail=50 trade-bot"
```

Follow logs in real-time:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs -f --tail=200 trade-bot"
```

Last 100 lines with timestamps:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs --tail=100 -t trade-bot"
```

### Frontend

Last 50 lines:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs --tail=50 frontend"
```

Follow logs in real-time:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs -f frontend"
```

### Both containers

Last 50 lines:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs --tail=50"
```

Follow all logs in real-time:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs -f"
```

## Check for Errors

Backend errors (last 200 lines):
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs --tail=200 trade-bot | grep -i error"
```

Frontend errors:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs --tail=200 frontend | grep -i error"
```

## Verify Deployment Success

### 1. Check container health
```bash
ssh tradebotalphahorizon.duckdns.org "docker ps --filter name=trade-bot --filter name=frontend"
```
✅ Both containers should show "Up" status

### 2. Check backend is responding
```bash
curl -I https://tradebotalphahorizon.duckdns.org/trade-bot/api/positions
```
✅ Should return HTTP 200 OK

### 3. Check frontend is accessible
```bash
curl -I https://tradebotalphahorizon.duckdns.org/
```
✅ Should return HTTP 200 OK

### 4. Check recent logs for startup messages

Backend startup (look for "Bot starts!" or Spring Boot banner):
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs --tail=100 trade-bot | grep -E 'Started|Bot starts'"
```

Frontend startup (look for nginx startup):
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs --tail=50 frontend | grep -i 'nginx'"
```

## Restart Containers (if needed)

Restart backend only:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml restart trade-bot"
```

Restart frontend only:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml restart frontend"
```

Restart all:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml restart"
```

## Common Issues

### Container keeps restarting

Check why it's failing:
```bash
ssh tradebotalphahorizon.duckdns.org "docker compose -f /home/asus/trade-bot/docker-compose.prod.yml logs --tail=200 trade-bot"
```

### Port conflicts

Check what's using the ports:
```bash
ssh tradebotalphahorizon.duckdns.org "ss -tulpn | grep -E ':(80|443|8080)'"
```

### Disk space issues

Check disk usage:
```bash
ssh tradebotalphahorizon.duckdns.org "df -h"
```

Clean up Docker:
```bash
ssh tradebotalphahorizon.duckdns.org "docker system prune -af"
```

## Quick Health Check Script

Save this as `check_deploy.sh` for easy verification:

```bash
#!/bin/bash
SERVER="tradebotalphahorizon.duckdns.org"
COMPOSE="/home/asus/trade-bot/docker-compose.prod.yml"

echo "=== Container Status ==="
ssh $SERVER "docker compose -f $COMPOSE ps"
echo ""

echo "=== Recent Backend Logs ==="
ssh $SERVER "docker compose -f $COMPOSE logs --tail=20 trade-bot"
echo ""

echo "=== Recent Frontend Logs ==="
ssh $SERVER "docker compose -f $COMPOSE logs --tail=20 frontend"
echo ""

echo "=== API Health Check ==="
curl -s -o /dev/null -w "Backend API: %{http_code}\n" https://$SERVER/trade-bot/api/positions
curl -s -o /dev/null -w "Frontend: %{http_code}\n" https://$SERVER/
```