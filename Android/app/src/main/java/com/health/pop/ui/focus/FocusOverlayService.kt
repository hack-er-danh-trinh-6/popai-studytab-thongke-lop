package com.health.pop.ui.focus

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.health.pop.MainActivity

/** Hiện lời nhắc focus trên các ứng dụng khác sau khi Activity POP AI đi xuống nền. */
class FocusOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SHOW -> showOverlay(
                    intent.getStringExtra(EXTRA_TASK_NAME).orEmpty(),
                    intent.getIntExtra(EXTRA_TARGET_MINUTES, 25),
                )
                ACTION_HIDE -> removeOverlay()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(ACTION_SHOW)
            addAction(ACTION_HIDE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(commandReceiver, filter)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            removeOverlay()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PREPARE) return START_NOT_STICKY
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlay(
            intent?.getStringExtra(EXTRA_TASK_NAME).orEmpty(),
            intent?.getIntExtra(EXTRA_TARGET_MINUTES, 25) ?: 25,
        )
        return START_NOT_STICKY
    }

    private fun showOverlay(taskName: String, targetMinutes: Int) {
        removeOverlay()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackgroundColor(0xF2171722.toInt())
            isClickable = true
            isFocusable = false
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(24))
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(24).toFloat()
            }
        }
        val title = TextView(this).apply {
            text = "Bạn đang trong thời gian tập trung"
            textSize = 22f
            setTextColor(0xFF172033.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val message = TextView(this).apply {
            text = "Đồng hồ vẫn đang chạy. Hãy quay lại POP AI để tiếp tục phiên tập trung."
            textSize = 16f
            setTextColor(0xFF5F6675.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        val task = TextView(this).apply {
            text = "${taskName.ifBlank { "Tập trung học tập" }} · $targetMinutes phút"
            textSize = 14f
            setTextColor(0xFF315F9B.toInt())
            gravity = Gravity.CENTER
        }
        val returnIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_RETURN_TO_FOCUS, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_RETURN_TO_FOCUS,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val button = Button(this).apply {
            text = "Quay lại đồng hồ tập trung"
            setOnClickListener { pendingIntent.send() }
        }

        card.addView(title, matchWidth())
        card.addView(message, matchWidth(top = 12))
        card.addView(task, matchWidth(top = 4))
        card.addView(button, matchWidth(top = 20))
        root.addView(card, LinearLayout.LayoutParams(-1, -2))

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            wm.addView(root, params)
            overlayView = root
        } catch (_: WindowManager.BadTokenException) {
            stopSelf()
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun matchWidth(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        if (top > 0) topMargin = dp(top)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun removeOverlay() {
        val view = overlayView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: IllegalArgumentException) {
            // View đã được hệ thống gỡ khi Activity/window bị huỷ.
        }
        overlayView = null
    }

    override fun onDestroy() {
        removeOverlay()
        unregisterReceiver(commandReceiver)
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.health.pop.focus.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.health.pop.focus.HIDE_OVERLAY"
        const val ACTION_PREPARE = "com.health.pop.focus.PREPARE_OVERLAY"
        const val EXTRA_TASK_NAME = "focus_task_name"
        const val EXTRA_TARGET_MINUTES = "focus_target_minutes"
        private const val REQUEST_RETURN_TO_FOCUS = 901

        fun prepare(context: Context) {
            context.startService(Intent(context, FocusOverlayService::class.java).setAction(ACTION_PREPARE))
        }

        fun show(context: Context, taskName: String, targetMinutes: Int) {
            val intent = Intent(ACTION_SHOW).setPackage(context.packageName).apply {
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_TARGET_MINUTES, targetMinutes)
            }
            context.sendBroadcast(intent)
        }

        fun hide(context: Context) {
            context.sendBroadcast(Intent(ACTION_HIDE).setPackage(context.packageName))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusOverlayService::class.java))
        }
    }
}
