package app.walldock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
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
 * Captures des README (docs/images/readme/<langue>) : l'écran des réglages tel qu'il apparaît
 * sur une tablette en paysage (1280 × 800 dp), onglets Connexions et Services.
 * Valeurs d'exemple uniquement. Régénérer :
 * ./gradlew testGithubDebugUnitTest --tests app.walldock.ReadmeScreenshotsTest
 * puis copier app/build/preview/readme/<langue>/ dans docs/images/readme/<langue>/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReadmeScreenshotsTest {

    @Test @Config(qualifiers = "fr-w1280dp-h800dp-land-mdpi") fun fr() = shoot("fr", "tablette@exemple.fr", "/dashboard/salon", "Réveil")
    @Test @Config(qualifiers = "en-w1280dp-h800dp-land-mdpi") fun en() = shoot("en", "tablet@example.com", "/dashboard/living-room", "Wake up")
    @Test @Config(qualifiers = "de-w1280dp-h800dp-land-mdpi") fun de() = shoot("de", "tablet@beispiel.de", "/dashboard/wohnzimmer", "Wecker")
    @Test @Config(qualifiers = "es-w1280dp-h800dp-land-mdpi") fun es() = shoot("es", "tableta@ejemplo.es", "/dashboard/salon", "Despertador")

    private fun shoot(lang: String, email: String, dashboard: String, alarm: String) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("walldock", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = Prefs(app)
        prefs.pin = "1234"
        prefs.platform = Prefs.PLATFORM_GLADYS
        prefs.ha.access = Prefs.ACCESS_BOTH
        prefs.ha.ip = "192.168.1.10"
        prefs.ha.external = "https://ha.example.com"
        prefs.haUser = "tablet"
        prefs.haPassword = "password"
        prefs.gladys.access = Prefs.ACCESS_LOCAL
        prefs.gladys.ip = "192.168.1.20"
        prefs.gladys.port = 80
        prefs.gladysUser = email
        prefs.gladysPassword = "password"
        prefs.gladysDashboard = withFullscreen(dashboard)
        prefs.gladysDashboardLocked = true
        prefs.calendarInDock = true
        prefs.chargeEnabled = true
        prefs.mqttEnabled = true
        prefs.mqttHost = "192.168.1.20"
        prefs.mqttUser = "tablet"
        prefs.mqttPassword = "password"
        Alarms.save(app, emptyList())
        Alarms.add(app, 7, 0, 0b0011111, alarm, true)

        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        ShadowLooper.idleMainLooper()
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        // Pas de curseur dans le premier champ sur les captures
        root.isFocusableInTouchMode = true
        root.requestFocus()
        val dir = File(System.getProperty("preview.dir") ?: "build/preview", "readme/$lang").also { it.mkdirs() }
        for ((tab, name) in listOf(SettingsActivity.TAB_CONNECTION to "reglages-connexions", SettingsActivity.TAB_SERVICES to "reglages-services")) {
            activity.selectTab(tab)
            ShadowLooper.idleMainLooper()
            // Écran entier, comme sur la tablette : barre du haut + début de l'onglet
            val shot = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            Canvas(shot).also { it.drawColor(0xFF050C1E.toInt()); root.draw(it) }
            File(dir, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
