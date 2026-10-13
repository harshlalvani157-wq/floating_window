package com.freefloat.workspace.floating

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.freefloat.workspace.MainActivity
import com.freefloat.workspace.R

/**
 * Persistent smart-sidebar controller. It deliberately launches third-party apps
 * through their normal launcher activities; Android does not permit a normal APK
 * to embed arbitrary apps inside an overlay window.
 */
class FloatingWindowService : Service() {
    private var windowManager: WindowManager? = null
    private var sidebarView: SmartSidebarView? = null
    private var opened = false
    private val channelId = "freefloat_workspace"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(101, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_MINIMIZE -> sidebarView?.collapse()
            ACTION_CLOSE -> sidebarView?.collapse()
            else -> showSidebarHandle()
        }
        return START_STICKY
    }

    private fun showSidebarHandle() {
        if (!Settings.canDrawOverlays(this) || sidebarView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sidebarView = SmartSidebarView(this).also { addOverlay(it) }
    }

    private fun addOverlay(view: SmartSidebarView) {
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(52, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        try { windowManager?.addView(view, params) } catch (_: WindowManager.BadTokenException) { removeOverlay() }
    }

    private fun updateOverlaySize(width: Int) {
        val view = sidebarView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.width = width
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.END
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun removeOverlay() {
        sidebarView?.let { runCatching { windowManager?.removeView(it) } }
        sidebarView = null
    }

    private fun launchInstalledApp(info: ResolveInfo) {
        val launchIntent = packageManager.getLaunchIntentForPackage(info.activityInfo.packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (Build.VERSION.SDK_INT >= 24) {
            // OEMs/devices that support Android freeform may honor these bounds.
            // Stock phones normally ignore them and open the app normally.
            val options = ActivityOptions.makeBasic().setLaunchBounds(Rect(80, 160, 900, 1100))
            runCatching { startActivity(launchIntent, options.toBundle()) }.onFailure { startActivity(launchIntent) }
        } else startActivity(launchIntent)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(channelId, "Workspace", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, FloatingWindowService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("FreeFloat smart sidebar active").setContentText("Swipe or tap the right-edge handle to launch apps").setContentIntent(open).setOngoing(true).addAction(0, "Open", open).addAction(0, "Stop", stop).build()
    }

    override fun onDestroy() { removeOverlay(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private inner class SmartSidebarView(context: Context) : LinearLayout(context) {
        private val handle: TextView
        private val panel: LinearLayout
        private var editMode = false

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            handle = TextView(context).apply {
                text = "‹\n›"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = rounded(Color.rgb(78, 65, 112), 18)
                setOnClickListener { expand() }
                contentDescription = "Open FreeFloat smart sidebar"
            }
            panel = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(18, 18, 14, 18)
                background = rounded(Color.rgb(31, 27, 42), 26)
                visibility = View.GONE
            }
            addView(handle, LayoutParams(52, 150))
            addView(panel, LayoutParams(310, LayoutParams.MATCH_PARENT))
        }

        fun collapse() {
            opened = false
            panel.visibility = View.GONE
            handle.visibility = View.VISIBLE
            updateOverlaySize(52)
        }

        private fun expand() {
            opened = true
            panel.visibility = View.VISIBLE
            handle.visibility = View.GONE
            editMode = false
            renderPanel()
            updateOverlaySize(330)
        }

        private fun renderPanel() {
            panel.removeAllViews()
            val titleRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            val title = TextView(context).apply { text = "Smart sidebar"; textSize = 20f; setTextColor(Color.WHITE); setTypeface(typeface, android.graphics.Typeface.BOLD); layoutParams = LayoutParams(0, 58, 1f) }
            val close = TextView(context).apply { text = "×"; textSize = 30f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); contentDescription = "Close sidebar"; setOnClickListener { collapse() } }
            titleRow.addView(title); titleRow.addView(close, LayoutParams(48, 58)); panel.addView(titleRow)
            val hint = TextView(context).apply { text = if (editMode) "Choose an installed app" else "Tools and apps"; textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, 0, 0, 12) }
            panel.addView(hint)
            if (!editMode) {
                addSection("TOOLS")
                addTool("Quick note", android.R.drawable.ic_menu_edit) { launchNotes() }
                addTool("Open FreeFloat workspace", android.R.drawable.ic_menu_view) { startActivity(Intent(this@FloatingWindowService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); collapse() }
                addSection("APPS")
            }
            val apps = launchableApps()
            if (editMode || apps.isNotEmpty()) {
                apps.take(if (editMode) 40 else 8).forEach { addApp(it) }
            } else {
                val empty = TextView(context).apply { text = "No launchable apps found"; setTextColor(Color.LTGRAY); setPadding(0, 12, 0, 12) }; panel.addView(empty)
            }
            val scroll = panel.getChildAt(panel.childCount - 1)
            if (scroll !is ScrollView) {
                val current = ArrayList<View>()
                while (panel.childCount > 3) current.add(panel.getChildAt(3).also { panel.removeView(it) })
                val list = LinearLayout(context).apply { orientation = VERTICAL }
                current.forEach { list.addView(it) }
                val scrollView = ScrollView(context).apply { addView(list) }
                panel.addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            }
            val edit = TextView(context).apply { text = if (editMode) "Done" else "Edit app shortcuts"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = rounded(Color.rgb(78, 65, 112), 14); setPadding(12, 10, 12, 10); setOnClickListener { editMode = !editMode; renderPanel() } }
            panel.addView(edit, LayoutParams(LayoutParams.MATCH_PARENT, 52))
        }

        private fun addSection(label: String) { panel.addView(TextView(context).apply { text = label; textSize = 12f; setTextColor(Color.rgb(190, 170, 240)); setPadding(0, 10, 0, 6) }) }
        private fun addTool(label: String, icon: Int, action: () -> Unit) { val row = itemRow(label, icon); row.setOnClickListener { action() }; panel.addView(row) }
        private fun addApp(info: ResolveInfo) { val label = info.loadLabel(packageManager).toString(); val row = itemRow(label, android.R.drawable.sym_def_app_icon); row.setOnClickListener { launchInstalledApp(info) }; panel.addView(row) }
        private fun itemRow(label: String, icon: Int): LinearLayout { return LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = 56; setPadding(8, 3, 8, 3); val image = ImageView(context).apply { setImageResource(icon); contentDescription = label }; addView(image, LayoutParams(40, 40)); addView(TextView(context).apply { text = label; textSize = 16f; setTextColor(Color.WHITE); setPadding(14, 0, 0, 0) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)) } }
        private fun launchableApps(): List<ResolveInfo> { val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER); return packageManager.queryIntentActivities(intent, 0).filter { it.activityInfo.packageName != packageName }.distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString().lowercase() } }
        private fun launchNotes() { startActivity(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "") }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); collapse() }
        private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    }

    companion object { const val ACTION_STOP = "com.freefloat.workspace.STOP"; const val ACTION_MINIMIZE = "com.freefloat.workspace.MINIMIZE"; const val ACTION_CLOSE = "com.freefloat.workspace.CLOSE" }
}
