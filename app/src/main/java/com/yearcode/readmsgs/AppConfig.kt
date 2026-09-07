package com.yearcode.readmsgs

import android.content.Context
import android.content.SharedPreferences

/** 轮询间隔：1 分钟 */
const val INTERVAL_MS = 60_000L

/** 邮件加密方式 */
enum class MailSecurity(val id: String, val label: String) {
    NONE("none", "无加密"),
    SSL("ssl", "SSL（隐式TLS，常用465）"),
    TLS("tls", "TLS/STARTTLS（常用587）");

    companion object {
        fun fromId(id: String?): MailSecurity =
            values().firstOrNull { it.id == id } ?: SSL
    }
}

/** 应用配置 */
data class AppConfig(
    val fromEmail: String = "",
    val toEmails: String = "",
    val username: String = "",
    val password: String = "",
    val smtpHost: String = "",
    val smtpPort: String = "465",
    val security: MailSecurity = MailSecurity.SSL,
) {
    fun isValid(): Boolean =
        fromEmail.isNotBlank() &&
            toEmails.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            smtpHost.isNotBlank() &&
            smtpPort.isNotBlank()
}

/** 配置与运行状态存储（SharedPreferences） */
object AppConfigStore {

    private const val PREF_NAME = "read_msgs_config"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_BOUNDARY = "boundary_ms"   // 上次成功转发到的短信时间戳，用于增量读取
    private const val KEY_LAST_RESULT = "last_result"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(ctx: Context): AppConfig {
        val p = prefs(ctx)
        return AppConfig(
            fromEmail = p.getString("from_email", "") ?: "",
            toEmails = p.getString("to_emails", "") ?: "",
            username = p.getString("username", "") ?: "",
            password = p.getString("password", "") ?: "",
            smtpHost = p.getString("smtp_host", "") ?: "",
            smtpPort = p.getString("smtp_port", "465") ?: "465",
            security = MailSecurity.fromId(p.getString("security", MailSecurity.SSL.id)),
        )
    }

    fun save(ctx: Context, cfg: AppConfig) {
        prefs(ctx).edit()
            .putString("from_email", cfg.fromEmail)
            .putString("to_emails", cfg.toEmails)
            .putString("username", cfg.username)
            .putString("password", cfg.password)
            .putString("smtp_host", cfg.smtpHost)
            .putString("smtp_port", cfg.smtpPort)
            .putString("security", cfg.security.id)
            .apply()
    }

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun boundary(ctx: Context): Long = prefs(ctx).getLong(KEY_BOUNDARY, 0L)

    fun setBoundary(ctx: Context, value: Long) =
        prefs(ctx).edit().putLong(KEY_BOUNDARY, value).apply()

    fun lastResult(ctx: Context): String = prefs(ctx).getString(KEY_LAST_RESULT, "") ?: ""

    fun setLastResult(ctx: Context, text: String) =
        prefs(ctx).edit().putString(KEY_LAST_RESULT, text).apply()
}
