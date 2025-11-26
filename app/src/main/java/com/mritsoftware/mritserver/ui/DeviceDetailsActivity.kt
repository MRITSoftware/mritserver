package com.mritsoftware.mritserver.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mritsoftware.mritserver.R
import com.mritsoftware.mritserver.model.TuyaDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceDetailsActivity : AppCompatActivity() {
    
    private lateinit var deviceName: TextView
    private lateinit var deviceId: TextView
    private lateinit var deviceIp: TextView
    private lateinit var deviceStatus: TextView
    private lateinit var discoverIpButton: MaterialButton
    private lateinit var turnOnButton: MaterialButton
    private lateinit var turnOffButton: MaterialButton
    private lateinit var deviceCard: MaterialCardView
    
    private var device: TuyaDevice? = null
    private var connectingBottomSheet: BottomSheetDialog? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_details)
        
        device = intent.getSerializableExtra("device") as? TuyaDevice
        
        if (device == null) {
            Toast.makeText(this, "Dispositivo não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupToolbar()
        setupViews()
        loadDeviceInfo()
        setupListeners()
    }
    
    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = device?.name ?: "Detalhes"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun setupViews() {
        deviceName = findViewById(R.id.deviceName)
        deviceId = findViewById(R.id.deviceId)
        deviceIp = findViewById(R.id.deviceIp)
        deviceStatus = findViewById(R.id.deviceStatus)
        discoverIpButton = findViewById(R.id.discoverIpButton)
        turnOnButton = findViewById(R.id.turnOnButton)
        turnOffButton = findViewById(R.id.turnOffButton)
        deviceCard = findViewById(R.id.deviceCard)
    }
    
    private fun loadDeviceInfo() {
        device?.let { dev ->
            deviceName.text = dev.name
            
            // Mostrar apenas os últimos 5 caracteres do ID
            val deviceIdFull = dev.id
            val deviceIdMasked = if (deviceIdFull.length > 5) {
                "***${deviceIdFull.takeLast(5)}"
            } else {
                deviceIdFull
            }
            deviceId.text = "ID: $deviceIdMasked"
            
            deviceStatus.text = if (dev.isOnline) "Status: Online" else "Status: Offline"
            
            // Buscar IP do SharedPreferences
            val prefs = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
            val savedIp = prefs.getString("device_${dev.id}_ip", null)
            
            // Se tiver IP salvo, usar ele
            if (savedIp != null && dev.lanIp == null) {
                dev.lanIp = savedIp
            }
            
            // Atualizar IP se tiver
            deviceIp.text = "IP Local: ${dev.lanIp ?: savedIp ?: "Não configurado"}"
            
            // Cor do status
            deviceStatus.setTextColor(
                if (dev.isOnline) getColor(R.color.status_online)
                else getColor(R.color.status_offline)
            )
        }
    }
    
    private fun setupListeners() {
        discoverIpButton.setOnClickListener {
            discoverDeviceIp()
        }
        
        turnOnButton.setOnClickListener {
            sendCommand("on")
        }
        
        turnOffButton.setOnClickListener {
            sendCommand("off")
        }
    }
    
    private fun sendCommand(action: String) {
        device?.let { dev ->
            // Mostrar bottom sheet
            showConnectingBottomSheet()
            
            coroutineScope.launch {
                try {
                    // Buscar local_key e IP
                    val prefs = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
                    val localKey = prefs.getString("device_${dev.id}_local_key", null)
                    val lanIp = dev.lanIp ?: prefs.getString("device_${dev.id}_ip", null) ?: "auto"
                    
                    if (localKey == null) {
                        dismissConnectingBottomSheet()
                        Toast.makeText(this@DeviceDetailsActivity, "Local Key não configurada para este dispositivo", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    
                    // Enviar comando
                    val success = withContext(Dispatchers.IO) {
                        try {
                            val url = java.net.URL("http://127.0.0.1:8000/tuya/command")
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "POST"
                            connection.setRequestProperty("Content-Type", "application/json")
                            connection.doOutput = true
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000
                            
                            val jsonBody = org.json.JSONObject().apply {
                                put("action", action)
                                put("tuya_device_id", dev.id)
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
                            Log.e("DeviceDetails", "Erro ao enviar comando", e)
                            false
                        }
                    }
                    
                    // Aguardar um pouco para mostrar a animação
                    delay(1500)
                    
                    dismissConnectingBottomSheet()
                    
                    if (success) {
                        Toast.makeText(this@DeviceDetailsActivity, "Comando enviado com sucesso", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@DeviceDetailsActivity, "Erro ao enviar comando", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    dismissConnectingBottomSheet()
                    Log.e("DeviceDetails", "Erro ao enviar comando", e)
                    Toast.makeText(this@DeviceDetailsActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showConnectingBottomSheet() {
        val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_connecting, null)
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.setContentView(bottomSheetView)
        bottomSheet.setCancelable(false)
        
        val circleCard = bottomSheetView.findViewById<MaterialCardView>(R.id.circleCard)
        val closeButton = bottomSheetView.findViewById<ImageButton>(R.id.closeButton)
        
        // Iniciar animação de rotação
        circleCard?.let {
            val rotateAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate_animation)
            it.startAnimation(rotateAnimation)
        }
        
        // Botão fechar
        closeButton?.setOnClickListener {
            dismissConnectingBottomSheet()
        }
        
        connectingBottomSheet = bottomSheet
        bottomSheet.show()
    }
    
    private fun dismissConnectingBottomSheet() {
        connectingBottomSheet?.dismiss()
        connectingBottomSheet = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        dismissConnectingBottomSheet()
    }
    
    private fun discoverDeviceIp() {
        device?.let { dev ->
            discoverIpButton.isEnabled = false
            discoverIpButton.text = "Descobrindo..."
            
            coroutineScope.launch {
                try {
                    // Inicializar Python se necessário
                    if (!com.chaquo.python.Python.isStarted()) {
                        com.chaquo.python.Python.start(com.chaquo.python.android.AndroidPlatform(this@DeviceDetailsActivity))
                    }
                    
                    // Chamar função Python para descobrir IP
                    val python = com.chaquo.python.Python.getInstance()
                    val module = python.getModule("tuya_server")
                    
                    val discoveredIp = withContext(Dispatchers.IO) {
                        try {
                            val result = module.callAttr("discover_tuya_ip", dev.id)
                            if (result != null && result.toString() != "None") {
                                result.toString()
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Log.e("DeviceDetails", "Erro ao descobrir IP", e)
                            null
                        }
                    }
                    
                    if (discoveredIp != null && discoveredIp.isNotBlank()) {
                        dev.lanIp = discoveredIp
                        deviceIp.text = "IP Local: $discoveredIp"
                        
                        // Salvar IP
                        val prefs = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
                        prefs.edit().putString("device_${dev.id}_ip", discoveredIp).apply()
                        
                        Toast.makeText(this@DeviceDetailsActivity, "IP descoberto: $discoveredIp", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@DeviceDetailsActivity, "IP não encontrado. Verifique se o dispositivo está na mesma rede.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("DeviceDetails", "Erro ao descobrir IP", e)
                    Toast.makeText(this@DeviceDetailsActivity, "Erro ao descobrir IP: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    discoverIpButton.isEnabled = true
                    discoverIpButton.text = "Descobrir IP"
                }
            }
        }
    }
}

