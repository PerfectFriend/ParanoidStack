/**
 * Реализация криптографических функций NaCl (Networking and Cryptography library).
 * Включает: X25519 DH, Salsa20/HSalsa20, Poly1305 MAC, crypto_box.
 *
 * Используется для:
 * - Обмена ключами (X25519 ECDH)
 * - Шифрования с аутентификацией (crypto_box = XSalsa20 + Poly1305)
 * - Вывода ключей (HSalsa20-based KDF)
 *
 * Реализация следует спецификациям NaCl/TweetNaCl.
 */
package com.example.data

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/** Объект-синглтон с криптографическими примитивами NaCl */
object NaClCrypto {
    private const val TAG = "NaClCrypto"

    // Константа SIGMA для Salsa20: "expand 32-byte k"
    private val SIGMA = intArrayOf(0x61707865, 0x3320646e, 0x79622d32, 0x6b206574)

    /** Сгенерировать X25519 ключевую пару */
    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("XDH")
        kpg.initialize(NamedParameterSpec("X25519"))
        return kpg.generateKeyPair()
    }

    /** Преобразовать публичный ключ в X509EncodedKeySpec */
    fun toX509Public(pk: ByteArray): java.security.PublicKey {
        return KeyFactory.getInstance("XDH").generatePublic(X509EncodedKeySpec(pk))
    }

    /** Преобразовать приватный ключ в PKCS8EncodedKeySpec */
    fun toPKCS8Private(sk: ByteArray): java.security.PrivateKey {
        return KeyFactory.getInstance("XDH").generatePrivate(PKCS8EncodedKeySpec(sk))
    }

    /** Выполнить X25519 Diffie-Hellman между приватным и публичным ключом */
    fun dh(privateKey: java.security.PrivateKey, publicKey: java.security.PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("XDH")
        ka.init(privateKey)
        ka.doPhase(publicKey, true)
        return ka.generateSecret()
    }

    /** DH с сырыми байтовыми ключами (автоматически парсит их) */
    fun dhRaw(sk: ByteArray, pk: ByteArray): ByteArray {
        return dh(toPKCS8Private(sk), toX509Public(pk))
    }

    /** Преобразовать массив байт в массив 32-битных целых (little-endian) */
    private fun toIntsLE(bs: ByteArray): IntArray {
        val out = IntArray(bs.size / 4)
        for (i in out.indices) {
            out[i] = bs[i * 4].toInt() and 0xFF or
                    (bs[i * 4 + 1].toInt() and 0xFF shl 8) or
                    (bs[i * 4 + 2].toInt() and 0xFF shl 16) or
                    (bs[i * 4 + 3].toInt() and 0xFF shl 24)
        }
        return out
    }

    /** Преобразовать массив 32-битных целых обратно в байты (little-endian) */
    private fun fromIntsLE(xs: IntArray): ByteArray {
        val out = ByteArray(xs.size * 4)
        for (i in xs.indices) {
            out[i * 4] = (xs[i] and 0xFF).toByte()
            out[i * 4 + 1] = (xs[i] shr 8 and 0xFF).toByte()
            out[i * 4 + 2] = (xs[i] shr 16 and 0xFF).toByte()
            out[i * 4 + 3] = (xs[i] shr 24 and 0xFF).toByte()
        }
        return out
    }

    /**
     * Ядро Salsa20: преобразует 16 слов состояния.
     * Реализует четверть-раундовые операции Salsa20 (column rounds + diagonal rounds).
     *
     * The state is a 4x4 matrix of 32-bit words arranged as:
     *   [c0,  k0,  k1,  k2]
     *   [k3,  c1,  n0,  n1]
     *   [n2,  n3,  c2,  k4]
     *   [k5,  k6,  k7,  c3]
     * where c = constants (SIGMA), k = key, n = nonce + counter.
     *
     * Each double round performs 8 column quarter-rounds followed by 8 diagonal quarter-rounds.
     * A quarter-round (a, b, c, d) does:
     *   b ^= (a + d) <<< 7;  c ^= (b + a) <<< 9;  d ^= (c + b) <<< 13;  a ^= (d + c) <<< 18
     *
     * After all rounds, the original state is added back (mod 2^32) to produce the output.
     */
    private fun salsaCore(out: IntArray, k: IntArray, n: IntArray, c: IntArray, rounds: Int = 20) {
        // Инициализация состояния: константы, ключ, nonce, счётчик
        var x0 = c[0]; var x1 = k[0]; var x2 = k[1]; var x3 = k[2]
        var x4 = k[3]; var x5 = c[1]; var x6 = n[0]; var x7 = n[1]
        var x8 = n[2]; var x9 = n[3]; var x10 = c[2]; var x11 = k[4]
        var x12 = k[5]; var x13 = k[6]; var x14 = k[7]; var x15 = c[3]

        // Сохраняем начальное состояние для финального сложения
        val j0 = x0; val j1 = x1; val j2 = x2; val j3 = x3
        val j4 = x4; val j5 = x5; val j6 = x6; val j7 = x7
        val j8 = x8; val j9 = x9; val j10 = x10; val j11 = x11
        val j12 = x12; val j13 = x13; val j14 = x14; val j15 = x15

        repeat(rounds / 2) {
            // 10 double rounds — each double round = 2 rounds (column + diagonal)
            var t: Int
            // Column round: QR on each column of the 4x4 state matrix
            // QR(x0, x4, x8,  x12)
            t = (x0 + x12); x4 = x4 xor (t shl 7 or (t ushr 25)); t = (x4 + x0); x8 = x8 xor (t shl 9 or (t ushr 23))
            t = (x8 + x4); x12 = x12 xor (t shl 13 or (t ushr 19)); t = (x12 + x8); x0 = x0 xor (t shl 18 or (t ushr 14))
            // QR(x5, x9, x13, x1)
            t = (x5 + x1); x9 = x9 xor (t shl 7 or (t ushr 25)); t = (x9 + x5); x13 = x13 xor (t shl 9 or (t ushr 23))
            t = (x13 + x9); x1 = x1 xor (t shl 13 or (t ushr 19)); t = (x1 + x13); x5 = x5 xor (t shl 18 or (t ushr 14))
            // QR(x10, x14, x2, x6)
            t = (x10 + x6); x14 = x14 xor (t shl 7 or (t ushr 25)); t = (x14 + x10); x2 = x2 xor (t shl 9 or (t ushr 23))
            t = (x2 + x14); x6 = x6 xor (t shl 13 or (t ushr 19)); t = (x6 + x2); x10 = x10 xor (t shl 18 or (t ushr 14))
            // QR(x15, x3, x7, x11)
            t = (x15 + x11); x3 = x3 xor (t shl 7 or (t ushr 25)); t = (x3 + x15); x7 = x7 xor (t shl 9 or (t ushr 23))
            t = (x7 + x3); x11 = x11 xor (t shl 13 or (t ushr 19)); t = (x11 + x7); x15 = x15 xor (t shl 18 or (t ushr 14))
            // Diagonal round: QR on each diagonal of the 4x4 state matrix
            // QR(x0, x5, x10, x15)
            t = (x0 + x15); x5 = x5 xor (t shl 7 or (t ushr 25)); t = (x5 + x0); x10 = x10 xor (t shl 9 or (t ushr 23))
            t = (x10 + x5); x15 = x15 xor (t shl 13 or (t ushr 19)); t = (x15 + x10); x0 = x0 xor (t shl 18 or (t ushr 14))
            // QR(x1, x6, x11, x12)
            t = (x1 + x12); x6 = x6 xor (t shl 7 or (t ushr 25)); t = (x6 + x1); x11 = x11 xor (t shl 9 or (t ushr 23))
            t = (x11 + x6); x12 = x12 xor (t shl 13 or (t ushr 19)); t = (x12 + x11); x1 = x1 xor (t shl 18 or (t ushr 14))
            // QR(x2, x7, x8, x13)
            t = (x2 + x13); x7 = x7 xor (t shl 7 or (t ushr 25)); t = (x7 + x2); x8 = x8 xor (t shl 9 or (t ushr 23))
            t = (x8 + x7); x13 = x13 xor (t shl 13 or (t ushr 19)); t = (x13 + x8); x2 = x2 xor (t shl 18 or (t ushr 14))
            // QR(x3, x4, x9, x14)
            t = (x3 + x14); x4 = x4 xor (t shl 7 or (t ushr 25)); t = (x4 + x3); x9 = x9 xor (t shl 9 or (t ushr 23))
            t = (x9 + x4); x14 = x14 xor (t shl 13 or (t ushr 19)); t = (x14 + x9); x3 = x3 xor (t shl 18 or (t ushr 14))
        }

        // Финальное сложение с сохранённым состоянием
        out[0] = x0 + j0; out[1] = x1 + j1; out[2] = x2 + j2; out[3] = x3 + j3
        out[4] = x4 + j4; out[5] = x5 + j5; out[6] = x6 + j6; out[7] = x7 + j7
        out[8] = x8 + j8; out[9] = x9 + j9; out[10] = x10 + j10; out[11] = x11 + j11
        out[12] = x12 + j12; out[13] = x13 + j13; out[14] = x14 + j14; out[15] = x15 + j15
    }

    /**
     * HSalsa20: функция сжатия для вывода ключей.
     * Применяется в KDF Double Ratchet и в crypto_box.
     *
     * @param key 32-байтный ключ
     * @param input 16-байтный вход
     * @return 32 байта вывода
     */
    fun hsalsa20(key: ByteArray, input: ByteArray): ByteArray {
        val k = toIntsLE(key)
        val i = toIntsLE(input.copyOf(16))
        val out = IntArray(16)
        salsaCore(out, k, i, SIGMA, 20)
        // Извлекаем указанные 8 слов в качестве вывода
        return fromIntsLE(intArrayOf(out[0], out[5], out[10], out[15], out[6], out[7], out[8], out[9]))
    }

    /** Salsa20 с XOR и счётчиком (для шифрования потока) */
    private fun salsa20XorIc(out: ByteArray, inp: ByteArray, key: ByteArray, nonce: ByteArray, ic: Long) {
        val k = toIntsLE(key)
        val n = IntArray(4)
        n[0] = toIntsLE(nonce.copyOf(8))[0]
        n[1] = toIntsLE(nonce.copyOf(8))[1]
        n[2] = (ic and 0xFFFFFFFFL).toInt()
        n[3] = (ic ushr 32).toInt()

        val block = IntArray(16)
        var i = 0
        var counter = ic
        while (i < inp.size) {
            n[2] = (counter and 0xFFFFFFFFL).toInt()
            n[3] = (counter ushr 32).toInt()
            salsaCore(block, k, n, SIGMA, 20)
            val blockB = fromIntsLE(block)
            val remaining = minOf(64, inp.size - i)
            for (j in 0 until remaining) {
                out[i + j] = (inp[i + j].toInt() xor blockB[j].toInt()).toByte()
            }
            i += 64
            counter++
        }
    }

    /**
     * Poly1305 MAC (Message Authentication Code).
     * Используется для аутентификации в crypto_box.
     *
     * Poly1305 interprets the message as a series of 16-byte blocks, each treated as
     * a 130-bit integer. It computes a polynomial evaluation over GF(2^130 - 5):
     *   h = (h + block) * r  (mod 2^130 - 5)
     * where r is the clamped first 16 bytes of the key. The final result is added
     * to the second 16 bytes of the key (s) to produce a 16-byte authentication tag.
     */
    private fun poly1305Mac(msg: ByteArray, key: ByteArray): ByteArray {
        // Clamp r: clear top 4 bits of each 32-bit word and ensure certain bits are zero
        // (r[3], r[7], r[11], r[15] are masked to 0x0F and r[4], r[8] to 0xFC)
        // Convert to Long first to prevent sign extension from interfering with bitwise operations
        val r0 = ((key[0].toLong() and 0xFF) or ((key[1].toLong() and 0xFF) shl 8) or ((key[2].toLong() and 0xFF) shl 16) or ((key[3].toLong() and 0xFF) shl 24)) and 0x0FFFFFFFL
        val r1 = ((key[4].toLong() and 0xFF) or ((key[5].toLong() and 0xFF) shl 8) or ((key[6].toLong() and 0xFF) shl 16) or ((key[7].toLong() and 0xFF) shl 24)) and 0x0FFFFFFCL
        val r2 = ((key[8].toLong() and 0xFF) or ((key[9].toLong() and 0xFF) shl 8) or ((key[10].toLong() and 0xFF) shl 16) or ((key[11].toLong() and 0xFF) shl 24)) and 0x0FFFFFFCL
        val r3 = ((key[12].toLong() and 0xFF) or ((key[13].toLong() and 0xFF) shl 8) or ((key[14].toLong() and 0xFF) shl 16) or ((key[15].toLong() and 0xFF) shl 24)) and 0x0FFFFFFCL

        var h0 = 0L; var h1 = 0L; var h2 = 0L; var h3 = 0L
        var h4 = 0L

        /** Финальная обработка с приведением по модулю просто 2^130-5 */
        fun finish() {
            h2 = h2 and 0xFFFFFFFFL; h3 = h3 and 0xFFFFFFFFL
            var c = h4; c = c shl 2; h0 += c; c = h0 ushr 32; h0 = h0 and 0xFFFFFFFFL
            h1 += c; c = h1 ushr 32; h1 = h1 and 0xFFFFFFFFL
            h2 += c; c = h2 ushr 32; h2 = h2 and 0xFFFFFFFFL
            h3 += c; h3 = h3 and 0xFFFFFFFFL

            var mask = if ((h3 ushr 31) != 0L) 0xFFFFFFFFL else 0L
            c = h0 and mask; h0 -= c; c = h0 ushr 32; h0 = h0 and 0xFFFFFFFFL
            h1 -= c; c = h1 ushr 32; h1 = h1 and 0xFFFFFFFFL; h2 -= c; c = h2 ushr 32; h2 = h2 and 0xFFFFFFFFL
            h3 -= c; h3 = h3 and 0xFFFFFFFFL
            mask = mask.inv()
            c = (h0 and mask) + (128 shl 24).toLong(); h0 = h0 and 0xFFFFFFFFL xor (c and 0xFFFFFFFFL)
            c = c ushr 32; h1 += c; c = c ushr 32; h2 += c; c = c ushr 32; h3 += c
        }

        // Обработка сообщения по 16-байтным блокам
        for (i in msg.indices step 16) {
            val block = ByteArray(16)
            val len = minOf(16, msg.size - i)
            System.arraycopy(msg, i, block, 0, len)
            if (len < 16) block[len] = 1.toByte()
            val f0 = block[0].toInt() and 0xFF or ((block[1].toInt() and 0xFF) shl 8) or ((block[2].toInt() and 0xFF) shl 16) or ((block[3].toInt() and 0xFF) shl 24)
            val f1 = block[4].toInt() and 0xFF or ((block[5].toInt() and 0xFF) shl 8) or ((block[6].toInt() and 0xFF) shl 16) or ((block[7].toInt() and 0xFF) shl 24)
            val f2 = block[8].toInt() and 0xFF or ((block[9].toInt() and 0xFF) shl 8) or ((block[10].toInt() and 0xFF) shl 16) or ((block[11].toInt() and 0xFF) shl 24)
            val f3 = block[12].toInt() and 0xFF or ((block[13].toInt() and 0xFF) shl 8) or ((block[14].toInt() and 0xFF) shl 16) or ((block[15].toInt() and 0xFF) shl 24)

            h0 += f0.toLong(); h1 += f1.toLong(); h2 += f2.toLong(); h3 += f3.toLong()

            // Умножение в GF(2^130-5)
            var t0 = (h0 * r0.toLong()) and 0xFFFFFFFFL; var t1 = (h1 * r0.toLong() + h0 * r1.toLong()) and 0xFFFFFFFFL
            var t2 = (h2 * r0.toLong() + h1 * r1.toLong() + h0 * r2.toLong()) and 0xFFFFFFFFL
            var t3 = (h3 * r0.toLong() + h2 * r1.toLong() + h1 * r2.toLong() + h0 * r3.toLong()) and 0xFFFFFFFFL
            var t4 = (h3 * r1.toLong() + h2 * r2.toLong() + h1 * r3.toLong()) and 0xFFFFFFFFL

            var c = t0 ushr 26; t0 = t0 and 0x3FFFFFFL; t1 += c
            c = t1 ushr 26; t1 = t1 and 0x3FFFFFFL; t2 += c
            c = t2 ushr 26; t2 = t2 and 0x3FFFFFFL; t3 += c
            c = t3 ushr 26; t3 = t3 and 0x3FFFFFFL; t4 += c
            c = t4 ushr 26; t4 = t4 and 0x3FFFFFFL; t0 += c * 5L

            h0 = t0 and 0xFFFFFFFFL; h1 = t1 and 0xFFFFFFFFL; h2 = t2 and 0xFFFFFFFFL; h3 = t3 and 0xFFFFFFFFL; h4 = t4 and 0xFFFFFFFFL
        }

        finish()

        // Добавляем s (вторую половину ключа)
        val s0 = key[16].toInt() and 0xFF or ((key[17].toInt() and 0xFF) shl 8) or ((key[18].toInt() and 0xFF) shl 16) or ((key[19].toInt() and 0xFF) shl 24)
        val s1 = key[20].toInt() and 0xFF or ((key[21].toInt() and 0xFF) shl 8) or ((key[22].toInt() and 0xFF) shl 16) or ((key[23].toInt() and 0xFF) shl 24)
        val s2 = key[24].toInt() and 0xFF or ((key[25].toInt() and 0xFF) shl 8) or ((key[26].toInt() and 0xFF) shl 16) or ((key[27].toInt() and 0xFF) shl 24)
        val s3 = key[28].toInt() and 0xFF or ((key[29].toInt() and 0xFF) shl 8) or ((key[30].toInt() and 0xFF) shl 16) or ((key[31].toInt() and 0xFF) shl 24)

        h0 += s0.toLong(); h1 += s1.toLong(); h2 += s2.toLong(); h3 += s3.toLong()

        return byteArrayOf(
            (h0 and 0xFF).toByte(), ((h0 shr 8) and 0xFF).toByte(), ((h0 shr 16) and 0xFF).toByte(), ((h0 shr 24) and 0xFF).toByte(),
            (h1 and 0xFF).toByte(), ((h1 shr 8) and 0xFF).toByte(), ((h1 shr 16) and 0xFF).toByte(), ((h1 shr 24) and 0xFF).toByte(),
            (h2 and 0xFF).toByte(), ((h2 shr 8) and 0xFF).toByte(), ((h2 shr 16) and 0xFF).toByte(), ((h2 shr 24) and 0xFF).toByte(),
            (h3 and 0xFF).toByte(), ((h3 shr 8) and 0xFF).toByte(), ((h3 shr 16) and 0xFF).toByte(), ((h3 shr 24) and 0xFF).toByte()
        )
    }

    private val zeros16 = ByteArray(16)

    /** 24-byte zero nonce for crypto_box when key already provides uniqueness (e.g. Double Ratchet). */
    val NONCE_24: ByteArray = ByteArray(24)

    /** Вычислить общий ключ (subKey) для crypto_box (HSalsa20 на общем секрете) */
    fun cryptoBoxBeforeNm(theirPk: ByteArray, mySk: ByteArray): ByteArray {
        val sharedSecret = dhRaw(mySk, theirPk)
        return hsalsa20(sharedSecret, zeros16)
    }

    /** Зашифровать сообщение через crypto_box (XSalsa20-Poly1305) */
    fun cryptoBox(msg: ByteArray, nonce: ByteArray, theirPk: ByteArray, mySk: ByteArray): ByteArray {
        val subKey = cryptoBoxBeforeNm(theirPk, mySk)
        return cryptoBoxAfterNm(msg, nonce, subKey)
    }

    /** Расшифровать сообщение через crypto_box_open */
    fun cryptoBoxOpen(ct: ByteArray, nonce: ByteArray, theirPk: ByteArray, mySk: ByteArray): ByteArray {
        val subKey = cryptoBoxBeforeNm(theirPk, mySk)
        return cryptoBoxOpenAfterNm(ct, nonce, subKey)
    }

    /**
     * Шифрование с использованием предварительно вычисленного ключа.
     * Алгоритм: XSalsa20 для шифрования + Poly1305 для MAC.
     *
     * The nonce is 24 bytes: first 16 bytes are used as HSalsa20 input to derive
     * the XSalsa20 key; the remaining 8 bytes are used as the Salsa20 stream cipher nonce.
     * The Poly1305 authentication key is derived by encrypting 32 zero bytes with
     * counter=0. The actual message is encrypted with counter=1 (and subsequent blocks).
     * The MAC is appended to the ciphertext.
     */
    fun cryptoBoxAfterNm(msg: ByteArray, nonce: ByteArray, subKey: ByteArray): ByteArray {
        // XSalsa20: hash subKey + first 16 nonce bytes through HSalsa20 to get stream cipher key
        val xsalsa20Key = hsalsa20(subKey, nonce.copyOfRange(0, 16))
        val polyKey = ByteArray(32)
        val zeroMsg = ByteArray(32)
        // Первый блок Salsa20 для генерации ключа Poly1305 (counter = 0)
        salsa20XorIc(polyKey, zeroMsg, xsalsa20Key, nonce.copyOfRange(16, 24), 0L)
        val ciphertext = ByteArray(msg.size)
        // Последующие блоки для шифрования (counter starts at 1)
        salsa20XorIc(ciphertext, msg, xsalsa20Key, nonce.copyOfRange(16, 24), 1L)
        val tag = poly1305Mac(ciphertext, polyKey)
        return ciphertext + tag
    }

    /**
     * Расшифровка с предварительно вычисленным ключом.
     * Вычисляет MAC и проверяет его перед расшифровкой.
     *
     * MAC-then-decrypt: the Poly1305 tag is verified BEFORE decryption to prevent
     * timing attacks and oracle padding attacks. If the tag doesn't match,
     * a SecurityException is thrown and no decryption result is returned.
     */
    fun cryptoBoxOpenAfterNm(ctWithTag: ByteArray, nonce: ByteArray, subKey: ByteArray): ByteArray {
        if (ctWithTag.size < 16) throw IllegalArgumentException("ciphertext too short")
        val ct = ctWithTag.copyOfRange(0, ctWithTag.size - 16)
        val tag = ctWithTag.copyOfRange(ctWithTag.size - 16, ctWithTag.size)
        val xsalsa20Key = hsalsa20(subKey, nonce.copyOfRange(0, 16))
        val polyKey = ByteArray(32)
        val zeroMsg = ByteArray(32)
        salsa20XorIc(polyKey, zeroMsg, xsalsa20Key, nonce.copyOfRange(16, 24), 0L)
        val expectedTag = poly1305Mac(ct, polyKey)
        // Проверка MAC перед расшифровкой (защита от атак)
        if (!MessageDigest.isEqual(tag, expectedTag)) {
            android.util.Log.e(TAG, "crypto_box_open: Poly1305 tag mismatch")
            throw SecurityException("Poly1305 tag verification failed")
        }
        val plaintext = ByteArray(ct.size)
        salsa20XorIc(plaintext, ct, xsalsa20Key, nonce.copyOfRange(16, 24), 1L)
        return plaintext
    }
}
