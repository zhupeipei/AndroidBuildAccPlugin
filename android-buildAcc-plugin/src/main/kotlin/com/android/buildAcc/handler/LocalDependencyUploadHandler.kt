package com.android.buildAcc.handler

import com.android.build.gradle.AppExtension
import com.android.buildAcc.constants.MAVEN_REPO_HTTP_URL
import com.android.buildAcc.constants.MAVEN_REPO_LOCAL_URL
import com.android.buildAcc.model.LocalDependencyModel
import com.android.buildAcc.model.MavenInfo
import com.android.buildAcc.model.MavenModel
import com.android.buildAcc.model.RepoType
import com.android.buildAcc.util.MAVEN_TASK_PREFIX_FOR_LOCAL_DEPENDENCY
import com.android.buildAcc.util.configurationList
import com.android.buildAcc.util.execCmd
import com.android.buildAcc.util.getAppProject
import com.android.buildAcc.util.getDefaultMavenGroupId
import com.android.buildAcc.util.isAndroidPlugin
import com.android.buildAcc.util.isAppPlugin
import com.android.buildAcc.util.isJavaPlugin
import com.android.buildAcc.util.isNullOrEmpty
import com.android.buildAcc.util.log
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * @author ZhuPeipei
 * @date 2022/11/15 17:22
 * 将libs依赖上传到maven仓库中
 */
class LocalDependencyUploadHandler {
    fun configLocalDependencyMavenPublishPlugin(
        subProject: Project,
        dependencyMap: HashMap<String, ArrayList<LocalDependencyModel>>
    ) {
        if (subProject == subProject.rootProject) {
            return
        } else if (isAppPlugin(subProject) || isJavaPlugin(subProject)) {
            return
        } else if (isAndroidPlugin(subProject)) {
            subProject.afterEvaluate {
                val list = dependencyMap[subProject.name]
                configMavenPublish(subProject, list)
            }
            return
        }
    }

    fun createAndConfigPublishTask(
        project: Project,
        dependencyMap: HashMap<String, ArrayList<LocalDependencyModel>>
    ) {
        if (dependencyMap.size <= 0) {
            return
        }
        val (_, appProject) = getAppProject(project)
            ?: throw RuntimeException("无法替换本地aar依赖，因为appProject无法找到")
        val preBuildTask = appProject.tasks.named("preBuild")

        project.rootProject.allprojects.forEach { subProject ->
            if (subProject == subProject.rootProject) {
            } else if (isAppPlugin(subProject) || isJavaPlugin(subProject)) {
            } else if (isAndroidPlugin(subProject)) {
                val list = dependencyMap[subProject.name]
                hookPreBuildTask(subProject, list, preBuildTask)
            }
        }
    }

    private fun configMavenPublish(
        subProject: Project,
        localDependencyModelList: ArrayList<LocalDependencyModel>?
    ) {
        if (localDependencyModelList == null || localDependencyModelList.size <= 0) {
            return
        }
        if (!subProject.pluginManager.hasPlugin("maven-publish")) {
            return
        }
        log("project ${subProject.name} --- configMavenPublish for localDependency")
        subProject.extensions.configure<PublishingExtension>("publishing") { publishingExt ->
            val useNet = !MAVEN_REPO_HTTP_URL.isNullOrEmpty()

            localDependencyModelList.forEach { model ->
                createPublishConfig(subProject, publishingExt, model, useNet)
            }
            publishingExt.repositories { repositoryHandler ->
                repositoryHandler.maven {
                    it.setUrl(if (useNet) MAVEN_REPO_LOCAL_URL else MAVEN_REPO_LOCAL_URL)
                }
            }
        }
    }

    private fun createPublishConfig(
        project: Project,
        publishingExt: PublishingExtension,
        model: LocalDependencyModel,
        useNet: Boolean
    ) {
        publishingExt.publications { publicationContainer ->
            val publicationName =
                "$MAVEN_TASK_PREFIX_FOR_LOCAL_DEPENDENCY-${model.dependencyName}-${model.type.capitalize()}"
            if (publicationContainer.findByName(publicationName) == null) {
                publicationContainer.create(
                    publicationName,
                    MavenPublication::class.java
                ) {
                    // 这里最好是对依赖的文件做个md5校验，后续再加上
                    with(it) {
                        groupId = project.getDefaultMavenGroupId()
                        artifactId = model.dependencyName
                        version = "1.0.0"
                        artifact(model.localPath)
                    }

                    val mavenModel = MavenModel(it.groupId, it.artifactId, it.version)
                    model.mavenInfo = createMavenInfo(mavenModel, project, useNet)

                    log(
                        "project ${project.path}, maven{groupId=${it.groupId}," +
                                " artifactId=${it.artifactId}, version=${it.version}," +
                                " url=${MAVEN_REPO_LOCAL_URL}}"
                    )
                }
            }
        }
    }

    private fun createMavenInfo(
        mavenModel: MavenModel,
        project: Project,
        useNet: Boolean
    ): MavenInfo {
        return if (useNet) {
            val cmdResult =
                project.execCmd("curl -I $MAVEN_REPO_HTTP_URL${mavenModel.toPath()}")
            if (cmdResult.startsWith("HTTP/1.1 200 OK")) {
                MavenInfo(RepoType.RepoNet, mavenModel, true)
            } else {
                MavenInfo(RepoType.RepoNet, mavenModel, false)
            }
        } else {
            val modelExist = File(MAVEN_REPO_LOCAL_URL, mavenModel.toPath()).exists()
            MavenInfo(RepoType.RepoLocal, mavenModel, modelExist)
        }
    }

    private fun hookPreBuildTask(
        project: Project,
        localDependencyModelList: ArrayList<LocalDependencyModel>?,
        preBuildTask: TaskProvider<Task>
    ) {
        if (localDependencyModelList == null || localDependencyModelList.size <= 0) {
            return
        }
        runCatching {
            localDependencyModelList.forEach { model ->
                if (model.mavenInfo?.modelExist == false) {
                    val publicationName =
                        "$MAVEN_TASK_PREFIX_FOR_LOCAL_DEPENDENCY-${model.dependencyName}-${model.type.capitalize()}"
                    runCatching {
                        val uploadTask =
                            project.tasks.named("publish${publicationName.capitalize()}PublicationToMavenRepository")
                        preBuildTask.configure {
                            it.finalizedBy(uploadTask)
                        }
                    }
                }
            }
        }
    }

    // 方法 1. 替换所有模块中的本地依赖; 2. 替换需要远程加载的模块
    // 从长期来看，最好用第一种方法
    fun resolveDependency(
        rootProject: Project,
        appExtension: AppExtension,
        localDependencyMap: HashMap<String, ArrayList<LocalDependencyModel>>
    ) {
        if (localDependencyMap.size <= 0) {
            return
        }
        // 这里在远程aar依赖的情况下，需要请求网络，最好通过异步的方式来实现
        rootProject.allprojects.forEach { subProject ->
            val models = localDependencyMap[subProject.name]
            if (models != null) {
                val list = configurationList(subProject, appExtension)
                val map = hashMapOf<String, String>()
                list.forEach { configuration ->
                    val iterator = configuration.dependencies.iterator()
                    while (iterator.hasNext()) {
                        val dependency = iterator.next()
                        val model = canReplaceDependency(dependency, models)
                        val mavenModel = model?.mavenInfo?.mavenModel
                        if (model != null && mavenModel != null) {
                            iterator.remove()
                            map.put(
                                "${mavenModel.groupId}:${mavenModel.artifactId}:${mavenModel.version}",
                                configuration.name
                            )
                        }
                    }
                }
                map.forEach {
                    subProject.dependencies.add(it.value, it.key)
                }
            }
        }
    }

    private fun canReplaceDependency(
        dependency: Dependency,
        models: ArrayList<LocalDependencyModel>
    ): LocalDependencyModel? {
        // 这里只考虑这种情况 api(name: 'HiPushSdk-v6.0.3.103-release', ext: 'aar')
        if (dependency is DefaultExternalModuleDependency && dependency.artifacts.isNotEmpty()) {
            if (dependency.group == null && dependency.version == null) {
                dependency.artifacts.forEach {
                    if (it is DefaultDependencyArtifact && ("aar" == it.type || "jar" == it.type)) {
                        models.forEach { dependencyModel ->
                            if (dependencyModel.dependencyName == it.name && dependencyModel.type == it.type) {
                                return dependencyModel
                            }
                        }
                    }
                }
            }
        }
        return null
    }
}