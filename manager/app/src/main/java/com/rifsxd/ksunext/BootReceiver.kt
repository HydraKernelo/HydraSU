package com.rifsxd.ksunext

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // HydraSU: 寄生控制台只在管理器存活（前台/后台）时可用 —— 开机不自启，属设计行为
    }
}
