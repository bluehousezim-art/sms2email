package zw.ecocash.forwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Step 1 of safety: SAVE FIRST. Nothing is sent from here; the row is written, then the worker is asked to send. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        // Long messages arrive in several parts: join them per sender.
        val bySender = parts.groupBy { it.originatingAddress ?: "" }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allowed = Settings(context).senderList()
                val dao = AppDb.get(context).dao()
                for ((sender, msgs) in bySender) {
                    if (!Scrub.wanted(sender, allowed)) continue
                    dao.insert(Sms(
                        sender = sender,
                        body = msgs.joinToString("") { it.messageBody ?: "" },
                        receivedAt = msgs.first().timestampMillis
                    ))
                }
                Sender.kick(context)
            } finally { pending.finish() }
        }
    }
}

/** After a restart or app update: re-arm the schedules and send anything left over. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Sender.schedulePeriodic(context)
        Sender.kick(context)
    }
}
