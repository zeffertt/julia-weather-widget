package uk.zeffertt.juliaweather

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Fetches both forecasts every 30 minutes (same as the HA automation) and redraws the widget. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val st = Store(applicationContext)
        val got = mutableListOf<List<Fc>>()
        val notes = mutableListOf<String>()
        runCatching { Sources.metNo(st.lat, st.lon) }
            .onSuccess { got += it; notes += "Met.no OK" }.onFailure { notes += "Met.no failed" }
        if (st.apiKey.isNotBlank()) {
            runCatching { Sources.metOffice(st.lat, st.lon, st.apiKey) }
                .onSuccess { got += it; notes += "Met Office OK" }
                .onFailure { notes += "Met Office failed (${it.message})" }
        } else notes += "Met Office off (no key)"

        val time = SimpleDateFormat("EEE HH:mm", Locale.UK).format(Date())
        if (got.isNotEmpty()) {
            st.cache = Sources.toJson(got)
            st.lastFetch = System.currentTimeMillis()
        }
        st.status = "$time · " + notes.joinToString(" · ")
        WeatherWidget.renderAll(applicationContext)
        return if (got.isNotEmpty()) Result.success() else Result.retry()
    }

    companion object {
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES).setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("periodic", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("now", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
