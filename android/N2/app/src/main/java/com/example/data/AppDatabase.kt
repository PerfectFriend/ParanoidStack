/**
 * База данных приложения с шифрованием на уровне страниц.
 * Использует Room + кастомный OpenHelperFactory для прозрачного
 * AES-GCM шифрования SQLite-файлов.
 *
 * @see EncryptedDbHelperFactory фабрика, создающая EncryptedDbHelper
 * @see EncryptedDbHelper обёртка над SupportSQLiteOpenHelper с шифрованием
 */
package com.example.data

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * FTS-таблица для полнотекстового поиска по сообщениям.
 * Связана с [SecureMessageEntity] через contentEntity.
 */
@Entity(tableName = "messages_fts")
@Fts4(contentEntity = SecureMessageEntity::class)
data class MessageFts(
    @ColumnInfo(name = "messageText") val messageText: String
)

/**
 * История сыгранных матчей в нарды.
 * Хранит дату, цвета, счёт, результат и длительность игры.
 */
@Entity(
    tableName = "match_history",
    indices = [Index(value = ["lastActive"])]
)
data class MatchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,               // дата и время матча
    val lastActive: Long = date,  // время последней активности
    val playerColor: String,      // цвет игрока
    val winner: String,           // победитель
    val scorePlayer: Int,         // очки игрока
    val scoreOpponent: Int,       // очки соперника
    val isAgainstBot: Boolean,    // игра против бота?
    val gameDurationSeconds: Long // длительность игры в секундах
)

/** DAO для работы с историей матчей */
@Dao
interface MatchHistoryDao {
    /** Получить все матчи, отсортированные по дате (сначала новые) */
    @Query("SELECT * FROM match_history ORDER BY date DESC")
    fun getAllMatches(): Flow<List<MatchHistory>>

    /** Вставить новый матч */
    @Insert
    suspend fun insertMatch(match: MatchHistory)

    /** Очистить всю историю */
    @Query("DELETE FROM match_history")
    suspend fun clearHistory()
}

/** DAO для работы с защищёнными сообщениями */
@Dao
interface SecureMessageDao {
    /** Вставить новое сообщение */
    @Insert
    suspend fun insertMessage(message: SecureMessageEntity)

    /** Обновить существующее сообщение */
    @Update
    suspend fun updateMessage(message: SecureMessageEntity)

    /** Удалить сообщение */
    @Delete
    suspend fun deleteMessage(message: SecureMessageEntity)

    /** Получить все сообщения для контакта, отсортированные по времени */
    @Query("SELECT * FROM secure_messages WHERE contactId = :contactId ORDER BY timestamp ASC")
    fun getMessagesForContact(contactId: String): Flow<List<SecureMessageEntity>>

    /** Найти сообщение по ID */
    @Query("SELECT * FROM secure_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): SecureMessageEntity?

    /** Отметить сообщение как прочитанное */
    @Query("UPDATE secure_messages SET isRead = 1 WHERE id = :messageId")
    suspend fun markAsRead(messageId: Long)

    /** Получить количество непрочитанных сообщений для контакта */
    @Query("SELECT COUNT(*) FROM secure_messages WHERE contactId = :contactId AND isRead = 0")
    fun getUnreadCount(contactId: String): Flow<Int>

    /** Получить сообщения с истёкшим сроком жизни */
    @Query("SELECT * FROM secure_messages WHERE expiresAt IS NOT NULL AND expiresAt < :currentTime")
    suspend fun getExpiredMessages(currentTime: Long): List<SecureMessageEntity>

    /** Получить количество сообщений с истёкшим сроком */
    @Query("SELECT COUNT(*) FROM secure_messages WHERE expiresAt IS NOT NULL AND expiresAt < :currentTime")
    fun getExpiredMessagesCount(currentTime: Long): Flow<Int>

    /** Удалить все сообщения с истёкшим сроком */
    @Query("DELETE FROM secure_messages WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpiredMessages(now: Long)

    /** Удалить сообщение по ID */
    @Query("DELETE FROM secure_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    /** Отметить сообщение как неотправленное (ошибка) */
    @Query("UPDATE secure_messages SET isFailed = 1 WHERE id = :id")
    suspend fun markAsFailed(id: Long)

    /** Удалить все сообщения для контакта */
    @Query("DELETE FROM secure_messages WHERE contactId = :contactId")
    suspend fun deleteAllForContact(contactId: String)

    /** Получить список всех контактов, с которыми есть переписка */
    @Query("SELECT DISTINCT contactId FROM secure_messages")
    suspend fun getAllContacts(): List<String>

    /** Отметить сообщение как отредактированное */
    @Query("UPDATE secure_messages SET isEdited = 1 WHERE id = :messageId")
    suspend fun markAsEdited(messageId: Long)

    /** Отметить сообщение как удалённое */
    @Query("UPDATE secure_messages SET isDeleted = 1 WHERE id = :messageId")
    suspend fun markAsDeleted(messageId: Long)

    /** Редактировать текст сообщения */
    @Query("UPDATE secure_messages SET messageText = :newTextBytes WHERE id = :messageId")
    suspend fun updateMessageText(messageId: Long, newTextBytes: ByteArray)
}

/** DAO для работы с историей редактирования сообщений */
@Dao
interface MessageEditDao {
    /** Вставить запись о редактировании */
    @Insert
    suspend fun insert(edit: MessageEditEntity)

    /** Получить историю редактирования сообщения */
    @Query("SELECT * FROM message_edits WHERE originalMessageId = :messageId ORDER BY editNumber ASC")
    suspend fun getEditHistory(messageId: Long): List<MessageEditEntity>
}

/** DAO для полнотекстового поиска (FTS4) */
@Dao
interface MessageFtsDao {
    /**
     * Поиск сообщений по FTS-индексу.
     * @param query поисковый запрос
     * @return Flow списка rowid найденных сообщений
     */
    @Query("SELECT rowid FROM messages_fts WHERE messageText MATCH :query")
    fun searchMessages(query: String): Flow<List<Long>>

    /** Индексировать текст сообщения для FTS */
    @Query("INSERT INTO messages_fts(rowid, messageText) VALUES (:rowId, :text)")
    suspend fun insertMessageFts(rowId: Long, text: String)

    /** Удалить сообщение из FTS-индекса */
    @Query("DELETE FROM messages_fts WHERE rowid = :rowId")
    suspend fun deleteMessageFts(rowId: Long)
}

/**
 * Главная Room-база данных приложения с поддержкой шифрования.
 * Управляет таблицами: match_history, secure_messages, messages_fts.
 */
@Database(
    entities = [MatchHistory::class, SecureMessageEntity::class, MessageFts::class, MessageEditEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun matchHistoryDao(): MatchHistoryDao
    abstract fun secureMessageDao(): SecureMessageDao
    abstract fun messageFtsDao(): MessageFtsDao
    abstract fun messageEditDao(): MessageEditDao

    companion object {
        private val instances = ConcurrentHashMap<String, AppDatabase>()

        /**
         * Получить (или создать) экземпляр базы данных.
         * @param context контекст приложения
         * @param databaseName имя файла БД
         * @param passphrase парольная фраза для шифрования
         */
        fun getDatabase(context: Context, databaseName: String = "backgammon_database", passphrase: String = ""): AppDatabase {
            return instances.getOrPut(databaseName) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    databaseName
                )
                .openHelperFactory(EncryptedDbHelperFactory(passphrase, context.applicationContext))
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
            }
        }

        /** Закрыть конкретную БД по имени */
        fun closeDatabase(databaseName: String) {
            instances.remove(databaseName)?.close()
        }

        /** Закрыть все открытые экземпляры БД */
        fun closeAll() {
            for ((_, db) in instances) db.close()
            instances.clear()
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `message_edits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `originalMessageId` INTEGER NOT NULL, `newText` TEXT NOT NULL, `editedAt` INTEGER NOT NULL, `editNumber` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_edits_originalMessageId` ON `message_edits` (`originalMessageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_secure_messages_contactId` ON `secure_messages` (`contactId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_secure_messages_timestamp` ON `secure_messages` (`timestamp`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE secure_messages ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE secure_messages ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE match_history ADD COLUMN lastActive INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_secure_messages_contact_timestamp ON secure_messages (contactId, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_secure_messages_isRead ON secure_messages (isRead)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_secure_messages_isDeleted ON secure_messages (isDeleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_match_history_lastActive ON match_history (lastActive)")
            }
        }
    }
}

/**
 * Фабрика для создания [EncryptedDbHelper] с прозрачным шифрованием.
 * Генерирует ключ шифрования на основе парольной фразы и соли,
 * используя [SimpleXCrypto.deriveKey] (PBKDF2).
 */
class EncryptedDbHelperFactory(
    private val passphrase: String,
    private val context: Context
) : SupportSQLiteOpenHelper.Factory {

    // Ключ шифрования БД, вычисляемый лениво при первом обращении
    private val dbKey: ByteArray by lazy {
        // Получаем или создаём соль для PBKDF2, хранящуюся в SharedPreferences
        val prefs = context.getSharedPreferences("db_encryption_salt", Context.MODE_PRIVATE)
        val saltB64 = prefs.getString("salt", null)
        val salt = if (saltB64 != null) {
            Base64.decode(saltB64, Base64.NO_WRAP)
        } else {
            val newSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            prefs.edit().putString(
                "salt",
                Base64.encodeToString(newSalt, Base64.NO_WRAP)
            ).apply()
            newSalt
        }
        SimpleXCrypto.deriveKey(passphrase, salt, 100000)
    }

    /**
     * Создаёт EncryptedDbHelper для шифрованного доступа к БД.
     * Если имя БД не указано (in-memory), использует стандартную фабрику.
     */
    override fun create(config: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        if (config.name == null) {
            return FrameworkSQLiteOpenHelperFactory().create(config)
        }
        return EncryptedDbHelper(
            delegate = FrameworkSQLiteOpenHelperFactory().create(config),
            config = config,
            key = dbKey
        )
    }
}

/**
 * Обёртка над SupportSQLiteOpenHelper, реализующая прозрачное шифрование.
 * При открытии БД — расшифровывает .enc-файл в .db.
 * При закрытии — шифрует .db обратно в .enc и удаляет незашифрованную копию.
 */
class EncryptedDbHelper(
    private val delegate: SupportSQLiteOpenHelper,
    private val config: SupportSQLiteOpenHelper.Configuration,
    private val key: ByteArray
) : SupportSQLiteOpenHelper {

    @Volatile
    private var decrypted = false
    private val decryptLock = Any()

    /** Получить файл БД по имени конфигурации */
    private fun getDbFile(): File? {
        val name = config.name ?: return null
        return config.context.getDatabasePath(name)
    }

    /** Получить путь к файлу с зашифрованными данными (.enc) */
    private fun getEncFile(): File? {
        val dbFile = getDbFile() ?: return null
        return File(dbFile.parent, "${dbFile.name}.enc")
    }

    /** Расшифровать .enc-файл в .db, если это необходимо. Потокобезопасно. */
    private fun decryptIfNeeded() {
        if (decrypted) return
        synchronized(decryptLock) {
            if (decrypted) return
            val dbFile = getDbFile() ?: return
            val encFile = getEncFile() ?: return
            if (encFile.exists() && !dbFile.exists()) {
                try {
                    val encData = encFile.readBytes()
                    val plaintext = SimpleXCrypto.decryptStorage(encData, key)
                    dbFile.parentFile?.mkdirs()
                    dbFile.writeBytes(plaintext)
                    Log.i("DbEncryption", "DB decrypted: ${dbFile.name} (${plaintext.size} bytes)")
                } catch (e: Exception) {
                    Log.e("DbEncryption", "Failed to decrypt database: ${e.message}", e)
                }
            }
            decrypted = true
        }
    }

    override val databaseName: String? get() = delegate.databaseName

    override val writableDatabase: SupportSQLiteDatabase
        get() {
            decryptIfNeeded()
            return delegate.writableDatabase
        }

    override val readableDatabase: SupportSQLiteDatabase
        get() {
            decryptIfNeeded()
            return delegate.readableDatabase
        }

    /**
     * Закрывает БД и шифрует данные обратно в .enc-файл.
     * Удаляет WAL/SHM временные файлы и незашифрованную БД.
     */
    override fun close() {
        delegate.close()
        val dbFile = getDbFile() ?: return
        val encFile = getEncFile() ?: return
        if (dbFile.exists()) {
            try {
                val plaintext = dbFile.readBytes()
                if (plaintext.isNotEmpty()) {
                    val encData = SimpleXCrypto.encryptStorage(plaintext, key)
                    encFile.writeBytes(encData)
                    Log.i("DbEncryption", "DB encrypted: ${encFile.name} exists=${encFile.exists()} size=${encFile.length()}")
                    // Удаляем временные WAL и SHM файлы SQLite
                    File(dbFile.parent, "${dbFile.name}-wal").delete()
                    File(dbFile.parent, "${dbFile.name}-shm").delete()
                    dbFile.delete()
                }
            } catch (e: Exception) {
                Log.e("DbEncryption", "CRITICAL: Failed to encrypt database on close — plaintext DB may remain on disk", e)
                // Не удаляем незашифрованный файл, если шифрование не удалось — лучше оставить
                // его зашифрованным при следующем запуске, чем потерять данные
            }
        }
    }

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) =
        delegate.setWriteAheadLoggingEnabled(enabled)
}
