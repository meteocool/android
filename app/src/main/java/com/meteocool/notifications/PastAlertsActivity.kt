package com.meteocool.notifications

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meteocool.R
import java.text.DateFormat
import java.util.Date

class PastAlertsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertHistory.captureDelivered(this)
        render()
    }

    private fun render() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val scroll = ScrollView(this).apply { addView(column) }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        column.addView(MaterialButton(this).apply { setText(R.string.dismiss); setOnClickListener { finish() } })
        column.addView(TextView(this).apply { setText(R.string.past_alerts); setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline5) })
        val alerts = AlertHistory.load(this)
        if (alerts.isEmpty()) column.addView(TextView(this).apply { setText(R.string.past_alerts_empty) })
        alerts.forEach { alert ->
            column.addView(TextView(this).apply {
                text = "${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(alert.received))}\n${alert.title}\n${alert.body}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body1)
                setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
            })
        }
        if (alerts.isNotEmpty()) column.addView(MaterialButton(this).apply {
            setText(R.string.past_alerts_clear)
            setOnClickListener {
                MaterialAlertDialogBuilder(this@PastAlertsActivity).setMessage(R.string.past_alerts_clear_confirm)
                    .setNegativeButton(R.string.dismiss, null)
                    .setPositiveButton(R.string.past_alerts_clear) { _, _ ->
                        Notifications.clearAll(this@PastAlertsActivity)
                        AlertHistory.clear(this@PastAlertsActivity)
                        render()
                    }.show()
            }
        })
    }
}
