package com.encore.chunkoid.ui.nbt

import br.com.gamemods.nbtmanipulator.*

data class NbtTreeNode(
    var key: String,
    var tag: NbtTag,
    val parentTag: NbtTag?,
    val depth: Int,
    var isExpanded: Boolean = false,
    val path: String,
    var listIndex: Int = -1
) {
    val isContainer: Boolean
        get() = tag is NbtCompound || tag is NbtList<*> || tag is NbtByteArray || tag is NbtIntArray || tag is NbtLongArray

    val tagTypeName: String
        get() = when (tag) {
            is NbtCompound -> "Compound"
            is NbtList<*> -> "List"
            is NbtByte -> "Byte"
            is NbtShort -> "Short"
            is NbtInt -> "Int"
            is NbtLong -> "Long"
            is NbtFloat -> "Float"
            is NbtDouble -> "Double"
            is NbtString -> "String"
            is NbtByteArray -> "ByteArray"
            is NbtIntArray -> "IntArray"
            is NbtLongArray -> "LongArray"
            is NbtEnd -> "End"
            else -> tag::class.java.simpleName.removePrefix("Nbt")
        }

    val displayValue: String
        get() = when (val t = tag) {
            is NbtCompound -> "(${t.size} entries)"
            is NbtList<*> -> "(${t.size} items)"
            is NbtByteArray -> "(${t.value.size} bytes)"
            is NbtIntArray -> "(${t.value.size} ints)"
            is NbtLongArray -> "(${t.value.size} longs)"
            is NbtByte -> "${t.signed}b"
            is NbtShort -> "${t.value}s"
            is NbtInt -> "${t.value}"
            is NbtLong -> "${t.value}L"
            is NbtFloat -> "${t.value}f"
            is NbtDouble -> "${t.value}d"
            is NbtString -> "\"${t.value}\""
            else -> t.toString()
        }
}
