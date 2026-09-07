package com.yearcode.readmsgs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 前台服务：每 1 分钟轮询一次短信收件箱，
 * 把 boundary 之后收到的新短信通过 SMTP 邮件转发到指定邮箱。
 *
 * 采用前台服务的原因：Android 8+ 后台进程在后台无法自由执行，
 * WorkManager 最短周期为 15 分钟，无法满足 1 分钟一次的要求。
 */
class SmsForwardService : Service() {

    companion object {
        private const val CHANNEL_ID = "sms_forward_channel"
        private const val NOTIFY_ID = 1001

        private const val ACTION_START = "com.yearcode.readmsgs.action.START"
        private const val ACTION_STOP = "com.yearcode.readmsgs.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, SmsForwardService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SmsForwardService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private var workerThread: HandlerThread? = null
    private var handler: Handler? = null
    private val intervalMs = INTERVAL_MS

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AppConfigStore.setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!AppConfigStore.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()
        ensureLoop()
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "短信转发服务",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)

        val notification = buildNotification(AppConfigStore.lastResult(this).ifBlank { "运行中" })
        startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setContentTitle("短信转发服务运行中")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFY_ID, buildNotification(text))
    }

    private fun ensureLoop() {
        if (handler == null) {
            workerThread = HandlerThread("sms-forward-thread").also { it.start() }
            handler = Handler(workerThread!!.looper)
        }
        handler?.removeCallbacksAndMessages(null)
        handler?.postDelayed(::runCheck, 0L)
    }

    private fun runCheck() {
        if (!AppConfigStore.isEnabled(this)) {
            stopSelf()
            return
        }
        try {
            val config = AppConfigStore.load(this)
            val boundary = AppConfigStore.boundary(this)

            if (boundary <= 0L) {
                // 第一次启动：以当前时间作为边界，只转发之后收到的新短信
                AppConfigStore.setBoundary(this, System.currentTimeMillis())
                AppConfigStore.setLastResult(this, "已就绪：从现在开始监视新短信")
                updateNotification(AppConfigStore.lastResult(this))
            } else {
                val items = SmsReader.readNewerThan(this, boundary)

                if (items.isEmpty()) {
                    // 没有新短信，不需要发信
                } else if (!config.isValid()) {
                    AppConfigStore.setLastResult(this, "配置不完整，未发送（共 ${items.size} 条待转发）")
                    updateNotification(AppConfigStore.lastResult(this))
                } else {
                    val body = buildMailBody(items)
                    val subject = "短信转发（${items.size} 条）"
                    MailSender.send(config, subject, body)

                    // 发送成功后才推进边界，避免丢短信；失败则下轮重发
                    val newBoundary = items.maxOf { it.date } + 1
                    AppConfigStore.setBoundary(this, newBoundary)
                    AppConfigStore.setLastResult(this, "已转发 ${items.size} 条新短信 ${nowText()}")
                    updateNotification(AppConfigStore.lastResult(this))
                }
            }
        } catch (e: SecurityException) {
            AppConfigStore.setLastResult(this, "缺少短信读取权限，请重新授权")
            updateNotification(AppConfigStore.lastResult(this))
        } catch (e: Exception) {
            AppConfigStore.setLastResult(this, "转发失败：${e.message ?: e.javaClass.simpleName}")
            updateNotification(AppConfigStore.lastResult(this))
        }

        if (AppConfigStore.isEnabled(this)) {
            handler?.postDelayed(::runCheck, intervalMs)
        }
    }

    private fun buildMailBody(items: List<SmsItem>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("共 ${items.size} 条短信：")
            appendLine()
            items.forEachIndexed { index, item ->
                append("【${index + 1}】")
                appendLine("发件人: ${item.address}")
                appendLine("时间: ${fmt.format(Date(item.date))}")
                appendLine("内容: ${item.body}")
                appendLine()
            }
        }
    }

    private fun nowText(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onDestroy() {
        handler?.removeCallbacksAndMessages(null)
        workerThread?.quitSafely()
        handler = null
        workerThread = null
        super.onDestroy()
    }
}
