package com.odootogo.btprint.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.odootogo.btprint.R
import com.odootogo.btprint.data.PrinterSettings
import com.odootogo.btprint.print.PrintJob
import com.odootogo.btprint.print.PrintQueue
import com.odootogo.btprint.print.PrinterEngine
import com.odootogo.btprint.server.LocalPrintServer
import com.odootogo.btprint.ui.MainActivity
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service au premier plan (type connectedDevice) :
 *  - consomme la [PrintQueue] et imprime les travaux un par un ;
 *  - fait tourner le serveur HTTP local si activé.
 */
class PrintService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var engine: PrinterEngine
    private var server: LocalPrintServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = PrinterEngine(this)
        createChannel()
        scope.launch {
            while (isActive) {
                process(PrintQueue.jobs.receive())
                if (shouldStopWhenIdle()) {
                    stopSelf()
                    break
                }
            }
        }
    }

    /**
     * Sans serveur local, le service n'a aucune raison de rester au premier plan une fois
     * la file vidée. On patiente un peu : l'utilisateur partage souvent plusieurs documents
     * d'affilée et cela évite de faire clignoter la notification.
     */
    private suspend fun shouldStopWhenIdle(): Boolean {
        if (PrinterSettings(this).serverEnabled) return false
        delay(IDLE_TIMEOUT_MS)
        return PrintQueue.isEmpty && !PrinterSettings(this).serverEnabled
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // startForeground doit être appelé après chaque startForegroundService()
        try {
            val notification = buildNotification(PrintQueue.status.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification) // pas de type avant Android 10
            }
        } catch (e: Exception) {
            // Typiquement : permission BLUETOOTH_CONNECT non accordée
            Log.e(TAG, "Impossible de passer au premier plan", e)
            PrintQueue.setStatus("Service arrêté : ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        syncServer()
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        scope.cancel()
        super.onDestroy()
    }

    private fun process(job: PrintJob) {
        updateNotification("Impression : ${job.label}…")
        PrintQueue.setStatus("Impression en cours : ${job.label}…")

        val message = try {
            engine.print(job)
            "Imprimé : ${job.label}"
        } catch (e: Throwable) {
            Log.e(TAG, "Échec d'impression", e)
            "Échec (${job.label}) : ${e.message ?: e.javaClass.simpleName}"
        } finally {
            job.cleanup()
        }

        PrintQueue.setStatus(message)
        updateNotification(message)
    }

    /** Démarre, redémarre (changement de port) ou arrête le serveur local selon les réglages. */
    private fun syncServer() {
        val settings = PrinterSettings(this)
        if (!settings.serverEnabled) {
            server?.stop()
            server = null
            return
        }
        val current = server
        if (current != null && current.isAlive && current.listeningPort == settings.serverPort) return

        current?.stop()
        server = LocalPrintServer(applicationContext, settings.serverPort).also { s ->
            try {
                s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                Log.i(TAG, "Serveur local sur http://127.0.0.1:${settings.serverPort}")
            } catch (e: Exception) {
                Log.e(TAG, "Serveur local indisponible", e)
                PrintQueue.setStatus("Serveur local indisponible : ${e.message}")
            }
        }
    }

    // --- Notification -------------------------------------------------------------------------

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, PrintService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val settings = PrinterSettings(this)
        val title = settings.printerName?.let { "Imprimante : $it" } ?: "BT Print"

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_print)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Arrêter", stop).build())
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val TAG = "PrintService"
        private const val CHANNEL_ID = "print_service"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.odootogo.btprint.STOP"
        private const val IDLE_TIMEOUT_MS = 5_000L

        /** À appeler depuis le premier plan (activité visible). Relit aussi les réglages du serveur. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, PrintService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PrintService::class.java))
        }
    }
}
