### accounts
Переработка бизнес-смысла таблицы bot_user
Поля:
- id (uuid, PK)
- referral_percentage1lvl
- referral_percentage2lvl
- referral_percentage3lvl
- created_at
- updated_at
- subscription_price_rate (DEFAULT 1)
- traffic_package_rate (DEFAULT 1)

### remnawave_subscriptions
Таблица для ремнавейв-подписок юзера, обеспечивает связь с ними. У одного юзера много подписок
Поля:
- id (UUID)
- short_id
- remnawave_uuid
- remnawave_id
- account_id  (FK accounts.id)
- created_at

Не храним никаких подробностей, чтобы не допустить неконсистентности. Все получаем из запроса к ремнавейв

### promo_codes
Помнить про конкурентный доступ при работе с этой таблицей
Можно добавить условие "если реферальный, то реферрер не нал"
`CHECK current_uses <= max_uses`
Поля:
- id
- amount
- code (UNIQUE, NOT NULL)
- max_uses
- current_uses
- is_referral
- referrer (FK accounts.id NULLABLE)
- created_at


### promo_code_uses
Поля:
- id
- promo_code_id (FK promo_codes.id)
- account_id (FK accounts.id)
- created_at
`UNIQUE(promo_code_id, account_id)`


### balance_log
На данный момент построение лога транзакций операция ресурсо-затратная. Она представляет собой SQL на страницу с кучей джоинов, условий и преобразований. При переходе на динамическое высчитывание баланса такая дорогая операция станет критичной.
При использовании данной таблицы построение лога операций станет очень простой задачей, так как фильтрация будет всегда точечная по id конкретного пользователя
Необходимо добавить b-tree-индекс (hash не бывает включенным) на поле account_id и включить в него amount (для высчитывания текущего баланса юзера нужны только эти 2 поля)

`(account_id, created_at DESC) INCLUDE (amount, type)` - для быстрой истории


Поля:
- id (uuid)
- amount (может быть как +, так и -)
- type
- account_id  (FK accounts.id)
- created_at
- purchase_id (NULLABLE)
- top_up_id (NULLABLE)
- withdrawal_id (NULLABLE)

Ровно одно поле из последних трех не налл

Поле type - енам со значениями:
- SUBSCRIPTION_PURCHASE
- TRAFFIC_PURCHASE
- DEVICE_PURCHASE (история и новые покупки устройств по старому флоу)
- TOP_UP_EXTERNAL
- ADMIN
- TOP_UP_REFERRAL
- TOP_UP_PROMO_CODE
- WITHDRAWAL
- WITHDRAWAL_REJECT
-

Записи с типом WITHDRAWAL и WITHDRAWAL_REJECT могут ссылаться на одну сущность вывода



### purchases
Поля:
- id
- paid_amount
- account_price_rate (DEFAULT 1)
- account_id (FK accounts.id)
- created_at
- type (SUBSCRIPTION/TRAFFIC/DEVICE)
- remnawave_subscription_id
- traffic_gb (NULLABLE)
- sub_days (NULLABLE)
- sub_devices (NULLABLE)
- device_count (NULLABLE)

Обязательно храним remnawave_subscription_id, так как оба типа покупок относятся к подписке remnawave, а не к аккаунту
DEVICE в поле type используется и при переносе истории, и для новых покупок устройств по старому флоу. Новая политика цен и пересчёт срока при смене устройств в текущую задачу не входят.
### referrals
Поля:
- id (uuid)
- referrer (FK accounts.id)
- referral (FK accounts.id)
- created_at
запрет `referrer_id = referral_id`
`UNIQUE(referral_id)`
### top_ups
Нужно иметь ввиду, что в этой таблице есть неконсистентность - часть админских пополнений отрицательные
Поля:
- id
- value
- top_up_source (REFERRAL/EXTERNAL/PROMO_CODE/ADMIN)
- created_at
- account_id (FK accounts.id)
- platega_id (FK UNIQUE nullable platega_payments.id)
- promo_code_id (FK nullable promo_codes.id)
- referral (FK accounts.id)



### withdrawals
Поля:
- id
- account_id (FK accounts.id)
- created_at
- amount
- status (REJECTED, DONE, IN_PROGRESS)
- type (CARD, CRYPTO)
- requisites
- updated_at

### web_users
Поля:
- id (uuid, PK)
- email (UNIQUE)
- account_id (FK accounts.id)
- password_hash???
- name
- created_at
- updated_at
- status

### email_confirmations
Таблица будет определена во время разработки авторизации

### bot_users
Поля:
- telegram_id (PK)
- created_at
- name
- last_name
- username
- account_id (FK accounts.id)
- updated_at


### platega_payments
- id
- provider_payment_id (UNIQUE)
- amount
- account_id (FK, дублирование для быстрого доступа)
- status
- created_at
- updated_at
- payment_method


### subscription_adjustments
- id
- subscription_id
- old_devices
- new_devices
- old_expires_at
- new_expires_at
- conversion_factor
- pricing_policy_version
- created_at
