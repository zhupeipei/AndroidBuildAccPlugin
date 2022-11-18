package com.android.buildAcc.handler

import com.android.buildAcc.constants.PROJECT_MAVEN_MAP
import com.android.buildAcc.util.ASSEMBLE
import com.android.buildAcc.util.MAVEN_TASK_PREFIX
import com.android.buildAcc.util.getAppAssembleTask
import com.android.buildAcc.util.getAppProject
import com.android.buildAcc.util.getBundleAarTask
import com.android.buildAcc.util.log
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * @author ZhuPeipei
 * @date 2022/11/3 20:41
 * 将子模块打包为aar
 */
class AarBuildHandler {
    fun handleAssembleTask(project: Project) {
        val (appExtension, appProject) = getAppProject(project) ?: return

        appExtension.applicationVariants.forEach { applicationVariant ->
            val assembleTaskProvider = getAppAssembleTask(
                appProject,
                ASSEMBLE + applicationVariant.flavorName.capitalize() + applicationVariant.buildType.name.capitalize()
            )
            val buildAccAfterAssembleTask = createAfterAssembleTask(project)
            assembleTaskProvider?.configure { assembleTask ->
                assembleTask.finalizedBy(buildAccAfterAssembleTask)
                project.rootProject.allprojects.forEach { project ->
                    val mavenFileExist =
                        PROJECT_MAVEN_MAP[project.name]?.mavenInfo?.modelExist ?: false
                    if (!mavenFileExist) {
                        val buildTypeName = applicationVariant.buildType.name
                        getBundleAarTask(project, buildTypeName)?.let { bundleAarTask ->
                            assembleTask.finalizedBy(bundleAarTask)
                            hookPublishToMavenTask(bundleAarTask, buildTypeName, project)
                        }
                    }
                }
            }
        }
    }

    private fun createAfterAssembleTask(project: Project): Task {
        val taskName = "buildAccAfterAssembleTask"
        val task = project.rootProject.tasks.findByName(taskName)
        if (task != null) {
            return task
        }
        val buildAccAfterAssembleTask = project.rootProject.task(taskName)
        buildAccAfterAssembleTask.doLast {
            log("=================================================================================================================================")
            log("=================================================================================================================================")
            log("=============================================buildAccAfterAssembleTask===========================================================")
            log("=================================================================================================================================")
            log("=================================================================================================================================")
        }
        return buildAccAfterAssembleTask
    }

    private val publishTaskSet = mutableSetOf<String>()

    private fun hookPublishToMavenTask(
        bundleAarTaskProvider: TaskProvider<Task>,
        buildTypeName: String,
        project: Project
    ) {
        val key = "${project.path}-${buildTypeName}"
        if (publishTaskSet.contains(key)) {
            return
        }
        publishTaskSet.add(key)
        val publicationName = "$MAVEN_TASK_PREFIX${buildTypeName.capitalize()}"

        val publishTask =
            runCatching { project.tasks.named("publish${publicationName.capitalize()}PublicationToMavenRepository") }.getOrNull()
                ?: return
        // PublicationToMavenRepository 暂未实现，PublicationToMavenLocal
        log("${publishTask.name} set executed after ${bundleAarTaskProvider.name} for project (${project.name})")
        val buildAccAfterBundleAarTask = createAfterBundleAarTask(project)
        bundleAarTaskProvider.configure { bundleAarTask ->
            bundleAarTask.finalizedBy(buildAccAfterBundleAarTask)
            bundleAarTask.finalizedBy(publishTask)
        }
    }

    private fun createAfterBundleAarTask(project: Project): Task {
        val taskName = "buildAccAfterBundleAarTask"
        val task = project.rootProject.tasks.findByName(taskName)
        if (task != null) {
            return task
        }
        val buildAccAfterBundleAarTask = project.rootProject.task(taskName)
        buildAccAfterBundleAarTask.doLast {
            log("=================================================================================================================================")
            log("=================================================================================================================================")
            log("============================================buildAccAfterBundleAarTask===========================================================")
            log("=================================================================================================================================")
            log("=================================================================================================================================")
        }
        return buildAccAfterBundleAarTask
    }
}