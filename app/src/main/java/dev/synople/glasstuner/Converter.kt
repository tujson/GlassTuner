package dev.synople.glasstuner

import java.lang.Math.min

/**
 * From https://github.com/chRyNaN/Android-Guitar-Tuner/blob/master/app/src/main/java/com/chrynan/android_guitar_tuner/tuner/converter/PCMArrayConverter.java
 * Converted from Java to Kotlin. Removed byte array conversion
 */

private const val SHORT_DIVISOR = (-1 * java.lang.Short.MIN_VALUE).toShort()

fun convert(shortArray: ShortArray): FloatArray {
    val convertedArray = FloatArray(shortArray.size)

    for (i in 0 until shortArray.size) {
        convertedArray[i] = shortArray[i].toFloat() / SHORT_DIVISOR
        convertedArray[i] = if (convertedArray[i] < -1) -1f else min(convertedArray[i], 1f)
    }

    return convertedArray
}