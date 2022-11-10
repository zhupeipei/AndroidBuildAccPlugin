package com.android.buildAcc.plugin

import com.android.buildAcc.handler.BuildTimeCostHandler
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * @author ZhuPeipei
 * @date 2022/11/9 21:52
 */
class BuildTimeCostPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.gradle.addListener(BuildTimeCostHandler().taskExecutionListener)
    }
}