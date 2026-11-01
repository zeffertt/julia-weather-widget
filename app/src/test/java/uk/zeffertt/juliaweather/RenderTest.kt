package uk.zeffertt.juliaweather

import android.graphics.Bitmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws the widget with real Android graphics and saves PNGs to build/renders for checking by eye. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderTest {
    private fun save(o: Out, name: String, w: Int = 1080, h: Int = 585) {
        val dir = File("build/renders").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { Render.bitmap(o, w, h).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun render() {
        val dry = Out("cloudy", "Sun 1st · Sunset 16:33", "9° - 11°, feels 8°", "Cloudy all day", "Dry", "Dry", "Dry",
            "Dry", "Dry", "Dry", "none", "Dry all day", "Jeans or trousers", "Jumper + warm coat", "Scarf")
        save(dry, "dry")
        save(Out("rain", "Sun 1st · Sunset 16:33", "9° - 11°, feels 8°", "Cloudy, then rainy", "Dry", "Rain", "Showers",
            "Dry", "Rain", "Showers 60%", "raincoat", "Rain 12:00-14:00, showers 18:00-20:00 · Windy",
            "Jeans or trousers, boots", "Warm jumper under the raincoat", "Scarf"), "raincoat")
        save(Out("spells", "Mon 2nd · Sunset 16:31", "14° - 17°, feels 15°", "Sunny spells, then showery", "Dry",
            "Showers", "Dry", "Dry", "Showers 60%", "Dry", "umbrella", "Showers 12:00-14:00",
            "Trousers, or skirt with tights, waterproof shoes", "Long sleeves + light jacket", "Nothing extra"), "umbrella")
        save(dry.copy(icon = "sunny"), "icon_sunny", 676, 366)
        save(dry.copy(icon = "storm"), "icon_storm", 676, 366)
        save(dry.copy(icon = "snow"), "icon_snow", 676, 366)
        save(dry.copy(icon = "fog"), "icon_fog", 676, 366)
        save(dry.copy(icon = "night"), "icon_night", 676, 366)
        // Store preview image (no stale data)
        save(dry.copy(day = "Sun 1st · Sunset 16:33"), "widget_preview", 676, 366)
    }
}
