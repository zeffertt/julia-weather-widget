package uk.zeffertt.juliaweather

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Fetches hourly forecasts and converts them to HA units, as HA's own integrations do. */
object Sources {

    private fun get(url: String, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            if (c.responseCode !in 200..299) throw RuntimeException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun time(s: String): ZonedDateTime = OffsetDateTime.parse(s).atZoneSameInstant(ZoneOffset.UTC)

    // ---------- Met.no (what HA's weather.forecast_home uses) ----------

    fun metNo(lat: Double, lon: Double): List<Fc> {
        val body = get(
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.2f&lon=%.2f".format(lat, lon),
            mapOf("User-Agent" to "julia-weather-widget/1.0 github.com/zeffertt/julia-weather-widget"),
        )
        return parseMetNo(body)
    }

    fun parseMetNo(body: String): List<Fc> {
        val ts = JSONObject(body).getJSONObject("properties").getJSONArray("timeseries")
        val out = mutableListOf<Fc>()
        for (i in 0 until ts.length()) {
            val e = ts.getJSONObject(i)
            val data = e.getJSONObject("data")
            val next = data.optJSONObject("next_1_hours") ?: continue // hourly part only
            val inst = data.getJSONObject("instant").getJSONObject("details")
            out += Fc(
                time = time(e.getString("time")),
                temp = inst.getDouble("air_temperature"),
                wind = inst.optDouble("wind_speed", 0.0) * 3.6,
                apparent = null, precip = next.optJSONObject("details")?.optDouble("precipitation_amount", 0.0) ?: 0.0,
                prob = null, gust = null,
                cond = metNoCondition(next.getJSONObject("summary").getString("symbol_code")),
            )
        }
        return out
    }

    fun metNoCondition(code: String): String {
        val night = code.endsWith("_night")
        val c = code.substringBefore("_")
        return when {
            c.contains("thunder") -> "lightning-rainy"
            c == "clearsky" -> if (night) "clear-night" else "sunny"
            c == "fair" || c == "partlycloudy" -> "partlycloudy"
            c == "cloudy" -> "cloudy"
            c == "fog" -> "fog"
            c.startsWith("heavyrain") -> "pouring"
            c.contains("rain") -> "rainy"
            c.contains("sleet") -> "snowy-rainy"
            c.contains("snow") -> "snowy"
            else -> "cloudy"
        }
    }

    // ---------- Met Office DataHub (what HA's weather.met_office_merton uses) ----------

    fun metOffice(lat: Double, lon: Double, key: String): List<Fc> {
        val body = get(
            "https://data.hub.api.metoffice.gov.uk/sitespecific/v0/point/hourly" +
                "?latitude=%.4f&longitude=%.4f&excludeParameterMetadata=true&includeLocationName=false".format(lat, lon),
            mapOf("apikey" to key, "accept" to "application/json"),
        )
        return parseMetOffice(body)
    }

    fun parseMetOffice(body: String): List<Fc> {
        val ts = JSONObject(body).getJSONArray("features").getJSONObject(0)
            .getJSONObject("properties").getJSONArray("timeSeries")
        val out = mutableListOf<Fc>()
        for (i in 0 until ts.length()) {
            val e = ts.getJSONObject(i)
            out += Fc(
                time = time(e.getString("time")),
                temp = e.getDouble("screenTemperature"),
                wind = e.optDouble("windSpeed10m", 0.0) * 3.6,
                apparent = if (e.has("feelsLikeTemperature")) e.getDouble("feelsLikeTemperature") else null,
                precip = e.optDouble("totalPrecipAmount", 0.0),
                prob = if (e.has("probOfPrecipitation")) e.getDouble("probOfPrecipitation") else null,
                gust = if (e.has("windGustSpeed10m")) e.getDouble("windGustSpeed10m") * 3.6 else null,
                cond = metOfficeCondition(e.optInt("significantWeatherCode", 7)),
            )
        }
        return out
    }

    fun metOfficeCondition(code: Int): String = when (code) {
        0 -> "clear-night"
        1 -> "sunny"
        2, 3 -> "partlycloudy"
        5, 6 -> "fog"
        7, 8 -> "cloudy"
        -1, 9, 10, 11, 12 -> "rainy"
        13, 14, 15 -> "pouring"
        16, 17, 18 -> "snowy-rainy"
        19, 20, 21 -> "hail"
        in 22..27 -> "snowy"
        28, 29 -> "lightning-rainy"
        30 -> "lightning"
        else -> "cloudy"
    }

    // ---------- cache ----------

    fun toJson(sources: List<List<Fc>>): String = JSONArray().apply {
        for (src in sources) put(JSONArray().apply {
            for (f in src) put(JSONObject().apply {
                put("t", f.time.toInstant().toString()); put("T", f.temp); put("w", f.wind)
                f.apparent?.let { put("a", it) }; put("mm", f.precip)
                f.prob?.let { put("p", it) }; f.gust?.let { put("g", it) }; put("c", f.cond)
            })
        })
    }.toString()

    fun fromJson(s: String): List<List<Fc>> {
        val a = JSONArray(s)
        return (0 until a.length()).map { i ->
            val src = a.getJSONArray(i)
            (0 until src.length()).map { j ->
                val o = src.getJSONObject(j)
                Fc(
                    time = time(o.getString("t")), temp = o.getDouble("T"), wind = o.getDouble("w"),
                    apparent = if (o.has("a")) o.getDouble("a") else null, precip = o.getDouble("mm"),
                    prob = if (o.has("p")) o.getDouble("p") else null,
                    gust = if (o.has("g")) o.getDouble("g") else null, cond = o.getString("c"),
                )
            }
        }
    }
}
