package com.odootogo.btprint.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.odootogo.btprint.data.PaperWidth
import com.odootogo.btprint.data.PrinterCharset
import com.odootogo.btprint.data.PrinterSettings
import com.odootogo.btprint.print.BluetoothPermissions
import com.odootogo.btprint.print.PrintJob
import com.odootogo.btprint.print.PrintQueue
import com.odootogo.btprint.print.TestReceipts
import com.odootogo.btprint.server.LocalPrintServer
import com.odootogo.btprint.service.PrintService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = PrinterSettings(this)
        setContent {
            // Couleurs dynamiques (Material You) à partir d'Android 12 seulement
            val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(this)
            else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                MainScreen(settings)
            }
        }
    }
}

private data class PairedPrinter(val name: String, val address: String)

private val REQUIRED_PERMISSIONS = buildList {
    addAll(BluetoothPermissions.runtime)           // Android 12+
    // Permission d'exécution uniquement depuis Android 13
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun hasBluetoothPermissions(context: Context) = BluetoothPermissions.granted(context)

@SuppressLint("MissingPermission") // appelé uniquement si la permission est accordée
private fun pairedPrinters(adapter: BluetoothAdapter?): List<PairedPrinter> =
    adapter?.bondedDevices.orEmpty()
        .map { PairedPrinter(it.name ?: "Sans nom", it.address) }
        .sortedBy { it.name.lowercase() }

@Composable
private fun MainScreen(settings: PrinterSettings) {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }

    var hasPerms by remember { mutableStateOf(hasBluetoothPermissions(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(settings.printerAddress) }
    var paper by remember { mutableStateOf(settings.paperWidth) }
    var charset by remember { mutableStateOf(settings.charset) }
    var autoCut by remember { mutableStateOf(settings.autoCut) }
    var serverEnabled by remember { mutableStateOf(settings.serverEnabled) }
    var tokenRequired by remember { mutableStateOf(settings.tokenRequired) }
    var token by remember { mutableStateOf(settings.serverToken) }
    val status by PrintQueue.status.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPerms = hasBluetoothPermissions(context)
        refreshKey++
    }
    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey++ }

    val bluetoothOn = remember(refreshKey, hasPerms) { adapter?.isEnabled == true }
    val printers = remember(refreshKey, hasPerms) {
        if (hasPerms) pairedPrinters(adapter) else emptyList()
    }

    LaunchedEffect(Unit) {
        if (!hasPerms && REQUIRED_PERMISSIONS.isNotEmpty()) permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }
    // Démarre le service (serveur local) dès que tout est prêt
    LaunchedEffect(hasPerms, serverEnabled) {
        if (hasPerms && serverEnabled) PrintService.start(context)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("BT Print", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Impression Bluetooth ESC/POS", style = MaterialTheme.typography.bodyMedium)

            if (!hasPerms) {
                Section("Autorisation requise") {
                    Text("L'app a besoin de l'accès Bluetooth pour parler à l'imprimante.")
                    Button(onClick = { permissionLauncher.launch(REQUIRED_PERMISSIONS) }) {
                        Text("Autoriser")
                    }
                }
                return@Column
            }

            if (!bluetoothOn) {
                Section("Bluetooth désactivé") {
                    Button(onClick = {
                        enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }) { Text("Activer le Bluetooth") }
                }
            }

            Section("Imprimante") {
                if (printers.isEmpty()) {
                    Text("Aucun appareil appairé. Appairez d'abord l'imprimante dans les réglages Bluetooth (code souvent 0000 ou 1234).")
                }
                printers.forEach { printer ->
                    OptionRow(
                        selected = printer.address == selected,
                        title = printer.name,
                        subtitle = printer.address
                    ) {
                        selected = printer.address
                        settings.printerAddress = printer.address
                        settings.printerName = printer.name
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { refreshKey++ }) { Text("Actualiser") }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }) { Text("Réglages Bluetooth") }
                }
            }

            Section("Papier") {
                Text(
                    "Si les lignes sortent coupées en deux, choisissez la largeur inférieure.",
                    style = MaterialTheme.typography.bodySmall
                )
                PaperWidth.entries.forEach { option ->
                    OptionRow(option == paper, option.label, "${option.charsPerLine} caractères / ligne") {
                        paper = option
                        settings.paperWidth = option
                    }
                }
            }

            Section("Encodage des caractères") {
                Text("Si les accents s'impriment mal, essayez une autre table.", style = MaterialTheme.typography.bodySmall)
                PrinterCharset.available.forEach { option ->
                    OptionRow(option == charset, option.label, "ESC t ${option.escPosCode}") {
                        charset = option
                        settings.charset = option
                    }
                }
            }

            Section("Options") {
                SwitchRow("Couper le papier après impression", autoCut) {
                    autoCut = it
                    settings.autoCut = it
                }
                HorizontalDivider()
                SwitchRow("Serveur local (navigateur, Odoo POS)", serverEnabled) {
                    serverEnabled = it
                    settings.serverEnabled = it
                    if (!it) PrintService.stop(context)
                }
                if (serverEnabled) {
                    Text(
                        "http://127.0.0.1:${settings.serverPort}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    HorizontalDivider()
                    SwitchRow("Exiger un jeton (en-tête ${LocalPrintServer.TOKEN_HEADER})", tokenRequired) {
                        tokenRequired = it
                        settings.tokenRequired = it
                    }
                    if (tokenRequired) {
                        Text(
                            "À renseigner dans la page ou le module Odoo qui appelle le serveur.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(token, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("BT Print token", token))
                            }) { Text("Copier") }
                            TextButton(onClick = { token = settings.regenerateToken() }) { Text("Régénérer") }
                        }
                    } else {
                        Text(
                            "Sans jeton, toute page ou application du téléphone peut imprimer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Section("Impression système") {
                Text(
                    "Pour imprimer depuis le menu « Imprimer » de Chrome, d'un lecteur PDF ou d'Odoo " +
                        "dans le navigateur, activez BT Print une fois dans les services d'impression.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_PRINT_SETTINGS))
                }) { Text("Services d'impression") }
            }

            Section("Tests") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = selected != null,
                        onClick = { PrintQueue.submit(context, PrintJob.FormattedText(TestReceipts.text(paper))) }
                    ) { Text("Ticket test") }
                    OutlinedButton(
                        enabled = selected != null,
                        onClick = { PrintQueue.submit(context, PrintJob.Image(TestReceipts.image(paper))) }
                    ) { Text("Image test") }
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun OptionRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
