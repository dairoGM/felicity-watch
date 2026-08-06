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

    val ALL = arrayOf(MIGRATION_6_7)
}
