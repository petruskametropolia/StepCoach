package com.example.stepcoach

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate

class Wakeup (
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val stepsToday = prefs.getFloat("stepsToday", 0f)

        val db = thisDatabase.getDatabase(applicationContext)
        val dao = db.stepsDao()

        dao.insertOrUpdate(Steps(today, stepsToday.toInt()))

        prefs.edit()
            .remove("stepsToday")
            .putString("lastSavedDate", today)
            .apply()

        return Result.success()
    }
}