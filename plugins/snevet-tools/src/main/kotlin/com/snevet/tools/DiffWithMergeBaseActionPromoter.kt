package com.snevet.tools

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext

class DiffWithMergeBaseActionPromoter : ActionPromoter {
    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
        return actions.filterIsInstance<DiffWithMergeBaseAction>()
    }
}
