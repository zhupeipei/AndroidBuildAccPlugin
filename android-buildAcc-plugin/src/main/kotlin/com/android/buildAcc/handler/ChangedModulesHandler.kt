package com.android.buildAcc.handler

import com.android.buildAcc.constants.PROJECT_MAVEN_MAP
import com.android.buildAcc.model.BuildAccExtension
import com.android.buildAcc.model.BundleInfo
import com.android.buildAcc.model.LocalDependencyModel
import com.android.buildAcc.util.isAndroidPlugin
import com.android.buildAcc.util.log
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.jetbrains.kotlin.konan.file.File
import java.util.*

/**
 * @author ZhuPeipei
 * @date 2022/11/8 20:17
 */
class ChangedModulesHandler {
    // 这里存储了需要参与加速编译的Bundle
    private val mNeedResolvedProjectMap = hashMapOf<String, BundleInfo>()
    private val mDependencyMap = hashMapOf<String, HashSet<String>>()

    // 存储下本地依赖的aar和jar包，这里默认都会成功，失败的逻辑后续再处理
    val mLocalDependencyMap = hashMapOf<String, ArrayList<LocalDependencyModel>>()

    fun needAccBuildBundle(project: Project) = mNeedResolvedProjectMap.containsKey(project.name)

    fun getBundleInfo(project: Project) = mNeedResolvedProjectMap[project.name]

    fun getBundleSize() = mNeedResolvedProjectMap.size

    // 这里先处理下配置的参数
    fun initProject(project: Project, buildAccExtension: BuildAccExtension?) {
        val includeProjects =
            buildAccExtension?.includeBundles?.map { it.toLowerCase(Locale.getDefault()) }
        val excludeProjects =
            buildAccExtension?.excludeBundles?.map { it.toLowerCase(Locale.getDefault()) }
        project.rootProject.allprojects.forEach {
            if (it != project.rootProject) {
                if (excludeProjects == null || !excludeProjects.contains(it.name.toLowerCase(Locale.getDefault()))) {
                    if (includeProjects == null) {
                        mNeedResolvedProjectMap[it.name] = BundleInfo(it)
                    } else {
                        if (includeProjects.contains(it.name.toLowerCase(Locale.getDefault()))) {
                            mNeedResolvedProjectMap[it.name] = BundleInfo(it)
                        }
                    }
                }
            }
        }
    }

    // 1. 目前所有子project都会打包为aar
    // 2. AppPlugin不需要打包为aar，java后续再做支持
    fun resolveProject(project: Project) {
        val projectName = project.name
        if (!mNeedResolvedProjectMap.containsKey(projectName)) {
            return
        }
        if (!isAndroidPlugin(project)) {
            mNeedResolvedProjectMap.remove(projectName)
            return
        }

        // 如果mNeedResolvedProjectMap这个模块里有子项目的依赖，而子项目又不在这个map里面，那么该模块也不会被纳入到加速编译中
        // 如果一个项目依赖的子项目的jar、aar没有生成，这时候不应该进行依赖替换
        // 只需要处理一层，因为所有项目都会调用这个方法
        project.configurations.forEach { configuration ->
            configuration.dependencies.forEach { dependency ->
                if (dependency is DefaultProjectDependency) {
                    var dependencySet = mDependencyMap[projectName]
                    if (dependencySet == null) {
                        dependencySet = hashSetOf()
                        mDependencyMap[projectName] = dependencySet
                    }
                    dependencySet.add(dependency.name)

                    if (!mNeedResolvedProjectMap.containsKey(dependency.name)) {
                        // 这里说明子项目没有参与加速编译，那么当前project也不能参与加速编译
                        mNeedResolvedProjectMap.remove(projectName)
                        PROJECT_MAVEN_MAP.remove(projectName) // 这个也需要remove掉
                        log("$project 项目无法参与加速编译，因为${dependency.name}没有包含到加速编译的模块中")
                        // 如果remove掉当前项目，那么依赖当前项目的其他项目也需要remove掉
                        removeProjectAndParentProjectDependency(projectName) {
                            log(">>> $it 项目无法参与加速编译，因为${it}依赖的项目---${projectName}没有包含到加速编译的模块中")
                        }
                    }
                } else if (dependency is DefaultExternalModuleDependency && dependency.artifacts.isNotEmpty()) {
                    // 这里判断有点简单了，还没有想到比较好的方式处理
                    if (dependency.group == null && dependency.version == null) {
                        dependency.artifacts.forEach {
                            if (it is DefaultDependencyArtifact && ("aar" == it.type || "jar" == it.type)) {
                                // 这里拿到了api(name: 'HiPushSdk-v6.0.3.103-release', ext: 'aar')这种类型的依赖
                                // 这种类型的依赖在gradle-7上已经不再被支持了
                                val path =
                                    "${project.projectDir}${File.separator}libs${File.separator}${it.name}.${it.type}"
                                if (File(path).exists) {
                                    // 这里将会开始操作
                                    var list = mLocalDependencyMap[projectName]
                                    if (list == null) {
                                        list = arrayListOf()
                                        mLocalDependencyMap[projectName] = list
                                    }
                                    list.add(LocalDependencyModel(path, it.name, it.type))
                                }
                            }
                        }
                    }
                } else if (dependency is DefaultSelfResolvingDependency) {
                    // 这里需要考虑如下的依赖，暂时先不做处理（需要考虑过滤依赖系统的jar）
                    // api fileTree (include: '*.jar', dir: 'libs')
                    // api fileTree (dir: 'libs', excludes: ['*.jar'])
                    // api fileTree (dir: 'libs', include: ['*.jar'])
                    // api files("libs/HiPushSdk-v6.0.3.103-release.aar")

                    // Dependencies of file collection===unspecified===null===null===
                    // class org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency_Decorated===
                    // [XXX/Library/Android/sdk/platforms/android-32/core-for-system-modules.jar]
                    // log("222 dependency: ${dependency.buildDependencies}===${dependency.name}===
                    // ${dependency.group}===${dependency.version}===${dependency.javaClass}===
                    // ${dependency.files.files}")
                }
            }
        }
    }

    // 去除加速编译项目中maven没有生成的项目、父项目、子项目
    // 如果项目不在加速编译的项目中，那么该项目以及父项目都不应该参与加速编译；
    // 如果加速编译的项目对应的aar文件还没有生成，那么该项目、父项目、子项目都不应该参与编译
    fun checkProjectMavenFileExist() {
        val projectMavenNotExistNames = mutableListOf<String>()
        mNeedResolvedProjectMap.forEach { (projectName, bundleInfo) ->
            if (bundleInfo.mavenInfo?.modelExist == false) {
                projectMavenNotExistNames.add(projectName)
            }
        }
        projectMavenNotExistNames.forEach { projectName ->
            log("$projectName 项目无法参与加速编译，因为当前项目的mavenRepo不存在")
            mNeedResolvedProjectMap.remove(projectName)
            PROJECT_MAVEN_MAP.remove(projectName)

            removeProjectAndParentProjectDependency(projectName) {
                log(">>> $it 项目无法参与加速编译，因为${it}依赖的项目---${projectName}对应的mavenRepo不存在")
            }
            // 不应该移除子项目依赖，而是应该替换子项目依赖
//            removeProjectChildDependency(projectName) {
//                log(">>> $it 项目无法参与加速编译，因为${it}父依赖---${projectName}对应的mavenRepo不存在")
//            }
        }
    }

    // 移除依赖projectName的所有父项目
    private fun removeProjectAndParentProjectDependency(
        projectName: String,
        listener: (String) -> Unit
    ) {
        // 遍历所有的项目，如果有项目依赖projectName，那么需要去除掉
        var removedProjectNameList: ArrayList<String>? = null
        mDependencyMap.forEach { (name, set) ->
            if (set.contains(projectName)) {
                listener(name)
                mNeedResolvedProjectMap.remove(name)
                PROJECT_MAVEN_MAP.remove(name)
                if (removedProjectNameList == null) {
                    removedProjectNameList = ArrayList<String>()
                }
                removedProjectNameList?.add(name)
            }
        }
        removedProjectNameList?.forEach {
            removeProjectAndParentProjectDependency(it, listener)
        }
    }

    private fun removeProjectChildDependency(projectName: String, listener: (String) -> Unit) {
        val childProjectSet = mDependencyMap[projectName] ?: return
        childProjectSet.forEach { childProjectName ->
            listener(childProjectName)
            mNeedResolvedProjectMap.remove(childProjectName)
            PROJECT_MAVEN_MAP.remove(childProjectName)
        }
        val list = mutableListOf<String>()
        list.addAll(childProjectSet)
        list.forEach {
            removeProjectChildDependency(it, listener)
        }
    }

    fun printLog(rootProject: Project) {
        log("================================================== 各个模块是否参与编译加速 (参与加速编译的用*标记)")
        rootProject.allprojects.forEach {
            val log = if (mNeedResolvedProjectMap.containsKey(it.name)) "*" else ""
            log("project ${it.name} $log")
        }
        log("================================================== 各个模块是否参与编译加速 end")
    }
}