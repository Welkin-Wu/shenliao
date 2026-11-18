package com.shenliao.deepchat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * 对话与图片的本地存储。全部落在应用私有目录，不上传任何第三方服务器。
 */
class ConversationStore(private val ctx: Context) {

    private val file: File get() = File(ctx.filesDir, "conversations.json")

    private val attachDir: File
        get() = File(ctx.filesDir, "attachments").also { if (!it.exists()) it.mkdirs() }

    fun load(): List<Conversation> = try {
        if (!file.exists()) emptyList()
        else {
            val arr = JSONArray(file.readText())
            // 逐条解析：某一条坏数据只丢那一条，不至于整个历史都读不出来
            (0 until arr.length()).mapNotNull { i ->
                runCatching { arr.optJSONObject(i)?.let(Conversation::fromJson) }.getOrNull()
            }
        }
    } catch (t: Throwable) {
        emptyList()
    }

    fun clearCache() {
        runCatching { ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
    }

    fun save(list: List<Conversation>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            val tmp = File(ctx.filesDir, "conversations.json.tmp")
            tmp.writeText(arr.toString())
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } catch (_: Throwable) {
            // 存储失败不应影响对话本身
        }
    }

    fun clearAll() {
        try {
            if (file.exists()) file.delete()
            attachDir.listFiles()?.forEach { it.delete() }
        } catch (_: Throwable) {
        }
    }

    /**
     * 把用户选的图片复制压缩进私有目录，返回可长期引用的附件。
     * 同时处理 EXIF 旋转，避免模型看到的是歪的。
     */
    fun importImage(uri: Uri, maxSide: Int = 1400, quality: Int = 86): Attachment? {
        return try {
            val resolver = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null

            val rotation = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    when (ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            }.getOrDefault(0f)

            val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
            if (rotation != 0f || scale < 1f) {
                val m = Matrix().apply {
                    if (rotation != 0f) postRotate(rotation)
                    if (scale < 1f) postScale(scale, scale)
                }
                val scaled = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (scaled != bmp) bmp.recycle()
                bmp = scaled
            }

            val out = File(attachDir, "img_${UUID.randomUUID()}.jpg")
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            bmp.recycle()
            Attachment(kind = Attachment.KIND_IMAGE, path = out.absolutePath, mime = "image/jpeg")
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 文档（PDF / Word / PPT / txt …）复制进私有目录，随后由 FilesApi 上传解析。
     */
    fun importDocument(uri: Uri, displayName: String, mime: String): Attachment? {
        return try {
            val safeName = displayName.ifBlank { "file" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(80)
            val out = File(attachDir, "doc_${UUID.randomUUID().toString().take(8)}_$safeName")
            val copied = ctx.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied || !out.exists() || out.length() == 0L) {
                out.delete()
                return null
            }
            Attachment(
                kind = Attachment.KIND_DOC,
                path = out.absolutePath,
                mime = mime.ifBlank { "application/octet-stream" },
                name = displayName.ifBlank { out.name },
                sizeBytes = out.length(),
                state = Attachment.STATE_UPLOADING,
            )
        } catch (t: Throwable) {
            null
        }
    }
}
