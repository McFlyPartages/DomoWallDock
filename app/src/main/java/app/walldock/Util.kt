package app.walldock

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.text.InputType
import android.widget.EditText
import android.widget.Toast

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** Fenêtre par-dessus les autres applis : TYPE_APPLICATION_OVERLAY à partir d'Android 8, TYPE_PHONE avant. */
@Suppress("DEPRECATION")
val overlayWindowType: Int
    get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        android.view.WindowManager.LayoutParams.TYPE_PHONE
    }

/** Service au premier plan : startForegroundService n'existe qu'à partir d'Android 8. */
fun Context.startServiceCompat(intent: android.content.Intent) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
}

/** Autorisation « installer des applis inconnues » : réglage global avant Android 8. */
fun Context.canInstallPackages(): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

data class LaunchableApp(val packageName: String, val label: String, val icon: Drawable)

object Apps {

    /** Toutes les applis lançables installées, sauf WallDock, triées par nom. */
    fun listLaunchable(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(intent, 0)
        return activities
            .filter { it.activityInfo.packageName != context.packageName }
            .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Une appli installée, ou null si elle a été désinstallée. */
    fun load(context: Context, packageName: String): LaunchableApp? {
        val pm = context.packageManager
        return try {
            @Suppress("DEPRECATION")
            val info = pm.getApplicationInfo(packageName, 0)
            LaunchableApp(packageName, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

object Dashboard {
    /** Ramène le tableau de bord WallDock au premier plan. */
    fun bringToFront(context: Context) {
        // 1) Ouverture directe : autorisée tant qu'une fenêtre WallDock est visible (bouton ⌂)
        try {
            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Bloqué par Android : le bouton Accueil virtuel ci-dessous prend le relais
        }
        // 2) Bouton Accueil virtuel (détection d'activité active) : ramène à WallDock s'il est l'écran d'accueil
        InteractionService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
    }
}

object Pin {
    /** Demande le code administrateur, puis lance [onSuccess] s'il est correct. */
    fun ask(activity: Activity, prefs: Prefs, onSuccess: () -> Unit) {
        if (prefs.pin.isBlank()) {
            onSuccess()
            return
        }
        val input = EditText(activity)
        input.isSingleLine = true
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        input.hint = tr(R.string.pin_title)
        val check = {
            if (input.text.toString() == prefs.pin) onSuccess() else activity.toast(tr(R.string.pin_wrong))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(tr(R.string.pin_title))
            .setView(input)
            .setPositiveButton("OK") { _, _ -> check() }
            .setNegativeButton(tr(R.string.cancel), null)
            .create()
        // La touche « OK » / Entrée du pavé numérique valide aussi le code
        input.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || enter) {
                dialog.dismiss()
                check()
                true
            } else {
                false
            }
        }
        // Pavé numérique ouvert d'emblée
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        input.requestFocus()
    }
}
