package com.android.buildAcc.util

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle

/**
 * @author ZhuPeipei
 * @date 2022/11/3 10:20
 */
open class BuildListenerWrapper : BuildListener {
    fun buildStarted(gradle: Gradle) {
        log("buildStarted")
    }

    override fun settingsEvaluated(setting: Settings) {
        log("settingsEvaluated")
    }

    override fun projectsLoaded(gradle: Gradle) {
        log("projectsLoaded")
    }

    override fun projectsEvaluated(gradle: Gradle) {
        log("projectsEvaluated")
    }

    override fun buildFinished(buildResult: BuildResult) {
        log("buildFinished")
    }
}