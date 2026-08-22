package com.n3.app.services

import android.util.Log
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue

class TrafficPadding private constructor() {
    companion object {
        private const val TAG = "NexusChat/Padding"
        private const val CELL_SIZE = 128
        private const val MAX_CELL_SIZE = 1500
        @Volatile private var instance: TrafficPadding? = null
        fun getInstance(): TrafficPadding =
            instance ?: synchronized(this) {
                instance ?: TrafficPadding().also { instance = it }
            }
    }

    data class PaddingConfig(
        val fixedCellSize: Int = CELL_SIZE,
        val maxPadding: Int = 512,
        val minPadding: Int = 4,
        val enableRandomDummyCells: Boolean = true,
        val dummyCellIntervalMs: Long = 3000,
        val enableTimingJitter: Boolean = true,
        val maxJitterMs: Int = 200,
        val enableCoverTraffic: Boolean = true,
        val coverTrafficRatio: Float = 0.1f
    )

    private val rng = SecureRandom()
    private var config = PaddingConfig()

    fun configure(cfg: PaddingConfig) { config = cfg }

    data class PaddedPacket(
        val data: ByteArray,
        val padding: ByteArray,
        val totalSize: Int
    )

    fun padToCell(data: ByteArray, cellSize: Int = config.fixedCellSize): ByteArray {
        val payloadLen = data.size
        val totalCells = (payloadLen + cellSize - 1) / cellSize
        val totalSize = totalCells * cellSize
        val padded = data.copyOf(totalSize)
        val padding = ByteArray(totalSize - payloadLen)
        rng.nextBytes(padding)
        System.arraycopy(padding, 0, padded, payloadLen, padding.size)
        return padded
    }

    fun padWithRandom(data: ByteArray, minPad: Int = config.minPadding, maxPad: Int = config.maxPadding): ByteArray {
        val padLen = rng.nextInt(maxPad - minPad) + minPad
        val padding = ByteArray(padLen)
        rng.nextBytes(padding)
        return data + padding
    }

    fun padToBlock(data: ByteArray, blockSize: Int = 128): ByteArray {
        val remainder = data.size % blockSize
        if (remainder == 0) return data
        val padLen = blockSize - remainder
        val padding = ByteArray(padLen)
        rng.nextBytes(padding)
        return data + padding
    }

    fun addHeaderPadding(data: ByteArray, maxHeaderPad: Int = 64): ByteArray {
        val padLen = rng.nextInt(maxHeaderPad) + 1
        val header = ByteArray(padLen)
        rng.nextBytes(header)
        return header + data
    }

    data class PaddedFrame(
        val frameType: Byte,
        val sequenceNum: Int,
        val payload: ByteArray,
        val padding: ByteArray
    )

    fun frameData(data: ByteArray, seqNum: Int): ByteArray {
        val frameType: Byte = if (rng.nextBoolean()) 0x01 else 0x02
        val padLen = rng.nextInt(32) + 4
        val padding = ByteArray(padLen)
        rng.nextBytes(padding)
        val header = byteArrayOf(
            frameType,
            (seqNum shr 24).toByte(), (seqNum shr 16).toByte(),
            (seqNum shr 8).toByte(), seqNum.toByte(),
            (data.size shr 8).toByte(), data.size.toByte(),
            padLen.toByte()
        )
        return header + data + padding
    }

    fun generateDummyCell(cellSize: Int = MAX_CELL_SIZE): ByteArray {
        val cell = ByteArray(cellSize)
        rng.nextBytes(cell)
        cell[0] = 0x00
        cell[1] = 0x00
        cell[2] = 0x00
        cell[3] = 0x01
        return cell
    }

    fun stripPadding(data: ByteArray): ByteArray {
        if (data.size < 8) return data
        val dataSize = ((data[5].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)
        val headerSize = 8
        if (dataSize < 0 || headerSize + dataSize > data.size) return data
        return data.copyOfRange(headerSize, headerSize + dataSize)
    }

    fun getJitteredDelay(baseMs: Long = 50): Long {
        if (!config.enableTimingJitter) return baseMs
        return baseMs + rng.nextInt(config.maxJitterMs).toLong()
    }

    fun obfuscateLength(originalLen: Int, blockSize: Int = 32): Int {
        val blocks = (originalLen + blockSize - 1) / blockSize
        return blocks * blockSize + rng.nextInt(blockSize)
    }

    fun destroy() { instance = null }
}
