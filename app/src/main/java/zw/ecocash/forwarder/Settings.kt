package zw.ecocash.forwarder

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Everything the owner types in Setup. The app password is stored encrypted. */
class Settings(c: Context) {
    private val p: SharedPreferences = EncryptedSharedPreferences.create(
        c.applicationContext, "settings",
        MasterKey.Builder(c.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var lineName: String get() = p.getString("line", "") ?: ""; set(v) = p.edit().putString("line", v).apply()
    /** This phone's own Gmail address (the sender that identifies the line). */
    var gmail: String get() = p.getString("gmail", "") ?: ""; set(v) = p.edit().putString("gmail", v.trim()).apply()
    /** Gmail app password created in THAT Gmail account. */
    var appPassword: String get() = p.getString("pw", "") ?: ""; set(v) = p.edit().putString("pw", v.replace(" ", "")).apply()
    var toEmail: String get() = p.getString("to", "bluehousezim@gmail.com") ?: ""; set(v) = p.edit().putString("to", v.trim()).apply()
    var ownerPin: String get() = p.getString("pin", "") ?: ""; set(v) = p.edit().putString("pin", v).apply()
    /** Comma separated sender names to forward. */
    var senders: String get() = p.getString("senders", DEFAULT_SENDERS) ?: DEFAULT_SENDERS; set(v) = p.edit().putString("senders", v).apply()
    var lastScan: Long get() = p.getLong("lastScan", 0); set(v) = p.edit().putLong("lastScan", v).apply()

    fun isConfigured() = gmail.isNotBlank() && appPassword.isNotBlank() && toEmail.isNotBlank()
    fun senderList(): List<String> = senders.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    companion object { const val DEFAULT_SENDERS = "+263164,+263164USD,EcoCash" }
}
