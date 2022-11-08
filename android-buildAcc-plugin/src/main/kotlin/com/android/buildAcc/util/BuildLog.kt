package com.android.buildAcc.util

/**
 * @author ZhuPeipei
 * @date 2022/11/3 10:21
 */
private const val enabled = true

fun log(msg: String?) {
    if (enabled) println(msg)
}