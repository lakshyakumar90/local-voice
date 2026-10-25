package dev.localvoice.core

import kotlin.math.*

/** Windowed-sinc low-pass resampling; preserves duration and rejects aliasing above target Nyquist. */
object PcmResampler {
    fun resample(input: ShortArray, count: Int, sourceRate: Int, targetRate: Int = 16_000): FloatArray {
        require(count in 0..input.size && sourceRate > 0 && targetRate > 0)
        if(sourceRate == targetRate) return FloatArray(count) { input[it]/32768f }
        val output = FloatArray((count.toLong()*targetRate/sourceRate).toInt())
        val radius = 24
        val cutoff = min(1.0,targetRate.toDouble()/sourceRate)*.9
        val kernels = Array(128) { phase -> DoubleArray(radius*2) { tap ->
            val x = tap-radius+1-phase/128.0
            val sinc = if(abs(x)<1e-9) cutoff else sin(PI*x*cutoff)/(PI*x)
            sinc * (.5+.5*cos(PI*x/radius)).coerceAtLeast(0.0)
        } }
        for(i in output.indices) {
            val center = i.toDouble()*sourceRate/targetRate
            val index = floor(center).toInt()
            val kernel = kernels[((center-index)*128).toInt().coerceIn(0,127)]
            var sum = 0.0; var weight = 0.0
            for(tap in kernel.indices) {
                val src = index+tap-radius+1
                if(src in 0 until count) { sum += input[src]*kernel[tap]; weight += kernel[tap] }
            }
            output[i] = if(abs(weight)>1e-10) (sum/weight/32768).toFloat().coerceIn(-1f,1f) else 0f
        }
        return output
    }
}
