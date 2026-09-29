-- Licenciamiento de clientes: periodo free + validación manual de
-- transferencia desde el master.
--
-- Flujo que implementan estas columnas:
--   1. El cliente canjea un código  -> free_started_at = now(), estado 'free'
--   2. Vencido el periodo free      -> el cliente envía un ID de transferencia
--                                      (estado 'pending')
--   3. El master aprueba            -> estado 'approved', acceso indefinido
--      El master rechaza            -> estado 'rejected', +1 intento; el
--                                      cliente necesita un código nuevo
--   4. Al 3er rechazo               -> estado 'blocked'; solo el master
--                                      desbloquea
--
-- Aplicar con: psql "$SUPABASE_DB_URL" -f supabase/migrations/001_licensing.sql
-- (o pegar en el SQL Editor del panel de Supabase).

begin;

-- ---------------------------------------------------------------------------
-- 1. Estado de licencia por dispositivo
-- ---------------------------------------------------------------------------

-- Texto + CHECK en vez de un enum de Postgres: agregar un estado nuevo a un
-- enum exige ALTER TYPE (que no corre dentro de una transacción en versiones
-- viejas), mientras que aquí basta con editar el CHECK.
alter table public.account_devices
  add column if not exists license_status text not null default 'none'
    check (license_status in ('none', 'free', 'pending', 'approved', 'rejected', 'blocked'));

-- Momento en que arrancó el periodo free. Se fija UNA sola vez, en el primer
-- canje del dispositivo: si se repusiera en cada código, un cliente rechazado
-- obtendría periodo free otra vez con solo pedir un código nuevo.
alter table public.account_devices
  add column if not exists free_started_at timestamptz;

-- ID de la transferencia que el cliente declara. Es un dato que teclea el
-- usuario, no una referencia a nada: el master lo compara contra su banco a
-- mano. Sin unique -- un mismo comprobante mal tecleado no debe romper el
-- registro de otro cliente.
alter table public.account_devices
  add column if not exists transfer_reference text;

alter table public.account_devices
  add column if not exists transfer_submitted_at timestamptz;

-- Fecha de la decisión del master (aprobación o rechazo), que el listado de
-- clientes muestra junto al estado.
alter table public.account_devices
  add column if not exists license_decided_at timestamptz;

-- Rechazos acumulados en el ciclo vigente. Se reinicia a 0 al aprobar y al
-- desbloquear: el contador mide "intentos fallidos seguidos", no el historial
-- de por vida del dispositivo.
alter table public.account_devices
  add column if not exists rejected_attempts int not null default 0;

-- Motivo opcional del rechazo, para que el cliente sepa qué corregir.
alter table public.account_devices
  add column if not exists license_reject_reason text;

-- El listado del master filtra por estado; son pocas filas por cuenta, pero
-- el índice hace gratis la consulta de "quién está pendiente de validar".
create index if not exists account_devices_license_status_idx
  on public.account_devices (license_status);

-- ---------------------------------------------------------------------------
-- 2. Configuración de la cuenta (días de periodo free)
-- ---------------------------------------------------------------------------

-- Tabla de una sola fila por cuenta. Se usa `id` fijo porque el resto del
-- esquema no tiene todavía un concepto de account_id propio: las filas se
-- aíslan por proyecto de Supabase.
create table if not exists public.account_settings (
  id text primary key default 'default',
  free_period_days int not null default 7 check (free_period_days between 0 and 365),
  updated_at timestamptz not null default now()
);

insert into public.account_settings (id)
  values ('default')
  on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- 3. RLS
-- ---------------------------------------------------------------------------

-- La app usa la anon key, igual que el resto de tablas de este proyecto. Estas
-- políticas replican ese mismo criterio para que la app cliente pueda leer su
-- propio estado y declarar su transferencia.
--
-- ATENCION: esto NO impide que un cliente se auto-apruebe editando la fila
-- directamente contra la API con la anon key. Cerrar eso requiere mover la
-- aprobación a una función SECURITY DEFINER o a un Edge Function con la
-- service key, y autenticar de verdad al master. Ver la nota en el README.
alter table public.account_settings enable row level security;

drop policy if exists account_settings_read on public.account_settings;
create policy account_settings_read on public.account_settings
  for select using (true);

drop policy if exists account_settings_write on public.account_settings;
create policy account_settings_write on public.account_settings
  for update using (true) with check (true);

commit;
