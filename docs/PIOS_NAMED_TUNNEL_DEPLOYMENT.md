# PIOS — Развёртывание постоянного адреса пилота: именованный Cloudflare Tunnel

Статус: **Решение принято Product Owner, 2026-08-07. Домен утверждён: `piosapp.ru`.** Это операционный документ, расширяющий `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` и `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` — ни один из них не переписывается, оба остаются в силе как есть (`CLAUDE.md`, «Never Delete Documentation»). Не ADR: решение здесь чисто инфраструктурное (какой конкретно механизм адреса выбран и как его развернуть), не архитектурное — ровно то разделение, которое `ADR-048` Decision 4 сам проводит между «нужна ли постоянная адресация» (архитектурный факт, решён там) и «каким механизмом» (стоит денег и домена, решение Product Owner, отложено туда же).

**Область действия.** Только поверхность пилота — то, чем пользуются Артур, четыре водителя и пассажиры (`https://piosapp.ru` → фронтенд). Этот документ **не** создаёт и не готовит второй адрес (`ops.piosapp.ru` или аналогичный) для удалённого управления платформой — тот путь описан в `ADR-048`, который сегодня имеет статус **Draft, не ратифицирован**, и его реализация здесь не начинается.

**Адрес — корневой домен, не поддомен.** `PIOS_PILOT_FRONTEND_ORIGIN=https://piosapp.ru` (без `pios.` перед доменом) — так решено Product Owner, и именно это значение сейчас стоит во всех пяти backend-службах.

---

## Что уже сделано в репозитории этим документом

- `windows-services/frontend/pios-frontend.xml` — порт изменён с `5173` на `4173`, добавлен `--strictPort`. Это ровно порт и флаг, которые `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Раздел 4 уже описывает для туннелируемой поверхности.
- `windows-services/cloudflared/pios-cloudflared.xml` — новое определение службы Windows для `cloudflared`, по тому же шаблону WinSW, что и остальные шесть служб. Не новый компонент архитектуры: `cloudflared` как цель T-2 уже был назван в `ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` («a versioned WinSW XML per target… plus `cloudflared`»), тогда отложенный до «следующего этапа» (O-2). Сейчас этот этап наступил.
- `.gitignore` — добавлены `windows-services/cloudflared/config.yml` и `windows-services/cloudflared/*.json`: файл конфигурации туннеля и файл учётных данных (закрытый ключ туннеля) никогда не должны попасть в git.
- **Все пять** `windows-services/{dispatch,driver-management,identity,order-management,passenger-experience}/pios-*.xml` — `PIOS_PILOT_FRONTEND_ORIGIN` обновлён на `https://piosapp.ru`. Проверено: во всей папке `windows-services/` не осталось ни одного рабочего упоминания `192.168.*` или порта `5173` (кроме одного документирующего комментария в `pios-frontend.xml`, объясняющего сам факт смены порта).

Ничего из перечисленного не закоммичено и не запушено — изменения остаются локальными до отдельного подтверждения.

---

## Что нужно сделать на ASUS — по шагам

Предполагается, что ASUS уже клонирует этот git-репозиторий (так сегодня попадают туда обновления пяти backend-сервисов и фронтенда) и что на нём уже установлен JDK 21 и настроены шесть существующих служб — ничего из этого шаги ниже не меняют.

### Шаг 1 — Домен: уже сделано

`piosapp.ru` зарегистрирован. Осталось добавить его как зону в Cloudflare, если это ещё не сделано при регистрации:
- Если домен куплен через **Cloudflare Registrar** — зона уже существует, этот шаг пропускается.
- Если домен куплен у другого регистратора — добавить его в Cloudflare Dashboard (Add a Site → `piosapp.ru`) и сменить NS-записи у регистратора на выданные Cloudflare. Смена NS может распространяться от нескольких минут до нескольких часов — стоит сделать это заранее, не в день пилота.

### Шаг 2 — Установить `cloudflared` на ASUS

Скачать официальный установщик Windows (`cloudflared-windows-amd64.msi`) с `https://github.com/cloudflare/cloudflared/releases/latest` и установить. Стандартный путь установки — `C:\Program Files (x86)\cloudflared\cloudflared.exe` (именно этот путь уже прописан в `windows-services/cloudflared/pios-cloudflared.xml`; если установщик выбрал другой путь — поправить `<executable>` в этом файле перед установкой службы на Шаге 6).

Проверить:
```
"C:\Program Files (x86)\cloudflared\cloudflared.exe" --version
```

### Шаг 3 — Авторизация

```
cloudflared tunnel login
```
Откроется браузер — выбрать аккаунт Cloudflare и зону `piosapp.ru`. Появится сертификат авторизации на диске (`%USERPROFILE%\.cloudflared\cert.pem`) — этого шага достаточно один раз.

### Шаг 4 — Создать именованный туннель

```
cloudflared tunnel create pios-pilot
```
Команда выведет **UUID туннеля** и создаст файл учётных данных, обычно `%USERPROFILE%\.cloudflared\<UUID>.json`. Записать UUID — он понадобится в Шаге 5.

**Перенести** файл учётных данных в `C:\Projects\PIOS-Foundation_v1.0\windows-services\cloudflared\<UUID>.json` (не копировать — переносить, чтобы не оставлять второй экземпляр секрета на диске без необходимости). `.gitignore` уже настроен так, что этот файл не попадёт в git из этой папки.

### Шаг 5 — Привязать домен к туннелю и создать файл конфигурации

```
cloudflared tunnel route dns pios-pilot piosapp.ru
```
Cloudflare сам создаст нужную DNS-запись в зоне домена (CNAME с flattening на корне — Cloudflare поддерживает это для apex-доменов). Ничего в панели Cloudflare руками создавать не нужно.

Затем создать `C:\Projects\PIOS-Foundation_v1.0\windows-services\cloudflared\config.yml` (этот файл не в git — создаётся один раз, вручную, прямо на ASUS):

```yaml
tunnel: <UUID из Шага 4>
credentials-file: C:\Projects\PIOS-Foundation_v1.0\windows-services\cloudflared\<UUID>.json

ingress:
  - hostname: piosapp.ru
    service: http://127.0.0.1:4173
  - service: http_status:404
```

Маршрут только один — на фронтенд (`4173`). Ни один backend-порт (`8081`–`8086`) здесь не упоминается и не должен: фронтенд уже проксирует все пять модулей через собственную таблицу `preview.proxy` (`frontend/vite.config.ts`) — так было устроено с самого начала (`PIOS_PILOT_INFRASTRUCTURE_DECISION.md`), туннель просто становится входной точкой перед тем же самым механизмом, ничего в нём не меняя.

### Шаг 6 — Получить обновлённую конфигурацию на ASUS

```
git pull
```
Подтягивает уже подготовленные изменения: `PIOS_PILOT_FRONTEND_ORIGIN=https://piosapp.ru` во всех пяти backend-службах, порт `4173` у фронтенда, файл службы `cloudflared`. Выполняется после того, как эти изменения будут закоммичены и запушены — то есть после отдельного подтверждения commit'а.

### Шаг 7 — Установить и запустить службу `cloudflared`

Скопировать `WinSW.exe` (тот же файл, что уже используется для остальных шести служб) в `windows-services\cloudflared\` под именем `pios-cloudflared.exe` — по тому же соглашению, что и у существующих служб (исполняемый файл и `.xml`-конфигурация с одинаковым именем в одной папке). Затем из этой папки:
```
pios-cloudflared.exe install
pios-cloudflared.exe start
```

### Шаг 8 — Перезапустить остальные шесть служб

После `git pull` (обновлённые `PIOS_PILOT_FRONTEND_ORIGIN` и порт фронтенда):
```
services.msc
```
или через `sc.exe` — перезапустить `pios-frontend`, `pios-dispatch`, `pios-driver-management`, `pios-identity`, `pios-order-management`, `pios-passenger-experience`. `pios-platform-ops` этот документ не трогает.

### Шаг 9 — Проверка с реального телефона, не на Wi-Fi ASUS

1. Отключить Wi-Fi на телефоне (мобильный интернет).
2. Открыть `https://piosapp.ru` — должен открыться фронтенд PIOS, замок HTTPS в адресной строке.
3. Открыть личную ссылку одного из водителей `https://piosapp.ru/i/<driverId>` — должна открыться так же.
4. На экране водителя (`DriverHome.tsx`) проверить кнопки «Копировать»/«Поделиться» — они не работали по `http://` на локальный IP, должны заработать по `https://`.
5. Создать тестовый заказ и убедиться, что запрос действительно доходит до backend (в частности, ответ не блокируется CORS) — это прямая проверка того, что `PIOS_PILOT_FRONTEND_ORIGIN` во всех пяти службах реально совпадает с адресом, по которому открыт фронтенд.

Если шаг 5 не проходит — почти наверняка одна из пяти служб backend не была перезапущена после Шага 8, и всё ещё держит в памяти старое значение `PIOS_PILOT_FRONTEND_ORIGIN` (переменные окружения WinSW читаются один раз при старте процесса).

---

## Чего этот документ не решает

- **Второй адрес для удалённого управления** (например, `ops.piosapp.ru` → `platform-ops` на `127.0.0.1:8090`) — предусмотрен `ADR-048`, но тот ADR всё ещё Draft. Один и тот же туннель технически способен обслуживать оба хоста (`ADR-048` Decision 1 это и описывает), поэтому когда `ADR-048` будет ратифицирован, добавление второго `ingress`-правила в уже существующий `config.yml` — небольшое, чисто аддитивное расширение, а не повторная настройка с нуля. Делать это сейчас — преждевременно и не входит в задачу.
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

`windows-services/frontend/pios-frontend.xml` (порт 5173→4173, `--strictPort`), `windows-services/cloudflared/pios-cloudflared.xml` (новый), `.gitignore` (исключения для `config.yml`/учётных данных туннеля), `windows-services/{dispatch,driver-management,identity,order-management,passenger-experience}/pios-*.xml` (`PIOS_PILOT_FRONTEND_ORIGIN` → `https://piosapp.ru`), `docs/PIOS_NAMED_TUNNEL_DEPLOYMENT.md` (этот документ). Ничего не закоммичено.
