package com.android.buildAcc.util

import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState

/**
 * @author ZhuPeipei
 * @date 2022/11/4 21:35
 */
open class ProjectEvaluationListenerWrapper : ProjectEvaluationListener {
    override fun beforeEvaluate(project: Project) {

    }

    override fun afterEvaluate(project: Project, projectState: ProjectState) {
    }
}