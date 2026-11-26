package com.chinoisfacile.mandarin.domain.audio

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Arrays

object AudioPackHash {
    fun hashKey64(text: String): Long {
        val normalized = text.trim()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (digest[i].toLong() and 0xFFL)
        }
        return result
    }
}

data class RawPackEntry(
    val keyHash64: Long,
    val offset: Long,
    val length: Int,
)

data class PackedClipRef(
    val assetPath: String,
    val offset: Long,
    val length: Int,
)

class AudioPackIndex private constructor(
    private val packAssetPaths: List<String>,
    private val hashes: LongArray,
    private val packIndices: ByteArray,
    private val offsets: LongArray,
    private val lengths: IntArray,
) {
    val size: Int get() = hashes.size
    val isEmpty: Boolean get() = hashes.isEmpty()

    fun lookup(text: String): PackedClipRef? {
        if (text.isBlank() || hashes.isEmpty()) return null
        val key = AudioPackHash.hashKey64(text)
        val idx = Arrays.binarySearch(hashes, key)
        if (idx < 0) return null
        val packIdx = packIndices[idx].toInt() and 0xFF
        return PackedClipRef(
            assetPath = packAssetPaths[packIdx],
            offset = offsets[idx],
            length = lengths[idx],
        )
    }

    companion object {
        val EMPTY = AudioPackIndex(
            packAssetPaths = emptyList(),
            hashes = LongArray(0),
            packIndices = ByteArray(0),
            offsets = LongArray(0),
            lengths = IntArray(0),
        )

        fun fromPacks(packs: List<Pair<String, List<RawPackEntry>>>): AudioPackIndex {
            if (packs.isEmpty()) return EMPTY
            val paths = ArrayList<String>(packs.size.coerceAtMost(255))
            val combined = ArrayList<CombinedEntry>()

            for ((assetPath, entries) in packs) {
                if (entries.isEmpty()) continue
                if (paths.size >= 255) break
                val packIdx = paths.size
                paths += assetPath
                for (entry in entries) {
                    combined += CombinedEntry(
                        keyHash64 = entry.keyHash64,
                        packIdx = packIdx,
                        offset = entry.offset,
                        length = entry.length,
                    )
                }
            }

            if (combined.isEmpty()) return EMPTY
            combined.sortWith(compareBy<CombinedEntry> { it.keyHash64 }.thenBy { it.packIdx })

            val deduped = ArrayList<CombinedEntry>(combined.size)
            var lastHash = 0L
            var hasLast = false
            for (item in combined) {
                if (!hasLast || item.keyHash64 != lastHash) {
                    deduped += item
                    lastHash = item.keyHash64
                    hasLast = true
                }
            }

            val count = deduped.size
            val hashes = LongArray(count)
            val packIndices = ByteArray(count)
            val offsets = LongArray(count)
            val lengths = IntArray(count)

            for (i in 0 until count) {
                val item = deduped[i]
                hashes[i] = item.keyHash64
                packIndices[i] = item.packIdx.toByte()
                offsets[i] = item.offset
                lengths[i] = item.length
            }

            return AudioPackIndex(
                packAssetPaths = paths,
                hashes = hashes,
                packIndices = packIndices,
                offsets = offsets,
                lengths = lengths,
            )
        }
    }

    private data class CombinedEntry(
        val keyHash64: Long,
        val packIdx: Int,
        val offset: Long,
        val length: Int,
    )
}

object AudioPackCodec {
    val MAGIC = byteArrayOf('E'.code.toByte(), 'M'.code.toByte(), 'A'.code.toByte(), 'U'.code.toByte())
    const val FORMAT_VERSION = 1
    const val HEADER_SIZE_BYTES = 10
    const val INDEX_ENTRY_SIZE_BYTES = 20
    private const val MAX_ENTRIES = 500_000

    fun readIndex(input: InputStream): List<RawPackEntry>? {
        return try {
            val dis = DataInputStream(input)
            val magic = ByteArray(4)
            dis.readFully(magic)
            if (!magic.contentEquals(MAGIC)) return null

            val version = dis.readUnsignedShort()
            if (version != FORMAT_VERSION) return null

            val entryCount = dis.readInt()
            if (entryCount < 0 || entryCount > MAX_ENTRIES) return null

            val dataStartOffset = HEADER_SIZE_BYTES.toLong() +
                entryCount.toLong() * INDEX_ENTRY_SIZE_BYTES.toLong()
            val entries = ArrayList<RawPackEntry>(entryCount)
            for (i in 0 until entryCount) {
                val hash = dis.readLong()
                val offset = dis.readLong()
                val length = dis.readInt()
                if (offset < dataStartOffset || length <= 0) {
                    return null
                }
                entries += RawPackEntry(
                    keyHash64 = hash,
                    offset = offset,
                    length = length,
                )
            }
            entries
        } catch (_: EOFException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun writePack(
        output: OutputStream,
        entries: Map<String, ByteArray>,
    ) {
        val sorted = entries.entries
            .map { (text, bytes) -> Triple(AudioPackHash.hashKey64(text), text, bytes) }
            .sortedBy { it.first }

        val dos = DataOutputStream(output)
        dos.write(MAGIC)
        dos.writeShort(FORMAT_VERSION)
        dos.writeInt(sorted.size)

        var currentOffset = HEADER_SIZE_BYTES.toLong() +
            sorted.size.toLong() * INDEX_ENTRY_SIZE_BYTES.toLong()
        for ((hash, _, bytes) in sorted) {
            dos.writeLong(hash)
            dos.writeLong(currentOffset)
            dos.writeInt(bytes.size)
            currentOffset += bytes.size.toLong()
        }

        for ((_, _, bytes) in sorted) {
            dos.write(bytes)
        }
        dos.flush()
    }
}
