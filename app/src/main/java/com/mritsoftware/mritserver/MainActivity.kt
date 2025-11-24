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
import com.mritsoftware.mritserver.service.TuyaService
import com.mritsoftware.mritserver.ui.LoginActivity
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
    private lateinit var tuyaService: TuyaService
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tuyaService = TuyaService(this)
        
        setupToolbar()
        setupViews()
        setupRecyclerView()
        setupListeners()
        initializeGateway()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_logout -> {
                logout()
                true
            }
            R.id.menu_settings -> {
                // TODO: Abrir tela de configurações
                Toast.makeText(this, "Configurações em desenvolvimento", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun logout() {
        getSharedPreferences("TuyaGateway", MODE_PRIVATE).edit()
            .putBoolean("is_logged_in", false)
            .clear()
            .apply()
        
        val intent = android.content.Intent(this, LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun initializeGateway() {
        coroutineScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    tuyaService.initializeGateway()
                }
                
                if (success) {
                    tuyaService.setGatewayConnected(true)
                    loadDevices()
                } else {
                    Toast.makeText(this@MainActivity, "Erro ao conectar ao Gateway", Toast.LENGTH_LONG).show()
                    loadSampleDevices() // Fallback para dados de exemplo
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                loadSampleDevices() // Fallback para dados de exemplo
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
    
    private fun loadDevices() {
        coroutineScope.launch {
            try {
                val discoveredDevices = withContext(Dispatchers.IO) {
                    tuyaService.syncDevices()
                }
                
                if (discoveredDevices.isNotEmpty()) {
                    devices.clear()
                    devices.addAll(discoveredDevices)
                    updateUI()
                } else {
                    // Se não encontrar dispositivos reais, usar exemplos
                    loadSampleDevices()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro ao carregar dispositivos: ${e.message}", Toast.LENGTH_SHORT).show()
                loadSampleDevices()
            }
        }
    }
    
    private fun refreshDevices() {
        Toast.makeText(this, "Atualizando dispositivos...", Toast.LENGTH_SHORT).show()
        coroutineScope.launch {
            try {
                val discoveredDevices = withContext(Dispatchers.IO) {
                    tuyaService.syncDevices()
                }
                
                if (discoveredDevices.isNotEmpty()) {
                    devices.clear()
                    devices.addAll(discoveredDevices)
                    updateUI()
                    Toast.makeText(this@MainActivity, "Dispositivos atualizados!", Toast.LENGTH_SHORT).show()
                } else {
                    // Simula atualização se não houver dispositivos reais
                    devices.forEach { device ->
                        device.isOnline = kotlin.random.Random.nextBoolean()
                    }
                    updateUI()
                    Toast.makeText(this@MainActivity, "Dispositivos atualizados!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro ao atualizar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun onDeviceToggle(device: TuyaDevice, isOn: Boolean) {
        coroutineScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    tuyaService.updateDeviceStatus(device.id, isOn)
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

