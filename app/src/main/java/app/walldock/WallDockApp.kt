package app.walldock

import android.app.Application
import android.content.Context

/** Garde le contexte de l'appli pour traduire les textes partout (anglais par défaut, français dans values-fr). */
class WallDockApp : Application() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Language.wrap(base))

    override fun onCreate() {
        super.onCreate()
        context = this
        // Plantage : on garde la cause pour l'envoyer à Home Assistant au redémarrage
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val frames = error.stackTrace.take(12).joinToString("\n") { "  at $it" }
                var cause = error.cause
                val causes = StringBuilder()
                while (cause != null && causes.length < 1500) {
                    causes.append("\nCaused by: ").append(cause).append("\n")
                        .append(cause.stackTrace.take(6).joinToString("\n") { "  at $it" })
                    cause = cause.cause
                }
                getSharedPreferences("walldock_crash", MODE_PRIVATE).edit()
                    .putString("last", "${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRANCE).format(java.util.Date())} [${thread.name}] $error\n$frames$causes")
                    .commit()
            } catch (e: Throwable) {
                // rien de plus à faire
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        lateinit var context: Context
            private set
    }
}

/** Texte traduit selon la langue de la tablette. */
fun tr(id: Int, vararg args: Any): String =
    // Sans argument, pas de mise en forme : un « % » dans le texte ferait planter getString(id, …)
    if (args.isEmpty()) WallDockApp.context.getString(id) else WallDockApp.context.getString(id, *args)

/**
 * Langue de l'appli choisie dans les réglages ("" = celle de la tablette).
 * Appliquée à l'appli, aux écrans et aux services ; changer de langue relance l'appli.
 */
object Language {
    const val KEY = "app_language"
    val CHOICES = listOf("", "fr", "en", "de", "es")

    private fun chosen(context: Context): String =
        context.getSharedPreferences("walldock", Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun wrap(base: Context): Context {
        val tag = chosen(base)
        if (tag.isEmpty()) return base
        val locale = java.util.Locale.forLanguageTag(tag)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** La WebView d'Android remet parfois la langue du système : on réapplique la nôtre. */
    @Suppress("DEPRECATION")
    fun reapply(context: Context) {
        val tag = chosen(context)
        if (tag.isEmpty()) return
        val locale = java.util.Locale.forLanguageTag(tag)
        for (res in listOf(context.resources, WallDockApp.context.resources)) {
            val config = android.content.res.Configuration(res.configuration)
            config.setLocale(locale)
            res.updateConfiguration(config, res.displayMetrics)
        }
    }
}
