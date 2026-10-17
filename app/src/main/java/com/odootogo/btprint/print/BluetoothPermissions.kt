package com.odootogo.btprint.print

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Permissions Bluetooth selon la version d'Android :
 *  - Android 12+ : BLUETOOTH_CONNECT / BLUETOOTH_SCAN, à demander à l'exécution ;
 *  - avant : BLUETOOTH / BLUETOOTH_ADMIN, accordées à l'installation (manifeste, maxSdkVersion 30).
 */
object BluetoothPermissions {

    val runtime: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            emptyList()
        }

    fun granted(context: Context): Boolean =
        runtime.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
}
