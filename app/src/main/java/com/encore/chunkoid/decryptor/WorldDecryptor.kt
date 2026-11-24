package com.encore.chunkoid.decryptor

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WorldDecryptor(private val context: Context) {

    suspend fun decryptWorld(
        sourceDoc: DocumentFile,
        targetDir: File,
        listener: DecryptListener
    ): String? = withContext(Dispatchers.IO) {
        try {
            listener.onLog("Prohledávám strukturu složky světa...")

            var dbDoc: DocumentFile? = null
            sourceDoc.listFiles().forEach { child ->
                if (child.isDirectory && child.name == "db") {
                    dbDoc = child
                }
            }

            if (dbDoc == null) {
                listener.onError("Ve složce nebyla nalezena platná databázová složka db. Zkontrolujte, zda jste vybrali kořenovou složku světa NetEase.")
                return@withContext null
            }

            val validDbDoc = dbDoc!!
            val dbFiles = validDbDoc.listFiles()
            var currentDoc: DocumentFile? = null
            var manifestDoc: DocumentFile? = null

            for (file in dbFiles) {
                if (file.isFile) {
                    val name = file.name ?: continue
                    if (name == "CURRENT") currentDoc = file
                    if (name.startsWith("MANIFEST")) manifestDoc = file
                }
            }

            if (currentDoc == null || manifestDoc == null) {
                listener.onError("Ve složce db chybí soubor CURRENT nebo MANIFEST, který je nezbytný pro databázi.")
                return@withContext null
            }

            listener.onLog("Čtu ukazatel CURRENT a počítám dešifrovací klíč...")
            val decryptKey = try {
                context.contentResolver.openInputStream(currentDoc.uri)?.use { inputStream ->
                    NetEaseDecryptor.deriveKey(inputStream, manifestDoc.name.orEmpty())
                }
            } catch (e: Exception) {
                listener.onLog("Nepodařilo se získat klíč nebo jsou data nezašifrovaná: ${e.message}. Pokud nejsou zašifrovaná, zkopírují se beze změny.")
                null
            }

            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            val targetDbDir = File(targetDir, "db")
            if (!targetDbDir.exists()) {
                targetDbDir.mkdirs()
            }

            listener.onLog("Kopíruji základní metadata světa...")
            sourceDoc.listFiles().forEach { child ->
                if (child.isFile) {
                    val outFile = File(targetDir, child.name ?: "")
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            listener.onLog("Postupně dešifruji a exportuji hlavní databázi LevelDB...")
            val totalFiles = dbFiles.size
            var processed = 0

            for (file in dbFiles) {
                if (file.isFile) {
                    val fileName = file.name ?: continue
                    val outFile = File(targetDbDir, fileName)

                    if (decryptKey != null) {
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            FileOutputStream(outFile).use { output ->
                                NetEaseDecryptor.decryptFile(input, output, decryptKey)
                            }
                        }
                    } else {
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                processed++
                listener.onProgress((processed * 100) / totalFiles)
            }

            listener.onSuccess(targetDir.absolutePath)
            targetDir.absolutePath

        } catch (e: Exception) {
            e.printStackTrace()
            listener.onError("Při dešifrování došlo k neznámé chybě: ${e.message}")
            null
        }
    }

    interface DecryptListener {
        fun onProgress(progress: Int)
        fun onLog(message: String)
        fun onSuccess(exportPath: String)
        fun onError(error: String)
    }
}
