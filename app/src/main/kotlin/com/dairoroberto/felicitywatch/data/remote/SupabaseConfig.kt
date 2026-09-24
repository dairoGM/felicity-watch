package com.dairoroberto.felicitywatch.data.remote

/**
 * Proyecto de Supabase usado como respaldo/sincronización del historial de
 * lecturas. [ANON_KEY] es la clave PÚBLICA (publishable), la misma que
 * Supabase espera que vaya embebida en apps cliente — el acceso real lo
 * controla la política de Row Level Security de la tabla en el servidor,
 * no el secreto de esta clave. La contraseña de administración de Postgres
 * NUNCA se usa desde la app.
 */
object SupabaseConfig {
    const val BASE_URL = "https://fplwgmttcdymneepexmo.supabase.co/"
    const val ANON_KEY = "sb_publishable_M-GlAXI0t-pTYP7XqtoK-A_llaJ-onZ"
}
