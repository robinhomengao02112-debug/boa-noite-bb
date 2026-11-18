package com.sandbox.patchsim

import android.app.Application
import com.sandbox.patchsim.core.model.LogLevel
import com.sandbox.patchsim.di.ServiceLocator

class PatchSimApp : Application() {

    lateinit var services: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        services = ServiceLocator(this)
        services.logRepository.log(LogLevel.INFO, "App", "PatchSim Lab started (local sandbox mode)")
        SandboxSeeder.seedIfEmpty(services)
    }
}
