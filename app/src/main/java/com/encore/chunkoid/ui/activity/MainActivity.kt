package com.encore.chunkoid.ui.activity

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.preference.PreferenceManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import androidx.drawerlayout.widget.DrawerLayout
import com.encore.chunkoid.R
import com.encore.chunkoid.core.world.ArchiveSearcher
import com.encore.chunkoid.core.world.NbtVersionReader
import com.encore.chunkoid.core.world.Platform
import com.encore.chunkoid.databinding.ActivityMainBinding
import com.encore.chunkoid.model.ConversionSettings
import com.encore.chunkoid.ui.fragment.OutputManagerFragment
import com.encore.chunkoid.utils.BackgroundUtils
import com.encore.chunkoid.utils.ToastUtils
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private var inputUri: Uri? = null
    private var inputWorldName: String = ""
    private var selectedFormat: String = ""
    private var selectedFormatDisplayName: String = ""
    private var isArchiveMode = false
    private var isWorldInfoVisible = false
    private var worldPlatform = Platform.BEDROCK
    private lateinit var archiveSearcher: ArchiveSearcher

    private fun mapVersionToChunkerFormat(displayName: String): String {
        val versionNum = displayName.replace("Java ", "").replace("Bedrock ", "")

        return if (displayName.startsWith("Java")) {
            mapJavaVersion(versionNum)
        } else {
            mapBedrockVersion(versionNum)
        }
    }

    private fun mapJavaVersion(versionNum: String): String {
        return when(versionNum) {
            getString(R.string.java_1_8_8) -> "JAVA_1_8_8"
            getString(R.string.java_1_9) -> "JAVA_1_9"
            getString(R.string.java_1_9_1) -> "JAVA_1_9_1"
            getString(R.string.java_1_9_2) -> "JAVA_1_9_2"
            getString(R.string.java_1_9_3) -> "JAVA_1_9_3"
            getString(R.string.java_1_10) -> "JAVA_1_10"
            getString(R.string.java_1_10_1) -> "JAVA_1_10_1"
            getString(R.string.java_1_10_2) -> "JAVA_1_10_2"
            getString(R.string.java_1_11) -> "JAVA_1_11"
            getString(R.string.java_1_11_1) -> "JAVA_1_11_1"
            getString(R.string.java_1_11_2) -> "JAVA_1_11_2"
            getString(R.string.java_1_12) -> "JAVA_1_12"
            getString(R.string.java_1_12_1) -> "JAVA_1_12_1"
            getString(R.string.java_1_12_2) -> "JAVA_1_12_2"
            getString(R.string.java_1_13) -> "JAVA_1_13"
            getString(R.string.java_1_13_1) -> "JAVA_1_13_1"
            getString(R.string.java_1_13_2) -> "JAVA_1_13_2"
            getString(R.string.java_1_14) -> "JAVA_1_14"
            getString(R.string.java_1_14_1) -> "JAVA_1_14_1"
            getString(R.string.java_1_14_2) -> "JAVA_1_14_2"
            getString(R.string.java_1_14_3) -> "JAVA_1_14_3"
            getString(R.string.java_1_14_4) -> "JAVA_1_14_4"
            getString(R.string.java_1_15) -> "JAVA_1_15"
            getString(R.string.java_1_15_1) -> "JAVA_1_15_1"
            getString(R.string.java_1_15_2) -> "JAVA_1_15_2"
            getString(R.string.java_1_16) -> "JAVA_1_16"
            getString(R.string.java_1_16_1) -> "JAVA_1_16_1"
            getString(R.string.java_1_16_2) -> "JAVA_1_16_2"
            getString(R.string.java_1_16_3) -> "JAVA_1_16_3"
            getString(R.string.java_1_16_4) -> "JAVA_1_16_4"
            getString(R.string.java_1_16_5) -> "JAVA_1_16_5"
            getString(R.string.java_1_17) -> "JAVA_1_17"
            getString(R.string.java_1_17_1) -> "JAVA_1_17_1"
            getString(R.string.java_1_18) -> "JAVA_1_18"
            getString(R.string.java_1_18_1) -> "JAVA_1_18_1"
            getString(R.string.java_1_18_2) -> "JAVA_1_18_2"
            getString(R.string.java_1_19) -> "JAVA_1_19"
            getString(R.string.java_1_19_1) -> "JAVA_1_19_1"
            getString(R.string.java_1_19_2) -> "JAVA_1_19_2"
            getString(R.string.java_1_19_3) -> "JAVA_1_19_3"
            getString(R.string.java_1_19_4) -> "JAVA_1_19_4"
            getString(R.string.java_1_20) -> "JAVA_1_20"
            getString(R.string.java_1_20_1) -> "JAVA_1_20_1"
            getString(R.string.java_1_20_2) -> "JAVA_1_20_2"
            getString(R.string.java_1_20_3) -> "JAVA_1_20_3"
            getString(R.string.java_1_20_4) -> "JAVA_1_20_4"
            getString(R.string.java_1_20_5) -> "JAVA_1_20_5"
            getString(R.string.java_1_20_6) -> "JAVA_1_20_6"
            getString(R.string.java_1_21) -> "JAVA_1_21"
            getString(R.string.java_1_21_1) -> "JAVA_1_21_1"
            getString(R.string.java_1_21_2) -> "JAVA_1_21_2"
            getString(R.string.java_1_21_3) -> "JAVA_1_21_3"
            getString(R.string.java_1_21_4) -> "JAVA_1_21_4"
            getString(R.string.java_1_21_5) -> "JAVA_1_21_5"
            getString(R.string.java_1_21_6) -> "JAVA_1_21_6"
            getString(R.string.java_1_21_7) -> "JAVA_1_21_7"
            getString(R.string.java_1_21_8) -> "JAVA_1_21_8"
            getString(R.string.java_1_21_9) -> "JAVA_1_21_9"
            getString(R.string.java_1_21_10) -> "JAVA_1_21_10"
            getString(R.string.java_1_21_11) -> "JAVA_1_21_11"
            getString(R.string.java_26_1) -> "JAVA_26_1"
            getString(R.string.java_26_2) -> "JAVA_26_2"
            else -> "JAVA_1_21_11"
        }
    }

    private fun mapBedrockVersion(versionNum: String): String {
        return when(versionNum) {
            getString(R.string.bedrock_1_12) -> "BEDROCK_R12"
            getString(R.string.bedrock_1_13) -> "BEDROCK_R13"
            getString(R.string.bedrock_1_14) -> "BEDROCK_R14"
            getString(R.string.bedrock_1_14_1) -> "BEDROCK_R14_1"
            getString(R.string.bedrock_1_14_20) -> "BEDROCK_R14_20"
            getString(R.string.bedrock_1_14_30) -> "BEDROCK_R14_30"
            getString(R.string.bedrock_1_14_60) -> "BEDROCK_R14_60"
            getString(R.string.bedrock_1_16) -> "BEDROCK_R16"
            getString(R.string.bedrock_1_16_20) -> "BEDROCK_R16_20"
            getString(R.string.bedrock_1_16_100) -> "BEDROCK_R16_100"
            getString(R.string.bedrock_1_16_200) -> "BEDROCK_R16_200"
            getString(R.string.bedrock_1_16_210) -> "BEDROCK_R16_210"
            getString(R.string.bedrock_1_16_220) -> "BEDROCK_R16_220"
            getString(R.string.bedrock_1_17) -> "BEDROCK_R17"
            getString(R.string.bedrock_1_17_10) -> "BEDROCK_R17_10"
            getString(R.string.bedrock_1_17_20) -> "BEDROCK_R17_20"
            getString(R.string.bedrock_1_17_30) -> "BEDROCK_R17_30"
            getString(R.string.bedrock_1_17_40) -> "BEDROCK_R17_40"
            getString(R.string.bedrock_1_18) -> "BEDROCK_R18"
            getString(R.string.bedrock_1_18_10) -> "BEDROCK_R18_10"
            getString(R.string.bedrock_1_18_20) -> "BEDROCK_R18_20"
            getString(R.string.bedrock_1_18_30) -> "BEDROCK_R18_30"
            getString(R.string.bedrock_1_19) -> "BEDROCK_R19"
            getString(R.string.bedrock_1_19_10) -> "BEDROCK_R19_10"
            getString(R.string.bedrock_1_19_20) -> "BEDROCK_R19_20"
            getString(R.string.bedrock_1_19_30) -> "BEDROCK_R19_30"
            getString(R.string.bedrock_1_19_40) -> "BEDROCK_R19_40"
            getString(R.string.bedrock_1_19_50) -> "BEDROCK_R19_50"
            getString(R.string.bedrock_1_19_60) -> "BEDROCK_R19_60"
            getString(R.string.bedrock_1_19_70) -> "BEDROCK_R19_70"
            getString(R.string.bedrock_1_19_80) -> "BEDROCK_R19_80"
            getString(R.string.bedrock_1_20) -> "BEDROCK_R20"
            getString(R.string.bedrock_1_20_10) -> "BEDROCK_R20_10"
            getString(R.string.bedrock_1_20_20) -> "BEDROCK_R20_20"
            getString(R.string.bedrock_1_20_30) -> "BEDROCK_R20_30"
            getString(R.string.bedrock_1_20_40) -> "BEDROCK_R20_40"
            getString(R.string.bedrock_1_20_50) -> "BEDROCK_R20_50"
            getString(R.string.bedrock_1_20_60) -> "BEDROCK_R20_60"
            getString(R.string.bedrock_1_20_70) -> "BEDROCK_R20_70"
            getString(R.string.bedrock_1_20_80) -> "BEDROCK_R20_80"
            getString(R.string.bedrock_1_21) -> "BEDROCK_R21"
            getString(R.string.bedrock_1_21_20) -> "BEDROCK_R21_20"
            getString(R.string.bedrock_1_21_30) -> "BEDROCK_R21_30"
            getString(R.string.bedrock_1_21_40) -> "BEDROCK_R21_40"
            getString(R.string.bedrock_1_21_50) -> "BEDROCK_R21_50"
            getString(R.string.bedrock_1_21_60) -> "BEDROCK_R21_60"
            getString(R.string.bedrock_1_21_70) -> "BEDROCK_R21_70"
            getString(R.string.bedrock_1_21_80) -> "BEDROCK_R21_80"
            getString(R.string.bedrock_1_21_90) -> "BEDROCK_R21_90"
            getString(R.string.bedrock_1_21_100) -> "BEDROCK_R21_100"
            getString(R.string.bedrock_1_21_110) -> "BEDROCK_R21_110"
            getString(R.string.bedrock_1_21_120) -> "BEDROCK_R21_120"
            getString(R.string.bedrock_1_21_130) -> "BEDROCK_R21_130"
            getString(R.string.bedrock_1_26) -> "BEDROCK_R26"
            getString(R.string.bedrock_1_26_10) -> "BEDROCK_R26_10"
            getString(R.string.bedrock_1_26_20) -> "BEDROCK_R26_20"
            getString(R.string.bedrock_1_26_30) -> "BEDROCK_R26_30"
            else -> "BEDROCK_R21_130"
        }
    }

    private var activeItemId = R.id.nav_item_converter
    private val inactiveScale = 0.97f
    private val inactiveTextAlpha = 0f
    private val animDuration = 150L

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            inputUri = it
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                android.util.Log.w("MainActivity", "Failed to take persistable URI permission", e)
            }
            isArchiveMode = false
            processSelectedWorld(it)
        }
    }

    private val archivePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            inputUri = it
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                android.util.Log.w("MainActivity", "Failed to take persistable URI permission", e)
            }
            isArchiveMode = true
            processSelectedWorld(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        drawerLayout = binding.drawerLayout
        applyNoElevationToCards()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { _, insets ->
            val statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            val navBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom

            binding.scrollWorkbench.setPadding(
                binding.scrollWorkbench.paddingLeft,
                statusBarHeight + (16 * resources.displayMetrics.density).toInt(),
                binding.scrollWorkbench.paddingRight,
                (160 * resources.displayMetrics.density).toInt()
            )

            binding.fragmentContainer.setPadding(
                binding.fragmentContainer.paddingLeft,
                statusBarHeight,
                binding.fragmentContainer.paddingRight,
                0
            )

            binding.bottomNavigation.setPadding(
                binding.bottomNavigation.paddingLeft,
                0,
                binding.bottomNavigation.paddingRight,
                navBarHeight
            )

            insets
        }

        setupDrawer()
        setupClickListeners()
        setupBottomNavigation()
        checkPermissions()
        updateNavDrawerVisibility()
        updateDockBarState()

        archiveSearcher = ArchiveSearcher(contentResolver, filesDir)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateNavDrawerVisibility()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            BackgroundUtils.applyCustomBackground(this)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_OUTPUT_MANAGER, false) == true) {
            switchToOutputManager(false)
        }
        if (intent?.getBooleanExtra(EXTRA_RESET_TO_INITIAL_STATE, false) == true) {
            switchToInitialState()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_item_converter -> {
                    switchToConverter()
                    true
                }
                R.id.nav_item_output -> {
                    switchToOutputManager(true)
                    true
                }
                else -> false
            }
        }
        switchToConverter()
    }

    private var outputManagerFragment: OutputManagerFragment? = null

    private fun switchToConverter() {
        activeItemId = R.id.nav_item_converter
        binding.scrollWorkbench.visibility = View.VISIBLE
        binding.fragmentContainer.visibility = View.GONE
        outputManagerFragment?.let {
            supportFragmentManager.beginTransaction().hide(it).commitAllowingStateLoss()
        }
    }

    private fun switchToOutputManager(animate: Boolean) {
        activeItemId = R.id.nav_item_output
        binding.scrollWorkbench.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE

        val fm = supportFragmentManager
        if (outputManagerFragment == null) {
            val existing = fm.findFragmentById(R.id.fragment_container) as? OutputManagerFragment
            if (existing != null) {
                outputManagerFragment = existing
                fm.beginTransaction().show(existing).commitAllowingStateLoss()
            } else {
                val newFrag = OutputManagerFragment()
                outputManagerFragment = newFrag
                fm.beginTransaction()
                    .add(R.id.fragment_container, newFrag)
                    .commitAllowingStateLoss()
            }
        } else {
            fm.beginTransaction().show(outputManagerFragment!!).commitAllowingStateLoss()
        }
    }

    private fun showCustomToast(message: String, isError: Boolean = false) {
        ToastUtils.show(this, message, isError)
    }

    private fun setupDrawer() {
        val navDrawer = binding.navDrawer
        val navDrawerView = navDrawer.root

        val navItemSettings = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_settings)
        val navItemTerminal = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_terminal)

        val navItemResourceConverter = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_resource_converter)
        val navItemDecrypt = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_decrypt)
        val navItemDimensionPruning = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_dimension_pruning)
        val navItemNbtEditor = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_nbt_editor)
        val navItemAbout = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_about)
        val navItemHelp = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_help)

        navItemSettings.setOnClickListener {
            drawerLayout.closeDrawer(navDrawerView, false)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        navItemTerminal.setOnClickListener {
            drawerLayout.closeDrawer(navDrawerView, false)
            startActivity(Intent(this, TerminalActivity::class.java))
        }

        navItemResourceConverter.setOnClickListener {
            drawerLayout.closeDrawer(navDrawerView, false)
            startActivity(Intent(this, PackConverterActivity::class.java))
        }

        navItemDecrypt.setOnClickListener {
            drawerLayout.closeDrawer(navDrawerView, false)
            startActivity(Intent(this, DecryptActivity::class.java))
        }

        navItemDimensionPruning.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Experimentální funkce")
                .setMessage("Chystáte se otevřít nástroj pro ořez dimenzí a chunků.\n\nTento nástroj přímo upravuje data MCA/LevelDB. Nesprávným použitím můžete poškodit nebo ztratit chunky světa. Před použitím doporučujeme svět zálohovat!")
                .setPositiveButton("Rozumím") { _, _ ->
                    drawerLayout.closeDrawer(navDrawerView, false)
                    val intent = Intent(this, DimensionSettingsActivity::class.java)
                    startActivity(intent)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        navItemNbtEditor.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Experimentální funkce")
                .setMessage("Chystáte se otevřít editor NBT a nízkoúrovňových dat.\n\nTento nástroj může přímo číst a zapisovat základní datové struktury. Začátečníkům doporučujeme zvýšenou opatrnost; před použitím si svět zálohujte!")
                .setPositiveButton("Rozumím") { _, _ ->
                    drawerLayout.closeDrawer(navDrawerView, false)
                    val intent = Intent(this, NbtEditorActivity::class.java)
                    startActivity(intent)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        navItemHelp.setOnClickListener {
            drawerLayout.closeDrawer(navDrawerView, false)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chunkoid.top/docs/index.html"))
            startActivity(intent)
        }

        navItemAbout.setOnClickListener {
            drawerLayout.closeDrawer(navDrawerView, false)
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    fun updateNavDrawerVisibility() {
        val navDrawerView = binding.navDrawer.root
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val navItemTerminal = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_terminal)

        val navItemResourceConverter = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_resource_converter)
        val navItemDecrypt = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_decrypt)

        val showTerminal = prefs.getBoolean("show_terminal", true)
        val showUndeveloped = prefs.getBoolean("show_undeveloped", false)

        navItemTerminal.visibility = if (showTerminal) View.VISIBLE else View.GONE

        navItemResourceConverter.visibility = View.VISIBLE
        navItemDecrypt.visibility = View.VISIBLE
    }

    private fun applyNoElevationToCards() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        removeCardElevation(rootView)
    }

    private fun removeCardElevation(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is com.google.android.material.card.MaterialCardView) {
                child.cardElevation = 0f
            } else if (child is ViewGroup) {
                removeCardElevation(child)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnMenu.setOnClickListener {
            drawerLayout.openDrawer(binding.navDrawer.root)
        }

        binding.cardHeroImport.setOnClickListener {
            if (binding.cardInitialState.visibility == View.VISIBLE) {
                showImportTypeBottomSheet()
            }
        }

        binding.btnClearWorld.setOnClickListener {
            clearOutput()
            switchToInitialState()
        }

        binding.btnSelectTarget.setOnClickListener {
            showVersionPickerDialog()
        }

        binding.btnDockAction.setOnClickListener {
            if (inputUri == null) {
                showImportTypeBottomSheet()
            } else if (selectedFormat.isEmpty()) {
                showCustomToast("Vyberte cílový formát a verzi", isError = true)
                showVersionPickerDialog()
            } else {
                startConversion()
            }
        }
    }

    private fun showImportTypeBottomSheet() {
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_import_type, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(bottomSheetView)

        bottomSheetView.findViewById<View>(R.id.btn_import_archive_option)?.setOnClickListener {
            isArchiveMode = true
            dialog.dismiss()
            archivePickerLauncher.launch(arrayOf("*/*"))
        }

        bottomSheetView.findViewById<View>(R.id.btn_import_folder_option)?.setOnClickListener {
            isArchiveMode = false
            dialog.dismiss()
            folderPickerLauncher.launch(null)
        }

        dialog.show()
    }

    fun switchToConverterTab() {
        binding.bottomNavigation.selectedItemId = R.id.nav_item_converter
    }

    private fun updateDockBarState() {
        if (inputUri == null) {
            binding.btnDockAction.text = "Importovat svět"
        } else {
            binding.btnDockAction.text = if (selectedFormatDisplayName.isNotEmpty()) "Spustit převod" else "Vybrat cílovou verzi"
        }
    }

    private fun showVersionPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_version_picker, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)

        var selectedVersion: String? = null

        val bedrockVersionGroups = listOf(
            VersionGroup("1.12", getString(R.string.bedrock_subtitle_1_12), listOf(getString(R.string.bedrock_1_12))),
            VersionGroup("1.13", getString(R.string.bedrock_subtitle_1_13), listOf(getString(R.string.bedrock_1_13))),
            VersionGroup("1.14", getString(R.string.bedrock_subtitle_1_14), listOf(getString(R.string.bedrock_1_14), getString(R.string.bedrock_1_14_1), getString(R.string.bedrock_1_14_20), getString(R.string.bedrock_1_14_30), getString(R.string.bedrock_1_14_60))),
            VersionGroup("1.16", getString(R.string.bedrock_subtitle_1_16), listOf(getString(R.string.bedrock_1_16), getString(R.string.bedrock_1_16_20), getString(R.string.bedrock_1_16_100), getString(R.string.bedrock_1_16_200), getString(R.string.bedrock_1_16_210), getString(R.string.bedrock_1_16_220))),
            VersionGroup("1.17", getString(R.string.bedrock_subtitle_1_17), listOf(getString(R.string.bedrock_1_17), getString(R.string.bedrock_1_17_10), getString(R.string.bedrock_1_17_20), getString(R.string.bedrock_1_17_30), getString(R.string.bedrock_1_17_40))),
            VersionGroup("1.18", getString(R.string.bedrock_subtitle_1_18), listOf(getString(R.string.bedrock_1_18), getString(R.string.bedrock_1_18_10), getString(R.string.bedrock_1_18_20), getString(R.string.bedrock_1_18_30))),
            VersionGroup("1.19", getString(R.string.bedrock_subtitle_1_19), listOf(getString(R.string.bedrock_1_19), getString(R.string.bedrock_1_19_10), getString(R.string.bedrock_1_19_20), getString(R.string.bedrock_1_19_30), getString(R.string.bedrock_1_19_40), getString(R.string.bedrock_1_19_50), getString(R.string.bedrock_1_19_60), getString(R.string.bedrock_1_19_70), getString(R.string.bedrock_1_19_80))),
            VersionGroup("1.20", getString(R.string.bedrock_subtitle_1_20), listOf(getString(R.string.bedrock_1_20), getString(R.string.bedrock_1_20_10), getString(R.string.bedrock_1_20_20), getString(R.string.bedrock_1_20_30), getString(R.string.bedrock_1_20_40), getString(R.string.bedrock_1_20_50), getString(R.string.bedrock_1_20_60), getString(R.string.bedrock_1_20_70), getString(R.string.bedrock_1_20_80))),
            VersionGroup("1.21", getString(R.string.bedrock_subtitle_1_21), listOf(getString(R.string.bedrock_1_21), getString(R.string.bedrock_1_21_20), getString(R.string.bedrock_1_21_30), getString(R.string.bedrock_1_21_40), getString(R.string.bedrock_1_21_50), getString(R.string.bedrock_1_21_60), getString(R.string.bedrock_1_21_70), getString(R.string.bedrock_1_21_80), getString(R.string.bedrock_1_21_90), getString(R.string.bedrock_1_21_100), getString(R.string.bedrock_1_21_110), getString(R.string.bedrock_1_21_120), getString(R.string.bedrock_1_21_130))),
            VersionGroup("1.26", getString(R.string.bedrock_subtitle_1_26), listOf(getString(R.string.bedrock_1_26), getString(R.string.bedrock_1_26_10), getString(R.string.bedrock_1_26_20), getString(R.string.bedrock_1_26_30)))
        )

        val javaVersionGroups = listOf(
            VersionGroup("1.8.8", getString(R.string.java_subtitle_1_8), listOf(getString(R.string.java_1_8_8))),
            VersionGroup("1.9", getString(R.string.java_subtitle_1_9), listOf(getString(R.string.java_1_9), getString(R.string.java_1_9_1), getString(R.string.java_1_9_2), getString(R.string.java_1_9_3))),
            VersionGroup("1.10", getString(R.string.java_subtitle_1_10), listOf(getString(R.string.java_1_10), getString(R.string.java_1_10_1), getString(R.string.java_1_10_2))),
            VersionGroup("1.11", getString(R.string.java_subtitle_1_11), listOf(getString(R.string.java_1_11), getString(R.string.java_1_11_1), getString(R.string.java_1_11_2))),
            VersionGroup("1.12", getString(R.string.java_subtitle_1_12), listOf(getString(R.string.java_1_12), getString(R.string.java_1_12_1), getString(R.string.java_1_12_2))),
            VersionGroup("1.13", getString(R.string.java_subtitle_1_13), listOf(getString(R.string.java_1_13), getString(R.string.java_1_13_1), getString(R.string.java_1_13_2))),
            VersionGroup("1.14", getString(R.string.java_subtitle_1_14), listOf(getString(R.string.java_1_14), getString(R.string.java_1_14_1), getString(R.string.java_1_14_2), getString(R.string.java_1_14_3), getString(R.string.java_1_14_4))),
            VersionGroup("1.15", getString(R.string.java_subtitle_1_15), listOf(getString(R.string.java_1_15), getString(R.string.java_1_15_1), getString(R.string.java_1_15_2))),
            VersionGroup("1.16", getString(R.string.java_subtitle_1_16), listOf(getString(R.string.java_1_16), getString(R.string.java_1_16_1), getString(R.string.java_1_16_2), getString(R.string.java_1_16_3), getString(R.string.java_1_16_4), getString(R.string.java_1_16_5))),
            VersionGroup("1.17", getString(R.string.java_subtitle_1_17), listOf(getString(R.string.java_1_17), getString(R.string.java_1_17_1))),
            VersionGroup("1.18", getString(R.string.java_subtitle_1_18), listOf(getString(R.string.java_1_18), getString(R.string.java_1_18_1), getString(R.string.java_1_18_2))),
            VersionGroup("1.19", getString(R.string.java_subtitle_1_19), listOf(getString(R.string.java_1_19), getString(R.string.java_1_19_1), getString(R.string.java_1_19_2), getString(R.string.java_1_19_3), getString(R.string.java_1_19_4))),
            VersionGroup("1.20", getString(R.string.java_subtitle_1_20), listOf(getString(R.string.java_1_20), getString(R.string.java_1_20_1), getString(R.string.java_1_20_2), getString(R.string.java_1_20_3), getString(R.string.java_1_20_4), getString(R.string.java_1_20_5), getString(R.string.java_1_20_6))),
            VersionGroup("1.21", getString(R.string.java_subtitle_1_21), listOf(getString(R.string.java_1_21), getString(R.string.java_1_21_1), getString(R.string.java_1_21_2), getString(R.string.java_1_21_3), getString(R.string.java_1_21_4), getString(R.string.java_1_21_5), getString(R.string.java_1_21_6), getString(R.string.java_1_21_7), getString(R.string.java_1_21_8), getString(R.string.java_1_21_9), getString(R.string.java_1_21_10), getString(R.string.java_1_21_11))),
            VersionGroup("26.x", getString(R.string.java_subtitle_26), listOf(getString(R.string.java_26_1), getString(R.string.java_26_2)))
        )

        val bedrockCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_bedrock)
        val bedrockArrow = dialogView.findViewById<ImageView>(R.id.iv_bedrock_arrow)
        val bedrockVersionsLayout = dialogView.findViewById<LinearLayout>(R.id.ll_bedrock_versions)
        var bedrockExpanded = false

        bedrockCard.setOnClickListener {
            bedrockExpanded = !bedrockExpanded
            bedrockVersionsLayout.visibility = if (bedrockExpanded) View.VISIBLE else View.GONE
            bedrockArrow.rotation = if (bedrockExpanded) 180f else 0f
        }

        val javaCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_java)
        val javaArrow = dialogView.findViewById<ImageView>(R.id.iv_java_arrow)
        val javaVersionsLayout = dialogView.findViewById<LinearLayout>(R.id.ll_java_versions)
        var javaExpanded = false

        javaCard.setOnClickListener {
            javaExpanded = !javaExpanded
            javaVersionsLayout.visibility = if (javaExpanded) View.VISIBLE else View.GONE
            javaArrow.rotation = if (javaExpanded) 180f else 0f
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm).setOnClickListener {
            if (selectedVersion.isNullOrEmpty()) {
                showCustomToast(getString(R.string.error_no_format), isError = true)
            } else {
                dialog.dismiss()
                updateTargetVersionDisplay(selectedVersion!!)
            }
        }

        fun updateConfirmButton() {
            val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm)
            if (selectedVersion.isNullOrEmpty()) {
                btnConfirm.text = getString(R.string.ok)
            } else {
                val platform = if (selectedVersion!!.startsWith("Java")) "Java Edition" else "Bedrock Edition"
                val versionNum = selectedVersion!!.replace("Java ", "").replace("Bedrock ", "")
                btnConfirm.text = "Potvrdit (${platform} ${versionNum})"
            }
        }

        populateVersionGroups(bedrockVersionsLayout, bedrockVersionGroups) { version ->
            selectedVersion = "Bedrock $version"
            updateConfirmButton()
        }

        populateVersionGroups(javaVersionsLayout, javaVersionGroups) { version ->
            selectedVersion = "Java $version"
            updateConfirmButton()
        }

        dialog.show()
    }

    private data class VersionGroup(val name: String, val subtitle: String, val versions: List<String>)

    private fun populateVersionGroups(container: LinearLayout, groups: List<VersionGroup>, onVersionSelect: (String) -> Unit) {
        var lastSelectedButton: androidx.appcompat.widget.AppCompatButton? = null

        for (group in groups) {
            val groupView = layoutInflater.inflate(R.layout.item_version_group, container, false)
            val groupHeader = groupView.findViewById<LinearLayout>(R.id.ll_group_header)
            val groupTitle = groupView.findViewById<TextView>(R.id.tv_group_title)
            val groupSubtitle = groupView.findViewById<TextView>(R.id.tv_group_subtitle)
            val groupArrow = groupView.findViewById<ImageView>(R.id.iv_group_arrow)
            val versionsLayout = groupView.findViewById<LinearLayout>(R.id.ll_versions)
            val flexboxLayout = groupView.findViewById<LinearLayout>(R.id.flexbox_versions)

            groupTitle.text = group.name
            groupSubtitle.text = group.subtitle

            var groupExpanded = false
            groupHeader.setOnClickListener {
                groupExpanded = !groupExpanded
                versionsLayout.visibility = if (groupExpanded) View.VISIBLE else View.GONE
                groupArrow.rotation = if (groupExpanded) 180f else 0f
            }

            var currentRow: LinearLayout? = null
            var buttonsInRow = 0
            val maxButtonsPerRow = 3

            for (version in group.versions) {
                if (currentRow == null || buttonsInRow >= maxButtonsPerRow) {
                    currentRow = LinearLayout(this)
                    currentRow.orientation = LinearLayout.HORIZONTAL
                    currentRow.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    flexboxLayout.addView(currentRow)
                    buttonsInRow = 0
                }

                val button = androidx.appcompat.widget.AppCompatButton(this)
                button.text = version.replace("Bedrock ", "").replace("Java ", "")
                button.setTextColor(ContextCompat.getColor(this, R.color.white))
                button.setBackgroundResource(R.drawable.version_button_bg)
                button.setPadding(12, 8, 12, 8)
                button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.weight = 1f
                params.setMargins(4, 4, 4, 4)
                button.layoutParams = params

                button.setOnClickListener {
                    lastSelectedButton?.setBackgroundResource(R.drawable.version_button_bg)
                    button.setBackgroundResource(R.drawable.version_button_selected)
                    lastSelectedButton = button
                    onVersionSelect(version)
                }

                currentRow.addView(button)
                buttonsInRow++
            }

            container.addView(groupView)
        }
    }

    private fun processSelectedWorld(uri: Uri) {
        if (isArchiveMode) {
            binding.cardInitialState.visibility = View.GONE
            binding.layoutExtractProgress.visibility = View.VISIBLE
            binding.cardWorldInfo.visibility = View.GONE

            archiveSearcher.searchAndExtractArchive(uri, object : ArchiveSearcher.SearchCallback {
                override fun onProgress(progress: Int, message: String) {
                    runOnUiThread {
                        binding.progressExtract.progress = progress
                        binding.tvExtractProgress.text = message
                    }
                }

                override fun onSuccess(worldDir: File) {
                    runOnUiThread {
                        binding.layoutExtractProgress.visibility = View.GONE

                        if (!validateWorld()) {
                            clearOutput()
                            switchToInitialState()
                            showCustomToast("Načtení světa se nezdařilo! Nebyla nalezena platná data.", isError = true)
                            return@runOnUiThread
                        }

                        worldPlatform = if (archiveSearcher.isMcWorldFile(uri)) {
                            Platform.BEDROCK
                        } else {
                            archiveSearcher.detectPlatformFromDirectory()
                        }

                        readWorldNameFromArchive(uri)
                        val iconBitmap = loadWorldIconFromArchive(uri)
                        if (iconBitmap != null) {
                            binding.ivWorldIcon.setImageBitmap(iconBitmap)
                        } else {
                            binding.ivWorldIcon.setImageResource(R.drawable.unknown)
                        }

                        updateWorldInfoDisplay()
                        switchToWorldInfoState()
                    }
                }

                override fun onError(errorMessage: String) {
                    runOnUiThread {
                        binding.layoutExtractProgress.visibility = View.GONE
                        showCustomToast(errorMessage, isError = true)
                        switchToInitialState()
                    }
                }
            })
        } else {
            readWorldNameFromDirectory(uri)
            detectPlatformFromDirectory(uri)
            loadWorldIconFromDirectory(uri)

            if (!validateWorld()) {
                clearOutput()
                switchToInitialState()
                showCustomToast("Načtení světa se nezdařilo!", isError = true)
                return
            }

            updateWorldInfoDisplay()
            switchToWorldInfoState()
        }
    }

    private fun validateWorld(): Boolean {
        if (isArchiveMode) {
            val inputDir = File(filesDir, "input")
            val dbDir = File(inputDir, "db")
            val regionDir = File(inputDir, "region")
            val dimensionsDir = File(inputDir, "dimensions")
            return (dbDir.exists() && dbDir.isDirectory) ||
                   (regionDir.exists() && regionDir.isDirectory) ||
                   (dimensionsDir.exists() && dimensionsDir.isDirectory)
        } else {
            if (inputUri == null) return false
            try {
                val documentFile = DocumentFile.fromTreeUri(this, inputUri!!)
                if (documentFile?.isDirectory != true) return false

                val dbDir = documentFile.findFile("db")
                val regionDir = documentFile.findFile("region")
                val dimensionsDir = documentFile.findFile("dimensions")
                return (dbDir != null && dbDir.isDirectory) ||
                       (regionDir != null && regionDir.isDirectory) ||
                       (dimensionsDir != null && dimensionsDir.isDirectory)
            } catch (e: Exception) {
                return false
            }
        }
    }

    private fun clearOutput() {
        val inputDir = File(filesDir, "input")
        if (inputDir.exists()) {
            deleteDir(inputDir)
        }
    }

    private fun deleteDir(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { deleteDir(it) }
        }
        dir.delete()
    }

    private fun switchToInitialState() {
        inputUri = null
        inputWorldName = ""
        selectedFormat = ""
        selectedFormatDisplayName = ""
        isWorldInfoVisible = false
        binding.cardInitialState.visibility = View.VISIBLE
        binding.layoutExtractProgress.visibility = View.GONE
        binding.cardWorldInfo.visibility = View.GONE
        binding.tvTargetVersion.text = "Klepnutím vyberte cílovou platformu a verzi"
        updateDockBarState()
    }

    private fun detectPlatformFromDirectory(uri: Uri) {
        try {
            val documentFile = DocumentFile.fromTreeUri(this, uri)
            documentFile?.let { dir ->
                val regionFolder = dir.findFile("region")
                val dimensionsFolder = dir.findFile("dimensions")
                if ((regionFolder != null && regionFolder.isDirectory) ||
                    (dimensionsFolder != null && dimensionsFolder.isDirectory)) {
                    worldPlatform = Platform.JAVA
                    return
                }

                worldPlatform = Platform.BEDROCK
            }
        } catch (e: Exception) {
            worldPlatform = Platform.BEDROCK
        }
    }

    private fun loadWorldIconFromDirectory(uri: Uri) {
        try {
            val documentFile = DocumentFile.fromTreeUri(this, uri)
            documentFile?.let { dir ->
                var iconFile = dir.findFile("icon.png")
                if (iconFile != null && iconFile.isFile) {
                    loadIconFromUri(iconFile.uri)
                    return
                }

                iconFile = dir.findFile("world_icon.jpeg")
                if (iconFile != null && iconFile.isFile) {
                    loadIconFromUri(iconFile.uri)
                    return
                }

                iconFile = dir.findFile("world_icon.jpg")
                if (iconFile != null && iconFile.isFile) {
                    loadIconFromUri(iconFile.uri)
                    return
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadWorldIconFromArchive(uri: Uri): Bitmap? {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry

                while (entry != null) {
                    val entryName = entry.name.lowercase()

                    if (entryName.contains("icon.png") ||
                        entryName.contains("world_icon.jpeg") ||
                        entryName.contains("world_icon.jpg")) {
                        val bitmap = BitmapFactory.decodeStream(zipInputStream)
                        zipInputStream.closeEntry()
                        zipInputStream.close()
                        return bitmap
                    }

                    entry = zipInputStream.nextEntry
                }

                zipInputStream.closeEntry()
                zipInputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun loadIconFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    binding.ivWorldIcon.setImageBitmap(bitmap)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun readWorldNameFromDirectory(uri: Uri?) {
        if (uri == null) {
            inputWorldName = ""
            return
        }

        try {
            val documentFile = DocumentFile.fromTreeUri(this, uri)
            if (documentFile?.isDirectory == true) {
                val levelNameFile = documentFile.findFile("levelname.txt")
                if (levelNameFile != null && levelNameFile.isFile) {
                    contentResolver.openInputStream(levelNameFile.uri)?.use { inputStream ->
                        val content = inputStream.bufferedReader().use { it.readText() }
                        inputWorldName = content.trim()
                        return
                    }
                }

                val folderName = documentFile.name ?: "Unknown World"
                inputWorldName = folderName
            }
        } catch (e: Exception) {
            inputWorldName = ""
        }
    }

    private fun readWorldNameFromArchive(uri: Uri) {
        try {
            val inputDir = File(filesDir, "input")
            val levelNameFile = File(inputDir, "levelname.txt")
            if (levelNameFile.exists() && levelNameFile.isFile) {
                val content = levelNameFile.readText().trim()
                if (content.isNotEmpty()) {
                    inputWorldName = content
                    return
                }
            }

            val fileName = getFileNameFromUri(uri) ?: "World"
            inputWorldName = fileName
        } catch (e: Exception) {
            e.printStackTrace()
            inputWorldName = getFileNameFromUri(uri) ?: "World"
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null

        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
        }

        if (fileName == null) {
            fileName = uri.path?.substringAfterLast('/')
        }

        return fileName?.substringBeforeLast('.')
    }

    private fun updateWorldInfoDisplay() {
        binding.tvWorldName.text = inputWorldName

        if (worldPlatform == Platform.JAVA) {
            binding.tvPlatform.text = getString(R.string.java_edition)
        } else {
            binding.tvPlatform.text = getString(R.string.bedrock_edition)
        }

        loadWorldVersion()
        updateDockBarState()
    }

    private fun loadWorldVersion() {
        binding.cardPlatform.visibility = View.GONE

        Thread {
            val versionInfo = if (isArchiveMode) {
                NbtVersionReader.readLevelDat(File(filesDir, "input"))
            } else {
                inputUri?.let { uri ->
                    try {
                        DocumentFile.fromTreeUri(this, uri)?.findFile("level.dat")?.uri?.let { levelDatUri ->
                            contentResolver.openInputStream(levelDatUri)?.use { inputStream ->
                                NbtVersionReader.readLevelDatFromStream(inputStream)
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            runOnUiThread {
                versionInfo?.let { info ->
                    binding.tvVersion.text = NbtVersionReader.getVersionDisplay(info)

                    if (!archiveSearcher.isMcWorldFile(inputUri ?: Uri.EMPTY)) {
                        worldPlatform = info.platform
                        binding.tvPlatform.text = if (worldPlatform == Platform.JAVA) {
                            getString(R.string.java_edition)
                        } else {
                            getString(R.string.bedrock_edition)
                        }
                    }
                    binding.cardPlatform.visibility = View.VISIBLE
                    updateDockBarState()
                }
            }
        }.start()
    }

    private fun updateTargetVersionDisplay(versionDisplayName: String) {
        selectedFormatDisplayName = versionDisplayName
        selectedFormat = mapVersionToChunkerFormat(versionDisplayName)
        val platform = if (versionDisplayName.startsWith("Java")) getString(R.string.java_edition) else getString(R.string.bedrock_edition)
        val versionNum = versionDisplayName.replace("Java ", "").replace("Bedrock ", "")
        binding.tvTargetVersion.text = "Převést na ${platform} ${versionNum}"
        updateDockBarState()
    }

    private fun switchToWorldInfoState() {
        binding.cardInitialState.visibility = View.GONE
        binding.layoutExtractProgress.visibility = View.GONE
        binding.cardWorldInfo.visibility = View.VISIBLE
        isWorldInfoVisible = true
        showCustomToast("Svět byl úspěšně načten!")
    }

    private fun startConversion() {
        if (!validateInputs()) return

        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("hide_entity_warning", false)) {
            val view = layoutInflater.inflate(R.layout.dialog_entity_warning, null)
            val checkBox = view.findViewById<android.widget.CheckBox>(R.id.cb_dont_show_again)
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Upozornění: může dojít ke ztrátě entit a struktur")
                .setView(view)
                .setPositiveButton("Pokračovat v převodu") { _, _ ->
                    if (checkBox.isChecked) {
                        prefs.edit().putBoolean("hide_entity_warning", true).apply()
                    }
                    proceedWithConversion()
                }
                .setNegativeButton("Zrušit", null)
                .show()
        } else {
            proceedWithConversion()
        }
    }

    private fun proceedWithConversion() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val maxMemory = prefs.getInt("max_memory", 4096)
        val keepOriginalNbt = prefs.getBoolean("keep_original_nbt", false)
        val lowMemoryMode = prefs.getBoolean("low_memory_mode", false)

        val keepNether = prefs.getBoolean("keep_nether", true)
        val keepEnd = prefs.getBoolean("keep_end", true)

        val pruningJson = if (!keepNether || !keepEnd) {
            val netherStr = if (!keepNether) "\"NETHER\": {\"include\": false}" else ""
            val endStr = if (!keepEnd) "\"THE_END\": {\"include\": false}" else ""
            val parts = listOf(netherStr, endStr).filter { it.isNotEmpty() }
            "{${parts.joinToString(", ")}}"
        } else {
            null
        }

        val settings = ConversionSettings(
            inputUri = inputUri!!,
            inputWorldName = inputWorldName,
            targetFormat = selectedFormat,
            sourcePlatform = worldPlatform.name,
            maxMemory = maxMemory,
            keepOriginalNbt = keepOriginalNbt,
            lowMemoryMode = lowMemoryMode,
            pruningSettings = pruningJson
        )

        val intent = Intent(this, ConversionActivity::class.java).apply {
            putExtra(ConversionActivity.EXTRA_SETTINGS, settings)
        }
        startActivity(intent)
    }

    private fun validateInputs(): Boolean {
        var valid = true

        if (inputUri == null) {
            showCustomToast(getString(R.string.error_no_input), isError = true)
            valid = false
        }

        if (inputWorldName.isEmpty()) {
            showCustomToast("Název světa se nepodařilo zjistit", isError = true)
            valid = false
        }

        return valid
    }

    private fun checkPermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (!Environment.isExternalStorageManager()) {
                    requestManageStoragePermission()
                }
            }
            else -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ),
                        PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

    private fun requestManageStoragePermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_required_title)
            .setMessage(R.string.storage_permission_required_message)
            .setPositiveButton(R.string.grant) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showCustomToast(getString(R.string.permission_required), isError = true)
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        const val EXTRA_SHOW_OUTPUT_MANAGER = "extra_show_output_manager"
        const val EXTRA_RESET_TO_INITIAL_STATE = "extra_reset_to_initial_state"
        private const val PREFS_NAME = "chunkoid_prefs"
    }
}
