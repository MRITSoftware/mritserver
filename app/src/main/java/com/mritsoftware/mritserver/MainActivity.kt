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
import com.mritsoftware.mritserver.service.ServerService
import com.mritsoftware.mritserver.ui.SettingsActivity
import android.content.Intent
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
    private lateinit var serverService: ServerService
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupToolbar()
        setupViews()
        setupRecyclerView()
        setupListeners()
        startServerService()
        loadSampleDevices()
    }
    
    private fun startServerService() {
        val intent = Intent(this, ServerService::class.java)
        startService(intent)
        
        // Atualizar status após um pequeno delay para o servidor iniciar
        coroutineScope.launch {
            kotlinx.coroutines.delay(1000)
            updateServerStatus()
        }
    }
    
    private fun updateServerStatus() {
        gatewayStatus.text = "Servidor rodando na porta 8000"
        gatewayStatus.setTextColor(getColor(R.color.teal_700))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Não parar o serviço aqui - deixar rodando em background
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
                
                // Enviar comando via HTTP para o servidor local
                val action = if (isOn) "on" else "off"
                val success = withContext(Dispatchers.IO) {
                    sendCommandToLocalServer(
                        deviceId = device.id,
                        localKey = localKey,
                        action = action,
                        lanIp = device.lanIp ?: "auto"
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
    
    private fun sendCommandToLocalServer(
        deviceId: String,
        localKey: String,
        action: String,
        lanIp: String
    ): Boolean {
        return try {
            val url = java.net.URL("http://127.0.0.1:8000/tuya/command")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            
            val jsonBody = org.json.JSONObject().apply {
                put("action", action)
                put("tuya_device_id", deviceId)
                put("local_key", localKey)
                put("lan_ip", lanIp)
            }
            
            val outputStream = connection.outputStream
            val writer = java.io.OutputStreamWriter(outputStream, "UTF-8")
            writer.write(jsonBody.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            responseCode == 200
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getLocalKeyForDevice(deviceId: String): String? {
        // Buscar do dispositivo ou do SharedPreferences
        val device = devices.find { it.id == deviceId }
        return device?.localKey ?: getSharedPreferences("TuyaGateway", MODE_PRIVATE)
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

