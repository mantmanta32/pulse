package com.arena.chargepulse

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arena.chargepulse.ui.ChargePulseScreen
import com.arena.chargepulse.ui.theme.ChargePulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChargePulseTheme {
                val model: BatteryViewModel = viewModel()
                val state by model.state.collectAsStateWithLifecycle()
                ChargePulseScreen(state)
            }
        }
    }
}

data class BatteryState(
    val isPlugged: Boolean = false,
    val percentage: Int? = null,
    val voltage: Float? = null,
    val currentMa: Int? = null
)

class BatteryViewModel(application: Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val batteryManager = application.getSystemService(BatteryManager::class.java)
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(readBattery(null))
    val state: kotlinx.coroutines.flow.StateFlow<BatteryState> = _state

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _state.value = readBattery(intent)
        }
    }

    init {
        // ACTION_BATTERY_CHANGED is sticky, so this also gives an immediate real reading.
        ContextCompat.registerReceiver(
            application,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )?.let { _state.value = readBattery(it) }
    }

    private fun readBattery(intent: Intent?): BatteryState {
        val source = intent ?: return BatteryState(
            percentage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeIf { it in 0..100 },
            voltage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_VOLTAGE)
                .takeIf { it > 0 }?.div(1000f),
            currentMa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                .takeIf { it != Int.MIN_VALUE }?.div(1000)
        )
        val level = source.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = source.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else null
        val plugged = source.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val managerVoltage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_VOLTAGE)
        val voltage = managerVoltage.takeIf { it > 0 }?.div(1000f)
            ?: source.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }?.div(1000f)
        val rawCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return BatteryState(
            isPlugged = plugged,
            percentage = percentage,
            voltage = voltage,
            currentMa = rawCurrent.takeIf { it != Int.MIN_VALUE }?.div(1000)
        )
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(receiver)
        super.onCleared()
    }
}
