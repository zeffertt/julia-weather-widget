package uk.zeffertt.juliaweather

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** One-off setup: Met Office key and location. Opened from the app icon. */
class SettingsActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var preview: ImageView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val st = Store(this)
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val ink = Color.parseColor("#4a3a40")
        val rose = Color.parseColor("#c25a7c")
        val muted = Color.parseColor("#a8909a")

        fun label(t: String) = TextView(this).apply {
            text = t; setTextColor(rose); textSize = 13f; setPadding(0, px(16), 0, px(4))
        }
        fun field(v: String, type: Int) = EditText(this).apply {
            setText(v); inputType = type; setTextColor(ink); textSize = 15f
        }

        val key = field(st.apiKey, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        val numType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        val lat = field(st.lat.toString(), numType)
        val lon = field(st.lon.toString(), numType)

        preview = ImageView(this).apply { adjustViewBounds = true }
        status = TextView(this).apply { setTextColor(muted); textSize = 12f; setPadding(0, px(12), 0, 0) }

        val save = Button(this).apply {
            text = "Save and update"
            setOnClickListener {
                st.apiKey = key.text.toString()
                lat.text.toString().toDoubleOrNull()?.let { st.lat = it }
                lon.text.toString().toDoubleOrNull()?.let { st.lon = it }
                RefreshWorker.runNow(this@SettingsActivity)
                RefreshWorker.schedule(this@SettingsActivity)
                Toast.makeText(this@SettingsActivity, "Updating…", Toast.LENGTH_SHORT).show()
                status.postDelayed({ refresh() }, 4000)
            }
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(28), px(20), px(28))
            addView(TextView(this@SettingsActivity).apply {
                text = "Julia weather"; textSize = 22f; setTextColor(rose)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = "Add the widget: long-press the home screen > Widgets > Julia weather. Tap the widget to refresh."
                textSize = 13f; setTextColor(ink); setPadding(0, px(8), 0, px(12))
            })
            addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(label("Met Office API key (optional - Met.no works without it)"))
            addView(key)
            addView(label("Latitude"))
            addView(lat)
            addView(label("Longitude"))
            addView(lon)
            addView(save, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(16) })
            addView(status)
            gravity = Gravity.TOP
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#fffafb"))
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val w = resources.displayMetrics.widthPixels - (40 * resources.displayMetrics.density).toInt()
        preview.setImageBitmap(Render.bitmap(WeatherWidget.current(this), w, w))
        status.text = "Last update: " + Store(this).status
        WeatherWidget.renderAll(this)
    }
}
