package com.android.buildAcc.handler

import com.android.build.gradle.AppExtension
import com.android.buildAcc.constants.MAVEN_REPO_DEFAULT_URL
import com.android.buildAcc.constants.PROJECT_MAVEN_MAP
import com.android.buildAcc.constants.isRepoNetType
import com.android.buildAcc.model.MavenModel
import com.android.buildAcc.util.configurationList
import com.android.buildAcc.util.log
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import java.io.File

/**
 * @author ZhuPeipei
 * @date 2022/11/5 20:14
 */
class ReplaceDependencyHandler {
    // 策略
    // 1. 找到所有可以被打包的aar模块
    // 2. 检查对应的aar模块是否之前已经生成过
    // 3. 替换依赖
    fun resolveDependency(rootProject: Project, appExtension: AppExtension) {
        // 这里在远程aar依赖的情况下，需要请求网络，最好通过异步的方式来实现
        rootProject.allprojects.forEach { subProject ->
            log("subproject ============================== ${subProject.name}")
            val mavenModel = PROJECT_MAVEN_MAP[subProject.name]
            val mavenModelExist = mavenModelExist(mavenModel)
            if (!mavenModelExist) {
                log("${subProject.name} mavenModelExist false")
                val map = hashMapOf<String, String>()
                val list = configurationList(subProject, appExtension)
                list.forEach { configuration ->
                    val iterator = configuration.dependencies.iterator()
                    while (iterator.hasNext()) {
                        val dependency = iterator.next()
                        log("${subProject.name} configuration: ${configuration.name}, ${dependency.group}-${dependency.name}")
                        if (dependency is DefaultProjectDependency
                            && PROJECT_MAVEN_MAP.containsKey(dependency.name)
                        ) {
                            val childModel = PROJECT_MAVEN_MAP[dependency.name]
                            val exist = mavenModelExist(childModel)
                            if (childModel != null && exist) {
                                // subProject中有项目依赖，并且该项目依赖已经打包为aar，那么就进行替换
//                                val map = hashMapOf<String, String>()
//                                map["group"] = dependency.group!!
//                                map["module"] = dependency.name
//                                configuration.exclude(map)

                                iterator.remove()
                                map.put(
                                    "${childModel.groupId}:${childModel.artifactId}:${childModel.version}",
                                    configuration.name
                                )

                                log("替换==========================================")
                                log("${dependency.group}---${dependency.name}")
                            }
                        }
                    }
                }
                map.forEach {
                    subProject.dependencies.add(it.value, it.key)
                }
            }
        }
    }

    private fun mavenModelExist(mavenModel: MavenModel?): Boolean {
        mavenModel ?: return false
        return if (isRepoNetType()) {
            true
        } else {
            // local file
            File(MAVEN_REPO_DEFAULT_URL, mavenModel.toPath()).exists()
        }
    }
}