package com.android.buildAcc.util

import com.android.build.gradle.AppExtension
import com.android.buildAcc.constants.CONFIGURATIONS
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * @author ZhuPeipei
 * @date 2022/11/4 15:01
 */
const val ASSEMBLE = "assemble"
const val BUNDLE = "bundle"
const val AAR = "Aar"
const val MAVEN_TASK_PREFIX = "mavenBuildAccFor"

fun getAppProject(project: Project): Pair<AppExtension, Project>? {
    project.rootProject.allprojects.forEach {
        runCatching {
            it.extensions.getByType(AppExtension::class.java)?.let { appExtension ->
                return Pair(appExtension, it)
            }
        }
    }
    return null
}

fun getAppAssembleTask(project: Project, taskName: String): TaskProvider<Task>? {
    val (appExtension, appProject) = getAppProject(project) ?: return null
    return runCatching { appProject.tasks.named(taskName) }.getOrNull()
}

fun getBundleAarTask(project: Project, buildType: String): TaskProvider<Task>? {
    return runCatching { project.tasks.named(BUNDLE + buildType.capitalize() + AAR) }.getOrNull()
}

fun isAndroidPlugin(curProject: Project): Boolean {
    return curProject.plugins.hasPlugin("com.android.library")
}

fun isAppPlugin(curProject: Project): Boolean {
    return curProject.plugins.hasPlugin("com.android.application")
}

fun isJavaPlugin(curProject: Project): Boolean {
    return curProject.plugins.hasPlugin("java-library")
}

fun Project.getDefaultMavenGroupId(): String {
    return "com.buildAcc.${this.name}"
}

fun Project.getDefaultMavenArtifactId(buildTypeName: String): String {
    return "$name-$buildTypeName"
}

// 这里每次发布都会替换
fun Project.getDefaultMavenVersion(buildTypeName: String): String {
    return "1.0.0-$buildTypeName"
}

fun configurationList(project: Project, appExtension: AppExtension) =
    with(hashMapOf<String, Configuration>()) {
        CONFIGURATIONS.forEach { configuration ->
            runCatching { put(configuration, project.configurations.maybeCreate(configuration)) }

            appExtension.applicationVariants.forEach { applicationVariant ->
                runCatching {
                    val name =
                        applicationVariant.flavorName + applicationVariant.buildType.name.capitalize() + configuration.capitalize()
                    put(name, project.configurations.maybeCreate(name))
                }
                runCatching {
                    val name =
                        applicationVariant.buildType.name.capitalize() + configuration.capitalize()
                    put(name, project.configurations.maybeCreate(name))
                }
                runCatching {
                    val name =
                        applicationVariant.flavorName + configuration.capitalize()
                    put(name, project.configurations.maybeCreate(name))
                }
            }
        }
        values
    }

fun pathEquals(path: String, comparePath: String) =
    File(path).canonicalPath == File(comparePath).canonicalPath