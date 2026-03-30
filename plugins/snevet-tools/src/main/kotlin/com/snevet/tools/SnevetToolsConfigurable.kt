package com.snevet.tools

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel

class SnevetToolsConfigurable(private val project: Project) : BoundConfigurable("snevet tools") {

    private val settings get() = SnevetToolsSettings.getInstance(project)

    override fun createPanel() = panel {
        group("Diff with Merge Base") {
            row("Base branch:") {
                textField()
                    .bindText(settings::baseBranch)
                    .columns(20)
                    .comment("Branch to compute merge base from (e.g. master, main, develop)")
            }
        }
    }
}
