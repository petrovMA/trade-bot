# Trade Bot

## Architecture Overview

### Deployment Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Docker Compose                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────────────────┐     ┌─────────────────────────────┐  │
│   │   Frontend (nginx)  │     │    Backend (Spring Boot)    │  │
│   │  trade-bot-frontend │     │     trade-bot-backend       │  │
│   │      Port 8082      │────▶│        Port 8081            │  │
│   │                     │     │    (internal: 8080)         │  │
│   │  - Serves React SPA │     │                             │  │
│   │  - Proxies /api/*   │     │  - REST API                 │  │
│   │    to backend       │     │  - Trading logic            │  │
│   └─────────────────────┘     │  - WebSocket connections    │  │
│                               └─────────────────────────────┘  │
│                                                                 │
│   Network: trade-bot-network (bridge)                          │
└─────────────────────────────────────────────────────────────────┘
```

### Request Flow

1. **Browser** → `http://server:8082/trade-bot/` → **Nginx** serves React SPA
2. **React App** → `/trade-bot/api/*` → **Nginx proxy** → `http://trade-bot:8080/*` → **Spring Boot API**

### Key Configuration Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Container orchestration (ports 8081, 8082) |
| `frontend/nginx/nginx.conf` | Nginx config with API proxy |
| `frontend/vite.config.ts` | Frontend build config (base: `/trade-bot/`) |
| `frontend/src/services/api.ts` | API base URL: `/trade-bot/api` |
| `Dockerfile` | Backend image (amazoncorretto:17) |
| `frontend/nginx/Dockerfile` | Frontend image (multi-stage: node → nginx) |

### Port Mapping

| Service | Container Port | Host Port |
|---------|---------------|-----------|
| Backend (trade-bot) | 8080 | 8081 |
| Frontend (nginx) | 80 | 8082 |

---

## Fresh Server Deployment (From Scratch)

Полная инструкция для развертывания на новом сервере с нуля.

### Prerequisites

На сервере должны быть установлены:
- Docker и Docker Compose
- Git (для клонирования репозитория)

```bash
# Install Docker (Ubuntu/Debian)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Install Docker Compose (if not included with Docker)
sudo apt install docker-compose-plugin -y

# Verify installation
docker --version
docker compose version
```

### Step-by-Step Deployment

```bash
# 1. Clone the repository
git clone <your-repo-url> trade-bot
cd trade-bot

# 2. Copy the pre-built JAR (if not building on server)
# Option A: Build on server
./gradlew clean build
cp build/libs/trade-bot-2.0-SNAPSHOT.jar .

# Option B: Copy from local machine
# scp build/libs/trade-bot-2.0-SNAPSHOT.jar user@server:/path/to/trade-bot/

# 3. Configure environment (optional - for OAuth)
cat > .env << 'EOF'
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
REQUIRE_AUTH=false
EOF

# 4. Ensure required directories exist
mkdir -p database exchangeBots exchangeConfigs logging pages results

# 5. Build and start containers
docker compose build --no-cache
docker compose up -d

# 6. Verify deployment
docker compose ps
curl http://localhost:8081/actuator/health
curl http://localhost:8082/health
```

### What Gets Deployed

После запуска будут доступны:
- **Frontend**: `http://server-ip:8082/trade-bot/` (React SPA через Nginx)
- **Backend API**: `http://server-ip:8081/` (Spring Boot REST API)
- **Health checks**: `http://server-ip:8081/actuator/health`, `http://server-ip:8082/health`

### Changing Ports

Если порты 8081/8082 заняты, измените в `docker-compose.yml`:

```yaml
services:
  trade-bot:
    ports:
      - "NEW_BACKEND_PORT:8080"
  frontend:
    ports:
      - "NEW_FRONTEND_PORT:80"
```

### Important: Nginx API Path Configuration

Frontend использует базовый путь `/trade-bot/` (настроено в `vite.config.ts`), поэтому API запросы идут на `/trade-bot/api/*`.

**Nginx конфигурация должна использовать `location /trade-bot/api/`**, а не `location /api/`:

```nginx
# Правильно (nginx-ssl.conf)
location /trade-bot/api/ {
    proxy_pass http://trade-bot:8080/;
    proxy_set_header Authorization $http_authorization;  # Важно для Basic Auth!
    # ... остальные настройки
}
```

**Обрабатываемые endpoints:**
- POST: `/load_bot`, `/start_bot`, `/resume_bot`, `/create_bot`, `/endpoint/trade`, `/get_necessary_balance`, `/emulate`
- GET: `/positions`, `/orders`

### SSL Deployment (docker-compose-ssl.yml)

Для HTTPS используйте `docker-compose-ssl.yml`. SSL сертификаты монтируются с хоста в контейнер frontend (nginx).

**Важно про порты:**
- HTTP: `8082:80` (редирект на HTTPS)
- HTTPS: `8443:443`

#### Сертификаты Let's Encrypt

По умолчанию в `docker-compose-ssl.yml` сертификаты ожидаются в:
`/home/asus/AI-SIGNAL/alpha_horizon_py_trading/certbot/conf` (см. `LETSENCRYPT_PATH`).

**Рекомендуемый способ (зафиксировать путь в .env проекта):**
```bash
# в каталоге проекта (рядом с docker-compose-ssl.yml)
echo "LETSENCRYPT_PATH=/home/asus/AI-SIGNAL/alpha_horizon_py_trading/certbot/conf" >> .env

docker compose -f docker-compose-ssl.yml up -d
```

**Разовый запуск (через export):**
```bash
export LETSENCRYPT_PATH=/home/asus/AI-SIGNAL/alpha_horizon_py_trading/certbot/conf
docker compose -f docker-compose-ssl.yml up -d
```

**Проверка, что сертификаты реально примонтировались внутрь контейнера:**
```bash
docker inspect trade-bot-frontend --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}'
docker exec trade-bot-frontend ls -la /etc/letsencrypt/live/tradebotalphahorizon.duckdns.org/
```

**Структура сертификатов (внутри контейнера):**
```
/etc/letsencrypt/
└── live/
    └── tradebotalphahorizon.duckdns.org/
        ├── fullchain.pem
        └── privkey.pem
```

> Если `LETSENCRYPT_PATH` уже задан в окружении (например, ранее был `/path/to/certs`), он может переопределить дефолтный путь. В таком случае задайте правильный путь в `.env` или в текущей shell-сессии перед `docker compose`.

---

#### If you use an external reverse-proxy (alpha-horizon-nginx-prod) on :443

Если домен `tradebotalphahorizon.duckdns.org` обслуживается *внешним* nginx (например, контейнер `alpha-horizon-nginx-prod`, который слушает `80/443`), то важно:

1) **Docker network**: внешний nginx должен быть подключен к сети `trade-bot_trade-bot-network`, иначе он не сможет резолвить `trade-bot-frontend` / `trade-bot-backend` и будет `502 (Host is unreachable)`.

```bash
# посмотреть сети проекта trade-bot
docker network ls | grep trade-bot

# подключить внешний nginx к сети trade-bot (имя сети может отличаться)
docker network connect trade-bot_trade-bot-network alpha-horizon-nginx-prod

# проверить резолвинг и доступность апстримов из контейнера nginx
docker exec alpha-horizon-nginx-prod sh -lc 'getent hosts trade-bot-frontend trade-bot-backend || true'
docker exec alpha-horizon-nginx-prod sh -lc 'curl -fsS http://trade-bot-backend:8080/actuator/health'
```

2) **Frontend proxy_pass**: если вы проксируете `/trade-bot/` на `trade-bot-frontend`, не направляйте внешний nginx на порт `80` внутри `trade-bot-frontend` при SSL-сборке: на 80 там включён `301` редирект на HTTPS, и вы получите редирект на корень домена.

Правильный вариант — проксировать `/trade-bot/` на HTTPS-порт контейнера `trade-bot-frontend`:

```nginx
location /trade-bot/ {
    auth_basic "Trade Bot Access";
    auth_basic_user_file /etc/nginx/.htpasswd_tradebot;

    # важно: https + порт 443
    proxy_pass https://trade-bot-frontend:443/;
    proxy_ssl_server_name on;
}
```

> Важно не путать схему и порт. Конфигурация вида `proxy_pass http://trade-bot-frontend:443/;` неверна.

---

## Production Deployment (Quick Guide)

Краткая инструкция по развертыванию на production сервере.

### 1. Сборка Backend

```bash
# Очистка и сборка проекта
./gradlew clean build

# Результат: build/libs/trade-bot-2.0-SNAPSHOT.jar
```

### 2. Сборка Frontend

```bash
# Перейти в директорию frontend
cd frontend

# Установить зависимости (если не установлены)
npm install

# Собрать production build
npm run build

# Результат: frontend/dist/
cd ..
```

### 3. Отправка файлов на сервер

**Вариант A: Через SCP (с локальной машины)**

* Отправить весь проект
```bash
scp -r /path/to/trade-bot user@server:/path/to/destination/
```
* Или только необходимые файлы
```bash
scp build/libs/trade-bot-2.0-SNAPSHOT.jar user@server:/path/to/trade-bot/
scp -r frontend/dist user@server:/path/to/trade-bot/frontend/dist/
scp docker-compose.yml user@server:/path/to/trade-bot/
scp Dockerfile user@server:/path/to/trade-bot/
```

**Вариант B: Через Git (рекомендуется)**
```bash
# На сервере
git pull origin main
./gradlew clean build
cd frontend && npm run build && cd ..
```

### 4. Настройка Google OAuth

**Подробная инструкция:** [GOOGLE_OAUTH_SETUP.md](docs/GOOGLE_OAUTH_SETUP.md)

**Быстрая настройка:**
```bash
# Создать .env файл
nano .env

# Добавить:
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
REQUIRE_AUTH=false  # true для включения авторизации
```

**Важно:** Session cookie уже настроен как `trade-bot-session` для избежания конфликтов с другими приложениями.

### 5. Запуск Docker на сервере

```bash
# Остановить предыдущую версию (если запущена)
docker compose down

# Собрать и запустить
docker compose build --no-cache
docker compose up -d

# Проверить логи
docker compose logs -f
```

### Быстрый редеплой только Backend (не трогая Frontend)

Backend сервис в `docker-compose.yml` / `docker-compose-ssl.yml` называется `trade-bot` (контейнер `trade-bot-backend`).

**Вариант A (рекомендуется): пересобрать backend и пересоздать только backend-контейнер**

Без SSL:
```bash
git pull
./gradlew clean build

docker compose build trade-bot
docker compose up -d --no-deps --force-recreate trade-bot

docker compose ps
docker logs -f trade-bot-backend
curl -sS http://localhost:8081/actuator/health
```

С SSL:
```bash
git pull
./gradlew clean build

docker compose -f docker-compose-ssl.yml build trade-bot
docker compose -f docker-compose-ssl.yml up -d --no-deps --force-recreate trade-bot

docker compose -f docker-compose-ssl.yml ps
docker logs -f trade-bot-backend
curl -sS http://localhost:8081/actuator/health
```

**Вариант B: просто рестартнуть backend (без пересборки образа)**
```bash
docker compose restart trade-bot
# или (SSL)
docker compose -f docker-compose-ssl.yml restart trade-bot
```

### Доступ к приложению

После запуска приложение будет доступно:
- **Backend API:** `http://server-ip:8081`
- **Frontend:** `http://server-ip:8082`

Для production с доменом (SSL compose):
- **Backend API:** `http://trade-bot-backend:8080` (внутри docker сети) или `http://SERVER_IP:8081` (с хоста)
- **Frontend HTTPS:** `https://tradebotalphahorizon.duckdns.org:8443`  
  (в `docker-compose-ssl.yml` HTTPS проброшен как `8443:443`; порт `8082` — это HTTP и он делает редирект на HTTPS)

> Если у вас есть внешний reverse-proxy (например, отдельный nginx на хосте на 443), то обычно он проксирует на `8443` (или сразу на `8082`/`80`), а TLS завершается на внешнем прокси.

### Проверка работы

```bash
# Статус контейнеров
docker compose ps

# Логи в реальном времени
docker compose logs -f

# Проверка endpoint
curl http://localhost:8081/actuator/health
```

---

## Run without Docker

This guide explains how to run the application on a Linux machine without using Docker.

### 1. Environment Preparation

First, you need to install the necessary tools: Java for running the application and `tmux` for managing the process in the background.

**Install Java (OpenJDK 17):**
```bash
# Update package list and install Java 17
sudo apt update
sudo apt install openjdk-17-jdk -y

# Verify the installation
java -version
```

**Install tmux:**
`tmux` is a terminal multiplexer that allows you to run processes in persistent sessions, so the bot can continue running even after you disconnect.
```bash
# Install tmux
sudo apt install tmux -y
```

### 2. Build the Application

Use the Gradle wrapper included in the project to build the application from source. This will compile the code and package it into an executable JAR file.

```bash
# Make the gradlew script executable
chmod +x ./gradlew

# Build the project
./gradlew build
```
The final JAR file will be located in the `build/libs/` directory.

### 3. Run the Application

Once the project is built, you can run it using the `java -jar` command. It's recommended to run it inside a `tmux` session to keep it active in the background.

**Start a tmux session:**
```bash
# Create a new session named "trade-bot"
tmux new -s trade-bot
```

**Run the bot:**
Inside the `tmux` session, run the following command. Make sure to replace `trade-bot-0.0.1-SNAPSHOT.jar` with the actual name of the JAR file in your `build/libs/` directory.

```bash
# Run the application JAR
java -jar build/libs/trade-bot-0.0.1-SNAPSHOT.jar
```

The bot is now running.

### 4. Managing the Session

- **Detach from the session:** To leave the bot running in the background, press `Ctrl+b` and then `d`.
- **Re-attach to the session:** To check the bot's console output or manage it, re-attach to the session:
  ```bash
  tmux attach -t trade-bot
  ```
- **Stop the bot:** Attach to the session and press `Ctrl+c` to stop the Java process.

---

## Docker Commands Reference

**Rebuild gradle and restart (recommended after updates):**
```bash
./gradlew clean build
docker compose down
docker compose build --no-cache
docker compose up -d
```

### Redeploy only backend (trade-bot) without touching frontend

```bash
./gradlew clean build

docker compose build trade-bot
docker compose up -d --no-deps --force-recreate trade-bot

# logs
docker logs -f trade-bot-backend
```

(SSL compose file)
```bash
docker compose -f docker-compose-ssl.yml build trade-bot
docker compose -f docker-compose-ssl.yml up -d --no-deps --force-recreate trade-bot
```

### Basic Operations

**Start the application:**
```bash
docker compose up -d
```

**Stop the application:**
```bash
docker compose down
```

**Restart the application:**
```bash
docker compose restart
```

### Building and Updating

**Build from scratch (after code changes):**
```bash
docker compose build --no-cache
docker compose up -d
```

**Rebuild and restart (recommended after updates):**
```bash
docker compose down
docker compose build --no-cache
docker compose up -d
```

**Force recreate containers:**
```bash
docker compose up -d --force-recreate
```

### Monitoring and Logs

**View live logs:**
```bash
docker compose logs -f
```

**View logs with limited lines:**
```bash
docker compose logs -f --tail=100
```

**View logs for specific timeframe:**
```bash
docker compose logs --since="1h"
```

**View logs without following:**
```bash
docker compose logs --tail=50
```

### Debugging and Maintenance

**Check container status:**
```bash
docker compose ps
```

**Access container shell (if needed):**
```bash
docker compose exec trade-bot sh
```

**View container resource usage:**
```bash
docker stats
```

**Clean up unused Docker resources:**
```bash
docker system prune -f
```

**Remove all containers and rebuild:**
```bash
docker compose down --rmi all --volumes
docker compose up -d --build
```

### Troubleshooting

**502 Bad Gateway (nginx can't reach backend):**

```bash
# 1) Check container status
docker compose -f docker-compose-ssl.yml ps

# 2) Backend logs
docker logs trade-bot-backend --tail=50

# 3) Backend health from host
curl -v http://localhost:8081/actuator/health

# 4) Test connectivity from frontend to backend (inside docker network)
docker exec trade-bot-frontend wget -qO- http://trade-bot:8080/actuator/health

# 5) Frontend health (HTTPS)
curl -k https://localhost:8443/health
```

Notes:
- Backend healthcheck uses `curl` (the backend image does not include `wget`).
- Frontend (SSL) healthcheck uses `https://127.0.0.1/health` because BusyBox `wget` may resolve `localhost` to `::1` first and fail if nginx isn't listening on IPv6.

**Common 502 causes and fixes:**
| Cause | Fix |
|-------|-----|
| Backend not started | `docker compose -f docker-compose-ssl.yml up -d trade-bot` |
| Backend crashed | Check logs: `docker logs trade-bot-backend` |
| Network mismatch | Restart all: `docker compose -f docker-compose-ssl.yml down && docker compose -f docker-compose-ssl.yml up -d` |
| JAR file missing | Build: `./gradlew clean build && cp build/libs/trade-bot-2.0-SNAPSHOT.jar .` |
| Wrong port in nginx | Verify nginx config uses `proxy_pass http://trade-bot:8080/` |

**Quick fix for 502:**
```bash
# Restart everything
docker compose -f docker-compose-ssl.yml down
docker compose -f docker-compose-ssl.yml up -d

# Wait for backend to start (startup can take ~45s, healthcheck start_period=60s)
sleep 70
docker compose -f docker-compose-ssl.yml ps

# Backend health (host)
curl -sS http://localhost:8081/actuator/health

# Frontend health (HTTPS)
curl -k https://localhost:8443/health
```

**If you encounter "error sign!" or API issues:**
1. Stop the container: `docker compose down`
2. Rebuild without cache: `docker compose build --no-cache`
3. Start again: `docker compose up -d`
4. Monitor logs: `docker compose logs -f`

**If container exits with ClassNotFoundException:**
1. Clean build locally: `./gradlew clean build`
2. Rebuild Docker image: `docker compose build --no-cache`
3. Start container: `docker compose up -d`

**SSL certificate errors (nginx won't start):**
```bash
# 1) Check if certs exist on host
ls -la ~/AI-SIGNAL/alpha_horizon_py_trading/certbot/conf/live/tradebotalphahorizon.duckdns.org/

# 2) Ensure LETSENCRYPT_PATH points to the certbot "conf" directory
# Recommended: persist in .env
echo "LETSENCRYPT_PATH=/home/asus/AI-SIGNAL/alpha_horizon_py_trading/certbot/conf" >> .env

# Or temporarily:
export LETSENCRYPT_PATH=/home/asus/AI-SIGNAL/alpha_horizon_py_trading/certbot/conf

# 3) Recreate frontend
docker compose -f docker-compose-ssl.yml up -d --force-recreate frontend

# 4) Verify mount inside container
docker exec trade-bot-frontend ls -la /etc/letsencrypt/live/tradebotalphahorizon.duckdns.org/
```

> Если в `docker inspect trade-bot-frontend` видно, что в `/etc/letsencrypt` примонтирован неверный путь (например, `/path/to/certs`), значит `LETSENCRYPT_PATH` переопределён окружением. Исправьте `.env` или переменную окружения и пересоздайте контейнер.

**For persistent data issues:**
```bash
# Remove volumes and start fresh
docker compose down --volumes
docker compose up -d
```

**Gate.io API: "No enum constant SIDE.ASK/BID" errors:**

Эта ошибка возникает при парсинге ордеров Gate.io:
```
ERROR: Failed to parse order ... No enum constant bot.trade.exchanges.clients.SIDE.ASK
ERROR: Failed to parse order ... No enum constant bot.trade.exchanges.clients.SIDE.BID
```

**Причина:** Gate.io API через XChange библиотеку возвращает `OrderType.BID`/`OrderType.ASK`, но код пытался конвертировать через строковое имя в enum `SIDE` (который содержит `BUY`/`SELL`).

**Исправление (уже применено в коде):**
- Файл: `src/main/kotlin/bot/trade/exchanges/clients/ClientGate.kt`
- Изменено: `SIDE.valueOf(it.type.name)` → `SIDE.valueOf(orderType)`
- Используется правильный метод конвертации `SIDE.valueOf(OrderType)`, который преобразует:
  - `OrderType.BID` → `SIDE.BUY`
  - `OrderType.ASK` → `SIDE.SELL`

**Проверка исправления после деплоя:**
```bash
# Проверить логи на отсутствие ошибок парсинга
docker logs trade-bot-backend --tail=100 | grep -i "Failed to parse order"

# Должно быть пусто, если ошибка исправлена
```

### Quick Commands Summary

| Command | Description |
|---------|-------------|
| `docker compose up -d` | Start in background |
| `docker compose down` | Stop and remove containers |
| `docker compose logs -f` | Follow logs in real-time |
| `docker compose build --no-cache` | Rebuild without cache |
| `docker compose restart` | Restart containers |
| `docker compose ps` | Show container status |

---

## Logs Analysis

### Fetching Logs from Server for Local Analysis

Для удобного анализа логов с production сервера локально, используйте скрипт `fetch_logs.sh`.

#### Использование скрипта fetch_logs.sh

**1. Убедитесь, что скрипт исполняемый:**
```bash
chmod +x fetch_logs.sh
```

**2. Запустите скрипт:**
```bash
./fetch_logs.sh
```

**3. Результат:**
Скрипт создаст директорию `logs_from_server/logs_YYYYMMDD_HHMMSS/` с следующими файлами:
- `docker_backend.log` - Логи Backend контейнера (последние 5000 строк)
- `docker_frontend.log` - Логи Frontend контейнера (последние 5000 строк)
- `docker_status.txt` - Статус и конфигурация Docker контейнеров
- `system_info.txt` - Системная информация сервера (CPU, память, диск, сеть)
- `logging/` - Директория с логами приложения из volume (если есть)
- `README.txt` - Описание собранных логов

#### Что делает скрипт:

1. **Собирает Docker логи** контейнеров Backend и Frontend
2. **Получает логи приложения** из volume `logging/` (если есть файловые логи)
3. **Собирает статус контейнеров** (docker ps, docker inspect, docker stats)
4. **Получает системную информацию** (память, диск, процессы, сеть)
5. **Автоматически ищет ошибки** и выводит их в консоль

#### Быстрый анализ логов:

```bash
# Поиск всех ошибок в Backend
grep -i "error\|exception\|failed" logs_from_server/logs_*/docker_backend.log

# Последние 100 строк Backend
tail -100 logs_from_server/logs_*/docker_backend.log

# Поиск конкретной ошибки
grep -A 5 -B 5 "NullPointerException" logs_from_server/logs_*/docker_backend.log

# Подсчет ошибок по типам
grep -i "exception" logs_from_server/logs_*/docker_backend.log | awk -F: '{print $2}' | sort | uniq -c | sort -rn

# Анализ использования памяти
cat logs_from_server/logs_*/system_info.txt | grep -A 10 "Memory Usage"

# Проверка статуса контейнеров
cat logs_from_server/logs_*/docker_status.txt | grep -A 10 "Docker Compose Status"
```

#### Настройка скрипта:

Отредактируйте переменные в начале `fetch_logs.sh`:
```bash
SERVER="tradebotalphahorizon.duckdns.org"  # Адрес сервера
SERVER_USER="asus"                         # Пользователь на сервере
SERVER_PATH="/home/asus/trade-bot"        # Путь к проекту на сервере
```

#### Альтернативные способы получения логов:

**Просмотр логов напрямую через SSH:**
```bash
# Backend логи (последние 100 строк)
ssh asus@tradebotalphahorizon.duckdns.org 'docker logs trade-bot-backend --tail=100'

# Frontend логи
ssh asus@tradebotalphahorizon.duckdns.org 'docker logs trade-bot-frontend --tail=100'

# Логи в реальном времени (следить за новыми записями)
ssh asus@tradebotalphahorizon.duckdns.org 'cd /home/asus/trade-bot && docker compose logs -f'
```

**Получение только последних ошибок:**
```bash
# Только ошибки из Backend за последние 500 строк
ssh asus@tradebotalphahorizon.duckdns.org 'docker logs trade-bot-backend --tail=500' | grep -i "error\|exception"
```

**Скачивание конкретного файла логов:**
```bash
# Если приложение пишет логи в файлы
scp asus@tradebotalphahorizon.duckdns.org:/home/asus/trade-bot/logging/application.log ./local_app.log
```

#### Анализ типичных проблем:

**1. Проблемы с подключением к Exchange API:**
```bash
grep -i "connection\|timeout\|refused" logs_from_server/logs_*/docker_backend.log
```

**2. Ошибки базы данных:**
```bash
grep -i "sql\|database\|jdbc" logs_from_server/logs_*/docker_backend.log
```

**3. Ошибки аутентификации:**
```bash
grep -i "auth\|unauthorized\|forbidden\|401\|403" logs_from_server/logs_*/docker_backend.log
```

**4. Out of Memory ошибки:**
```bash
grep -i "outofmemory\|heap space" logs_from_server/logs_*/docker_backend.log
cat logs_from_server/logs_*/system_info.txt | grep -A 5 "Memory Usage"
```

**5. Проблемы с сетью между контейнерами:**
```bash
grep -i "502\|bad gateway\|connection refused" logs_from_server/logs_*/docker_frontend.log
cat logs_from_server/logs_*/docker_status.txt | grep -A 20 "Docker Networks"
```

#### Мониторинг в реальном времени:

Для непрерывного мониторинга логов на сервере:
```bash
# Открыть SSH сессию и следить за логами
ssh asus@tradebotalphahorizon.duckdns.org

# На сервере
cd /home/asus/trade-bot
docker compose -f docker-compose-ssl.yml logs -f --tail=100

# Или только Backend
docker logs -f trade-bot-backend

# Фильтровать только ошибки в реальном времени
docker logs -f trade-bot-backend 2>&1 | grep -i --line-buffered "error\|exception"
```

#### Автоматический сбор логов по расписанию:

Для регулярного сбора логов добавьте в crontab:
```bash
# Открыть crontab
crontab -e

# Добавить строку (каждый день в 3:00 ночи)
0 3 * * * /path/to/trade-bot/fetch_logs.sh >> /path/to/trade-bot/fetch_logs.log 2>&1
```

#### Очистка старых логов:

```bash
# Удалить логи старше 7 дней
find logs_from_server/ -type d -name "logs_*" -mtime +7 -exec rm -rf {} \;

# Посмотреть размер всех собранных логов
du -sh logs_from_server/
```

---

# Params Description

## common.conf

| Параметр | Описание | Пример |
|---|---|---|
| bot_properties.telegram_proxy.use_proxy | Использовать прокси для телеграм бота | true/false |
| bot_properties.telegram_proxy.host | ip адрес прокси | |
| bot_properties.telegram_proxy.port_host | ip адрес прокси:порт прокси | |
| bot_properties.telegram_proxy.port | порт | |
| bot_properties.telegram_proxy.user | имя юзера | |
| bot_properties.hma_address_calc | | |
| bot_properties.bot.admin_id | ID админа (пользователь который может взаимодействовать с ботом) | |
| bot_properties.bot.chat_id | ID чата в который будут лететь сигналы | |
| bot_properties.bot.bot_name | имя бота | |
| bot_properties.bot.bot_token | токен бота | |

## collect_exchange_candlestick.conf

| Параметр | Описание | Пример |
|---|---|---|
| mark_gap | | |
| path_out | Путь для сохранения файлов со свечками | |
| path_db | Путь к базе данных со свечками | |
| first_day_for_check | Первый день для проверки свечек | |
| ignore_pairs | Список пар для игнорирования | |

## settings.json

| Параметр | Описание | Пример |
|---|---|---|
| flow_name | Имя потока | |
| symbol | Торговая пара | |
| exchange_type | Тип биржи | |
| orderBalanceType | Тип баланса для ордеров (first, second) | |
| countOfDigitsAfterDotForAmount | Количество знаков после запятой для суммы | |
| countOfDigitsAfterDotForPrice | Количество знаков после запятой для цены | |
| strategy_type | Тип стратегии (LONG, SHORT, BOTH) | |
| order_type | Тип ордера (MARKET, LIMIT) | |
| parameters | Параметры торговли | |
| trend_detector | Детектор тренда | |
| min_order_amount | Минимальная сумма ордера | |
| market_type | Тип рынка | |
| market_type_comment | Комментарий к типу рынка | |
| strategy_type_comment | Комментарий к типу стратегии | |
| auto_balance | Автоматический баланс | true/false |
| min_order_amount.amount | Сумма минимального ордера | |
| min_order_amount.countOfDigitsAfterDotForAmount | Количество знаков после запятой для минимальной суммы ордера | |
| trend_detector.not_auto_calc_trend | Не считать тренд автоматически | true/false |
| trend_detector.rsi1 | Первый RSI | |
| trend_detector.rsi2 | Второй RSI | |
| trend_detector.hma_parameters | Параметры HMA | |
| trend_detector.input_kline_interval | Интервал свечей | |
| trend_detector.rsi1.rsi_period | Период RSI для первого RSI | |
| trend_detector.rsi1.time_frame | Таймфрейм для первого RSI | |
| trend_detector.rsi2.rsi_period | Период RSI для второго RSI | |
| trend_detector.rsi2.time_frame | Таймфрейм для второго RSI | |
| trend_detector.hma_parameters.hma1_period | Период HMA1 | |
| trend_detector.hma_parameters.hma2_period | Период HMA2 | |
| trend_detector.hma_parameters.hma3_period | Период HMA3 | |
| trend_detector.hma_parameters.time_frame | Таймфрейм для HMA | |
| parameters.long_parameters | Параметры для LONG стратегии | |
| parameters.short_parameters | Параметры для SHORT стратегии | |
| parameters.long_parameters.trading_range | Торговый диапазон для LONG стратегии | |
| parameters.long_parameters.in_order_quantity | Количество в ордере для LONG стратегии | |
| parameters.long_parameters.in_order_distance | Расстояние между ордерами для LONG стратегии | |
| parameters.long_parameters.trailing_in_order_distance | Трейлинг расстояние для LONG стратегии | |
| parameters.long_parameters.trigger_in_order_distance | Триггерное расстояние для LONG стратегии | |
| parameters.long_parameters.trigger_distance | Триггерное расстояние для LONG стратегии | |
| parameters.long_parameters.min_tp_distance | Минимальное расстояние для тейк-профита для LONG стратегии | |
| parameters.long_parameters.max_tp_distance | Максимальное расстояние для тейк-профита для LONG стратегии | |
| parameters.long_parameters.max_trigger_count | Максимальное количество триггеров для LONG стратегии | |
| parameters.long_parameters.set_close_orders | Установить ордера на закрытие для LONG стратегии | true/false |
| parameters.long_parameters.counter_distance | Расстояние для контр-ордера для LONG стратегии | |
| parameters.long_parameters.use_realized_pnl_in_calc_profit | Использовать реализованный PNL при расчете прибыли для LONG стратегии | true/false |
| parameters.long_parameters.entire_tp | Весь тейк-профит для LONG стратегии | |
| parameters.short_parameters.trading_range | Торговый диапазон для SHORT стратегии | |
| parameters.short_parameters.in_order_quantity | Количество в ордере для SHORT стратегии | |
| parameters.short_parameters.in_order_distance | Расстояние между ордерами для SHORT стратегии | |
| parameters.short_parameters.trailing_in_order_distance | Трейлинг расстояние для SHORT стратегии | |
| parameters.short_parameters.trigger_in_order_distance | Триггерное расстояние для SHORT стратегии | |
| parameters.short_parameters.trigger_distance | Триггерное расстояние для SHORT стратегии | |
| parameters.short_parameters.min_tp_distance | Минимальное расстояние для тейк-профита для SHORT стратегии | |
| parameters.short_parameters.max_tp_distance | Максимальное расстояние для тейк-профита для SHORT стратегии | |
| parameters.short_parameters.max_trigger_count | Максимальное количество триггеров для SHORT стратегии | |
| parameters.short_parameters.set_close_orders | Установить ордера на закрытие для SHORT стратегии | true/false |
| parameters.short_parameters.counter_distance | Расстояние для контр-ордера для SHORT стратегии | |
| parameters.short_parameters.use_realized_pnl_in_calc_profit | Использовать реализованный PNL при расчете прибыли для SHORT стратегии | true/false |
| parameters.short_parameters.entire_tp | Весь тейк-профит для SHORT стратегии | |
| parameters.long_parameters.entire_tp.max_trigger_amount | Максимальное количество триггеров для всего тейк-профита для LONG стратегии | |
| parameters.long_parameters.entire_tp.max_profit_percent | Максимальный процент прибыли для всего тейк-профита для LONG стратегии | |
| parameters.long_parameters.entire_tp.max_loss_percent | Максимальный процент убытка для всего тейк-профита для LONG стратегии | |
| parameters.long_parameters.entire_tp.enabled_in_hedge | Включить весь тейк-профит в хеджировании для LONG стратегии | true/false |
| parameters.long_parameters.entire_tp.enabled | Включить весь тейк-профит для LONG стратегии | true/false |
| parameters.long_parameters.entire_tp.tp_distance | Расстояние для тейк-профита для всего тейк-профита для LONG стратегии | |
| parameters.short_parameters.entire_tp.max_trigger_amount | Максимальное количество триггеров для всего тейк-профита для SHORT стратегии | |
| parameters.short_parameters.entire_tp.max_profit_percent | Максимальный процент прибыли для всего тейк-профита для SHORT стратегии | |
| parameters.short_parameters.entire_tp.max_loss_percent | Максимальный процент убытка для всего тейк-профита для SHORT стратегии | |
| parameters.short_parameters.entire_tp.enabled_in_hedge | Включить весь тейк-профит в хеджировании для SHORT стратегии | true/false |
| parameters.short_parameters.entire_tp.enabled | Включить весь тейк-профит для SHORT стратегии | true/false |
| parameters.short_parameters.entire_tp.tp_distance | Расстояние для тейк-профита для всего тейк-профита для SHORT стратегии | |
