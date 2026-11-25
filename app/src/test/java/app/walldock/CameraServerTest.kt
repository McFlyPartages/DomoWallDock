package app.walldock

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

/** Caméra partagée : jeton exigé, caméra absente, jeton enregistré. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraServerTest {

    private lateinit var prefs: Prefs
    private lateinit var server: CameraServer
    private var port = 0

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("walldock", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(app)
        port = ServerSocket(0).use { it.localPort }
        server = CameraServer(port, token = { "secret-token-123" }, camera = { null })
        server.start()
        assertTrue(server.isRunning)
    }

    @After fun tearDown() = server.stop()

    private fun code(path: String): Int {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 3_000
        connection.readTimeout = 5_000
        return try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    @Test fun refusesWithoutToken() {
        assertEquals(401, code("/snapshot.jpg"))
        assertEquals(401, code("/stream.mjpeg?token=wrong"))
        assertEquals(401, code("/snapshot.jpg?token="))
    }

    @Test fun goodTokenWithoutCameraIsUnavailable() {
        assertEquals(503, code("/snapshot.jpg?token=secret-token-123"))
    }

    @Test fun unknownPathWithGoodToken() {
        // La caméra manque : on répond 503 avant même de regarder le chemin
        assertEquals(503, code("/nothing?token=secret-token-123"))
    }

    @Test fun stopClosesThePort() {
        server.stop()
        assertFalse(server.isRunning)
    }

    @Test fun tokenComparison() {
        assertTrue(CameraServer.sameToken("abc", "abc"))
        assertFalse(CameraServer.sameToken("abd", "abc"))
        assertFalse(CameraServer.sameToken("", ""))
    }

    @Test fun tokenIsCreatedOnceAndCanBeRenewed() {
        val first = prefs.cameraToken
        assertEquals(32, first.length)
        assertEquals(first, prefs.cameraToken)
        val second = prefs.newCameraToken()
        assertNotEquals(first, second)
        assertEquals(second, prefs.cameraToken)
        assertFalse(prefs.cameraShareEnabled)
        assertEquals(-1, prefs.cameraRotation)
    }

    @Test fun addresses() {
        assertEquals("http://192.168.60.20:8081/stream.mjpeg?token=t", CameraServer.streamUrl("192.168.60.20", "t"))
        assertEquals("http://192.168.60.20:8081/snapshot.jpg?token=t", CameraServer.snapshotUrl("192.168.60.20", "t"))
    }
}
