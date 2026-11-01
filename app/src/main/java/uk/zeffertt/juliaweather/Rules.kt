package uk.zeffertt.juliaweather

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

/** One hourly forecast entry, in HA units (C, km/h, mm, %). */
data class Fc(
    val time: ZonedDateTime,
    val temp: Double,
    val wind: Double,
    val apparent: Double?,
    val precip: Double,
    val prob: Double?,
    val gust: Double?,
    val cond: String,
)

data class Out(
    val icon: String, val day: String, val temp: String, val summary: String,
    val m: String, val l: String, val e: String,
    val mt: String, val lt: String, val et: String,
    val verdict: String, val rain: String,
    val bottom: String, val top: String, val extras: String,
)

/**
 * Straight port of the HA automation julia_weather_outfit (1 Nov 2026).
 * Keep in step with HA: the thresholds are all in this file.
 * sources: Met.no first, then Met Office (same order as HA).
 */
object Rules {

    private data class E(val hr: Int, val t: Double, val fl: Double, val mm: Double, val p: Double,
                         val w: Double, val g: Double, val c: String)

    private data class H(val hr: Int, val t: Double, val fl: Double, val mm: Double, val w: Double,
                         val g: Double, val p: Double, val c: String, val wet: Boolean, val rain: Boolean)

    // Jinja's round is Python's round: halves go to the even number
    private fun r(x: Double) = round(x).toInt()

    private val names = mapOf(
        "sunny" to "Sunny", "clear-night" to "Clear", "partlycloudy" to "Sunny spells", "cloudy" to "Cloudy",
        "fog" to "Foggy", "rainy" to "Showery", "pouring" to "Rainy", "lightning" to "Stormy",
        "lightning-rainy" to "Stormy", "snowy" to "Snowy", "snowy-rainy" to "Sleet", "hail" to "Hail",
        "windy" to "Windy", "windy-variant" to "Windy", "exceptional" to "Unsettled",
    )

    private val iconMap = mapOf(
        "sunny" to "sunny", "clear-night" to "night", "partlycloudy" to "spells", "cloudy" to "cloudy",
        "fog" to "fog", "rainy" to "rain", "pouring" to "rain", "lightning" to "storm",
        "lightning-rainy" to "storm", "snowy" to "snow", "snowy-rainy" to "snow", "hail" to "snow",
        "windy" to "cloudy", "windy-variant" to "cloudy", "exceptional" to "cloudy",
    )

    private fun cap(s: String) = if (s.isEmpty()) s else s[0].uppercase() + s.substring(1).lowercase()

    /** Most common value; ties go to the one seen first (as in the HA template). */
    private fun mostCommon(cs: List<String>): String {
        var best = ""; var n = 0
        for (u in cs.distinct()) {
            val k = cs.count { it == u }
            if (k > n) { best = u; n = k }
        }
        return best
    }

    fun compute(sources: List<List<Fc>>, now: ZonedDateTime, sunset: (LocalDate) -> LocalTime?): Out {
        val zone = now.zone
        val tmr = now.hour * 60 + now.minute >= 1170
        val d = if (tmr) now.toLocalDate().plusDays(1) else now.toLocalDate()
        val dd = d.dayOfMonth
        val suf = if (dd in 11..13) "th" else when (dd % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        var dl = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK).take(3) + " " + dd + suf

        val ents = mutableListOf<E>()
        for (src in sources) for (f in src) {
            val t = f.time.withZoneSameInstant(zone)
            if (t.toLocalDate() == d && t.hour in 7..21) {
                val T = f.temp
                val v = f.wind
                val wc = if (T <= 10 && v > 4.8) 13.12 + 0.6215 * T - 11.37 * v.pow(0.16) + 0.3965 * T * v.pow(0.16) else T
                val fl = minOf(wc, f.apparent ?: wc)
                ents += E(t.hour, T, fl, f.precip, f.prob ?: 0.0, v, f.gust ?: v, f.cond)
            }
        }

        val hrs = ents.map { it.hr }.distinct().sorted().map { hr ->
            val sel = ents.filter { it.hr == hr }
            val mm = sel.maxOf { it.mm }
            val p = sel.maxOf { it.p }
            H(hr, sel.sumOf { it.t } / sel.size, sel.minOf { it.fl }, mm, sel.maxOf { it.w }, sel.maxOf { it.g },
                p, sel[0].c, mm >= 0.2 || p >= 50, mm >= 1.5 || p >= 80)
        }

        val ss = mutableListOf<String>(); val st = mutableListOf<String>(); var keywet = false
        for (hs in listOf(listOf(7, 8, 9), listOf(12, 13), listOf(18, 19, 20, 21))) {
            val sel = hrs.filter { it.hr in hs }
            val s = when {
                sel.isEmpty() -> "Passed"
                sel.any { it.rain } -> "Rain"
                sel.any { it.wet } -> "Showers"
                else -> "Dry"
            }
            if (s == "Rain" || s == "Showers") keywet = true
            ss += s
            val pp = if (sel.isNotEmpty()) sel.maxOf { it.p } else 0.0
            st += s + if (pp >= 20 && (s == "Showers" || s == "Rain")) " ${r(pp)}%" else ""
        }

        val sst = sunset(d)
        if (sst != null) dl += " · Sunset " + "%02d:%02d".format(sst.hour, sst.minute)

        if (hrs.isEmpty()) {
            return Out("cloudy", dl, "No forecast", "", "Passed", "Passed", "Passed", "Passed", "Passed", "Passed",
                "none", "-", "-", "-", "-")
        }

        val temps = hrs.map { it.t }
        val hi = temps.max(); val lo = temps.min()
        val day = hrs.filter { it.hr <= 19 }
        val base = day.ifEmpty { hrs }
        val feels = base.minOf { it.fl }
        val wet = hrs.filter { it.wet }
        val heavy = hrs.any { it.mm >= 2 }
        val windywet = wet.any { it.w >= 30 }
        val most = wet.size >= max(4, ceil(hrs.size / 2.0).toInt())
        val raincoat = most || heavy || windywet || (keywet && hi <= 10)

        // Group wet hours into spells
        val spells = mutableListOf<String>()
        var s0 = -1; var e0 = -1; var hv = false
        fun close() { if (s0 >= 0) spells += (if (hv) "rain" else "showers") + " %02d:00-%02d:00".format(s0, e0 + 1) }
        for (x in wet) {
            if (x.hr == e0 + 1) { e0 = x.hr; hv = hv || x.rain }
            else { close(); s0 = x.hr; e0 = x.hr; hv = x.rain }
        }
        close()

        val maxg = hrs.maxOf { it.g }
        val gusty = maxg >= 30
        val windlbl = if (maxg >= 60) "Very windy" else if (maxg >= 45) "Windy" else "Slightly windy"
        var rl = spells.take(if (gusty) 2 else 3).joinToString(", ")
        rl = if (rl.isNotEmpty()) rl[0].uppercase() + rl.substring(1) else "Dry all day"
        if (gusty) rl += " · $windlbl"

        val parts = listOf(hrs.filter { it.hr <= 12 }, hrs.filter { it.hr > 12 })
            .filter { it.isNotEmpty() }
            .map { seg -> mostCommon(seg.map { it.c }).let { names[it] ?: cap(it) } }
        val sm = when {
            parts.size == 2 && parts[0] != parts[1] -> parts[0] + ", then " + parts[1].lowercase()
            parts.size == 2 -> parts[0] + " all day"
            parts.size == 1 -> parts[0]
            else -> ""
        }

        val h = feels - 2
        var bt = when {
            h <= 5 -> "Thick trousers or jeans, tights under"
            h <= 11 -> "Jeans or trousers"
            h <= 16 -> "Trousers, or skirt with tights"
            h <= 20 -> "Light trousers, skirt or dress"
            else -> "Shorts, skirt or dress"
        }
        if (wet.isNotEmpty()) bt += if (h <= 11) ", boots" else ", waterproof shoes"
        val tp = when {
            raincoat -> if (h <= 8) "Warm jumper under the raincoat" else if (h <= 17) "Jumper under the raincoat" else "Light top under the raincoat"
            h <= 3 -> "Thick jumper + warm coat"
            h <= 8 -> "Jumper + warm coat"
            h <= 12 -> "Jumper + light coat"
            h <= 17 -> "Long sleeves + light jacket"
            h <= 21 -> "T-shirt, layer for later"
            else -> "Light top"
        }
        val ex = if (h <= 3) "Scarf, hat, gloves" else if (h <= 8) "Scarf" else "Nothing extra"

        val allc = hrs.map { it.c }
        val ic = when {
            allc.any { it == "lightning" || it == "lightning-rainy" } -> "storm"
            allc.any { it == "snowy" || it == "snowy-rainy" || it == "hail" } -> "snow"
            wet.isNotEmpty() -> "rain"
            else -> iconMap[mostCommon(allc)] ?: "cloudy"
        }

        return Out(
            icon = ic, day = dl,
            temp = "${r(lo)}° - ${r(hi)}°, feels ${r(feels)}°",
            summary = sm, m = ss[0], l = ss[1], e = ss[2], mt = st[0], lt = st[1], et = st[2],
            verdict = if (raincoat) "raincoat" else if (keywet) "umbrella" else "none",
            rain = rl, bottom = bt, top = tp, extras = ex,
        )
    }
}
