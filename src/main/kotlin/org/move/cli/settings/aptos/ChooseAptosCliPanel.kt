package org.move.cli.settings.aptos

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.SPEEDSEARCH
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.layout.selected
import org.move.cli.sdks.sdksService
import org.move.cli.settings.VersionLabel
import org.move.cli.settings.aptos.AptosExecType.BUNDLED
import org.move.cli.settings.aptos.AptosExecType.LOCAL
import org.move.cli.settings.isValidExecutable
import org.move.ide.actions.DownloadAptosSDKAction
import org.move.ide.notifications.logOrShowBalloon
import org.move.ide.notifications.showBalloon
import org.move.openapiext.PluginPathManager
import org.move.openapiext.pathField
import org.move.stdext.blankToNull
import org.move.stdext.toPathOrNull
import java.nio.file.Path

enum class AptosExecType {
    BUNDLED,
    LOCAL;

    companion object {
        val isPreCompiledSupportedForThePlatform: Boolean
            get() {
                if (Registry.`is`("org.move.aptos.bundled.force.supported")) {
                    return true
                }
                if (Registry.`is`("org.move.aptos.bundled.force.unsupported")) {
                    return false
                }
                return !SystemInfo.isMac
            }

        fun bundledPath(): String? = PluginPathManager.bundledAptosCli

        fun aptosExecPath(execType: AptosExecType, localAptosPath: String?): Path? {
            val pathCandidate =
                when (execType) {
                    BUNDLED -> bundledPath()?.toPathOrNull()
                    LOCAL -> localAptosPath?.blankToNull()?.toPathOrNull()
                }
            return pathCandidate?.takeIf { it.isValidExecutable() }
        }
    }
}

class ChooseAptosCliPanel(versionUpdateListener: (() -> Unit)?): Disposable {

    data class Data(
        val aptosExecType: AptosExecType,
        val localAptosPath: String?
    )

    var data: Data
        get() {
            val execType = if (bundledRadioButton.isSelected) BUNDLED else LOCAL
            val path = localPathField.text.blankToNull()
            return Data(
                aptosExecType = execType,
                localAptosPath = path
            )
        }
        set(value) {
            when (value.aptosExecType) {
                BUNDLED -> {
                    bundledRadioButton.isSelected = true
                    localRadioButton.isSelected = false
                }
                LOCAL -> {
                    bundledRadioButton.isSelected = false
                    localRadioButton.isSelected = true
                }
            }
            localPathField.text = value.localAptosPath ?: ""
            updateVersion()
        }

    private val localPathField =
        pathField(
            FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor(),
            this,
            "Choose Aptos CLI",
            onTextChanged = { _ ->
                updateVersion()
            })
    private val versionLabel = VersionLabel(this, versionUpdateListener)

    private val bundledRadioButton = JBRadioButton("Bundled")
    private val localRadioButton = JBRadioButton("Local")

    private val downloadPrecompiledBinaryAction = DownloadAptosSDKAction().also {
        it.onFinish = { sdk ->
            localPathField.text = sdk.targetFile.toString()
            updateVersion()
        }
    }
    private val popupActionGroup = DefaultActionGroup(
        listOfNotNull(
            if (AptosExecType.isPreCompiledSupportedForThePlatform) downloadPrecompiledBinaryAction else null
        )
    )
    private val getAptosActionLink =
        DropDownLink("Get Aptos") { dropDownLink ->
            val dataContext = DataManager.getInstance().getDataContext(dropDownLink)
            JBPopupFactory.getInstance().createActionGroupPopup(
                null,
                popupActionGroup,
                dataContext,
                SPEEDSEARCH,
                false,
                null,
                -1,
                { _ -> false },
                null
            )
        }


    fun attachToLayout(layout: Panel): Row {
        val resultRow = with(layout) {
            group("Aptos CLI") {
                buttonsGroup {
                    row {
                        cell(bundledRadioButton)
                            .enabled(AptosExecType.isPreCompiledSupportedForThePlatform)
                            .actionListener { _, _ ->
                                updateVersion()
                            }
                    }
                    row {
                        comment(
                            "Bundled version is not available for MacOS. Refer to the " +
                                    "<a href=\"https://aptos.dev/tools/aptos-cli/install-cli/install-cli-mac\">Official Aptos CLI docs</a> " +
                                    "on how to install it on your platform."
                        )
                            .visible(!AptosExecType.isPreCompiledSupportedForThePlatform)
                    }
                    row {
                        cell(localRadioButton)
                            .actionListener { _, _ ->
                                updateVersion()
                            }
                        cell(localPathField)
                            .enabledIf(localRadioButton.selected)
                            .align(AlignX.FILL)
                            .resizableColumn()
                        if (popupActionGroup.childrenCount != 0) {
                            cell(getAptosActionLink)
                                .enabledIf(localRadioButton.selected)
                        }
                    }
                    row("--version :") { cell(versionLabel) }
//                    row {
//                        comment(
//                            "Bundled version of the Aptos CLI can be outdated. Refer to the " +
//                                    "<a href=\"https://aptos.dev/tools/aptos-cli/install-cli\">Official Aptos CLI docs</a> " +
//                                    "on how to install and update new version for your platform."
//                        )
//                            .visible(AptosExecType.isPreCompiledSupportedForThePlatform)
//                    }
                }
            }
        }
        updateVersion()
        return resultRow
    }

    private fun updateVersion() {
        val aptosPath =
            when {
                bundledRadioButton.isSelected -> AptosExecType.bundledPath()
                else -> localPathField.text
            }?.toPathOrNull()
        versionLabel.updateAndNotifyListeners(aptosPath)
    }

    fun updateAptosSdks(sdkPath: String) {
        if (sdkPath == "") return

        // do not save if the executable has no `--version`
        if (versionLabel.isError()) return

        // do not save if it's not an aptos
        if ("aptos" !in versionLabel.text) return

        val settingsService = sdksService()
        if (sdkPath in settingsService.state.aptosSdkPaths) return

        settingsService.state.aptosSdkPaths.add(sdkPath)

        LOG.logOrShowBalloon("Aptos SDK saved: $sdkPath")
    }

    override fun dispose() {
        Disposer.dispose(localPathField)
    }

    companion object {
        private val LOG = logger<ChooseAptosCliPanel>()
    }
}
