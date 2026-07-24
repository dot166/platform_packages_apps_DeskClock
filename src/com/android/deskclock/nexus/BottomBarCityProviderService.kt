package com.android.deskclock.nexus

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder

class BottomBarCityProviderService : Service() {
    override fun onBind(intent: Intent?): IBinder {
        return BottomBarCityProvider(this, ComponentName(this, this::class.java))
    }
}