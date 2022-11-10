package com.android.buildAcc.constants

import com.android.buildAcc.model.BundleInfo

/**
 * @author ZhuPeipei
 * @date 2022/11/4 20:12
 */
const val MAVEN_REPO_HTTP = 1
const val MAVEN_REPO_LOCAL = 2

var MAVEN_REPO_LOCAL_URL = ""
var MAVEN_REPO_HTTP_URL: String? = null

// buildType获取逻辑比较靠后，因此只能通过默认值的方式写入
var BUILD_TYPES = setOf("debug", "release")

// project path对应的aar model
val PROJECT_MAVEN_MAP = mutableMapOf<String, BundleInfo>()

val CONFIGURATIONS = arrayOf("implementation", "api", "compileOnly")


