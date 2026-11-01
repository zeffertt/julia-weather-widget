package uk.zeffertt.juliaweather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesTest {
    private fun res(n: String) = javaClass.getResource("/$n")!!.readText()

    @Test fun metNoLive() {
        val f = Sources.parseMetNo(res("metno_sample.json"))
        assertTrue("hourly entries: ${f.size}", f.size > 40)
        assertTrue(f.all { it.prob == null && it.gust == null && it.apparent == null }) // as HA's Met.no
        assertTrue(f.all { it.temp in -30.0..45.0 && it.wind >= 0 })
        assertTrue(f.map { it.cond }.all { it in setOf("sunny", "clear-night", "partlycloudy", "cloudy", "fog", "rainy", "pouring", "lightning-rainy", "snowy", "snowy-rainy") })
    }

    @Test fun metOffice() {
        val f = Sources.parseMetOffice(res("metoffice_sample.json"))
        assertEquals(2, f.size)
        assertEquals(11.2, f[0].gust!!, 0.01)       // 3.11 m/s -> km/h
        assertEquals(6.84, f[0].wind, 0.01)
        assertEquals("cloudy", f[0].cond)
        assertEquals("rainy", f[1].cond)
        assertEquals(62.0, f[1].prob!!, 0.0)
        assertEquals(0.6, f[1].precip, 0.0)
    }

    @Test fun metNoSymbols() {
        assertEquals("partlycloudy", Sources.metNoCondition("fair_day"))
        assertEquals("clear-night", Sources.metNoCondition("clearsky_night"))
        assertEquals("pouring", Sources.metNoCondition("heavyrainshowers_day"))
        assertEquals("rainy", Sources.metNoCondition("lightrain"))
        assertEquals("lightning-rainy", Sources.metNoCondition("rainandthunder"))
        assertEquals("snowy-rainy", Sources.metNoCondition("lightsleetshowers_night"))
    }
}
