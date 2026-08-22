/**
 * CBOR (Concise Binary Object Representation) encoder/decoder for the SMP protocol.
 *
 * Implements a minimal subset of RFC 7049 sufficient for SMP and XFTP frame
 * serialization. Supports unsigned/negative integers, byte strings, UTF-8 text
 * strings, arrays, maps, booleans, and null.
 *
 * @see SmpProtocol
 * @see com.example.data.protocol.xftp.XftpProtocol
 */
package com.paranoidx.sdk.protocol

/**
 * Файл: SmpCbor.kt
 * Пакет: com.example.data.protocol.smp
 * Назначение: Реализация кодирования/декодирования CBOR (Concise Binary Object Representation)
 * для SMP-протокола. Поддерживает: целые числа (unsigned/negative), байтовые строки,
 * текстовые строки (UTF-8), массивы, словари (map), булевы значения и null.
 *
 * CBOR используется как формат сериализации тела фреймов SMP и XFTP.
 * Данный парсер реализует минимальное подмножество CBOR, необходимое для протокола.
 *
 * @see SmpProtocol
 * @see XftpProtocol
 */
object SmpCbor {
    private const val MT_UNSIGNED = 0x00
    private const val MT_BYTES    = 0x40
    private const val MT_TEXT     = 0x60
    private const val MT_ARRAY    = 0x80
    private const val MT_MAP      = 0xa0
    private const val MT_SIMPLE   = 0xe0

    /**
     * Кодирует целое число (unsigned или отрицательное) в CBOR.
     * @param value целое число
     * @return CBOR-представление числа
     */
    fun encodeInt(value: Int): ByteArray {
        return when {
            value < 0 -> encodeIntNeg(value)
            value <= 0x17 -> byteArrayOf(value.toByte())
            value <= 0xff -> byteArrayOf(0x18, value.toByte())
            value <= 0xffff -> byteArrayOf(0x19, (value shr 8).toByte(), value.toByte())
            else -> byteArrayOf(0x1a, (value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())
        }
    }

    private fun encodeIntNeg(value: Int): ByteArray {
        val pos = -1 - value
        return when {
            pos <= 0x17 -> byteArrayOf((0x20 or pos).toByte())
            pos <= 0xff -> byteArrayOf(0x38, pos.toByte())
            pos <= 0xffff -> byteArrayOf(0x39, (pos shr 8).toByte(), pos.toByte())
            else -> byteArrayOf(0x3a, (pos shr 24).toByte(), (pos shr 16).toByte(), (pos shr 8).toByte(), pos.toByte())
        }
    }

    /**
     * Кодирует массив байт в CBOR (major type 2).
     * @param data данные для кодирования
     * @return CBOR-представление байтовой строки
     */
    fun encodeBytes(data: ByteArray): ByteArray {
        val len = data.size
        val prefix = when {
            len <= 0x17 -> byteArrayOf((0x40 or len).toByte())
            len <= 0xff -> byteArrayOf(0x58, len.toByte())
            len <= 0xffff -> byteArrayOf(0x59, (len shr 8).toByte(), len.toByte())
            else -> byteArrayOf(0x5a, (len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte())
        }
        return prefix + data
    }

    /**
     * Кодирует текстовую строку UTF-8 в CBOR (major type 3).
     * @param text текст для кодирования
     * @return CBOR-представление текстовой строки
     */
    fun encodeText(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val len = bytes.size
        val prefix = when {
            len <= 0x17 -> byteArrayOf((0x60 or len).toByte())
            len <= 0xff -> byteArrayOf(0x78, len.toByte())
            len <= 0xffff -> byteArrayOf(0x79, (len shr 8).toByte(), len.toByte())
            else -> byteArrayOf(0x7a, (len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte())
        }
        return prefix + bytes
    }

    /**
     * Кодирует словарь (map) из пар ключ-значение в CBOR (major type 5).
     * @param pairs список пар (закодированный ключ, закодированное значение)
     * @return CBOR-представление словаря
     */
    fun encodeMap(pairs: List<Pair<ByteArray, ByteArray>>): ByteArray {
        val len = pairs.size
        val prefix = when {
            len <= 0x17 -> byteArrayOf((0xa0 or len).toByte())
            len <= 0xff -> byteArrayOf(0xb8.toByte(), len.toByte())
            len <= 0xffff -> byteArrayOf(0xb9.toByte(), (len shr 8).toByte(), len.toByte())
            else -> byteArrayOf(0xba.toByte(), (len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte())
        }
        return prefix + pairs.flatMap { (k, v) -> k.toList() + v.toList() }.toByteArray()
    }

    /**
     * Кодирует булево значение в CBOR.
     */
    fun encodeBool(value: Boolean): ByteArray = byteArrayOf((if (value) 0xf5 else 0xf4).toByte())

    /**
     * Кодирует null в CBOR.
     */
    fun encodeNull(): ByteArray = byteArrayOf(0xf6.toByte())

    /**
     * Кодирует целочисленный ключ для словаря (алиас для encodeInt).
     */
    fun encodeLong(value: Long): ByteArray {
        return when {
            value < 0L -> {
                val pos = -1L - value
                when {
                    pos <= 0x17L -> byteArrayOf((0x20 or pos.toInt()).toByte())
                    pos <= 0xffL -> byteArrayOf(0x38, pos.toByte())
                    pos <= 0xffffL -> byteArrayOf(0x39, (pos shr 8).toByte(), pos.toByte())
                    pos <= 0xffffffffL -> byteArrayOf(
                        0x3a, (pos shr 24).toByte(), (pos shr 16).toByte(),
                        (pos shr 8).toByte(), pos.toByte()
                    )
                    else -> byteArrayOf(
                        0x3b, (pos shr 56).toByte(), (pos shr 48).toByte(),
                        (pos shr 40).toByte(), (pos shr 32).toByte(),
                        (pos shr 24).toByte(), (pos shr 16).toByte(),
                        (pos shr 8).toByte(), pos.toByte()
                    )
                }
            }
            value <= 0x17L -> byteArrayOf(value.toByte())
            value <= 0xffL -> byteArrayOf(0x18, value.toByte())
            value <= 0xffffL -> byteArrayOf(0x19, (value shr 8).toByte(), value.toByte())
            value <= 0xffffffffL -> byteArrayOf(
                0x1a, (value shr 24).toByte(), (value shr 16).toByte(),
                (value shr 8).toByte(), value.toByte()
            )
            else -> byteArrayOf(
                0x1b, (value shr 56).toByte(), (value shr 48).toByte(),
                (value shr 40).toByte(), (value shr 32).toByte(),
                (value shr 24).toByte(), (value shr 16).toByte(),
                (value shr 8).toByte(), value.toByte()
            )
        }
    }

    fun encodeIntKey(key: Int): ByteArray = encodeInt(key)

    /**
     * Кодирует текстовый ключ для словаря (алиас для encodeText).
     */
    fun encodeTextKey(key: String): ByteArray = encodeText(key)

    /**
     * Разобранное CBOR-значение.
     * @property type тип CBOR-значения (0-7)
     * @property value значение (Int, String, Boolean, null, или список пар для map)
     * @property bytes сырые байты (для major type 2)
     */
    data class CborValue(val type: Int, val value: Any?, val bytes: ByteArray?) {
        fun asInt(): Int = value as Int
        fun asBytes(): ByteArray = bytes ?: (value as ByteArray)
        fun asText(): String = value as String
        fun asBool(): Boolean = value as Boolean
        @Suppress("UNCHECKED_CAST")
        fun asMap(): List<Pair<Any?, Any?>> = (value as? List<*>)?.let {
            it as List<Pair<Any?, Any?>>
        } ?: emptyList()
    }

    fun decode(data: ByteArray, offset: Int = 0): Pair<CborValue, Int> {
        val initial = data[offset].toInt() and 0xff
        val majorType = initial and 0xe0
        val additionalInfo = initial and 0x1f

        return when (majorType) {
            MT_UNSIGNED -> {
                val (value, newOff) = decodeUnsigned(data, offset, additionalInfo)
                Pair(CborValue(0, value, null), newOff)
            }
            MT_BYTES -> {
                val (len, afterLen) = decodeLength(data, offset, additionalInfo)
                val bytes = data.copyOfRange(afterLen, afterLen + len)
                Pair(CborValue(2, null, bytes), afterLen + len)
            }
            MT_TEXT -> {
                val (len, afterLen) = decodeLength(data, offset, additionalInfo)
                val text = data.decodeToString(afterLen, afterLen + len)
                Pair(CborValue(3, text, null), afterLen + len)
            }
            MT_ARRAY -> {
                val (len, afterLen) = decodeLength(data, offset, additionalInfo)
                var current = afterLen
                val items = mutableListOf<CborValue>()
                for (i in 0 until len) {
                    val (item, newOff) = decode(data, current)
                    items.add(item)
                    current = newOff
                }
                Pair(CborValue(4, items, null), current)
            }
            MT_MAP -> {
                val (len, afterLen) = decodeLength(data, offset, additionalInfo)
                var current = afterLen
                val pairs = mutableListOf<Pair<Any?, Any?>>()
                for (i in 0 until len) {
                    val (key, kOff) = decode(data, current)
                    val (value, vOff) = decode(data, kOff)
                    pairs.add(Pair(key.value ?: key.bytes, value.value ?: value.bytes))
                    current = vOff
                }
                Pair(CborValue(5, pairs, null), current)
            }
            MT_SIMPLE -> {
                when (additionalInfo) {
                    20 -> Pair(CborValue(7, false, null), offset + 1)
                    21 -> Pair(CborValue(7, true, null), offset + 1)
                    22 -> Pair(CborValue(7, null, null), offset + 1)
                    else -> throw IllegalArgumentException("Unsupported simple value: $additionalInfo")
                }
            }
            else -> throw IllegalArgumentException("Unsupported major type: $majorType at offset $offset")
        }
    }

    private fun decodeUnsigned(data: ByteArray, offset: Int, additionalInfo: Int): Pair<Int, Int> {
        return when {
            additionalInfo <= 0x17 -> Pair(additionalInfo, offset + 1)
            additionalInfo == 0x18 -> Pair(data[offset + 1].toInt() and 0xff, offset + 2)
            additionalInfo == 0x19 -> Pair(
                ((data[offset + 1].toInt() and 0xff) shl 8) or (data[offset + 2].toInt() and 0xff),
                offset + 3
            )
            additionalInfo == 0x1a -> Pair(
                ((data[offset + 1].toInt() and 0xff) shl 24) or
                ((data[offset + 2].toInt() and 0xff) shl 16) or
                ((data[offset + 3].toInt() and 0xff) shl 8) or
                (data[offset + 4].toInt() and 0xff),
                offset + 5
            )
            else -> throw IllegalArgumentException("Unsupported additional info for unsigned: $additionalInfo")
        }
    }

    private fun decodeLength(data: ByteArray, offset: Int, additionalInfo: Int): Pair<Int, Int> {
        return when {
            additionalInfo <= 0x17 -> Pair(additionalInfo, offset + 1)
            additionalInfo == 0x18 -> Pair(data[offset + 1].toInt() and 0xff, offset + 2)
            additionalInfo == 0x19 -> Pair(
                ((data[offset + 1].toInt() and 0xff) shl 8) or (data[offset + 2].toInt() and 0xff),
                offset + 3
            )
            additionalInfo == 0x1a -> Pair(
                ((data[offset + 1].toInt() and 0xff) shl 24) or
                ((data[offset + 2].toInt() and 0xff) shl 16) or
                ((data[offset + 3].toInt() and 0xff) shl 8) or
                (data[offset + 4].toInt() and 0xff),
                offset + 5
            )
            else -> throw IllegalArgumentException("Unsupported additional info for length: $additionalInfo")
        }
    }
}
