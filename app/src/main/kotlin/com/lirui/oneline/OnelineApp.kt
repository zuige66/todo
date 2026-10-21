package com.lirui.oneline

import android.app.Application
import com.lirui.oneline.reminder.NotificationHelper

class OnelineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create notification channels up front (idempotent).
        NotificationHelper.init(this)
    }
}