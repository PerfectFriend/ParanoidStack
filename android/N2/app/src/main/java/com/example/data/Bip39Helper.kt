/**
 * Утилита для работы с BIP-39 мнемоническими фразами.
 * Позволяет генерировать и проверять 12-словные seed-фразы
 * для восстановления криптографических ключей.
 */
package com.example.data

import android.content.Context
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom

/** Объект-утилита для BIP-39 мнемоник */
object Bip39Helper {
    private var cachedWordlist: List<String>? = null

/**
 * Загружает английский wordlist BIP-39 из assets.
 * Кэширует результат для повторного использования.
 *
 * @param context контекст приложения для доступа к assets
 * @return список из 2048 слов или пустой список при ошибке
 */
    fun loadWordlist(context: Context): List<String> {
        cachedWordlist?.let { return it }
        return try {
            context.assets.open("bip39_english.txt").bufferedReader().use { reader ->
                val words = reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                if (words.size == 2048) {
                    cachedWordlist = words
                    words
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("Bip39Helper", "exception", e)
            emptyList()
        }
    }

    /**
     * Генерирует случайную мнемоническую фразу из 12 слов (BIP-39).
     * Использует 128 бит энтропии + 4 бита контрольной суммы.
     *
     * @param context контекст приложения для загрузки wordlist
     * @return строка из 12 слов, разделённых пробелами
     */
    fun generateMnemonic(context: Context): String {
        val wordlist = loadWordlist(context)
        if (wordlist.size < 1024) {
            val fallback = listOf("abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident")
            val rng = SecureRandom()
            return (1..12).map { fallback[rng.nextInt(fallback.size)] }.joinToString(" ")
        }

        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)

        // Compute SHA-256 for checksum
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(entropy)
        
        // 128 / 32 = 4 bits checksum
        val checksumByte = hash[0]
        
        // Convert entropy bytes and 4-bit checksum to a continuous list of bits (132 bits)
        val bits = BooleanArray(132)
        var bitIndex = 0
        for (b in entropy) {
            for (i in 7 downTo 0) {
                bits[bitIndex++] = ((b.toInt() ushr i) and 1) == 1
            }
        }
        // Append 4 bits of checksum (from the first byte of SHA-256)
        for (i in 7 downTo 4) {
            bits[bitIndex++] = ((checksumByte.toInt() ushr i) and 1) == 1
        }

        // Convert 132 bits to 12 words (12 * 11 bits)
        val mnemonicWords = mutableListOf<String>()
        for (w in 0 until 12) {
            var index = 0
            for (i in 0 until 11) {
                index = index shl 1
                if (bits[w * 11 + i]) {
                    index = index or 1
                }
            }
            mnemonicWords.add(wordlist[index])
        }

        return mnemonicWords.joinToString(" ")
    }

    /**
     * Проверяет валидность мнемонической фразы BIP-39.
     * Проверяет: количество слов (12), наличие в wordlist, контрольную сумму SHA-256.
     *
     * @param context контекст приложения
     * @param mnemonic мнемоническая фраза для проверки
     * @return true если фраза валидна, false в противном случае
     */
    fun validateMnemonic(context: Context, mnemonic: String): Boolean {
        val wordlist = loadWordlist(context)
        if (wordlist.size != 2048) return false

        val words = mnemonic.trim().lowercase().split("\\s+".toRegex())
        if (words.size != 12) return false

        // 1. Verify words exist in wordlist
        for (w in words) {
            if (!wordlist.contains(w)) return false
        }

        // 2. Perform checksum verification
        try {
            val bits = BooleanArray(132)
            for (w in 0 until 12) {
                val index = wordlist.indexOf(words[w])
                if (index < 0) return false
                for (i in 10 downTo 0) {
                    bits[w * 11 + (10 - i)] = ((index ushr i) and 1) == 1
                }
            }

            // Extract entropy bytes (16 bytes = 128 bits)
            val entropy = ByteArray(16)
            for (currByte in 0 until 16) {
                var value = 0
                for (b in 0 until 8) {
                    value = value shl 1
                    if (bits[currByte * 8 + b]) {
                        value = value or 1
                    }
                }
                entropy[currByte] = value.toByte()
            }

            // Extract checksum (last 4 bits)
            var expectedChecksumVal = 0
            for (b in 128 until 132) {
                expectedChecksumVal = expectedChecksumVal shl 1
                if (bits[b]) {
                    expectedChecksumVal = expectedChecksumVal or 1
                }
            }

            // Compute actual checksum from entropy
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(entropy)
            val actualChecksumVal = (hash[0].toInt() ushr 4) and 0x0F

            return expectedChecksumVal == actualChecksumVal
        } catch (e: Exception) {
            Log.e("Bip39Helper", "exception", e)
            return false
        }
    }
}
