package com.dairoroberto.felicitywatch.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migraciones formales de Room — reemplazan fallbackToDestructiveMigration(),
 * que hasta ahora borraba TODO el historial local (lecturas de PV/batería/
 * red) cada vez que se agregaba un campo a una entidad. La app ya está en
 * uso real en varios teléfonos, así que perder el historial en cada
 * actualización deja de ser aceptable a partir de aquí.
 *
 * Cada Migration debe registrarse en DatabaseModule.provideAppDatabase() vía
 * .addMigrations(...). Al agregar una nueva versión, escribir la Migration
 * correspondiente en este archivo ANTES de subir AppDatabase.version.
 */
object Migrations {

    /** v6 → v7: agrega push_notifications.ruleType (TEXT nullable) — el tipo
     * real de la regla que disparó cada push (GRID_ONLINE/GRID_OFFLINE/...),
     * antes ausente, lo que obligaba a adivinar el color del push por
     * palabras clave del mensaje en NotificationsScreen. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE push_notifications ADD COLUMN ruleType TEXT")
        }
    }

    /** v7 → v8: agrega la tabla `appliances` (catálogo de equipos de la casa
     * con su consumo declarado en watts), usada por el reporte de detección
     * de encendido/apagado. Solo CREATE TABLE: no toca ninguna tabla
     * existente, así que el historial de lecturas queda intacto. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // El SQL debe coincidir EXACTAMENTE con el createSql que Room
            // genera para ApplianceEntity (ver app/schemas/.../8.json), en
            // el mismo orden de cláusulas: Room valida el esquema al abrir
            // la base y cualquier diferencia — incluso "NOT NULL PRIMARY KEY"
            // en vez de "PRIMARY KEY AUTOINCREMENT NOT NULL" — lanza
            // IllegalStateException y la app no arranca.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `appliances` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`watts` INTEGER NOT NULL, " +
                    "`toleranceWatts` INTEGER NOT NULL)"
            )
        }
    }

    /** v8 → v9: agrega `appliances.minWatts` (consumo mínimo en operación),
     * para describir equipos inverter que modulan (un split puede consumir
     * 400 W manteniendo y 1600 W en arranque; un valor único no lo cubre).
     *
     * Los equipos ya registrados se migran con minWatts = watts, es decir
     * tratados como consumo fijo — exactamente el comportamiento que tenían
     * antes, así que ninguna configuración del usuario se pierde ni cambia
     * de significado. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Se RECREA la tabla en vez de usar ALTER TABLE ADD COLUMN:
            // SQLite exige un DEFAULT al agregar una columna NOT NULL a una
            // tabla existente, pero la entidad de Room no declara default,
            // y Room compara los defaults al validar el esquema — la
            // diferencia haría fallar la apertura de la base con
            // IllegalStateException. Recrear + copiar deja el esquema
            // idéntico al que Room genera (ver app/schemas/.../9.json).
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `appliances_new` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`watts` INTEGER NOT NULL, " +
                    "`minWatts` INTEGER NOT NULL, " +
                    "`toleranceWatts` INTEGER NOT NULL)"
            )
            // minWatts = watts para los equipos ya registrados: los deja
            // como consumo fijo, exactamente el comportamiento que tenían
            // antes de existir el rango. Ninguna configuración se pierde.
            db.execSQL(
                "INSERT INTO `appliances_new` (`id`, `name`, `watts`, `minWatts`, `toleranceWatts`) " +
                    "SELECT `id`, `name`, `watts`, `watts`, `toleranceWatts` FROM `appliances`"
            )
            db.execSQL("DROP TABLE `appliances`")
            db.execSQL("ALTER TABLE `appliances_new` RENAME TO `appliances`")
        }
    }

    /** v9 → v10: agrega `appliances.room` (habitación del equipo). Los
     * equipos ya registrados quedan con room = "" (sin asignar), que la UI
     * agrupa aparte — ninguna configuración existente se pierde.
     *
     * Se recrea la tabla por el mismo motivo que en v8→v9: ALTER TABLE ADD
     * COLUMN NOT NULL exige un DEFAULT en SQLite, la entidad de Room no lo
     * declara, y Room falla al validar si los defaults difieren. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `appliances_new` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`watts` INTEGER NOT NULL, " +
                    "`minWatts` INTEGER NOT NULL, " +
                    "`toleranceWatts` INTEGER NOT NULL, " +
                    "`room` TEXT NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `appliances_new` (`id`, `name`, `watts`, `minWatts`, `toleranceWatts`, `room`) " +
                    "SELECT `id`, `name`, `watts`, `minWatts`, `toleranceWatts`, '' FROM `appliances`"
            )
            db.execSQL("DROP TABLE `appliances`")
            db.execSQL("ALTER TABLE `appliances_new` RENAME TO `appliances`")
        }
    }

    /** v10 → v11: agrega `confirmedWatts` y `confirmedAtEpochMillis` a
     * appliances — el consumo MEDIDO por la app al confirmar el equipo, y
     * cuándo se midió.
     *
     * Aquí sí sirve ALTER TABLE ADD COLUMN (a diferencia de v8→v9 y v9→v10):
     * ambas columnas son nullable, así que SQLite no exige DEFAULT y el
     * esquema resultante coincide con el que Room genera. Los equipos ya
     * registrados quedan con null = "sin confirmar", que es exactamente su
     * estado real; no se pierde nada. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `appliances` ADD COLUMN `confirmedWatts` INTEGER")
            db.execSQL("ALTER TABLE `appliances` ADD COLUMN `confirmedAtEpochMillis` INTEGER")
        }
    }

    /** v11 → v12: agrega la tabla `dismissed_appliance_events`, que guarda
     * qué eventos de actividad descartó el usuario.
     *
     * Hace falta una tabla porque los eventos NO se persisten: se
     * recalculan desde el historial de lecturas cada vez, así que "borrar"
     * uno no puede ser un DELETE — volvería a aparecer al siguiente
     * recálculo. Solo CREATE TABLE: no toca nada existente. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dismissed_appliance_events` " +
                    "(`eventEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`eventEpochMillis`))"
            )
        }
    }

    /** v12 → v13: agrega `appliances.similarGroup` (grupo de equipos
     * eléctricamente similares, p. ej. "split convencional") y
     * `appliances.confirmationCount` (cuántas veces se confirmó), para el
     * autoaprendizaje del consumo real de cada equipo.
     *
     * Se RECREA la tabla, igual que en v8→v9 y v9→v10 y por el mismo motivo:
     * ambas columnas son NOT NULL y SQLite exige un DEFAULT al agregarlas con
     * ALTER TABLE, pero la entidad de Room no declara defaults (verificado
     * contra app/schemas/.../13.json), y Room compara los defaults al validar
     * el esquema — la diferencia haría fallar la apertura de la base.
     *
     * Los equipos ya registrados quedan con similarGroup = "" (equipo único,
     * aprende solo de sí mismo) y confirmationCount = 0 o 1 según si ya
     * tenían un consumo medido: con eso, una confirmación previa se respeta
     * como primera observación en vez de descartarse. Ninguna configuración
     * ni medición existente se pierde. */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `appliances_new` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`watts` INTEGER NOT NULL, " +
                    "`minWatts` INTEGER NOT NULL, " +
                    "`toleranceWatts` INTEGER NOT NULL, " +
                    "`room` TEXT NOT NULL, " +
                    "`confirmedWatts` INTEGER, " +
                    "`confirmedAtEpochMillis` INTEGER, " +
                    "`similarGroup` TEXT NOT NULL, " +
                    "`confirmationCount` INTEGER NOT NULL)"
            )
            // confirmationCount = 1 para los que ya tenían medición: ese valor
            // es una observación real y debe pesar en el promedio futuro. Los
            // no medidos arrancan en 0 para que su primera confirmación tome
            // el valor observado tal cual.
            db.execSQL(
                "INSERT INTO `appliances_new` " +
                    "(`id`, `name`, `watts`, `minWatts`, `toleranceWatts`, `room`, " +
                    "`confirmedWatts`, `confirmedAtEpochMillis`, `similarGroup`, `confirmationCount`) " +
                    "SELECT `id`, `name`, `watts`, `minWatts`, `toleranceWatts`, `room`, " +
                    "`confirmedWatts`, `confirmedAtEpochMillis`, '', " +
                    "CASE WHEN `confirmedWatts` IS NULL THEN 0 ELSE 1 END " +
                    "FROM `appliances`"
            )
            db.execSQL("DROP TABLE `appliances`")
            db.execSQL("ALTER TABLE `appliances_new` RENAME TO `appliances`")
        }
    }

    /** v13 → v14: agrega la tabla `imported_backups`, que registra qué
     * respaldos de inventario ya se importaron en este teléfono.
     *
     * Hace falta para cumplir "no importar el mismo inventario dos veces":
     * cada archivo trae un backupId único y, al importarlo, se anota aquí;
     * un segundo intento con el mismo archivo se rechaza sin insertar nada.
     *
     * Solo CREATE TABLE: no toca ninguna tabla existente, así que el
     * inventario y el historial quedan intactos. */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `imported_backups` " +
                    "(`backupId` TEXT NOT NULL, " +
                    "`importedAtEpochMillis` INTEGER NOT NULL, " +
                    "`applianceCount` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`backupId`))"
            )
        }
    }

    val ALL = arrayOf(
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14
    )
}
