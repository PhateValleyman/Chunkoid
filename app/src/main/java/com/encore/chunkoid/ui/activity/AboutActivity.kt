package com.encore.chunkoid.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.encore.chunkoid.R
import com.encore.chunkoid.utils.BackgroundUtils
import com.encore.chunkoid.utils.ForkReleaseUpdater

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        applyNoElevationToCards()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val btnCheckUpdate = findViewById<Button>(R.id.btn_check_update)
        btnCheckUpdate.setOnClickListener { checkForUpdates(btnCheckUpdate) }

        val btnDonate = findViewById<Button>(R.id.btn_donate)
        btnDonate.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ifdian.net/a/EASY7"))
            startActivity(intent)
        }

        val tvchunkerLink = findViewById<TextView>(R.id.tv_chunker_link)
        tvchunkerLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HiveGamesOSS/Chunker"))
            startActivity(intent)
        }

        val tvnbtLink = findViewById<TextView>(R.id.tv_nbt_link)
        tvnbtLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/PowerNukkit/NBT-Manipulator"))
            startActivity(intent)
        }

        val tvLevelDbLink = findViewById<TextView>(R.id.tv_leveldb_link)
        tvLevelDbLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HiveGamesOSS/leveldb-mcpe-java"))
            startActivity(intent)
        }

        val tvsdkLink = findViewById<TextView>(R.id.tv_sdk_link)
        tvsdkLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Dicecan/NetEaseDecryptorSDK"))
            startActivity(intent)
        }

        val tvwebsiteLink = findViewById<TextView>(R.id.tv_website_link)
        tvwebsiteLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chunkoid.top"))
            startActivity(intent)
        }

        val ivBilibili = findViewById<ImageView>(R.id.iv_bilibili)
        ivBilibili.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://space.bilibili.com/672708380"))
            startActivity(intent)
        }

        val ivGithub = findViewById<ImageView>(R.id.iv_github)
        ivGithub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/PhateValleyman/Chunkoid"))
            startActivity(intent)
        }

        val tvdocsLink = findViewById<TextView>(R.id.tv_docs_link)
        tvdocsLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chunkoid.top/docs/index.html"))
            startActivity(intent)
        }
    }

    private fun checkForUpdates(button: Button) {
        button.isEnabled = false
        button.text = getString(R.string.update_checking)
        Thread {
            val result = runCatching { ForkReleaseUpdater.findLatestStableApk() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                button.isEnabled = true
                button.text = getString(R.string.check_update)
                result.fold(
                    onSuccess = { release ->
                        if (release == null) showNoReleaseDialog() else showUpdateConfirmation(release)
                    },
                    onFailure = { error -> showUpdateError(error) }
                )
            }
        }.start()
    }

    private fun showNoReleaseDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_no_release_title)
            .setMessage(R.string.update_no_release_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showUpdateConfirmation(release: ForkReleaseUpdater.ReleaseInfo) {
        @Suppress("DEPRECATION")
        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        val availableVersion = release.tagName.ifBlank { release.title }
        val baseMessage = getString(R.string.update_available_message, availableVersion, currentVersion)
        val notes = release.notes.trim().take(MAX_RELEASE_NOTES_LENGTH)
        val message = if (notes.isBlank()) baseMessage else "$baseMessage\n\n${getString(R.string.update_notes_label)}\n$notes"
        AlertDialog.Builder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.update_download_button) { _, _ -> enqueueDownload(release.asset) }
            .show()
    }

    private fun enqueueDownload(asset: ForkReleaseUpdater.ReleaseAsset) {
        runCatching { ForkReleaseUpdater.enqueueApkDownload(this, asset) }
            .onSuccess { Toast.makeText(this, R.string.update_download_started, Toast.LENGTH_LONG).show() }
            .onFailure { showUpdateError(it) }
    }

    private fun showUpdateError(error: Throwable) {
        val detail = error.localizedMessage ?: getString(R.string.update_error_unknown)
        AlertDialog.Builder(this)
            .setTitle(R.string.update_error_title)
            .setMessage(getString(R.string.update_error_message, detail))
            .setPositiveButton(R.string.ok, null)
            .show()
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

    private companion object {
        const val MAX_RELEASE_NOTES_LENGTH = 1800
    }
}
