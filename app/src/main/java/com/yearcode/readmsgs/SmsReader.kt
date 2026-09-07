package com.yearcode.readmsgs

import android.content.Context
import android.net.Uri
import android.provider.Telephony

/** 一条短信 */
data class SmsItem(
    val id: Long,
    val address: String,
    val date: Long,
    val body: String,
)

/** 读取短信收件箱 */
object SmsReader {

    private val SMS_INBOX_URI: Uri = Uri.parse("content://sms/inbox")

    /**
     * 读取时间戳大于 afterMillis 的短信（按时间正序）
     * @throws SecurityException 没有 READ_SMS 权限时抛出
     */
    fun readNewerThan(ctx: Context, afterMillis: Long): List<SmsItem> {
        val result = mutableListOf<SmsItem>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY,
        )
        ctx.contentResolver.query(
            SMS_INBOX_URI,
            projection,
            "${Telephony.Sms.DATE} > ?",
            arrayOf(afterMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            while (cursor.moveToNext()) {
                result.add(
                    SmsItem(
                        id = cursor.getLong(idCol),
                        address = cursor.getString(addrCol) ?: "",
                        date = cursor.getLong(dateCol),
                        body = cursor.getString(bodyCol) ?: "",
                    )
                )
            }
        }
        return result
    }
}
