package com.android.buildAcc.constants

import com.android.buildAcc.model.BundleInfo

/**
 * @author ZhuPeipei
 * @date 2022/11/4 20:12
 */
var MAVEN_REPO_LOCAL_URL = ""
var MAVEN_REPO_HTTP_URL: String? = null

var MAVEN_PUBLISH_URL: String = ""

// buildType获取逻辑比较靠后，因此只能通过默认值的方式写入
var BUILD_TYPES = setOf("debug", "release")

// project path对应的aar model
val PROJECT_MAVEN_MAP = mutableMapOf<String, BundleInfo>()

val CONFIGURATIONS = arrayOf("implementation", "api", "compileOnly")

val WHITE_LIST_FOLDER = hashSetOf<String>()

val WHITE_LIST_FILE = hashSetOf<String>()


