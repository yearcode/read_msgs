package com.yearcode.readmsgs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机/升级后自动拉起转发服务 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!AppConfigStore.isEnabled(context)) {
            return
        }
        try {
            SmsForwardService.start(context)
        } catch (_: Exception) {
            // 个别厂商限制后台启动前台服务时静默失败，由用户手动打开 App 即可恢复
        }
    }
}
