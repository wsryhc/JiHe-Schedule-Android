package com.example.jihe_schedule.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.jihe_schedule.widget.NativeScheduleWidget

object WidgetHelper {
    fun refreshNow(context: Context) {
        try {
            Log.d("JiHeNative", "👊 [Helper] 发送全局刷新广播...")

            // 发送强制刷新广播给 NativeScheduleWidget
            val intent = Intent(context, NativeScheduleWidget::class.java)
            intent.action = NativeScheduleWidget.FORCE_UPDATE
            context.sendBroadcast(intent)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}