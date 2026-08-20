package com.encore.chunkoid.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.encore.chunkoid.R
import com.encore.chunkoid.core.runtime.RootFSInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SplashActivity : AppCompatActivity() {

    private val TAG = "SplashActivity"
    private var progressText: TextView? = null
    private var retryDialog: AlertDialog? = null
    private var initJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastProgress = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        progressText = findViewById(R.id.progress_text)

        // Check if RootFS and cli.jar are both ready
        if (isEnvironmentReady(this)) {
            proceedToMainActivity()
            return
        }

        // First launch or missing environment - show progress & initialize
        progressText?.visibility = TextView.VISIBLE
        progressText?.text = getString(R.string.initializing)

        performInitialization()
    }

    private fun isEnvironmentReady(context: Context): Boolean {
        val rootfsReady = RootFSInstaller.isRootFSReady(context)
        val cliJar = File(context.filesDir, "cli.jar")
        val cliJarReady = cliJar.exists() && cliJar.length() > 1024 * 1024 // > 1MB
        return rootfsReady && cliJarReady
    }

    private fun performInitialization() {
        initJob?.cancel()
        initJob = CoroutineScope(Dispatchers.IO).launch {
            val success = initializeFiles()
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                if (success) {
                    proceedToMainActivity()
                } else {
                    showRetryDialog()
                }
            }
        }
    }

    private suspend fun initializeFiles(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Extract rootfs if not ready
                if (!RootFSInstaller.isRootFSReady(this@SplashActivity)) {
                    val rootfsOk = RootFSInstaller.extractRootFS(this@SplashActivity, object : RootFSInstaller.ProgressCallback {
                        override fun onProgress(progress: Int) {
                            val scaledProgress = (progress * 0.7).toInt()
                            updateProgress(scaledProgress)
                        }

                        override fun onComplete(success: Boolean) {}
                    })
                    if (!rootfsOk) {
                        Log.e(TAG, "Failed to extract rootfs")
                        return@withContext false
                    }
                }

                // 2. Copy chunker cli.jar if not ready
                if (!copyChunkerJar()) {
                    Log.e(TAG, "Failed to copy cli.jar")
                    return@withContext false
                }

                updateProgress(100)
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Exception during initialization", e)
                return@withContext false
            }
        }
    }

    private fun copyChunkerJar(): Boolean {
        val targetFile = File(filesDir, "cli.jar")
        if (targetFile.exists() && targetFile.length() > 1024 * 1024) {
            return true
        }

        val tempFile = File(filesDir, "cli.jar.tmp")
        try {
            if (tempFile.exists()) tempFile.delete()

            assets.open("cli.jar").use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    var copiedBytes: Long = 0
                    val approxTotal = 31 * 1024 * 1024L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        copiedBytes += bytesRead

                        val progress = 70 + ((copiedBytes.toDouble() / approxTotal) * 30).toInt().coerceIn(0, 30)
                        updateProgress(progress)
                    }
                    output.flush()
                }
            }

            if (tempFile.exists() && tempFile.length() > 1024 * 1024) {
                if (targetFile.exists()) targetFile.delete()
                return tempFile.renameTo(targetFile)
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed copying chunker jar", e)
            tempFile.delete()
            return false
        }
    }

    private fun updateProgress(progress: Int) {
        if (progress == lastProgress) return
        lastProgress = progress
        mainHandler.post {
            if (!isFinishing && !isDestroyed) {
                progressText?.text = getString(R.string.initializing_copying, progress)
            }
        }
    }

    private fun showRetryDialog() {
        if (isFinishing || isDestroyed) return
        retryDialog?.dismiss()
        retryDialog = AlertDialog.Builder(this)
            .setMessage(R.string.initializing_failed)
            .setPositiveButton(R.string.retry) { _, _ ->
                progressText?.text = getString(R.string.initializing)
                performInitialization()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .create()
        retryDialog?.show()
    }

    private fun proceedToMainActivity() {
        retryDialog?.dismiss()
        retryDialog = null
        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }, 500)
    }

    override fun onDestroy() {
        super.onDestroy()
        initJob?.cancel()
        retryDialog?.dismiss()
        retryDialog = null
        mainHandler.removeCallbacksAndMessages(null)
    }
}