package com.mritsoftware.mritserver

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.mritsoftware.mritserver.adapter.DeviceAdapter
import com.mritsoftware.mritserver.model.TuyaDevice

class MainActivity : AppCompatActivity() {
    
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var gatewayStatus: TextView
    private lateinit var deviceCount: TextView
    private lateinit var refreshButton: MaterialButton
    
    private val devices = mutableListOf<TuyaDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupToolbar()
        setupViews()
        setupRecyclerView()
        loadSampleDevices()
        setupListeners()
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
        // Simula atualização - substitua pela chamada real à API Tuya
        devices.forEach { device ->
            device.isOnline = kotlin.random.Random.nextBoolean()
        }
        updateUI()
        Toast.makeText(this, "Dispositivos atualizados!", Toast.LENGTH_SHORT).show()
    }
    
    private fun onDeviceToggle(device: TuyaDevice, isOn: Boolean) {
        // Aqui você faria a chamada real para controlar o dispositivo Tuya
        device.isOn = isOn
        val message = if (isOn) {
            "${device.name} ligado"
        } else {
            "${device.name} desligado"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        // Atualizar dispositivo na lista
        deviceAdapter.notifyDataSetChanged()
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

