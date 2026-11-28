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

    @Test @Config(qualifiers = "fr-w800dp-h1280dp-port-mdpi") fun gladysFr() = gladys("fr", "tablette@exemple.fr", "/dashboard/salon", "/dashboard/veille", "cuisine")
    @Test @Config(qualifiers = "en-w800dp-h1280dp-port-mdpi") fun gladysEn() = gladys("en", "tablet@example.com", "/dashboard/living-room", "/dashboard/screensaver", "kitchen")
    @Test @Config(qualifiers = "de-w800dp-h1280dp-port-mdpi") fun gladysDe() = gladys("de", "tablet@beispiel.de", "/dashboard/wohnzimmer", "/dashboard/bildschirmschoner", "kueche")
    @Test @Config(qualifiers = "es-w800dp-h1280dp-port-mdpi") fun gladysEs() = gladys("es", "tableta@ejemplo.es", "/dashboard/salon", "/dashboard/salvapantallas", "cocina")

    @Test @Config(qualifiers = "fr-w1280dp-h800dp-land-mdpi") fun dockFr() = dock("fr")
    @Test @Config(qualifiers = "en-w1280dp-h800dp-land-mdpi") fun dockEn() = dock("en")
    @Test @Config(qualifiers = "de-w1280dp-h800dp-land-mdpi") fun dockDe() = dock("de")
    @Test @Config(qualifiers = "es-w1280dp-h800dp-land-mdpi") fun dockEs() = dock("es")

    private fun gladys(lang: String, email: String, dashboard: String, saver: String, allowed: String) {
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
        prefs.gladysDashboardLocked = true
        prefs.gladysAllowedDashboards = listOf(dashboard.substringBeforeLast('/') + "/" + allowed)
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
        val pages = root.getChildAt(1) as ViewGroup
        val width = root.width

        val dir = File(System.getProperty("preview.dir") ?: "build/preview", "tuto/$lang").also { it.mkdirs() }
        fun save(name: String, bitmap: Bitmap) =
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Barre du haut (onglet Connexion en cours)
        val top = Bitmap.createBitmap(width, bar.height, Bitmap.Config.ARGB_8888)
        Canvas(top).also { bar.draw(it) }
        save("01-barre", top)

        /** Dessine l'onglet [tab] en entier ; ses titres de section (repérés par leur tag) servent à découper. */
        fun render(tab: Int): Pair<Bitmap, List<View>> {
            activity.selectTab(tab)
            ShadowLooper.idleMainLooper()
            val column = (pages.getChildAt(tab) as ScrollView).getChildAt(0) as ViewGroup
            column.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            column.layout(0, 0, width, column.measuredHeight)
            val full = Bitmap.createBitmap(width, column.measuredHeight, Bitmap.Config.ARGB_8888)
            Canvas(full).also { it.drawColor(0xFF050C1E.toInt()); column.draw(it) }
            val headings = (0 until column.childCount).map { column.getChildAt(it) }.filter { it.tag == SettingsActivity.HEADING_TAG }
            return full to headings
        }
        /** D'un titre (null : haut de l'onglet) au titre suivant (null : bas de l'onglet). */
        fun crop(name: String, shot: Pair<Bitmap, List<View>>, from: String?, whole: Boolean = false) {
            val (full, headings) = shot
            val i = if (from == null) -1 else headings.indexOfFirst { (it as TextView).text.toString() == from }
            check(from == null || i >= 0) { "titre introuvable : $from" }
            val start = if (i < 0) 0 else headings[i].top
            val end = if (whole) full.height else headings.getOrNull(i + 1)?.top ?: full.height
            save(name, Bitmap.createBitmap(full, 0, start, width, end - start))
        }
        crop("02-connexion", render(SettingsActivity.TAB_CONNECTION), null, whole = true)
        val general = render(SettingsActivity.TAB_GENERAL)
        crop("03-communs", general, null)
        crop("04-veille", general, tr(R.string.settings_screensaver))
        crop("05-nuit", general, tr(R.string.settings_schedule_title))
        crop("06-mouvement", general, tr(R.string.settings_motion_title))
        crop("07-autorisations", general, tr(R.string.settings_permissions))
        val services = render(SettingsActivity.TAB_SERVICES)
        crop("08-calendrier", services, tr(R.string.calendar_title))
        crop("09-batterie", services, tr(R.string.settings_charge_title))
        crop("10-mqtt", services, tr(R.string.settings_mqtt_title))
        crop("11-photos", services, tr(R.string.settings_photos_title))
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
