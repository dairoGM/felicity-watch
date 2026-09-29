-- Metadatos de equipos (inversor/batería) de cada dispositivo, para que la
-- pestaña Clientes (master) pueda mostrar "Equipos" de un cliente puntual
-- exactamente igual que su propia pantalla Equipos (DevicesScreen) — alias,
-- modelo, planta, país, propietario, capacidad instalada — sin necesitar
-- las credenciales de FSolar de ese cliente.
--
-- Esto NO viene en power_readings (esa tabla es solo la serie de tiempo de
-- potencia/batería): estos metadatos casi no cambian, así que se guardan
-- como un snapshot JSON de la última consulta al endpoint de Felicity de
-- cada cliente, en vez de normalizarlos en columnas — más simple de subir
-- y de versionar si Felicity agrega/quita campos.
--
-- Aplicar con: psql "$SUPABASE_DB_URL" -f supabase/migrations/003_account_device_equipment.sql
-- (o pegar en el SQL Editor del panel de Supabase).

begin;

create table if not exists public.account_device_equipment (
  device_id text primary key references public.account_devices(device_id) on delete cascade,
  -- Array JSON de DeviceInfo (serialNumber, role, model, alias, status,
  -- plantName, plantId, ownerName, countryName, ratedPowerKw) tal como los
  -- serializa EquipmentSnapshotDto en la app.
  devices_json jsonb not null,
  updated_at timestamptz not null default now()
);

alter table public.account_device_equipment enable row level security;

drop policy if exists "account_device_equipment_all" on public.account_device_equipment;
create policy "account_device_equipment_all" on public.account_device_equipment
  for all using (true) with check (true);

commit;
