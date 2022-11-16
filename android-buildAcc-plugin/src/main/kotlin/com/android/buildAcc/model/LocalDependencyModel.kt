package com.android.buildAcc.model

/**
 * @author ZhuPeipei
 * @date 2022/11/15 21:49
 */
data class LocalDependencyModel(
    val localPath: String,
    val dependencyName: String,
    val type: String,
    var mavenInfo: MavenInfo? = null
)
