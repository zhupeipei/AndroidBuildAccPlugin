package com.android.buildAcc.handler

import com.android.buildAcc.model.TaskExecTimeInfo
import com.android.buildAcc.util.BuildListenerWrapper
import com.android.buildAcc.util.log
import org.gradle.BuildResult
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskState

/**
 * @author ZhuPeipei
 * @date 2022/11/9 21:59
 */
class BuildTimeCostHandler {
    // 用来记录 task 的执行时长等信息
    private val timeCostMap = hashMapOf<String, TaskExecTimeInfo>()
    private val timeCostMapForProject = hashMapOf<String, ArrayList<TaskExecTimeInfo>>()

    // 用来按顺序记录执行的 task 名称
    private val taskPathList = ArrayList<String>()

    private var mBuildStartTime = 0L

    val taskExecutionListener = object : TaskExecutionListener {
        override fun beforeExecute(task: Task) {
            // task开始执行之前搜集task的信息
            val projectName = task.project.name
            val timeInfo = TaskExecTimeInfo(projectName, task.path, System.currentTimeMillis())
            timeCostMap[task.path] = timeInfo
            taskPathList.add(task.path)

            var list = timeCostMapForProject[projectName]
            if (list == null) {
                list = ArrayList<TaskExecTimeInfo>()
                timeCostMapForProject[projectName] = list
            }
            list.add(timeInfo)
        }

        override fun afterExecute(task: Task, taskState: TaskState) {
            // task执行完之后，记录结束时的时间
            val curTime = System.currentTimeMillis()
            timeCostMap[task.path]?.apply {
                end = curTime
                total = end - start
            }
        }
    }

    fun calProjectExecTime(projectName: String): Long {
        var costTime = 0L
        timeCostMapForProject[projectName]?.forEach {
            costTime += it.total
        }
        return costTime
    }

    fun config(gradle: Gradle) {
        mBuildStartTime = System.currentTimeMillis()

        gradle.addListener(taskExecutionListener)
        gradle.addBuildListener(object : BuildListenerWrapper() {
            override fun buildFinished(buildResult: BuildResult) {
                log("===========================build Time Cost==============================")
                timeCostMapForProject.forEach {
                    val timeCost = calProjectExecTime(it.key)
                    log("project ${it.key} cost $timeCost")
                }
                val totalTime = with(System.currentTimeMillis() - mBuildStartTime) {
                    if (this > 60_000) {
                        val minute = this / 60_000
                        val second = (this - minute * 60_000) / 1000
                        "${minute}m${second}s"
                    } else {
                        "${this / 1000}"
                    }
                }
                log("total build time: $totalTime")
            }
        })
    }

}