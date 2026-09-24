# PIOS — Развёртывание постоянного адреса пилота: именованный Cloudflare Tunnel

Статус: **Решение принято Product Owner, 2026-08-07. Домен утверждён: `piosapp.ru`.** Это операционный документ, расширяющий `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` и `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` — ни один из них не переписывается, оба остаются в силе как есть (`CLAUDE.md`, «Never Delete Documentation»). Не ADR: решение здесь чисто инфраструктурное (какой конкретно механизм адреса выбран и как его развернуть), не архитектурное — ровно то разделение, которое `ADR-048` Decision 4 сам проводит между «нужна ли постоянная адресация» (архитектурный факт, решён там) и «каким механизмом» (стоит денег и домена, решение Product Owner, отложено туда же).

**Область действия.** Только поверхность пилота — то, чем пользуются Артур, четыре водителя и пассажиры (`https://piosapp.ru` → фронтенд). Этот документ **не** создаёт и не готовит второй адрес (`ops.piosapp.ru` или аналогичный) для удалённого управления платформой — тот путь описан в `ADR-048`, который сегодня имеет статус **Draft, не ратифицирован**, и его реализация здесь не начинается.

**Адрес — корневой домен, не поддомен.** `PIOS_PILOT_FRONTEND_ORIGIN=https://piosapp.ru` (без `pios.` перед доменом) — так решено Product Owner, и именно это значение сейчас стоит во всех пяти backend-службах.

---

## Что уже сделано в репозитории этим документом

- `windows-services/frontend/pios-frontend.xml` — custom Node server слушает release-порт `4173`; `PUBLIC_ORIGIN=https://piosapp.ru` задан явно для canonical/OpenGraph URL.
- `windows-services/cloudflared/pios-cloudflared.xml` — новое определение службы Windows для `cloudflared`, по тому же шаблону WinSW, что и остальные шесть служб. Не новый компонент архитектуры: `cloudflared` как цель T-2 уже был назван в `ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` («a versioned WinSW XML per target… plus `cloudflared`»), тогда отложенный до «следующего этапа» (O-2). Сейчас этот этап наступил.
- `windows-services/cloudflared/run-tunnel.ps1` и `config.production.yml` — один воспроизводимый production-контракт: token mode, секрет только в environment службы Windows, отслеживаемый non-secret ingress только на `127.0.0.1:4173`, обязательный catch-all `404`.
- `.gitignore` — локальные `config.yml`, credentials JSON и token-файлы остаются исключёнными как исторические/диагностические артефакты. Ни один из них не является входом production-службы.
- **Все пять** `windows-services/{dispatch,driver-management,identity,order-management,passenger-experience}/pios-*.xml.template` задают `PIOS_PILOT_FRONTEND_ORIGIN=https://piosapp.ru`; generator переносит это значение в gitignored service XML.

Этот документ не разрешает запуск, перезапуск или изменение live-туннеля. Provisioning и запуск выполняются только отдельной deployment-командой.

---

## Что нужно сделать на ASUS — по шагам

Предполагается, что ASUS уже клонирует этот git-репозиторий (так сегодня попадают туда обновления пяти backend-сервисов и фронтенда) и что на нём уже установлен JDK 21 и настроены шесть существующих служб — ничего из этого шаги ниже не меняют.

### Шаг 1 — Домен: уже сделано

`piosapp.ru` зарегистрирован. Осталось добавить его как зону в Cloudflare, если это ещё не сделано при регистрации:
- Если домен куплен через **Cloudflare Registrar** — зона уже существует, этот шаг пропускается.
- Если домен куплен у другого регистратора — добавить его в Cloudflare Dashboard (Add a Site → `piosapp.ru`) и сменить NS-записи у регистратора на выданные Cloudflare. Смена NS может распространяться от нескольких минут до нескольких часов — стоит сделать это заранее, не в день пилота.

### Шаг 2 — Установить `cloudflared` на ASUS

Скачать официальный установщик Windows (`cloudflared-windows-amd64.msi`) с `https://github.com/cloudflare/cloudflared/releases/latest` и установить. Стандартный путь установки — `C:\Program Files (x86)\cloudflared\cloudflared.exe` (именно этот путь проверяет `windows-services/cloudflared/run-tunnel.ps1`; иной install path требует осознанного изменения wrapper и повторного config-теста до deployment).

Проверить:
```
"C:\Program Files (x86)\cloudflared\cloudflared.exe" --version
```

### Шаг 3 — Получить connector token существующего туннеля

В Cloudflare Dashboard открыть существующий production tunnel для `piosapp.ru` и получить его connector token. Новый tunnel, tunnel UUID, `cert.pem` или credentials JSON для этого release-контракта не нужны. Значение токена нельзя помещать в Git, XML, PowerShell-файл, командный лог или ticket.

### Шаг 4 — Получить release-конфигурацию

После отдельно разрешённого `git pull` проверить наличие:

- `windows-services/cloudflared/config.production.yml` — только `piosapp.ru → http://127.0.0.1:4173` и catch-all `404`;
- `windows-services/cloudflared/run-tunnel.ps1` — читает только `PIOS_CLOUDFLARED_TUNNEL_TOKEN` из process environment;
- `windows-services/cloudflared/pios-cloudflared.xml` — запускает wrapper через Windows PowerShell.

Backend-порты (`8081`–`8086`, `8091`) в ingress запрещены. Внешний запрос всегда приходит на frontend `4173`; только frontend proxy направляет разрешённые `/v1/...` маршруты локальным backend-службам. `/v1/persons` отсутствует, поэтому dormant Network Management не экспонируется.

### Шаг 5 — Установить службу без запуска и provision токен

Скопировать `WinSW.exe` в `windows-services\cloudflared\` под именем `pios-cloudflared.exe`, затем выполнить elevated:

```powershell
pios-cloudflared.exe install
```

После появления `HKLM:\SYSTEM\CurrentControlSet\Services\pios-cloudflared` добавить к этой службе `REG_MULTI_SZ` value `Environment` с единственной парой `PIOS_CLOUDFLARED_TUNNEL_TOKEN=<connector token>`. Это ACL-защищённая конфигурация SCM; токен получает только процесс службы. Не использовать Machine-wide environment и не создавать `tunnel.token` рядом с исходниками. До наличия непустой переменной wrapper завершится с кодом `1`, ничего не подключая.

### Шаг 6 — Запустить службу `cloudflared`

Только после отдельного deployment-разрешения и boolean-проверки наличия переменной (не печатая значение):
```
pios-cloudflared.exe start
```

### Шаг 7 — Перезапустить остальные шесть служб

После `git pull` (обновлённые `PIOS_PILOT_FRONTEND_ORIGIN` и порт фронтенда):
```
services.msc
```
или через `sc.exe` — перезапустить `pios-frontend`, `pios-dispatch`, `pios-driver-management`, `pios-identity`, `pios-order-management`, `pios-passenger-experience`. `pios-platform-ops` этот документ не трогает.

### Шаг 8 — Проверка с реального телефона, не на Wi-Fi ASUS

1. Отключить Wi-Fi на телефоне (мобильный интернет).
2. Открыть `https://piosapp.ru` — должен открыться фронтенд PIOS, замок HTTPS в адресной строке.
3. Открыть личную ссылку одного из водителей `https://piosapp.ru/i/<driverId>` — должна открыться так же.
4. На экране водителя (`DriverHome.tsx`) проверить кнопки «Копировать»/«Поделиться» — они не работали по `http://` на локальный IP, должны заработать по `https://`.
5. Создать тестовый заказ и убедиться, что запрос действительно доходит до backend (в частности, ответ не блокируется CORS) — это прямая проверка того, что `PIOS_PILOT_FRONTEND_ORIGIN` во всех пяти службах реально совпадает с адресом, по которому открыт фронтенд.

Если backend-вызов не проходит CORS — проверить, что пять generated WinSW XML созданы из актуальных templates с `PIOS_PILOT_FRONTEND_ORIGIN=https://piosapp.ru`, а службы перезапущены после генерации. Если connector не стартует — проверить только наличие `PIOS_CLOUDFLARED_TUNNEL_TOKEN` в SCM environment и существование tracked `config.production.yml`, не выводя токен.

---

## Чего этот документ не решает

- **Второй адрес для удалённого управления** (например, `ops.piosapp.ru` → `platform-ops` на `127.0.0.1:8090`) — предусмотрен `ADR-048`, но тот ADR всё ещё Draft. Его нет в `config.production.yml`; добавление запрещено без отдельной ратификации и review.
- **Внешний шлюз доступа (Cloudflare Access) перед `piosapp.ru`** — и не должен: `ADR-044` Decision 11 и `ADR-048` Decision 3 прямо говорят, что аутентификация перед поверхностью водителей и пассажиров означала бы участническую аутентификацию, что вне текущей архитектуры.
- **Постоянство самого домена** (продление регистрации, оплата) — операционная задача владельца аккаунта, не техническая.

## Traceability

| Предмет | Источник |
|---|---|
| Порт 4173 и флаг `--strictPort` для туннелируемой поверхности | `PIOS_PILOT_INFRASTRUCTURE_DECISION.md`, Раздел 4 |
| Пять маршрутов `preview.proxy`, включая Variant A (`/v1/health/<module>`) | `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` |
| `cloudflared` как запланированная, но отложенная цель T-2 | `ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md`, T-2 Result; O-2 |
| Именованный туннель как рекомендованный механизм для стабильного адреса | `ADR-048` Decision 4 |
| Разделение «нужна ли постоянная адресация» (архитектура) vs «каким механизмом» (Product Owner) | `ADR-048` Status, Decision 4 |
| Второй хост для удалённого управления, пока не реализуется | `ADR-048` Decision 1 (диаграмма с двумя хостами), сам ADR всё ещё Draft |
| Запрет шлюза доступа перед поверхностью водителей/пассажиров | `ADR-044` Decision 11; `ADR-048` Decision 3 |
| Домен | Product Owner, `piosapp.ru`, утверждён 2026-08-07 |

## Files Changed

Актуальный release-контракт находится в `windows-services/frontend/pios-frontend.xml`, `windows-services/cloudflared/{pios-cloudflared.xml,run-tunnel.ps1,config.production.yml}`, пяти backend `*.xml.template`, `frontend/server/backendRoutes.mjs` и этом документе. Generated XML, connector token, credentials JSON и локальный `config.yml` не входят в Git.
