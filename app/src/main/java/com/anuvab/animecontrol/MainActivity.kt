package com.anuvab.animecontrol

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private val REQ_BG_IMAGE = 101
    private val REQ_CHIBI_IMAGE = 102
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("anime_prefs", Context.MODE_PRIVATE)

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0F18"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, dp(36), pad, pad)
        }

        // Title
        val title = TextView(this).apply {
            text = "ANIME CONTROL STUDIO"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val subtitle = TextView(this).apply {
            text = "Customize your pull-down Quick Settings with Anime art & neon controls"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(4), 0, dp(20))
        }
        container.addView(title)
        container.addView(subtitle)

        // Status Card
        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundBg(Color.parseColor("#1A1F35"), dp(16))
        }
        container.addView(statusText)
        addSpace(container, 16)

        // STEP 1: PERMISSIONS
        container.addView(sectionHeader("1. Activate Control Center"))

        container.addView(actionButton("Grant Brightness & Settings Permission", "#2563EB") {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        })
        addSpace(container, 10)

        container.addView(actionButton("Enable 'Anime Control Center' in Accessibility", "#E11D48") {
            Toast.makeText(this, "Find 'Anime Control Center' and turn it ON", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        addSpace(container, 10)

        container.addView(actionButton("Preview / Open Anime Panel Now", "#10B981") {
            val svc = AnimeControlService.instance
            if (svc != null) {
                svc.showControlPanel()
            } else {
                Toast.makeText(this, "Please enable Accessibility Service first!", Toast.LENGTH_SHORT).show()
            }
        })
        addSpace(container, 24)

        // STEP 2: ANIME ARTWORK
        container.addView(sectionHeader("2. Anime Wallpaper & Character Sticker"))

        container.addView(actionButton("Pick Full Anime Background Image (Gallery)", "#7C3AED") {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(Intent.createChooser(intent, "Select Anime Wallpaper"), REQ_BG_IMAGE)
        })
        addSpace(container, 10)

        container.addView(actionButton("Pick Bottom Anime Character / Chibi PNG", "#DB2777") {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(Intent.createChooser(intent, "Select Character Image"), REQ_CHIBI_IMAGE)
        })
        addSpace(container, 10)

        container.addView(actionButton("Reset to Built-In Cyberpunk Anime Theme", "#334155") {
            File(filesDir, "anime_bg.jpg").delete()
            File(filesDir, "anime_chibi.png").delete()
            Toast.makeText(this, "Reset to default theme!", Toast.LENGTH_SHORT).show()
        })
        addSpace(container, 24)

        // STEP 3: NEON ACCENT COLOR
        container.addView(sectionHeader("3. Active Toggle & Slider Color"))

        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val colors = listOf(
            "Realme Blue" to "#1E6FF9",
            "Cyber Pink" to "#FF2A85",
            "Solo Purple" to "#9D4EDD",
            "Neon Cyan" to "#00B4D8",
            "Crimson" to "#EF233C"
        )
        for ((name, hex) in colors) {
            val swatch = Button(this).apply {
                text = name
                textSize = 11f
                setTextColor(Color.WHITE)
                background = roundBg(Color.parseColor(hex), dp(12))
                val lp = LinearLayout.LayoutParams(0, dp(48), 1f)
                lp.setMargins(dp(4), 0, dp(4), 0)
                layoutParams = lp
                setOnClickListener {
                    prefs.edit().putString("accent_hex", hex).apply()
                    Toast.makeText(this@MainActivity, "Color set to $name!", Toast.LENGTH_SHORT).show()
                }
            }
            colorRow.addView(swatch)
        }
        container.addView(colorRow)
        addSpace(container, 24)

        // STEP 4: BACKGROUND DIMMING SLIDER
        val currentDim = prefs.getInt("bg_dim", 130)
        val dimLabel = sectionHeader("4. Wallpaper Darkness Overlay: ${(currentDim * 100) / 255}%")
        container.addView(dimLabel)

        val dimSeek = SeekBar(this).apply {
            max = 220
            progress = currentDim
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    dimLabel.text = "4. Wallpaper Darkness Overlay: ${(p * 100) / 255}%"
                    prefs.edit().putInt("bg_dim", p).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        container.addView(dimSeek)
        addSpace(container, 40)

        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    override fun onResume() {
        super.onResume()
        val canWrite = Settings.System.canWrite(this)
        val svcRunning = AnimeControlService.instance != null
        statusText.text = "Service Status:\n" +
            "• Brightness Control Permission: ${if (canWrite) "READY" else "NEEDS PERMISSION"}\n" +
            "• Top-Right Swipe Overlay Service: ${if (svcRunning) "ACTIVE (Swipe down from top-right!)" else "INACTIVE (Tap button 2 below)"}"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            val targetFile = if (requestCode == REQ_BG_IMAGE) {
                File(filesDir, "anime_bg.jpg")
            } else {
                File(filesDir, "anime_chibi.png")
            }
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(this, "Anime image saved! Swipe down from top-right to see it.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sectionHeader(title: String) = TextView(this).apply {
        text = title
        textSize = 15f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.parseColor("#E2E8F0"))
        setPadding(0, 0, 0, dp(10))
    }

    private fun actionButton(label: String, colorHex: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = roundBg(Color.parseColor(colorHex), dp(14))
        setPadding(dp(16), dp(14), dp(16), dp(14))
        setOnClickListener { onClick() }
    }

    private fun roundBg(color: Int, radiusPx: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx.toFloat()
    }

    private fun addSpace(parent: LinearLayout, heightDp: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
