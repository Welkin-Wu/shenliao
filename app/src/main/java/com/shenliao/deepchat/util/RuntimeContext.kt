package com.shenliao.deepchat.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 把「现在几点」和「大概在哪」塞进系统提示。
 *
 * 模型自己没有时钟也没有定位，之前问「今天几号」「现在几点」「我在哪」它只能瞎猜，
 * 所以每次请求现算一份时间；位置需要权限，拿到后缓存到本地，按城市级精度告诉模型。
 */
object RuntimeContext {

    private const val PREFS = "runtime_context"
    private const val K_LAT = "lat"
    private const val K_LON = "lon"
    private const val K_ACC = "acc"
    private const val K_PLACE = "place"
    private const val K_AT = "at"

    fun hasLocationPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 现算的时间行（不需要任何权限） */
    fun timeLine(): String {
        val now = Calendar.getInstance()
        val dateTime = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(now.time)
        val week = SimpleDateFormat("EEEE", Locale.CHINA).format(now.time)
        val zone = TimeZone.getDefault().id
        return "当前时间是 $dateTime，$week，设备时区 $zone。" +
            "用户问到今天的日期、现在几点、星期几时，一律以此为准，不要凭训练数据猜。"
    }

    /** 位置行：有缓存就用缓存（城市级），没有返回 null */
    fun locationLine(context: Context): String? {
        if (!hasLocationPermission(context)) return null
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = sp.getFloat(K_LAT, 0f)
        val lon = sp.getFloat(K_LON, 0f)
        if (lat == 0f && lon == 0f) return null
        val place = sp.getString(K_PLACE, null)
        val acc = sp.getFloat(K_ACC, 0f)
        val placeText = if (!place.isNullOrBlank()) "（$place）" else ""
        val accText = if (acc > 0f) "，精度约 ${acc.toInt()} 米" else ""
        return "用户大概在纬度 ${"%.2f".format(lat)}、经度 ${"%.2f".format(lon)}$placeText$accText。" +
            "只用于和本地有关的回答（天气、附近、当地习俗等），不要主动强调你在追踪位置。"
    }

    /** 组装成一段 system 上下文；两个开关都关时返回 null */
    fun build(context: Context, withTime: Boolean, withLocation: Boolean): String? {
        val parts = mutableListOf<String>()
        if (withTime) parts += timeLine()
        if (withLocation) locationLine(context)?.let { parts += it }
        if (parts.isEmpty()) return null
        return parts.joinToString("\n")
    }

    /**
     * 请求一次定位并缓存。需要已授予权限。
     * 串行、超时 8 秒，拿到第一个 fix 就取消监听。
     */
    @SuppressLint("MissingPermission")
    fun refreshLocation(context: Context, onDone: (Boolean) -> Unit = {}) {
        if (!hasLocationPermission(context)) {
            onDone(false)
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            onDone(false)
            return
        }

        // 先看看有没有现成的最后一次位置
        val cached = runCatching {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }.getOrNull()
        if (cached != null) {
            save(context, cached)
        }

        val providers = listOfNotNull(
            LocationManager.NETWORK_PROVIDER.takeIf { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) },
            LocationManager.GPS_PROVIDER.takeIf { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) },
        )
        if (providers.isEmpty()) {
            onDone(cached != null)
            return
        }

        var finished = false
        lateinit var listener: LocationListener

        fun finish(loc: Location?) {
            if (finished) return
            finished = true
            runCatching { lm.removeUpdates(listener) }
            if (loc != null) save(context, loc)
            onDone(loc != null || cached != null)
        }

        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = finish(location)
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        runCatching {
            providers.forEach { p ->
                lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
            }
        }.onFailure { finish(null) }

        android.os.Handler(Looper.getMainLooper()).postDelayed({ finish(null) }, 8000)
    }

    private fun save(context: Context, location: Location) {
        val place = runCatching {
            @Suppress("DEPRECATION")
            val geocoder = android.location.Geocoder(context, Locale.CHINA)
            if (android.location.Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.let { listOfNotNull(it.locality, it.subAdminArea, it.adminArea).firstOrNull() }
            } else {
                null
            }
        }.getOrNull()

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(K_LAT, location.latitude.toFloat())
            .putFloat(K_LON, location.longitude.toFloat())
            .putFloat(K_ACC, location.accuracy)
            .putLong(K_AT, System.currentTimeMillis())
            .apply {
                if (place != null) putString(K_PLACE, place)
            }
            .apply()
    }
}
