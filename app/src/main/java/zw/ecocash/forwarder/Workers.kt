package zw.ecocash.forwarder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Sends everything still waiting. Retries with growing delay (1, 2, 4 ... 30 min) when there is no network. */
class SendWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val s = Settings(applicationContext)
        if (!s.isConfigured()) return Result.success()
        val dao = AppDb.get(applicationContext).dao()
        var failed = false
        for (sms in dao.pending()) {
            try {
                Mailer.send(s, sms)
                dao.markSent(sms.id, System.currentTimeMillis())
            } catch (e: Exception) {
                dao.markFailed(sms.id, (e.message ?: e.javaClass.simpleName).take(200))
                failed = true
                break // keep order; try again later
            }
        }
        dao.purgeSentBefore(System.currentTimeMillis() - 14L * 24 * 3600 * 1000)
        return if (failed) Result.retry() else Result.success()
    }
}

/** Every 15 minutes (and after boot): read the phone's own inbox and save anything the live receiver missed. */
class CatchUpWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return Result.success()
        val s = Settings(applicationContext)
        val dao = AppDb.get(applicationContext).dao()
        val allowed = s.senderList()
        val now = System.currentTimeMillis()
        // First ever scan: look back 24h only (do not flood the sheet with old history).
        val since = if (s.lastScan == 0L) now - 24L * 3600 * 1000 else s.lastScan - 10L * 60 * 1000
        applicationContext.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val addr = c.getString(0) ?: continue
                if (!Scrub.wanted(addr, allowed)) continue
                dao.insert(Sms(sender = addr, body = c.getString(1) ?: "", receivedAt = c.getLong(2)))
            }
        }
        s.lastScan = now
        Sender.kick(applicationContext)
        return Result.success()
    }
}

object Sender {
    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Run the send worker once, as soon as there is a network. Keeps trying until done. */
    fun kick(c: Context) {
        val req = OneTimeWorkRequestBuilder<SendWorker>()
            .setConstraints(net)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("send", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun schedulePeriodic(c: Context) {
        val req = PeriodicWorkRequestBuilder<CatchUpWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("catchup", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
