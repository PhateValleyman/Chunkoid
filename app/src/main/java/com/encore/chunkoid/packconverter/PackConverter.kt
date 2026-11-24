package com.encore.chunkoid.packconverter

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PackConverter(private val context: Context) {

    interface ConvertListener {
        fun onProgress(progress: Int)
        fun onLog(message: String)
        fun onSuccess(outputPath: String)
        fun onError(error: String)
    }

    suspend fun convertPack(
        sourceDoc: DocumentFile,
        targetDir: File,
        direction: ConversionDirection,
        listener: ConvertListener
    ): String? = withContext(Dispatchers.IO) {
        try {
            listener.onLog("Prohledávám strukturu složky balíčku...")

            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            val allFiles = mutableListOf<Pair<String, DocumentFile>>()
            collectFiles(sourceDoc, "", allFiles)

            listener.onLog("Nalezeno ${allFiles.size} souborů")

            if (allFiles.isEmpty()) {
                listener.onError("Nebyly nalezeny žádné soubory. Zkontrolujte, zda jste vybrali správnou složku balíčku.")
                return@withContext null
            }

            val processedBlockTextures = mutableSetOf<String>()
            val processedItemTextures = mutableSetOf<String>()
            val allTargetTextures = mutableSetOf<String>()

            var packName = "Converted Pack"
            var packDescription = "Converted by Chunkoid"
            var headerUuid = UUID.randomUUID().toString()
            var moduleUuid = UUID.randomUUID().toString()

            for ((relativePath, file) in allFiles) {
                if (file.isDirectory) continue

                val targetPath = PackPathRules.getTargetPath(relativePath, direction)

                if (targetPath != null) {
                    val targetFile = File(targetDir, targetPath)
                    targetFile.parentFile?.mkdirs()

                    if (direction == ConversionDirection.JAVA_TO_BEDROCK) {
                        when {
                            relativePath.endsWith(".json") && targetPath.startsWith("texts/") -> {
                                convertJavaLangToBedrock(file, targetFile, listener)
                            }
                            relativePath.endsWith("sounds.json") -> {
                                convertJavaSoundsToBedrock(file, targetFile, listener)
                            }
                            relativePath == "assets/minecraft/texts/splashes.txt" -> {
                                convertSplashesTxtToJson(file, targetFile, listener)
                            }
                            relativePath.endsWith(".png.mcmeta") -> {
                                listener.onLog("Přeskakuji metadata animace: $relativePath")
                                continue
                            }
                            relativePath.startsWith("assets/minecraft/textures/block/") -> {
                                copyFile(file, targetFile)
                                processedBlockTextures.add(targetPath)
                            }
                            relativePath.startsWith("assets/minecraft/textures/item/") -> {
                                copyFile(file, targetFile)
                                processedItemTextures.add(targetPath)
                            }
                            else -> {
                                copyFile(file, targetFile)
                            }
                        }
                    } else {
                        when {
                            relativePath.endsWith(".lang") && targetPath.endsWith(".json") -> {
                                convertBedrockLangToJava(file, targetFile, listener)
                            }
                            relativePath == "sounds/sound_definitions.json" -> {
                                convertBedrockSoundsToJava(file, targetFile, listener)
                            }
                            else -> {
                                copyFile(file, targetFile)
                            }
                        }
                    }

                    if (targetPath.endsWith(".png") || targetPath.endsWith(".tga")) {
                        allTargetTextures.add(targetPath)
                    }
                } else {
                    if (direction == ConversionDirection.JAVA_TO_BEDROCK) {
                        when {
                            relativePath == "pack.mcmeta" -> {
                                val mcmetaContent = readFileContent(file)
                                try {
                                    val json = JSONObject(mcmetaContent)
                                    if (json.has("pack")) {
                                        val packObj = json.getJSONObject("pack")
                                        if (packObj.has("description")) {
                                            packDescription = packObj.getString("description")
                                        }
                                    }
                                } catch (e: Exception) {
                                    listener.onLog("Soubor pack.mcmeta se nepodařilo zpracovat: ${e.message}")
                                }
                                generateBedrockManifest(targetDir, packName, packDescription, headerUuid, moduleUuid, listener)
                            }
                            relativePath == "pack.png" -> {
                                val targetFile = File(targetDir, "pack_icon.png")
                                copyFile(file, targetFile)
                            }
                            else -> {
                                listener.onLog("Přeskakuji soubor bez mapování: $relativePath")
                            }
                        }
                    } else {
                        when {
                            relativePath == "manifest.json" -> {
                                val manifestContent = readFileContent(file)
                                try {
                                    val json = JSONObject(manifestContent)
                                    if (json.has("header")) {
                                        val header = json.getJSONObject("header")
                                        if (header.has("name")) packName = header.getString("name")
                                        if (header.has("description")) packDescription = header.getString("description")
                                        if (header.has("uuid")) headerUuid = header.getString("uuid")
                                        if (json.has("modules")) {
                                            val modules = json.getJSONArray("modules")
                                            if (modules.length() > 0) {
                                                val module = modules.getJSONObject(0)
                                                if (module.has("uuid")) moduleUuid = module.getString("uuid")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    listener.onLog("Soubor manifest.json se nepodařilo zpracovat: ${e.message}")
                                }
                                generateJavaMcmeta(targetDir, packDescription, listener)
                            }
                            relativePath == "pack_icon.png" -> {
                                val targetFile = File(targetDir, "pack.png")
                                copyFile(file, targetFile)
                            }
                            else -> {
                                listener.onLog("Přeskakuji soubor bez mapování: $relativePath")
                            }
                        }
                    }
                }

                val progress = ((allFiles.indexOfFirst { it.first == relativePath } + 1) * 100) / allFiles.size
                listener.onProgress(progress)
            }

            if (direction == ConversionDirection.JAVA_TO_BEDROCK) {
                if (processedBlockTextures.isNotEmpty()) {
                    generateTerrainTexture(targetDir, processedBlockTextures, listener)
                    generateBlocksJson(targetDir, processedBlockTextures, listener)
                }
                if (processedItemTextures.isNotEmpty()) {
                    generateItemTexture(targetDir, processedItemTextures, listener)
                }
                if (allTargetTextures.isNotEmpty()) {
                    generateTexturesList(targetDir, allTargetTextures, listener)
                }
            }

            listener.onSuccess(targetDir.absolutePath)
            targetDir.absolutePath

        } catch (e: Exception) {
            e.printStackTrace()
            listener.onError("Při převodu došlo k neznámé chybě: ${e.message}")
            null
        }
    }

    private fun collectFiles(doc: DocumentFile, prefix: String, result: MutableList<Pair<String, DocumentFile>>) {
        doc.listFiles().forEach { child ->
            val childPath = if (prefix.isEmpty()) child.name ?: "" else "$prefix/${child.name}"
            if (child.isDirectory) {
                collectFiles(child, childPath, result)
            } else {
                result.add(childPath to child)
            }
        }
    }

    private fun copyFile(source: DocumentFile, target: File) {
        context.contentResolver.openInputStream(source.uri)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun readFileContent(file: DocumentFile): String {
        return context.contentResolver.openInputStream(file.uri)?.use { input ->
            input.bufferedReader().readText()
        } ?: ""
    }

    private fun convertJavaLangToBedrock(source: DocumentFile, target: File, listener: ConvertListener) {
        try {
            val content = readFileContent(source)
            val json = JSONObject(content)
            val langBuilder = StringBuilder()
            json.keys().forEach { key ->
                val value = json.getString(key).replace("\\n", "\n")
                langBuilder.append("$key=$value\n")
            }
            target.writeText(langBuilder.toString())
            listener.onLog("Převádím jazykový soubor: ${source.name} -> ${target.name}")
        } catch (e: Exception) {
            listener.onLog("Jazykový soubor se nepodařilo převést: ${source.name}, ${e.message}")
            copyFile(source, target)
        }
    }

    private fun convertBedrockLangToJava(source: DocumentFile, target: File, listener: ConvertListener) {
        try {
            val content = readFileContent(source)
            val json = JSONObject()
            content.lines().forEach { line ->
                if (line.isNotEmpty() && line.contains("=")) {
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        json.put(parts[0], parts[1].replace("\n", "\\n"))
                    }
                }
            }
            target.writeText(json.toString(2))
            listener.onLog("Převádím jazykový soubor: ${source.name} -> ${target.name}")
        } catch (e: Exception) {
            listener.onLog("Jazykový soubor se nepodařilo převést: ${source.name}, ${e.message}")
            copyFile(source, target)
        }
    }

    private fun convertJavaSoundsToBedrock(source: DocumentFile, target: File, listener: ConvertListener) {
        try {
            val content = readFileContent(source)
            val json = JSONObject(content)
            val result = JSONObject()

            json.keys().forEach { soundKey ->
                val soundValue = json.get(soundKey)
                if (soundValue is JSONObject) {
                    val soundsArray = mutableListOf<JSONObject>()
                    if (soundValue.has("sounds")) {
                        val sounds = soundValue.getJSONArray("sounds")
                        for (i in 0 until sounds.length()) {
                            val sound = sounds.get(i)
                            val soundObj = JSONObject()
                            if (sound is String) {
                                soundObj.put("name", sound)
                            } else if (sound is JSONObject) {
                                if (sound.has("name")) soundObj.put("name", sound.getString("name"))
                                if (sound.has("volume")) soundObj.put("volume", sound.getDouble("volume"))
                                if (sound.has("pitch")) soundObj.put("pitch", sound.getDouble("pitch"))
                                if (sound.has("weight")) soundObj.put("weight", sound.getInt("weight"))
                            }
                            soundsArray.add(soundObj)
                        }
                    }
                    result.put(soundKey, JSONArray(soundsArray))
                }
            }

            target.writeText(result.toString(2))
            listener.onLog("Převádím definice zvuků: ${target.name}")
        } catch (e: Exception) {
            listener.onLog("Definice zvuků se nepodařilo převést: ${e.message}")
            copyFile(source, target)
        }
    }

    private fun convertBedrockSoundsToJava(source: DocumentFile, target: File, listener: ConvertListener) {
        try {
            val content = readFileContent(source)
            val json = JSONObject(content)
            val result = JSONObject()

            json.keys().forEach { soundKey ->
                val soundValue = json.get(soundKey)
                if (soundValue is JSONArray) {
                    val soundObj = JSONObject()
                    val soundsArray = JSONArray()
                    for (i in 0 until soundValue.length()) {
                        val sound = soundValue.getJSONObject(i)
                        val javaSoundObj = JSONObject()
                        if (sound.has("name")) javaSoundObj.put("name", sound.getString("name"))
                        if (sound.has("volume")) javaSoundObj.put("volume", sound.getDouble("volume"))
                        if (sound.has("pitch")) javaSoundObj.put("pitch", sound.getDouble("pitch"))
                        if (sound.has("weight")) javaSoundObj.put("weight", sound.getInt("weight"))
                        soundsArray.put(javaSoundObj)
                    }
                    soundObj.put("sounds", soundsArray)
                    result.put(soundKey, soundObj)
                }
            }

            target.writeText(result.toString(2))
            listener.onLog("Převádím definice zvuků: ${target.name}")
        } catch (e: Exception) {
            listener.onLog("Definice zvuků se nepodařilo převést: ${e.message}")
            copyFile(source, target)
        }
    }

    private fun convertSplashesTxtToJson(source: DocumentFile, target: File, listener: ConvertListener) {
        try {
            val content = readFileContent(source)
            val lines = content.lines().filter { it.isNotEmpty() }
            val json = JSONObject()
            json.put("splashes", JSONArray(lines))
            target.writeText(json.toString(2))
            listener.onLog("Převádím úvodní hlášky: ${target.name}")
        } catch (e: Exception) {
            listener.onLog("Úvodní hlášky se nepodařilo převést: ${e.message}")
            copyFile(source, target)
        }
    }

    private fun generateBedrockManifest(targetDir: File, name: String, description: String, headerUuid: String, moduleUuid: String, listener: ConvertListener) {
        try {
            val manifest = JSONObject().apply {
                put("format_version", 2)
                put("header", JSONObject().apply {
                    put("description", "$description\nConverted by Chunkoid")
                    put("name", name)
                    put("uuid", headerUuid)
                    put("version", JSONArray(listOf(1, 0, 0)))
                    put("min_engine_version", JSONArray(listOf(1, 21, 0)))
                })
                put("modules", JSONArray().apply {
                    put(JSONObject().apply {
                        put("description", "$description\nConverted by Chunkoid")
                        put("type", "resources")
                        put("uuid", moduleUuid)
                        put("version", JSONArray(listOf(1, 0, 0)))
                    })
                })
            }

            File(targetDir, "manifest.json").writeText(manifest.toString(2))
            listener.onLog("Vytvářím manifest.json pro Bedrock Edition")
        } catch (e: Exception) {
            listener.onLog("Soubor manifest.json se nepodařilo vytvořit: ${e.message}")
        }
    }

    private fun generateJavaMcmeta(targetDir: File, description: String, listener: ConvertListener) {
        try {
            val mcmeta = JSONObject().apply {
                put("pack", JSONObject().apply {
                    put("pack_format", 18)
                    put("description", "$description\nConverted with Chunkoid")
                })
            }

            File(targetDir, "pack.mcmeta").writeText(mcmeta.toString(2))
            listener.onLog("Vytvářím pack.mcmeta pro Java Edition")
        } catch (e: Exception) {
            listener.onLog("Soubor pack.mcmeta se nepodařilo vytvořit: ${e.message}")
        }
    }

    private fun generateTerrainTexture(targetDir: File, textures: Set<String>, listener: ConvertListener) {
        try {
            val terrainTexture = JSONObject().apply {
                put("resource_pack_name", "vanilla")
                put("texture_name", "atlas.terrain")
                put("texture_data", JSONObject().apply {
                    textures.forEach { texturePath ->
                        val textureName = texturePath.replace("textures/blocks/", "").replace(".png", "")
                        put(textureName, JSONObject().apply {
                            put("textures", texturePath.replace(".png", ""))
                        })
                    }
                })
            }

            File(targetDir, "textures/terrain_texture.json").writeText(terrainTexture.toString(2))
            listener.onLog("Vytvářím terrain_texture.json (${textures.size} textur bloků)")
        } catch (e: Exception) {
            listener.onLog("Soubor terrain_texture.json se nepodařilo vytvořit: ${e.message}")
        }
    }

    private fun generateBlocksJson(targetDir: File, textures: Set<String>, listener: ConvertListener) {
        try {
            val blocks = JSONObject()
            textures.forEach { texturePath ->
                val textureName = texturePath.replace("textures/blocks/", "").replace(".png", "")
                blocks.put(textureName, JSONObject().apply {
                    put("sound", "stone")
                    put("textures", JSONObject().apply {
                        put("up", textureName)
                        put("down", textureName)
                        put("north", textureName)
                        put("south", textureName)
                        put("west", textureName)
                        put("east", textureName)
                    })
                })
            }

            File(targetDir, "blocks.json").writeText(blocks.toString(2))
            listener.onLog("Vytvářím blocks.json (${textures.size} bloků)")
        } catch (e: Exception) {
            listener.onLog("Soubor blocks.json se nepodařilo vytvořit: ${e.message}")
        }
    }

    private fun generateItemTexture(targetDir: File, textures: Set<String>, listener: ConvertListener) {
        try {
            val itemTexture = JSONObject().apply {
                put("resource_pack_name", "vanilla")
                put("texture_name", "atlas.items")
                put("texture_data", JSONObject().apply {
                    textures.forEach { texturePath ->
                        val textureName = texturePath.replace("textures/items/", "").replace(".png", "")
                        put(textureName, JSONObject().apply {
                            put("textures", texturePath.replace(".png", ""))
                        })
                    }
                })
            }

            File(targetDir, "textures/item_texture.json").writeText(itemTexture.toString(2))
            listener.onLog("Vytvářím item_texture.json (${textures.size} textur předmětů)")
        } catch (e: Exception) {
            listener.onLog("Soubor item_texture.json se nepodařilo vytvořit: ${e.message}")
        }
    }

    private fun generateTexturesList(targetDir: File, textures: Set<String>, listener: ConvertListener) {
        try {
            val json = JSONArray(textures.toList())
            File(targetDir, "textures/textures_list.json").writeText(json.toString(2))
            listener.onLog("Vytvářím textures_list.json (${textures.size} textur)")
        } catch (e: Exception) {
            listener.onLog("Soubor textures_list.json se nepodařilo vytvořit: ${e.message}")
        }
    }
}