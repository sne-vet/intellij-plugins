package com.snevet.tools

import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser
import com.intellij.openapi.vcs.changes.ui.SimpleTreeEditorDiffPreview
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File
import javax.swing.tree.TreeSelectionModel

class DiffWithMergeBaseAction : DumbAwareAction() {

    companion object {
        private val LOG = logger<DiffWithMergeBaseAction>()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    @Suppress("UnstableApiUsage")
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = SnevetToolsSettings.getInstance(project)
        val baseBranch = settings.baseBranch

        val repoManager = GitRepositoryManager.getInstance(project)
        val repositories = repoManager.repositories
        if (repositories.isEmpty()) {
            Messages.showWarningDialog(project, "No Git repository found in this project.", "snevet tools")
            return
        }

        if (repositories.size > 1) {
            LOG.warn("Multiple Git roots detected; only '${repositories.first().root.name}' will be used")
            Messages.showWarningDialog(
                project,
                "Multiple Git roots detected. Only '${repositories.first().root.name}' will be used for the diff.",
                "snevet tools"
            )
        }

        val repository = repositories.first()

        SnevetToolsCoroutineScope.getInstance(project).cs.launch {
            try {
                val changes = withBackgroundProgress(project, "Computing diff with $baseBranch...") {
                    withContext(Dispatchers.IO) {
                        computeChanges(project, repository, baseBranch)
                    }
                }
                withContext(Dispatchers.EDT) {
                    if (changes.isEmpty()) {
                        Messages.showInfoMessage(project, "No changes found compared to '$baseBranch'.", "snevet tools")
                    } else {
                        showChanges(project, changes, baseBranch)
                    }
                }
            } catch (ex: ProcessCanceledException) {
                throw ex
            } catch (ex: Exception) {
                LOG.warn("Failed to compute diff with '$baseBranch'", ex)
                withContext(Dispatchers.EDT) {
                    Messages.showErrorDialog(
                        project,
                        "Failed to compute diff with '$baseBranch':\n${ex.message}",
                        "snevet tools"
                    )
                }
            }
        }
    }

    private fun findMergeBase(project: Project, repository: GitRepository, baseBranch: String): String {
        val handler = GitLineHandler(project, repository.root, GitCommand.MERGE_BASE)
        handler.addParameters(baseBranch, "HEAD")
        handler.setSilent(true)

        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            throw VcsException("Could not compute merge base: branch '$baseBranch' not found or repository has no commits.")
        }

        return result.output.firstOrNull()?.trim()
            ?: throw VcsException("No merge base found between HEAD and '$baseBranch'")
    }

    private fun computeChanges(project: Project, repository: GitRepository, baseBranch: String): List<Change> {
        val mergeBase = findMergeBase(project, repository, baseBranch)

        val handler = GitLineHandler(project, repository.root, GitCommand.DIFF)
        handler.addParameters(mergeBase, "-M", "--name-status")
        handler.setSilent(true)

        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            throw VcsException(result.errorOutputAsJoinedString.ifEmpty { "git diff failed" })
        }

        // Detect binary files via --numstat (binary files show "-\t-\t<path>")
        val binaryPaths = mutableSetOf<String>()
        val numstatHandler = GitLineHandler(project, repository.root, GitCommand.DIFF)
        numstatHandler.addParameters(mergeBase, "-M", "--numstat")
        numstatHandler.setSilent(true)
        val numstatResult = Git.getInstance().runCommand(numstatHandler)
        if (numstatResult.success()) {
            for (line in numstatResult.output) {
                if (line.startsWith("-\t-\t")) {
                    binaryPaths.add(expandBracePath(line.substringAfter("-\t-\t").trim()))
                }
            }
        }

        val changes = mutableListOf<Change>()

        for (line in result.output) {
            if (line.isBlank()) continue
            val parts = line.split("\t")
            if (parts.size < 2) continue

            val status = parts[0].trim().first()

            val oldPath: String
            val newPath: String
            when {
                (status == 'R' || status == 'C') && parts.size >= 3 -> {
                    oldPath = parts[1].trim()
                    newPath = parts[2].trim()
                }
                else -> {
                    oldPath = parts[1].trim()
                    newPath = parts[1].trim()
                }
            }

            val isBinary = newPath in binaryPaths || oldPath in binaryPaths

            val oldFilePath = VcsUtil.getFilePath(File(repository.root.path, oldPath), false)
            val newFilePath = VcsUtil.getFilePath(File(repository.root.path, newPath), false)

            val mergeBaseRevision = GitRevisionNumber(mergeBase)
            val beforeRevision: ContentRevision? = if (status != 'A') {
                if (isBinary) BinaryPlaceholderRevision(oldFilePath, mergeBaseRevision)
                else GitContentRevision.createRevision(oldFilePath, mergeBaseRevision, project)
            } else null

            val afterRevision: ContentRevision? = if (status != 'D') {
                CurrentContentRevision.create(newFilePath)
            } else null

            if (beforeRevision != null || afterRevision != null) {
                changes.add(Change(beforeRevision, afterRevision))
            }
        }

        // Include untracked files as new additions
        val lsHandler = GitLineHandler(project, repository.root, GitCommand.LS_FILES)
        lsHandler.addParameters("--others", "--exclude-standard")
        lsHandler.setSilent(true)
        val lsResult = Git.getInstance().runCommand(lsHandler)
        if (lsResult.success()) {
            for (line in lsResult.output) {
                if (line.isBlank()) continue
                val relativePath = line.trim()
                val filePath = VcsUtil.getFilePath(File(repository.root.path, relativePath), false)
                changes.add(Change(null, CurrentContentRevision.create(filePath)))
            }
        }

        return changes
    }

    private fun showChanges(project: Project, changes: List<Change>, baseBranch: String) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("snevet tools") ?: return
        val contentManager = toolWindow.contentManager
        contentManager.removeAllContents(true)

        val title = "Changes since $baseBranch"
        val browser = MergeBaseChangesBrowser(project)
        browser.viewer.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION)

        val diffProcessor = MergeBaseDiffRequestProcessor(project, browser, changes)
        val diffPreview = MergeBaseEditorDiffPreview(diffProcessor, browser, title)
        browser.setShowDiffActionPreview(diffPreview)
        browser.setChangesToDisplay(changes)
        browser.viewer.invokeAfterRefresh {
            if (browser.selectedChanges.isEmpty()) {
                browser.selectEntries(listOf(changes.first()))
            }
            diffPreview.openPreview(false)
        }

        val content = contentManager.factory.createContent(browser, title, false)
        content.setPreferredFocusableComponent(browser.preferredFocusedComponent)
        content.setDisposer(Disposable {
            diffPreview.closePreview()
            Disposer.dispose(diffProcessor)
            browser.shutdown()
        })
        contentManager.addContent(content)
        toolWindow.setAvailable(true)
        toolWindow.activate(null)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }

    private fun expandBracePath(raw: String): String {
        val braceOpen = raw.indexOf('{')
        val braceClose = raw.indexOf('}')
        val arrow = raw.indexOf(" => ", braceOpen.coerceAtLeast(0))
        return if (braceOpen >= 0 && arrow in braceOpen until braceClose) {
            val prefix = raw.substring(0, braceOpen)
            val newPart = raw.substring(arrow + 4, braceClose)
            val suffix = raw.substring(braceClose + 1)
            "$prefix$newPart$suffix"
        } else if (raw.contains(" => ")) {
            raw.substringAfter(" => ")
        } else {
            raw
        }
    }

    private class BinaryPlaceholderRevision(
        private val path: FilePath,
        private val rev: VcsRevisionNumber
    ) : ContentRevision {
        override fun getContent(): String? = null
        override fun getFile(): FilePath = path
        override fun getRevisionNumber(): VcsRevisionNumber = rev
    }

    private class MergeBaseChangesBrowser(project: Project) : SimpleAsyncChangesBrowser(project, false, false) {
        override fun createPopupMenuActions(): List<AnAction> = listOf(diffAction)
    }

    private class MergeBaseEditorDiffPreview(
        diffProcessor: MergeBaseDiffRequestProcessor,
        private val browser: SimpleAsyncChangesBrowser,
        private val title: String
    ) : SimpleTreeEditorDiffPreview(diffProcessor, browser.viewer, browser, true) {
        override fun getCurrentName(): String = title

        override fun returnFocusToTree() {
            browser.preferredFocusedComponent.requestFocusInWindow()
        }
    }

    private class MergeBaseDiffRequestProcessor(
        project: Project,
        private val browser: SimpleAsyncChangesBrowser,
        private val changes: List<Change>
    ) : ChangeViewDiffRequestProcessor(project, DiffPlaces.CHANGES_VIEW) {
        override fun iterateSelectedChanges(): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
            return VcsTreeModelData.exactlySelected(browser.viewer)
                .userObjects(Change::class.java)
                .map { ChangeViewDiffRequestProcessor.ChangeWrapper(it) }
        }

        override fun iterateAllChanges(): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
            return displayedChanges().map { ChangeViewDiffRequestProcessor.ChangeWrapper(it) }
        }

        override fun showAllChangesForEmptySelection(): Boolean = false

        override fun selectChange(change: ChangeViewDiffRequestProcessor.Wrapper) {
            browser.selectEntries(listOf(change.userObject))
        }

        private fun displayedChanges(): List<Change> {
            val treeChanges = VcsTreeModelData.all(browser.viewer).userObjects(Change::class.java)
            return treeChanges.ifEmpty { changes }
        }
    }
}
