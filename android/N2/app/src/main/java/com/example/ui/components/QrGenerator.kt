package com.example.ui.components

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * Утилита для генерации QR-кода из текста.
 * Использует библиотеку ZXing с максимальной коррекцией ошибок (Level H).
 */
object QrGenerator {
    /**
     * Генерирует двумерный булевый массив (bit matrix) QR-кода из переданного текста.
     * Уровень коррекции H позволяет накладывать логотип поверх кода без потери читаемости.
     *
     * @param content содержимое для кодирования
     * @return матрица пикселей true = чёрный, false = белый
     */
    fun generate(content: String): Array<BooleanArray> {
        // Пустая строка — возвращаем минимальную пустую матрицу 21x21
        if (content.isBlank()) {
            return Array(21) { BooleanArray(21) }
        }
        return try {
            // Параметры кодирования: коррекция H, отступ 1, кодировка UTF-8
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            // Конвертируем ZXing BitMatrix в Kotlin Array<BooleanArray>
            val matrix = Array(height) { BooleanArray(width) }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    matrix[y][x] = bitMatrix.get(x, y)
                }
            }
            matrix
        } catch (e: Exception) {
            // Безопасное возвращение пустой матрицы при ошибке
            Array(21) { BooleanArray(21) }
        }
    }
}
