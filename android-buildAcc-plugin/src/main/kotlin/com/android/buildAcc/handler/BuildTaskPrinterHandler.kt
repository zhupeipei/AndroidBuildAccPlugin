package com.android.buildAcc.handler

import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.invocation.Gradle
import org.jetbrains.kotlin.com.google.gson.JsonObject

/**
 * @author ZhuPeipei
 * @date 2022/11/29 18:06
 */
class BuildTaskPrinterHandler {
    fun config(gradle: Gradle) {
        gradle.taskGraph.whenReady {
            val assembleTask = it.allTasks.find { it.name.contains("assemble") } ?: return@whenReady
            val json = JsonObject()
            json.add(assembleTask.path, recursiveTaskDependencies(it, assembleTask, ""))
//            log("$json")
        }
    }

    private fun recursiveTaskDependencies(
        graph: TaskExecutionGraph,
        task: Task,
        prefix: String
    ): JsonObject {
        val child = JsonObject()
        graph.getDependencies(task)?.forEach {
            child.add(it.path, recursiveTaskDependencies(graph, it, "${prefix}="))
        }
        return child
    }
}