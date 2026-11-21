package com.richi_mc.hackathon3b

import android.app.Application
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.app.DefaultAppContainer

class Validat3BApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
