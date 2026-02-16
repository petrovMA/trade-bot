---
title: Google OAuth 2.0 Setup Guide
description: Пошаговая инструкция по настройке Google OAuth 2.0 для Trade Bot (Spring Security, Google Cloud Console)
tags: [security, oauth, google, spring-security, setup]
status: deprecated
related_files: [docs/guides/google-auth-setup.md, build.gradle.kts, src/main/resources/application.properties]
---

# Google OAuth 2.0 Setup Guide - Trade Bot

> **DEPRECATED**: Этот документ описывает подход с прямым Spring Security OAuth на порту 8081.
> Он **НЕ используется** в текущей архитектуре. Актуальная авторизация — через nginx `auth_request`,
> см. `docs/guides/google-auth-setup.md`. Оставлен как справка на случай возврата к прямому OAuth.

Пошаговая инструкция по настройке Google OAuth авторизации для Trade Bot.

## Содержание

- [Предварительные требования](#предварительные-требования)
- [Настройка Google Cloud Console](#настройка-google-cloud-console)
- [Настройка проекта Trade Bot](#настройка-проекта-trade-bot)
- [Проверка конфигурации](#проверка-конфигурации)
- [Устранение проблем](#устранение-проблем)

---

## Предварительные требования

- ✅ Рабочее развертывание на сервере с HTTPS
- ✅ Домен: `tradebotalphahorizon.duckdns.org`
- ✅ Аккаунт Google с доступом к [Google Cloud Console](https://console.cloud.google.com/)
- ✅ Проект уже запущен на порту **8081** (настроен в docker-compose.yml)

---

## Настройка Google Cloud Console

### Шаг 1: Обновление Authorized JavaScript Origins

В вашей существующей конфигурации Google OAuth (Client ID: `568777366600-m8e62rmi98i3l5rr721itf26i6537d3t.apps.googleusercontent.com`):

1. Откройте [Google Cloud Console](https://console.cloud.google.com/)
2. Перейдите в **APIs & Services** → **Credentials**
3. Найдите OAuth 2.0 Client ID: **"Alpha Horizon Trading Bot"**
4. Нажмите на него для редактирования

**Текущие настройки (из скриншота):**
```
Authorized JavaScript origins:
✅ https://tradebotalphahorizon.duckdns.org  (AI-SIGNAL)
```

**Добавьте:**
```
Authorized JavaScript origins:
https://tradebotalphahorizon.duckdns.org        (AI-SIGNAL - уже есть)
https://tradebotalphahorizon.duckdns.org:8081   (Trade-bot - ДОБАВИТЬ)
```

> **Важно:** Нажмите кнопку **"+ Add URI"** в секции "Authorized JavaScript origins" и добавьте второй URL.

---

### Шаг 2: Обновление Authorized Redirect URIs

**Текущие настройки (из скриншота):**
```
Authorized redirect URIs:
✅ https://tradebotalphahorizon.duckdns.org/auth/google/callback  (AI-SIGNAL HTTPS)
✅ http://tradebotalphahorizon.duckdns.org/auth/google/callback   (AI-SIGNAL HTTP)
```

**Добавьте:**
```
Authorized redirect URIs:
https://tradebotalphahorizon.duckdns.org/auth/google/callback          (AI-SIGNAL HTTPS - уже есть)
http://tradebotalphahorizon.duckdns.org/auth/google/callback           (AI-SIGNAL HTTP - уже есть)
https://tradebotalphahorizon.duckdns.org:8081/login/oauth2/code/google (Trade-bot - ДОБАВИТЬ)
```

> **Важно:**
> - Нажмите кнопку **"+ Add URI"** в секции "Authorized redirect URIs"
> - Для Spring Boot OAuth2 стандартный путь: `/login/oauth2/code/{provider}`
> - В данном случае: `/login/oauth2/code/google`

---

### Шаг 3: Сохранение Client Secret

**Из скриншота видно:**
- **Client ID:** `568777366600-m8e62rmi98i3l5rr721itf26i6537d3t.apps.googleusercontent.com`
- **Client Secret:** `****KwzU` (последние 4 символа видны)

Если вы **потеряли** полный Client Secret:
1. Нажмите **"+ Add secret"** на странице конфигурации
2. Скопируйте новый Client Secret в безопасное место
3. Используйте его в настройках Trade Bot

---

### Шаг 4: Сохранение изменений

1. Нажмите кнопку **"Save"** внизу страницы
2. Дождитесь сообщения об успешном сохранении
3. Изменения вступят в силу через **5-10 минут**

---

## Настройка проекта Trade Bot

### Шаг 1: Добавление зависимостей Spring Security OAuth2

Откройте `build.gradle.kts` и добавьте зависимости:

```kotlin
dependencies {
    // ... existing dependencies ...

    // Spring Security OAuth2
    implementation("org.springframework.boot:spring-boot-starter-security:3.2.3")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client:3.2.3")
}
```

**Пересоберите проект:**
```bash
./gradlew clean build
```

---

### Шаг 2: Настройка переменных окружения

Создайте файл `.env` в корне проекта (или обновите существующий):

```bash
# Google OAuth 2.0 Credentials
GOOGLE_CLIENT_ID=568777366600-m8e62rmi98i3l5rr721itf26i6537d3t.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=ваш-полный-client-secret

# Security
REQUIRE_AUTH=false  # Установите true когда готовы включить авторизацию
```

> **Важно:** Файл `.env` должен быть добавлен в `.gitignore`!

---

### Шаг 3: Обновление docker-compose.yml

Добавьте переменные окружения в сервис `trade-bot`:

```yaml
services:
  trade-bot:
    build: .
    container_name: trade-bot-backend
    ports:
      - "8081:8080"
    environment:
      - GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
      - GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}
      - REQUIRE_AUTH=${REQUIRE_AUTH:-false}
    env_file:
      - .env
    volumes:
      # ... existing volumes ...
```

---

### Шаг 4: Включение OAuth в application.properties

Файл `src/main/resources/application.properties` уже обновлен с настройками session cookie и OAuth.

**Для включения OAuth раскомментируйте строки:**

```properties
# ======== SESSION CONFIGURATION ========
# Уже активно - настроен custom cookie name для избежания конфликтов
server.servlet.session.cookie.name=trade-bot-session
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.same-site=lax
server.servlet.session.timeout=1h

# ======== GOOGLE OAUTH 2.0 CONFIGURATION ========
# Раскомментируйте когда готовы включить OAuth:
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=openid,email,profile
spring.security.oauth2.client.registration.google.redirect-uri=https://tradebotalphahorizon.duckdns.org:8081/login/oauth2/code/google

spring.security.oauth2.client.provider.google.authorization-uri=https://accounts.google.com/o/oauth2/v2/auth
spring.security.oauth2.client.provider.google.token-uri=https://oauth2.googleapis.com/token
spring.security.oauth2.client.provider.google.user-info-uri=https://www.googleapis.com/oauth2/v3/userinfo
spring.security.oauth2.client.provider.google.user-name-attribute=sub
```

---

### Шаг 5: Создание Security Configuration

Создайте файл `src/main/kotlin/bot/config/SecurityConfig.kt`:

```kotlin
package bot.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = ["REQUIRE_AUTH"], havingValue = "true", matchIfMissing = false)
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    // Публичные endpoints
                    .requestMatchers("/", "/error", "/actuator/health").permitAll()
                    .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()

                    // Все остальные требуют авторизации
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .loginPage("/oauth2/authorization/google")
                    .defaultSuccessUrl("/main", true)
                    .failureUrl("/login?error=true")
            }
            .logout { logout ->
                logout
                    .logoutSuccessUrl("/")
                    .invalidateHttpSession(true)
                    .deleteCookies("trade-bot-session")
            }
            .csrf { csrf ->
                csrf.disable() // Отключаем для API endpoints, включите в продакшене
            }

        return http.build()
    }
}
```

---

### Шаг 6: Создание User Controller

Создайте файл `src/main/kotlin/bot/trade/rest_controller/UserController.kt`:

```kotlin
package bot.trade.rest_controller

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/user")
class UserController {

    @GetMapping("/info")
    fun getUserInfo(@AuthenticationPrincipal principal: OAuth2User?): Map<String, Any?> {
        return if (principal != null) {
            mapOf(
                "authenticated" to true,
                "name" to principal.getAttribute<String>("name"),
                "email" to principal.getAttribute<String>("email"),
                "picture" to principal.getAttribute<String>("picture")
            )
        } else {
            mapOf("authenticated" to false)
        }
    }
}
```

---

## Проверка конфигурации

### Чеклист настройки Google Console

На основе вашего скриншота:

- [x] **Name:** Alpha Horizon Trading Bot ✅
- [x] **Client ID:** 568777366600-m8e62rmi98i3l5rr721itf26i6537d3t.apps.googleusercontent.com ✅
- [ ] **Authorized JavaScript origins:**
  - [x] `https://tradebotalphahorizon.duckdns.org` ✅ (уже добавлено)
  - [ ] `https://tradebotalphahorizon.duckdns.org:8081` ⚠️ **НУЖНО ДОБАВИТЬ**
- [ ] **Authorized redirect URIs:**
  - [x] `https://tradebotalphahorizon.duckdns.org/auth/google/callback` ✅ (AI-SIGNAL)
  - [x] `http://tradebotalphahorizon.duckdns.org/auth/google/callback` ✅ (AI-SIGNAL)
  - [ ] `https://tradebotalphahorizon.duckdns.org:8081/login/oauth2/code/google` ⚠️ **НУЖНО ДОБАВИТЬ**
- [x] **Client Secret:** Создан ✅

---

### Проверка работы OAuth

1. **Пересоберите проект:**
   ```bash
   ./gradlew clean build
   ```

2. **Пересоберите Docker контейнер:**
   ```bash
   docker-compose down
   docker-compose build --no-cache
   docker-compose up -d
   ```

3. **Проверьте логи:**
   ```bash
   docker-compose logs -f trade-bot
   ```

4. **Тестирование авторизации:**
   - Откройте: `https://tradebotalphahorizon.duckdns.org:8081`
   - Вас должно перенаправить на страницу входа Google
   - После успешной авторизации вернет на `/main`

5. **Проверка session cookie:**
   - Откройте DevTools (F12) → Application → Cookies
   - Должно быть два разных cookie:
     - AI-SIGNAL: cookie с именем по умолчанию (или `session`)
     - Trade-bot: `trade-bot-session`

6. **Проверка API:**
   ```bash
   curl https://tradebotalphahorizon.duckdns.org:8081/api/user/info
   ```

---

## Устранение проблем

### Ошибка: redirect_uri_mismatch

**Причина:** Redirect URI в коде не соответствует настройкам Google Console.

**Решение:**
1. Проверьте точное совпадение URL в Google Console
2. Убедитесь, что нет лишних слешей в конце
3. Проверьте протокол (https://)
4. Подождите 5-10 минут после изменений в Google Console

---

### Ошибка: invalid_client

**Причина:** Неверный Client ID или Client Secret.

**Решение:**
1. Проверьте переменные окружения `GOOGLE_CLIENT_ID` и `GOOGLE_CLIENT_SECRET`
2. Убедитесь, что Client Secret не истек
3. При необходимости создайте новый Client Secret в Google Console

---

### Session cookie конфликт

**Симптомы:** Авторизация в одном приложении сбрасывает авторизацию в другом.

**Решение:**
1. Проверьте `application.properties`:
   ```properties
   server.servlet.session.cookie.name=trade-bot-session
   ```
2. Проверьте в браузере DevTools → Cookies:
   - AI-SIGNAL должен использовать другое имя cookie
   - Trade-bot должен использовать `trade-bot-session`

---

### CORS ошибки

**Решение:** Создайте `src/main/kotlin/bot/config/WebConfig.kt`:

```kotlin
package bot.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(
                "https://tradebotalphahorizon.duckdns.org:8081",
                "https://tradebotalphahorizon.duckdns.org:8082"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowCredentials(true)
    }
}
```

---

## Архитектура после настройки

### Порты и URL

| Приложение | Backend Port | Frontend Port | OAuth Callback URL |
|-----------|--------------|---------------|-------------------|
| AI-SIGNAL | 8080 (internal) | 80/443 | `https://tradebotalphahorizon.duckdns.org/auth/google/callback` |
| Trade-bot | 8081 | 8082 | `https://tradebotalphahorizon.duckdns.org:8081/login/oauth2/code/google` |

### Session Cookies

| Приложение | Cookie Name | Domain | Path |
|-----------|-------------|--------|------|
| AI-SIGNAL | `session` (default) | tradebotalphahorizon.duckdns.org | / |
| Trade-bot | `trade-bot-session` | tradebotalphahorizon.duckdns.org | / |

### OAuth Client

**Вариант 1: Shared Client ID (Текущий)**
- Один Client ID для обоих приложений
- Разные redirect URIs
- Пользователи могут использовать один Google аккаунт для обоих приложений

**Вариант 2: Separate Client IDs (Альтернатива)**
- Отдельный Client ID для каждого приложения
- Полная изоляция
- Независимое управление

---

## Включение авторизации (когда готовы)

1. **Раскомментируйте OAuth настройки** в `application.properties`
2. **Установите переменную окружения:**
   ```bash
   REQUIRE_AUTH=true
   ```
3. **Пересоберите и перезапустите:**
   ```bash
   ./gradlew clean build
   docker-compose down
   docker-compose build
   docker-compose up -d
   ```

---

## Итоговый чеклист

Перед запуском в продакшн:

- [ ] Google OAuth Client ID настроен
- [ ] Добавлены все необходимые redirect URIs
- [ ] Client Secret сохранен в безопасном месте
- [ ] `.env` файл создан и добавлен в `.gitignore`
- [ ] Session cookie name настроен: `trade-bot-session`
- [ ] Spring Security зависимости добавлены
- [ ] SecurityConfig создан
- [ ] Приложение пересобрано и перезапущено
- [ ] OAuth тестирован и работает
- [ ] Проверено отсутствие конфликтов с AI-SIGNAL

---

## Дополнительные ресурсы

- [Spring Security OAuth2 Client Docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)
- [Google OAuth 2.0 Guide](https://developers.google.com/identity/protocols/oauth2)
- [Google Cloud Console](https://console.cloud.google.com/)

---

**Последнее обновление:** 2026-01-04
**Версия:** 1.0