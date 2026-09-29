-- Detalle de batería en power_readings — necesario para que la pestaña
-- Clientes (detalle de un cliente puntual) pueda calcular Autonomía y
-- Excedente Solar exactamente igual que el Panel del propio cliente.
--
-- Antes power_readings solo traía battery_power_watts (ya calculado), sin
-- voltaje/corriente/capacidad — insuficiente para
-- estimateBatteryRuntimeHours() y timeToFullChargeLabel() (ver
-- DashboardScreen.kt / BatteryRuntimeRing), que necesitan capacityAh +
-- voltage del banco, no solo la potencia instantánea.
--
-- Aplicar con: psql "$SUPABASE_DB_URL" -f supabase/migrations/002_power_readings_battery_detail.sql
-- (o pegar en el SQL Editor del panel de Supabase).

begin;

alter table public.power_readings
  add column if not exists battery_voltage double precision,
  add column if not exists battery_current double precision,
  add column if not exists battery_capacity_ah double precision;

commit;
