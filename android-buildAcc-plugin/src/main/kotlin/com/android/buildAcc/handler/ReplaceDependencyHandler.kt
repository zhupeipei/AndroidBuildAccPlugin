package com.android.buildAcc.handler

import com.android.build.gradle.AppExtension
import com.android.buildAcc.constants.PROJECT_MAVEN_MAP
import com.android.buildAcc.util.configurationList
import com.android.buildAcc.util.log
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency

/**
 * @author ZhuPeipei
 * @date 2022/11/5 20:14
 */
class ReplaceDependencyHandler(private val mChangedModulesHandler: ChangedModulesHandler) {
    // 策略
    // 1. 找到所有可以被打包的aar模块
    // 2. 检查对应的aar模块是否之前已经生成过
    // 3. 替换依赖
    fun resolveDependency(rootProject: Project, appExtension: AppExtension) {
        // 这里在远程aar依赖的情况下，需要请求网络，最好通过异步的方式来实现
        // 这里需要注意一点，如果lib对应的jar/aar不存在，此时lib也不能做替换；
        // 如果lib1依赖lib2，同时别的项目也依赖了lib2，lib2对应的aar存在，lib1对应aar不存在，这时候会导致lib2既有远程依赖又有本地依赖
        mChangedModulesHandler.checkProjectMavenFileExist()

        mChangedModulesHandler.printLog(rootProject)

        rootProject.allprojects.forEach { subProject ->
            log("subproject ============================== ${subProject.name}")
            val bundleInfo = PROJECT_MAVEN_MAP[subProject.name]
            // bundleInfo == null 说明project不需要进行加速编译
            if (bundleInfo == null) {
                val map = hashMapOf<String, String>()
                val list = configurationList(subProject, appExtension)
                list.forEach { configuration ->
                    val iterator = configuration.dependencies.iterator()
                    while (iterator.hasNext()) {
                        val dependency = iterator.next()
//                        log("${subProject.name} configuration: ${configuration.name}, ${dependency.group}-${dependency.name}")
                        if (dependency is DefaultProjectDependency
                            && PROJECT_MAVEN_MAP.containsKey(dependency.name)
                        ) {
                            val aarBundle = PROJECT_MAVEN_MAP[dependency.name]
                            val aarMavenInfo = aarBundle?.mavenInfo?.mavenModel
                            if (aarBundle?.mavenInfo?.modelExist == false) {
                                throw RuntimeException("${aarMavenInfo}对应的aar或者jar包不存在")
                            }
                            val mavenAarExist = aarBundle?.mavenInfo?.modelExist ?: false
                            if (aarMavenInfo != null && mavenAarExist) {
                                // subProject中有项目依赖，并且该项目依赖已经打包为aar，那么就进行替换
//                                val map = hashMapOf<String, String>()
//                                map["group"] = dependency.group!!
//                                map["module"] = dependency.name
//                                configuration.exclude(map)

                                iterator.remove()
                                map.put(
                                    "${aarMavenInfo.groupId}:${aarMavenInfo.artifactId}:${aarMavenInfo.version}",
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
}