package com.android.buildAcc.model

/**
 * @author ZhuPeipei
 * @date 2022/11/9 22:00
 */
class TaskExecTimeInfo(
    val projectName: String,
    val path: String,
    // task 执行开始时间
    val start: Long
) {
    // task执行总时长
    var total: Long = 0

    // task 结束时间
    var end: Long = 0
}