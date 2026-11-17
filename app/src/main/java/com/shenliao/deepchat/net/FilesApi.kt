package com.shenliao.deepchat.net

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * DeepSeek Files API：上传即解析，返回 file-api-xxx，聊天里用 file 块引用。
 * 文档（PDF/Word/PPT）解析最长 120s，所以超时给足。
 */
object FilesApi {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(330, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    fun endpointOf(baseUrl: String): String {
        val b = baseUrl.trim().trimEnd('/')
        return when {
            b.isEmpty() -> "https://api.deepseek.com/files"
            b.endsWith("/files") -> b
            else -> "$b/files"
        }
    }

    fun newUploadCall(
        baseUrl: String,
        apiKey: String,
        file: File,
        filename: String,
        mime: String,
        purpose: String,
    ): Call {
        val mediaType = mime.ifBlank { "application/octet-stream" }.toMediaTypeOrNull()
            ?: "application/octet-stream".toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("purpose", purpose) // 必须在 file 之前
            .addFormDataPart("file", filename, file.asRequestBody(mediaType))
            .build()
        val request = Request.Builder()
            .url(endpointOf(baseUrl))
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(body)
            .build()
        return client.newCall(request)
    }

    /** 返回 file_id；失败抛 [ApiException]。 */
    fun upload(
        baseUrl: String,
        apiKey: String,
        file: File,
        filename: String,
        mime: String,
        purpose: String,
    ): String {
        val call = newUploadCall(baseUrl, apiKey, file, filename, mime, purpose)
        call.execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, ChatApi.readableError(text))
            }
            val obj = runCatching { JSONObject(text) }
                .getOrElse { throw IOException("上传返回的不是 JSON：${text.take(160)}") }
            val id = obj.optString("id", "")
            if (id.isBlank()) throw IOException("上传成功但没拿到 file_id")
            return id
        }
    }

    fun delete(baseUrl: String, apiKey: String, fileId: String) {
        runCatching {
            val b = baseUrl.trim().trimEnd('/')
            val request = Request.Builder()
                .url("$b/files/$fileId")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .delete()
                .build()
            client.newBuilder().callTimeout(30, TimeUnit.SECONDS).build().newCall(request).execute()
                .use { it.body?.string() }
        }
    }
}
