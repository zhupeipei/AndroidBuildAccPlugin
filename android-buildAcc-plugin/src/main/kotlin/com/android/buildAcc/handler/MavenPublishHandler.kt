package com.android.buildAcc.handler

import com.android.build.gradle.AppExtension
import com.android.buildAcc.constants.BUILD_TYPES
import com.android.buildAcc.constants.MAVEN_REPO_HTTP_URL
import com.android.buildAcc.constants.MAVEN_REPO_LOCAL_URL
import com.android.buildAcc.constants.PROJECT_MAVEN_MAP
import com.android.buildAcc.model.BundleInfo
import com.android.buildAcc.model.MavenInfo
import com.android.buildAcc.model.MavenModel
import com.android.buildAcc.model.RepoType
import com.android.buildAcc.util.MAVEN_TASK_PREFIX
import com.android.buildAcc.util.execCmd
import com.android.buildAcc.util.getCommitId
import com.android.buildAcc.util.getDefaultMavenArtifactId
import com.android.buildAcc.util.getDefaultMavenGroupId
import com.android.buildAcc.util.getLastModifiedTimeStamp
import com.android.buildAcc.util.getLocalMavenVersion
import com.android.buildAcc.util.getRemoteMavenVersion
import com.android.buildAcc.util.isAndroidPlugin
import com.android.buildAcc.util.isAppPlugin
import com.android.buildAcc.util.isJavaPlugin
import com.android.buildAcc.util.isNullOrEmpty
import com.android.buildAcc.util.log
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import java.io.File

/**
 * @author ZhuPeipei
 * @date 2022/11/4 14:53
 */
class MavenPublishHandler(private val mChangedModulesHandler: ChangedModulesHandler) {
    fun configRepository(rootProject: Project) {
        // 配置repository，这样在repository中能找到子模块打包的插件
//        if (rootProject.repositories.findByName("maven1000") == null) {
//            project.repositories.maven {
//                it.setUrl(MAVEN_REPO_DEFAULT_URL)
//                it.name = "maven1000"
//            }
//        }
    }

    fun applyMavenPublishPlugin(project: Project) {
        if (mChangedModulesHandler.needAccBuildBundle(project)) {
            applyMavenPublishPluginInternal(project)
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
                // 创建buildType
                val buildTypes = if (appExtension == null) BUILD_TYPES else
                    mutableSetOf<String>().apply {
                        appExtension.applicationVariants.forEach { applicationVariant ->
                            add(applicationVariant.buildType.name)
                        }
                    }
                val buildTypeName = buildTypes.first()
                // bundleInfo
                val bundleInfo =
                    createBundleInfo(subProject, buildTypeName) ?: return@configure
                val mavenInfo = bundleInfo.mavenInfo ?: return@configure
                val mavenModel = mavenInfo.mavenModel

                PROJECT_MAVEN_MAP[subProject.name] = bundleInfo

                publishingExt.publications { publicationContainer ->
                    val publicationName = "$MAVEN_TASK_PREFIX${buildTypeName.capitalize()}"
                    if (publicationContainer.findByName(publicationName) == null) {
                        publicationContainer.create(
                            publicationName,
                            MavenPublication::class.java
                        ) {
                            with(it) {
                                groupId = mavenModel.groupId
                                artifactId = mavenModel.artifactId
                                version = mavenModel.version
                                from(subProject.components.getByName(buildTypeName))
                            }
//                            with(it) {
//                                groupId = subProject.getDefaultMavenGroupId()
//                                artifactId = subProject.getDefaultMavenArtifactId(buildTypeName)
//                                version = subProject.getDefaultMavenVersion(buildTypeName)
//                                from(subProject.components.getByName(buildTypeName))
//                            }
//                            PROJECT_MAVEN_MAP[subProject.name] =
//                                MavenModel(it.groupId, it.artifactId, it.version)

                            log(
                                "project ${subProject.path}, maven{groupId=${it.groupId}," +
                                        " artifactId=${it.artifactId}, version=${it.version}," +
                                        " url=${mavenInfo.url}}"
                            )
                        }
                    }
                }
                publishingExt.repositories { repositoryHandler ->
                    repositoryHandler.maven {
                        it.setUrl(mavenInfo.url)
                    }
                }
            }
        }
    }

    private fun createBundleInfo(project: Project, buildTypeName: String): BundleInfo? {
        val bundleInfo = mChangedModulesHandler.getBundleInfo(project) ?: return null

        val result = project.execCmd("git -C ${project.projectDir.absolutePath} status --porcelain")
        if (result.isBlank() && !MAVEN_REPO_HTTP_URL.isNullOrEmpty()) {
            // 这里表示git目录是干净的，可以使用http上传模块
            val commitId = getCommitId(project)
            if (!commitId.isNullOrEmpty()) {
                val mavenModel = MavenModel(
                    groupId = project.getDefaultMavenGroupId(),
                    artifactId = project.getDefaultMavenArtifactId(buildTypeName),
                    version = project.getRemoteMavenVersion("$commitId", buildTypeName)
                )

                val cmdResult =
                    project.execCmd("curl -I ${MAVEN_REPO_HTTP_URL}${mavenModel.toPath()}")
                val mavenInfo = if (cmdResult.startsWith("HTTP/1.1 200 OK")) {
                    MavenInfo(RepoType.RepoNet, mavenModel, true)
                } else {
                    MavenInfo(RepoType.RepoNet, mavenModel, false)
                }
                bundleInfo.mavenInfo = mavenInfo
                return bundleInfo
            }
        }

        // 如果git获取信息失败，这里会转为本地依赖
        // 这里需要查询本地依赖
        val lastModifiedTime = project.getLastModifiedTimeStamp()
        val mavenModel = MavenModel(
            groupId = project.getDefaultMavenGroupId(),
            artifactId = project.getDefaultMavenArtifactId(buildTypeName),
            version = project.getLocalMavenVersion(buildTypeName, lastModifiedTime)
        )
        val modelExist = File(MAVEN_REPO_LOCAL_URL, mavenModel.toPath()).exists()
        val mavenInfo = MavenInfo(RepoType.RepoLocal, mavenModel, modelExist)
        bundleInfo.mavenInfo = mavenInfo

        log("bundleInfo=$bundleInfo")

        return bundleInfo
    }
}