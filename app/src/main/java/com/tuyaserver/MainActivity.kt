package com.tuyaserver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.format.Formatter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    
    private lateinit var configManager: ConfigManager
    private lateinit var statusText: TextView
    private lateinit var siteNameText: TextView
    private lateinit var ipText: TextView
    private lateinit var ipContainer: LinearLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        configManager = ConfigManager(this)
        
        statusText = findViewById(R.id.statusText)
        siteNameText = findViewById(R.id.siteNameText)
        ipText = findViewById(R.id.ipText)
        ipContainer = findViewById(R.id.ipContainer)
        
        val startButton: Button = findViewById(R.id.startButton)
        val stopButton: Button = findViewById(R.id.stopButton)
        val configButton: Button = findViewById(R.id.configButton)
        val tuyaCommandButton: Button = findViewById(R.id.tuyaCommandButton)
        
        // Configurar clique para copiar IP
        ipContainer.setOnClickListener {
            copyIpToClipboard()
        }
        
        updateUI()
        
        startButton.setOnClickListener {
            try {
                val intent = Intent(this, TuyaServerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                // Aguarda um pouco antes de atualizar UI
                statusText.postDelayed({
                    updateUI()
                }, 500)
            } catch (e: Exception) {
                statusText.text = "Erro: ${e.message}"
                android.util.Log.e("MainActivity", "Erro ao iniciar serviço", e)
            }
        }
        
        stopButton.setOnClickListener {
            showStopConfirmation()
        }
        
        configButton.setOnClickListener {
            showConfigDialog()
        }
        
        tuyaCommandButton.setOnClickListener {
            showTuyaCommandDialog()
        }
    }
    
    private fun updateUI() {
        val siteName = configManager.getSiteName()
        siteNameText.text = "Site: $siteName"
        
        // Atualiza IP local (oculto)
        val localIp = getLocalIpAddress()
        ipText.text = maskIpAddress(localIp)
        
        // Verifica se o serviço está rodando
        val isRunning = isServiceRunning(TuyaServerService::class.java)
        val tuyaCommandButton: Button = findViewById(R.id.tuyaCommandButton)
        
        if (isRunning) {
            statusText.text = "Servidor: Rodando na porta 8000"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            tuyaCommandButton.visibility = android.view.View.VISIBLE
        } else {
            statusText.text = "Servidor: Parado"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tuyaCommandButton.visibility = android.view.View.GONE
        }
    }
    
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                        return address.hostAddress ?: "Não disponível"
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Erro ao obter IP", e)
        }
        
        // Fallback: usar WifiManager
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            if (ipAddress != 0) {
                return Formatter.formatIpAddress(ipAddress)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Erro ao obter IP via WifiManager", e)
        }
        
        return "Não disponível"
    }
    
    private fun maskIpAddress(ip: String): String {
        if (ip == "Não disponível" || ip.isEmpty()) {
            return "Não disponível"
        }
        // Oculta os últimos números: 192.168.1.100 -> 192.168.1.xxx
        val parts = ip.split(".")
        if (parts.size == 4) {
            return "${parts[0]}.${parts[1]}.${parts[2]}.xxx"
        }
        return ip
    }
    
    private fun copyIpToClipboard() {
        val localIp = getLocalIpAddress()
        if (localIp != "Não disponível") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("IP Local", localIp)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "IP copiado: $localIp", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "IP não disponível", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        
        for (service in runningServices) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    private fun showStopConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Parar Servidor")
            .setMessage("Tem certeza que deseja parar o servidor?")
            .setPositiveButton("Sim, Parar") { _, _ ->
                try {
                    stopService(Intent(this, TuyaServerService::class.java))
                    updateUI()
                    Toast.makeText(this, "Servidor parado", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    statusText.text = "Erro: ${e.message}"
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun showConfigDialog() {
        val input = EditText(this)
        input.hint = "Nome do site (ex: GELAFIT_SP01)"
        input.setText(configManager.getSiteName())
        
        AlertDialog.Builder(this)
            .setTitle("Configuração")
            .setMessage("Digite o nome deste site/tablet:")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val siteName = input.text.toString().trim()
                if (siteName.isNotEmpty()) {
                    configManager.saveConfig(siteName)
                    updateUI()
                    // Reinicia o serviço para aplicar nova configuração
                    val intent = Intent(this, TuyaServerService::class.java)
                    stopService(intent)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun showTuyaCommandDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val actionInput = EditText(this).apply {
            hint = "Ação (on/off)"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
        
        val deviceIdInput = EditText(this).apply {
            hint = "Device ID"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
        
        val localKeyInput = EditText(this).apply {
            hint = "Local Key"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
        
        val lanIpInput = EditText(this).apply {
            hint = "LAN IP do dispositivo Tuya"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        layout.addView(actionInput)
        layout.addView(deviceIdInput)
        layout.addView(localKeyInput)
        layout.addView(lanIpInput)
        
        AlertDialog.Builder(this)
            .setTitle("Comando Tuya")
            .setMessage("Preencha os dados do dispositivo:")
            .setView(layout)
            .setPositiveButton("Enviar") { _, _ ->
                val action = actionInput.text.toString().trim()
                val deviceId = deviceIdInput.text.toString().trim()
                val localKey = localKeyInput.text.toString().trim()
                val lanIp = lanIpInput.text.toString().trim()
                
                if (action.isNotEmpty() && deviceId.isNotEmpty() && localKey.isNotEmpty() && lanIp.isNotEmpty()) {
                    sendTuyaCommand(action, deviceId, localKey, lanIp)
                } else {
                    Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun sendTuyaCommand(action: String, deviceId: String, localKey: String, lanIp: String) {
        val localIp = getLocalIpAddress()
        if (localIp == "Não disponível") {
            Toast.makeText(this, "IP não disponível", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Mostra loading
        Toast.makeText(this, "Enviando comando...", Toast.LENGTH_SHORT).show()
        
        // Envia comando via HTTP usando coroutines
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val url = java.net.URL("http://$localIp:8000/tuya/command")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                
                val json = """
                    {
                        "action": "$action",
                        "tuya_device_id": "$deviceId",
                        "local_key": "$localKey",
                        "lan_ip": "$lanIp",
                        "protocol_version": 3.4
                    }
                """.trimIndent()
                
                connection.outputStream.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                
                val responseCode = connection.responseCode
                val response = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Erro desconhecido"
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (responseCode == 200) {
                        Toast.makeText(this@MainActivity, "✅ Comando enviado com sucesso!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Erro $responseCode: $response", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "⏱️ Timeout ao conectar ao servidor", Toast.LENGTH_LONG).show()
                }
            } catch (e: java.net.UnknownHostException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ Não foi possível conectar ao servidor", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val errorMsg = e.message ?: "Erro desconhecido"
                    Toast.makeText(this@MainActivity, "❌ Erro: $errorMsg", Toast.LENGTH_LONG).show()
                    android.util.Log.e("MainActivity", "Erro ao enviar comando Tuya", e)
                }
            }
        }
    }
}

