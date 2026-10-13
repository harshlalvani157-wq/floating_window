package com.freefloat.workspace.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.freefloat.workspace.MainActivity
import com.freefloat.workspace.R

class FloatingWindowService : Service() {
    private var windowManager: WindowManager? = null
    private var overlay: TextView? = null
    private val channelId = "freefloat_workspace"

    override fun onCreate() { super.onCreate(); createChannel(); startForeground(101, notification()) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) { ACTION_STOP -> stopSelf(); ACTION_MINIMIZE -> overlay?.animate()?.alpha(0.45f)?.start(); ACTION_CLOSE -> removeOverlay(); else -> showOverlay() }
        return START_STICKY
    }
    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this) || overlay != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = TextView(this).apply { text = "FreeFloat  •  Workspace active"; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xCC2B2638.toInt()); setPadding(24, 12, 24, 12); setOnClickListener { startActivity(Intent(this@FloatingWindowService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 48 }
        try { windowManager?.addView(overlay, params) } catch (_: WindowManager.BadTokenException) { removeOverlay() }
    }
    private fun removeOverlay() { overlay?.let { runCatching { windowManager?.removeView(it) } }; overlay = null }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(channelId, "Workspace", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(): Notification { val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); val stop = PendingIntent.getService(this, 2, Intent(this, FloatingWindowService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE); return NotificationCompat.Builder(this, channelId).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("FreeFloat Workspace active").setContentText("Your floating workspace is ready").setContentIntent(open).setOngoing(true).addAction(0, "Open", open).addAction(0, "Stop", stop).build() }
    override fun onDestroy() { removeOverlay(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { const val ACTION_STOP = "com.freefloat.workspace.STOP"; const val ACTION_MINIMIZE = "com.freefloat.workspace.MINIMIZE"; const val ACTION_CLOSE = "com.freefloat.workspace.CLOSE" }
}
