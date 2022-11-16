package com.android.buildAcc.plugin

import com.android.build.gradle.AppExtension
import com.android.buildAcc.constants.BUILD_TYPES
import com.android.buildAcc.constants.MAVEN_REPO_HTTP_URL
import com.android.buildAcc.constants.MAVEN_REPO_LOCAL_URL
import com.android.buildAcc.handler.AarBuildHandler
import com.android.buildAcc.handler.BuildTimeCostHandler
import com.android.buildAcc.handler.ChangedModulesHandler
import com.android.buildAcc.handler.LocalDependencyUploadHandler
import com.android.buildAcc.handler.MavenPublishHandler
import com.android.buildAcc.handler.ReplaceDependencyHandler
import com.android.buildAcc.model.BuildAccExtension
import com.android.buildAcc.util.BuildListenerWrapper
import com.android.buildAcc.util.ProjectEvaluationListenerWrapper
import com.android.buildAcc.util.isAppPlugin
import com.android.buildAcc.util.log
import org.gradle.BuildResult
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectState
import org.gradle.api.invocation.Gradle
import java.io.File

/**
 * @author ZhuPeipei
 * @date 2022/11/3 11:12
 */
class BuildAccPlugin : Plugin<Project> {
    private var mRootProject: Project? = null
    private var mAppProject: Project? = null
    private var mBuildAccExtension: BuildAccExtension? = null

    private val mBuildTimeCostHandler = BuildTimeCostHandler()
    private val mChangedModulesHandler = ChangedModulesHandler()
    private val mavenPublish = MavenPublishHandler(mChangedModulesHandler)
    private val mLocalDependencyUploadHandler = LocalDependencyUploadHandler()

    override fun apply(project: Project) {
        if (project.rootProject != project) {
            throw RuntimeException("当前plugin只能在root module中使用")
        }
        mRootProject = project

        mBuildAccExtension =
            project.extensions.create("BuildAccExtension", BuildAccExtension::class.java)

        project.afterEvaluate {
            val buildType = mBuildAccExtension?.buildType
            if (buildType?.isNotEmpty() == true) {
                BUILD_TYPES = setOf(buildType)
            }
            val localUrl = mBuildAccExtension?.mavenLocalUrl
            MAVEN_REPO_LOCAL_URL = if (localUrl?.isNotEmpty() == true) {
                if (localUrl.startsWith("/")) {
                    localUrl
                } else {
                    File(project.rootProject.projectDir, localUrl).canonicalPath
                }
            } else {
                "${project.rootProject.projectDir}${File.separator}gradle_plugins/"
            }
            mBuildAccExtension?.mavenUrl?.apply {
                if (startsWith("http")) {
                    MAVEN_REPO_HTTP_URL = if (endsWith("/")) {
                        this
                    } else {
                        "$this/"
                    }
                }
            }
            log("MAVEN_REPO_LOCAL_URL=${MAVEN_REPO_LOCAL_URL}, MAVEN_REPO_HTTP_URL=${MAVEN_REPO_HTTP_URL}")

            val version =
                runCatching { project.gradle.gradleVersion.split(".")[0].toInt() }.getOrNull() ?: 0
            if (version >= 7) {
                // 在7.0上 必须要在setting.gradle中配置
                val gradle = project.rootProject.gradle as DefaultGradle
                val repos = gradle.settings.dependencyResolutionManagement.repositories
                repos.find {
                    val uri = (it as? DefaultMavenArtifactRepository)?.url
                    uri?.scheme == "file" && pathEquals(uri.path, MAVEN_REPO_LOCAL_URL)
                } ?: throw RuntimeException(
                    "gradle 7.0以上版本需要在setting.gradle中添加aar依赖路径，" +
                            "或者手动在setting.gradle中应用BuildAccRepoApplyPlugin插件"
                )
            }

            // 更新下业务层设置的模块
            mChangedModulesHandler.initProject(project, mBuildAccExtension)
        }

        // 这个需要在7下 整合下
        // mavenPublish.configRepository(project.rootProject)

        // 在这种情况下，需要看下是否每次都会生成aar

        project.gradle.addListener(mBuildTimeCostHandler.taskExecutionListener)
        project.gradle.addProjectEvaluationListener(object : ProjectEvaluationListenerWrapper() {
            override fun afterEvaluate(subProject: Project, projectState: ProjectState) {
                super.afterEvaluate(subProject, projectState)
                mChangedModulesHandler.resolveProject(subProject)

                if (mChangedModulesHandler.needAccBuildBundle(subProject)) {
                    log("project ${subProject.name} needAccBuildBundle")
                    // 应用maven-publish插件
                    mavenPublish.applyMavenPublishPlugin(subProject)
                    // 配置maven上传的一些参数
                    mavenPublish.configSubProjectMavenPublishPlugin(subProject)
                    // 添加上传本地依赖的一些文件
                    mLocalDependencyUploadHandler.configLocalDependencyMavenPublishPlugin(
                        subProject,
                        mChangedModulesHandler.mLocalDependencyMap
                    )
                }
            }
        })
        project.gradle.addBuildListener(object : BuildListenerWrapper() {
            override fun projectsEvaluated(gradle: Gradle) {
                super.projectsEvaluated(gradle)

                mAppProject = project.rootProject.allprojects.find { isAppPlugin(it) }

                val appExtension =
                    runCatching { mAppProject?.extensions?.getByType(AppExtension::class.java) }.getOrNull()

                if (mAppProject == null || appExtension == null) {
                    throw RuntimeException("未找到com.android.application模块")
                }

                mChangedModulesHandler.printLog()

                val handler = AarBuildHandler()
                // 在assembleTask后，将子模块打包为aar并上传
                handler.handleAssembleTask(project)

                val replaceDependencyHandler = ReplaceDependencyHandler()
                replaceDependencyHandler.resolveDependency(project.rootProject, appExtension)

                mLocalDependencyUploadHandler.resolveDependency(
                    project.rootProject,
                    appExtension,
                    mChangedModulesHandler.mLocalDependencyMap
                )
            }

            override fun buildFinished(buildResult: BuildResult) {
                super.buildFinished(buildResult)
            }
        })
    }
}