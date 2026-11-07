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
    private lateinit var seekYOffset: SeekBar
    private lateinit var seekNotchWidth: SeekBar
    private lateinit var tvYOffsetLabel: TextView
    private lateinit var tvWidthLabel: TextView

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
        seekYOffset = findViewById(R.id.seekYOffset)
        seekNotchWidth = findViewById(R.id.seekNotchWidth)
        tvYOffsetLabel = findViewById(R.id.tvYOffsetLabel)
        tvWidthLabel = findViewById(R.id.tvWidthLabel)

        // Load saved offsets for Redmi Note 13 Pro 5G
        val currentY = prefs.getInt("y_offset", 18)
        val currentW = prefs.getInt("notch_width", 92)

        seekYOffset.progress = currentY
        seekNotchWidth.progress = currentW
        tvYOffsetLabel.text = "Khoảng cách từ mép trên màn hình (Y-Offset): ${currentY}dp"
        tvWidthLabel.text = "Độ rộng viên thuốc lúc đóng: ${currentW}dp"
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

        seekYOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvYOffsetLabel.text = "Khoảng cách từ mép trên màn hình (Y-Offset): ${progress}dp"
                prefs.edit().putInt("y_offset", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                restartServiceIfRunning()
            }
        })

        seekNotchWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvWidthLabel.text = "Độ rộng viên thuốc lúc đóng: ${progress}dp"
                prefs.edit().putInt("notch_width", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                restartServiceIfRunning()
            }
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

    private fun restartServiceIfRunning() {
        if (switchEnable.isChecked) {
            stopOverlayService()
            startOverlayService()
        }
    }

    private fun sendStateBroadcast(state: String) {
        val intent = Intent(NusaOverlayService.ACTION_SET_STATE).apply {
            putExtra("state", state)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
