package zw.ecocash.forwarder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { Root() } } }
    }

    @Composable
    fun Root() {
        val settings = remember { Settings(this) }
        var unlocked by remember { mutableStateOf(settings.ownerPin.isEmpty()) }
        var tab by remember { mutableStateOf(0) }

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Status") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Setup") })
            }
            if (tab == 0) StatusScreen(settings)
            else if (!unlocked) PinGate(settings) { unlocked = true }
            else SetupScreen(settings)
        }
    }

    // ---------- Screen 1: Status ----------
    @Composable
    fun StatusScreen(settings: Settings) {
        val dao = remember { AppDb.get(this).dao() }
        val startOfDay = remember {
            Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        }
        val pending by dao.pendingCount().collectAsState(0)
        val sentToday by dao.sentSince(startOfDay).collectAsState(0)
        val lastSent by dao.lastSentAt().collectAsState(null)
        val err by dao.lastError().collectAsState(null)
        val recent by dao.recent().collectAsState(emptyList())
        val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }

        Column(Modifier.padding(16.dp)) {
            Text(settings.lineName.ifBlank { "Line not named" }, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            val ok = pending == 0 && settings.isConfigured()
            Text(
                when {
                    !settings.isConfigured() -> "NOT SET UP - open Setup"
                    pending == 0 -> "All sent"
                    else -> "$pending waiting to send"
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text("Sent today: $sentToday")
            Text("Last sent: ${lastSent?.let { fmt.format(Date(it)) } ?: "never"}")
            if (pending > 0 && !err.isNullOrBlank()) Text("Last problem: $err", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { Sender.kick(this@MainActivity) }) { Text("Send waiting now") }
            Spacer(Modifier.height(12.dp))
            Text("Recent", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(recent) { r ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("${fmt.format(Date(r.receivedAt))}  ${if (r.status == 1) "sent" else "WAITING"}")
                        Text(r.body.take(90), style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    // ---------- PIN gate ----------
    @Composable
    fun PinGate(settings: Settings, onOk: () -> Unit) {
        var pin by remember { mutableStateOf("") }
        var bad by remember { mutableStateOf(false) }
        Column(Modifier.padding(16.dp)) {
            Text("Owner PIN")
            OutlinedTextField(pin, { pin = it; bad = false }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            if (bad) Text("Wrong PIN", color = MaterialTheme.colorScheme.error)
            Button(onClick = { if (pin == settings.ownerPin) onOk() else bad = true }) { Text("Unlock") }
        }
    }

    // ---------- Screen 2/3: Setup + permissions ----------
    @Composable
    fun SetupScreen(settings: Settings) {
        var line by remember { mutableStateOf(settings.lineName) }
        var gmail by remember { mutableStateOf(settings.gmail) }
        var pw by remember { mutableStateOf(settings.appPassword) }
        var to by remember { mutableStateOf(settings.toEmail) }
        var senders by remember { mutableStateOf(settings.senders) }
        var pin by remember { mutableStateOf(settings.ownerPin) }
        var saved by remember { mutableStateOf(false) }

        val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("1. Allow SMS", style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                perms.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.POST_NOTIFICATIONS))
            }) { Text("Grant SMS permission") }

            Text("2. Keep running in background", style = MaterialTheme.typography.titleMedium)
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            Button(onClick = {
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                }
            }) { Text("Turn off battery limits") }
            Text("Also on Samsung: Settings > Apps > EcoCash Forwarder > Battery > Unrestricted, and remove it from 'Sleeping apps'.", style = MaterialTheme.typography.bodySmall)

            Text("3. This line", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(line, { line = it }, label = { Text("Line name, e.g. Zengeza") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(gmail, { gmail = it }, label = { Text("This phone's Gmail address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pw, { pw = it }, label = { Text("Gmail app password (16 letters)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            TextButton(onClick = {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/apppasswords")))
            }) { Text("Tap here to create the app password (opens Google)") }
            Text("Sign in with THIS phone's Gmail. If Google says it is not available, turn on 2-Step Verification first.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(to, { to = it }, label = { Text("Send to") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(senders, { senders = it }, label = { Text("SMS senders to forward (comma separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pin, { pin = it }, label = { Text("Owner PIN (blank = none)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

            Button(onClick = {
                settings.lineName = line; settings.gmail = gmail; settings.appPassword = pw
                settings.toEmail = to; settings.senders = senders; settings.ownerPin = pin
                saved = true
                Sender.kick(this@MainActivity)
            }) { Text("Save") }
            if (saved) Text("Saved. Check the Status tab.")
        }
    }
}
