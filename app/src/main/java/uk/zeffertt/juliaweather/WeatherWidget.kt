package uk.zeffertt.juliaweather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import java.time.ZoneId
import java.time.ZonedDateTime

class WeatherWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        renderAll(ctx)
        RefreshWorker.schedule(ctx)
        RefreshWorker.runNow(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) {
        renderAll(ctx)
    }

    override fun onEnabled(ctx: Context) = RefreshWorker.schedule(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) {
            renderAll(ctx)
            RefreshWorker.runNow(ctx)
        }
    }

    companion object {
        const val ACTION_REFRESH = "uk.zeffertt.juliaweather.REFRESH"

        fun current(ctx: Context): Out {
            val st = Store(ctx)
            val zone = ZoneId.systemDefault()
            val sources = st.cache?.let { runCatching { Sources.fromJson(it) }.getOrNull() } ?: emptyList()
            val out = Rules.compute(sources, ZonedDateTime.now(zone)) { Sun.sunset(it, st.lat, st.lon, zone) }
            return if (st.cache == null) out.copy(temp = "Updating…") else out
        }

        fun renderAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherWidget::class.java))
            if (ids.isEmpty()) return
            val out = current(ctx)
            val dens = ctx.resources.displayMetrics.density
            val tap = PendingIntent.getBroadcast(
                ctx, 0, Intent(ctx, WeatherWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            for (id in ids) {
                val o = mgr.getAppWidgetOptions(id)
                // Portrait phone: width = min width, height = max height
                val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320).takeIf { it > 0 } ?: 320
                val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180).takeIf { it > 0 } ?: 180
                // Cap the bitmap so it stays well inside the widget memory limit
                val wPx = minOf((wDp * dens).toInt(), 1100)
                val hPx = minOf((hDp * dens).toInt(), 1100)
                val views = RemoteViews(ctx.packageName, R.layout.widget).apply {
                    setImageViewBitmap(R.id.panel, Render.bitmap(out, wPx, hPx))
                    setOnClickPendingIntent(R.id.panel, tap)
                    setContentDescription(R.id.panel, "${out.day}. ${out.temp}. ${out.summary}. ${out.rain}")
                }
                mgr.updateAppWidget(id, views)
            }
        }
    }
}
