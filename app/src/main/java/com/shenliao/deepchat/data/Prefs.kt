package com.shenliao.deepchat.data

import android.content.Context
import androidx.core.content.edit

class Prefs(ctx: Context) {

    private val sp = ctx.getSharedPreferences("deepchat_settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        preset = sp.getString("preset", Settings.PRESET_DEEPSEEK) ?: Settings.PRESET_DEEPSEEK,
        baseUrl = sp.getString("baseUrl", Settings.DEFAULT_BASE_URL) ?: Settings.DEFAULT_BASE_URL,
        apiKey = sp.getString("apiKey", "") ?: "",
        model = sp.getString("model", Settings.DEFAULT_MODEL) ?: Settings.DEFAULT_MODEL,
        systemPrompt = sp.getString("systemPrompt", "") ?: "",
        thinking = sp.getBoolean("thinking", true),
        effort = sp.getString("effort", "high") ?: "high",
        temperature = sp.getFloat("temperature", 0.7f),
        maxTokens = sp.getInt("maxTokens", 0),
        docMode = sp.getString("docMode", Settings.DOC_MODE_TEXT) ?: Settings.DOC_MODE_TEXT,
    )

    fun save(s: Settings) {
        sp.edit {
            putString("preset", s.preset)
            putString("baseUrl", s.baseUrl)
            putString("apiKey", s.apiKey)
            putString("model", s.model)
            putString("systemPrompt", s.systemPrompt)
            putBoolean("thinking", s.thinking)
            putString("effort", s.effort)
            putFloat("temperature", s.temperature)
            putInt("maxTokens", s.maxTokens)
            putString("docMode", s.docMode)
        }
    }
}
