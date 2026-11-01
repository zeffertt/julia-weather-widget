package uk.zeffertt.juliaweather

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Sunset time (local), standard almanac method, about a minute accurate. */
object Sun {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun norm(x: Double, m: Double) = ((x % m) + m) % m

    fun sunset(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): LocalTime? {
        val n = date.dayOfYear
        val lngHour = lon / 15
        val t = n + (18 - lngHour) / 24
        val m = 0.9856 * t - 3.289
        val l = norm(m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634, 360.0)
        var ra = norm(deg(atan(0.91764 * tan(rad(l)))), 360.0)
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(90.833)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = deg(acos(cosH)) / 15
        val tt = h + ra - 0.06571 * t - 6.622
        val ut = norm(tt - lngHour, 24.0)
        val secs = (ut * 3600).toLong()
        val utc = date.atStartOfDay(ZoneOffset.UTC).plusSeconds(secs)
        return utc.withZoneSameInstant(zone).toLocalTime()
    }
}
