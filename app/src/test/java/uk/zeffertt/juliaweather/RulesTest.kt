package uk.zeffertt.juliaweather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Expected values were rendered by HA itself (render_template with the automation's own template,
 * same forecast input and a fixed "now"), so these tests prove the port matches HA.
 */
class RulesTest {
    private val london = ZoneId.of("Europe/London")

    private fun load(): MutableMap<String, MutableList<Fc>> {
        val m = linkedMapOf("metno" to mutableListOf<Fc>(), "metoffice" to mutableListOf())
        javaClass.getResource("/ha_forecast_2026-11-01.csv")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { l ->
                val c = l.split(",")
                fun d(i: Int) = c[i].takeIf { it.isNotEmpty() }?.toDouble()
                m[c[0]]!! += Fc(OffsetDateTime.parse(c[1]).atZoneSameInstant(london), d(2)!!, d(3) ?: 0.0, d(4),
                    d(5) ?: 0.0, d(6), d(7), c[8])
            }
        return m
    }

    private fun MutableList<Fc>.set(dt: String, f: (Fc) -> Fc) {
        val i = indexOfFirst { it.time.toOffsetDateTime().toString().startsWith(dt) }
        if (i >= 0) this[i] = f(this[i])
    }

    private fun run(src: Map<String, List<Fc>>, now: String): Out =
        Rules.compute(listOf(src["metno"]!!, src["metoffice"]!!), ZonedDateTime.parse(now).withZoneSameInstant(london)) {
            LocalTime.of(16, 33)
        }

    private fun check(exp: Out, got: Out) {
        assertEquals(exp.day.substringBefore(" · Sunset"), got.day.substringBefore(" · Sunset"))
        assertEquals(exp.copy(day = ""), got.copy(day = ""))
    }

    @Test fun scenarioA_now() = check(
        Out("cloudy", "Sun 1st", "9° - 11°, feels 8°", "Cloudy all day", "Dry", "Dry", "Dry", "Dry", "Dry", "Dry",
            "none", "Dry all day", "Jeans or trousers", "Jumper + warm coat", "Scarf"),
        run(load(), "2026-11-01T09:06:00Z"))

    @Test fun scenarioB_tomorrowAfter1930() = check(
        Out("spells", "Mon 2nd", "9° - 12°, feels 6°", "Sunny spells all day", "Dry", "Dry", "Dry", "Dry", "Dry", "Dry",
            "none", "Dry all day · Slightly windy", "Thick trousers or jeans, tights under", "Jumper + warm coat", "Scarf"),
        run(load(), "2026-11-01T19:45:00Z"))

    @Test fun scenarioC_rainAndWind() {
        val s = load()
        s["metno"]!!.set("2026-11-01T12") { it.copy(precip = 0.5) }
        s["metno"]!!.set("2026-11-01T13") { it.copy(precip = 2.5, cond = "pouring") }
        s["metoffice"]!!.set("2026-11-01T18") { it.copy(prob = 60.0) }
        s["metoffice"]!!.set("2026-11-01T19") { it.copy(prob = 85.0) }
        s["metoffice"]!!.set("2026-11-01T20") { it.copy(gust = 50.0) }
        check(
            Out("rain", "Sun 1st", "9° - 11°, feels 8°", "Cloudy all day", "Dry", "Rain", "Rain", "Dry", "Rain", "Rain 85%",
                "raincoat", "Rain 12:00-14:00, rain 18:00-20:00 · Windy", "Jeans or trousers, boots",
                "Warm jumper under the raincoat", "Scarf"),
            run(s, "2026-11-01T08:00:00Z"))
    }

    @Test fun scenarioD_coldAndSnow() {
        val s = load()
        for (k in s.keys) s[k] = s[k]!!.map { it.copy(temp = it.temp - 8, apparent = it.apparent?.minus(8)) }.toMutableList()
        s["metoffice"]!!.set("2026-11-01T08") { it.copy(prob = 55.0) } // no 08:00 entry, as in HA
        s["metno"]!!.set("2026-11-01T15") { it.copy(cond = "snowy") }
        check(
            Out("snow", "Sun 1st", "1° - 3°, feels -2°", "Cloudy all day", "Dry", "Dry", "Dry", "Dry", "Dry", "Dry",
                "none", "Dry all day", "Thick trousers or jeans, tights under", "Thick jumper + warm coat",
                "Scarf, hat, gloves"),
            run(s, "2026-11-01T06:30:00Z"))
    }

    @Test fun sunsetMatchesHa() {
        // HA's sun.sun said 16:33 today
        val t = Sun.sunset(LocalDate.of(2026, 11, 1), 51.43, -0.17, london)!!
        val diff = Duration.between(LocalTime.of(16, 33), t).abs().toMinutes()
        assertTrue("got $t", diff <= 2)
    }

    @Test fun cacheRoundTrip() {
        val s = load()
        val src = listOf(s["metno"]!!, s["metoffice"]!!)
        val back = Sources.fromJson(Sources.toJson(src))
        val now = ZonedDateTime.parse("2026-11-01T09:06:00Z").withZoneSameInstant(london)
        assertEquals(Rules.compute(src, now) { null }, Rules.compute(back, now) { null })
    }
}
