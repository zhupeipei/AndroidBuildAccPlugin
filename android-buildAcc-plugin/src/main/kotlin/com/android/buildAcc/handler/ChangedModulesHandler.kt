package com.android.buildAcc.handler

import com.android.build.api.dsl.Bundle
import com.android.buildAcc.constants.PROJECT_MAVEN_MAP
import com.android.buildAcc.model.BuildAccExtension
import com.android.buildAcc.model.BundleInfo
import com.android.buildAcc.util.isAndroidPlugin
import com.android.buildAcc.util.log
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/**
 * @author ZhuPeipei
 * @date 2022/11/8 20:17
 */
class ChangedModulesHandler {
    // 这里存储了需要参与加速编译的Bundle
    private val mNeedResolvedProjectMap = hashMapOf<String, BundleInfo>()
    private val mDependencyMap = hashMapOf<String, HashSet<String>>()

    fun needAccBuildBundle(project: Project) = mNeedResolvedProjectMap.containsKey(project.name)

    fun getBundleInfo(project: Project) = mNeedResolvedProjectMap[project.name]

    fun getBundleSize() = mNeedResolvedProjectMap.size

    // 这里先处理下配置的参数
    fun initProject(project: Project, buildAccExtension: BuildAccExtension?) {
        val includeProjects =
            buildAccExtension?.includeBundles?.map { it.lowercase(Locale.getDefault()) }
        val excludeProjects =
            buildAccExtension?.excludeBundles?.map { it.lowercase(Locale.getDefault()) }
        project.rootProject.allprojects.forEach {
            if (excludeProjects == null || !excludeProjects.contains(it.name.lowercase(Locale.getDefault()))) {
                if (includeProjects == null) {
                    mNeedResolvedProjectMap[it.name] = BundleInfo(it)
                } else {
                    if (includeProjects.contains(it.name.lowercase(Locale.getDefault()))) {
                        mNeedResolvedProjectMap[it.name] = BundleInfo(it)
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
}