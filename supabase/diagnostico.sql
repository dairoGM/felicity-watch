-- Diagnóstico del licenciamiento. Correr en el SQL Editor de Supabase.

-- 1. ¿Están las columnas de la migración 001? Deben salir 7 filas.
--    Si sale vacío o faltan columnas, 001_licensing.sql no se aplicó.
select column_name, data_type
from information_schema.columns
where table_name = 'account_devices'
  and column_name in (
    'license_status', 'free_started_at', 'transfer_reference',
    'transfer_submitted_at', 'license_decided_at', 'rejected_attempts',
    'license_reject_reason'
  )
order by column_name;

-- 2. ¿Existe la tabla de configuración con su fila por defecto?
select * from public.account_settings;

-- 3. Estado real de cada dispositivo.
select
  device_id,
  role,
  display_name,
  license_status,
  free_started_at,
  rejected_attempts,
  revoked,
  approved_at
from public.account_devices
order by role, approved_at desc nulls last;

-- ---------------------------------------------------------------------------
-- ARREGLO para un cliente que quedó en 'none' con el periodo free sin arrancar
-- ---------------------------------------------------------------------------
-- Poner el device_id que salga en la consulta 3. Le arranca la prueba AHORA.
--
-- update public.account_devices
--    set license_status = 'free',
--        free_started_at = now()
--  where device_id = '<PEGAR_DEVICE_ID>'
--    and role = 'client';
