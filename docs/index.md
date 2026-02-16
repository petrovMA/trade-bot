---
title: Документация проекта
description: Индекс документации trade-bot — навигация по всем разделам
tags: [index, navigation, documentation]
status: active
related_files: [docs/architecture/spike-aggregation-design.md, docs/guides/google-auth-setup.md, docs/guides/google-oauth-setup.md, docs/reference/version-history.md]
---

# Документация проекта

Этот индекс описывает структуру документации и помогает быстро находить нужные материалы.

## Разделы

- `architecture/` — архитектурные решения и дизайн.
  - `spike-aggregation-design.md` — дизайн агрегации спайков. **[active]**
- `guides/` — практические руководства и инструкции.
  - `google-auth-setup.md` — текущая auth архитектура (nginx auth_request). **[active]**
  - `google-oauth-setup.md` — прямой OAuth через Spring Security. **[deprecated]** — не используется
- `reference/` — справочная информация и версии.
  - `version-history.md` — история версий (changelog). **[active]**
- `tutorials/` — пошаговые учебные материалы (пока пусто).

## Примечания

- Исходные файлы проекта были перенесены в `docs/project/` по вашему требованию (в корне остаются только `ReadMe.md`, `CLAUDE.md`, `GEMINI.md`).
