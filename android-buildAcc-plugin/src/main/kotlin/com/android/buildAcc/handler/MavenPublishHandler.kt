package com.android.buildAcc.handler

import com.android.build.gradle.AppExtension
import com.android.buildAcc.constants.BUILD_TYPES
import com.android.buildAcc.constants.MAVEN_REPO_DEFAULT_URL
import com.android.buildAcc.constants.PROJECT_MAVEN_MAP
import com.android.buildAcc.model.MavenModel
import com.android.buildAcc.util.MAVEN_TASK_PREFIX
import com.android.buildAcc.util.getDefaultMavenArtifactId
import com.android.buildAcc.util.getDefaultMavenGroupId
import com.android.buildAcc.util.getDefaultMavenVersion
import com.android.buildAcc.util.isAndroidPlugin
import com.android.buildAcc.util.isAppPlugin
import com.android.buildAcc.util.isJavaPlugin
import com.android.buildAcc.util.log
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

/**
 * @author ZhuPeipei
 * @date 2022/11/4 14:53
 */
class MavenPublishHandler {
    fun configRepository(rootProject: Project) {
        // 配置repository，这样在repository中能找到子模块打包的插件
//        if (rootProject.repositories.findByName("maven1000") == null) {
//            project.repositories.maven {
//                it.setUrl(MAVEN_REPO_DEFAULT_URL)
//                it.name = "maven1000"
//            }
//        }
    }

    // 1. 目前所有子project都会打包为aar
    // 2. AppPlugin不需要打包为aar，java后续再做支持
    fun applyMavenPublishPlugin(project: Project) {
        if (project == project.rootProject) {
            return
        } else if (isAppPlugin(project) || isJavaPlugin(project)) {
            return
        } else if (isAndroidPlugin(project)) {
            applyMavenPublishPluginInternal(project)
            return
        }
    }

    private fun applyMavenPublishPluginInternal(project: Project) {
        project.afterEvaluate {
            if (!project.pluginManager.hasPlugin("maven-publish")) {
                project.pluginManager.apply("maven-publish")
            } else {
                log("当前模块已经添加了maven-publish插件，将被替换")
            }
            log("project ${project.name} plugin (maven-publish) has applied")
        }
    }

    fun configSubProjectMavenPublishPlugin(project: Project) {
        configSubProjectMavenPublishPlugin(project, null)
    }

    private fun configSubProjectMavenPublishPlugin(project: Project, appExtension: AppExtension?) {
        if (project == project.rootProject) {
            return
        } else if (isAppPlugin(project) || isJavaPlugin(project)) {
            return
        } else if (isAndroidPlugin(project)) {
            configMavenPublish(project, appExtension)
            return
        }
    }

    // 不管有没有publish的插件，这里都会设置一个特有的publication，然后再后面的任务去依赖他
    // 上传的插件设置有两个问题
    // 1. apply插件时必须在project evaluated之前，因而在app下依赖插件会出现一些子模块已经依赖完成的情况
    // 2. 对于模块A依赖模块B，两个模块都需要按照buildType打包，这里因为模块A上传时无法判断应该
    // 使用模块B的哪个buildType（可以理解为maven插件的一个bug）， 因此在依赖插件时，只能设置单个buildType。
    // 对于上层可以理解为./gradlew :app:assemble这个执行只能执行
    // mavenDebugToMavenLoadRepository和mavenReleaseToMavenLoadRepository中的一个
    // 具体可以参考（https://stackoverflow.com/questions/51247830/publishing-is-not-able-to-resolve-a-dependency-on-a-project-with-multiple-public）
    private fun configMavenPublish(subProject: Project, appExtension: AppExtension?) {
        //if (!subProject.pluginManager.hasPlugin("maven-publish")) {
        //    subProject.pluginManager.apply("maven-publish")
        //}
        subProject.afterEvaluate {
            if (!subProject.pluginManager.hasPlugin("maven-publish")) {
                return@afterEvaluate
            }
            log("project ${subProject.name} --- configMavenPublish")
            subProject.extensions.configure<PublishingExtension>("publishing") { publishingExt ->
                publishingExt.publications { publicationContainer ->
                    val buildTypes = if (appExtension == null) BUILD_TYPES else
                        mutableSetOf<String>().apply {
                            appExtension.applicationVariants.forEach { applicationVariant ->
                                add(applicationVariant.buildType.name)
                            }
                        }
                    val buildTypeName = buildTypes.first()
                    val publicationName = "$MAVEN_TASK_PREFIX${buildTypeName.capitalize()}"
                    if (publicationContainer.findByName(publicationName) == null) {
                        publicationContainer.create(
                            publicationName,
                            MavenPublication::class.java
                        ) {
                            with(it) {
                                groupId = subProject.getDefaultMavenGroupId()
                                artifactId = subProject.getDefaultMavenArtifactId(buildTypeName)
                                version = subProject.getDefaultMavenVersion(buildTypeName)
                                from(subProject.components.getByName(buildTypeName))
                            }
                            PROJECT_MAVEN_MAP[subProject.name] =
                                MavenModel(it.groupId, it.artifactId, it.version)

                            log("project ${subProject.path}, maven{groupId=${it.groupId}," +
                                    " artifactId=${it.artifactId}, version=${it.version}," +
                                    " url=${MAVEN_REPO_DEFAULT_URL}}")
                        }
                    }
                }
                publishingExt.repositories { repositoryHandler ->
                    repositoryHandler.maven {
                        it.setUrl(MAVEN_REPO_DEFAULT_URL)
                    }
                }
            }
        }
    }
}