---
title: Documentation Map
description: Карта документации проекта — статусы, связи code-docs, навигация для AI-агентов
tags: [index, navigation, meta]
status: active
---

# Documentation Map

Карта всей документации проекта. AI-агенты: читай этот файл первым для навигации по docs/.

## Документы

| Документ | Статус | Описание |
|----------|--------|----------|
| `guides/google-auth-setup.md` | **active** | Текущая архитектура auth — nginx `auth_request` через Python-проект |
| `guides/google-oauth-setup.md` | **deprecated** | Устаревший вариант — прямой Spring Security OAuth на порту 8081. НЕ используется, оставлен как справка |
| `guides/ai-friendly-docs-prompt.md` | **active** | Промпт/ТЗ для AI-агентов по улучшению документации в любом проекте |
| `guides/sandbox-setup.md` | **active** | Изолированная Docker-среда для Claude Code (без SSH/деплоя) |
| `architecture/spike-aggregation-design.md` | **active** | Дизайн spike aggregation mode для `AlgorithmGrid` |
| `reference/version-history.md` | **active** | Changelog проекта. Обновлять после каждого изменения кода |

## Code → Docs

| Код | Документация |
|-----|-------------|
| `AlgorithmGrid.kt` (spike logic) | `architecture/spike-aggregation-design.md` |
| `spike/SpikeDetector.kt`, `spike/PriceStabilizer.kt`, `spike/SpikeConfig.kt` | `architecture/spike-aggregation-design.md` |
| Docker/nginx deployment, `docker-compose.prod.yml` | `guides/google-auth-setup.md` |
| `.claude/sandbox/` (Dockerfile, docker-compose.yml, run.sh) | `guides/sandbox-setup.md` |
| `frontend/nginx/nginx-ssl.conf` | `guides/google-auth-setup.md` (секция Nginx proxy_pass) |
| `ClientExtended.kt`, `StreamExtendedImpl.kt` | `reference/version-history.md` (v2.1.0) |
| `ClientOneInch.kt`, `LimitOrderBuilder.kt` | `reference/version-history.md` (v2.0.0) |

## Структура папок

```
docs/
├── map.md                              ← ты здесь
├── index.md                            — общий индекс документации
├── architecture/
│   └── spike-aggregation-design.md     — дизайн spike aggregation
├── guides/
│   ├── google-auth-setup.md            — текущая auth (active)
│   ├── google-oauth-setup.md           — старая auth (deprecated)
│   ├── ai-friendly-docs-prompt.md      — промпт для AI-friendly доков
│   └── sandbox-setup.md               — Claude Code sandbox (Docker)
├── reference/
│   └── version-history.md              — changelog
└── tutorials/                          — (пусто)
```

## Правила обновления

- При добавлении нового .md файла в docs/ — добавь строку в таблицу "Документы" выше
- При связывании кода с документацией — добавь строку в "Code → Docs"
- При устаревании документа — смени статус на **deprecated** и укажи замену
