package app.walldock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Captures du tutoriel Gladys (docs/images/tuto-gladys) : le vrai écran des réglages,
 * configuré pour Gladys avec des valeurs d'exemple, découpé section par section.
 * Régénérer : ./gradlew testGithubDebugUnitTest --tests app.walldock.TutoScreenshotsTest
 * puis copier app/build/preview/tuto/<langue>/ dans docs/images/tuto-gladys/<langue>/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TutoScreenshotsTest {

    @Test @Config(qualifiers = "fr-w800dp-h1280dp-port-mdpi") fun gladysFr() = gladys("fr", "tablette@exemple.fr", "/dashboard/salon", "/dashboard/veille")
    @Test @Config(qualifiers = "en-w800dp-h1280dp-port-mdpi") fun gladysEn() = gladys("en", "tablet@example.com", "/dashboard/living-room", "/dashboard/screensaver")
    @Test @Config(qualifiers = "de-w800dp-h1280dp-port-mdpi") fun gladysDe() = gladys("de", "tablet@beispiel.de", "/dashboard/wohnzimmer", "/dashboard/bildschirmschoner")
    @Test @Config(qualifiers = "es-w800dp-h1280dp-port-mdpi") fun gladysEs() = gladys("es", "tableta@ejemplo.es", "/dashboard/salon", "/dashboard/salvapantallas")

    @Test @Config(qualifiers = "fr-w1280dp-h800dp-land-mdpi") fun dockFr() = dock("fr")
    @Test @Config(qualifiers = "en-w1280dp-h800dp-land-mdpi") fun dockEn() = dock("en")
    @Test @Config(qualifiers = "de-w1280dp-h800dp-land-mdpi") fun dockDe() = dock("de")
    @Test @Config(qualifiers = "es-w1280dp-h800dp-land-mdpi") fun dockEs() = dock("es")

    private fun gladys(lang: String, email: String, dashboard: String, saver: String) {
        val app = RuntimeEnvironment.getApplication()
        val prefs = Prefs(app)
        prefs.pin = "1234"
        prefs.platform = Prefs.PLATFORM_GLADYS
        prefs.gladys.access = Prefs.ACCESS_LOCAL
        prefs.gladys.ip = "192.168.1.20"
        prefs.gladys.port = 80
        prefs.gladysUser = email
        prefs.gladysPassword = "motdepasse"
        prefs.gladysDashboard = dashboard
        prefs.mqttEnabled = true
        prefs.mqttHost = "192.168.1.20"
        prefs.mqttUser = "tablet"
        prefs.mqttPassword = "motdepasse"
        prefs.chargeEnabled = true
        prefs.scheduleEnabled = true
        prefs.motionEnabled = true
        prefs.motionOffSeconds = 300
        prefs.screensaverEnabled = true
        prefs.screensaverUrl = saver

        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        ShadowLooper.idleMainLooper()
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        val bar = root.getChildAt(0)
        val column = (root.getChildAt(1) as ScrollView).getChildAt(0) as ViewGroup
        val width = root.width
        column.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        column.layout(0, 0, width, column.measuredHeight)
        val full = Bitmap.createBitmap(width, column.measuredHeight, Bitmap.Config.ARGB_8888)
        Canvas(full).also { it.drawColor(0xFF050C1E.toInt()); column.draw(it) }

        val dir = File(System.getProperty("preview.dir") ?: "build/preview", "tuto/$lang").also { it.mkdirs() }
        fun save(name: String, bitmap: Bitmap) =
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Barre du haut
        val top = Bitmap.createBitmap(width, bar.height, Bitmap.Config.ARGB_8888)
        Canvas(top).also { bar.draw(it) }
        save("01-barre", top)

        // Titres de section (repérés par leur tag) : chaque capture va d'un titre au suivant
        val headings = (0 until column.childCount).map { column.getChildAt(it) }.filter { it.tag == SettingsActivity.HEADING_TAG }
        fun crop(name: String, from: String, untilNext: Int = 1) {
            val i = headings.indexOfFirst { (it as TextView).text.toString() == from }
            check(i >= 0) { "titre introuvable : $from" }
            val start = headings[i].top
            val end = headings.getOrNull(i + untilNext)?.top ?: column.measuredHeight
            save(name, Bitmap.createBitmap(full, 0, start, width, end - start))
        }
        crop("02-connexion", tr(R.string.settings_section_connection), untilNext = 2)
        crop("03-communs", tr(R.string.settings_common), untilNext = 1)
        crop("04-veille", tr(R.string.settings_screensaver))
        crop("05-nuit", tr(R.string.settings_schedule_title))
        crop("06-mouvement", tr(R.string.settings_motion_title))
        crop("07-autorisations", tr(R.string.settings_permissions))
        crop("08-calendrier", tr(R.string.calendar_title))
        crop("09-batterie", tr(R.string.settings_charge_title))
        crop("10-mqtt", tr(R.string.settings_mqtt_title))
        crop("11-photos", tr(R.string.settings_photos_title))
    }

    /** Écran principal, dock ouvert (la page Gladys elle-même n'est pas dessinée : pas de navigateur ici). */
    private fun dock(lang: String) {
        val prefs = Prefs(RuntimeEnvironment.getApplication())
        prefs.pin = "1234"
        prefs.platform = Prefs.PLATFORM_GLADYS
        prefs.gladys.ip = "192.168.1.20"
        prefs.calendarInDock = true
        prefs.jarvisWakeWord = false // Gladys : Jarvis décoché, donc absent du dock
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        ShadowLooper.idleMainLooper()
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        // La poignée ▲ ouvre le dock
        val handle = (0 until root.childCount).map { root.getChildAt(it) }.first { it is TextView && it.text == "▲" }
        handle.performClick()
        ShadowLooper.idleMainLooper()
        val shot = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        Canvas(shot).also { it.drawColor(0xFF1B2433.toInt()); root.draw(it) }
        val dir = File(System.getProperty("preview.dir") ?: "build/preview", "tuto/$lang").also { it.mkdirs() }
        val dock = Bitmap.createBitmap(shot, 0, root.height - 260, root.width, 260)
        File(dir, "00-dock.png").outputStream().use { dock.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
