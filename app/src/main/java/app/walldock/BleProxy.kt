package app.walldock

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.walldock.esphome.EsphomeServer

/**
 * Proxy Bluetooth ESPHome : la tablette écoute les annonces Bluetooth Low Energy et les
 * transmet brutes à Home Assistant (BluetoothLERawAdvertisementsResponse), qui les décode
 * comme celles d'un module ESPHome « bluetooth_proxy » (écoute seule, pas de connexion).
 *
 * L'écoute ne tourne que si l'option est activée ET qu'un Home Assistant est abonné.
 */
class BleProxy(private val context: Context, private val server: EsphomeServer) {

    enum class Status { OFF, WAITING, SCANNING, NO_PERMISSION, BT_OFF, UNSUPPORTED, ERROR }

    private val main = Handler(Looper.getMainLooper())
    private val pending = ArrayList<EsphomeServer.BleAdvertisement>()
    private var scanning = false
    private var wanted = false
    private var receiverRegistered = false

    /** Option activée (réglages) et HA abonné : on écoute. */
    fun update(enabled: Boolean, subscribed: Boolean) {
        main.post {
            wanted = enabled && subscribed
            if (enabled) registerReceiver() else unregisterReceiver()
            if (wanted) start() else stop(if (enabled) Status.WAITING else Status.OFF)
        }
    }

    fun release() {
        main.post {
            wanted = false
            unregisterReceiver()
            stop(Status.OFF)
        }
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        if (scanning) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            setStatus(Status.UNSUPPORTED)
            return
        }
        if (!hasPermissions(context)) {
            setStatus(Status.NO_PERMISSION)
            return
        }
        if (!adapter.isEnabled) {
            setStatus(Status.BT_OFF)
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            setStatus(Status.BT_OFF)
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()
        // Un filtre vide (qui laisse tout passer) : sans filtre, Android coupe l'écoute écran éteint
        val filters = listOf(ScanFilter.Builder().build())
        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            setStatus(Status.NO_PERMISSION)
            return
        } catch (e: IllegalStateException) {
            setStatus(Status.BT_OFF)
            return
        }
        scanning = true
        seen.clear()
        setStatus(Status.SCANNING)
        server.setBluetoothScanning(true)
        // Android ralentit les écoutes de plus de 30 minutes : on relance avant
        main.removeCallbacks(restart)
        main.postDelayed(restart, RESTART_MS)
        main.removeCallbacks(flush)
        main.postDelayed(flush, FLUSH_MS)
    }

    @SuppressLint("MissingPermission")
    private fun stop(next: Status) {
        main.removeCallbacks(restart)
        main.removeCallbacks(flush)
        if (scanning) {
            scanning = false
            try {
                context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback)
            } catch (e: Exception) {
                // Bluetooth éteint entre-temps ou autorisation retirée : l'écoute est déjà arrêtée
            }
            server.setBluetoothScanning(false)
        }
        synchronized(pending) { pending.clear() }
        setStatus(next)
    }

    private val restart = Runnable {
        if (!scanning) return@Runnable
        stop(Status.WAITING)
        if (wanted) start()
    }

    private val flush = object : Runnable {
        override fun run() {
            val batch = synchronized(pending) {
                val copy = ArrayList(pending)
                pending.clear()
                copy
            }
            server.sendBleAdvertisements(batch)
            if (scanning) main.postDelayed(this, FLUSH_MS)
        }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = add(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { add(it) }

        override fun onScanFailed(errorCode: Int) {
            main.post {
                scanning = false
                server.setBluetoothScanning(false)
                errorCodeValue = errorCode
                setStatus(Status.ERROR)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun add(result: ScanResult) {
        val raw = result.scanRecord?.bytes ?: return
        val data = trim(raw)
        if (data.isEmpty()) return
        val address = try {
            result.device.address.replace(":", "").toLong(16)
        } catch (e: NumberFormatException) {
            return
        }
        // Type d'adresse (publique / aléatoire) : connu d'Android 15 ; sans effet en écoute seule
        val type = if (Build.VERSION.SDK_INT >= 35) {
            try {
                if (result.device.addressType == BluetoothDevice.ADDRESS_TYPE_PUBLIC) 0 else 1
            } catch (e: SecurityException) {
                0
            }
        } else {
            0
        }
        seen[address] = SystemClock.elapsedRealtime()
        synchronized(pending) {
            if (pending.size < MAX_PENDING) pending.add(EsphomeServer.BleAdvertisement(address, result.rssi, type, data))
        }
    }

    /**
     * Android complète l'annonce par des zéros (62 octets) : on garde les structures
     * « longueur, type, données » jusqu'à la première de longueur nulle.
     */
    private fun trim(raw: ByteArray): ByteArray {
        var i = 0
        while (i < raw.size) {
            val length = raw[i].toInt() and 0xFF
            if (length == 0 || i + 1 + length > raw.size) break
            i += 1 + length
        }
        return raw.copyOf(i)
    }

    private fun setStatus(value: Status) {
        status = value
    }

    // Bluetooth rallumé sur la tablette : l'écoute reprend toute seule
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> if (wanted) {
                    scanning = false
                    start()
                }
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> if (scanning) stop(Status.BT_OFF)
            }
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(stateReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(stateReceiver)
        } catch (e: IllegalArgumentException) {
            // déjà retiré
        }
        receiverRegistered = false
    }

    companion object {
        /** Fonctions annoncées à HA : écoute, annonces brutes, état de l'écoute. */
        const val FEATURES = EsphomeServer.BLE_FEATURE_PASSIVE_SCAN or
            EsphomeServer.BLE_FEATURE_RAW_ADVERTISEMENTS or
            EsphomeServer.BLE_FEATURE_STATE_AND_MODE

        private const val FLUSH_MS = 100L
        private const val RESTART_MS = 25 * 60 * 1000L
        private const val MAX_PENDING = 512
        private const val SEEN_WINDOW_MS = 5 * 60 * 1000L

        @Volatile
        var status = Status.OFF
            private set

        @Volatile
        private var errorCodeValue = 0

        /** Appareils vus (adresse → dernière annonce), pour l'affichage dans les réglages. */
        private val seen = java.util.concurrent.ConcurrentHashMap<Long, Long>()

        /** Autorisations nécessaires : la position aussi, sinon Android masque les balises. */
        fun requiredPermissions(): List<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            }

        fun hasPermissions(context: Context): Boolean =
            requiredPermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

        fun statusText(context: Context): String = when (status) {
            Status.OFF -> tr(R.string.ble_status_off)
            Status.WAITING -> tr(R.string.ble_status_waiting)
            Status.SCANNING -> {
                val since = SystemClock.elapsedRealtime() - SEEN_WINDOW_MS
                seen.entries.removeIf { it.value < since }
                tr(R.string.ble_status_scanning, seen.size)
            }
            Status.NO_PERMISSION -> tr(R.string.ble_status_no_permission)
            Status.BT_OFF -> tr(R.string.ble_status_bt_off)
            Status.UNSUPPORTED -> tr(R.string.ble_status_unsupported)
            Status.ERROR -> tr(R.string.ble_status_error, errorCodeValue)
        }
    }
}
