package com.encore.chunkoid.ui.activity

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import br.com.gamemods.nbtmanipulator.*
import com.encore.chunkoid.R
import com.encore.chunkoid.databinding.ActivityDimensionSettingsBinding
import com.encore.chunkoid.utils.BackgroundUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

import android.widget.TextView
import com.encore.chunkoid.core.leveldb.BedrockLevelDbHelper
import com.encore.chunkoid.ui.view.ChunkGridView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.ChipGroup

class DimensionSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDimensionSettingsBinding
    private var loadingDialog: Dialog? = null

    private var scannedWorldUri: Uri? = null
    private var scannedZipBytes: ByteArray? = null
    private var scannedMcaEntries: MutableMap<String, ByteArray> = mutableMapOf()
    private var scannedBedrockDbFiles: MutableMap<String, ByteArray> = mutableMapOf()
    private var scannedRegionList: MutableList<Pair<String, List<Pair<Int, Int>>>> = mutableListOf()
    private var scannedTotalPopulated = 0
    private var scannedTotalUseless = 0
    private var scannedIsBedrockWorld: Boolean = false
    private var scannedSpawnX: Int = 0
    private var scannedSpawnZ: Int = 0

    private val selectWorldLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            analyzeWorldSave(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDimensionSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.dimension_and_chunk_settings)

        setupSmartProfiles()
        setupOverworldPruning()
        setupNetherStrategy()
        setupEndStrategy()
        setupWorldScannerAndOptimizer()
        setupAdvancedTools()
    }

    private fun setupSmartProfiles() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentProfile = prefs.getString("smart_pruning_profile", "smart_auto")

        when (currentProfile) {
            "smart_auto" -> binding.chipProfileSmart.isChecked = true
            "builder" -> binding.chipProfileBuilder.isChecked = true
            "speed" -> binding.chipProfileSpeed.isChecked = true
            "custom" -> binding.chipProfileCustom.isChecked = true
            else -> binding.chipProfileSmart.isChecked = true
        }

        binding.chipGroupSmartProfiles.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds.first()) {
                R.id.chip_profile_smart -> applySmartProfile("smart_auto")
                R.id.chip_profile_builder -> applySmartProfile("builder")
                R.id.chip_profile_speed -> applySmartProfile("speed")
                R.id.chip_profile_custom -> applySmartProfile("custom")
            }
        }
    }

    private fun applySmartProfile(profile: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString("smart_pruning_profile", profile).apply()

        when (profile) {
            "smart_auto" -> {

                binding.switchEnableChunkPruning.isChecked = true
                binding.rbAnchorSpawn.isChecked = true
                binding.chipPreset32.isChecked = true
                binding.rbNetherCore.isChecked = true
                binding.rbEndIsland.isChecked = true
            }
            "builder" -> {

                binding.switchEnableChunkPruning.isChecked = true
                binding.rbAnchorSpawn.isChecked = true
                binding.chipPreset64.isChecked = true
                binding.rbNetherAll.isChecked = true
                binding.rbEndIsland.isChecked = true
            }
            "speed" -> {

                binding.switchEnableChunkPruning.isChecked = true
                binding.rbAnchorSpawn.isChecked = true
                binding.chipPreset16.isChecked = true
                binding.rbNetherNone.isChecked = true
                binding.rbEndNone.isChecked = true
            }
            "custom" -> {

            }
        }
    }

    private fun setupOverworldPruning() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isChunkPruningEnabled = prefs.getBoolean("enable_chunk_pruning", true)
        binding.switchEnableChunkPruning.isChecked = isChunkPruningEnabled
        binding.layoutPruningOptions.visibility = if (isChunkPruningEnabled) View.VISIBLE else View.GONE

        binding.switchEnableChunkPruning.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_chunk_pruning", isChecked).apply()
            binding.layoutPruningOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        val anchor = prefs.getString("overworld_center_anchor", "spawn")
        if (anchor == "origin") {
            binding.rbAnchorOrigin.isChecked = true
        } else {
            binding.rbAnchorSpawn.isChecked = true
        }

        binding.rgCenterAnchor.setOnCheckedChangeListener { _, checkedId ->
            val newAnchor = if (checkedId == R.id.rb_anchor_origin) "origin" else "spawn"
            prefs.edit().putString("overworld_center_anchor", newAnchor).apply()
        }

        val currentPreset = prefs.getInt("chunk_prune_preset", 32)
        when (currentPreset) {
            16 -> binding.chipPreset16.isChecked = true
            32 -> binding.chipPreset32.isChecked = true
            64 -> binding.chipPreset64.isChecked = true
            else -> binding.chipPresetCustom.isChecked = true
        }

        val minX = prefs.getInt("chunk_prune_min_x", -32)
        val maxX = prefs.getInt("chunk_prune_max_x", 32)
        val minZ = prefs.getInt("chunk_prune_min_z", -32)
        val maxZ = prefs.getInt("chunk_prune_max_z", 32)

        binding.etMinChunkX.setText(minX.toString())
        binding.etMaxChunkX.setText(maxX.toString())
        binding.etMinChunkZ.setText(minZ.toString())
        binding.etMaxChunkZ.setText(maxZ.toString())

        binding.layoutCustomCoords.visibility = if (currentPreset == -1) View.VISIBLE else View.GONE

        binding.chipGroupPrunePresets.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds.first()) {
                R.id.chip_preset_16 -> {
                    prefs.edit().putInt("chunk_prune_preset", 16)
                        .putInt("chunk_prune_min_x", -16)
                        .putInt("chunk_prune_max_x", 16)
                        .putInt("chunk_prune_min_z", -16)
                        .putInt("chunk_prune_max_z", 16)
                        .apply()
                    binding.etMinChunkX.setText("-16")
                    binding.etMaxChunkX.setText("16")
                    binding.etMinChunkZ.setText("-16")
                    binding.etMaxChunkZ.setText("16")
                    binding.layoutCustomCoords.visibility = View.GONE
                }
                R.id.chip_preset_32 -> {
                    prefs.edit().putInt("chunk_prune_preset", 32)
                        .putInt("chunk_prune_min_x", -32)
                        .putInt("chunk_prune_max_x", 32)
                        .putInt("chunk_prune_min_z", -32)
                        .putInt("chunk_prune_max_z", 32)
                        .apply()
                    binding.etMinChunkX.setText("-32")
                    binding.etMaxChunkX.setText("32")
                    binding.etMinChunkZ.setText("-32")
                    binding.etMaxChunkZ.setText("32")
                    binding.layoutCustomCoords.visibility = View.GONE
                }
                R.id.chip_preset_64 -> {
                    prefs.edit().putInt("chunk_prune_preset", 64)
                        .putInt("chunk_prune_min_x", -64)
                        .putInt("chunk_prune_max_x", 64)
                        .putInt("chunk_prune_min_z", -64)
                        .putInt("chunk_prune_max_z", 64)
                        .apply()
                    binding.etMinChunkX.setText("-64")
                    binding.etMaxChunkX.setText("64")
                    binding.etMinChunkZ.setText("-64")
                    binding.etMaxChunkZ.setText("64")
                    binding.layoutCustomCoords.visibility = View.GONE
                }
                R.id.chip_preset_custom -> {
                    prefs.edit().putInt("chunk_prune_preset", -1).apply()
                    binding.layoutCustomCoords.visibility = View.VISIBLE
                }
            }
        }

        fun setupCoordWatcher(view: com.google.android.material.textfield.TextInputEditText, key: String, defaultVal: Int) {
            view.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val num = s?.toString()?.trim()?.toIntOrNull() ?: defaultVal
                    prefs.edit().putInt(key, num).apply()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        setupCoordWatcher(binding.etMinChunkX, "chunk_prune_min_x", -32)
        setupCoordWatcher(binding.etMaxChunkX, "chunk_prune_max_x", 32)
        setupCoordWatcher(binding.etMinChunkZ, "chunk_prune_min_z", -32)
        setupCoordWatcher(binding.etMaxChunkZ, "chunk_prune_max_z", 32)
    }

    private fun setupNetherStrategy() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val strategy = prefs.getString("nether_strategy", "core")

        when (strategy) {
            "core" -> binding.rbNetherCore.isChecked = true
            "all" -> binding.rbNetherAll.isChecked = true
            "none" -> binding.rbNetherNone.isChecked = true
            else -> binding.rbNetherCore.isChecked = true
        }

        binding.rgNetherStrategy.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.rb_nether_core -> "core"
                R.id.rb_nether_all -> "all"
                R.id.rb_nether_none -> "none"
                else -> "core"
            }
            prefs.edit()
                .putString("nether_strategy", selected)
                .putBoolean("keep_nether", selected != "none")
                .apply()
        }
    }

    private fun setupEndStrategy() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val strategy = prefs.getString("end_strategy", "island_only")

        when (strategy) {
            "island_only" -> binding.rbEndIsland.isChecked = true
            "all" -> binding.rbEndAll.isChecked = true
            "none" -> binding.rbEndNone.isChecked = true
            else -> binding.rbEndIsland.isChecked = true
        }

        binding.rgEndStrategy.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.rb_end_island -> "island_only"
                R.id.rb_end_all -> "all"
                R.id.rb_end_none -> "none"
                else -> "island_only"
            }
            prefs.edit()
                .putString("end_strategy", selected)
                .putBoolean("keep_end", selected != "none")
                .apply()
        }
    }

    private fun setupWorldScannerAndOptimizer() {
        binding.btnSelectWorldToScan.setOnClickListener {
            selectWorldLauncher.launch(arrayOf("*/*"))
        }

        binding.btnOpenChunkMapPicker.setOnClickListener {
            showWorldChunkMapPickerDialog()
        }

        binding.btnRunWorldPrune.setOnClickListener {
            runOneClickWorldPrune()
        }
    }

    private fun setupAdvancedTools() {
        binding.btnOpenNbtEditor.setOnClickListener {
            val intent = Intent(this, NbtEditorActivity::class.java)
            startActivity(intent)
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "minecraft_world.zip"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val str = cursor.getString(nameIndex)
                    if (!str.isNullOrBlank()) name = str
                }
            }
        } catch (e: Exception) {}
        return name
    }

    private fun analyzeWorldSave(uri: Uri) {
        showLoading("正在深度体检与分析世界存档...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                scannedWorldUri = uri
                scannedMcaEntries.clear()
                scannedBedrockDbFiles.clear()
                scannedRegionList.clear()
                scannedTotalPopulated = 0
                scannedTotalUseless = 0

                val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (rawBytes == null || rawBytes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        Toast.makeText(this@DimensionSettingsActivity, "无法读取该文件，可能没有权限", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                scannedZipBytes = rawBytes

                var worldName = "Minecraft World"
                var versionStr = "未知"
                var spawnX = 0
                var spawnY = 64
                var spawnZ = 0
                var isBedrockWorld = false
                var bedrockChunkKeysCount = 0

                val tempDir = File(cacheDir, "analyzer_temp").apply {
                    if (exists()) deleteRecursively()
                    mkdirs()
                }

                ZipInputStream(ByteArrayInputStream(rawBytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name.endsWith("level.dat", true)) {
                            val datBytes = zis.readBytes()

                            try {
                                val nbt = NbtIO.readNbtFile(ByteArrayInputStream(datBytes) as java.io.InputStream, true, false, false)
                                val dataTag = (nbt.tag as? NbtCompound)?.get("Data") as? NbtCompound ?: nbt.tag as? NbtCompound
                                if (dataTag != null) {
                                    worldName = (dataTag["LevelName"] as? NbtString)?.value ?: worldName
                                    versionStr = (dataTag["Version"] as? NbtCompound)?.get("Name")?.let { (it as? NbtString)?.value } ?: "Java"
                                    spawnX = (dataTag["SpawnX"] as? NbtInt)?.value ?: 0
                                    spawnY = (dataTag["SpawnY"] as? NbtInt)?.value ?: 64
                                    spawnZ = (dataTag["SpawnZ"] as? NbtInt)?.value ?: 0
                                }
                            } catch (e: Exception) {

                                try {
                                    val nbt = NbtIO.readNbtFile(ByteArrayInputStream(datBytes) as java.io.InputStream, false, true, true)
                                    val tag = nbt.tag as? NbtCompound
                                    if (tag != null) {
                                        isBedrockWorld = true
                                        worldName = (tag["LevelName"] as? NbtString)?.value ?: worldName
                                        versionStr = "Bedrock"
                                        spawnX = (tag["SpawnX"] as? NbtInt)?.value ?: 0
                                        spawnY = (tag["SpawnY"] as? NbtInt)?.value ?: 64
                                        spawnZ = (tag["SpawnZ"] as? NbtInt)?.value ?: 0
                                    }
                                } catch (e2: Exception) {

                                }
                            }
                        } else if (name.endsWith(".mca", true) || name.endsWith(".mcr", true)) {
                            val mcaBytes = zis.readBytes()
                            scannedMcaEntries[name] = mcaBytes
                            val tempFile = File(tempDir, "temp_${scannedMcaEntries.size}_${File(name).name}")
                            tempFile.writeBytes(mcaBytes)
                            try {
                                AnvilRegionFile(tempFile).use { anvil ->
                                    val pop = anvil.getPopulatedChunks()
                                    scannedTotalPopulated += pop.size
                                    val useless = anvil.scanUselessChunks(maxInhabitedTimeTicks = 0L)
                                    scannedTotalUseless += useless.size
                                    if (useless.isNotEmpty()) {
                                        scannedRegionList.add(Pair(name, useless.map { Pair(it.first.localX, it.first.localZ) }))
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ChunkoidSlim", "Error parsing MCA ${name}: ${e.message}")
                            }
                        } else if ((name.contains("db/") || name.contains("db\\")) && !entry.isDirectory) {
                            isBedrockWorld = true
                            bedrockChunkKeysCount++
                            scannedBedrockDbFiles[name] = zis.readBytes()
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                tempDir.deleteRecursively()

                scannedIsBedrockWorld = isBedrockWorld
                scannedSpawnX = spawnX
                scannedSpawnZ = spawnZ

                val prefs = PreferenceManager.getDefaultSharedPreferences(this@DimensionSettingsActivity)
                prefs.edit()
                    .putInt("world_spawn_x", spawnX)
                    .putInt("world_spawn_z", spawnZ)
                    .apply()

                withContext(Dispatchers.Main) {
                    hideLoading()
                    binding.tvScanWorldName.text = "世界名称: $worldName (版本: $versionStr)"
                    binding.tvScanSpawnCoords.text = "世界真实出生点: [X: $spawnX, Y: $spawnY, Z: $spawnZ] (区块: [${spawnX shr 4}, ${spawnZ shr 4}])"

                    val rawArchiveSize = scannedZipBytes?.size?.toLong() ?: 0L
                    if (isBedrockWorld && scannedMcaEntries.isEmpty()) {
                        binding.tvScanChunksStats.text = "已识别基岩版 LevelDB 数据库存档 | 数据文件: $bedrockChunkKeysCount 个 | 存档大小: ${formatBytes(rawArchiveSize)}\n点击下方【地图可视化选区删除】可圈选任意区块物理擦除。"
                    } else {
                        val pct = if (scannedTotalPopulated > 0) (scannedTotalUseless.toFloat() / scannedTotalPopulated.toFloat()) * 100f else 0f
                        val estimatedSavings = if (scannedTotalPopulated > 0) {
                            (rawArchiveSize * (scannedTotalUseless.toDouble() / scannedTotalPopulated.toDouble())).toLong()
                        } else {
                            0L
                        }
                        val estimatedNewSize = (rawArchiveSize - estimatedSavings).coerceAtLeast(0L)
                        binding.tvScanChunksStats.text = "已生成区块: $scannedTotalPopulated 个 | 0停留时间无用区块: $scannedTotalUseless 个 (${"%.1f".format(pct)}%)\n存档大小: ${formatBytes(rawArchiveSize)} | 预计瘦身至 ~${formatBytes(estimatedNewSize)} (节省 ~${formatBytes(estimatedSavings)})"
                    }
                    binding.layoutScanReport.visibility = View.VISIBLE

                    Toast.makeText(this@DimensionSettingsActivity, "体检完成！已成功识别出生点与区块数据", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("ChunkoidSlim", "analyzeWorldSave error", e)
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@DimensionSettingsActivity, "分析失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showWorldChunkMapPickerDialog() {
        val uri = scannedWorldUri
        val zipBytes = scannedZipBytes
        if (uri == null || zipBytes == null || (scannedMcaEntries.isEmpty() && scannedBedrockDbFiles.isEmpty())) {
            Toast.makeText(this, "请先选择并体检一个世界存档", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_chunk_picker, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        BackgroundUtils.applyDialogTheme(dialog, this, dialogView)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_region_title)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tv_region_subtitle)
        val toggleTool = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_select_tool)
        val scrollDims = dialogView.findViewById<View>(R.id.scroll_dimension_tabs)
        val chipGroupDims = dialogView.findViewById<ChipGroup>(R.id.chip_group_dimensions)
        val chunkGridView = dialogView.findViewById<ChunkGridView>(R.id.chunk_grid_view)
        val tvSelectedChunkCoord = dialogView.findViewById<TextView>(R.id.tv_selected_chunk_coord)
        val tvSelectedChunkStatus = dialogView.findViewById<TextView>(R.id.tv_selected_chunk_status)
        val tvSelectedBlockRange = dialogView.findViewById<TextView>(R.id.tv_selected_block_range)
        val btnActionSelectAll = dialogView.findViewById<View>(R.id.btn_action_select_all)
        val btnActionInvert = dialogView.findViewById<View>(R.id.btn_action_invert)
        val btnActionClear = dialogView.findViewById<View>(R.id.btn_action_clear)
        val btnPruneUseless = dialogView.findViewById<View>(R.id.btn_prune_useless_chunks)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel_chunk_picker)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btn_confirm_select_chunk)
        val btnDeleteChunk = dialogView.findViewById<MaterialButton>(R.id.btn_delete_chunk_dialog)

        btnConfirm?.visibility = View.GONE
        btnCancel?.setOnClickListener { dialog.dismiss() }

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

        if (!scannedIsBedrockWorld && scannedMcaEntries.isNotEmpty()) {
            tvTitle.text = "世界 2D 区块地图 (Java 版)"
            scrollDims.visibility = View.GONE

            showLoading("正在解析全部区域地图...")
            lifecycleScope.launch(Dispatchers.IO) {
                val allGlobalChunks = mutableSetOf<Pair<Int, Int>>()
                val uselessChunks = mutableSetOf<Pair<Int, Int>>()

                val tempDir = File(cacheDir, "map_picker_temp").apply {
                    if (exists()) deleteRecursively()
                    mkdirs()
                }

                for ((mcaName, mcaBytes) in scannedMcaEntries) {
                    val coords = AnvilRegionFile.parseRegionCoordinates(File(mcaName).name) ?: continue
                    val rx = coords.first
                    val rz = coords.second
                    val tempFile = File(tempDir, File(mcaName).name)
                    tempFile.writeBytes(mcaBytes)
                    try {
                        AnvilRegionFile(tempFile).use { anvil ->
                            for (c in anvil.getPopulatedChunks()) {
                                val gx = rx * 32 + c.localX
                                val gz = rz * 32 + c.localZ
                                allGlobalChunks.add(Pair(gx, gz))
                            }
                            for (u in anvil.scanUselessChunks(0L)) {
                                val gx = rx * 32 + u.first.localX
                                val gz = rz * 32 + u.first.localZ
                                uselessChunks.add(Pair(gx, gz))
                            }
                        }
                    } catch (e: Exception) {}
                }
                tempDir.deleteRecursively()

                withContext(Dispatchers.Main) {
                    hideLoading()
                    tvSubtitle.text = "已生成 ${allGlobalChunks.size} 个区块 (含 ${uselessChunks.size} 个0停留时间区块)"
                    chunkGridView.setWorldChunkData(
                        allGlobalChunks,
                        spawnX = scannedSpawnX shr 4,
                        spawnZ = scannedSpawnZ shr 4
                    )
                    btnPruneUseless?.setOnClickListener {
                        chunkGridView.clearSelection()
                        chunkGridView.selectedChunks.addAll(uselessChunks)
                        chunkGridView.onSelectionChangedListener?.invoke(uselessChunks.size, uselessChunks)
                        chunkGridView.invalidate()
                    }

                    btnDeleteChunk.setOnClickListener {
                        val targets = if (chunkGridView.selectedChunks.isNotEmpty()) {
                            chunkGridView.selectedChunks.toSet()
                        } else if (chunkGridView.isChunkPopulated(chunkGridView.selectedX, chunkGridView.selectedZ)) {
                            setOf(Pair(chunkGridView.selectedX, chunkGridView.selectedZ))
                        } else emptySet()

                        if (targets.isEmpty()) return@setOnClickListener

                        MaterialAlertDialogBuilder(this@DimensionSettingsActivity)
                            .setTitle("确认物理删除区块")
                            .setMessage("确定要物理删除选中的 ${targets.size} 个区块吗？\n将自动创建原存档备份。")
                            .setPositiveButton("立即删除") { _, _ ->
                                dialog.dismiss()
                                executeCustomJavaChunksPrune(uri, zipBytes, targets)
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }

                    dialog.show()
                }
            }
        } else if (scannedIsBedrockWorld) {
            tvTitle.text = "世界 2D 区块地图 (基岩版)"
            scrollDims.visibility = View.VISIBLE

            showLoading("正在解析 LevelDB 数据库区块...")
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val tempDbDir = File(cacheDir, "bedrock_map_db").apply {
                        if (exists()) deleteRecursively()
                        mkdirs()
                    }
                    for ((fName, fBytes) in scannedBedrockDbFiles) {
                        val relPath = if (fName.contains("db/")) fName.substringAfter("db/") else if (fName.contains("db\\")) fName.substringAfter("db\\") else File(fName).name
                        if (relPath.isNotBlank()) {
                            val targetFile = File(tempDbDir, relPath)
                            targetFile.parentFile?.mkdirs()
                            targetFile.writeBytes(fBytes)
                        }
                    }

                    val helper = BedrockLevelDbHelper(tempDbDir)

                    var currentDim = 0
                    fun loadBedrockDim(dimId: Int) {
                        currentDim = dimId
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val populated = helper.getPopulatedChunkCoordinates(dimId)
                                val blockEntities = helper.getBlockEntityChunkCoordinates(dimId)
                                withContext(Dispatchers.Main) {
                                    tvSubtitle.text = "当前维度已生成 ${populated.size} 个区块"
                                    chunkGridView.clearSelection()
                                    chunkGridView.setWorldChunkData(
                                        populated,
                                        blockEntityCoords = blockEntities,
                                        spawnX = if (dimId == 0) scannedSpawnX shr 4 else null,
                                        spawnZ = if (dimId == 0) scannedSpawnZ shr 4 else null
                                    )
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@DimensionSettingsActivity, "加载维度区块失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        hideLoading()
                        loadBedrockDim(0)

                        chipGroupDims?.setOnCheckedStateChangeListener { _, checkedIds ->
                            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
                            when (checkedIds.first()) {
                                R.id.chip_dim_overworld -> loadBedrockDim(0)
                                R.id.chip_dim_nether -> loadBedrockDim(1)
                                R.id.chip_dim_end -> loadBedrockDim(2)
                            }
                        }

                        btnPruneUseless?.visibility = View.GONE

                        btnDeleteChunk.setOnClickListener {
                            val targets = if (chunkGridView.selectedChunks.isNotEmpty()) {
                                chunkGridView.selectedChunks.toSet()
                            } else if (chunkGridView.isChunkPopulated(chunkGridView.selectedX, chunkGridView.selectedZ)) {
                                setOf(Pair(chunkGridView.selectedX, chunkGridView.selectedZ))
                            } else emptySet()

                            if (targets.isEmpty()) return@setOnClickListener

                            MaterialAlertDialogBuilder(this@DimensionSettingsActivity)
                                .setTitle("确认物理删除区块")
                                .setMessage("确定要物理删除当前维度的 ${targets.size} 个区块吗？\n将自动创建原存档备份。")
                                .setPositiveButton("立即删除") { _, _ ->
                                    dialog.dismiss()
                                    executeCustomBedrockChunksPrune(uri, zipBytes, tempDbDir, helper, targets, currentDim)
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .show()
                        }

                        dialog.setOnDismissListener {
                            try {
                                helper.close()
                                tempDbDir.deleteRecursively()
                            } catch (e: Exception) {}
                        }

                        dialog.show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        Toast.makeText(this@DimensionSettingsActivity, "打开 LevelDB 数据库失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun executeCustomJavaChunksPrune(uri: Uri, zipBytes: ByteArray, targets: Set<Pair<Int, Int>>) {
        showLoading("正在执行指定区块物理删除...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                try {
                    val bakDir = File(filesDir, "backups").apply { mkdirs() }
                    val bakFile = File(bakDir, "world_backup_${System.currentTimeMillis()}.zip")
                    bakFile.writeBytes(zipBytes)
                } catch (e: Exception) {}

                val tempDir = File(cacheDir, "prune_custom_temp").apply {
                    if (exists()) deleteRecursively()
                    mkdirs()
                }

                val regionGroup = targets.groupBy { Pair(it.first shr 5, it.second shr 5) }
                val updatedMcaEntries = mutableMapOf<String, ByteArray>()
                var prunedCount = 0

                for ((name, mcaData) in scannedMcaEntries) {
                    val coords = AnvilRegionFile.parseRegionCoordinates(File(name).name)
                    if (coords != null && regionGroup.containsKey(coords)) {
                        val chunksInRegion = regionGroup[coords] ?: emptyList()
                        val localCoords = chunksInRegion.map { Pair(it.first and 31, it.second and 31) }
                        val tempFile = File(tempDir, "prune_${File(name).name}")
                        tempFile.writeBytes(mcaData)
                        try {
                            AnvilRegionFile(tempFile).use { anvil ->
                                anvil.deleteChunks(localCoords)
                                prunedCount += localCoords.size
                            }
                            updatedMcaEntries[name] = tempFile.readBytes()
                        } catch (e: Exception) {}
                    }
                }
                tempDir.deleteRecursively()

                val baos = ByteArrayOutputStream()
                ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                    ZipOutputStream(baos).use { zos ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val newEntry = ZipEntry(entry.name)
                            zos.putNextEntry(newEntry)
                            val updated = updatedMcaEntries[entry.name]
                            if (updated != null) {
                                zos.write(updated)
                            } else {
                                zis.copyTo(zos)
                            }
                            zos.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                savePrunedZipAndNotify(uri, zipBytes, baos.toByteArray(), prunedCount)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@DimensionSettingsActivity, "删除失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun executeCustomBedrockChunksPrune(
        uri: Uri,
        zipBytes: ByteArray,
        tempDbDir: File,
        helper: BedrockLevelDbHelper,
        targets: Set<Pair<Int, Int>>,
        dimId: Int
    ) {
        showLoading("正在执行基岩版 LevelDB 区块删除...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                try {
                    val bakDir = File(filesDir, "backups").apply { mkdirs() }
                    val bakFile = File(bakDir, "bedrock_backup_${System.currentTimeMillis()}.zip")
                    bakFile.writeBytes(zipBytes)
                } catch (e: Exception) {}

                val deletedCount = helper.deleteChunks(targets, dimId)
                helper.close()

                val updatedDbMap = mutableMapOf<String, ByteArray>()
                tempDbDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relPath = file.relativeTo(tempDbDir).path.replace('\\', '/')
                        updatedDbMap[relPath] = file.readBytes()
                    }
                }
                tempDbDir.deleteRecursively()

                val baos = ByteArrayOutputStream()
                val writtenDbKeys = mutableSetOf<String>()
                ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                    ZipOutputStream(baos).use { zos ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            if (name.contains("db/") || name.contains("db\\")) {
                                val rel = if (name.contains("db/")) name.substringAfter("db/") else name.substringAfter("db\\")
                                val updatedBytes = updatedDbMap[rel]
                                if (updatedBytes != null) {
                                    val newEntry = ZipEntry(name)
                                    zos.putNextEntry(newEntry)
                                    zos.write(updatedBytes)
                                    zos.closeEntry()
                                    writtenDbKeys.add(rel)
                                }
                            } else {
                                val newEntry = ZipEntry(name)
                                zos.putNextEntry(newEntry)
                                zis.copyTo(zos)
                                zos.closeEntry()
                            }
                            entry = zis.nextEntry
                        }
                        for ((rel, bData) in updatedDbMap) {
                            if (!writtenDbKeys.contains(rel)) {
                                val newEntry = ZipEntry("db/$rel")
                                zos.putNextEntry(newEntry)
                                zos.write(bData)
                                zos.closeEntry()
                            }
                        }
                    }
                }

                savePrunedZipAndNotify(uri, zipBytes, baos.toByteArray(), targets.size)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@DimensionSettingsActivity, "删除失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun savePrunedZipAndNotify(uri: Uri, origZipBytes: ByteArray, finalZipBytes: ByteArray, prunedCount: Int) {
        var writeDirectSuccess = false
        var savedOutFile: File? = null
        var directWriteError = ""

        try {
            val outStream = contentResolver.openOutputStream(uri, "rwt")
                ?: contentResolver.openOutputStream(uri, "wt")
                ?: contentResolver.openOutputStream(uri)
            if (outStream != null) {
                outStream.use { it.write(finalZipBytes) }
                writeDirectSuccess = true
            } else {
                directWriteError = "openOutputStream returned null"
            }
        } catch (e: Exception) {
            directWriteError = e.message ?: "Unknown security error"
        }

        if (!writeDirectSuccess) {
            val baseName = getFileName(uri).substringBeforeLast(".")
            var fallbackSuccess = false
            try {
                val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Chunkoid").apply { mkdirs() }
                val target = File(outDir, "${baseName}_pruned_${System.currentTimeMillis() % 10000}.zip")
                target.writeBytes(finalZipBytes)
                savedOutFile = target
                fallbackSuccess = true
            } catch (e: Exception) {}

            if (!fallbackSuccess) {
                val outDir = File(filesDir, "pruned").apply { mkdirs() }
                val target = File(outDir, "${baseName}_pruned_saved.zip")
                target.writeBytes(finalZipBytes)
                savedOutFile = target
            }
        }

        val origSize = origZipBytes.size.toLong()
        val newSize = finalZipBytes.size.toLong()
        val savedDiff = (origSize - newSize).coerceAtLeast(0L)

        withContext(Dispatchers.Main) {
            hideLoading()
            val resultMessage = if (writeDirectSuccess) {
                "🎉 删除成功！原存档已更新。\n\n• 成功删除区块: $prunedCount 个\n• 存档体积: ${formatBytes(origSize)} ➔ ${formatBytes(newSize)} (节省 ${formatBytes(savedDiff)})"
            } else {
                "🎉 删除成功！\n注: 原存档为只读，新存档已另存至：\n📂 ${savedOutFile?.absolutePath}\n\n• 成功删除区块: $prunedCount 个\n• 存档体积: ${formatBytes(origSize)} ➔ ${formatBytes(newSize)} (节省 ${formatBytes(savedDiff)})"
            }

            val resDialog = MaterialAlertDialogBuilder(this@DimensionSettingsActivity)
                .setTitle("区块删除完成")
                .setMessage(resultMessage)
                .setPositiveButton("确定") { _, _ ->
                    if (writeDirectSuccess) {
                        analyzeWorldSave(uri)
                    }
                }
                .create()
            BackgroundUtils.applyDialogTheme(resDialog, this@DimensionSettingsActivity)
            resDialog.show()
        }
    }

    private fun runOneClickWorldPrune() {
        val uri = scannedWorldUri
        val zipBytes = scannedZipBytes
        if (uri == null || zipBytes == null || scannedMcaEntries.isEmpty()) {
            Toast.makeText(this, "当前存档不包含 MCA 区域文件或请先选择存档体检", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf(
            "⚡ 一键清理 0 停留时间无用区块 (共 $scannedTotalUseless 个，推荐)",
            "⏱️ 清理低停留时间区块 (< 20 刻 / 1秒)",
            "🗑️ 清空重置全部已生成区块"
        )
        var selected = 0

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nbt_prune_dialog_title)
            .setMessage("确定要对该存档执行物理瘦身吗？\nChunkoid 将自动在 backups 目录创建原文件备份。")
            .setSingleChoiceItems(options, 0) { _, which ->
                selected = which
            }
            .setPositiveButton("立即执行瘦身") { _, _ ->
                executeWorldPruning(uri, zipBytes, selected)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        BackgroundUtils.applyDialogTheme(dialog, this)
        dialog.show()
    }

    private fun executeWorldPruning(uri: Uri, zipBytes: ByteArray, pruneMode: Int) {
        showLoading("正在执行物理瘦身并重构存档...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {

                try {
                    val bakDir = File(filesDir, "backups").apply { mkdirs() }
                    val bakFile = File(bakDir, "world_backup_${System.currentTimeMillis()}.zip")
                    bakFile.writeBytes(zipBytes)
                } catch (e: Exception) {
                    android.util.Log.w("ChunkoidSlim", "Backup failed: ${e.message}")
                }

                val tempDir = File(cacheDir, "prune_exec_temp").apply {
                    if (exists()) deleteRecursively()
                    mkdirs()
                }

                var prunedCount = 0
                val updatedMcaEntries = mutableMapOf<String, ByteArray>()

                for ((name, mcaData) in scannedMcaEntries) {
                    val tempFile = File(tempDir, "prune_${File(name).name}")
                    tempFile.writeBytes(mcaData)
                    try {
                        AnvilRegionFile(tempFile).use { anvil ->
                            val targetCoords = when (pruneMode) {
                                0 -> anvil.scanUselessChunks(0L).map { Pair(it.first.localX, it.first.localZ) }
                                1 -> anvil.scanUselessChunks(20L).map { Pair(it.first.localX, it.first.localZ) }
                                2 -> anvil.getPopulatedChunks().map { Pair(it.localX, it.localZ) }
                                else -> emptyList()
                            }
                            if (targetCoords.isNotEmpty()) {
                                anvil.deleteChunks(targetCoords)
                                prunedCount += targetCoords.size
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChunkoidSlim", "Failed to prune MCA file: $name", e)
                    }
                    updatedMcaEntries[name] = tempFile.readBytes()
                }

                tempDir.deleteRecursively()

                val baos = ByteArrayOutputStream()
                ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                    ZipOutputStream(baos).use { zos ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val newEntry = ZipEntry(entry.name)
                            zos.putNextEntry(newEntry)
                            val updated = updatedMcaEntries[entry.name]
                            if (updated != null) {
                                zos.write(updated)
                            } else {
                                zis.copyTo(zos)
                            }
                            zos.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                val finalZipBytes = baos.toByteArray()
                var writeDirectSuccess = false
                var savedOutFile: File? = null
                var directWriteError = ""

                try {
                    val outStream = contentResolver.openOutputStream(uri, "rwt")
                        ?: contentResolver.openOutputStream(uri, "wt")
                        ?: contentResolver.openOutputStream(uri)
                    if (outStream != null) {
                        outStream.use { it.write(finalZipBytes) }
                        writeDirectSuccess = true
                    } else {
                        directWriteError = "openOutputStream returned null"
                    }
                } catch (e: Exception) {
                    directWriteError = e.message ?: "Unknown security error"
                    android.util.Log.w("ChunkoidSlim", "Direct write failed: $directWriteError")
                }

                if (!writeDirectSuccess) {
                    val baseName = getFileName(uri).substringBeforeLast(".")
                    var fallbackSuccess = false

                    try {
                        val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Chunkoid").apply { mkdirs() }
                        val target = File(outDir, "${baseName}_slimming_${System.currentTimeMillis() % 10000}.zip")
                        target.writeBytes(finalZipBytes)
                        savedOutFile = target
                        fallbackSuccess = true
                    } catch (e: Exception) {
                        android.util.Log.e("ChunkoidSlim", "Fallback 1 (Downloads) failed: ${e.message}")
                    }

                    if (!fallbackSuccess) {
                        try {
                            val outDir = File(filesDir, "pruned").apply { mkdirs() }
                            val target = File(outDir, "${baseName}_slimming_saved.zip")
                            target.writeBytes(finalZipBytes)
                            savedOutFile = target
                            fallbackSuccess = true
                        } catch (e: Exception) {
                            android.util.Log.e("ChunkoidSlim", "Fallback 2 (filesDir) failed: ${e.message}")
                            throw Exception("彻底写入失败 (原因为: $directWriteError)，且后备保存也失败: ${e.message}")
                        }
                    }
                }

                val origSize = zipBytes.size.toLong()
                val newSize = finalZipBytes.size.toLong()
                val savedDiff = (origSize - newSize).coerceAtLeast(0L)

                withContext(Dispatchers.Main) {
                    hideLoading()
                    val resultMessage = if (writeDirectSuccess) {
                        "🎉 瘦身成功！原存档已更新。\n\n• 清理无用区块: $prunedCount 个\n• 存档体积: ${formatBytes(origSize)} ➔ ${formatBytes(newSize)} (节省 ${formatBytes(savedDiff)})"
                    } else {
                        "🎉 瘦身成功！\n注: 原存档为只读，新存档已另存至：\n📂 ${savedOutFile?.absolutePath}\n\n• 清理无用区块: $prunedCount 个\n• 存档体积: ${formatBytes(origSize)} ➔ ${formatBytes(newSize)} (节省 ${formatBytes(savedDiff)})"
                    }

                    val resDialog = MaterialAlertDialogBuilder(this@DimensionSettingsActivity)
                        .setTitle("瘦身完成")
                        .setMessage(resultMessage)
                        .setPositiveButton("确定") { _, _ ->
                            if (writeDirectSuccess) {
                                analyzeWorldSave(uri)
                            }
                        }
                        .create()
                    BackgroundUtils.applyDialogTheme(resDialog, this@DimensionSettingsActivity)
                    resDialog.show()
                }
            } catch (e: Exception) {
                android.util.Log.e("ChunkoidSlim", "executeWorldPruning fatal error", e)
                withContext(Dispatchers.Main) {
                    hideLoading()
                    val trace = e.stackTraceToString()
                    MaterialAlertDialogBuilder(this@DimensionSettingsActivity)
                        .setTitle("瘦身遇到异常")
                        .setMessage("详细错误日志:\n$trace")
                        .setPositiveButton("关闭", null)
                        .show()
                }
            }
        }
    }

    private fun showLoading(msg: String) {
        if (loadingDialog?.isShowing == true) loadingDialog?.dismiss()
        val builder = MaterialAlertDialogBuilder(this)
            .setMessage(msg)
            .setCancelable(false)
        loadingDialog = builder.create()
        BackgroundUtils.applyDialogTheme(loadingDialog!!, this)
        loadingDialog?.show()
    }

    private fun hideLoading() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            BackgroundUtils.applyCustomBackground(this)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}