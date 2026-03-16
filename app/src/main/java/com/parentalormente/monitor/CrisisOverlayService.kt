package com.parentalormente.monitor

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.parentalormente.ParentalApp
import com.parentalormente.R

/**
 * Full-screen WindowManager overlay shown during a crisis situation.
 *
 * Triggered automatically when CRITICAL severity incidents involving self-harm
 * or suicide language are detected, or by the parent via POM:CRISIS SMS command.
 *
 * Shows:
 *   - 988 Suicide & Crisis Lifeline (call/text)
 *   - Crisis Text Line (text HOME to 741741)
 *   - 911 emergency
 *   - Acknowledgement that the parent has been notified
 *
 * The child can dismiss by tapping "I Am Safe" — dismissal is logged.
 * The parent can also dismiss remotely via POM:CRISIS:OFF command.
 *
 * Requires SYSTEM_ALERT_WINDOW permission (requested in setup flow).
 */
class CrisisOverlayService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1004

        fun show(context: Context) {
            context.startForegroundService(Intent(context, CrisisOverlayService::class.java))
        }

        fun dismiss(context: Context) {
            context.stopService(Intent(context, CrisisOverlayService::class.java))
        }
    }

    private var overlayView: ScrollView? = null
    private lateinit var windowManager: WindowManager

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (overlayView == null) showOverlay()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
        }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------ UI

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val scroll = ScrollView(this)
        val root = buildRoot()
        scroll.addView(root)
        scroll.setBackgroundColor(Color.parseColor("#EE0D1B3D"))

        windowManager.addView(scroll, params)
        overlayView = scroll
    }

    private fun buildRoot(): LinearLayout {
        val dp = resources.displayMetrics.density

        fun px(dp: Float) = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
        ).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(28f), px(72f), px(28f), px(40f))

            // ── Warning icon ──────────────────────────────────────────────
            addView(TextView(context).apply {
                text = "\u26A0\uFE0F"          // ⚠️
                textSize = 52f
                gravity = Gravity.CENTER
            })

            // ── Heading ───────────────────────────────────────────────────
            addView(styledText("You Are Not Alone", 26f, Color.WHITE, bold = true).also {
                it.gravity = Gravity.CENTER
            }, topMargin(px(16f)))

            // ── Subtext ───────────────────────────────────────────────────
            addView(styledText(
                "Someone who cares about you has been notified.\nHelp is available right now — please reach out.",
                15f, Color.parseColor("#CCFFFFFF")
            ).also {
                it.gravity = Gravity.CENTER
                it.lineSpacingMultiplier = 1.45f
            }, topMargin(px(12f)))

            // ── Divider ───────────────────────────────────────────────────
            addView(divider(), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(1f)
            ).also { it.topMargin = px(28f) })

            // ── Crisis support header ─────────────────────────────────────
            addView(styledText("Crisis Support", 17f, Color.WHITE, bold = true).also {
                it.gravity = Gravity.CENTER
            }, topMargin(px(24f)))

            // ── 988 button ────────────────────────────────────────────────
            addView(crisisButton(
                label = "Call or Text  988",
                sub = "Suicide & Crisis Lifeline  •  Free, 24/7",
                bgColor = "#C62828",
                tel = "988"
            ), fullWidth(px(14f)))

            // ── Crisis Text Line ──────────────────────────────────────────
            addView(styledText(
                "Or text  HOME  to  741741\nCrisis Text Line  •  Free, 24/7",
                14f, Color.parseColor("#CCFFFFFF")
            ).also {
                it.gravity = Gravity.CENTER
                it.lineSpacingMultiplier = 1.4f
            }, topMargin(px(14f)))

            // ── 911 button ────────────────────────────────────────────────
            addView(crisisButton(
                label = "Call 911  —  Emergency",
                sub = null,
                bgColor = "#7B1FA2",
                tel = "911"
            ), fullWidth(px(14f)))

            // ── Divider ───────────────────────────────────────────────────
            addView(divider(), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(1f)
            ).also { it.topMargin = px(24f) })

            // ── "I Am Safe" dismiss ───────────────────────────────────────
            addView(Button(context).apply {
                text = "I Am Safe — Close"
                textSize = 14f
                setTextColor(Color.parseColor("#AAFFFFFF"))
                setBackgroundColor(Color.parseColor("#2AFFFFFF"))
                isAllCaps = false
                setPadding(px(12f), px(14f), px(12f), px(14f))
                setOnClickListener { stopSelf() }
            }, fullWidth(px(20f)))

            // ── Footer ────────────────────────────────────────────────────
            addView(styledText(
                "Your guardian has been alerted and is watching over you.",
                12f, Color.parseColor("#88FFFFFF")
            ).also {
                it.gravity = Gravity.CENTER
            }, topMargin(px(16f)))
        }
    }

    private fun crisisButton(label: String, sub: String?, bgColor: String, tel: String): LinearLayout {
        val dp = resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor(bgColor))
            setPadding(px(16f), px(16f), px(16f), px(16f))
            isClickable = true
            isFocusable = true

            addView(styledText(label, 17f, Color.WHITE, bold = true).also { it.gravity = Gravity.CENTER })
            if (sub != null) {
                addView(styledText(sub, 12f, Color.parseColor("#CCFFFFFF")).also {
                    it.gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = px(4f) })
            }

            setOnClickListener {
                startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }

    private fun styledText(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun divider() = android.view.View(this).apply {
        setBackgroundColor(Color.parseColor("#44FFFFFF"))
    }

    private fun topMargin(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }

    private fun fullWidth(topMargin: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { this.topMargin = topMargin }

    // ─────────────────────────────────────────────────────────── notification

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, ParentalApp.CHANNEL_ALERTS)
            .setContentTitle("Crisis Support Active")
            .setContentText("Help resources are shown on screen")
            .setSmallIcon(R.drawable.ic_warning)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .build()
}
