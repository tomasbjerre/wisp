package com.github.tomasbjerre.wisp

import android.app.Application
import android.content.Context
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.data.WispDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

/**
 * Manual, framework-free service locator. The app is small enough that a
 * DI framework would add more ceremony than it removes.
 */
class WispApplication : Application() {
    lateinit var repository: SessionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = WispDatabase.build(this)
        repository = SessionRepository(database.sessionDao(), database.trackPointDao())

        val osmdroidPrefs = getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        Configuration.getInstance().load(this, osmdroidPrefs)
        Configuration.getInstance().userAgentValue = packageName

        // See specs/tracking.md#what-must-survive-interruption.
        CoroutineScope(Dispatchers.IO).launch { repository.recoverUnfinishedSession() }
    }
}
