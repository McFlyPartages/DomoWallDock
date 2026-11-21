package app.walldock

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** La langue choisie dans les réglages l'emporte sur celle de la tablette (ici le français). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr")
class LanguageTest {

    private fun titleIn(tag: String): String {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("walldock", Context.MODE_PRIVATE).edit().putString(Language.KEY, tag).commit()
        return Language.wrap(app).getString(R.string.settings_title)
    }

    @Test fun systeme() = assertEquals("DomoWallDock — réglages", titleIn(""))
    @Test fun anglais() = assertEquals("DomoWallDock — settings", titleIn("en"))
    @Test fun allemand() = assertEquals("DomoWallDock — Einstellungen", titleIn("de"))
    @Test fun espagnol() = assertEquals("DomoWallDock — ajustes", titleIn("es"))
}
