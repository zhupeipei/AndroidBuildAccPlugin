package com.android.buildAcc.model

import com.android.buildAcc.constants.MAVEN_REPO_HTTP_URL
import com.android.buildAcc.constants.MAVEN_REPO_LOCAL_URL
import org.gradle.api.Project

/**
 * @author ZhuPeipei
 * @date 2022/11/8 21:27
 */
// BundleInfo 表示需要参与到加速编译的模块
// 当前仓库适合远程还是本地依赖
// 如果该模块的资源已经缓存好，那么就直接依赖替换；否则会参与到编译中
data class BundleInfo(
    val project: Project,
    var mavenInfo: MavenInfo? = null
)

data class MavenInfo(
    val repoType: RepoType,
    val mavenModel: MavenModel,
    // 模块是否存在
    var modelExist: Boolean = false
) {
    val url = when (repoType) {
        RepoType.RepoLocal -> MAVEN_REPO_LOCAL_URL
        RepoType.RepoNet -> MAVEN_REPO_HTTP_URL
    }
}

enum class RepoType {
    RepoNet, RepoLocal
}