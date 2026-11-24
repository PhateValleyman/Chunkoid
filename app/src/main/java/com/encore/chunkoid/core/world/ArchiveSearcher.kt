package com.encore.chunkoid.core.world

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ArchiveSearcher(private val contentResolver: ContentResolver, private val filesDir: File) {

    interface SearchCallback {
        fun onProgress(progress: Int, message: String)
        fun onSuccess(worldDir: File)
        fun onError(errorMessage: String)
    }

    fun searchAndExtractArchive(uri: Uri, callback: SearchCallback) {
        Thread {
            try {
                val inputDir = File(filesDir, "input")
                if (inputDir.exists()) {
                    inputDir.deleteRecursively()
                }
                inputDir.mkdirs()

                callback.onProgress(0, "Rozbaluji archiv...")

                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val zipInputStream = ZipInputStream(inputStream)
                    var entry = zipInputStream.nextEntry
                    var extractedCount = 0
                    val buffer = ByteArray(8192)

                    while (entry != null) {
                        val entryName = entry.name
                        // Avoid Zip Slip vulnerability
                        val entryFile = File(inputDir, entryName)
                        if (!entryFile.canonicalPath.startsWith(inputDir.canonicalPath)) {
                            entry = zipInputStream.nextEntry
                            continue
                        }

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            entryFile.outputStream().use { outputStream ->
                                var bytesRead: Int
                                while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                            }
                        }

                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                        extractedCount++
                        if (extractedCount % 10 == 0) {
                            val progress = (extractedCount / 5).coerceIn(5, 50)
                            callback.onProgress(progress, "Rozbaluji archiv ($extractedCount souborů)...")
                        }
                    }
                    zipInputStream.close()
                }

                callback.onProgress(50, "Hledám svět v archivu...")

                val levelDatFile = findLevelDat(inputDir)
                if (levelDatFile == null) {
                    inputDir.deleteRecursively()
                    callback.onError("V archivu nebyl nalezen platný svět")
                    return@Thread
                }

                val actualWorldDir = levelDatFile.parentFile
                if (actualWorldDir == null || actualWorldDir.absolutePath == inputDir.absolutePath) {
                    callback.onProgress(100, "Svět je připraven")
                    callback.onSuccess(inputDir)
                    return@Thread
                }

                callback.onProgress(75, "Uspořádávám soubory světa...")

                moveWorldToRoot(inputDir, actualWorldDir)

                callback.onProgress(100, "Svět je připraven")
                callback.onSuccess(inputDir)

            } catch (e: Exception) {
                e.printStackTrace()
                val inputDir = File(filesDir, "input")
                if (inputDir.exists()) {
                    inputDir.deleteRecursively()
                }
                callback.onError("Zpracování archivu se nezdařilo: ${e.message}")
            }
        }.start()
    }

    private fun findLevelDat(searchDir: File): File? {
        val files = searchDir.listFiles() ?: return null
        for (file in files) {
            if (file.isDirectory) {
                val found = findLevelDat(file)
                if (found != null) {
                    return found
                }
            } else if (file.name.equals("level.dat", ignoreCase = true)) {
                return file
            }
        }
        return null
    }

    private fun moveWorldToRoot(inputDir: File, worldDir: File) {
        val worldFiles = worldDir.listFiles() ?: return

        for (file in worldFiles) {
            val targetFile = File(inputDir, file.name)
            if (targetFile.exists()) {
                targetFile.deleteRecursively()
            }
            file.renameTo(targetFile)
        }

        val emptyDirs = mutableListOf<File>()
        collectEmptyDirs(inputDir, emptyDirs)
        emptyDirs.forEach { it.delete() }
    }

    private fun collectEmptyDirs(dir: File, emptyDirs: MutableList<File>) {
        val files = dir.listFiles() ?: return

        var isEmpty = true
        for (file in files) {
            if (file.isDirectory) {
                collectEmptyDirs(file, emptyDirs)
                if (!emptyDirs.contains(file)) {
                    isEmpty = false
                }
            } else {
                isEmpty = false
            }
        }

        if (isEmpty && dir.absolutePath != File(filesDir, "input").absolutePath) {
            emptyDirs.add(dir)
        }
    }

    fun isZipFile(uri: Uri): Boolean {
        val fileName = getFileName(uri)
        return fileName?.lowercase()?.endsWith(".zip") == true ||
               uri.path?.lowercase()?.endsWith(".zip") == true ||
               uri.lastPathSegment?.lowercase()?.endsWith(".zip") == true
    }

    fun isMcWorldFile(uri: Uri): Boolean {
        val fileName = getFileName(uri)
        return fileName?.lowercase()?.endsWith(".mcworld") == true ||
               uri.path?.lowercase()?.endsWith(".mcworld") == true ||
               uri.lastPathSegment?.lowercase()?.endsWith(".mcworld") == true
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex("_display_name")
                    if (nameIndex != -1) {
                        return it.getString(nameIndex)
                    }
                }
            }
            uri.path?.substringAfterLast('/')
        } catch (e: Exception) {
            uri.path?.substringAfterLast('/')
        }
    }

    fun extractArchiveToInput(uri: Uri, progressCallback: ((Int, String) -> Unit)? = null): Boolean {
        try {
            val inputDir = File(filesDir, "input")
            if (inputDir.exists()) {
                inputDir.deleteRecursively()
            }
            inputDir.mkdirs()

            contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                var extractedCount = 0
                val buffer = ByteArray(8192)

                while (entry != null) {
                    val entryName = entry.name
                    val entryFile = File(inputDir, entryName)
                    if (!entryFile.canonicalPath.startsWith(inputDir.canonicalPath)) {
                        entry = zipInputStream.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        entryFile.outputStream().use { outputStream ->
                            var bytesRead: Int
                            while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                        }
                    }

                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                    extractedCount++
                    if (extractedCount % 10 == 0) {
                        val progress = (extractedCount / 5).coerceIn(5, 95)
                        progressCallback?.invoke(progress, "Rozbaluji archiv ($extractedCount souborů)...")
                    }
                }

                zipInputStream.close()
            }

            progressCallback?.invoke(100, "Rozbalování dokončeno")
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun detectPlatformFromDirectory(): Platform {
        val inputDir = File(filesDir, "input")
        val dbFolder = File(inputDir, "db")
        val regionFolder = File(inputDir, "region")
        val dimensionsFolder = File(inputDir, "dimensions")

        if (dbFolder.exists() && dbFolder.isDirectory) {
            return Platform.BEDROCK
        }
        if ((regionFolder.exists() && regionFolder.isDirectory) ||
            (dimensionsFolder.exists() && dimensionsFolder.isDirectory)) {
            return Platform.JAVA
        }
        return Platform.BEDROCK
    }

    fun detectPlatformFromArchive(uri: Uri): Platform {
        if (isMcWorldFile(uri)) {
            return Platform.BEDROCK
        }

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                var hasDbFolder = false
                var hasRegionFolder = false
                var hasDimensionsFolder = false

                while (entry != null) {
                    val entryName = entry.name.lowercase()

                    if (entryName.contains("db/") || entryName.endsWith("/db")) {
                        hasDbFolder = true
                    }
                    if (entryName.contains("region/") || entryName.endsWith("/region")) {
                        hasRegionFolder = true
                    }
                    if (entryName.contains("dimensions/") || entryName.endsWith("/dimensions")) {
                        hasDimensionsFolder = true
                    }

                    entry = zipInputStream.nextEntry
                }

                zipInputStream.close()

                if (hasDbFolder && !hasRegionFolder) return Platform.BEDROCK
                if (hasRegionFolder || hasDimensionsFolder) return Platform.JAVA
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Platform.BEDROCK
    }
}
