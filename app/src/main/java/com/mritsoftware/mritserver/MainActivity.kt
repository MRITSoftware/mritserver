package com.mritsoftware.mritserver

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.mritsoftware.mritserver.adapter.DeviceAdapter
import com.mritsoftware.mritserver.model.TuyaDevice
import com.mritsoftware.mritserver.service.FlaskService
import com.mritsoftware.mritserver.ui.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var gatewayStatus: TextView
    private lateinit var deviceCount: TextView
    private lateinit var refreshButton: MaterialButton
    
    private val devices = mutableListOf<TuyaDevice>()
    private lateinit var flaskService: FlaskService
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        flaskService = FlaskService(this)
        
        setupToolbar()
        setupViews()
        setupRecyclerView()
        setupListeners()
        checkServerConnection()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                val intent = android.content.Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun checkServerConnection() {
        coroutineScope.launch {
            try {
                val isConnected = withContext(Dispatchers.IO) {
                    flaskService.checkServerHealth()
                }
                
                if (isConnected) {
                    val siteInfo = withContext(Dispatchers.IO) {
                        flaskService.getSiteInfo()
                    }
                    gatewayStatus.text = "Conectado"
                    if (siteInfo != null) {
                        gatewayStatus.text = "Conectado - $siteInfo"
                    }
                    gatewayStatus.setTextColor(getColor(R.color.teal_700))
                    loadSampleDevices() // Por enquanto usa dados de exemplo
                } else {
                    gatewayStatus.text = "Desconectado"
                    gatewayStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    Toast.makeText(
                        this@MainActivity,
                        "Servidor Flask não encontrado. Configure em Configurações.",
                        Toast.LENGTH_LONG
                    ).show()
                    loadSampleDevices()
                }
            } catch (e: Exception) {
                gatewayStatus.text = "Erro de conexão"
                gatewayStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                loadSampleDevices()
            }
        }
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }
    
    private fun setupViews() {
        gatewayStatus = findViewById(R.id.gatewayStatus)
        deviceCount = findViewById(R.id.deviceCount)
        refreshButton = findViewById(R.id.refreshButton)
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter(devices) { device, isOn ->
            onDeviceToggle(device, isOn)
        }
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)
        devicesRecyclerView.adapter = deviceAdapter
    }
    
    private fun setupListeners() {
        refreshButton.setOnClickListener {
            refreshDevices()
        }
    }
    
    private fun loadSampleDevices() {
        // Dispositivos de exemplo - substitua pela integração real com Tuya
        devices.clear()
        devices.addAll(listOf(
            TuyaDevice(
                id = "1",
                name = "Lâmpada Sala",
                type = TuyaDevice.DeviceType.LIGHT,
                isOnline = true,
                isOn = false,
                brightness = 80
            ),
            TuyaDevice(
                id = "2",
                name = "Interruptor Quarto",
                type = TuyaDevice.DeviceType.SWITCH,
                isOnline = true,
                isOn = true
            ),
            TuyaDevice(
                id = "3",
                name = "Sensor Temperatura",
                type = TuyaDevice.DeviceType.SENSOR,
                isOnline = true,
                temperature = 22
            ),
            TuyaDevice(
                id = "4",
                name = "Lâmpada Cozinha",
                type = TuyaDevice.DeviceType.LIGHT,
                isOnline = false,
                isOn = false
            )
        ))
        updateUI()
    }
    
    private fun refreshDevices() {
        Toast.makeText(this, "Atualizando dispositivos...", Toast.LENGTH_SHORT).show()
        coroutineScope.launch {
            // Por enquanto apenas atualiza status online/offline
            devices.forEach { device ->
                device.isOnline = kotlin.random.Random.nextBoolean()
            }
            updateUI()
            Toast.makeText(this@MainActivity, "Dispositivos atualizados!", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun onDeviceToggle(device: TuyaDevice, isOn: Boolean) {
        coroutineScope.launch {
            try {
                // Buscar local_key do dispositivo (por enquanto usa um valor padrão)
                // TODO: Armazenar local_key de cada dispositivo
                val localKey = getLocalKeyForDevice(device.id)
                
                if (localKey == null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Configure a local_key do dispositivo ${device.name}",
                        Toast.LENGTH_LONG
                    ).show()
                    device.isOn = !isOn
                    deviceAdapter.notifyDataSetChanged()
                    return@launch
                }
                
                val action = if (isOn) "on" else "off"
                val success = withContext(Dispatchers.IO) {
                    flaskService.sendCommand(
                        deviceId = device.id,
                        localKey = localKey,
                        action = action,
                        lanIp = null // "auto" será usado
                    )
                }
                
                if (success) {
                    device.isOn = isOn
                    val message = if (isOn) {
                        "${device.name} ligado"
                    } else {
                        "${device.name} desligado"
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    deviceAdapter.notifyDataSetChanged()
                } else {
                    // Reverter se falhar
                    device.isOn = !isOn
                    deviceAdapter.notifyDataSetChanged()
                    Toast.makeText(this@MainActivity, "Erro ao controlar dispositivo", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Reverter se houver erro
                device.isOn = !isOn
                deviceAdapter.notifyDataSetChanged()
                Toast.makeText(this@MainActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun getLocalKeyForDevice(deviceId: String): String? {
        // TODO: Buscar do SharedPreferences ou banco de dados
        // Por enquanto retorna null para forçar configuração
        return getSharedPreferences("TuyaGateway", MODE_PRIVATE)
            .getString("device_${deviceId}_local_key", null)
    }
    
    private fun updateUI() {
        val onlineCount = devices.count { it.isOnline }
        gatewayStatus.text = if (onlineCount > 0) "Conectado" else "Desconectado"
        gatewayStatus.setTextColor(
            if (onlineCount > 0) getColor(R.color.teal_700)
            else getColor(android.R.color.holo_red_dark)
        )
        deviceCount.text = "${devices.size} dispositivos (${onlineCount} online)"
        deviceAdapter.notifyDataSetChanged()
    }
}

