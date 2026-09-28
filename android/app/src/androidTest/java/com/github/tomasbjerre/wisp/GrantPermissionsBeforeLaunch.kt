package com.github.tomasbjerre.wisp

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/**
 * Grants runtime permissions to the app under test before anything else in the rule chain runs —
 * put it outermost, ahead of the compose rule, so they are already granted when the activity
 * launches. Granting a running app's permissions can restart it, which is why the other tests
 * that grant them mid-test wait for idle afterwards.
 */
class GrantPermissionsBeforeLaunch(
    private vararg val permissions: String,
) : ExternalResource() {
    override fun before() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(packageName, it) }
    }
}
