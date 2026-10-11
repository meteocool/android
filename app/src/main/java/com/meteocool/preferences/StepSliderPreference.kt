package com.meteocool.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import com.meteocool.R

/**
 * A [ListPreference] shown as a slider with one stop per entry, as the iOS
 * app's Intensity Threshold and Notification Timeframe are: it ticks on
 * every step, the label follows while dragging, a tap on the track jumps
 * there, and the value is stored only on release, so the registration
 * is posted once rather than at every step.
 */
class StepSliderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ListPreference(context, attrs) {

    init {
        layoutResource = R.layout.preference_step_slider
        isSelectable = false
    }

    /** No dialog: the slider is the picker. */
    override fun onClick() = Unit

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val slider = holder.findViewById(R.id.slider) as Slider
        val label = holder.findViewById(R.id.value) as TextView
        val values = entryValues ?: return
        val names = entries ?: return
        val stored = findIndexOfValue(value).coerceAtLeast(0)

        slider.clearOnChangeListeners()
        slider.clearOnSliderTouchListeners()
        slider.valueFrom = 0f
        slider.valueTo = (values.size - 1).coerceAtLeast(1).toFloat()
        slider.stepSize = 1f
        slider.value = stored.toFloat()
        slider.contentDescription = title
        slider.setLabelFormatter { names[it.toInt().coerceIn(names.indices)].toString() }
        label.text = names[stored]

        var tracking = false
        slider.addOnChangeListener { view, position, fromUser ->
            val index = position.toInt()
            label.text = names[index]
            if (!fromUser) return@addOnChangeListener
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            // Keys and accessibility actions change it without a touch.
            if (!tracking) store(index)
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                tracking = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                tracking = false
                store(slider.value.toInt())
            }
        })
    }

    private fun store(index: Int) {
        val chosen = entryValues?.getOrNull(index)?.toString() ?: return
        if (chosen != value && callChangeListener(chosen)) value = chosen
    }
}
