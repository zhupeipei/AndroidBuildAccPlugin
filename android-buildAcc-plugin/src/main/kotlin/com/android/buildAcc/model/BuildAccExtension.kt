package com.android.buildAcc.model

/**
 * @author ZhuPeipei
 * @date 2022/11/5 17:28
 */
open class BuildAccExtension(
    // buildType因为gradle bug，只能设置一个
    var buildType: String = "debug",
    // maven远程存储的地址
    var mavenUrl: String? = null,
    // maven本地存储的地址
    var mavenLocalUrl: String = "./gradle_plugins/",
    // 包含的模块
    var includeBundles: Array<String>? = null,
    // 不参与加速编译的模块，优先级比includeModules高
    var excludeBundles: Array<String>? = null,
    // 在生成localMaven时，需要过滤一些文件夹，防止计算最后修改的时间戳改变导致每次都需要重新编译
    var whiteListFolder: Array<String>? = null,
    // 在生成localMaven时，需要过滤一些文件，防止计算最后修改的时间戳改变导致每次都需要重新编译
    var whiteListFile: Array<String>? = null
)