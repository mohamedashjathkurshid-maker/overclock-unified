
package com.ghostgrid.overclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class HudService : Service() {

    private lateinit var wm: WindowManager
    private var hudView: View? = null
    private var consoleView: View? = null
    private var tabView: View? = null
    private var fanView: FanView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val overlayType: Int
        get() = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
        addHud()
        addConsole()
        addEdgeTab()
        handler.post(updateLoop)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateLoop)
        listOf(hudView, consoleView, tabView, fanView).forEach { v ->
            v?.let { runCatching { wm.removeView(it) } }
        }
        super.onDestroy()
    }

    // ---------- foreground notification ----------
    private fun startForegroundWithNotification() {
        val ch = NotificationChannel("hud", "HUD", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, "hud")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("HUD//OVERCLOCK active")
            .setContentText("tap to configure")
            .setContentIntent(pi)
            .build()
        ServiceCompat.startForeground(this, 1, notif,
            if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
    }

    // ---------- the main HUD bar ----------
    private fun addHud() {
        val v = LayoutInflater.from(this).inflate(R.layout.view_hud, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 300 }

        attachDrag(v, lp,
            onTap = { HudActions.boost(this) },
            onHold = { HudActions.killBackground(this); toast("kill-all ✔") }
        )
        wm.addView(v, lp)
        hudView = v
    }

    // ---------- console bar ----------
    private fun addConsole() {
        val v = LayoutInflater.from(this).inflate(R.layout.view_console, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 120 }

        val input = v.findViewById<EditText>(R.id.consoleInput)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                val out = ConsoleCommands.run(this, input.text.toString())
                if (out.isNotEmpty()) toast(out)
                input.setText("")
                true
            } else false
        }
        wm.addView(v, lp)
        consoleView = v
    }

    // ---------- stealth edge tab + fan ----------
    private fun addEdgeTab() {
        val v = LayoutInflater.from(this).inflate(R.layout.view_edgetab, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END; x = 0; y = -100 }

        attachDrag(v, lp,
            onTap = { toggleFan() },
            onHold = { /* reposition mode: drag already handles */ }
        )
        wm.addView(v, lp)
        tabView = v
    }

    private var fanOpen = false
    private fun toggleFan() {
        if (fanOpen) { fanView?.let { runCatching { wm.removeView(it) } }; fanView = null; fanOpen = false; return }
        val fan = FanView(this)
        val pm = packageManager
        val launchables = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).take(5)
        launchables.forEach { ri ->
            val item = LayoutInflater.from(this).inflate(R.layout.fan_item, fan, false)
            val icon = item.findViewById<FrameLayout>(R.id.fanIcon)
            icon.background = pm.getApplicationIcon(ri.activityInfo.packageName)
            item.setOnClickListener {
                val i = pm.getLaunchIntentForPackage(ri.activityInfo.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                i?.let { startActivity(it) }
                toggleFan()
            }
            fan.addView(item)
        }
        val lp = WindowManager.LayoutParams(
            620, 620, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        wm.addView(fan, lp)
        fanView = fan; fanOpen = true
    }

    // ---------- shared drag / tap / hold logic ----------
    private fun attachDrag(v: View, lp: WindowManager.LayoutParams, onTap: () -> Unit, onHold: () -> Unit) {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var moved = false; var held = false
        val holdRunnable = Runnable { held = true; onHold(); v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                    moved = false; held = false
                    handler.postDelayed(holdRunnable, 600)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (dx*dx + dy*dy > 64) { moved = true; handler.removeCallbacks(holdRunnable) }
                    lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt()
                    wm.updateViewLayout(v, lp)
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(holdRunnable)
                    if (!moved && !held) onTap()
                }
            }
            true
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- live stats loop ----------
    private val updateLoop = object : Runnable {
        override fun run() {
            hudView?.let { v ->
                val s = StatsReader.read(this@HudService)
                v.findViewById<ProgressBar>(R.id.cpuBar)?.progress = s.cpuPct
                v.findViewById<ProgressBar>(R.id.ramBar)?.progress = s.ramPct
                v.findViewById<TextView>(R.id.cpuText)?.text = "${s.cpuPct}%"
                v.findViewById<TextView>(R.id.ramText)?.text = "${s.ramPct}%"
                v.findViewById<TextView>(R.id.netText)?.text = "${s.netKbps}K"
                v.findViewById<TextView>(R.id.tempText)?.text = "${s.tempC}°"
                v.findViewById<TextView>(R.id.fpsText)?.text = "fps:${s.fps}  zram:${s.zramPct}%"
            }
            handler.postDelayed(this, 1000)
        }
    }
}
