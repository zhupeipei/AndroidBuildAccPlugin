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
        // 只需要处理一层，因为所有项目都会调用这个方法
        project.configurations.forEach { configuration ->
            configuration.dependencies.forEach { dependency ->
                if (dependency is DefaultProjectDependency) {
                    var dependencySet = mDependencyMap[projectName]
                    if (dependencySet == null) {
                        dependencySet = hashSetOf()
                    }
                    dependencySet.add(dependency.name)

                    if (!mNeedResolvedProjectMap.containsKey(dependency.name)) {
                        // 这里说明子项目没有参与加速编译，那么当前project也不能参与加速编译
                        mNeedResolvedProjectMap.remove(projectName)
                        PROJECT_MAVEN_MAP.remove(projectName) // 这个也需要remove掉
                        log("$project 项目无法参与加速编译，因为${dependency.name}没有包含到编译加速的模块的中")
                        // 如果remove掉当前项目，那么依赖当前项目的其他项目也需要remove掉
                        removeProjectForDependency(projectName)
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

    private fun removeProjectForDependency(projectName: String) {
        // 遍历所有的项目，如果有项目依赖projectName，那么需要去除掉
        var removedProjectNameList: ArrayList<String>? = null
        mDependencyMap.forEach { (name, set) ->
            if (set.contains(projectName)) {
                log(">>> $name 项目无法参与加速编译，因为${projectName}没有包含到编译加速的模块的中")
                mNeedResolvedProjectMap.remove(name)
                PROJECT_MAVEN_MAP.remove(name)
                if (removedProjectNameList == null) {
                    removedProjectNameList = ArrayList<String>()
                }
                removedProjectNameList?.add(name)
            }
        }
        removedProjectNameList?.forEach {
            removeProjectForDependency(it)
        }
    }

    fun printLog() {
        log("================================================== 参与加速编译的模块如下：")
        mNeedResolvedProjectMap.forEach { (t, u) ->
            log("project $t")
        }
        log("================================================== 参与加速编译的模块如 end")
    }
}