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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arena.chargepulse.ui.ChargePulseScreen
import com.arena.chargepulse.ui.theme.ChargePulseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val currentA: Float? = null
)

class BatteryViewModel(application: Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val batteryManager = application.getSystemService(BatteryManager::class.java)
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(readBattery(null))
    val state: kotlinx.coroutines.flow.StateFlow<BatteryState> = _state

    // Latest sticky broadcast; level/voltage extras come from it.
    private var lastIntent: Intent? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lastIntent = intent
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
        )?.let {
            lastIntent = it
            _state.value = readBattery(it)
        }
        // The instantaneous current changes far more often than ACTION_BATTERY_CHANGED fires,
        // so poll the battery manager for a live readout.
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                _state.value = readBattery(lastIntent)
            }
        }
    }

    private fun readBattery(intent: Intent?): BatteryState {
        val source = intent ?: return BatteryState(
            percentage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeIf { it in 0..100 },
            currentA = readCurrentA()
        )
        val level = source.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = source.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else null
        val plugged = source.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val voltage = source.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }?.div(1000f)
        return BatteryState(
            isPlugged = plugged,
            percentage = percentage,
            voltage = voltage,
            currentA = readCurrentA()
        )
    }

    // BATTERY_PROPERTY_CURRENT_NOW is reported in microamps; keep the fraction (µA precision)
    // instead of truncating, so the amper readout can show live decimals.
    private fun readCurrentA(): Float? =
        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Int.MIN_VALUE }?.div(1_000_000f)

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(receiver)
        super.onCleared()
    }
}
