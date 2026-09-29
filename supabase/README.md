# Supabase — esquema y migraciones

SQL que hay que aplicar a mano en el proyecto de Supabase. No hay un runner
automático: cada archivo se corre una vez, en orden, desde el SQL Editor del
panel o con `psql`.

```bash
psql "$SUPABASE_DB_URL" -f supabase/migrations/001_licensing.sql
psql "$SUPABASE_DB_URL" -f supabase/migrations/002_power_readings_battery_detail.sql
```

| Archivo | Qué hace |
|---|---|
| `001_licensing.sql` | Periodo free + validación manual de transferencias |
| `002_power_readings_battery_detail.sql` | Voltaje/corriente/capacidad de batería en `power_readings` |

## 001_licensing.sql — licenciamiento de clientes

Agrega a `account_devices` el estado de licencia de cada cliente, y crea
`account_settings` con los días de prueba configurables desde el master.

### Ciclo

1. **Canje del código** → `license_status='free'`, `free_started_at=now()`.
   Solo la primera vez: `free_started_at` no se repone en canjes posteriores,
   porque si no, cada rechazo regalaría otro periodo de prueba y el ciclo nunca
   llegaría a exigir una transferencia válida.
2. **Vence el free** → el cliente pierde el acceso y la app le pide el ID de su
   transferencia → `license_status='pending'`.
3. **El master decide**:
   - Aprobar → `'approved'`, acceso indefinido, `rejected_attempts=0`.
   - Rechazar → `'rejected'`, `rejected_attempts+1`, y `revoked=true` para que
     necesite un código nuevo.
4. **Al 3er rechazo** → `'blocked'`. Solo el master desbloquea, y el desbloqueo
   devuelve la posibilidad de reintentar, no el acceso.

El contador mide rechazos **seguidos**: una aprobación lo reinicia.

### Estados

| `license_status` | Significado | ¿Da acceso? |
|---|---|---|
| `none` | Sin ciclo iniciado | No |
| `free` | En prueba | Sí, hasta que venza |
| `pending` | Transferencia enviada, esperando al master | **No** |
| `approved` | Validada | Sí, indefinido |
| `rejected` | Rechazada, puede reintentar | No |
| `blocked` | Agotó los 3 intentos | No |

`pending` **no** da acceso: declarar una transferencia no es haberla pagado, y
el acceso se concede cuando el master valida el pago. Si `pending` diera
acceso, bastaría con escribir cualquier ID inventado para usar la app
indefinidamente mientras nadie revisa.

El cliente en espera ve una pantalla de "Esperando validación" con un botón
para volver a comprobar, que consulta Supabase directamente — funciona aunque
el ciclo de monitoreo esté cortado por falta de acceso.

## Seguridad — limitación conocida

**Las políticas RLS de esta migración no impiden que un cliente se auto-apruebe.**

La app usa la `anon key` para todo, igual que el resto del esquema. Cualquiera
que la extraiga del APK puede hacer un `PATCH` a `account_devices` y ponerse
`license_status='approved'` por su cuenta. Las pantallas de licencia son un
control de producto, no una barrera de seguridad.

Cerrarlo de verdad requiere sacar la decisión del cliente:

- Mover aprobar/rechazar/desbloquear a funciones `SECURITY DEFINER` o a un Edge
  Function que use la `service key` (nunca embebida en el APK).
- Autenticar al master (Supabase Auth) en vez de confiar en un flag local.
- Restringir el `UPDATE` de los clientes a `transfer_reference` y
  `transfer_submitted_at` de su propia fila, y nada más.

Vale la pena hacerlo antes de que esto sostenga un cobro real.

## 002_power_readings_battery_detail.sql — detalle de batería

Agrega `battery_voltage`, `battery_current` y `battery_capacity_ah` a
`power_readings`. Antes esa tabla solo traía `battery_power_watts` (potencia
ya calculada), suficiente para el mapa/monitoreo básico de un cliente, pero
insuficiente para que la master vea Autonomía y Excedente Solar en el
detalle de un cliente (pestaña Clientes) exactamente igual que en el Panel
del propio cliente — esos cálculos necesitan la capacidad del banco (Ah) y
el voltaje instantáneo, no solo la potencia.
