package zw.ecocash.forwarder

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** One SMS. status: 0 = waiting to send, 1 = sent. The row is saved BEFORE any sending is tried. */
@Entity(
    tableName = "sms",
    indices = [Index(value = ["sender", "receivedAt", "body"], unique = true)]
)
data class Sms(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val status: Int = 0,
    val attempts: Int = 0,
    val lastError: String = "",
    val sentAt: Long = 0
)

@Dao
interface SmsDao {
    // IGNORE makes the unique index our duplicate guard (receiver + catch-up scan can see the same SMS).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(s: Sms): Long

    @Query("SELECT * FROM sms WHERE status = 0 ORDER BY receivedAt ASC")
    suspend fun pending(): List<Sms>

    @Query("SELECT COUNT(*) FROM sms WHERE status = 0")
    fun pendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms WHERE status = 1 AND sentAt >= :since")
    fun sentSince(since: Long): Flow<Int>

    @Query("SELECT MAX(sentAt) FROM sms WHERE status = 1")
    fun lastSentAt(): Flow<Long?>

    @Query("SELECT lastError FROM sms WHERE status = 0 AND lastError != '' ORDER BY receivedAt DESC LIMIT 1")
    fun lastError(): Flow<String?>

    @Query("SELECT * FROM sms ORDER BY receivedAt DESC LIMIT 30")
    fun recent(): Flow<List<Sms>>

    @Query("UPDATE sms SET status = 1, sentAt = :t, lastError = '' WHERE id = :id")
    suspend fun markSent(id: Long, t: Long)

    @Query("UPDATE sms SET attempts = attempts + 1, lastError = :err WHERE id = :id")
    suspend fun markFailed(id: Long, err: String)

    @Query("DELETE FROM sms WHERE status = 1 AND sentAt < :before")
    suspend fun purgeSentBefore(before: Long)
}

@Database(entities = [Sms::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): SmsDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "ecocash.db")
                .build().also { inst = it }
        }
    }
}
