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
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    private var onStepCountChanged: ((Float) -> Unit)? = null
    private lateinit var db: thisDatabase
    private lateinit var stepsDao: StepsDao
    private var initialStepCount = 0f
    private var lastSavedDate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = thisDatabase.getDatabase(this)
        stepsDao = db.stepsDao()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

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
                    StepCounterScreen(  onStepUpdate = { listener -> onStepCountChanged = listener },onNavigateToHistory = {
                        navController.navigate("step_history")
                    })
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

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0]
            val today = LocalDate.now().toString()

            if (lastSavedDate != today) {

                val yesterday = lastSavedDate
                val stepsYesterday = (totalSteps - initialStepCount).toInt()

                if (yesterday.isNotEmpty()) {
                    lifecycleScope.launch {
                        stepsDao.insertOrUpdate(Steps(yesterday, stepsYesterday))
                    }
                }

                // new day steps
                initialStepCount = totalSteps
                lastSavedDate = today
            }

            val Steps = (totalSteps - initialStepCount)
            onStepCountChanged?.invoke(Steps)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun StepCounterScreen(onStepUpdate: (listener: (Float) -> Unit) -> Unit, onNavigateToHistory: () -> Unit) {
    var steps by remember { mutableStateOf(0f) }

    onStepUpdate { newSteps ->
        steps = newSteps
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Askelten määrä", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = steps.toInt().toString(), style = MaterialTheme.typography.displayLarge)

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onNavigateToHistory) {
                Text(text = "Edelliset päivät")
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
                Text("Takaisin pääsivulle")
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