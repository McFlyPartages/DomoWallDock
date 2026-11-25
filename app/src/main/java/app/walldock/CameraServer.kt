package app.walldock

import android.os.SystemClock
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Caméra partagée : petit serveur HTTP qui sert l'image de la caméra frontale à Home Assistant
 * et à Gladys (ou à tout ce qui lit une caméra MJPEG).
 *
 * - `/snapshot.jpg?token=…` : une photo (pour une notification sur mouvement) ;
 * - `/stream.mjpeg?token=…` : le flux en direct, environ 6 images par seconde.
 *
 * Sans le bon jeton : 401. Les images ne sont encodées que pendant qu'on les demande et ne
 * sont jamais enregistrées sur la tablette.
 */
class CameraServer(
    private val port: Int,
    private val token: () -> String,
    private val camera: () -> MotionDetector?
) {

    private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { Thread(it, "walldock-camera-http").apply { isDaemon = true } }
    private val clients = AtomicInteger(0)

    @Volatile
    var lastError: String? = null
        private set

    val isRunning: Boolean
        get() = socket?.isClosed == false

    @Synchronized
    fun start() {
        if (isRunning) return
        try {
            val server = ServerSocket()
            server.reuseAddress = true
            server.bind(InetSocketAddress(port))
            socket = server
            lastError = null
            Thread({ acceptLoop(server) }, "walldock-camera-accept").apply { isDaemon = true }.start()
        } catch (e: Exception) {
            lastError = tr(R.string.port_in_use, port)
        }
    }

    @Synchronized
    fun stop() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // déjà fermé
        }
        socket = null
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: Exception) {
                break
            }
            if (clients.incrementAndGet() > MAX_CLIENTS) {
                clients.decrementAndGet()
                closeQuietly(client)
                continue
            }
            pool.execute {
                try {
                    handle(client, server)
                } catch (e: Exception) {
                    // client parti
                } finally {
                    clients.decrementAndGet()
                    closeQuietly(client)
                }
            }
        }
    }

    private fun handle(client: Socket, server: ServerSocket) {
        client.soTimeout = 10_000
        client.tcpNoDelay = true
        val requestLine = readHeaders(client.getInputStream()) ?: return
        val out = BufferedOutputStream(client.getOutputStream(), 64 * 1024)
        val parts = requestLine.split(' ')
        if (parts.size < 2 || (parts[0] != "GET" && parts[0] != "HEAD")) {
            status(out, 405, "Method Not Allowed")
            return
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "")
        val given = query.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "token" }
            ?.let { URLDecoder.decode(it[1], "UTF-8") } ?: ""
        if (!sameToken(given, token())) {
            status(out, 401, "Unauthorized")
            return
        }
        val detector = camera()
        if (detector == null || !detector.running) {
            status(out, 503, "Service Unavailable")
            return
        }
        when (path) {
            "/snapshot.jpg", "/snapshot.jpeg", "/image.jpg" -> snapshot(out, detector, parts[0] == "HEAD")
            "/stream.mjpeg", "/stream.mjpg", "/video.mjpg" -> stream(out, detector, server)
            else -> status(out, 404, "Not Found")
        }
    }

    private fun snapshot(out: OutputStream, detector: MotionDetector, headOnly: Boolean) {
        val asked = SystemClock.elapsedRealtime()
        detector.requestJpeg(KEEP_ENCODING_MS)
        val frame = detector.awaitJpeg(asked - 1, FIRST_FRAME_TIMEOUT_MS)
        if (frame == null) {
            status(out, 503, "Service Unavailable")
            return
        }
        val jpeg = frame.first
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray()
        )
        if (!headOnly) out.write(jpeg)
        out.flush()
    }

    private fun stream(out: OutputStream, detector: MotionDetector, server: ServerSocket) {
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=$BOUNDARY\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray()
        )
        out.flush()
        var last = SystemClock.elapsedRealtime() - 1
        var misses = 0
        while (!server.isClosed && detector.running) {
            detector.requestJpeg(KEEP_ENCODING_MS)
            val frame = detector.awaitJpeg(last, FIRST_FRAME_TIMEOUT_MS)
            if (frame == null) {
                // Plus d'image (caméra prise par une autre appli…) : on rend la main
                if (++misses >= 3) return
                continue
            }
            misses = 0
            last = frame.second
            val jpeg = frame.first
            out.write("--$BOUNDARY\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n".toByteArray())
            out.write(jpeg)
            out.write("\r\n".toByteArray())
            out.flush()
        }
    }

    /** Lit la requête jusqu'à la ligne vide ; renvoie la première ligne. */
    private fun readHeaders(input: InputStream): String? {
        val buffer = StringBuilder()
        var first: String? = null
        var total = 0
        while (true) {
            val c = input.read()
            if (c < 0) return null
            if (++total > MAX_HEADER_BYTES) return null
            if (c == '\n'.code) {
                val line = buffer.toString().trimEnd('\r')
                buffer.setLength(0)
                if (first == null) first = line
                else if (line.isEmpty()) return first
            } else {
                buffer.append(c.toChar())
            }
        }
    }

    private fun status(out: OutputStream, code: Int, text: String) {
        val body = "$code $text\n".toByteArray()
        out.write(
            ("HTTP/1.1 $code $text\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n").toByteArray()
        )
        out.write(body)
        out.flush()
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (e: Exception) {
            // déjà fermé
        }
    }

    companion object {
        const val DEFAULT_PORT = 8081
        private const val BOUNDARY = "domowalldockframe"
        private const val MAX_CLIENTS = 6
        private const val MAX_HEADER_BYTES = 8 * 1024
        /** Les JPEG restent encodés 3 s après la dernière demande. */
        private const val KEEP_ENCODING_MS = 3_000L
        private const val FIRST_FRAME_TIMEOUT_MS = 4_000L

        /** Comparaison en temps constant : ne pas laisser deviner le jeton à la durée de réponse. */
        fun sameToken(given: String, expected: String): Boolean =
            expected.isNotEmpty() && MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())

        /** Adresses à donner à Home Assistant et Gladys. */
        fun streamUrl(ip: String, token: String) = "http://$ip:$DEFAULT_PORT/stream.mjpeg?token=$token"
        fun snapshotUrl(ip: String, token: String) = "http://$ip:$DEFAULT_PORT/snapshot.jpg?token=$token"
    }
}
