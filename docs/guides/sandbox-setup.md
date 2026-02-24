---
title: Claude Code Sandbox
description: Изолированная среда для работы Claude Code без риска случайного деплоя
tags: [devops, docker, claude-code, security]
status: active
---

# Claude Code Sandbox

Изолированная Docker-среда, в которой Claude Code может свободно редактировать код, собирать проект и запускать тесты — но **не может** задеплоить на сервер или выполнить SSH-команды.

## Быстрый старт

```bash
# 1. Задать API-ключ (один раз)
echo 'ANTHROPIC_API_KEY=sk-ant-...' > .claude/sandbox/.env

# 2. Запустить Claude Code в песочнице
.claude/sandbox/run.sh claude

# Или открыть shell и запустить claude вручную
.claude/sandbox/run.sh
```

## Что доступно внутри

| Инструмент | Версия | Назначение |
|---|---|---|
| Java (JDK) | 17 | Сборка и запуск проекта |
| Gradle | через `./gradlew` | Сборка, тесты |
| Node.js | 18 | Frontend |
| Python 3 | 3.10 | Emulation-скрипты |
| Git | 2.34+ | Версионирование |
| Claude Code | latest | AI-агент |

## Что заблокировано

| Инструмент | Причина блокировки |
|---|---|
| `ssh`, `scp`, `rsync` | Удалены из образа — деплой-скрипты падают с `command not found` |
| Docker CLI | Не установлен, Docker socket не смонтирован |
| `~/.ssh` | Не смонтирована — SSH-ключи недоступны |

Сеть **включена** (Claude Code нужен доступ к Anthropic API, Gradle — для скачивания зависимостей), но без SSH-инструментов и ключей выход на сервер невозможен.

## Файлы

```
.claude/sandbox/
├── Dockerfile          # Образ: ubuntu 22.04 + JDK 17 + Node 18 + Python 3
├── docker-compose.yml  # Монтирование проекта, volumes для кэшей
├── run.sh              # Скрипт запуска (автоопределение UID/GID)
└── .env                # API-ключ (не коммитится, в .gitignore через '.*')
```

## Пересборка образа

```bash
cd .claude/sandbox
docker compose build --no-cache
```
