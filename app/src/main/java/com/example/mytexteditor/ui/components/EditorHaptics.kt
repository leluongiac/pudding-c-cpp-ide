package com.example.mytexteditor.ui.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

object EditorHaptics {
    private var cachedVibrator: Vibrator? = null
    private var isInitialized = false

    private fun getVibrator(context: Context): Vibrator? {
        if (!isInitialized) {
            cachedVibrator = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.applicationContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
            } catch (_: Exception) { null }
            isInitialized = true
        }
        return cachedVibrator
    }

    // Pre-create hiệu ứng để không cấp phát bộ nhớ khi gõ
    private val clickEffect: VibrationEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    } else null

    fun keyPress(view: View, context: Context) {
        val success = view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        if (!success) {
            val vibrator = getVibrator(context) ?: return
            try {
                if (clickEffect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(clickEffect)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(10)
                }
            } catch (_: Exception) {}
        }
    }
}