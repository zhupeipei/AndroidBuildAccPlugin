package com.android.buildAcc.plugin

import com.android.buildAcc.constants.MAVEN_REPO_LOCAL_URL
import com.android.buildAcc.model.BuildAccRepoExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.invocation.DefaultGradle
import org.jetbrains.kotlin.konan.file.File

/**
 * @author ZhuPeipei
 * @date 2022/11/5 19:35
 */
private class BuildAccRepoApplyPlugin : Plugin<Project> {
    private var mBuildAccRepoExtension: BuildAccRepoExtension? = null

    override fun apply(project: Project) {
        val buildAccRepoExtension =
            project.extensions.create("BuildAccRepoExtension", BuildAccRepoExtension::class.java)
        mBuildAccRepoExtension = buildAccRepoExtension

        if (buildAccRepoExtension == null || buildAccRepoExtension.repo.isEmpty()) {
            throw RuntimeException("BuildAccRepoExtension 未配置，请检查")
        }

        MAVEN_REPO_LOCAL_URL = if (buildAccRepoExtension.repo.startsWith("./")) {
            "${project.rootProject.projectDir}${File.separator}${
                buildAccRepoExtension.repo.substring(2)
            }"
        } else buildAccRepoExtension.repo

        // 对setting模块配置repository，这样在repository中能找到子模块打包的插件
        val gradle = project.rootProject.gradle as DefaultGradle
        project.afterEvaluate {
            gradle.settings.dependencyResolutionManagement.repositories.maven {
                it.setUrl(buildAccRepoExtension.repo)
            }
        }
    }
}