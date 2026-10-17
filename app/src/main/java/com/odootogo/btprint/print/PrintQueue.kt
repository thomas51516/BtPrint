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
        // (Re)démarre le service : il a pu s'arrêter seul (file vide, serveur désactivé).
        // Les appels viennent toujours d'un contexte autorisé : activité visible, ou serveur
        // HTTP qui ne tourne que lorsque le service est déjà au premier plan.
        PrintService.start(context.applicationContext)
    }

    /** Vrai si aucun travail n'attend dans la file. */
    val isEmpty: Boolean get() = channel.isEmpty

    internal fun setStatus(text: String) {
        _status.value = text
    }
}
