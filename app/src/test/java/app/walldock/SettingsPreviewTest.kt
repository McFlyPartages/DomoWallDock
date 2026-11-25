package app.walldock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
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
 * Aperçu de l'écran des réglages, sans tablette ni émulateur : Robolectric exécute le vrai
 * code de SettingsActivity et le dessine en PNG (barre du haut + chaque onglet, découpé en pages).
 * Valeurs fictives : aucun vrai mot de passe ni vraie adresse.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsPreviewTest {

    @Test
    @Config(qualifiers = "fr-w1280dp-h800dp-land-mdpi")
    fun paysage() = shoot("paysage")

    @Test
    @Config(qualifiers = "fr-w800dp-h1280dp-port-mdpi")
    fun portrait() = shoot("portrait")

    @Test
    @Config(qualifiers = "de-w1280dp-h800dp-land-mdpi")
    fun allemand() = shoot("de")

    @Test
    @Config(qualifiers = "es-w1280dp-h800dp-land-mdpi")
    fun espagnol() = shoot("es")

    @Test
    @Config(qualifiers = "en-w1280dp-h800dp-land-mdpi")
    fun anglais() = shoot("en")

    private fun shoot(name: String) {
        val prefs = Prefs(RuntimeEnvironment.getApplication())
        prefs.pin = "1234"
        prefs.platform = Prefs.PLATFORM_HA
        prefs.ha.access = Prefs.ACCESS_BOTH
        prefs.ha.ip = "192.168.1.10"
        prefs.ha.external = "https://ha.exemple.fr"
        prefs.haUser = "tablette"
        prefs.haPassword = "secret"
        prefs.gladys.ip = "192.168.1.20"
        prefs.gladysUser = "tablette@exemple.fr"
        prefs.gladysPassword = "secret"

        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        ShadowLooper.idleMainLooper()
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        val bar = root.getChildAt(0)
        val pages = root.getChildAt(1) as ViewGroup
        val width = root.width
        val dir = File(System.getProperty("preview.dir") ?: "build/preview").also { it.mkdirs() }
        // Un jeu d'images par onglet, la barre du haut (onglet en cours) répétée en tête de chacune
        for (tab in 0 until pages.childCount) {
            activity.selectTab(tab)
            ShadowLooper.idleMainLooper()
            val column = (pages.getChildAt(tab) as ScrollView).getChildAt(0)
            column.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            column.layout(0, 0, width, column.measuredHeight)
            val head = Bitmap.createBitmap(width, bar.height, Bitmap.Config.ARGB_8888)
            Canvas(head).also { it.drawColor(0xFF050C1E.toInt()); bar.draw(it) }
            val full = Bitmap.createBitmap(width, column.measuredHeight, Bitmap.Config.ARGB_8888)
            Canvas(full).also { it.drawColor(0xFF050C1E.toInt()); column.draw(it) }
            // Pages de 1600 px de haut au plus
            val page = 1600
            var top = 0
            var index = 1
            while (top < full.height) {
                val h = minOf(page, full.height - top)
                val out = Bitmap.createBitmap(width, bar.height + h, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                c.drawBitmap(head, 0f, 0f, null)
                c.drawBitmap(Bitmap.createBitmap(full, 0, top, width, h), 0f, bar.height.toFloat(), null)
                File(dir, "$name-onglet${tab + 1}-$index.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                top += h
                index++
            }
        }
    }
}
