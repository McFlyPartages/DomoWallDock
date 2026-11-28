package app.walldock

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Verrouillage sur le tableau de bord Gladys et case « Plein écran ». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockedPageTest {

    private lateinit var prefs: Prefs

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("walldock", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(app)
        prefs.platform = Prefs.PLATFORM_GLADYS
        prefs.gladys.access = Prefs.ACCESS_LOCAL
        prefs.gladys.ip = "192.168.80.11"
        prefs.gladys.port = 80
        prefs.gladysDashboard = "/dashboard/salon?fullscreen=force"
        prefs.gladysDashboardLocked = true
    }

    @Test fun pageVerrouilleePermise() {
        assertFalse(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/salon"))
        assertFalse(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/salon/?fullscreen=force"))
        assertFalse(prefs.isOutsideLockedPage("http://192.168.80.11/login"))
    }

    @Test fun autresPagesBloquees() {
        assertTrue(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/cuisine"))
        assertTrue(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/salon/edit"))
        assertTrue(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/integrations"))
        assertTrue(prefs.isOutsideLockedPage("https://exemple.fr/"))
    }

    @Test fun autresTableauxAutorises() {
        prefs.gladysAllowedDashboards = listOf("/dashboard/cuisine", "/dashboard/chambre?fullscreen=force", "  ")
        assertEquals(listOf("/dashboard/cuisine", "/dashboard/chambre?fullscreen=force"), prefs.gladysAllowedDashboards)
        assertFalse(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/cuisine"))
        assertFalse(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/chambre/"))
        assertFalse(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/salon"))
        assertTrue(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/bureau"))
        assertTrue(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/cuisine/edit"))
    }

    @Test fun sansCaseRienNestBloque() {
        prefs.gladysDashboardLocked = false
        assertFalse(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/cuisine"))
    }

    @Test fun homeAssistantPasConcerne() {
        prefs.platform = Prefs.PLATFORM_HA
        assertFalse(prefs.isOutsideLockedPage("http://192.168.80.11/dashboard/cuisine"))
    }

    @Test fun pleinEcran() {
        assertEquals("/dashboard/salon?fullscreen=force", withFullscreen("/dashboard/salon"))
        assertEquals("/dashboard/salon?a=1&fullscreen=force", withFullscreen("/dashboard/salon?a=1"))
        assertEquals("/dashboard/salon?fullscreen=force", withFullscreen("/dashboard/salon?"))
        assertEquals("/dashboard/salon?fullscreen=force#bas", withFullscreen("/dashboard/salon#bas"))
    }

    @Test fun pleinEcranDecoche() {
        assertEquals("/dashboard/salon", withoutFullscreen("/dashboard/salon?fullscreen=force"))
        assertEquals("/dashboard/salon?a=1", withoutFullscreen("/dashboard/salon?a=1&fullscreen=force"))
        assertEquals("/dashboard/salon?a=1&b=2", withoutFullscreen("/dashboard/salon?a=1&fullscreen=force&b=2"))
        assertEquals("/dashboard/salon#bas", withoutFullscreen("/dashboard/salon?fullscreen=force#bas"))
        assertEquals("/dashboard/salon", withoutFullscreen("/dashboard/salon"))
        assertTrue(hasFullscreen("/dashboard/salon?a=1&fullscreen=force"))
        assertTrue(hasFullscreen("/dashboard/salon?fullscreen"))
        assertFalse(hasFullscreen("/dashboard/salon?a=1"))
        assertFalse(hasFullscreen("/dashboard/fullscreen"))
    }
}
