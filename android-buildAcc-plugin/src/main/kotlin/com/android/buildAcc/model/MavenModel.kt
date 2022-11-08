package com.android.buildAcc.model

import java.io.File

/**
 * @author ZhuPeipei
 * @date 2022/11/5 20:46
 */
data class MavenModel(val groupId: String, val artifactId: String, val version: String) {
    fun toPath() = groupId.replace(".", File.separator) +
            File.separator + artifactId + File.separator + version + File.separator +
            "$artifactId-$version.aar"
}