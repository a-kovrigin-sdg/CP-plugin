package com.github.akovriginsdg.cpplugin.actions

import com.github.akovriginsdg.cpplugin.PluginConst
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.util.*

open class CreateDomainInjectionAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (project == null || file == null || !file.isDirectory) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val relativePath = getPathRelativeToRoot(project, file) ?: return

        val isInsideDomain = relativePath == PluginConst.DOMAIN_ROOT_DIR ||
                relativePath.startsWith("${PluginConst.DOMAIN_ROOT_DIR}/")
        val isInsideWeb = relativePath.startsWith(PluginConst.DATING_WEB_ROOT)
        val isInsideMobile = relativePath.startsWith(PluginConst.DATING_MOBILE_ROOT)

        e.presentation.isEnabledAndVisible = isInsideDomain && !isInsideWeb && !isInsideMobile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentDirectory = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val projectRoot = project.guessProjectDir() ?: return

        val inputName = getUserInput(project) ?: return

        generateDomainFiles(project, currentDirectory, inputName, projectRoot)
    }

    protected open fun getUserInput(project: Project): String? {
        return Messages.showInputDialog(
            project,
            "Enter name (PascalCase for Class, camelCase for Function):",
            "Create Domain Injection",
            Messages.getQuestionIcon()
        )
    }

    protected open fun getTemplate(project: Project, templateName: String): FileTemplate? {
        val manager = FileTemplateManager.getInstance(project)
        return try {
            manager.getJ2eeTemplate(templateName)
        } catch (e: Exception) {
            try { manager.getTemplate(templateName) } catch (ignored: Exception) { null }
        }
    }

    open fun generateDomainFiles(
        project: Project,
        currentDirectory: VirtualFile,
        inputName: String,
        projectRoot: VirtualFile
    ) {
        val isFunction = inputName.first().isLowerCase()
        val camelName = inputName.replaceFirstChar { it.lowercase(Locale.getDefault()) }
        val pascalName = inputName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val kebabName = toKebabCase(pascalName)

        val relativePathFromRoot = VfsUtil.getRelativePath(currentDirectory, projectRoot) ?: ""
        val subPath = relativePathFromRoot.removePrefix(PluginConst.DOMAIN_ROOT_DIR).removePrefix("/")

        val importPathSuffix = if (subPath.isNotEmpty()) "$subPath/$kebabName" else kebabName
        val contractImportPath = if (subPath.isNotEmpty()) "$subPath/$kebabName.contracts" else "$kebabName.contracts"

        val props = Properties(FileTemplateManager.getInstance(project).defaultProperties)
        props.setProperty("TYPE_NAME", pascalName)
        props.setProperty("FUNC_NAME", camelName)
        props.setProperty("KEBAB_NAME", kebabName)
        props.setProperty("DOMAIN_PREFIX", PluginConst.DOMAIN_IMPORT_PREFIX)
        props.setProperty("IMPORT_PATH", importPathSuffix)
        props.setProperty("CONTRACT_IMPORT", contractImportPath)

        val contractTplName = if (isFunction) PluginConst.TPL_DOMAIN_CONTRACTS_FUNCTION else PluginConst.TPL_DOMAIN_CONTRACTS_CLASS
        val implTplName = if (isFunction) PluginConst.TPL_DOMAIN_IMPL_FUNCTION else PluginConst.TPL_DOMAIN_IMPL_CLASS

        val tplContracts = getTemplate(project, contractTplName)
        val tplAggregator = getTemplate(project, PluginConst.TPL_DOMAIN_AGGREGATOR)
        val tplImpl = getTemplate(project, implTplName)

        if (tplContracts == null || tplAggregator == null || tplImpl == null) {
            Messages.showErrorDialog(project, "Some templates not found. Check Settings.", "Error")
            return
        }

        val psiManager = PsiManager.getInstance(project)
        val currentPsiDir = psiManager.findDirectory(currentDirectory) ?: return

        runWriteGeneration(
            project,
            projectRoot,
            currentPsiDir,
            kebabName,
            subPath,
            props,
            tplContracts,
            tplAggregator,
            tplImpl
        )
    }

    /**
     * Этот метод инкапсулирует WriteCommandAction и создание файлов.
     * Мы переопределим его в тесте, чтобы проверить аргументы, не вызывая реальный Velocity.
     */
    protected open fun runWriteGeneration(
        project: Project,
        projectRoot: VirtualFile,
        currentPsiDir: PsiDirectory,
        kebabName: String,
        subPath: String,
        props: Properties,
        tplContracts: FileTemplate,
        tplAggregator: FileTemplate,
        tplImpl: FileTemplate
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val contractsName = "$kebabName.contracts"
                val createdContracts = createFileFromTemplate(currentPsiDir, tplContracts, contractsName, props)

                val mainName = kebabName
                val createdMain = createFileFromTemplate(currentPsiDir, tplAggregator, mainName, props)

                val webPathFull = "${PluginConst.WEB_DOMAIN_PATH}/$subPath".trimEnd('/')
                val mobilePathFull = "${PluginConst.MOBILE_DOMAIN_PATH}/$subPath".trimEnd('/')

                val createdWeb = createInjectionTargetFile(project, projectRoot, webPathFull, kebabName, tplImpl, props)
                val createdMobile = createInjectionTargetFile(project, projectRoot, mobilePathFull, kebabName, tplImpl, props)

                if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val filesToOpen = listOfNotNull(createdContracts, createdWeb, createdMobile)
                    filesToOpen.forEach { fileEditorManager.openFile(it.virtualFile, false) }
                    if (createdMain != null) fileEditorManager.openFile(createdMain.virtualFile, true)
                }

            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Error creating files: ${ex.message}", "Error")
            }
        }
    }

    private fun createFileFromTemplate(
        dir: PsiDirectory,
        template: FileTemplate,
        fileName: String,
        props: Properties
    ): PsiFile? {
        if (dir.findFile("$fileName.ts") != null) return null

        val element = FileTemplateUtil.createFromTemplate(template, fileName, props, dir)
        return element as? PsiFile
    }

    private fun createInjectionTargetFile(
        project: Project,
        root: VirtualFile,
        relativePath: String,
        fileName: String,
        template: FileTemplate,
        props: Properties
    ): PsiFile? {
        val targetDirVfs = VfsUtil.createDirectoryIfMissing(root, relativePath) ?: return null
        val targetDirPsi = PsiManager.getInstance(project).findDirectory(targetDirVfs) ?: return null
        return createFileFromTemplate(targetDirPsi, template, fileName, props)
    }

    private fun getPathRelativeToRoot(project: Project, file: VirtualFile): String? {
        val baseDir = project.guessProjectDir() ?: return null
        return VfsUtil.getRelativePath(file, baseDir)
    }

    private fun toKebabCase(str: String): String {
        return str.replace(Regex("([a-z])([A-Z]+)"), "$1-$2").lowercase(Locale.getDefault())
    }
}