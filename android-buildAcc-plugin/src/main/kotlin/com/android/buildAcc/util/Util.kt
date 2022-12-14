package com.android.buildAcc.util

import com.android.build.gradle.AppExtension
import com.android.buildAcc.constants.CONFIGURATIONS
import com.android.buildAcc.constants.WHITE_LIST_FILE
import com.android.buildAcc.constants.WHITE_LIST_FOLDER
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author ZhuPeipei
 * @date 2022/11/4 15:01
 */
const val ASSEMBLE = "assemble"
const val BUNDLE = "bundle"
const val AAR = "Aar"
const val MAVEN_TASK_PREFIX = "mavenBuildAccFor"
const val MAVEN_TASK_PREFIX_FOR_LOCAL_DEPENDENCY = "mavenBuildAccInLocalDependency"

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

private val mDateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())

fun Project.getRemoteMavenVersion(commitId: String, buildTypeName: String): String {
//    return "${mDateFormat.format(Calendar.getInstance())}-$buildTypeName-$commitId"
    return "$buildTypeName-$commitId"
}

fun Project.getLocalMavenVersion(buildTypeName: String, lastModifiedTime: Long): String {
    return "1.0.0-$buildTypeName-$lastModifiedTime"
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

fun Project.execCmd(cmd: String): String {
    try {
        val stdOut = ByteArrayOutputStream()
        project.exec {
            it.executable = "sh"
            it.args = listOf("-c", cmd)
            it.standardOutput = stdOut
            it.errorOutput = stdOut
        }
        val result = stdOut.toString()
        log(
            "cmd = $cmd, result = ${
                if (result.length > 100) result.substring(
                    0,
                    100
                ) + "..." else result
            }"
        )
        return result
    } catch (e: Exception) {
        return ""
    }
}

fun String?.isNullOrEmpty() = this == null || this.isEmpty()

fun getCommitId(project: Project): String? {
    val projectDir = project.projectDir.absolutePath
    val commitRes = project.execCmd("cd $projectDir && git log | head -n 1")
    if (commitRes.startsWith("commit ")) {
        return commitRes.substring(7).replace("\n", "")
    }
    return null
}

class MyPair(var path: String, var time: Long)

fun Project.getLastModifiedTimeStamp(): Long {
    val path = this.projectDir.absolutePath
    val pair = MyPair("", 0)
    queryFolderLastModifiedTimeStamp(File(path), pair)
    log("project $name last update: ${pair.path}, ${pair.time}")
    return pair.time
}

private fun queryFolderLastModifiedTimeStamp(file: File, pair: MyPair) {
    if (file.isDirectory) {
        if (file.name.contains("build") || file.startsWith(".") || inWhiteListFolder(file.name)) {
            return
        }
        file.listFiles().forEach {
            queryFolderLastModifiedTimeStamp(it, pair)
        }
    } else if (!inWhiteListFile(file.name) || file.startsWith(".")) {
        if (pair.time < file.lastModified()) {
            pair.time = file.lastModified()
            pair.path = file.absolutePath
        }
    }
}

fun inWhiteListFolder(folderName: String) = WHITE_LIST_FOLDER.contains(folderName)

fun inWhiteListFile(fileName: String) = WHITE_LIST_FILE.contains(fileName)

fun Project.getLastModifiedTimeStampSimple(): Long {
    val path = this.projectDir.absolutePath
    return queryFolderLastModifiedTimeStampSimple(File(path))
}

private fun queryFolderLastModifiedTimeStampSimple(file: File): Long {
    if (file.isDirectory) {
        if (file.name.contains("build") || file.startsWith(".")) {
            return 0
        }
        var lastModifiedTime = 0L
        file.listFiles().forEach {
            val tmp = queryFolderLastModifiedTimeStampSimple(it)
            if (tmp > lastModifiedTime) {
                lastModifiedTime = tmp
            }
        }
        return lastModifiedTime
    } else {
        return file.lastModified()
    }
}