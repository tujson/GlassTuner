package dev.synople.glasstuner

import kotlin.math.pow

/**
 * From https://github.com/chRyNaN/Android-Guitar-Tuner/blob/master/app/src/main/java/com/chrynan/android_guitar_tuner/tuner/detection/YINPitchDetector.java
 * Converted from Java to Kotlin
 */

/**
 * Uses a YIN algorithm to determine the frequency of
 * the provided waveform data. The YIN algorithm is similar to the Auto-correlation Function used
 * for pitch detection but adds additional steps to better the accuracy of the results. Each step
 * lowers the error rate further. The following implementation was inspired by
 * [TarsosDsp](https://github.com/JorenSix/TarsosDSP/blob/master/src/core/be/tarsos/dsp/pitch/Yin.java)
 * and
 * [this YIN paper](http://recherche.ircam.fr/equipes/pcm/cheveign/ps/2002_JASA_YIN_proof.pdf).
 * The six steps in the YIN algorithm are (according to the YIN paper):
 *
 *
 *
 *  1. Auto-correlation Method
 *  1. Difference Function
 *  1. Cumulative Mean Normalized Difference Function
 *  1. Absolute Threshold
 *  1. Parabolic Interpolation
 *  1. Best Local Estimate
 *
 *
 * The first two steps, the Auto-correlation Method and the Difference Function, can seemingly be
 * combined into a single difference function step according to the YIN paper.
 */
class YINPitchDetector(private val sampleRate: Double, private val resultBuffer: FloatArray) {

    fun detect(wave: FloatArray): Double {
        val tau: Int = absoluteThreshold()

        // First, perform the functions to normalize the wave data

        // The first and second steps in the YIN algorithm
        autoCorrelationDifference(wave)

        // The third step in the YIN algorithm
        cumulativeMeanNormalizedDifference()

        // Then perform the functions to retrieve the tau (the approximate period)

        // The fourth step in the YIN algorithm

        // The fifth step in the YIN algorithm
        val betterTau = parabolicInterpolation(tau)

        // TODO implement the sixth and final step of the YIN algorithm
        // (it isn't implemented in the Tarsos DSP project but is briefly explained in the YIN
        // paper).

        // The fundamental frequency (note frequency) is the sampling rate divided by the tau (index
        // within the resulting buffer array that marks the period).
        // The period is the duration (or index here) of one cycle.
        // Frequency = 1 / Period, with respect to the sampling rate, Frequency = Sample Rate / Period
        return sampleRate / betterTau
    }

    /**
     * Performs the first and second step of the YIN Algorithm on the provided array buffer values.
     * This is a "combination" of the AutoCorrelation Method and the Difference Function. The
     * AutoCorrelation Method multiplies the array value at the specified index with the array value
     * at the specified index plus the "tau" (greek letter used in the formula). Whereas the
     * Difference Function takes the square of the difference of the two values. This is supposed to
     * provide a more accurate result (from about 10% to about 1.95% error rate). Note that this
     * formula is a riemann sum, meaning the operation specified above is performed and accumulated
     * for every value in the array. The result of this function is stored in a global array,
     * [.resultBuffer], which the subsequent steps of the algorithm should use.
     *
     * @param wave The waveform data to perform the AutoCorrelation Difference function on.
     */
    private fun autoCorrelationDifference(wave: FloatArray) {
        // Note this algorithm is currently slow (O(n^2)). Should look for any possible optimizations.
        val length = resultBuffer.size
        var i: Int
        var j = 1

        while (j < length) {
            i = 0
            while (i < length) {
                // d sub t (tau) = (x(i) - x(i - tau))^2, from i = 1 to result buffer size
                resultBuffer[j] += (wave[i] - wave[i + j]).toDouble().pow(2.0).toFloat()
                i++
            }
            j++
        }
    }

    /**
     * Performs the third step in the YIN Algorithm on the [.resultBuffer]. The result of this
     * function yields an even lower error rate (about 1.69% from 1.95%). The [.resultBuffer]
     * is updated when this function is performed.
     */
    private fun cumulativeMeanNormalizedDifference() {
        // newValue = oldValue / (runningSum / tau)
        // == (oldValue / 1) * (tau / runningSum)
        // == oldValue * (tau / runningSum)

        // Here we're using index i as the "tau" in the equation
        var i = 1
        val length = resultBuffer.size
        var runningSum = 0f

        // Set the first value in the result buffer to the value of one
        resultBuffer[0] = 1f

        while (i < length) {
            // The sum of this value plus all the previous values in the buffer array
            runningSum += resultBuffer[i]

            // The current value is updated to be the current value multiplied by the index divided by the running sum value
            resultBuffer[i] *= i / runningSum
            i++
        }
    }

    /**
     * Performs step four of the YIN Algorithm on the [.resultBuffer]. This is the first step
     * in the algorithm to attempt finding the period of the wave data. When attempting to determine
     * the period of a wave, it's common to search for the high or low peaks or dips of the wave.
     * This will allow you to determine the length of a cycle or its period. However, especially
     * with a natural sound sample, it is possible to have false dips. This makes determining the
     * period more difficult. This function attempts to resolve this issue by introducing a
     * threshold. The result of this function yields an even lower rate (about 0.78% from about
     * 1.69%).
     *
     * @return The tau indicating the approximate period.
     */
    private fun absoluteThreshold(): Int {
        var tau = 2
        val length = resultBuffer.size

        // The first two values in the result buffer should be 1, so start at the third value
        while (tau < length) {
            // If we are less than the threshold, continue on until we find the lowest value
            // indicating the lowest dip in the wave since we first crossed the threshold.
            if (resultBuffer[tau] < ABSOLUTE_THRESHOLD) {
                while (tau + 1 < length && resultBuffer[tau + 1] < resultBuffer[tau]) {
                    tau++
                }

                // We have the approximate tau value, so break the loop
                break
            }
            tau++
        }

        // Some implementations of this algorithm set the tau value to -1 to indicate no correct tau
        // value was found. This implementation will just return the last tau.
        tau = if (tau >= length) length - 1 else tau

        return tau
    }

    /**
     * Further lowers the error rate by using parabolas to smooth the wave between the minimum and
     * maximum points. Especially helps to detect higher frequencies more precisely. The result of
     * this function results in only a small error rate decline from about 0.78% to about 0.77%.
     */
    private fun parabolicInterpolation(currentTau: Int): Float {
        // Finds the points to fit the parabola between
        val x0 = if (currentTau < 1) currentTau else currentTau - 1
        val x2 = if (currentTau + 1 < resultBuffer.size) currentTau + 1 else currentTau

        // Finds the better tau estimate
        val betterTau: Float

        when {
            x0 == currentTau -> betterTau = if (resultBuffer[currentTau] <= resultBuffer[x2]) {
                currentTau.toFloat()
            } else {
                x2.toFloat()
            }
            x2 == currentTau -> betterTau = if (resultBuffer[currentTau] <= resultBuffer[x0]) {
                currentTau.toFloat()
            } else {
                x0.toFloat()
            }
            else -> {
                // Fit the parabola between the first point, current tau, and the last point to find a
                // better tau estimate.
                val s0 = resultBuffer[x0]
                val s1 = resultBuffer[currentTau]
                val s2 = resultBuffer[x2]

                betterTau = currentTau + (s2 - s0) / (2 * (2 * s1 - s2 - s0))
            }
        }

        return betterTau
    }

    companion object {
        // According to the YIN Paper, the threshold should be between 0.10 and 0.15
        private const val ABSOLUTE_THRESHOLD = 0.125f
    }
}