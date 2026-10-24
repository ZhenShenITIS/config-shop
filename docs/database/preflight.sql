-- Read-only checks against the OLD schema, before applying 002-account-schema.
-- Blocking checks must be resolved on the migration source; do not silently discard rows.
SELECT 'BLOCK: missing Remnawave reference' AS check_name, count(*) AS records
FROM bot_users WHERE nullif(btrim(remnawave_uuid),'') IS NULL AND remnawave_id IS NULL
UNION ALL
SELECT 'BLOCK: invalid Remnawave ID', count(*) FROM bot_users WHERE remnawave_id <= 0
UNION ALL
SELECT 'BLOCK: duplicate Remnawave UUID', count(*) FROM
 (SELECT remnawave_uuid FROM bot_users WHERE remnawave_uuid IS NOT NULL GROUP BY remnawave_uuid HAVING count(*)>1) d
UNION ALL
SELECT 'BLOCK: duplicate Remnawave ID', count(*) FROM
 (SELECT remnawave_id FROM bot_users WHERE remnawave_id IS NOT NULL GROUP BY remnawave_id HAVING count(*)>1) d
UNION ALL
SELECT 'BLOCK: invalid subscription purchase', count(*) FROM purchases p LEFT JOIN subscriptions s ON s.id=p.subscription_id
 WHERE p.purchase_type='SUBSCRIPTION' AND (s.id IS NULL OR s.duration_days<=0 OR s.device_count<=0)
UNION ALL
SELECT 'BLOCK: invalid purchase amount/type/parameters', count(*) FROM purchases
 WHERE paid_amount IS NULL OR paid_amount<0 OR purchase_type IS NULL OR purchase_type NOT IN ('SUBSCRIPTION','TRAFFIC','DEVICE')
 OR (purchase_type='TRAFFIC' AND (traffic_gb IS NULL OR traffic_gb<=0))
 OR (purchase_type='DEVICE' AND (device_count IS NULL OR device_count<=0))
UNION ALL
SELECT 'BLOCK: missing purchase owner', count(*) FROM purchases p LEFT JOIN bot_users b ON b.id=p.bot_user_id WHERE b.id IS NULL
UNION ALL
SELECT 'BLOCK: invalid promo code', count(*) FROM promo_codes
 WHERE amount IS NULL OR amount<0 OR coalesce(current_uses,0)<0
 OR (max_uses IS NOT NULL AND (max_uses<0 OR coalesce(current_uses,0)>max_uses))
 OR (is_referral AND referrer_id IS NULL)
UNION ALL
SELECT 'BLOCK: invalid top-up value/source', count(*) FROM top_ups
 WHERE value IS NULL OR (top_up_source<>'ADMIN' AND value<=0)
 OR top_up_source IS NULL OR top_up_source NOT IN ('EXTERNAL','ADMIN','REFERRAL','PROMO_CODE')
UNION ALL
SELECT 'BLOCK: EXTERNAL top-up without payment', count(*) FROM top_ups t LEFT JOIN platega_payments p ON p.transaction_id=t.external_id
 WHERE t.top_up_source='EXTERNAL' AND p.transaction_id IS NULL
UNION ALL
SELECT 'BLOCK: duplicate EXTERNAL top-up', count(*) FROM
 (SELECT external_id FROM top_ups WHERE top_up_source='EXTERNAL' GROUP BY external_id HAVING count(*)>1) d
UNION ALL
SELECT 'BLOCK: payment amount/currency mismatch', count(*) FROM top_ups t JOIN platega_payments p ON p.transaction_id=t.external_id
 WHERE t.top_up_source='EXTERNAL' AND (p.currency<>'RUB' OR p.amount IS NULL OR p.amount::numeric<>t.value)
UNION ALL
SELECT 'BLOCK: PROMO_CODE top-up without promo code', count(*) FROM top_ups t LEFT JOIN promo_codes p ON p.code=t.external_id
 WHERE t.top_up_source='PROMO_CODE' AND p.id IS NULL
UNION ALL
SELECT 'BLOCK: invalid withdrawal', count(*) FROM withdrawals
 WHERE amount IS NULL OR amount<=0 OR requisites IS NULL OR status IS NULL OR status NOT IN ('DONE','REJECTED','IN_PROGRESS')
 OR type IS NULL OR type NOT IN ('CARD','CRYPTO')
UNION ALL
SELECT 'REPORT: payment without known owner', count(*) FROM platega_payments p
 WHERE NOT EXISTS (SELECT 1 FROM top_ups t WHERE t.top_up_source='EXTERNAL' AND t.external_id=p.transaction_id)
UNION ALL
SELECT 'REPORT: credited payment with different status', count(*) FROM platega_payments p JOIN top_ups t ON t.external_id=p.transaction_id
 WHERE t.top_up_source='EXTERNAL' AND p.status<>'CONFIRMED';

WITH RECURSIVE paths AS (
 SELECT referrer_id AS origin,referral_id AS node FROM referrals
 UNION
 SELECT p.origin,r.referral_id FROM paths p JOIN referrals r ON r.referrer_id=p.node
)
SELECT 'BLOCK: referral cycle' AS check_name,count(*) AS records FROM paths WHERE origin=node;
