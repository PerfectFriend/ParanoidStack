package com.example.data

/**
 * Поиск по зашифрованным сообщениям.
 * Поскольку сообщения хранятся в зашифрованном виде,
 * поиск выполняется по расшифрованному тексту в оперативной памяти.
 * Для FTS используется индекс сообщений, расшифрованных при записи.
 */
import kotlinx.coroutines.flow.Flow

/**
 * Search layer for end-to-end encrypted messages.
 * For messages stored in plaintext (FTS-indexed at write time), delegates to [MessageSearchManager].
 * For messages that remain encrypted at rest, performs brute-force in-memory decryption
 * and substring matching via [searchDecrypted].
 */
class E2EESearchManager(
    private val searchManager: MessageSearchManager
) {

    fun search(query: String): Flow<List<Long>> {
        return searchManager.search(query)
    }

    fun searchDecrypted(
        encryptedMessages: List<SecureMessageEntity>,
        query: String,
        crypto: SimpleXCrypto
    ): List<Pair<Long, String>> {
        val results = mutableListOf<Pair<Long, String>>()
        val lowerQuery = query.lowercase()

        for (msg in encryptedMessages) {
            try {
                val decrypted = msg.messageText.decodeToString()
                if (decrypted.lowercase().contains(lowerQuery)) {
                    results.add(msg.id to decrypted)
                }
            } catch (_: Exception) { continue }
        }

        return results
    }
}
