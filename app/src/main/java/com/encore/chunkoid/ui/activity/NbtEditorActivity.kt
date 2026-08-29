package com.encore.chunkoid.ui.activity

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import com.encore.chunkoid.core.leveldb.LevelDbCategory
import com.encore.chunkoid.ui.adapter.LevelDbRecordAdapter
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import com.google.android.material.tabs.TabLayout
import com.google.android.material.appbar.MaterialToolbar
import android.widget.Toast
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.ProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.gamemods.nbtmanipulator.*
import com.encore.chunkoid.R
import com.encore.chunkoid.databinding.ActivityNbtEditorBinding
import com.encore.chunkoid.databinding.DialogAddNbtTagBinding
import com.encore.chunkoid.databinding.DialogEditNbtValueBinding
import com.encore.chunkoid.core.leveldb.BedrockLevelDbHelper
import com.encore.chunkoid.core.leveldb.LevelDbRecord
import com.encore.chunkoid.ui.adapter.ChunkListAdapter
import com.encore.chunkoid.ui.nbt.NbtTreeAdapter
import com.encore.chunkoid.ui.nbt.NbtTreeNode
import com.encore.chunkoid.ui.view.ChunkGridView
import com.encore.chunkoid.utils.BackgroundUtils
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import android.widget.ViewFlipper

class NbtEditorActivity : AppCompatActivity() {

    private lateinit var binding: com.encore.chunkoid.databinding.ActivityNbtEditorBinding
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var toolbar: MaterialToolbar
    private lateinit var treeAdapter: NbtTreeAdapter

    private var currentFileUri: Uri? = null
    private var currentFilePath: String? = null
    private var currentFileName: String = ""
    private var currentNbtFile: NbtFile? = null

    private var isCompressed: Boolean = true
    private var isLittleEndian: Boolean = false
    private var readHeaders: Boolean = false
    private var headerVersion: Int? = null

    private var isDirty: Boolean = false
    private var originalZipBytes: ByteArray? = null
    private var selectedZipEntryName: String? = null
    private val workspaceZipEntries = mutableMapOf<String, ByteArray>()
    private var activeWorkspaceName: String = ""

    private var currentAnvilRegionFile: AnvilRegionFile? = null
    private var currentRegionCachedFile: File? = null
    private var currentChunkX: Int = 0
    private var currentChunkZ: Int = 0

    private var currentLevelDbHelper: BedrockLevelDbHelper? = null
    private var currentLevelDbRecord: LevelDbRecord? = null
    private var currentLevelDbCachedFolder: File? = null
    private var levelDbAdapter: LevelDbRecordAdapter? = null
    @Volatile private var cachedLevelDbRecords: List<com.encore.chunkoid.core.leveldb.LevelDbRecord> = emptyList()
    private var isLoadingRecords: Boolean = false

    private var levelDbRefreshTrigger: ((Boolean) -> Unit)? = null

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadFromUri(uri)
            }
        }
    }

    private val saveAsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { saveToUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNbtEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewFlipper = binding.viewFlipper
        toolbar = binding.toolbar

        setupWorkspaceBrowser()
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        setupBackPressHandler()

        if (intent.data != null) {
            loadFromUri(intent.data!!)
        } else if (intent.hasExtra("file_path")) {
            val path = intent.getStringExtra("file_path")
            if (path != null) {
                loadFromFile(File(path))
            }
        } else {
            restoreWorkspaceSession()
            updateWorkspaceData()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            BackgroundUtils.applyCustomBackground(this)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        treeAdapter = NbtTreeAdapter(
            context = this,
            onNodeEdit = { node -> showEditValueDialog(node) },
            onNodeAddChild = { node -> showAddTagDialog(node) },
            onNodeRename = { node -> showRenameTagDialog(node) },
            onNodeDelete = { node -> showDeleteTagDialog(node) },
            onNodeCopyPath = { node -> copyToClipboard(node.path) },
            onNodeCopyValue = { node -> copyToClipboard(node.displayValue) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = treeAdapter
    }

    private fun setupListeners() {
        findViewById<View>(R.id.card_open_nbt_file)?.setOnClickListener {
            openFilePicker()
        }

        findViewById<View>(R.id.card_open_archive)?.setOnClickListener {
            openFilePicker()
        }

        findViewById<View>(R.id.btn_close_workspace)?.setOnClickListener {
            clearWorkspaceSession()
        }

        binding.emptyState.setOnClickListener {
            openFilePicker()
        }

        binding.fabAdd.setOnClickListener {
            currentNbtFile?.tag?.let { rootTag ->
                val rootKey = currentNbtFile?.name?.ifEmpty { "Root" } ?: "Root"
                val rootNode = NbtTreeNode(
                    key = rootKey,
                    tag = rootTag,
                    parentTag = null,
                    depth = 0,
                    path = rootKey
                )
                showAddTagDialog(rootNode)
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExit()
            }
        })
    }

    private fun handleExit() {
        if (viewFlipper.displayedChild == 1 && (currentLevelDbHelper != null || currentAnvilRegionFile != null || workspaceZipEntries.isNotEmpty())) {
            showWorkspaceView()
            return
        }
        if (isDirty && currentNbtFile != null) {
            val autoSave = prefs.getBoolean("nbt_auto_save", false)
            if (autoSave) {
                saveCurrentFile(silent = true)
                finish()
            } else {
                val dialog = MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.nbt_unsaved_title)
                    .setMessage(getString(R.string.nbt_unsaved_message, currentFileName))
                    .setPositiveButton(R.string.nbt_save_and_exit) { _, _ ->
                        saveCurrentFile(silent = true)
                        finish()
                    }
                    .setNegativeButton(R.string.nbt_discard_and_exit) { _, _ ->
                        finish()
                    }
                    .setNeutralButton(R.string.cancel, null)
                    .create()
                BackgroundUtils.applyDialogTheme(dialog, this)
                dialog.show()
            }
        } else {
            finish()
        }
    }
    private var loadingDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showLoadingDialog(message: String = "正在加载地图存档...") {
        runOnUiThread {
            if (loadingDialog?.isShowing == true) return@runOnUiThread
            val progressBar = ProgressBar(this).apply {
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
            loadingDialog = MaterialAlertDialogBuilder(this)
                .setTitle(message)
                .setView(progressBar)
                .setCancelable(false)
                .create()
            BackgroundUtils.applyDialogTheme(loadingDialog!!, this)
            loadingDialog?.show()
        }
    }

    private fun hideLoadingDialog() {
        runOnUiThread {
            loadingDialog?.dismiss()
            loadingDialog = null
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        openFileLauncher.launch(intent)
    }

    private fun isZipArchive(bytes: ByteArray): Boolean {
        return bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())
    }

    private fun loadFromUri(uri: Uri) {
        currentFileUri = uri
        currentFilePath = null
        currentFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "NBT File"

        showLoadingDialog("正在读取文件...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (AnvilRegionFile.isRegionFileName(currentFileName)) {
                    withContext(Dispatchers.Main) {
                        hideLoadingDialog()
                        loadRegionFromUri(uri, currentFileName)
                    }
                    return@launch
                }

                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    withContext(Dispatchers.Main) {
                        hideLoadingDialog()
                        Toast.makeText(this@NbtEditorActivity, R.string.nbt_load_failed, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                if (isZipArchive(bytes)) {
                    handleZipArchiveAsync(bytes, currentFileName)
                } else {
                    withContext(Dispatchers.Main) {
                        hideLoadingDialog()
                        originalZipBytes = null
                        selectedZipEntryName = null
                        workspaceZipEntries.clear()
                        activeWorkspaceName = ""
                        currentAnvilRegionFile?.close()
                        currentAnvilRegionFile = null
                        currentLevelDbHelper?.close()
                        currentLevelDbHelper = null
                        currentLevelDbRecord = null
                        updateWorkspaceUi()
                        parseNbtBytes(bytes, currentFileName)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    Toast.makeText(this@NbtEditorActivity, "${getString(R.string.nbt_load_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadFromFile(file: File) {
        currentFileUri = null
        currentFilePath = file.absolutePath
        currentFileName = file.name

        showLoadingDialog("正在加载存档...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {

                if (file.isDirectory && (File(file, "CURRENT").exists() || file.name.equals("db", true))) {
                    loadLevelDbFromFolderAsync(file)
                    return@launch
                }

                if (file.isFile && (file.name == "CURRENT" || file.name.endsWith(".ldb") || file.name.endsWith(".log"))) {
                    val parent = file.parentFile
                    if (parent != null && (parent.name.equals("db", true) || File(parent, "CURRENT").exists())) {
                        loadLevelDbFromFolderAsync(parent)
                        return@launch
                    }
                }

                if (AnvilRegionFile.isRegionFileName(currentFileName)) {
                    withContext(Dispatchers.Main) {
                        hideLoadingDialog()
                        loadRegionFromFile(file)
                    }
                    return@launch
                }

                val bytes = file.readBytes()
                if (isZipArchive(bytes)) {
                    handleZipArchiveAsync(bytes, currentFileName)
                } else {
                    withContext(Dispatchers.Main) {
                        hideLoadingDialog()
                        originalZipBytes = null
                        selectedZipEntryName = null
                        workspaceZipEntries.clear()
                        activeWorkspaceName = ""
                        currentAnvilRegionFile?.close()
                        currentAnvilRegionFile = null
                        currentLevelDbHelper?.close()
                        currentLevelDbHelper = null
                        currentLevelDbRecord = null
                        updateWorkspaceUi()
                        parseNbtBytes(bytes, currentFileName)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    Toast.makeText(this@NbtEditorActivity, "${getString(R.string.nbt_load_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun loadLevelDbFromFolderAsync(folder: File) {
        try {
            val cacheFolder = File(cacheDir, "nbt_active_leveldb")
            if (cacheFolder.exists()) cacheFolder.deleteRecursively()
            cacheFolder.mkdirs()

            folder.listFiles()?.forEach { f ->
                if (!f.isDirectory && f.name != "LOCK") {
                    f.copyTo(File(cacheFolder, f.name), overwrite = true)
                }
            }

            currentLevelDbCachedFolder = cacheFolder
            currentLevelDbHelper?.close()
            currentLevelDbHelper = BedrockLevelDbHelper(cacheFolder)
            currentAnvilRegionFile?.close()
            currentAnvilRegionFile = null
            originalZipBytes = null
            selectedZipEntryName = null
            workspaceZipEntries.clear()
            activeWorkspaceName = folder.name

            withContext(Dispatchers.Main) {
                hideLoadingDialog()
                showWorkspaceView()
                updateWorkspaceData()
                updateWorkspaceUi()
                reloadLevelDbCacheAsync()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                hideLoadingDialog()
                Toast.makeText(this@NbtEditorActivity, "解析 LevelDB 失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadRegionFromUri(uri: Uri, fileName: String) {
        try {
            val cacheRegion = File(cacheDir, "nbt_active_region.mca")
            contentResolver.openInputStream(uri)?.use { input ->
                cacheRegion.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            currentRegionCachedFile = cacheRegion
            currentAnvilRegionFile?.close()
            currentAnvilRegionFile = AnvilRegionFile(cacheRegion)
            currentLevelDbHelper?.close()
            currentLevelDbHelper = null
            currentLevelDbRecord = null
            originalZipBytes = null
            selectedZipEntryName = null
            workspaceZipEntries.clear()
            activeWorkspaceName = fileName

            showChunkPickerDialog()
        } catch (e: Exception) {
            Toast.makeText(this, "解析区块错误: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadRegionFromFile(file: File) {
        try {
            val cacheRegion = File(cacheDir, "nbt_active_region.mca")
            file.copyTo(cacheRegion, overwrite = true)
            currentRegionCachedFile = cacheRegion
            currentAnvilRegionFile?.close()
            currentAnvilRegionFile = AnvilRegionFile(cacheRegion)
            currentLevelDbHelper?.close()
            currentLevelDbHelper = null
            currentLevelDbRecord = null
            originalZipBytes = null
            selectedZipEntryName = null
            workspaceZipEntries.clear()
            activeWorkspaceName = file.name

            showChunkPickerDialog()
        } catch (e: Exception) {
            Toast.makeText(this, "解析区块错误: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun handleZipArchiveAsync(bytes: ByteArray, archiveName: String, targetEntry: String? = null) {
        workspaceZipEntries.clear()
        var hasLevelDb = false

        val cacheFolder = File(cacheDir, "nbt_active_leveldb")
        if (cacheFolder.exists()) cacheFolder.deleteRecursively()

        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        if (name.startsWith("db/") || name.startsWith("db\\")) {
                            hasLevelDb = true
                            val relName = name.substringAfter("db/").substringAfter("db\\")
                            if (relName.isNotEmpty() && relName != "LOCK") {
                                cacheFolder.mkdirs()
                                val targetFile = File(cacheFolder, relName)
                                targetFile.outputStream().use { out ->
                                    zis.copyTo(out)
                                }
                            }
                        } else if (name.endsWith(".dat", ignoreCase = true) ||
                            name.endsWith(".nbt", ignoreCase = true) ||
                            name.endsWith(".dat_old", ignoreCase = true) ||
                            name.endsWith(".mca", ignoreCase = true) ||
                            name.endsWith(".mcr", ignoreCase = true) ||
                            name.equals("levelname.txt", ignoreCase = true)) {
                            workspaceZipEntries[name] = zis.readBytes()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (hasLevelDb) {
            workspaceZipEntries["db/ (LevelDB 数据库)"] = byteArrayOf()
        }

        this.originalZipBytes = bytes
        this.activeWorkspaceName = archiveName
        saveWorkspaceToCache(bytes, archiveName)

        if (hasLevelDb) {
            this.currentLevelDbCachedFolder = cacheFolder
            this.currentLevelDbHelper?.close()
            this.currentLevelDbHelper = BedrockLevelDbHelper(cacheFolder)
            this.selectedZipEntryName = "db/ (LevelDB 数据库)"
            this.currentFileName = "db/"
        }

        withContext(Dispatchers.Main) {
            hideLoadingDialog()
            showWorkspaceView()
            updateWorkspaceData()
            updateWorkspaceUi()

            if (hasLevelDb) {
                reloadLevelDbCacheAsync()
            }
        }
    }
    private fun getSortedWorkspaceEntryNames(): Array<String> {
        return workspaceZipEntries.keys.sortedWith(Comparator { a, b ->
            when {
                a.startsWith("db/") -> -1
                b.startsWith("db/") -> 1
                a.equals("level.dat", true) || a.endsWith("/level.dat", true) -> -1
                b.equals("level.dat", true) || b.endsWith("/level.dat", true) -> 1
                a.endsWith(".mca", true) && !b.endsWith(".mca", true) -> 1
                !a.endsWith(".mca", true) && b.endsWith(".mca", true) -> -1
                else -> a.compareTo(b)
            }
        }).toTypedArray()
    }

    private fun switchToFileInWorkspace(entryName: String) {
        if (entryName.startsWith("db/")) {
            val zipBytes = originalZipBytes ?: return
            this.selectedZipEntryName = entryName
            this.currentFileName = "db/"
            isDirty = false
            prefs.edit().putString("nbt_last_entry", entryName).apply()

            try {
                val cacheFolder = File(cacheDir, "nbt_active_leveldb")
                if (cacheFolder.exists()) cacheFolder.deleteRecursively()
                cacheFolder.mkdirs()

                ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && (entry.name.startsWith("db/") || entry.name.startsWith("db\\"))) {
                            val relName = entry.name.substringAfter("db/").substringAfter("db\\")
                            if (relName.isNotEmpty() && relName != "LOCK") {
                                val targetFile = File(cacheFolder, relName)
                                targetFile.outputStream().use { out ->
                                    zis.copyTo(out)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                currentLevelDbCachedFolder = cacheFolder
                currentLevelDbHelper?.close()
                currentLevelDbHelper = BedrockLevelDbHelper(cacheFolder)
                currentAnvilRegionFile?.close()
                currentAnvilRegionFile = null
                updateWorkspaceUi()
                showLevelDbBrowserDialog()
            } catch (e: Exception) {
                Toast.makeText(this, "解析区块错误: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }

        val entryBytes = workspaceZipEntries[entryName] ?: return
        this.selectedZipEntryName = entryName
        this.currentFileName = entryName.substringAfterLast('/')
        isDirty = false
        prefs.edit().putString("nbt_last_entry", entryName).apply()

        if (AnvilRegionFile.isRegionFileName(entryName)) {
            try {
                val cacheRegion = File(cacheDir, "nbt_active_region.mca")
                cacheRegion.writeBytes(entryBytes)
                currentRegionCachedFile = cacheRegion
                currentAnvilRegionFile?.close()
                currentAnvilRegionFile = AnvilRegionFile(cacheRegion)
                currentLevelDbHelper?.close()
                currentLevelDbHelper = null
                currentLevelDbRecord = null
                updateWorkspaceUi()
                showChunkPickerDialog()
            } catch (e: Exception) {
                Toast.makeText(this, "未找到该区块数据", Toast.LENGTH_SHORT).show()
            }
        } else {
            currentAnvilRegionFile?.close()
            currentAnvilRegionFile = null
            currentLevelDbHelper?.close()
            currentLevelDbHelper = null
            currentLevelDbRecord = null
            updateWorkspaceUi()
            parseNbtBytes(entryBytes, "$activeWorkspaceName > $entryName")
        }
    }

    private fun showChunkPickerDialog(onDismissAction: (() -> Unit)? = null) {
        val region = currentAnvilRegionFile ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_chunk_picker, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        BackgroundUtils.applyDialogTheme(dialog, this, dialogView)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_region_title)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tv_region_subtitle)
        val toggleTool = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_select_tool)
        val chunkGridView = dialogView.findViewById<ChunkGridView>(R.id.chunk_grid_view)
        val tvSelectedChunkCoord = dialogView.findViewById<TextView>(R.id.tv_selected_chunk_coord)
        val tvSelectedChunkStatus = dialogView.findViewById<TextView>(R.id.tv_selected_chunk_status)
        val tvSelectedBlockRange = dialogView.findViewById<TextView>(R.id.tv_selected_block_range)
        val tvSelectedInhabitedTime = dialogView.findViewById<TextView>(R.id.tv_selected_inhabited_time)
        val btnActionSelectAll = dialogView.findViewById<View>(R.id.btn_action_select_all)
        val btnActionInvert = dialogView.findViewById<View>(R.id.btn_action_invert)
        val btnActionClear = dialogView.findViewById<View>(R.id.btn_action_clear)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel_chunk_picker)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm_select_chunk)
        val btnDeleteChunk = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete_chunk_dialog)
        val btnPrune = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_prune_useless_chunks)

        val rx = region.regionX
        val rz = region.regionZ
        val displayName = currentFileName.ifEmpty { region.file.name }
        tvTitle.text = if (rx != null && rz != null) "$displayName (区块 [$rx, $rz])" else displayName

        val chunkTable = region.getChunkTable()
        val populatedCount = chunkTable.count { it.exists }
        val pct = (populatedCount.toFloat() / 1024f) * 100f
        tvSubtitle.text = getString(R.string.nbt_region_stats, populatedCount, pct)

        chunkGridView.setChunkData(chunkTable)

        toggleTool?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                chunkGridView.selectMode = if (checkedId == R.id.btn_tool_box) {
                    ChunkGridView.SelectMode.BOX
                } else {
                    ChunkGridView.SelectMode.TAP
                }
            }
        }

        btnActionSelectAll?.setOnClickListener { chunkGridView.selectAllPopulated() }
        btnActionInvert?.setOnClickListener { chunkGridView.invertSelection() }
        btnActionClear?.setOnClickListener { chunkGridView.clearSelection() }

        fun updateSelectedDetails(lx: Int, lz: Int, exists: Boolean) {
            val gx = if (rx != null) rx * 32 + lx else lx
            val gz = if (rz != null) rz * 32 + lz else lz
            val bx = gx * 16
            val bz = gz * 16

            tvSelectedChunkCoord.text = if (rx != null && rz != null) {
                "全局区块: [$gx, $gz] (局部: [$lx, $lz])"
            } else {
                "区块: [$lx, $lz]"
            }

            tvSelectedBlockRange.text = "方块范围: [X: $bx..${bx + 15}, Z: $bz..${bz + 15}]"

            val count = chunkGridView.selectedChunks.size
            if (count > 0) {
                tvSelectedChunkStatus.text = "已选中 $count 个区块"
                tvSelectedChunkStatus.setTextColor(ContextCompat.getColor(this, R.color.md_theme_error))
                btnDeleteChunk?.text = "删除所选 ($count)"
                btnDeleteChunk?.visibility = View.VISIBLE
            } else {
                if (exists) {
                    val info = region.getChunkInfo(lx, lz)
                    tvSelectedChunkStatus.text = getString(R.string.nbt_chunk_status_populated, info.sectorCount)
                    tvSelectedChunkStatus.setTextColor(ContextCompat.getColor(this, R.color.md_theme_primary))
                    btnConfirm.text = getString(R.string.nbt_edit_chunk)
                    btnDeleteChunk?.text = "删除此区块"
                    btnDeleteChunk?.visibility = View.VISIBLE

                    val inhabitedTime = region.readChunkInhabitedTime(lx, lz)
                    if (inhabitedTime != null) {
                        tvSelectedInhabitedTime?.visibility = View.VISIBLE
                        if (inhabitedTime == 0L) {
                            tvSelectedInhabitedTime?.text = getString(R.string.nbt_inhabited_time_zero)
                            tvSelectedInhabitedTime?.setTextColor(android.graphics.Color.parseColor("#FF9100"))
                        } else {
                            val seconds = inhabitedTime / 20L
                            tvSelectedInhabitedTime?.text = getString(R.string.nbt_inhabited_time_fmt, inhabitedTime, seconds)
                            tvSelectedInhabitedTime?.setTextColor(ContextCompat.getColor(this, R.color.md_theme_onSurfaceVariant))
                        }
                    } else {
                        tvSelectedInhabitedTime?.visibility = View.GONE
                    }
                } else {
                    tvSelectedChunkStatus.text = getString(R.string.nbt_chunk_status_empty)
                    tvSelectedChunkStatus.setTextColor(ContextCompat.getColor(this, R.color.md_theme_outline))
                    btnConfirm.text = "初始化并编辑"
                    btnDeleteChunk?.text = "删除此区块"
                    btnDeleteChunk?.isEnabled = false
                    btnDeleteChunk?.alpha = 0.4f
                    btnDeleteChunk?.visibility = View.VISIBLE
                    tvSelectedInhabitedTime?.visibility = View.GONE
                }
            }
        }

        chunkGridView.onChunkSelectedListener = { lx, lz, exists ->
            updateSelectedDetails(lx, lz, exists)
        }

        chunkGridView.onSelectionChangedListener = { count, _ ->
            if (count > 0) {
                tvSelectedChunkStatus.text = "已选中 $count 个区块"
                tvSelectedChunkStatus.setTextColor(ContextCompat.getColor(this, R.color.md_theme_error))
                btnDeleteChunk?.text = "删除所选 ($count)"
                btnDeleteChunk?.visibility = View.VISIBLE
            } else {
                val exists = chunkGridView.isChunkPopulated(chunkGridView.selectedX, chunkGridView.selectedZ)
                updateSelectedDetails(chunkGridView.selectedX, chunkGridView.selectedZ, exists)
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            onDismissAction?.invoke()
        }

        btnConfirm.setOnClickListener {
            val lx = chunkGridView.selectedX
            val lz = chunkGridView.selectedZ
            if (lx in 0..31 && lz in 0..31) {
                loadChunkFromRegion(lx, lz)
                dialog.dismiss()
            }
        }

        btnDeleteChunk?.setOnClickListener {
            val selected = chunkGridView.selectedChunks
            if (selected.isNotEmpty()) {
                confirmBatchDeleteChunks(region, selected, chunkGridView, tvSubtitle, ::updateSelectedDetails)
            } else {
                val lx = chunkGridView.selectedX
                val lz = chunkGridView.selectedZ
                if (lx in 0..31 && lz in 0..31 && chunkGridView.isChunkPopulated(lx, lz)) {
                    confirmDeleteChunk(region, lx, lz, chunkGridView, tvSubtitle, ::updateSelectedDetails)
                }
            }
        }

        btnPrune?.setOnClickListener {
            showPruneUselessChunksDialog(region, chunkGridView, tvSubtitle, ::updateSelectedDetails)
        }

        if (chunkGridView.selectedX in 0..31 && chunkGridView.selectedZ in 0..31) {
            updateSelectedDetails(
                chunkGridView.selectedX,
                chunkGridView.selectedZ,
                chunkGridView.isChunkPopulated(chunkGridView.selectedX, chunkGridView.selectedZ)
            )
        }

        dialog.show()
    }

    private fun confirmBatchDeleteChunks(
        region: AnvilRegionFile,
        chunksToDelete: Set<Pair<Int, Int>>,
        chunkGridView: ChunkGridView,
        tvSubtitle: TextView,
        onDetailsUpdated: (Int, Int, Boolean) -> Unit
    ) {
        val count = chunksToDelete.size
        val confirmDialog = MaterialAlertDialogBuilder(this)
            .setTitle("批量删除区块")
            .setMessage("确定要物理删除选中的 $count 个区块吗？\n删除后不可恢复。")
            .setPositiveButton(R.string.ok) { _, _ ->
                try {
                    region.deleteChunks(chunksToDelete)
                    syncRegionFileToStorage()

                    chunkGridView.clearSelection()
                    chunkGridView.setChunkData(region.getChunkTable())

                    val popCount = region.getPopulatedChunks().size
                    val pct = (popCount.toFloat() / 1024f) * 100f
                    tvSubtitle.text = getString(R.string.nbt_region_stats, popCount, pct)
                    val first = region.getPopulatedChunks().firstOrNull()
                    if (first != null) {
                        onDetailsUpdated(first.localX, first.localZ, true)
                    }

                    Toast.makeText(this, "成功删除 $count 个区块", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "批量删除失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        BackgroundUtils.applyDialogTheme(confirmDialog, this)
        confirmDialog.show()
    }

    private fun confirmDeleteChunk(
        region: AnvilRegionFile,
        localX: Int,
        localZ: Int,
        chunkGridView: ChunkGridView,
        tvSubtitle: TextView,
        onDetailsUpdated: (Int, Int, Boolean) -> Unit
    ) {
        val rx = region.regionX
        val rz = region.regionZ
        val gx = if (rx != null) rx * 32 + localX else localX
        val gz = if (rz != null) rz * 32 + localZ else localZ

        val confirmDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nbt_delete_chunk_title)
            .setMessage(getString(R.string.nbt_delete_chunk_confirm, gx, gz))
            .setPositiveButton(R.string.ok) { _, _ ->
                try {
                    region.deleteChunk(localX, localZ)
                    syncRegionFileToStorage()

                    chunkGridView.setChunkPopulated(localX, localZ, false)

                    val popCount = region.getPopulatedChunks().size
                    val pct = (popCount.toFloat() / 1024f) * 100f
                    tvSubtitle.text = getString(R.string.nbt_region_stats, popCount, pct)
                    onDetailsUpdated(localX, localZ, false)

                    Toast.makeText(this, getString(R.string.nbt_delete_chunk_success, gx, gz), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.nbt_delete_chunk_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        BackgroundUtils.applyDialogTheme(confirmDialog, this)
        confirmDialog.show()
    }

    private fun showPruneUselessChunksDialog(
        region: AnvilRegionFile,
        chunkGridView: ChunkGridView,
        tvSubtitle: TextView,
        onDetailsUpdated: (Int, Int, Boolean) -> Unit
    ) {
        showLoadingDialog(getString(R.string.nbt_pruning_scan_title))
        lifecycleScope.launch(Dispatchers.IO) {
            val totalPopulated = region.getPopulatedChunks()
            val uselessList = region.scanUselessChunks(maxInhabitedTimeTicks = 0L)
            val lowTimeList = region.scanUselessChunks(maxInhabitedTimeTicks = 20L)

            withContext(Dispatchers.Main) {
                hideLoadingDialog()
                if (totalPopulated.isEmpty()) {
                    Toast.makeText(this@NbtEditorActivity, "当前区域文件中没有已生成的区块", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val uselessCount = uselessList.size
                val lowCount = lowTimeList.size
                val totalCount = totalPopulated.size
                val uselessPct = if (totalCount > 0) (uselessCount.toFloat() / totalCount.toFloat()) * 100f else 0f

                val options = arrayOf(
                    "${getString(R.string.nbt_prune_option_zero)} (共 $uselessCount 个)",
                    "${getString(R.string.nbt_prune_option_low)} (共 $lowCount 个)",
                    "${getString(R.string.nbt_prune_option_all)} (共 $totalCount 个)"
                )
                var selectedOption = 0

                val dialog = MaterialAlertDialogBuilder(this@NbtEditorActivity)
                    .setTitle(R.string.nbt_prune_dialog_title)
                    .setMessage(
                        if (uselessCount > 0)
                            getString(R.string.nbt_prune_stats_zero, uselessCount, uselessPct)
                        else
                            getString(R.string.nbt_prune_no_useless)
                    )
                    .setSingleChoiceItems(options, 0) { _, which ->
                        selectedOption = which
                    }
                    .setPositiveButton("立即清理") { _, _ ->
                        val targetChunks = when (selectedOption) {
                            0 -> uselessList.map { Pair(it.first.localX, it.first.localZ) }
                            1 -> lowTimeList.map { Pair(it.first.localX, it.first.localZ) }
                            2 -> totalPopulated.map { Pair(it.localX, it.localZ) }
                            else -> emptyList()
                        }

                        if (targetChunks.isEmpty()) {
                            Toast.makeText(this@NbtEditorActivity, "没有需要清理的区块", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }

                        showLoadingDialog("正在清理 ${targetChunks.size} 个区块...")
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                region.deleteChunks(targetChunks)
                                syncRegionFileToStorage()

                                val newTable = region.getChunkTable()
                                withContext(Dispatchers.Main) {
                                    hideLoadingDialog()
                                    chunkGridView.refreshWithData(newTable)

                                    val pop = newTable.count { it.exists }
                                    val pct = (pop.toFloat() / 1024f) * 100f
                                    tvSubtitle.text = getString(R.string.nbt_region_stats, pop, pct)

                                    val curX = chunkGridView.selectedX
                                    val curZ = chunkGridView.selectedZ
                                    if (curX in 0..31 && curZ in 0..31) {
                                        onDetailsUpdated(curX, curZ, chunkGridView.isChunkPopulated(curX, curZ))
                                    }

                                    Toast.makeText(this@NbtEditorActivity, getString(R.string.nbt_prune_success, targetChunks.size), Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    hideLoadingDialog()
                                    Toast.makeText(this@NbtEditorActivity, "清理失败: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .create()

                BackgroundUtils.applyDialogTheme(dialog, this@NbtEditorActivity)
                dialog.show()
            }
        }
    }

    private fun syncRegionFileToStorage() {
        val zipBytes = originalZipBytes
        val zipEntry = selectedZipEntryName
        if (zipBytes != null && zipEntry != null && currentRegionCachedFile != null) {
            val regionBytes = currentRegionCachedFile!!.readBytes()
            workspaceZipEntries[zipEntry] = regionBytes
            val updatedZip = updateZipWithNbt(zipBytes, zipEntry, regionBytes)
            originalZipBytes = updatedZip
            saveWorkspaceToCache(updatedZip, activeWorkspaceName)

            if (currentFilePath != null) {
                val file = File(currentFilePath!!)
                val bak = File("$currentFilePath.bak")
                if (file.exists()) file.copyTo(bak, overwrite = true)
                file.writeBytes(updatedZip)
            } else if (currentFileUri != null) {
                contentResolver.openOutputStream(currentFileUri!!)?.use { it.write(updatedZip) }
            }
        } else if (currentRegionCachedFile != null) {
            if (currentFilePath != null) {
                val file = File(currentFilePath!!)
                val bak = File("$currentFilePath.bak")
                if (file.exists()) file.copyTo(bak, overwrite = true)
                currentRegionCachedFile!!.copyTo(file, overwrite = true)
            } else if (currentFileUri != null) {
                contentResolver.openOutputStream(currentFileUri!!)?.use { out ->
                    currentRegionCachedFile!!.inputStream().use { it.copyTo(out) }
                }
            }
        }
    }

    private fun loadChunkFromRegion(localX: Int, localZ: Int) {
        val region = currentAnvilRegionFile ?: return
        val chunkInfo = region.getChunkInfo(localX, localZ)
        if (chunkInfo.exists) {
            try {
                val chunkNbt = region.readChunkNbt(localX, localZ)
                if (chunkNbt != null) {
                    loadChunkIntoEditor(localX, localZ, chunkNbt)
                } else {
                    Toast.makeText(this, "未找到该区块数据", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "解析区块错误: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("未生成区块")
                .setMessage(R.string.nbt_chunk_empty_prompt)
                .setPositiveButton("初始化空区块") { _, _ ->
                    val root = NbtCompound()
                    root["DataVersion"] = NbtInt(3465)
                    val newChunk = NbtFile("", root)
                    loadChunkIntoEditor(localX, localZ, newChunk)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    private fun loadChunkIntoEditor(chunkX: Int, chunkZ: Int, chunkNbt: NbtFile) {
        this.currentChunkX = chunkX
        this.currentChunkZ = chunkZ
        this.currentNbtFile = chunkNbt
        this.isDirty = false
        this.isCompressed = true
        this.isLittleEndian = false
        this.readHeaders = false

        val regionName = currentFileName.ifEmpty { currentAnvilRegionFile?.file?.name ?: "Region" }
        displayNbt(chunkNbt, regionName)
        updateWorkspaceUi()
    }

    inner class DrawerFilesAdapter(private val files: Array<String>) : androidx.recyclerview.widget.RecyclerView.Adapter<DrawerFilesAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(android.R.id.text1)
            init {
                view.setOnClickListener {
                    val chosen = files[adapterPosition]
                    if (chosen != selectedZipEntryName) {
                        switchToFileInWorkspace(chosen)
                    }
                    showNbtEditorView()
                }
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
        )
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = files[position]
            val isSelected = files[position] == selectedZipEntryName
            val primary = if (BackgroundUtils.isColorExtractionEnabled(this@NbtEditorActivity)) {
                BackgroundUtils.getExtractedPrimaryColor(this@NbtEditorActivity) ?: ContextCompat.getColor(this@NbtEditorActivity, R.color.md_theme_primary)
            } else {
                ContextCompat.getColor(this@NbtEditorActivity, R.color.md_theme_primary)
            }
            holder.textView.setTextColor(
                if (isSelected) primary else ContextCompat.getColor(this@NbtEditorActivity, R.color.md_theme_onSurface)
            )
        }
        override fun getItemCount() = files.size
    }

    private fun setupWorkspaceBrowser() {
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout_workspace)
        val layoutLevelDb = findViewById<View>(R.id.layout_drawer_leveldb)
        val layoutFiles = findViewById<View>(R.id.layout_drawer_files)
        val rvLevelDb = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_leveldb_records)
        val rvFiles = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_workspace_files)
        val etSearch = findViewById<android.widget.EditText>(R.id.et_leveldb_search)
        val chipGroup = findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_group_categories)
        val progressIndicator = findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progress_leveldb_loading)
        val tvSub = findViewById<TextView>(R.id.tv_workspace_subtitle)
        val btnWorkspacePrune = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_workspace_prune_chunks)

        rvLevelDb?.layoutManager = LinearLayoutManager(this)
        rvFiles?.layoutManager = LinearLayoutManager(this)

        if (levelDbAdapter == null) {
            levelDbAdapter = LevelDbRecordAdapter(this) { record ->
                loadLevelDbRecord(record)
            }
        }
        rvLevelDb?.adapter = levelDbAdapter

        btnWorkspacePrune?.setOnClickListener {
            if (currentLevelDbHelper != null) {
                showLevelDbChunkPruneDialog()
            } else if (workspaceZipEntries.keys.any { it.endsWith(".mca", true) || it.endsWith(".mcr", true) } || currentFilePath != null) {
                showWorldChunkOptimizerDialog()
            }
        }

        tabLayout?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                layoutLevelDb?.isVisible = tab?.position == 0
                layoutFiles?.isVisible = tab?.position == 1
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        fun updateRecordCountDisplay() {
            val count = levelDbAdapter?.getFilteredCount() ?: cachedLevelDbRecords.size
            val total = cachedLevelDbRecords.size
            if (isLoadingRecords) {

            } else if (count == total) {
                tvSub?.text = "$total 条记录"
            } else {
                tvSub?.text = "找到 $count 条 (共 $total 条)"
            }
        }

        fun refreshDbAsync(forceReload: Boolean = false) {
            val helper = currentLevelDbHelper ?: return
            if (!forceReload && cachedLevelDbRecords.isNotEmpty()) {

                levelDbAdapter?.setData(cachedLevelDbRecords)
                progressIndicator?.isVisible = false
                updateRecordCountDisplay()
                BackgroundUtils.applyCustomBackground(this@NbtEditorActivity)
                return
            }

            if (isLoadingRecords) return
            isLoadingRecords = true
            progressIndicator?.isVisible = true
            progressIndicator?.isIndeterminate = true
            tvSub?.text = "正在扫描 LevelDB 索引..."

            lifecycleScope.launch(Dispatchers.IO) {
                val records = try {
                    helper.getAllRecords(onProgress = { count ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            if (isLoadingRecords) {
                                tvSub?.text = "正在读取 LevelDB 数据 (已扫描 $count 条)..."
                            }
                        }
                    })
                } catch (e: Exception) {
                    emptyList()
                }
                cachedLevelDbRecords = records
                withContext(Dispatchers.Main) {
                    isLoadingRecords = false
                    progressIndicator?.isVisible = false
                    levelDbAdapter?.setData(records)
                    updateRecordCountDisplay()
                    BackgroundUtils.applyCustomBackground(this@NbtEditorActivity)
                }
            }
        }

        chipGroup?.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val currentCategory = when (checkedIds.first()) {
                R.id.chip_cat_player -> LevelDbCategory.PLAYER
                R.id.chip_cat_entity -> LevelDbCategory.ENTITY
                R.id.chip_cat_block_entity -> LevelDbCategory.BLOCK_ENTITY
                R.id.chip_cat_chunk -> LevelDbCategory.CHUNK
                R.id.chip_cat_world -> LevelDbCategory.WORLD
                R.id.chip_cat_other -> LevelDbCategory.OTHER
                else -> LevelDbCategory.ALL
            }
            levelDbAdapter?.setCategory(currentCategory)
            updateRecordCountDisplay()
        }

        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                levelDbAdapter?.filter(s?.toString().orEmpty())
                updateRecordCountDisplay()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        levelDbRefreshTrigger = { force -> refreshDbAsync(forceReload = force) }

        if (currentLevelDbHelper != null) {
            refreshDbAsync(forceReload = false)
        }
    }

    private fun reloadLevelDbCacheAsync() {
        cachedLevelDbRecords = emptyList()
        isLoadingRecords = false
        levelDbRefreshTrigger?.invoke(true)
    }

    private fun updateWorkspaceData() {
        val layoutNoWs = findViewById<View>(R.id.layout_no_workspace)
        val layoutActiveWs = findViewById<View>(R.id.layout_active_workspace)
        val btnWorkspacePrune = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_workspace_prune_chunks)

        val hasWorkspace = activeWorkspaceName.isNotEmpty() || currentLevelDbHelper != null
        layoutNoWs?.isVisible = !hasWorkspace
        layoutActiveWs?.isVisible = hasWorkspace

        if (!hasWorkspace) {
            btnWorkspacePrune?.isVisible = false
            return
        }

        val tvTitle = findViewById<TextView>(R.id.tv_workspace_title)
        tvTitle?.text = if (activeWorkspaceName.isNotEmpty()) activeWorkspaceName else "LevelDB 工作区"

        val rvFiles = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_workspace_files)
        val entryList = getSortedWorkspaceEntryNames()
        val adapter = DrawerFilesAdapter(entryList)
        rvFiles?.adapter = adapter

        val helper = currentLevelDbHelper
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout_workspace)
        if (helper != null) {
            tabLayout?.getTabAt(0)?.view?.isVisible = true
            tabLayout?.selectTab(tabLayout?.getTabAt(0))
            btnWorkspacePrune?.isVisible = true
            btnWorkspacePrune?.text = "🧹 LevelDB 区块范围裁剪与瘦身"
        } else {
            tabLayout?.getTabAt(0)?.view?.isVisible = false
            tabLayout?.selectTab(tabLayout?.getTabAt(1))
            findViewById<TextView>(R.id.tv_workspace_subtitle)?.text = "${entryList.size} 个文件"
            val hasMca = entryList.any { it.endsWith(".mca", true) || it.endsWith(".mcr", true) } || (currentFilePath != null && File(currentFilePath!!).isDirectory)
            btnWorkspacePrune?.isVisible = hasMca
            btnWorkspacePrune?.text = "🧹 全存档无用区块智能瘦身"
        }
    }

    private fun updateWorkspaceUi() {
        updateWorkspaceData()
        if (viewFlipper.displayedChild == 0) {
            binding.toolbar.title = if (activeWorkspaceName.isNotEmpty()) activeWorkspaceName else getString(R.string.nbt_editor_title)
            binding.toolbar.subtitle = if (currentLevelDbHelper != null) "LevelDB 数据库" else null
        } else {
            if (currentLevelDbRecord != null) {
                val sizeStr = formatBytes(currentLevelDbRecord!!.valueSize.toLong())
                binding.toolbar.title = currentLevelDbRecord!!.displayName
                binding.toolbar.subtitle = "${if (activeWorkspaceName.isNotEmpty()) activeWorkspaceName else "db/"} ($sizeStr)"
            } else if (currentAnvilRegionFile != null) {
                val region = currentAnvilRegionFile!!
                val regionName = currentFileName.ifEmpty { region.file.name }
                binding.toolbar.subtitle = if (activeWorkspaceName.isNotEmpty() && activeWorkspaceName != regionName) {
                    "$activeWorkspaceName > $regionName"
                } else {
                    regionName
                }

                val rx = region.regionX
                val rz = region.regionZ
                val gx = if (rx != null) rx * 32 + currentChunkX else currentChunkX
                val gz = if (rz != null) rz * 32 + currentChunkZ else currentChunkZ
                val bx = gx * 16
                val bz = gz * 16

                binding.toolbar.title = "区块: [$gx, $gz] (局部: [$currentChunkX, $currentChunkZ] | 方块: [$bx..${bx + 15}, $bz..${bz + 15}])"
            } else if (activeWorkspaceName.isNotEmpty() && !selectedZipEntryName.isNullOrEmpty()) {
                binding.toolbar.subtitle = activeWorkspaceName
                binding.toolbar.title = "$selectedZipEntryName (${workspaceZipEntries.size} 个文件)"
            }
        }
    }

    private fun showLevelDbBrowserDialog(onDismissAction: (() -> Unit)? = null) {
        showWorkspaceView()
    }

    private fun loadLevelDbRecord(record: LevelDbRecord) {
        val helper = currentLevelDbHelper ?: return
        this.currentLevelDbRecord = record
        this.currentAnvilRegionFile?.close()
        this.currentAnvilRegionFile = null

        if (record.isNbt) {
            showLoadingDialog("正在读取并解析 NBT 数据...")
            lifecycleScope.launch(Dispatchers.IO) {
                val rawBytes = helper.get(record.key)
                if (rawBytes == null) {
                    withContext(Dispatchers.Main) {
                        hideLoadingDialog()
                        Toast.makeText(this@NbtEditorActivity, "未找到该记录数据", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                try {
                    val nbtFile = BedrockLevelDbHelper.readBedrockNbt(rawBytes, record.hasMultipleCompounds)
                    withContext(Dispatchers.Main) {
                        hideLoadingDialog()
                        currentNbtFile = nbtFile
                        isDirty = false
                        isCompressed = false
                        isLittleEndian = true
                        readHeaders = false

                        displayNbt(nbtFile, record.displayName)
                        updateWorkspaceUi()
                        showNbtEditorView()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        hideLoadingDialog()
                        Toast.makeText(this@NbtEditorActivity, "解析 NBT 失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            val rawBytes = helper.get(record.key) ?: run {
                Toast.makeText(this, "未找到该记录数据", Toast.LENGTH_SHORT).show()
                return
            }
            val textPreview = try {
                String(rawBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
            val hexPreview = rawBytes.take(64).joinToString(" ") { "%02x".format(it) } + if (rawBytes.size > 64) "..." else ""

            val builder = MaterialAlertDialogBuilder(this)
                .setTitle(record.displayName)
                .setMessage("类型: 原始二进制 / 数据流\n大小: ${rawBytes.size} 字节\n\n十六进制预览:\n$hexPreview" + (if (textPreview.isNotEmpty() && textPreview.all { it.isLetterOrDigit() || it in "_-~.: /" }) "\n\n文本内容: $textPreview" else ""))
                .setPositiveButton(R.string.ok, null)

            if (record.chunkX != null && record.chunkZ != null) {
                builder.setNegativeButton(R.string.nbt_leveldb_delete_chunk) { _, _ ->
                    confirmDeleteLevelDbChunk(record.chunkX, record.chunkZ, record.dimensionId)
                }
            } else {
                builder.setNegativeButton("删除此记录") { _, _ ->
                    confirmDeleteLevelDbSingleKey(record)
                }
            }

            val dialog = builder.create()
            BackgroundUtils.applyDialogTheme(dialog, this)
            dialog.show()
        }
    }

    private fun confirmDeleteLevelDbSingleKey(record: LevelDbRecord) {
        val helper = currentLevelDbHelper ?: return
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nbt_delete_tag)
            .setMessage("确定要从 LevelDB 数据库中删除「${record.displayName}」吗？")
            .setPositiveButton(R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        helper.delete(record.key)
                        syncLevelDbToStorage()
                        withContext(Dispatchers.Main) {
                            reloadLevelDbCacheAsync()
                            Toast.makeText(this@NbtEditorActivity, "已删除记录: ${record.displayName}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@NbtEditorActivity, "删除失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        BackgroundUtils.applyDialogTheme(dialog, this)
        dialog.show()
    }

    private fun confirmDeleteLevelDbChunk(chunkX: Int, chunkZ: Int, dimensionId: Int?) {
        val helper = currentLevelDbHelper ?: return
        val allRecords = cachedLevelDbRecords.filter { it.chunkX == chunkX && it.chunkZ == chunkZ && (dimensionId == null || it.dimensionId == dimensionId) }
        val count = allRecords.size
        val dimName = when (dimensionId) {
            1 -> " [下界]"
            2 -> " [末地]"
            else -> ""
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nbt_leveldb_delete_chunk)
            .setMessage(getString(R.string.nbt_leveldb_delete_chunk_confirm, chunkX, chunkZ, count) + dimName)
            .setPositiveButton(R.string.ok) { _, _ ->
                showLoadingDialog("正在删除区块数据...")
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val deleted = helper.deleteChunk(chunkX, chunkZ, dimensionId)
                        syncLevelDbToStorage()
                        withContext(Dispatchers.Main) {
                            hideLoadingDialog()
                            reloadLevelDbCacheAsync()
                            Toast.makeText(this@NbtEditorActivity, getString(R.string.nbt_leveldb_delete_chunk_success, chunkX, chunkZ, deleted), Toast.LENGTH_SHORT).show()
                            if (viewFlipper.displayedChild == 1) {
                                showWorkspaceView()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            hideLoadingDialog()
                            Toast.makeText(this@NbtEditorActivity, "删除失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        BackgroundUtils.applyDialogTheme(dialog, this)
        dialog.show()
    }

    private fun showLevelDbChunkPruneDialog() {
        val helper = currentLevelDbHelper ?: return
        val chunkRecords = cachedLevelDbRecords.filter { it.chunkX != null && it.chunkZ != null }
        val distinctChunks = chunkRecords.map { Pair(it.chunkX!!, it.chunkZ!!) }.distinct()

        val options = arrayOf(
            "🗺️ 打开 2D 可视化地图选区删除 (推荐)",
            "清理核心区之外全部区块 (保留 ±16 区块 / 256m)",
            "清理中等区域之外区块 (保留 ±32 区块 / 512m)",
            "清理大型区域之外区块 (保留 ±64 区块 / 1024m)",
            "清空当前数据库中全部区块数据 (共 ${distinctChunks.size} 个区块)"
        )
        var selected = 0

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("LevelDB 区块裁剪与瘦身")
            .setMessage("数据库中已识别 ${distinctChunks.size} 个独立区块（共 ${chunkRecords.size} 条记录）。\n请选择要执行的区块清理策略：")
            .setSingleChoiceItems(options, 0) { _, which ->
                selected = which
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                if (selected == 0) {
                    showBedrockChunkMapPickerDialog()
                    return@setPositiveButton
                }

                val (minCoord, maxCoord) = when (selected) {
                    1 -> Pair(-16, 16)
                    2 -> Pair(-32, 32)
                    3 -> Pair(-64, 64)
                    else -> Pair(0, -1)
                }

                showLoadingDialog("正在清理 LevelDB 区块...")
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val deletedCount = if (selected == 4) {
                            helper.deleteChunks(distinctChunks)
                        } else {
                            helper.deleteChunksOutsideRange(minCoord, maxCoord, minCoord, maxCoord)
                        }
                        syncLevelDbToStorage()
                        withContext(Dispatchers.Main) {
                            hideLoadingDialog()
                            reloadLevelDbCacheAsync()
                            Toast.makeText(this@NbtEditorActivity, "已成功清理 $deletedCount 条区块数据！", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            hideLoadingDialog()
                            Toast.makeText(this@NbtEditorActivity, "清理失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        BackgroundUtils.applyDialogTheme(dialog, this)
        dialog.show()
    }

    private fun showBedrockChunkMapPickerDialog() {
        val helper = currentLevelDbHelper ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_chunk_picker, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        BackgroundUtils.applyDialogTheme(dialog, this, dialogView)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_region_title)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tv_region_subtitle)
        val toggleTool = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_select_tool)
        val scrollDims = dialogView.findViewById<View>(R.id.scroll_dimension_tabs)
        val chipGroupDims = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_group_dimensions)
        val chunkGridView = dialogView.findViewById<ChunkGridView>(R.id.chunk_grid_view)
        val tvSelectedChunkCoord = dialogView.findViewById<TextView>(R.id.tv_selected_chunk_coord)
        val tvSelectedChunkStatus = dialogView.findViewById<TextView>(R.id.tv_selected_chunk_status)
        val tvSelectedBlockRange = dialogView.findViewById<TextView>(R.id.tv_selected_block_range)
        val btnActionSelectAll = dialogView.findViewById<View>(R.id.btn_action_select_all)
        val btnActionInvert = dialogView.findViewById<View>(R.id.btn_action_invert)
        val btnActionClear = dialogView.findViewById<View>(R.id.btn_action_clear)
        val btnPruneUseless = dialogView.findViewById<View>(R.id.btn_prune_useless_chunks)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel_chunk_picker)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm_select_chunk)
        val btnDeleteChunk = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete_chunk_dialog)

        tvTitle.text = "LevelDB 2D 区块地图"
        scrollDims.visibility = View.VISIBLE
        btnPruneUseless?.visibility = View.GONE
        btnConfirm?.visibility = View.GONE

        btnCancel.setOnClickListener { dialog.dismiss() }

        toggleTool?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                chunkGridView.selectMode = if (checkedId == R.id.btn_tool_box) {
                    ChunkGridView.SelectMode.BOX
                } else {
                    ChunkGridView.SelectMode.TAP
                }
            }
        }

        btnActionSelectAll?.setOnClickListener { chunkGridView.selectAllPopulated() }
        btnActionInvert?.setOnClickListener { chunkGridView.invertSelection() }
        btnActionClear?.setOnClickListener { chunkGridView.clearSelection() }

        fun updateSelectedInfo(cx: Int, cz: Int, exists: Boolean) {
            val bx = cx * 16
            val bz = cz * 16
            tvSelectedChunkCoord.text = "区块: [$cx, $cz]"
            tvSelectedBlockRange.text = "方块范围: [X: $bx..${bx + 15}, Z: $bz..${bz + 15}]"
            val count = chunkGridView.selectedChunks.size
            if (count > 0) {
                tvSelectedChunkStatus.text = "已选中 $count 个区块"
                tvSelectedChunkStatus.setTextColor(ContextCompat.getColor(this, R.color.md_theme_error))
                btnDeleteChunk.text = "删除所选 ($count)"
                btnDeleteChunk.isEnabled = true
                btnDeleteChunk.alpha = 1.0f
                btnDeleteChunk.visibility = View.VISIBLE
            } else {
                tvSelectedChunkStatus.text = if (exists) "状态: 已生成" else "状态: 未生成"
                tvSelectedChunkStatus.setTextColor(
                    if (exists) ContextCompat.getColor(this, R.color.md_theme_primary)
                    else ContextCompat.getColor(this, R.color.md_theme_outline)
                )
                btnDeleteChunk.text = "删除此区块"
                btnDeleteChunk.isEnabled = exists
                btnDeleteChunk.alpha = if (exists) 1.0f else 0.4f
                btnDeleteChunk.visibility = View.VISIBLE
            }
        }

        chunkGridView.onChunkSelectedListener = { cx, cz, exists ->
            updateSelectedInfo(cx, cz, exists)
        }

        chunkGridView.onSelectionChangedListener = { count, _ ->
            if (count > 0) {
                tvSelectedChunkStatus.text = "已选中 $count 个区块"
                tvSelectedChunkStatus.setTextColor(ContextCompat.getColor(this, R.color.md_theme_error))
                btnDeleteChunk.text = "删除所选 ($count)"
                btnDeleteChunk.visibility = View.VISIBLE
            } else {
                val exists = chunkGridView.isChunkPopulated(chunkGridView.selectedX, chunkGridView.selectedZ)
                updateSelectedInfo(chunkGridView.selectedX, chunkGridView.selectedZ, exists)
            }
        }

        var currentDim = 0
        fun loadDimension(dimId: Int) {
            currentDim = dimId
            showLoadingDialog("正在加载维度区块...")
            lifecycleScope.launch(Dispatchers.IO) {
                val populated = helper.getPopulatedChunkCoordinates(dimId)
                val blockEntities = helper.getBlockEntityChunkCoordinates(dimId)
                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    tvSubtitle.text = "当前维度已生成 ${populated.size} 个区块"
                    chunkGridView.clearSelection()
                    chunkGridView.setWorldChunkData(
                        populated,
                        blockEntityCoords = blockEntities
                    )
                }
            }
        }

        chipGroupDims?.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds.first()) {
                R.id.chip_dim_overworld -> loadDimension(0)
                R.id.chip_dim_nether -> loadDimension(1)
                R.id.chip_dim_end -> loadDimension(2)
            }
        }

        loadDimension(0)

        btnDeleteChunk.setOnClickListener {
            val targets = if (chunkGridView.selectedChunks.isNotEmpty()) {
                chunkGridView.selectedChunks.toSet()
            } else if (chunkGridView.isChunkPopulated(chunkGridView.selectedX, chunkGridView.selectedZ)) {
                setOf(Pair(chunkGridView.selectedX, chunkGridView.selectedZ))
            } else emptySet()

            if (targets.isEmpty()) return@setOnClickListener

            MaterialAlertDialogBuilder(this)
                .setTitle("确认删除 LevelDB 区块")
                .setMessage("确定要物理删除当前维度的 ${targets.size} 个区块数据吗？\n删除后不可恢复。")
                .setPositiveButton("立即删除") { _, _ ->
                    showLoadingDialog("正在物理删除 LevelDB 区块...")
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val deleted = helper.deleteChunks(targets, currentDim)
                            syncLevelDbToStorage()
                            withContext(Dispatchers.Main) {
                                hideLoadingDialog()
                                reloadLevelDbCacheAsync()
                                Toast.makeText(this@NbtEditorActivity, "已成功删除 ${targets.size} 个区块 ($deleted 条记录)！", Toast.LENGTH_SHORT).show()
                                loadDimension(currentDim)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                hideLoadingDialog()
                                Toast.makeText(this@NbtEditorActivity, "删除失败: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        dialog.show()
    }

    private fun showWorldChunkOptimizerDialog() {
        val mcaEntries = workspaceZipEntries.keys.filter { it.endsWith(".mca", true) || it.endsWith(".mcr", true) }
        if (mcaEntries.isEmpty() && currentFilePath == null) {
            Toast.makeText(this, "当前存档中未找到任何 MCA 区域文件", Toast.LENGTH_SHORT).show()
            return
        }

        showLoadingDialog("正在扫描全存档 MCA 区域文件...")
        lifecycleScope.launch(Dispatchers.IO) {
            var totalMca = 0
            var totalPopulated = 0
            var totalUseless = 0
            val regionList = mutableListOf<Pair<String, List<Pair<Int, Int>>>>()

            val tempDir = File(cacheDir, "world_optimizer_temp").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            try {
                if (mcaEntries.isNotEmpty()) {
                    totalMca = mcaEntries.size
                    for (entry in mcaEntries) {
                        val bytes = workspaceZipEntries[entry] ?: continue
                        val tempMca = File(tempDir, "temp_${totalMca}_${File(entry).name}")
                        tempMca.writeBytes(bytes)
                        AnvilRegionFile(tempMca).use { anvil ->
                            val pop = anvil.getPopulatedChunks()
                            totalPopulated += pop.size
                            val useless = anvil.scanUselessChunks(maxInhabitedTimeTicks = 0L)
                            totalUseless += useless.size
                            if (useless.isNotEmpty()) {
                                regionList.add(Pair(entry, useless.map { Pair(it.first.localX, it.first.localZ) }))
                            }
                        }
                    }
                } else if (currentFilePath != null) {
                    val root = File(currentFilePath!!)
                    val mcaFiles = root.walkTopDown().filter { it.isFile && (it.name.endsWith(".mca", true) || it.name.endsWith(".mcr", true)) }.toList()
                    totalMca = mcaFiles.size
                    for (f in mcaFiles) {
                        AnvilRegionFile(f).use { anvil ->
                            val pop = anvil.getPopulatedChunks()
                            totalPopulated += pop.size
                            val useless = anvil.scanUselessChunks(maxInhabitedTimeTicks = 0L)
                            totalUseless += useless.size
                            if (useless.isNotEmpty()) {
                                regionList.add(Pair(f.absolutePath, useless.map { Pair(it.first.localX, it.first.localZ) }))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                tempDir.deleteRecursively()
            }

            withContext(Dispatchers.Main) {
                hideLoadingDialog()
                val pct = if (totalPopulated > 0) (totalUseless.toFloat() / totalPopulated.toFloat()) * 100f else 0f
                val msg = "全存档扫描结果：\n\n• MCA 区域文件：$totalMca 个\n• 总已生成区块：$totalPopulated 个\n• 发现无用区块：$totalUseless 个 (InhabitedTime = 0，占 $pct%)\n\n清理后将清除所有仅探索路过的无用区块，保留所有玩家建筑与常驻区域，可大幅减小存档体积。"

                val dialog = MaterialAlertDialogBuilder(this@NbtEditorActivity)
                    .setTitle(R.string.world_prune_chunks_title)
                    .setMessage(msg)
                    .setPositiveButton("一键清理全部无用区块") { _, _ ->
                        if (totalUseless == 0) {
                            Toast.makeText(this@NbtEditorActivity, "未发现无用区块", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }

                        showLoadingDialog("正在清理 $totalUseless 个无用区块...")
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                if (originalZipBytes != null) {
                                    val tempWorkDir = File(cacheDir, "world_prune_exec").apply {
                                        if (exists()) deleteRecursively()
                                        mkdirs()
                                    }
                                    for ((entry, uselessCoords) in regionList) {
                                        val bytes = workspaceZipEntries[entry] ?: continue
                                        val tempFile = File(tempWorkDir, File(entry).name)
                                        tempFile.writeBytes(bytes)
                                        AnvilRegionFile(tempFile).use { anvil ->
                                            anvil.deleteChunks(uselessCoords)
                                        }
                                        workspaceZipEntries[entry] = tempFile.readBytes()
                                    }
                                    tempWorkDir.deleteRecursively()

                                    val updatedZip = rebuildZipFromWorkspaceEntries()
                                    originalZipBytes = updatedZip
                                    saveWorkspaceToCache(updatedZip, activeWorkspaceName)

                                    if (currentFilePath != null) {
                                        val f = File(currentFilePath!!)
                                        val bak = File("$currentFilePath.bak")
                                        if (f.exists()) f.copyTo(bak, overwrite = true)
                                        f.writeBytes(updatedZip)
                                    } else if (currentFileUri != null) {
                                        contentResolver.openOutputStream(currentFileUri!!)?.use { it.write(updatedZip) }
                                    }
                                } else if (currentFilePath != null) {
                                    for ((path, uselessCoords) in regionList) {
                                        val f = File(path)
                                        if (f.exists()) {
                                            AnvilRegionFile(f).use { anvil ->
                                                anvil.deleteChunks(uselessCoords)
                                            }
                                        }
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    hideLoadingDialog()
                                    updateWorkspaceUi()
                                    Toast.makeText(this@NbtEditorActivity, "全存档瘦身完成！共清理了 $totalUseless 个无用区块！", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    hideLoadingDialog()
                                    Toast.makeText(this@NbtEditorActivity, "清理失败: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .create()

                BackgroundUtils.applyDialogTheme(dialog, this@NbtEditorActivity)
                dialog.show()
            }
        }
    }

    private fun rebuildZipFromWorkspaceEntries(): ByteArray {
        val originalZip = originalZipBytes ?: return byteArrayOf()
        val baos = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(originalZip)).use { zis ->
            ZipOutputStream(baos).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newEntry = ZipEntry(entry.name)
                    zos.putNextEntry(newEntry)
                    val customBytes = workspaceZipEntries[entry.name]
                    if (customBytes != null) {
                        zos.write(customBytes)
                    } else {
                        zis.copyTo(zos)
                    }
                    zos.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return baos.toByteArray()
    }

    private fun syncLevelDbToStorage() {
        val zipBytes = originalZipBytes
        if (zipBytes != null && currentLevelDbCachedFolder != null) {
            val updatedZip = updateZipWithLevelDbFolder(zipBytes, currentLevelDbCachedFolder!!)
            originalZipBytes = updatedZip
            saveWorkspaceToCache(updatedZip, activeWorkspaceName)

            if (currentFilePath != null) {
                val file = File(currentFilePath!!)
                val bak = File("$currentFilePath.bak")
                if (file.exists()) file.copyTo(bak, overwrite = true)
                file.writeBytes(updatedZip)
            } else if (currentFileUri != null) {
                contentResolver.openOutputStream(currentFileUri!!)?.use { it.write(updatedZip) }
            }
        } else if (currentFilePath != null && currentLevelDbCachedFolder != null) {
            val originalFolder = File(currentFilePath!!)
            if (originalFolder.isDirectory) {
                currentLevelDbCachedFolder!!.listFiles()?.forEach { f ->
                    if (!f.isDirectory && f.name != "LOCK") {
                        f.copyTo(File(originalFolder, f.name), overwrite = true)
                    }
                }
            }
        }
    }

    private fun showAddCustomLevelDbKeyDialog(onAdded: () -> Unit) {
        val helper = currentLevelDbHelper ?: return
        val binding = DialogAddNbtTagBinding.inflate(layoutInflater)
        binding.tvAddDialogTitle.text = getString(R.string.nbt_leveldb_add_key)
        binding.tvParentInfo.text = "Database: db/"
        binding.tilKey.hint = getString(R.string.nbt_leveldb_key_hint)
        binding.tilType.visibility = View.GONE
        binding.tilInitialValue.hint = "初始数值 (文本或留空)"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        BackgroundUtils.applyDialogTheme(dialog, this, binding.root)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = binding.etKey.text?.toString()?.trim().orEmpty()
                val initialVal = binding.etInitialValue.text?.toString().orEmpty()
                if (key.isEmpty()) {
                    binding.tvAddErrorHint.text = getString(R.string.nbt_key_empty)
                    binding.tvAddErrorHint.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                val keyBytes = key.toByteArray(Charsets.UTF_8)
                val valBytes = initialVal.toByteArray(Charsets.UTF_8)
                helper.put(keyBytes, valBytes)
                isDirty = true
                dialog.dismiss()
                onAdded()
            }
        }
        dialog.show()
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    private fun showWorkspaceView() {
        if (viewFlipper.displayedChild != 0) {
            viewFlipper.displayedChild = 0
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        }
        binding.toolbar.title = if (activeWorkspaceName.isNotEmpty()) activeWorkspaceName else getString(R.string.nbt_editor_title)
        binding.toolbar.subtitle = if (currentLevelDbHelper != null) "LevelDB 数据库" else null
        BackgroundUtils.applyCustomBackground(this)
    }

    private fun showNbtEditorView() {
        if (viewFlipper.displayedChild != 1) {
            viewFlipper.displayedChild = 1
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        if (currentLevelDbRecord != null) {
            val sizeStr = formatBytes(currentLevelDbRecord!!.valueSize.toLong())
            binding.toolbar.title = currentLevelDbRecord!!.displayName
            binding.toolbar.subtitle = "${if (activeWorkspaceName.isNotEmpty()) activeWorkspaceName else "db/"} ($sizeStr)"
        } else if (currentNbtFile != null) {
            binding.toolbar.title = currentFileName
            binding.toolbar.subtitle = if (activeWorkspaceName.isNotEmpty()) activeWorkspaceName else (if (currentNbtFile?.name?.isNotEmpty() == true) currentNbtFile?.name else null)
        }
        BackgroundUtils.applyCustomBackground(this)
    }

    private fun showFileSwitcherDialog() {
        showWorkspaceView()
    }

    private fun showZipEntrySwitcherDialog() {
        showWorkspaceView()
    }
    private fun saveWorkspaceToCache(bytes: ByteArray, name: String) {
        try {
            val cacheFile = File(cacheDir, "nbt_cached_workspace.zip")
            cacheFile.writeBytes(bytes)
            prefs.edit()
                .putString("nbt_last_workspace_name", name)
                .putString("nbt_last_entry", selectedZipEntryName.orEmpty())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreWorkspaceSession() {
        val cacheFile = File(cacheDir, "nbt_cached_workspace.zip")
        val savedName = prefs.getString("nbt_last_workspace_name", null)
        val savedEntry = prefs.getString("nbt_last_entry", null)

        if (cacheFile.exists() && savedName != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bytes = cacheFile.readBytes()
                    if (isZipArchive(bytes)) {
                        handleZipArchiveAsync(bytes, savedName, savedEntry)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@NbtEditorActivity, R.string.nbt_workspace_restored, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun clearWorkspaceSession() {
        val cacheFile = File(cacheDir, "nbt_cached_workspace.zip")
        if (cacheFile.exists()) cacheFile.delete()
        prefs.edit()
            .remove("nbt_last_workspace_name")
            .remove("nbt_last_entry")
            .apply()

        originalZipBytes = null
        selectedZipEntryName = null
        workspaceZipEntries.clear()
        activeWorkspaceName = ""
        currentNbtFile = null
        currentAnvilRegionFile?.close()
        currentAnvilRegionFile = null
        currentRegionCachedFile = null
        currentLevelDbHelper?.close()
        currentLevelDbHelper = null
        currentLevelDbRecord = null
        currentLevelDbCachedFolder = null
        cachedLevelDbRecords = emptyList()
        levelDbAdapter?.setData(emptyList())
        isDirty = false

        binding.emptyState.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.toolbar.title = getString(R.string.nbt_editor_title)
        binding.toolbar.subtitle = null
        updateWorkspaceUi()
    }

    private fun parseNbtBytes(bytes: ByteArray, fileName: String) {
        val attempts = listOf(
            Triple(true, false, false),
            Triple(false, true, true),
            Triple(true, false, true),
            Triple(true, true, false),
            Triple(true, true, true),
            Triple(false, false, false),
            Triple(false, false, true),
            Triple(false, true, false)
        )

        var loadedNbt: NbtFile? = null
        for ((comp, little, headers) in attempts) {
            try {
                val nbt = NbtIO.readNbtFile(ByteArrayInputStream(bytes), comp, little, headers)
                if (nbt.tag !is NbtEnd) {
                    loadedNbt = nbt
                    this.isCompressed = comp
                    this.isLittleEndian = little
                    this.readHeaders = headers
                    this.headerVersion = nbt.version
                    break
                }
            } catch (e: Exception) {
                continue
            }
        }

        if (loadedNbt == null) {
            Toast.makeText(this, R.string.nbt_load_failed, Toast.LENGTH_SHORT).show()
            return
        }

        this.currentNbtFile = loadedNbt
        displayNbt(loadedNbt, fileName)
    }

    private fun displayNbt(nbtFile: NbtFile, fileName: String) {
        binding.emptyState.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE

        binding.toolbar.title = fileName
        binding.toolbar.subtitle = if (nbtFile.name.isNotEmpty()) nbtFile.name else getString(R.string.nbt_root_tag)

        val formatStr = when {
            isLittleEndian && readHeaders -> "Bedrock (Header)"
            isLittleEndian -> "Bedrock (Little-Endian)"
            isCompressed -> "Java (GZIP)"
            else -> "Java (Uncompressed)"
        }

        val count = countTags(nbtFile.tag)

        val rootName = if (nbtFile.name.isNotEmpty()) nbtFile.name else "Root"
        treeAdapter.setRoot(nbtFile.tag, rootName)
    }

    private fun countTags(tag: NbtTag): Int {
        var count = 1
        when (tag) {
            is NbtCompound -> {
                tag.values.forEach { count += countTags(it) }
            }
            is NbtList<*> -> {
                tag.forEach { count += countTags(it) }
            }
            else -> {}
        }
        return count
    }

    private fun showEditValueDialog(node: NbtTreeNode) {
        val tag = node.tag
        val binding = DialogEditNbtValueBinding.inflate(layoutInflater)
        binding.tvDialogSubtitle.text = "${node.tagTypeName} - ${node.key}"

        val initialText = when (tag) {
            is NbtByte -> tag.signed.toString()
            is NbtShort -> tag.value.toString()
            is NbtInt -> tag.value.toString()
            is NbtLong -> tag.value.toString()
            is NbtFloat -> tag.value.toString()
            is NbtDouble -> tag.value.toString()
            is NbtString -> tag.value
            else -> ""
        }
        binding.etValue.setText(initialText)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        BackgroundUtils.applyDialogTheme(dialog, this, binding.root)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val input = binding.etValue.text?.toString().orEmpty()
                val updatedTag = parseInputToTag(input, tag)
                if (updatedTag != null) {
                    applyTagUpdate(node, updatedTag)
                    dialog.dismiss()
                } else {
                    binding.tvErrorHint.text = getString(R.string.nbt_invalid_number)
                    binding.tvErrorHint.visibility = View.VISIBLE
                }
            }
        }
        dialog.show()
    }

    private fun parseInputToTag(input: String, currentTag: NbtTag): NbtTag? {
        return try {
            when (currentTag) {
                is NbtByte -> input.toByteOrNull()?.let { NbtByte(it) }
                is NbtShort -> input.toShortOrNull()?.let { NbtShort(it) }
                is NbtInt -> input.toIntOrNull()?.let { NbtInt(it) }
                is NbtLong -> input.toLongOrNull()?.let { NbtLong(it) }
                is NbtFloat -> input.toFloatOrNull()?.let { NbtFloat(it) }
                is NbtDouble -> input.toDoubleOrNull()?.let { NbtDouble(it) }
                is NbtString -> NbtString(input)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun applyTagUpdate(node: NbtTreeNode, newTag: NbtTag) {
        val parent = node.parentTag
        when (parent) {
            is NbtCompound -> {
                parent[node.key] = newTag
            }
            is NbtList<*> -> {
                if (node.listIndex >= 0 && node.listIndex < parent.size) {
                    @Suppress("UNCHECKED_CAST")
                    (parent as MutableList<NbtTag>)[node.listIndex] = newTag
                }
            }
            is NbtByteArray -> {
                if (node.listIndex >= 0 && node.listIndex < parent.value.size && newTag is NbtByte) {
                    parent.value[node.listIndex] = newTag.signed
                }
            }
            is NbtIntArray -> {
                if (node.listIndex >= 0 && node.listIndex < parent.value.size && newTag is NbtInt) {
                    parent.value[node.listIndex] = newTag.value
                }
            }
            is NbtLongArray -> {
                if (node.listIndex >= 0 && node.listIndex < parent.value.size && newTag is NbtLong) {
                    parent.value[node.listIndex] = newTag.value
                }
            }
            null -> {
                currentNbtFile?.tag = newTag
            }
            else -> {}
        }
        node.tag = newTag
        isDirty = true
        treeAdapter.refreshTree()
    }

    private fun showAddTagDialog(parentNode: NbtTreeNode) {
        val binding = DialogAddNbtTagBinding.inflate(layoutInflater)
        val isParentCompound = parentNode.tag is NbtCompound
        binding.tvParentInfo.text = "Parent: ${parentNode.key} (${parentNode.tagTypeName})"
        binding.tilKey.isVisible = isParentCompound

        val defaultType = when (parentNode.tag) {
            is NbtByteArray -> "Byte"
            is NbtIntArray -> "Int"
            is NbtLongArray -> "Long"
            else -> "String"
        }

        val typeNames = when (parentNode.tag) {
            is NbtByteArray -> arrayOf("Byte")
            is NbtIntArray -> arrayOf("Int")
            is NbtLongArray -> arrayOf("Long")
            else -> arrayOf(
                "Compound", "List", "String", "Int", "Byte", "Short",
                "Long", "Float", "Double", "ByteArray", "IntArray", "LongArray"
            )
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, typeNames)
        binding.actvType.setAdapter(adapter)
        binding.actvType.setText(defaultType, false)
        binding.tilType.isVisible = isParentCompound || parentNode.tag is NbtList<*>

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        BackgroundUtils.applyDialogTheme(dialog, this, binding.root)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = binding.etKey.text?.toString()?.trim().orEmpty()
                val selectedType = binding.actvType.text?.toString().orEmpty()
                val initialVal = binding.etInitialValue.text?.toString().orEmpty()

                if (isParentCompound) {
                    if (key.isEmpty()) {
                        binding.tvAddErrorHint.text = getString(R.string.nbt_key_empty)
                        binding.tvAddErrorHint.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                    if ((parentNode.tag as NbtCompound).containsKey(key)) {
                        binding.tvAddErrorHint.text = getString(R.string.nbt_key_exists, key)
                        binding.tvAddErrorHint.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                }

                val newTag = createTagInstance(selectedType, initialVal)
                if (newTag == null) {
                    binding.tvAddErrorHint.text = getString(R.string.nbt_invalid_number)
                    binding.tvAddErrorHint.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                when (val p = parentNode.tag) {
                    is NbtCompound -> {
                        p[key] = newTag
                    }
                    is NbtList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        (p as MutableList<NbtTag>).add(newTag)
                    }
                    is NbtByteArray -> {
                        if (newTag is NbtByte) {
                            val list = p.value.toMutableList()
                            list.add(newTag.signed)
                            p.value = list.toByteArray()
                        }
                    }
                    is NbtIntArray -> {
                        if (newTag is NbtInt) {
                            val list = p.value.toMutableList()
                            list.add(newTag.value)
                            p.value = list.toIntArray()
                        }
                    }
                    is NbtLongArray -> {
                        if (newTag is NbtLong) {
                            val list = p.value.toMutableList()
                            list.add(newTag.value)
                            p.value = list.toLongArray()
                        }
                    }
                    else -> {}
                }

                isDirty = true
                treeAdapter.refreshTree()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun createTagInstance(typeName: String, initialValue: String): NbtTag? {
        return try {
            when (typeName) {
                "Compound" -> NbtCompound()
                "List" -> NbtList<NbtTag>()
                "String" -> NbtString(initialValue)
                "Int" -> NbtInt(initialValue.toIntOrNull() ?: 0)
                "Byte" -> NbtByte(initialValue.toByteOrNull() ?: 0)
                "Short" -> NbtShort(initialValue.toShortOrNull() ?: 0)
                "Long" -> NbtLong(initialValue.toLongOrNull() ?: 0L)
                "Float" -> NbtFloat(initialValue.toFloatOrNull() ?: 0f)
                "Double" -> NbtDouble(initialValue.toDoubleOrNull() ?: 0.0)
                "ByteArray" -> NbtByteArray(byteArrayOf())
                "IntArray" -> NbtIntArray(intArrayOf())
                "LongArray" -> NbtLongArray(longArrayOf())
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showRenameTagDialog(node: NbtTreeNode) {
        val parent = node.parentTag as? NbtCompound ?: return
        val binding = DialogAddNbtTagBinding.inflate(layoutInflater)
        binding.tvAddDialogTitle.text = getString(R.string.nbt_rename_key)
        binding.tvParentInfo.text = "Current: ${node.key}"
        binding.tilKey.hint = getString(R.string.nbt_tag_key)
        binding.etKey.setText(node.key)
        binding.tilType.visibility = View.GONE
        binding.tilInitialValue.visibility = View.GONE

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        BackgroundUtils.applyDialogTheme(dialog, this, binding.root)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newKey = binding.etKey.text?.toString()?.trim().orEmpty()
                if (newKey.isEmpty()) {
                    binding.tvAddErrorHint.text = getString(R.string.nbt_key_empty)
                    binding.tvAddErrorHint.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (newKey != node.key && parent.containsKey(newKey)) {
                    binding.tvAddErrorHint.text = getString(R.string.nbt_key_exists, newKey)
                    binding.tvAddErrorHint.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                val tag = parent.remove(node.key)
                if (tag != null) {
                    parent[newKey] = tag
                    node.key = newKey
                    isDirty = true
                    treeAdapter.refreshTree()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showDeleteTagDialog(node: NbtTreeNode) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nbt_delete_tag)
            .setMessage(getString(R.string.nbt_delete_confirm, node.key))
            .setPositiveButton(R.string.ok) { _, _ ->
                when (val parent = node.parentTag) {
                    is NbtCompound -> {
                        parent.remove(node.key)
                    }
                    is NbtList<*> -> {
                        if (node.listIndex >= 0 && node.listIndex < parent.size) {
                            parent.removeAt(node.listIndex)
                        }
                    }
                    is NbtByteArray -> {
                        if (node.listIndex >= 0 && node.listIndex < parent.value.size) {
                            val list = parent.value.toMutableList()
                            list.removeAt(node.listIndex)
                            parent.value = list.toByteArray()
                        }
                    }
                    is NbtIntArray -> {
                        if (node.listIndex >= 0 && node.listIndex < parent.value.size) {
                            val list = parent.value.toMutableList()
                            list.removeAt(node.listIndex)
                            parent.value = list.toIntArray()
                        }
                    }
                    is NbtLongArray -> {
                        if (node.listIndex >= 0 && node.listIndex < parent.value.size) {
                            val list = parent.value.toMutableList()
                            list.removeAt(node.listIndex)
                            parent.value = list.toLongArray()
                        }
                    }
                    else -> {}
                }
                isDirty = true
                treeAdapter.refreshTree()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        BackgroundUtils.applyDialogTheme(dialog, this)
        dialog.show()
    }

    private fun serializeCurrentNbt(): ByteArray {
        val nbtFile = currentNbtFile ?: return byteArrayOf()
        val baos = ByteArrayOutputStream()
        if (!readHeaders) {
            NbtIO.writeNbtFile(baos, nbtFile, isCompressed, isLittleEndian)
        } else {
            val nbtBaos = ByteArrayOutputStream()
            NbtIO.writeNbtFile(nbtBaos, nbtFile, isCompressed, isLittleEndian)
            val nbtBytes = nbtBaos.toByteArray()
            val finalStream = LittleEndianDataOutputStream(baos)
            finalStream.writeInt(headerVersion ?: 0)
            finalStream.writeInt(nbtBytes.size)
            finalStream.write(nbtBytes)
            finalStream.flush()
        }
        return baos.toByteArray()
    }

    private fun updateZipWithNbt(originalZip: ByteArray, entryNameToReplace: String, newNbtBytes: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(originalZip)).use { zis ->
            ZipOutputStream(baos).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newEntry = ZipEntry(entry.name)
                    zos.putNextEntry(newEntry)
                    if (entry.name == entryNameToReplace) {
                        zos.write(newNbtBytes)
                    } else {
                        zis.copyTo(zos)
                    }
                    zos.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return baos.toByteArray()
    }

    private fun updateZipWithLevelDbFolder(originalZip: ByteArray, levelDbFolder: File): ByteArray {
        val baos = ByteArrayOutputStream()
        val writtenEntries = mutableSetOf<String>()

        ZipInputStream(ByteArrayInputStream(originalZip)).use { zis ->
            ZipOutputStream(baos).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith("db/") || name.startsWith("db\\")) {
                        val relName = name.substringAfter("db/").substringAfter("db\\")
                        val file = File(levelDbFolder, relName)
                        if (file.exists() && !file.isDirectory) {
                            val newEntry = ZipEntry("db/$relName")
                            zos.putNextEntry(newEntry)
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                            writtenEntries.add(relName)
                        }
                    } else {
                        val newEntry = ZipEntry(name)
                        zos.putNextEntry(newEntry)
                        zis.copyTo(zos)
                        zos.closeEntry()
                    }
                    entry = zis.nextEntry
                }

                levelDbFolder.listFiles()?.forEach { f ->
                    if (!f.isDirectory && !writtenEntries.contains(f.name) && f.name != "LOCK") {
                        val newEntry = ZipEntry("db/${f.name}")
                        zos.putNextEntry(newEntry)
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
        return baos.toByteArray()
    }

    private fun saveCurrentFile(silent: Boolean = false) {
        val nbtFile = currentNbtFile ?: return

        val levelDb = currentLevelDbHelper
        val record = currentLevelDbRecord
        if (levelDb != null && record != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val updatedNbtBytes = BedrockLevelDbHelper.writeBedrockNbt(nbtFile, record.hasMultipleCompounds)
                    levelDb.put(record.key, updatedNbtBytes)

                    val updatedRecord = record.copy(valueSize = updatedNbtBytes.size)
                    currentLevelDbRecord = updatedRecord

                    val index = cachedLevelDbRecords.indexOfFirst { it.key.contentEquals(record.key) }
                    if (index >= 0) {
                        val mutable = cachedLevelDbRecords.toMutableList()
                        mutable[index] = updatedRecord
                        cachedLevelDbRecords = mutable
                    }

                    val zipBytes = originalZipBytes
                    if (zipBytes != null && currentLevelDbCachedFolder != null) {
                        val updatedZip = updateZipWithLevelDbFolder(zipBytes, currentLevelDbCachedFolder!!)
                        originalZipBytes = updatedZip
                        saveWorkspaceToCache(updatedZip, activeWorkspaceName)

                        if (currentFilePath != null) {
                            val file = File(currentFilePath!!)
                            val bak = File("$currentFilePath.bak")
                            if (file.exists()) file.copyTo(bak, overwrite = true)
                            file.writeBytes(updatedZip)
                        } else if (currentFileUri != null) {
                            contentResolver.openOutputStream(currentFileUri!!)?.use { it.write(updatedZip) }
                        }
                    } else if (currentFilePath != null && currentLevelDbCachedFolder != null) {
                        val originalFolder = File(currentFilePath!!)
                        if (originalFolder.isDirectory) {
                            currentLevelDbCachedFolder!!.listFiles()?.forEach { f ->
                                if (!f.isDirectory && f.name != "LOCK") {
                                    f.copyTo(File(originalFolder, f.name), overwrite = true)
                                }
                            }
                        }
                    }

                    isDirty = false
                    withContext(Dispatchers.Main) {
                        levelDbAdapter?.updateRecord(updatedRecord)
                        if (!silent) {
                            val msg = getString(R.string.nbt_leveldb_save_success, record.displayName)
                            Toast.makeText(this@NbtEditorActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (!silent) {
                            Toast.makeText(this@NbtEditorActivity, getString(R.string.nbt_save_failed, e.message), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            return
        }

        val region = currentAnvilRegionFile
        if (region != null) {
            try {
                region.writeChunkNbt(currentChunkX, currentChunkZ, nbtFile)

                val zipBytes = originalZipBytes
                val zipEntry = selectedZipEntryName
                if (zipBytes != null && zipEntry != null && currentRegionCachedFile != null) {
                    val regionBytes = currentRegionCachedFile!!.readBytes()
                    workspaceZipEntries[zipEntry] = regionBytes
                    val updatedZip = updateZipWithNbt(zipBytes, zipEntry, regionBytes)
                    originalZipBytes = updatedZip

                    saveWorkspaceToCache(updatedZip, activeWorkspaceName)

                    if (currentFilePath != null) {
                        val file = File(currentFilePath!!)
                        val bak = File("$currentFilePath.bak")
                        if (file.exists()) file.copyTo(bak, overwrite = true)
                        file.writeBytes(updatedZip)
                    } else if (currentFileUri != null) {
                        contentResolver.openOutputStream(currentFileUri!!)?.use { it.write(updatedZip) }
                    }
                } else if (currentRegionCachedFile != null) {
                    if (currentFilePath != null) {
                        val file = File(currentFilePath!!)
                        val bak = File("$currentFilePath.bak")
                        if (file.exists()) file.copyTo(bak, overwrite = true)
                        currentRegionCachedFile!!.copyTo(file, overwrite = true)
                    } else if (currentFileUri != null) {
                        contentResolver.openOutputStream(currentFileUri!!)?.use { out ->
                            currentRegionCachedFile!!.inputStream().use { it.copyTo(out) }
                        }
                    }
                }

                isDirty = false
                if (!silent) {
                    val msg = getString(R.string.nbt_chunk_save_success, currentChunkX, currentChunkZ, currentFileName)
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!silent) {
                    Toast.makeText(this, getString(R.string.nbt_save_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
            return
        }

        val zipBytes = originalZipBytes
        val zipEntry = selectedZipEntryName
        if (zipBytes != null && zipEntry != null) {
            try {
                val updatedNbt = serializeCurrentNbt()
                workspaceZipEntries[zipEntry] = updatedNbt
                val updatedZip = updateZipWithNbt(zipBytes, zipEntry, updatedNbt)
                originalZipBytes = updatedZip

                saveWorkspaceToCache(updatedZip, activeWorkspaceName)

                if (currentFilePath != null) {
                    val file = File(currentFilePath!!)
                    val bak = File("$currentFilePath.bak")
                    if (file.exists()) file.copyTo(bak, overwrite = true)
                    file.writeBytes(updatedZip)
                } else if (currentFileUri != null) {
                    contentResolver.openOutputStream(currentFileUri!!)?.use { it.write(updatedZip) }
                }
                isDirty = false
                if (!silent) {
                    Toast.makeText(this, getString(R.string.nbt_zip_save_success, zipEntry), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!silent) {
                    Toast.makeText(this, getString(R.string.nbt_save_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
            return
        }

        val path = currentFilePath
        if (path != null) {
            val file = File(path)
            try {
                val bakFile = File("$path.bak")
                if (file.exists()) {
                    file.copyTo(bakFile, overwrite = true)
                }

                NbtIO.writeNbtFile(file, nbtFile, isCompressed, isLittleEndian, readHeaders)
                isDirty = false
                if (!silent) {
                    Toast.makeText(this, R.string.nbt_save_success, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!silent) {
                    Toast.makeText(this, getString(R.string.nbt_save_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        } else if (currentFileUri != null) {
            saveToUri(currentFileUri!!, silent)
        } else {
            saveAsLauncher.launch(currentFileName.ifEmpty { "level.dat" })
        }
    }

    private fun saveToUri(uri: Uri, silent: Boolean = false) {
        val nbtFile = currentNbtFile ?: return
        try {
            val zipBytes = originalZipBytes
            val zipEntry = selectedZipEntryName
            if (zipBytes != null && zipEntry != null) {
                val updatedNbt = serializeCurrentNbt()
                workspaceZipEntries[zipEntry] = updatedNbt
                val updatedZip = updateZipWithNbt(zipBytes, zipEntry, updatedNbt)
                originalZipBytes = updatedZip
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(updatedZip)
                }
                isDirty = false
                if (!silent) {
                    Toast.makeText(this, getString(R.string.nbt_zip_save_success, zipEntry), Toast.LENGTH_SHORT).show()
                }
                return
            }

            contentResolver.openOutputStream(uri)?.use { stream ->
                if (!readHeaders) {
                    NbtIO.writeNbtFile(stream, nbtFile, isCompressed, isLittleEndian)
                } else {
                    val nbtBytes = serializeCurrentNbt()
                    stream.write(nbtBytes)
                }
            }
            isDirty = false
            if (!silent) {
                Toast.makeText(this, R.string.nbt_save_success, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            if (!silent) {
                Toast.makeText(this, getString(R.string.nbt_save_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("NBT", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.nbt_copied, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentAnvilRegionFile?.close()
        currentAnvilRegionFile = null
        currentLevelDbHelper?.close()
        currentLevelDbHelper = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_nbt_editor, menu)
        val autoSaveItem = menu.findItem(R.id.action_auto_save)
        autoSaveItem?.isChecked = prefs.getBoolean("nbt_auto_save", false)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleExit()
                true
            }
            R.id.action_open -> {
                openFilePicker()
                true
            }
            R.id.action_save -> {
                saveCurrentFile()
                true
            }
            R.id.action_save_as -> {
                val defaultName = currentFileName.ifEmpty { "level.dat" }
                saveAsLauncher.launch(defaultName)
                true
            }
            R.id.action_switch_file -> {
                showFileSwitcherDialog()
                true
            }
            R.id.action_auto_save -> {
                val isChecked = !item.isChecked
                item.isChecked = isChecked
                prefs.edit().putBoolean("nbt_auto_save", isChecked).apply()
                val msg = if (isChecked) R.string.nbt_auto_save_enabled_msg else R.string.nbt_auto_save_disabled_msg
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_expand_all -> {
                treeAdapter.expandAll()
                true
            }
            R.id.action_collapse_all -> {
                treeAdapter.collapseAll()
                true
            }
            R.id.action_close_workspace -> {
                clearWorkspaceSession()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
