package com.anuvab.animecontrol

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AnimeControlService : AccessibilityService() {

    companion object {
        var instance: AnimeControlService? = null
    }

    private lateinit var wm: WindowManager
    private var triggerView: View? = null
    private var panelView: View? = null
    private var eyeComfortView: View? = null

    private var isTorchOn = false
    private var isEyeComfortOn = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupTopRightSwipeTrigger()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeTrigger()
        hideControlPanel()
    }

    private fun setupTopRightSwipeTrigger() {
        removeTrigger()
        val screenW = resources.displayMetrics.widthPixels
        val triggerW = (screenW * 0.42f).toInt()

        triggerView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            var downY = 0f
            setOnTouchListener { _, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = ev.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (ev.rawY - downY > dp(24)) {
                            showControlPanel()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        val params = WindowManager.LayoutParams(
            triggerW,
            dp(28),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
        }

        try {
            wm.addView(triggerView, params)
        } catch (_: Exception) {}
    }

    private fun removeTrigger() {
        triggerView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            triggerView = null
        }
    }

    fun showControlPanel() {
        if (panelView != null) return

        val prefs = getSharedPreferences("anime_prefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_hex", "#1E6FF9") ?: "#1E6FF9")
        val bgDim = prefs.getInt("bg_dim", 130)

        val root = FrameLayout(this)

        // 1. Anime Background ImageView
        val bgImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val bgFile = File(filesDir, "anime_bg.jpg")
            if (bgFile.exists()) {
                setImageBitmap(BitmapFactory.decodeFile(bgFile.absolutePath))
            } else {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#140B29"), Color.parseColor("#091931"), Color.parseColor("#1F0A24"))
                )
            }
        }
        root.addView(bgImage, FrameLayout.LayoutParams(-1, -1))

        // 2. Darkening Scrim for Readability
        val scrim = View(this).apply {
            setBackgroundColor(Color.argb(bgDim, 8, 10, 18))
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        // 3. Optional Bottom Floating Chibi / Anime Character
        val chibiFile = File(filesDir, "anime_chibi.png")
        if (chibiFile.exists()) {
            val chibiView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_END
                alpha = 0.92f
                setImageBitmap(BitmapFactory.decodeFile(chibiFile.absolutePath))
            }
            val chibiParams = FrameLayout.LayoutParams(dp(220), dp(240), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(8), dp(20))
            }
            root.addView(chibiView, chibiParams)
        }

        // 4. Main Foreground Control Container (Matching Realme UI Screenshot)
        val mainColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(42), dp(20), dp(24))
        }

        // --- HEADER: Clock, Date, Battery & Gear ---
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val clockCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val now = Date()
        val timeText = TextView(this).apply {
            text = SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
            textSize = 36f
            setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.WHITE)
        }
        val dateText = TextView(this).apply {
            text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
        }
        clockCol.addView(timeText)
        clockCol.addView(dateText)

        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val rightActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val batLabel = TextView(this).apply {
            text = "$batPct%"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, dp(14), 0)
        }
        val settingsBtn = TextView(this).apply {
            text = "\u2699"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                hideControlPanel()
                openIntent(Intent(Settings.ACTION_SETTINGS))
            }
        }
        val closeBtn = TextView(this).apply {
            text = "\u2715"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(4), dp(6))
            setOnClickListener { hideControlPanel() }
        }
        rightActions.addView(batLabel)
        rightActions.addView(settingsBtn)
        rightActions.addView(closeBtn)

        headerRow.addView(clockCol)
        headerRow.addView(rightActions)
        mainColumn.addView(headerRow)
        addSpacer(mainColumn, 20)

        // --- TOP SECTION: Left (SIM + Wi-Fi Pills) & Right (Brightness + Volume Vertical Sliders) ---
        val topSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Left Pill Column (SIM2 + Wi-Fi)
        val pillCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(164), 1.05f)
        }

        val simPill = createBigPill("\u21C5   SIM & Data", accentColor) {
            hideControlPanel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                openIntent(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            } else {
                openIntent(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            }
        }
        val wifiPill = createBigPill("\uD83D\uDCF6   Wi-Fi Network", accentColor) {
            hideControlPanel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                openIntent(Intent(Settings.Panel.ACTION_WIFI))
            } else {
                openIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }
        pillCol.addView(simPill, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(12) })
        pillCol.addView(wifiPill, LinearLayout.LayoutParams(-1, 0, 1f))

        // Right Vertical Sliders Column (Brightness & Volume)
        val slidersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, dp(164), 1f).apply { leftMargin = dp(14) }
        }

        val curBright = try {
            (Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100) / 255
        } catch (_: Exception) { 60 }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) / maxVol

        val brightSlider = createVerticalSlider("\u2600", curBright, accentColor) { pct ->
            if (Settings.System.canWrite(this)) {
                val bVal = ((pct * 255) / 100).coerceIn(1, 255)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, bVal)
            }
        }
        val volSlider = createVerticalSlider("\uD83D\uDD0A", curVol, accentColor) { pct ->
            val vVal = ((pct * maxVol) / 100).coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vVal, 0)
        }

        slidersRow.addView(brightSlider, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(12) })
        slidersRow.addView(volSlider, LinearLayout.LayoutParams(0, -1, 1f))

        topSection.addView(pillCol)
        topSection.addView(slidersRow)
        mainColumn.addView(topSection)
        addSpacer(mainColumn, 24)

        // --- 4x3 CIRCULAR QUICK SETTINGS GRID (All 12 Toggles from Screenshot) ---
        val isRingActive = audioManager.ringerMode != AudioManager.RINGER_MODE_SILENT
        val isAutoRotate = try {
            Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
        } catch (_: Exception) { false }

        data class TileSpec(val icon: String, val label: String, var active: Boolean, val onTap: ( tileBtn: TextView ) -> Unit)

        val tiles = listOf(
            TileSpec("B", "Bluetooth", false) {
                hideControlPanel()
                openIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            TileSpec("\uD83D\uDD14", "Ring", isRingActive) { btn ->
                val nextMode = if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                    AudioManager.RINGER_MODE_VIBRATE
                } else {
                    AudioManager.RINGER_MODE_NORMAL
                }
                try {
                    audioManager.ringerMode = nextMode
                    val normal = nextMode == AudioManager.RINGER_MODE_NORMAL
                    styleCircleTile(btn, normal, accentColor)
                } catch (_: Exception) {}
            },
            TileSpec("\uD83D\uDD26", "Torch", isTorchOn) { btn ->
                toggleFlashlight()
                styleCircleTile(btn, isTorchOn, accentColor)
            },
            TileSpec("\uD83D\uDCCD", "Location", true) {
                hideControlPanel()
                openIntent(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            TileSpec("\u2708", "Aeroplane mode", false) {
                hideControlPanel()
                openIntent(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            },
            TileSpec("\uD83C\uDFE0", "Device controls", false) {
                hideControlPanel()
                openIntent(Intent(this, MainActivity::class.java))
            },
            TileSpec("\uD83D\uDD04", "Auto-rotate", isAutoRotate) { btn ->
                if (Settings.System.canWrite(this)) {
                    val cur = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    val next = if (cur == 1) 0 else 1
                    Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, next)
                    styleCircleTile(btn, next == 1, accentColor)
                } else {
                    Toast.makeText(this, "Enable Write Settings in Anime Studio first", Toast.LENGTH_SHORT).show()
                }
            },
            TileSpec("\u25D0", "Dark mode", true) {
                hideControlPanel()
                openIntent(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            },
            TileSpec("\uD83D\uDC41", "Eye Comfort", isEyeComfortOn) { btn ->
                toggleEyeComfort()
                styleCircleTile(btn, isEyeComfortOn, accentColor)
            },
            TileSpec("\u2702", "Screenshot", false) {
                hideControlPanel()
                Handler(Looper.getMainLooper()).postDelayed({
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                    }
                }, 350)
            },
            TileSpec("\uD83D\uDCE1", "Personal\nhotspot", false) {
                hideControlPanel()
                openIntent(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            },
            TileSpec("\u25CE", "realme Share", false) {
                hideControlPanel()
                openIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        )

        for (rowIdx in 0 until 3) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) }
            }
            for (colIdx in 0 until 4) {
                val spec = tiles[rowIdx * 4 + colIdx]
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                val circleBtn = TextView(this).apply {
                    text = spec.icon
                    textSize = 20f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
                    styleCircleTile(this, spec.active, accentColor)
                    setOnClickListener { spec.onTap(this) }
                }
                val lbl = TextView(this).apply {
                    text = spec.label
                    textSize = 11.5f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(6), 0, 0)
                }
                cell.addView(circleBtn)
                cell.addView(lbl)
                row.addView(cell)
            }
            mainColumn.addView(row)
        }

        // Bottom Swipe-Up Close Bar
        val closeHint = TextView(this).apply {
            text = "Tap here or swipe up to close"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(18), 0, dp(10))
            setOnClickListener { hideControlPanel() }
        }
        mainColumn.addView(closeHint)

        root.addView(mainColumn, FrameLayout.LayoutParams(-1, -1))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        panelView = root
        try {
            wm.addView(panelView, params)
        } catch (_: Exception) {}
    }

    fun hideControlPanel() {
        panelView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            panelView = null
        }
    }

    private fun createBigPill(title: String, accentColor: Int, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = "$title    \u203A"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = dp(22).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createVerticalSlider(icon: String, initialPct: Int, accentColor: Int, onChange: (Int) -> Unit): View {
        val box = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#38FFFFFF"))
                cornerRadius = dp(22).toFloat()
            }
            clipToOutline = true
        }
        val fill = View(this).apply {
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = dp(22).toFloat()
            }
        }
        val fillLp = FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM)
        box.addView(fill, fillLp)

        val iconView = TextView(this).apply {
            text = "$icon\n$initialPct%"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(14))
        }
        box.addView(iconView, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        box.post {
            fillLp.height = (box.height * initialPct.coerceIn(0, 100)) / 100
            fill.layoutParams = fillLp
        }

        box.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    val h = v.height.coerceAtLeast(1)
                    val pct = ((1f - (ev.y / h)) * 100f).toInt().coerceIn(0, 100)
                    fillLp.height = (h * pct) / 100
                    fill.layoutParams = fillLp
                    iconView.text = "$icon\n$pct%"
                    onChange(pct)
                    true
                }
                else -> false
            }
        }
        return box
    }

    private fun styleCircleTile(btn: TextView, active: Boolean, accentColor: Int) {
        btn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) accentColor else Color.parseColor("#38FFFFFF"))
        }
        btn.setTextColor(Color.WHITE)
    }

    private fun toggleFlashlight() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull() ?: return
            isTorchOn = !isTorchOn
            cm.setTorchMode(id, isTorchOn)
        } catch (_: Exception) {}
    }

    private fun toggleEyeComfort() {
        isEyeComfortOn = !isEyeComfortOn
        if (isEyeComfortOn) {
            if (eyeComfortView == null) {
                eyeComfortView = View(this).apply {
                    setBackgroundColor(Color.argb(45, 255, 140, 0))
                }
                val lp = WindowManager.LayoutParams(
                    -1, -1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                try { wm.addView(eyeComfortView, lp) } catch (_: Exception) {}
            }
        } else {
            eyeComfortView?.let {
                try { wm.removeView(it) } catch (_: Exception) {}
                eyeComfortView = null
            }
        }
    }

    private fun openIntent(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(intent) } catch (_: Exception) {}
    }

    private fun addSpacer(parent: LinearLayout, hDp: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(hDp)))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
