package com.android.buildAcc.model

/**
 * @author ZhuPeipei
 * @date 2022/11/5 17:28
 */
open class BuildAccExtension(
    // buildType因为gradle bug，只能设置一个
    var buildType: String = "debug",
    // maven地址
    var mavenUrl: String = "./gradle_plugins/"
)