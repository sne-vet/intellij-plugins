package com.snevet.tools

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(
    name = "SnevetToolsSettings",
    storages = [Storage("snevetTools.xml")]
)
class SnevetToolsSettings : PersistentStateComponent<SnevetToolsSettings> {

    var baseBranch: String = "master"

    override fun getState(): SnevetToolsSettings = this

    override fun loadState(state: SnevetToolsSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): SnevetToolsSettings =
            project.getService(SnevetToolsSettings::class.java)
    }
}
