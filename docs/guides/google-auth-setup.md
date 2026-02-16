---
title: Google OAuth Authentication Setup
description: Настройка авторизации trade-bot через nginx auth_request и Python-проект (alpha_horizon_py_trading)
tags: [security, oauth, nginx, docker, deployment]
status: active
related_files: [docs/guides/google-oauth-setup.md, docker-compose.prod.yml, frontend/nginx/nginx-ssl.conf]
---

# Google OAuth Authentication Setup

## Context

Это **актуальный** документ по авторизации. Trade-bot использует auth через nginx `auth_request` + Python-проект.
Альтернативный подход (прямой Spring Security OAuth на порту 8081) описан в `google-oauth-setup.md`, но **НЕ используется**.

## Overview

Trade-bot использует авторизацию через Python проект (alpha_horizon_py_trading).
Это означает, что:
- Не нужно создавать отдельное OAuth приложение для trade-bot
- Trade-bot защищен той же авторизацией что и основное приложение
- Пользователи логинятся один раз и имеют доступ к обоим приложениям

## Архитектура

```
[User Browser]
       |
       v
[Nginx (Python project)] -- port 80/443
       |
       +-- /auth/* --> [Python Backend] (Google OAuth)
       |
       +-- /trade-bot/* --> [trade-bot-frontend] (React)
       |
       +-- /trade-bot/api/* --> [trade-bot-backend] (Spring Boot)
```

Nginx проверяет авторизацию через `auth_request` директиву:
- Перед каждым запросом к `/trade-bot/*` nginx вызывает `/auth/verify`
- Если пользователь не авторизован, его редиректит на `/auth/login`

## Настройка (уже выполнена)

### 1. Google Cloud Console

OAuth credentials уже созданы в Python проекте:
- **Client ID**: 
- **Redirect URI**: 

### 2. Разрешенные email-адреса

Список разрешенных email находится в `.env` Python проекта:
```
/home/asus/Python/_ai_signals/AI-SIGNAL/alpha_horizon_py_trading/.env
```

Переменная `ALLOWED_EMAILS`:
```env
ALLOWED_EMAILS=petrovma92@gmail.com,tester205testovich@gmail.com
```

Чтобы добавить нового пользователя:
1. Добавь email в `ALLOWED_EMAILS` (через запятую)
2. Перезапусти Python backend: `docker-compose restart backend`

### 3. Сетевая конфигурация

Trade-bot контейнеры должны быть в сети `alpha-network`:

```yaml
# docker-compose.prod.yml
networks:
  alpha-network:
    external: true
    name: alpha_horizon_py_trading_alpha-network
```

**Важно**: Порты НЕ должны быть экспонированы напрямую!

## Деплой

### Полный деплой с нуля

```bash
# 1. Убедись что Python проект запущен
cd /home/asus/Python/_ai_signals/AI-SIGNAL/alpha_horizon_py_trading
docker-compose -f docker-compose.prod.yml up -d

# 2. Собери trade-bot
cd /mnt/data/home/asus/java/trade-bot
./gradlew clean build

# 3. Собери Docker образы
docker build -t trade-bot-backend:latest .
cd frontend && docker build -f nginx/Dockerfile -t trade-bot-frontend:latest .

# 4. Запусти trade-bot (подключится к сети Python проекта)
cd /mnt/data/home/asus/java/trade-bot
docker-compose -f docker-compose.prod.yml up -d

# 5. Проверь что контейнеры в правильной сети
docker network inspect alpha_horizon_py_trading_alpha-network
```

### Быстрый редеплой (только trade-bot)

```bash
cd /mnt/data/home/asus/java/trade-bot
docker-compose -f docker-compose.prod.yml down
./gradlew clean build
docker build -t trade-bot-backend:latest .
cd frontend && docker build -f nginx/Dockerfile -t trade-bot-frontend:latest . && cd ..
docker-compose -f docker-compose.prod.yml up -d
```

## Проверка

### 1. Проверить что порты закрыты

```bash
# Эти команды НЕ должны работать (порты закрыты):
curl http://localhost:8081/positions  # Connection refused
curl http://localhost:8082/trade-bot/ # Connection refused
```

### 2. Проверить доступ через nginx

```bash
# Без авторизации - должен редиректить на логин:
curl -I https://tradebotalphahorizon.duckdns.org/trade-bot/
# HTTP/1.1 302 Found
# Location: /auth/login?next=/trade-bot/

# С авторизацией (cookie из браузера):
curl -b "session=..." https://tradebotalphahorizon.duckdns.org/trade-bot/
# HTTP/1.1 200 OK
```

### 3. Проверить контейнеры

```bash
docker ps | grep trade-bot
# trade-bot-backend   Up
# trade-bot-frontend  Up

docker logs trade-bot-backend --tail 20
docker logs trade-bot-frontend --tail 20
```

## Nginx proxy_pass: критичный нюанс с переменными

При использовании переменных в `proxy_pass` для DNS re-resolution nginx **НЕ выполняет**
подстановку URI. Вместо этого URI из директивы **заменяет** оригинальный URI запроса.

**Неправильно** (все запросы получат URI `/trade-bot/`, JS/CSS файлы не будут загружаться):
```nginx
location /trade-bot/ {
    set $trade_bot_fe trade-bot-frontend;
    proxy_pass https://$trade_bot_fe/trade-bot/;   # BAD: URI /trade-bot/ заменит оригинальный URI
}
```

**Правильно** (оригинальный URI сохраняется):
```nginx
# Frontend — без URI в proxy_pass, оригинальный путь сохраняется
location /trade-bot/ {
    set $trade_bot_fe trade-bot-frontend;
    proxy_pass https://$trade_bot_fe;              # GOOD: /trade-bot/assets/x.js → /trade-bot/assets/x.js
}

# API — rewrite для удаления префикса, proxy_pass без URI
location /trade-bot/api/ {
    set $trade_bot_api trade-bot-backend:8080;
    rewrite ^/trade-bot/api/(.*) /$1 break;        # /trade-bot/api/positions → /positions
    proxy_pass http://$trade_bot_api;               # GOOD: передаёт rewritten URI
}
```

**Почему**: из документации nginx —
> "When variables are used in proxy_pass... if URI is specified in the directive,
> it is passed to the server as is, replacing the original request URI."

Конфигурация находится в Python проекте:
`/home/asus/AI-SIGNAL/alpha_horizon_py_trading/nginx/conf.d/prod.conf`

## Troubleshooting

### "Connection refused" при доступе к trade-bot

1. Проверь что контейнеры запущены: `docker ps`
2. Проверь сеть: `docker network inspect alpha_horizon_py_trading_alpha-network`
3. Контейнеры `trade-bot-backend` и `trade-bot-frontend` должны быть в списке

### "502 Bad Gateway" в nginx

1. Проверь логи nginx: `docker logs alpha-horizon-nginx-prod`
2. Проверь что trade-bot-backend доступен из сети:
   ```bash
   docker exec alpha-horizon-nginx-prod wget -qO- http://trade-bot-backend:8080/actuator/health
   ```

### Пользователь не может залогиниться

1. Проверь что email в `ALLOWED_EMAILS` в `.env` Python проекта
2. Проверь логи Python backend: `docker logs alpha-horizon-backend-prod`

## Безопасность

- **Никогда** не экспонируй порты 8081/8082 напрямую
- **Никогда** не коммить `.env` файлы с секретами
- Регулярно проверяй список `ALLOWED_EMAILS`
- Все запросы должны идти только через nginx с HTTPS
