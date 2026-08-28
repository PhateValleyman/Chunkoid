package com.encore.chunkoid.core.leveldb

import br.com.gamemods.nbtmanipulator.NbtCompound
import br.com.gamemods.nbtmanipulator.NbtFile
import br.com.gamemods.nbtmanipulator.NbtIO
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import org.iq80.leveldb.impl.Iq80DBFactory.factory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class LevelDbCategory(val title: String) {
    ALL("全部"),
    PLAYER("玩家"),
    ENTITY("实体"),
    BLOCK_ENTITY("方块实体"),
    WORLD("世界系统"),
    CHUNK("区块"),
    OTHER("其他")
}

data class LevelDbRecord(
    val key: ByteArray,
    val keyString: String,
    val displayName: String,
    val category: LevelDbCategory,
    val chunkX: Int? = null,
    val chunkZ: Int? = null,
    val dimensionId: Int? = null,
    val valueSize: Int,
    val isNbt: Boolean,
    val hasMultipleCompounds: Boolean = false
) {
    fun keyToHex(): String {
        return key.joinToString("") { "%02x".format(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LevelDbRecord
        return key.contentEquals(other.key)
    }

    override fun hashCode(): Int {
        return key.contentHashCode()
    }
}

class BedrockLevelDbHelper(val dbFolder: File) : Closeable, AutoCloseable {

    private var db: DB? = null

    fun open(readOnly: Boolean = false): DB {
        if (db == null) {
            val options = Options().apply {
                createIfMissing(false)
            }
            db = org.iq80.leveldb.impl.DbImpl(options, dbFolder.absolutePath, SafeEnv())
        }
        return db!!
    }

    fun getAllRecords(onProgress: ((scannedCount: Int) -> Unit)? = null): List<LevelDbRecord> {
        val database = open()
        val list = mutableListOf<LevelDbRecord>()
        val iterator = database.iterator()
        var count = 0
        try {
            iterator.seekToFirst()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val key = entry.key
                val value = entry.value
                val sample = if (value.size > 16) value.copyOfRange(0, 16) else value
                list.add(categorizeKey(key, value.size, sample))
                count++
                if (count % 200 == 0) {
                    onProgress?.invoke(count)
                }
            }
            onProgress?.invoke(count)
        } finally {
            iterator.close()
        }
        return list
    }

    fun get(key: ByteArray): ByteArray? {
        val database = open()
        return database.get(key)
    }

    fun put(key: ByteArray, value: ByteArray) {
        val database = open()
        database.put(key, value)
    }

    fun delete(key: ByteArray) {
        val database = open()
        database.delete(key)
    }

    fun deleteChunk(chunkX: Int, chunkZ: Int, dimensionId: Int? = null): Int {
        val database = open()
        val allRecords = getAllRecords()
        val toDelete = allRecords.filter { record ->
            record.chunkX == chunkX && record.chunkZ == chunkZ &&
                (dimensionId == null || record.dimensionId == dimensionId)
        }
        val batch = database.createWriteBatch()
        try {
            for (rec in toDelete) {
                batch.delete(rec.key)
            }
            database.write(batch)
        } finally {
            batch.close()
        }
        return toDelete.size
    }

    fun deleteChunks(chunkCoords: Collection<Pair<Int, Int>>, dimensionId: Int? = null): Int {
        val database = open()
        val allRecords = getAllRecords()
        val coordSet = chunkCoords.toSet()
        val toDelete = allRecords.filter { record ->
            record.chunkX != null && record.chunkZ != null &&
                coordSet.contains(Pair(record.chunkX, record.chunkZ)) &&
                (dimensionId == null || record.dimensionId == dimensionId)
        }
        val batch = database.createWriteBatch()
        try {
            for (rec in toDelete) {
                batch.delete(rec.key)
            }
            database.write(batch)
        } finally {
            batch.close()
        }
        return toDelete.size
    }

    fun deleteChunksOutsideRange(minChunkX: Int, maxChunkX: Int, minChunkZ: Int, maxChunkZ: Int, dimensionId: Int? = null): Int {
        val database = open()
        val allRecords = getAllRecords()
        val toDelete = allRecords.filter { record ->
            val cx = record.chunkX
            val cz = record.chunkZ
            cx != null && cz != null &&
                (dimensionId == null || record.dimensionId == dimensionId) &&
                (cx < minChunkX || cx > maxChunkX || cz < minChunkZ || cz > maxChunkZ)
        }
        val batch = database.createWriteBatch()
        try {
            for (rec in toDelete) {
                batch.delete(rec.key)
            }
            database.write(batch)
        } finally {
            batch.close()
        }
        return toDelete.size
    }

    fun getPopulatedChunkCoordinates(dimensionId: Int = 0): Set<Pair<Int, Int>> {
        val database = open()
        val set = mutableSetOf<Pair<Int, Int>>()
        val iterator = database.iterator()
        try {
            iterator.seekToFirst()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val key = entry.key
                if (key.size in 9..14 || key.size in 4..8) {
                    try {
                        val buffer = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN)
                        if (key.size >= 8) {
                            val chunkX = buffer.getInt(0)
                            val chunkZ = buffer.getInt(4)
                            val hasDim = key.size >= 13
                            val dim = if (hasDim) buffer.getInt(8) else 0
                            if (dim == dimensionId) {
                                set.add(Pair(chunkX, chunkZ))
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        } finally {
            iterator.close()
        }
        return set
    }

    fun getBlockEntityChunkCoordinates(dimensionId: Int = 0): Set<Pair<Int, Int>> {
        val database = open()
        val set = mutableSetOf<Pair<Int, Int>>()
        val iterator = database.iterator()
        try {
            iterator.seekToFirst()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val key = entry.key
                if (key.size in 9..14) {
                    try {
                        val buffer = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN)
                        val chunkX = buffer.getInt(0)
                        val chunkZ = buffer.getInt(4)
                        val hasDim = key.size >= 13
                        val dim = if (hasDim) buffer.getInt(8) else 0
                        val tagByte = if (hasDim && key.size >= 13) key[12].toInt() and 0xFF else key[8].toInt() and 0xFF
                        if (dim == dimensionId && tagByte == 0x31) {
                            set.add(Pair(chunkX, chunkZ))
                        }
                    } catch (e: Exception) {}
                }
            }
        } finally {
            iterator.close()
        }
        return set
    }

    override fun close() {
        try {
            db?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db = null
        }
    }

    companion object {

        fun categorizeKey(key: ByteArray, valueSize: Int, valueSample: ByteArray?): LevelDbRecord {
            val keyStr = try {
                String(key, Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }

            val isNbt = valueSample != null && valueSample.isNotEmpty() && (
                valueSample[0] == 0x0A.toByte() ||
                valueSample[0] == 0x09.toByte() ||
                valueSample[0] == 0x08.toByte()
            )

            if (keyStr == "~local_player") {
                return LevelDbRecord(key, keyStr, "本地房主玩家 (~local_player)", LevelDbCategory.PLAYER, valueSize = valueSize, isNbt = true)
            }
            if (keyStr.startsWith("player_server_")) {
                val uuid = keyStr.removePrefix("player_server_")
                return LevelDbRecord(key, keyStr, "服务器玩家 ($uuid)", LevelDbCategory.PLAYER, valueSize = valueSize, isNbt = true)
            }
            if (keyStr.startsWith("player_")) {
                val uuid = keyStr.removePrefix("player_")
                return LevelDbRecord(key, keyStr, "玩家数据 ($uuid)", LevelDbCategory.PLAYER, valueSize = valueSize, isNbt = true)
            }

            if (key.size >= 11 && keyStr.startsWith("actorprefix")) {
                val actorId = try {
                    if (key.size >= 19) ByteBuffer.wrap(key, 11, 8).order(ByteOrder.LITTLE_ENDIAN).long else null
                } catch (e: Exception) { null }
                val title = if (actorId != null) "实体生物 (ID: $actorId)" else "实体生物 ($keyStr)"
                return LevelDbRecord(key, keyStr, title, LevelDbCategory.ENTITY, valueSize = valueSize, isNbt = true)
            }
            if (keyStr.startsWith("digp")) {
                return LevelDbRecord(key, keyStr, "Digp 实体生物 ($keyStr)", LevelDbCategory.ENTITY, valueSize = valueSize, isNbt = true)
            }

            if (keyStr == "portals") {
                return LevelDbRecord(key, keyStr, "下界传送门索引 (portals)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
            }
            if (keyStr == "scoreboard") {
                return LevelDbRecord(key, keyStr, "计分板数据 (scoreboard)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
            }
            if (keyStr == "autonomousentities") {
                return LevelDbRecord(key, keyStr, "自主活动实体 (autonomousentities)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
            }
            if (keyStr == "Overworld" || keyStr == "Nether" || keyStr == "TheEnd") {
                return LevelDbRecord(key, keyStr, "维度数据 ($keyStr)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
            }
            if (keyStr.startsWith("map_")) {
                return LevelDbRecord(key, keyStr, "地图数据 (${keyStr.removePrefix("map_")})", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = isNbt)
            }
            if (keyStr.startsWith("village_") || keyStr.startsWith("VILLAGE_")) {
                return LevelDbRecord(key, keyStr, "村庄数据 ($keyStr)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
            }
            if (keyStr.startsWith("tickingarea_")) {
                return LevelDbRecord(key, keyStr, "常加载区域 ($keyStr)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
            }
            if (keyStr == "schedulerWT") {
                return LevelDbRecord(key, keyStr, "世界任务调度器 (schedulerWT)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = isNbt)
            }

            if (key.size in 9..14 || key.size in 4..8) {
                try {
                    val buffer = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN)
                    if (key.size >= 8) {
                        val chunkX = buffer.getInt(0)
                        val chunkZ = buffer.getInt(4)
                        val hasDim = key.size >= 13
                        val dim = if (hasDim) buffer.getInt(8) else 0
                        val tagByte = if (hasDim && key.size >= 13) key[12].toInt() and 0xFF else if (key.size >= 9) key[8].toInt() and 0xFF else -1

                        val dimName = when (dim) {
                            1 -> " [下界]"
                            2 -> " [末地]"
                            else -> ""
                        }

                        when (tagByte) {
                            0x31 -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimName 方块实体", LevelDbCategory.BLOCK_ENTITY, chunkX, chunkZ, dim, valueSize, isNbt = true, hasMultipleCompounds = true)
                            0x32 -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimName 实体数据", LevelDbCategory.ENTITY, chunkX, chunkZ, dim, valueSize, isNbt = true, hasMultipleCompounds = true)
                            0x2F -> {
                                val subIndex = if (hasDim && key.size >= 14) key[13].toInt() else if (key.size >= 10) key[9].toInt() else 0
                                return LevelDbRecord(key, keyStr, "子区块 [$chunkX, $chunkZ]$dimName (Y: $subIndex)", LevelDbCategory.CHUNK, chunkX, chunkZ, dim, valueSize, isNbt = false)
                            }
                            0x2C -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimName 版本信息", LevelDbCategory.CHUNK, chunkX, chunkZ, dim, valueSize, isNbt = false)
                            0x2D -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimName 2D 数据 (群系/高度)", LevelDbCategory.CHUNK, chunkX, chunkZ, dim, valueSize, isNbt = false)
                            0x33 -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimName 待处理方块刻", LevelDbCategory.CHUNK, chunkX, chunkZ, dim, valueSize, isNbt = true)
                            0x34 -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimName 额外方块数据", LevelDbCategory.CHUNK, chunkX, chunkZ, dim, valueSize, isNbt = isNbt)
                            0x35 -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimName 群系状态", LevelDbCategory.CHUNK, chunkX, chunkZ, dim, valueSize, isNbt = false)
                            0x36 -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimName 生成状态", LevelDbCategory.CHUNK, chunkX, chunkZ, dim, valueSize, isNbt = false)
                            0x37 -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimName 刷怪点数据", LevelDbCategory.CHUNK, chunkX, chunkZ, dim, valueSize, isNbt = isNbt)
                        }
                    }
                } catch (e: Exception) {

                }
            }

            val fallbackName = if (keyStr.isNotEmpty() && keyStr.all { it.isLetterOrDigit() || it in "_-~.: " }) {
                keyStr
            } else {
                "0x" + key.take(8).joinToString("") { "%02x".format(it) } + if (key.size > 8) "..." else ""
            }
            return LevelDbRecord(key, keyStr, fallbackName, LevelDbCategory.OTHER, valueSize = valueSize, isNbt = isNbt)
        }

        fun readBedrockNbt(bytes: ByteArray, isMultiple: Boolean = false): NbtFile {
            if (!isMultiple) {
                try {
                    val stream = ByteArrayInputStream(bytes)
                    return NbtIO.readNbtFile(stream, compressed = false, littleEndian = true, readHeaders = false)
                } catch (e: Exception) {

                    val stream = ByteArrayInputStream(bytes)
                    val rootCompound = NbtCompound()
                    var index = 0
                    while (stream.available() > 0) {
                        try {
                            val nbt = NbtIO.readNbtFile(stream, compressed = false, littleEndian = true, readHeaders = false)
                            val name = if (nbt.name.isNotEmpty()) nbt.name else "entry_$index"
                            rootCompound[name] = nbt.tag
                            index++
                        } catch (ex: Exception) {
                            break
                        }
                    }
                    if (index > 0) {
                        return NbtFile("Root", rootCompound)
                    }
                    throw e
                }
            } else {
                val rootCompound = NbtCompound()
                val stream = ByteArrayInputStream(bytes)
                var index = 0
                while (stream.available() > 0) {
                    try {
                        val nbt = NbtIO.readNbtFile(stream, compressed = false, littleEndian = true, readHeaders = false)
                        val name = if (nbt.name.isNotEmpty()) nbt.name else "entry_$index"
                        rootCompound[name] = nbt.tag
                        index++
                    } catch (e: Exception) {
                        break
                    }
                }
                if (index == 0) {

                    val singleStream = ByteArrayInputStream(bytes)
                    return NbtIO.readNbtFile(singleStream, compressed = false, littleEndian = true, readHeaders = false)
                }
                return NbtFile("Root", rootCompound)
            }
        }

        fun writeBedrockNbt(nbtFile: NbtFile, wasMultiple: Boolean = false): ByteArray {
            val baos = ByteArrayOutputStream()
            if (!wasMultiple) {
                NbtIO.writeNbtFile(baos, nbtFile, compressed = false, littleEndian = true)
            } else {
                val tag = nbtFile.tag
                if (tag is NbtCompound) {
                    tag.forEach { (key, childTag) ->
                        val singleFile = NbtFile("", childTag)
                        NbtIO.writeNbtFile(baos, singleFile, compressed = false, littleEndian = true)
                    }
                } else {
                    NbtIO.writeNbtFile(baos, nbtFile, compressed = false, littleEndian = true)
                }
            }
            return baos.toByteArray()
        }
    }
}
