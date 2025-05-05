package com.example.stepcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.stepcoach.ui.theme.StepCoachTheme
import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    private var onStepCountChanged: ((Float) -> Unit)? = null
    private lateinit var db: thisDatabase
    private lateinit var stepsDao: StepsDao
    private var initialStepCount = 0f
    private var lastSavedDate = ""
    private val prefs by lazy { getSharedPreferences("step_prefs", MODE_PRIVATE) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = thisDatabase.getDatabase(this)
        stepsDao = db.stepsDao()
        val workRequest = PeriodicWorkRequestBuilder<Wakeup>(1, TimeUnit.DAYS)
            .setInitialDelay(calculateMidnightDelay(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "midnight_reset",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        initialStepCount = prefs.getFloat("initialStepCount", -1f)
        lastSavedDate = prefs.getString("lastSavedDate", "") ?: ""
        val lastKnownSteps = prefs.getFloat("stepsToday", 0f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        lifecycleScope.launch {
            val today = LocalDate.now().toString()
            lastSavedDate = today

            val todaySteps = stepsDao.getStepsForDate(today)
            if (todaySteps != null) {
            }
        }

    setContent {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "step_counter") {
            composable("step_counter") {
                StepCounterScreen(
                    initialSteps = lastKnownSteps,
                    onStepUpdate = { listener -> onStepCountChanged = listener },
                    onNavigateToHistory = { navController.navigate("step_history") }
                )
            }
            composable("step_history") {
                StepHistoryScreen(stepsDao, navController)
            }
        }
    }

}

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->

        }

    override fun onResume() {
        super.onResume()
        stepSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0]
            val today = LocalDate.now().toString()

            if (lastSavedDate != today) {

                val stepsYesterday = (totalSteps - initialStepCount).toInt()
                if (lastSavedDate.isNotEmpty()) {
                    lifecycleScope.launch {
                        stepsDao.insertOrUpdate(Steps(lastSavedDate, stepsYesterday))
                    }
                }

                initialStepCount = totalSteps
                lastSavedDate = today
                prefs.edit()
                    .putFloat("initialStepCount", initialStepCount)
                    .putString("lastSavedDate", lastSavedDate)
                    .apply()
            }

            if (initialStepCount < 0f) {
                initialStepCount = totalSteps
                prefs.edit().putFloat("initialStepCount", initialStepCount).apply()
            }

            val stepsToday = totalSteps - initialStepCount
            onStepCountChanged?.invoke(stepsToday)

            prefs.edit()
                .putFloat("stepsToday", stepsToday)
                .apply()
        }
    }

    private fun calculateMidnightDelay(): Long {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return midnight.timeInMillis - now.timeInMillis
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}





@Composable
fun StepCounterScreen(
    initialSteps: Float,
    onStepUpdate: (listener: (Float) -> Unit) -> Unit,
    onNavigateToHistory: () -> Unit
) {
    var steps by rememberSaveable { mutableStateOf(initialSteps) }

    onStepUpdate { newSteps ->
        steps = newSteps
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Steps", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = steps.toInt().toString(), style = MaterialTheme.typography.displayLarge)

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onNavigateToHistory) {
                Text(text = "Previous days")
            }
        }
    }
}


@Composable
fun StepHistoryScreen(stepsDao: StepsDao, navController: NavController) {
    val stepsList by stepsDao.getAllSteps().collectAsState(initial = emptyList())

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text("Back")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                items(stepsList) { record ->
                    Text(
                        text = "${record.date}: ${record.steps} steps",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Divider()
                }
            }
        }
    }
}