package com.tva.missminutes

import android.app.Application
import android.util.Log

class MissMinutesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MissMinutes", "TVA Assistant initializing... All timelines must comply.")
    }
}
