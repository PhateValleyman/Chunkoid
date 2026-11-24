package com.encore.chunkoid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.encore.chunkoid.R
import com.encore.chunkoid.core.runtime.RootFSInstaller
import com.encore.chunkoid.core.runtime.TermuxExecutor
import com.encore.chunkoid.ui.activity.ConversionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.concurrent.Executors

class ConversionService : Service() {
    private val binder = LocalBinder()
    val progress = MutableLiveData<Int>(0)
    val status = MutableLiveData<String>("")
    val logMessage = MutableLiveData<com.encore.chunkoid.model.LogEntry?>()
    val isRunning = MutableLiveData<Boolean>(false)
    val rawLogOutput = MutableLiveData<String>("")
    val conversionSuccess = MutableLiveData<Boolean?>(null)

    private lateinit var termuxExecutor: TermuxExecutor
    private var conversionProcess: Process? = null
    private val rawLogBuilder = StringBuilder()
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        fun getService(): ConversionService = this@ConversionService
    }

    override fun onCreate() {
        super.onCreate()
        termuxExecutor = TermuxExecutor(this)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Převod světa",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zobrazuje průběh převodu světa Minecraft a informaci o běhu na pozadí"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String, progressPercent: Int, isIndeterminate: Boolean): Notification {
        val intent = Intent(this, ConversionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Převod světa Minecraft")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent, isIndeterminate)
            .build()
    }

    private fun updateNotification(statusText: String, progressPercent: Int, isIndeterminate: Boolean = false) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, buildNotification(statusText, progressPercent, isIndeterminate))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification", e)
        }
    }

    private fun stopForegroundService() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop foreground", e)
        }
    }

    fun stopConversion() {
        conversionProcess?.destroy()
        conversionProcess = null
        isRunning.value = false
        releaseWakeLock()
        conversionSuccess.value = false
        status.value = "Převod byl zastaven"
        progress.value = 0
        stopForegroundService()
        cleanInputFolder()
        cleanOutputFolder()
    }

    fun cleanInputFolder() {
        val inputDir = File(filesDir, "input")
        if (inputDir.exists()) {
            deleteDir(inputDir)
            Log.d(TAG, "Input folder cleaned successfully")
        }
    }

    fun cleanOutputFolder() {
        val outputDir = File(filesDir, "output")
        if (outputDir.exists()) {
            deleteDir(outputDir)
            Log.d(TAG, "Output folder cleaned successfully")
        }
    }

    fun getOutputDirectory(): File {
        return File(filesDir, "output")
    }

    fun startConversionFromUri(inputUri: android.net.Uri, settings: com.encore.chunkoid.model.ConversionSettings) {
        isRunning.value = true
        acquireWakeLock()
        conversionSuccess.value = null
        progress.value = 0
        rawLogBuilder.clear()

        createNotificationChannel()
        val initialNotification = buildNotification("Inicializuji prostředí...", 0, true)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground", e)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                status.postValue("Inicializuji prostředí...")
                updateNotification("Inicializuji prostředí...", 5, true)
                initializeEnvironment()

                status.postValue("Připravuji vstupní soubory...")
                progress.postValue(10)
                updateNotification("Připravuji vstupní soubory...", 10, false)
                val inputDir = copyUriToInternal(inputUri, "input")

                progress.postValue(15)
                updateNotification("Připravuji výstupní složku...", 15, false)
                val outputDir = File(filesDir, "output")
                if (outputDir.exists()) {
                    deleteDir(outputDir)
                }
                outputDir.mkdirs()

                status.postValue("Probíhá převod...")
                progress.postValue(20)
                updateNotification("Převádím svět...", 20, false)
                runChunkerConversion(inputDir, outputDir, settings)

                status.postValue("Uspořádávám výstupní soubory...")
                progress.postValue(90)
                updateNotification("Uspořádávám výstupní soubory...", 90, false)
                moveOutputToDocuments(outputDir, settings.inputWorldName)

                withContext(Dispatchers.Main) {
                    onConversionComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Conversion failed", e)
                withContext(Dispatchers.Main) {
                    onConversionError(e)
                }
            }
        }
    }

    private fun initializeEnvironment() {
        termuxExecutor.initialize()
    }

    private suspend fun copyUriToInternal(uri: android.net.Uri, folder: String): File {
        return withContext(Dispatchers.IO) {
            val targetDir = File(filesDir, folder)

            val hasValidWorld = targetDir.exists() && (
                File(targetDir, "db").exists() || File(targetDir, "region").exists()
            )

            if (hasValidWorld) {
                Log.d(TAG, "Input directory already has valid world data, using existing: ${targetDir.absolutePath}")
                return@withContext targetDir
            }

            if (targetDir.exists()) {
                deleteDir(targetDir)
            }
            targetDir.mkdirs()

            try {
                val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(this@ConversionService, uri)
                if (documentFile != null && documentFile.isDirectory) {
                    copyDirectory(documentFile, targetDir)
                    Log.d(TAG, "Copied directory to: ${targetDir.absolutePath}")
                } else {
                    Log.e(TAG, "URI is not a valid directory: $uri")
                }
            } catch (e: IllegalArgumentException) {
                Log.d(TAG, "URI is not a tree URI: ${e.message}")
            }

            targetDir
        }
    }

    private fun copyDirectory(source: androidx.documentfile.provider.DocumentFile, target: File) {
        target.mkdirs()
        source.listFiles().forEach { file ->
            val destFile = File(target, file.name ?: "unknown")
            if (file.isDirectory) {
                copyDirectory(file, destFile)
            } else {
                try {
                    contentResolver.openInputStream(file.uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy file: ${file.name}", e)
                }
            }
        }
    }

    private suspend fun runChunkerConversion(inputDir: File, outputDir: File, settings: com.encore.chunkoid.model.ConversionSettings) {
        withContext(Dispatchers.IO) {
            val jarPath = getChunkerJarPath()
            if (!File(jarPath).exists()) {
                throw FileNotFoundException("Chunker JAR not found at: $jarPath")
            }

            val args = mutableListOf(
                "-Xms256m",
                "-Xmx${settings.maxMemory}m"
            )
            
            if (settings.lowMemoryMode) {
                args.add("-XX:+UseSerialGC")
                args.add("-XX:MinHeapFreeRatio=10")
                args.add("-XX:MaxHeapFreeRatio=20")
                args.add("-Djava.util.concurrent.ForkJoinPool.common.parallelism=1")
            }
            
            args.addAll(listOf(
                "-jar", jarPath,
                "-i", inputDir.absolutePath,
                "-o", outputDir.absolutePath,
                "-f", settings.targetFormat
            ))
            if (settings.blockMappings != null) {
                args.add("-bm")
                args.add(settings.blockMappings)
            }

            if (settings.worldSettings != null) {
                args.add("-ws")
                args.add(settings.worldSettings)
            }

            if (settings.pruningSettings != null) {
                args.add("-p")
                args.add(settings.pruningSettings)
            }

            if (settings.converterSettings != null) {
                args.add("-c")
                args.add(settings.converterSettings)
            }

            if (settings.dimensionMappings != null) {
                args.add("-dm")
                args.add(settings.dimensionMappings)
            }

            if (settings.keepOriginalNbt) {
                args.add("-k")
            }

            status.postValue("Provádím převod...")
            progress.postValue(25)

            val process = termuxExecutor.executeJava(*args.toTypedArray())
            conversionProcess = process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            var line: String?
            var lastProgress = 25

            fun processLine(lineStr: String) {
                rawLogBuilder.appendLine(lineStr)

                // 过滤 Android linker 警告
                if (lineStr.contains("WARNING: linker:") || lineStr.contains("has unsupported flags")) {
                    return
                }

                Log.d(TAG, "Chunker: $lineStr")

                val progressMatch = Regex("(\\d+\\.\\d+)%").find(lineStr)
                if (progressMatch != null) {
                    val chunkProgress = progressMatch.groupValues[1].toDouble()
                    val calculatedProgress = (25 + chunkProgress * 0.65).toInt()
                    if (calculatedProgress > lastProgress) {
                        progress.postValue(calculatedProgress)
                        updateNotification("Převádím svět... ($calculatedProgress%)", calculatedProgress, false)
                        lastProgress = calculatedProgress
                    }
                } else {
                    logMessage.postValue(com.encore.chunkoid.model.LogEntry(lineStr, com.encore.chunkoid.model.LogEntry.Level.INFO))
                }
            }

            var stdoutActive = true
            var stderrActive = true

            while (stdoutActive || stderrActive) {
                if (stdoutActive) {
                    line = reader.readLine()
                    if (line != null) {
                        processLine(line)
                    } else {
                        stdoutActive = false
                    }
                }

                if (stderrActive) {
                    line = errorReader.readLine()
                    if (line != null) {
                        processLine(line)
                    } else {
                        stderrActive = false
                    }
                }

                if (!process.isAlive && stdoutActive && stderrActive) {
                    Thread.sleep(100)
                    while (true) {
                        var drained = false
                        line = reader.readLine()
                        if (line != null) {
                            processLine(line)
                            drained = true
                        }
                        line = errorReader.readLine()
                        if (line != null) {
                            processLine(line)
                            drained = true
                        }
                        if (!drained) break
                    }
                    break
                }
            }

            val exitCode = process.waitFor()
            progress.postValue(85)

            if (exitCode != 0) {
                throw RuntimeException("Chunker exited with code: $exitCode")
            }
        }
    }

    private fun getChunkerJarPath(): String {
        return File(filesDir, "cli.jar").absolutePath
    }

    private fun moveOutputToDocuments(sourceDir: File, worldName: String) {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }

        val chunkoidOutputDir = File(documentsDir, "chunkoid output")
        if (!chunkoidOutputDir.exists()) {
            chunkoidOutputDir.mkdirs()
        }

        val safeWorldName = worldName.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "_")
        val worldOutputDir = File(chunkoidOutputDir, safeWorldName)

        if (worldOutputDir.exists()) {
            deleteDir(worldOutputDir)
        }

        if (!sourceDir.renameTo(worldOutputDir)) {
            copyDirectory(sourceDir, worldOutputDir)
            deleteDir(sourceDir)
        }

        Log.d(TAG, "Output moved to: ${worldOutputDir.absolutePath}")
    }

    private fun copyDirectory(sourceDir: File, targetDir: File) {
        targetDir.mkdirs()
        sourceDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                copyDirectory(file, File(targetDir, file.name))
            } else {
                try {
                    file.inputStream().use { input ->
                        FileOutputStream(File(targetDir, file.name)).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy file: ${file.name}", e)
                }
            }
        }
    }

    private fun deleteDir(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { deleteDir(it) }
        }
        dir.delete()
    }

    private fun onConversionComplete() {
        isRunning.value = false
        releaseWakeLock()
        conversionSuccess.value = true
        status.value = "Převod byl úspěšný!"
        progress.value = 100
        rawLogOutput.value = rawLogBuilder.toString()
        stopForegroundService()
        cleanInputFolder()
    }

    private fun onConversionError(error: Exception) {
        isRunning.value = false
        releaseWakeLock()
        conversionSuccess.value = false
        status.value = "Převod se nezdařil: ${error.message}"
        progress.value = 0
        rawLogOutput.value = rawLogBuilder.toString()
        stopForegroundService()
    }

    fun getRawLog(): String {
        return rawLogBuilder.toString()
    }

    private fun acquireWakeLock() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("enable_wake_lock", true)) {
            return
        }
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Chunkoid::ConversionWakeLock")
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(4 * 60 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onDestroy() {
        stopConversion()
        super.onDestroy()
        releaseWakeLock()
    }

    companion object {
        private const val TAG = "ConversionService"
        private const val CHANNEL_ID = "chunkoid_conversion_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
