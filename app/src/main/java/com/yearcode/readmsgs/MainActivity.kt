package com.yearcode.readmsgs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etFromEmail: EditText
    private lateinit var etToEmails: EditText
    private lateinit var etSmtpHost: EditText
    private lateinit var etSmtpPort: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var spSecurity: Spinner
    private lateinit var tvStatus: TextView
    private lateinit var btnSaveStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnTest: Button
    private lateinit var btnOpenSettings: Button

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.READ_SMS] == true) {
                startForwardService()
            } else {
                tvStatus.text = "未授予短信读取权限，无法转发"
                btnOpenSettings.visibility = Button.VISIBLE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadConfigToViews()
        setupSecuritySpinner()

        btnSaveStart.setOnClickListener { onSaveAndStart() }
        btnStop.setOnClickListener { onStop() }
        btnTest.setOnClickListener { onTest() }
        btnOpenSettings.setOnClickListener { openAppSettings() }
    }

    private fun bindViews() {
        etFromEmail = findViewById(R.id.et_from_email)
        etToEmails = findViewById(R.id.et_to_emails)
        etSmtpHost = findViewById(R.id.et_smtp_host)
        etSmtpPort = findViewById(R.id.et_smtp_port)
        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        spSecurity = findViewById(R.id.sp_security)
        tvStatus = findViewById(R.id.tv_status)
        btnSaveStart = findViewById(R.id.btn_save_start)
        btnStop = findViewById(R.id.btn_stop)
        btnTest = findViewById(R.id.btn_test)
        btnOpenSettings = findViewById(R.id.btn_open_settings)
    }

    private fun setupSecuritySpinner() {
        val labels = MailSecurity.values().map { it.label }
        spSecurity.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun loadConfigToViews() {
        val cfg = AppConfigStore.load(this)
        etFromEmail.setText(cfg.fromEmail)
        etToEmails.setText(cfg.toEmails)
        etSmtpHost.setText(cfg.smtpHost)
        etSmtpPort.setText(cfg.smtpPort)
        etUsername.setText(cfg.username)
        etPassword.setText(cfg.password)
        spSecurity.setSelection(MailSecurity.values().indexOf(cfg.security).coerceAtLeast(0))
    }

    private fun collectConfig(): AppConfig {
        val idx = spSecurity.selectedItemPosition.coerceIn(0, MailSecurity.values().size - 1)
        return AppConfig(
            fromEmail = etFromEmail.text.toString().trim(),
            toEmails = etToEmails.text.toString().trim(),
            username = etUsername.text.toString().trim(),
            password = etPassword.text.toString(),
            smtpHost = etSmtpHost.text.toString().trim(),
            smtpPort = etSmtpPort.text.toString().trim(),
            security = MailSecurity.values()[idx],
        )
    }

    private fun onSaveAndStart() {
        val cfg = collectConfig()
        if (!cfg.isValid()) {
            Toast.makeText(this, "请完整填写发件人/收件人/SMTP服务器/账号密码", Toast.LENGTH_LONG).show()
            return
        }
        AppConfigStore.save(this, cfg)
        AppConfigStore.setEnabled(this, true)
        requestNeededPermissions()
    }

    private fun onStop() {
        AppConfigStore.setEnabled(this, false)
        SmsForwardService.stop(this)
        tvStatus.text = "服务已停止"
        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show()
    }

    private fun onTest() {
        val cfg = collectConfig()
        if (!cfg.isValid()) {
            Toast.makeText(this, "请先完整填写邮箱配置", Toast.LENGTH_LONG).show()
            return
        }
        tvStatus.text = "正在发送测试邮件…"
        Thread {
            val ok = runCatching {
                MailSender.send(cfg, "read_msgs SMTP 配置测试", "测试邮件发送成功")
            }
            runOnUiThread {
                if (ok.isSuccess) {
                    tvStatus.text = "测试邮件发送成功"
                    Toast.makeText(this, "测试邮件发送成功", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatus.text = "测试失败：${ok.exceptionOrNull()?.message}"
                }
            }
        }.start()
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val need = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isEmpty()) {
            startForwardService()
        } else {
            requestPermissions.launch(need.toTypedArray())
        }
    }

    private fun startForwardService() {
        try {
            SmsForwardService.start(this)
            tvStatus.text = "服务已启动：每 1 分钟检查一次新短信"
            btnOpenSettings.visibility = Button.INVISIBLE
        } catch (e: Exception) {
            tvStatus.text = "启动失败：${e.message}"
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        tvStatus.text = AppConfigStore.lastResult(this).ifBlank {
            if (AppConfigStore.isEnabled(this)) "服务配置已启用" else "当前未启动"
        }
        btnOpenSettings.visibility =
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) Button.VISIBLE else Button.INVISIBLE
    }
}
