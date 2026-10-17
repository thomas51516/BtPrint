package com.odootogo.btprint.print

import android.content.Context
import com.odootogo.btprint.service.PrintService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * File d'impression partagée par toute l'app (UI, partage, serveur HTTP).
 * Le [PrintService] la consomme travail par travail, dans l'ordre d'arrivée.
 */
object PrintQueue {

    private val channel = Channel<PrintJob>(Channel.UNLIMITED)
    val jobs: ReceiveChannel<PrintJob> get() = channel

    private val _status = MutableStateFlow("En attente")
    val status: StateFlow<String> = _status.asStateFlow()

    fun submit(context: Context, job: PrintJob) {
        channel.trySend(job)
        _status.value = "Ajouté à la file : ${job.label}"
        // Ne démarre le service que s'il ne tourne pas déjà
        // (évite un démarrage de service au premier plan depuis l'arrière-plan).
        if (!PrintService.isRunning) PrintService.start(context.applicationContext)
    }

    internal fun setStatus(text: String) {
        _status.value = text
    }
}
