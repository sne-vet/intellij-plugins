package com.snevet.tools

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class SnevetToolsCoroutineScope(val cs: CoroutineScope) {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): SnevetToolsCoroutineScope =
            project.getService(SnevetToolsCoroutineScope::class.java)
    }
}
