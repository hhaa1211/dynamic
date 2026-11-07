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

    private lateinit var seekEyeSpacing: SeekBar
    private lateinit var seekYOffset: SeekBar
    private lateinit var seekNotchWidth: SeekBar
    private lateinit var seekNotchHeight: SeekBar

    private lateinit var tvEyeSpacingLabel: TextView
    private lateinit var tvYOffsetLabel: TextView
    private lateinit var tvWidthLabel: TextView
    private lateinit var tvHeightLabel: TextView

    private val prefs by lazy { getSharedPreferences("nusa_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updatePermissionButtons()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtons()
    }

    private fun initViews() {
        switchEnable = findViewById(R.id.switchEnableNotch)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantNotification = findViewById(R.id.btnGrantNotification)

        seekEyeSpacing = findViewById(R.id.seekEyeSpacing)
        seekYOffset = findViewById(R.id.seekYOffset)
        seekNotchWidth = findViewById(R.id.seekNotchWidth)
        seekNotchHeight = findViewById(R.id.seekNotchHeight)

        tvEyeSpacingLabel = findViewById(R.id.tvEyeSpacingLabel)
        tvYOffsetLabel = findViewById(R.id.tvYOffsetLabel)
        tvWidthLabel = findViewById(R.id.tvWidthLabel)
        tvHeightLabel = findViewById(R.id.tvHeightLabel)

        // Default calibrated values for Redmi Note 13 Pro 5G:
        // Eye spacing: 34dp (leaves clear gap around 13dp center punch hole)
        // Y-Offset: 6dp
        // Width: 110dp
        // Height: 36dp
        val currentSpacing = prefs.getInt("eye_spacing", 34)
        val currentY = prefs.getInt("y_offset", 6)
        val currentW = prefs.getInt("notch_width", 110)
        val currentH = prefs.getInt("notch_height", 36)

        seekEyeSpacing.progress = currentSpacing
        seekYOffset.progress = currentY
        seekNotchWidth.progress = currentW
        seekNotchHeight.progress = currentH

        tvEyeSpacingLabel.text = "Khoảng cách 2 mắt né camera: ${currentSpacing}dp"
        tvYOffsetLabel.text = "Vị trí từ mép trên (Y-Offset): ${currentY}dp"
        tvWidthLabel.text = "Độ rộng viên thuốc: ${currentW}dp"
        tvHeightLabel.text = "Chiều cao viên thuốc: ${currentH}dp"

        switchEnable.isChecked = true
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

        // Live Real-Time Calibration
        seekEyeSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvEyeSpacingLabel.text = "Khoảng cách 2 mắt né camera: ${progress}dp"
                prefs.edit().putInt("eye_spacing", progress).apply()
                sendConfigBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekYOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvYOffsetLabel.text = "Vị trí từ mép trên (Y-Offset): ${progress}dp"
                prefs.edit().putInt("y_offset", progress).apply()
                sendConfigBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekNotchWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvWidthLabel.text = "Độ rộng viên thuốc: ${progress}dp"
                prefs.edit().putInt("notch_width", progress).apply()
                sendConfigBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekNotchHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvHeightLabel.text = "Chiều cao viên thuốc: ${progress}dp"
                prefs.edit().putInt("notch_height", progress).apply()
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
        findViewById<Button>(R.id.btnTestCharge).setOnClickListener {
            sendStateBroadcast("CHARGING")
        }
    }

    private fun updatePermissionButtons() {
        val hasOverlay = Settings.canDrawOverlays(this)
        btnGrantOverlay.text = if (hasOverlay) "Đã cấp ✓" else "Cấp quyền"
        btnGrantOverlay.isEnabled = !hasOverlay

        val hasNotif = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        btnGrantNotification.text = if (hasNotif) "Đã cấp ✓" else "Cấp quyền"
        btnGrantNotification.isEnabled = !hasNotif
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
            putExtra("eye_spacing", prefs.getInt("eye_spacing", 34))
            putExtra("y_offset", prefs.getInt("y_offset", 6))
            putExtra("notch_width", prefs.getInt("notch_width", 110))
            putExtra("notch_height", prefs.getInt("notch_height", 36))
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
