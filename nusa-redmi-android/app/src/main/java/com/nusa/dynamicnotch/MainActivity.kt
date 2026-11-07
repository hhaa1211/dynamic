package com.nusa.dynamicnotch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var switchEnable: SwitchMaterial
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantNotification: Button
    private lateinit var btnGrantAccessibility: Button

    private lateinit var seekEyeSpacing: SeekBar
    private lateinit var seekEyeYOffset: SeekBar
    private lateinit var seekEyeSize: SeekBar
    private lateinit var seekYOffset: SeekBar
    private lateinit var seekNotchWidth: SeekBar
    private lateinit var seekNotchHeight: SeekBar

    private lateinit var tvEyeSpacingLabel: TextView
    private lateinit var tvEyeYOffsetLabel: TextView
    private lateinit var tvEyeSizeLabel: TextView
    private lateinit var tvYOffsetLabel: TextView
    private lateinit var tvWidthLabel: TextView
    private lateinit var tvHeightLabel: TextView

    private lateinit var btnPresetRedmi: Button
    private lateinit var btnPresetWide: Button
    private lateinit var btnPresetBelow: Button

    private val prefs by lazy { getSharedPreferences("nusa_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updatePermissionButtons()

        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        }
        // Apply config on start
        sendConfigBroadcast()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtons()
    }

    private fun initViews() {
        switchEnable = findViewById(R.id.switchEnableNotch)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantNotification = findViewById(R.id.btnGrantNotification)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)

        btnPresetRedmi = findViewById(R.id.btnPresetRedmi)
        btnPresetWide = findViewById(R.id.btnPresetWide)
        btnPresetBelow = findViewById(R.id.btnPresetBelow)

        seekEyeSpacing = findViewById(R.id.seekEyeSpacing)
        seekEyeYOffset = findViewById(R.id.seekEyeYOffset)
        seekEyeSize = findViewById(R.id.seekEyeSize)
        seekYOffset = findViewById(R.id.seekYOffset)
        seekNotchWidth = findViewById(R.id.seekNotchWidth)
        seekNotchHeight = findViewById(R.id.seekNotchHeight)

        tvEyeSpacingLabel = findViewById(R.id.tvEyeSpacingLabel)
        tvEyeYOffsetLabel = findViewById(R.id.tvEyeYOffsetLabel)
        tvEyeSizeLabel = findViewById(R.id.tvEyeSizeLabel)
        tvYOffsetLabel = findViewById(R.id.tvYOffsetLabel)
        tvWidthLabel = findViewById(R.id.tvWidthLabel)
        tvHeightLabel = findViewById(R.id.tvHeightLabel)

        // Calibrated defaults for Redmi Note 13 Pro 5G (Punch hole centered ~14dp diameter)
        val spacing = prefs.getInt("eye_spacing", 68)
        val eyeY = prefs.getInt("eye_y_offset", 0) // range: -15 to +25 -> progress 0 to 40
        val eyeSize = prefs.getInt("eye_size", 10)  // range: 6 to 18 -> progress 0 to 12
        val yOffset = prefs.getInt("y_offset", 2)
        val notchW = prefs.getInt("notch_width", 140)
        val notchH = prefs.getInt("notch_height", 36)

        seekEyeSpacing.progress = spacing
        seekEyeYOffset.progress = eyeY + 15
        seekEyeSize.progress = eyeSize - 6
        seekYOffset.progress = yOffset
        seekNotchWidth.progress = notchW
        seekNotchHeight.progress = notchH

        updateLabels(spacing, eyeY, eyeSize, yOffset, notchW, notchH)
        switchEnable.isChecked = true
    }

    private fun updateLabels(spacing: Int, eyeY: Int, eyeSize: Int, yOffset: Int, notchW: Int, notchH: Int) {
        tvEyeSpacingLabel.text = "Khoảng cách 2 mắt né camera: ${spacing}dp"
        tvEyeYOffsetLabel.text = "Vị trí mắt lên/xuống (Y-Offset): ${eyeY}dp"
        tvEyeSizeLabel.text = "Kích thước mắt: ${eyeSize}dp"
        tvYOffsetLabel.text = "Vị trí từ mép trên (Y-Offset): ${yOffset}dp"
        tvWidthLabel.text = "Độ rộng viên thuốc: ${notchW}dp"
        tvHeightLabel.text = "Chiều cao viên thuốc: ${notchH}dp"
    }

    private fun setupListeners() {
        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Vui lòng cấp quyền vẽ trên ứng dụng khác trước!", Toast.LENGTH_SHORT).show()
                    switchEnable.isChecked = false
                    requestOverlayPermission()
                    return@setOnCheckedChangeListener
                }
                startOverlayService()
            } else {
                stopOverlayService()
            }
        }

        btnGrantOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        btnGrantNotification.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Fast Preset 1: Né camera chuẩn Redmi Note 13 Pro 5G
        btnPresetRedmi.setOnClickListener {
            applyPreset(spacing = 68, eyeY = 0, eyeSize = 10, yOffset = 2, notchW = 140, notchH = 36)
            Toast.makeText(this, "Đã áp dụng: Né camera chuẩn Redmi Note 13 Pro 5G", Toast.LENGTH_SHORT).show()
        }

        // Fast Preset 2: Mắt to rõ né xa camera
        btnPresetWide.setOnClickListener {
            applyPreset(spacing = 76, eyeY = 0, eyeSize = 12, yOffset = 2, notchW = 148, notchH = 38)
            Toast.makeText(this, "Đã áp dụng: Mắt to né xa camera", Toast.LENGTH_SHORT).show()
        }

        // Fast Preset 3: Đặt mắt nằm dưới nốt ruồi camera
        btnPresetBelow.setOnClickListener {
            applyPreset(spacing = 36, eyeY = 10, eyeSize = 10, yOffset = 2, notchW = 126, notchH = 46)
            Toast.makeText(this, "Đã áp dụng: Mắt đặt phía dưới camera", Toast.LENGTH_SHORT).show()
        }

        // 1. Spacing SeekBar
        seekEyeSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = if (progress < 20) 20 else progress
                tvEyeSpacingLabel.text = "Khoảng cách 2 mắt né camera: ${clamped}dp"
                prefs.edit().putInt("eye_spacing", clamped).apply()
                sendConfigBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 2. Eye Y Offset SeekBar
        seekEyeYOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualY = progress - 15 // Range: -15 to +25
                tvEyeYOffsetLabel.text = "Vị trí mắt lên/xuống (Y-Offset): ${actualY}dp"
                prefs.edit().putInt("eye_y_offset", actualY).apply()
                sendConfigBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 3. Eye Size SeekBar
        seekEyeSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualSize = progress + 6 // Range: 6 to 18
                tvEyeSizeLabel.text = "Kích thước mắt: ${actualSize}dp"
                prefs.edit().putInt("eye_size", actualSize).apply()
                sendConfigBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 4. Y Offset SeekBar
        seekYOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvYOffsetLabel.text = "Vị trí từ mép trên (Y-Offset): ${progress}dp"
                prefs.edit().putInt("y_offset", progress).apply()
                sendConfigBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 5. Notch Width SeekBar
        seekNotchWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = if (progress < 80) 80 else progress
                tvWidthLabel.text = "Độ rộng viên thuốc: ${clamped}dp"
                prefs.edit().putInt("notch_width", clamped).apply()
                sendConfigBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 6. Notch Height SeekBar
        seekNotchHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = if (progress < 24) 24 else progress
                tvHeightLabel.text = "Chiều cao viên thuốc: ${clamped}dp"
                prefs.edit().putInt("notch_height", clamped).apply()
                sendConfigBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Quick test triggers
        findViewById<Button>(R.id.btnTestPet).setOnClickListener {
            sendStateBroadcast("PET")
        }
        findViewById<Button>(R.id.btnTestMusic).setOnClickListener {
            sendStateBroadcast("MUSIC")
        }
        findViewById<Button>(R.id.btnTestPomo).setOnClickListener {
            sendStateBroadcast("POMODORO")
        }
        findViewById<Button>(R.id.btnTestCharge).setOnClickListener {
            sendStateBroadcast("CHARGING")
        }
        findViewById<Button>(R.id.btnTestCall).setOnClickListener {
            sendStateBroadcast("CALL")
        }
        findViewById<Button>(R.id.btnTestMinimal).setOnClickListener {
            sendStateBroadcast("MINIMAL")
        }
        findViewById<Button>(R.id.btnTestNotif).setOnClickListener {
            sendStateBroadcast("NOTIFICATION")
        }
        findViewById<Button>(R.id.btnTestRinger).setOnClickListener {
            sendStateBroadcast("RINGER")
        }

        btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    private fun applyPreset(spacing: Int, eyeY: Int, eyeSize: Int, yOffset: Int, notchW: Int, notchH: Int) {
        seekEyeSpacing.progress = spacing
        seekEyeYOffset.progress = eyeY + 15
        seekEyeSize.progress = eyeSize - 6
        seekYOffset.progress = yOffset
        seekNotchWidth.progress = notchW
        seekNotchHeight.progress = notchH

        updateLabels(spacing, eyeY, eyeSize, yOffset, notchW, notchH)

        prefs.edit()
            .putInt("eye_spacing", spacing)
            .putInt("eye_y_offset", eyeY)
            .putInt("eye_size", eyeSize)
            .putInt("y_offset", yOffset)
            .putInt("notch_width", notchW)
            .putInt("notch_height", notchH)
            .apply()

        sendConfigBroadcast()
    }

    private fun updatePermissionButtons() {
        val hasOverlay = Settings.canDrawOverlays(this)
        btnGrantOverlay.text = if (hasOverlay) "Đã cấp ✓" else "Cấp quyền"
        btnGrantOverlay.isEnabled = !hasOverlay

        val hasNotif = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        btnGrantNotification.text = if (hasNotif) "Đã cấp ✓" else "Cấp quyền"
        btnGrantNotification.isEnabled = !hasNotif

        val hasAccess = isAccessibilityEnabled()
        btnGrantAccessibility.text = if (hasAccess) "Đã cấp ✓" else "Cấp quyền"
        btnGrantAccessibility.isEnabled = !hasAccess
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.contains(packageName)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startOverlayService() {
        val intent = Intent(this, NusaOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        stopService(Intent(this, NusaOverlayService::class.java))
    }

    private fun sendStateBroadcast(state: String) {
        val intent = Intent(NusaOverlayService.ACTION_SET_STATE).apply {
            putExtra("state", state)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun sendConfigBroadcast() {
        val intent = Intent(NusaOverlayService.ACTION_UPDATE_CONFIG).apply {
            putExtra("eye_spacing", prefs.getInt("eye_spacing", 68))
            putExtra("eye_y_offset", prefs.getInt("eye_y_offset", 0))
            putExtra("eye_size", prefs.getInt("eye_size", 10))
            putExtra("y_offset", prefs.getInt("y_offset", 2))
            putExtra("notch_width", prefs.getInt("notch_width", 140))
            putExtra("notch_height", prefs.getInt("notch_height", 36))
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
