package uk.zeffertt.juliaweather

import android.content.Context

/** Settings and the last good forecast, kept on the phone only. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("julia_weather", Context.MODE_PRIVATE)

    var apiKey: String
        get() = p.getString("api_key", "") ?: ""
        set(v) = p.edit().putString("api_key", v.trim()).apply()

    // Default: Tooting
    var lat: Double
        get() = p.getFloat("lat", 51.43f).toDouble()
        set(v) = p.edit().putFloat("lat", v.toFloat()).apply()

    var lon: Double
        get() = p.getFloat("lon", -0.17f).toDouble()
        set(v) = p.edit().putFloat("lon", v.toFloat()).apply()

    var cache: String?
        get() = p.getString("cache", null)
        set(v) = p.edit().putString("cache", v).apply()

    var lastFetch: Long
        get() = p.getLong("last_fetch", 0)
        set(v) = p.edit().putLong("last_fetch", v).apply()

    var status: String
        get() = p.getString("status", "Not updated yet") ?: ""
        set(v) = p.edit().putString("status", v).apply()
}
