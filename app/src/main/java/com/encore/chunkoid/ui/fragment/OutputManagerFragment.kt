package com.encore.chunkoid.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.encore.chunkoid.R
import com.encore.chunkoid.databinding.FragmentOutputManagerBinding
import com.encore.chunkoid.model.WorldInfo
import com.encore.chunkoid.ui.activity.SettingsActivity
import com.encore.chunkoid.ui.adapter.WorldCardAdapter
import com.encore.chunkoid.utils.BackgroundUtils
import com.encore.chunkoid.utils.ToastUtils
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OutputManagerFragment : Fragment() {

    private var _binding: FragmentOutputManagerBinding? = null
    private val binding get() = _binding!!

    private lateinit var worldAdapter: WorldCardAdapter
    private val worlds = mutableListOf<WorldInfo>()

    private var pendingExportWorld: WorldInfo? = null
    private var pendingExportIsMcworld: Boolean = false
    private var pendingExportIsFolder: Boolean = false

    companion object {
        private const val CHUNKOID_OUTPUT_DIR = "chunkoid output"
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { destinationUri ->
            pendingExportWorld?.let { world ->
                exportToUri(world, destinationUri)
            }
        }
        pendingExportWorld = null
        pendingExportIsMcworld = false
    }

    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUri ->
            pendingExportWorld?.let { world ->
                exportFolderToTree(world, treeUri)
            }
        }
        pendingExportWorld = null
        pendingExportIsFolder = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOutputManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        view.findViewById<View>(R.id.btn_go_to_converter)?.setOnClickListener {
            (activity as? com.encore.chunkoid.ui.activity.MainActivity)?.switchToConverterTab()
        }
        if (BackgroundUtils.isCustomBackgroundEnabled(requireContext())) {
            BackgroundUtils.applyUnifiedMonetTheme(
                binding.root as ViewGroup,
                BackgroundUtils.isSystemInDarkTheme(requireContext()),
                requireContext()
            )
        }
        loadWorlds()
    }

    private fun setupRecyclerView() {
        worldAdapter = WorldCardAdapter(
            worlds = worlds,
            onExportClick = { world, action -> handleExportClick(world, action) }
        )
        binding.rvWorlds.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = worldAdapter
        }
    }

    private fun setupFab() {
        binding.fabStartGame.setOnClickListener { showGameLauncherMenu() }
    }

    override fun onResume() {
        super.onResume()
        loadWorlds()
    }

    private fun loadWorlds() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val chunkoidOutputDir = File(documentsDir, CHUNKOID_OUTPUT_DIR)

            if (!chunkoidOutputDir.exists() || !chunkoidOutputDir.isDirectory) {
                withContext(Dispatchers.Main) {
                    worlds.clear()
                    showEmptyState()
                }
                return@launch
            }

            val loadedList = mutableListOf<WorldInfo>()
            chunkoidOutputDir.listFiles()?.filter { it.isDirectory }?.forEach { worldDir ->
                val convertInfoFile = File(worldDir, "Convertinfo.txt")
                if (convertInfoFile.exists()) {
                    parseWorldInfo(worldDir, convertInfoFile)?.let { loadedList.add(it) }
                }
            }

            withContext(Dispatchers.Main) {
                worlds.clear()
                worlds.addAll(loadedList)
                if (worlds.isEmpty()) {
                    showEmptyState()
                } else {
                    showWorldList()
                }
            }
        }
    }

    private fun parseWorldInfo(worldDir: File, convertInfoFile: File): WorldInfo? {
        return try {
            val lines = convertInfoFile.readLines()
            var platform = ""
            var version = ""
            var levelname = worldDir.name

            for (line in lines) {
                when {
                    line.startsWith("platform ", ignoreCase = true) -> {
                        platform = line.substringAfter("platform ").trim()
                    }
                    line.startsWith("version ", ignoreCase = true) -> {
                        version = line.substringAfter("version ").trim()
                    }
                    line.startsWith("levelname ", ignoreCase = true) -> {
                        levelname = line.substringAfter("levelname ").trim()
                    }
                }
            }

            if (platform.isNotEmpty() && version.isNotEmpty()) {
                WorldInfo(
                    name = levelname,
                    directoryPath = worldDir.absolutePath,
                    platform = platform,
                    version = version
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showEmptyState() {
        binding.rvWorlds.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.fabStartGame.visibility = View.GONE
    }

    private fun showWorldList() {
        binding.rvWorlds.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.fabStartGame.visibility = View.VISIBLE
        worldAdapter.notifyDataSetChanged()
    }

    private fun handleExportClick(world: WorldInfo, action: WorldCardAdapter.ExportAction) {
        when (action) {
            WorldCardAdapter.ExportAction.EXPORT_MCWORLD -> startExport(world, isMcworld = true)
            WorldCardAdapter.ExportAction.EXPORT_ZIP -> startExport(world, isMcworld = false)
            WorldCardAdapter.ExportAction.EXPORT_FOLDER -> startFolderExport(world)
            WorldCardAdapter.ExportAction.VIEW_LOG -> viewConversionLog(world)
            WorldCardAdapter.ExportAction.DELETE -> deleteWorld(world)
        }
    }

    private fun startFolderExport(world: WorldInfo) {
        pendingExportWorld = world
        pendingExportIsFolder = true
        openDocumentTreeLauncher.launch(null)
    }

    private fun startExport(world: WorldInfo, isMcworld: Boolean) {
        pendingExportWorld = world
        pendingExportIsMcworld = isMcworld
        val extension = if (isMcworld) ".mcworld" else ".zip"
        createDocumentLauncher.launch(world.name + extension)
    }

    private fun exportToUri(world: WorldInfo, destinationUri: Uri) {
        val sourceDir = File(world.directoryPath)

        try {
            requireContext().contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    sourceDir.walkTopDown().forEach { file ->
                        val relativePath = file.relativeTo(sourceDir)
                        if (!file.isDirectory) {
                            zos.putNextEntry(ZipEntry(relativePath.toString()))
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
            ToastUtils.show(requireContext(), getString(R.string.export_success))
        } catch (e: Exception) {
            ToastUtils.show(requireContext(), getString(R.string.export_failed) + ": ${e.message}", isError = true)
        }
    }

    private fun exportFolderToTree(world: WorldInfo, treeUri: Uri) {
        try {
            val sourceDir = File(world.directoryPath)
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                ToastUtils.show(requireContext(), "Zdrojová složka neexistuje", isError = true)
                return
            }

            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(
                treeUri,
                takeFlags
            )

            val targetRoot = DocumentFile.fromTreeUri(requireContext(), treeUri)
                ?: run {
                    ToastUtils.show(requireContext(), "K cílové složce nelze získat přístup", isError = true)
                    return
                }

            val targetWorldDir = targetRoot.createDirectory(world.name)
                ?: run {
                    ToastUtils.show(requireContext(), "Cílovou složku nelze vytvořit", isError = true)
                    return
                }

            copyDirectoryToDocumentFile(sourceDir, targetWorldDir)
            ToastUtils.show(requireContext(), getString(R.string.export_success))
        } catch (e: Exception) {
            ToastUtils.show(requireContext(), getString(R.string.export_failed) + ": ${e.message}", isError = true)
        }
    }

    private fun copyDirectoryToDocumentFile(sourceDir: File, targetDir: DocumentFile) {
        sourceDir.listFiles()?.forEach { sourceFile ->
            if (sourceFile.isDirectory) {
                val newTargetDir = targetDir.createDirectory(sourceFile.name)
                if (newTargetDir != null) {
                    copyDirectoryToDocumentFile(sourceFile, newTargetDir)
                }
            } else {
                val targetFile = targetDir.createFile("*/*", sourceFile.name)
                if (targetFile != null) {
                    requireContext().contentResolver.openOutputStream(targetFile.uri)?.use { outputStream ->
                        FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }
        }
    }

    private fun viewConversionLog(world: WorldInfo) {
        try {
            val logFile = File(world.directoryPath, "converse_log.txt")
            if (!logFile.exists() || !logFile.isFile) {
                ToastUtils.show(requireContext(), "Protokol převodu neexistuje", isError = true)
                return
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                requireContext().packageName + ".fileprovider",
                logFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                ToastUtils.show(requireContext(), "Nenašla se aplikace pro otevření textového souboru", isError = true)
            }
        } catch (e: Exception) {
            ToastUtils.show(requireContext(), "Otevření protokolu se nezdařilo: ${e.message}", isError = true)
        }
    }

    private fun deleteWorld(world: WorldInfo) {
        try {
            val worldDir = File(world.directoryPath)
            if (worldDir.exists() && worldDir.isDirectory) {
                worldDir.deleteRecursively()
                worlds.remove(world)
                worldAdapter.notifyDataSetChanged()
                if (worlds.isEmpty()) {
                    showEmptyState()
                }
                ToastUtils.show(requireContext(), getString(R.string.delete_world) + ": " + world.name)
            }
        } catch (e: Exception) {
            ToastUtils.show(requireContext(), "Odstranění se nezdařilo: ${e.message}", isError = true)
        }
    }

    private fun showGameLauncherMenu() {
        data class GameInfo(val iconRes: Int, val packageName: String, val displayName: String, val subtitle: String)

        val games = listOf(
            GameInfo(R.drawable.ne, "com.netease.x19", "Bedrock Edition: NetEase", "Klient Minecraft NetEase"),
            GameInfo(R.drawable.be, "com.mojang.minecraftpe", "Bedrock Edition: mezinárodní verze", "Mojang Minecraft PE"),
            GameInfo(R.drawable.pojav, "net.kdt.pojavlaunch", "Java Edition: Pojav", "Spouštěč PojavLauncher"),
            GameInfo(R.drawable.fcl, "com.tungsten.fcl", "Java Edition: Fcl", "Spouštěč Fold Craft Launcher"),
            GameInfo(R.drawable.zl2, "com.movtery.zalithlauncher.v2", "Java Edition: ZL2", "Spouštěč Zalith Launcher")
        )

        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_game_launcher, null)
        val container = view.findViewById<LinearLayout>(R.id.ll_launcher_items)
        val pm = requireContext().packageManager

        val primaryColor = BackgroundUtils.getExtractedPrimaryColor(requireContext()) ?: ContextCompat.getColor(requireContext(), R.color.md_theme_primary)

        for (game in games) {
            val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.launcher_menu_item, container, false)
            val ivIcon = itemView.findViewById<ImageView>(R.id.iv_icon)
            val tvTitle = itemView.findViewById<TextView>(R.id.tv_title)
            val tvSubtitle = itemView.findViewById<TextView>(R.id.tv_subtitle)
            val tvStatus = itemView.findViewById<TextView>(R.id.tv_status)

            ivIcon.setImageResource(game.iconRes)
            tvTitle.text = game.displayName
            tvSubtitle.text = game.subtitle

            val isInstalled = pm.getLaunchIntentForPackage(game.packageName) != null
            if (isInstalled) {
                tvStatus.text = "Nainstalováno"
                tvStatus.setTextColor(primaryColor)
            } else {
                tvStatus.text = "Nenainstalováno"
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_outline))
            }

            itemView.setOnClickListener {
                bottomSheetDialog.dismiss()
                launchMinecraft(game.packageName)
            }

            container.addView(itemView)
        }

        bottomSheetDialog.setContentView(view)
        BackgroundUtils.applyDialogTheme(bottomSheetDialog, requireContext(), view)
        bottomSheetDialog.show()
    }

    private fun launchMinecraft(packageName: String) {
        try {
            val intent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                ToastUtils.show(requireContext(), getString(R.string.app_not_installed), isError = true)
            }
        } catch (e: Exception) {
            ToastUtils.show(requireContext(), getString(R.string.launch_failed) + ": ${e.message}", isError = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
