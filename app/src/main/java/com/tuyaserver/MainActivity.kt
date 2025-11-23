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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var configManager: ConfigManager
    private lateinit var statusText: TextView
    private lateinit var siteNameText: TextView
    private lateinit var ipText: TextView
    private lateinit var ipContainer: LinearLayout
    private val tuyaClient = TuyaClient()
    
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
        
        // Atualiza IP local (mostra completo)
        val localIp = getLocalIpAddress()
        ipText.text = localIp
        
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
        
        // Ação sempre será "on" (ligar), não precisa de campo
        
        val deviceIdInput = EditText(this).apply {
            hint = "Device ID do Tuya"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
        
        val localKeyInput = EditText(this).apply {
            hint = "Local Key do Tuya"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
        
        val lanIpInput = EditText(this).apply {
            hint = "IP do dispositivo Tuya (ex: 192.168.1.100)"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        layout.addView(deviceIdInput)
        layout.addView(localKeyInput)
        layout.addView(lanIpInput)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Comando Tuya - Ligar")
            .setMessage("Preencha os dados do dispositivo Tuya:\n\n• Device ID: ID do dispositivo\n• Local Key: Chave local\n• IP: IP do dispositivo na rede")
            .setView(layout)
            .setPositiveButton("Enviar") { _, _ ->
                val deviceId = deviceIdInput.text.toString().trim()
                val localKey = localKeyInput.text.toString().trim()
                val lanIp = lanIpInput.text.toString().trim()
                
                if (deviceId.isNotEmpty() && localKey.isNotEmpty() && lanIp.isNotEmpty()) {
                    // Sempre usa ação "on"
                    sendTuyaCommand("on", deviceId, localKey, lanIp)
                } else {
                    Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("🔍 Descobrir") { _, _ ->
                discoverTuyaDevices(deviceIdInput, lanIpInput)
            }
            .setNegativeButton("Cancelar", null)
            .create()
        
        dialog.show()
    }
    
    private fun discoverTuyaDevices(deviceIdInput: EditText, lanIpInput: EditText) {
        // Mostra opções de descoberta
        AlertDialog.Builder(this)
            .setTitle("Descobrir Dispositivo Tuya")
            .setMessage("Escolha o método de descoberta:")
            .setPositiveButton("🔍 Buscar na Rede") { _, _ ->
                discoverByBroadcast(deviceIdInput, lanIpInput)
            }
            .setNeutralButton("📡 Escanear IPs") { _, _ ->
                discoverByScanning(deviceIdInput, lanIpInput)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun discoverByBroadcast(deviceIdInput: EditText, lanIpInput: EditText) {
        Toast.makeText(this, "🔍 Procurando dispositivos Tuya na rede (broadcast)...", Toast.LENGTH_SHORT).show()
        
        activityScope.launch(Dispatchers.IO) {
            try {
                val devices = tuyaClient.discoverDevices()
                
                withContext(Dispatchers.Main) {
                    if (devices.isEmpty()) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Nenhum Dispositivo Encontrado")
                            .setMessage("A descoberta por broadcast não encontrou dispositivos.\n\nPossíveis causas:\n• Firewall bloqueando broadcast\n• Dispositivo não responde a descoberta\n• Roteador bloqueando UDP\n\nTente:\n• Escanear IPs da rede\n• Preencher manualmente")
                            .setPositiveButton("📡 Escanear IPs") { _, _ ->
                                discoverByScanning(deviceIdInput, lanIpInput)
                            }
                            .setNegativeButton("OK", null)
                            .show()
                    } else {
                        // Mostra lista de dispositivos para selecionar
                        val deviceNames = devices.mapIndexed { index, device ->
                            if (device.deviceId != "unknown") {
                                "${device.deviceId.take(8)}... @ ${device.ip}"
                            } else {
                                "Dispositivo @ ${device.ip}"
                            }
                        }.toTypedArray()
                        
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Dispositivos Encontrados (${devices.size})")
                            .setItems(deviceNames) { _, which ->
                                val selectedDevice = devices[which]
                                deviceIdInput.setText(selectedDevice.deviceId)
                                lanIpInput.setText(selectedDevice.ip)
                                Toast.makeText(this@MainActivity, "✅ Dispositivo selecionado!", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ Erro ao descobrir: ${e.message}", Toast.LENGTH_LONG).show()
                    android.util.Log.e("MainActivity", "Erro ao descobrir dispositivos", e)
                }
            }
        }
    }
    
    private fun discoverByScanning(deviceIdInput: EditText, lanIpInput: EditText) {
        val localIp = getLocalIpAddress()
        if (localIp == "Não disponível") {
            Toast.makeText(this, "❌ IP não disponível para escanear", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Extrai o prefixo da rede (ex: 192.168.1)
        val ipParts = localIp.split(".")
        if (ipParts.size != 4) {
            Toast.makeText(this, "❌ Formato de IP inválido", Toast.LENGTH_SHORT).show()
            return
        }
        
        val networkPrefix = "${ipParts[0]}.${ipParts[1]}.${ipParts[2]}"
        
        AlertDialog.Builder(this)
            .setTitle("Escanear Rede")
            .setMessage("Isso vai tentar descobrir dispositivos Tuya escaneando IPs da rede.\n\nRede: $networkPrefix.x\n\nIsso pode demorar alguns minutos.\n\nDeseja continuar?")
            .setPositiveButton("Escanear") { _, _ ->
                scanNetworkForTuyaDevices(networkPrefix, deviceIdInput, lanIpInput)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun scanNetworkForTuyaDevices(networkPrefix: String, deviceIdInput: EditText, lanIpInput: EditText) {
        Toast.makeText(this, "📡 Escaneando rede $networkPrefix.x...\nIsso pode demorar alguns minutos", Toast.LENGTH_LONG).show()
        
        activityScope.launch(Dispatchers.IO) {
            val foundDevices = mutableListOf<TuyaClient.DiscoveredDevice>()
            
            try {
                // Escaneia IPs de 1 a 254
                for (i in 1..254) {
                    val ip = "$networkPrefix.$i"
                    
                    // Tenta descobrir se é um dispositivo Tuya
                    val device = tuyaClient.probeDevice(ip)
                    if (device != null) {
                        foundDevices.add(device)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "✅ Encontrado: ${device.ip}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // Mostra progresso a cada 50 IPs
                    if (i % 50 == 0) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "📡 Escaneando... $i/254", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (foundDevices.isEmpty()) {
                        Toast.makeText(this@MainActivity, "❌ Nenhum dispositivo Tuya encontrado no escaneamento", Toast.LENGTH_LONG).show()
                    } else {
                        val deviceNames = foundDevices.map { device ->
                            if (device.deviceId != "unknown") {
                                "${device.deviceId.take(8)}... @ ${device.ip}"
                            } else {
                                "Dispositivo @ ${device.ip}"
                            }
                        }.toTypedArray()
                        
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Dispositivos Encontrados (${foundDevices.size})")
                            .setItems(deviceNames) { _, which ->
                                val selectedDevice = foundDevices[which]
                                deviceIdInput.setText(selectedDevice.deviceId)
                                lanIpInput.setText(selectedDevice.ip)
                                Toast.makeText(this@MainActivity, "✅ Dispositivo selecionado!", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ Erro ao escanear: ${e.message}", Toast.LENGTH_LONG).show()
                    android.util.Log.e("MainActivity", "Erro ao escanear rede", e)
                }
            }
        }
    }
    
    private fun sendTuyaCommand(action: String, deviceId: String, localKey: String, lanIp: String) {
        val localIp = getLocalIpAddress()
        if (localIp == "Não disponível") {
            Toast.makeText(this, "IP não disponível", Toast.LENGTH_SHORT).show()
            return
        }
        
        // lanIp é o IP do dispositivo Tuya (fornecido pelo usuário)
        // localIp é o IP do Android (usado para conectar ao servidor HTTP)
        
        // Mostra loading
        Toast.makeText(this, "Enviando comando...", Toast.LENGTH_SHORT).show()
        
        // Envia comando via HTTP usando coroutines
        activityScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("http://$localIp:8000/tuya/command")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                // Timeouts aumentados: comando Tuya pode demorar (3 tentativas UDP)
                connection.connectTimeout = 5000 // 5 segundos para conectar
                connection.readTimeout = 15000 // 15 segundos para ler resposta
                
                val json = """
                    {
                        "action": "$action",
                        "tuya_device_id": "$deviceId",
                        "local_key": "$localKey",
                        "lan_ip": "$lanIp",
                        "protocol_version": 3.3
                    }
                """.trimIndent()
                
                val jsonBytes = json.toByteArray(Charsets.UTF_8)
                connection.setRequestProperty("Content-Length", jsonBytes.size.toString())
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                
                connection.outputStream.use { os ->
                    os.write(jsonBytes)
                    os.flush()
                }
                
                val responseCode = connection.responseCode
                val response = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Erro desconhecido"
                }
                
                withContext(Dispatchers.Main) {
                    if (responseCode == 200) {
                        Toast.makeText(this@MainActivity, "✅ Comando enviado com sucesso!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Erro $responseCode: $response", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    val errorMsg = if (e.message?.contains("connect") == true) {
                        "⏱️ Timeout ao conectar ao servidor. Verifique se o servidor está rodando."
                    } else {
                        "⏱️ Timeout ao receber resposta do servidor. O comando pode ter sido enviado."
                    }
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                    android.util.Log.e("MainActivity", "Timeout ao enviar comando Tuya", e)
                }
            } catch (e: java.net.ConnectException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ Não foi possível conectar ao servidor. Verifique se está rodando.", Toast.LENGTH_LONG).show()
                    android.util.Log.e("MainActivity", "Erro de conexão ao enviar comando Tuya", e)
                }
            } catch (e: java.net.UnknownHostException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ IP não encontrado: $localIp", Toast.LENGTH_LONG).show()
                    android.util.Log.e("MainActivity", "IP não encontrado", e)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = e.message ?: "Erro desconhecido"
                    Toast.makeText(this@MainActivity, "❌ Erro: $errorMsg", Toast.LENGTH_LONG).show()
                    android.util.Log.e("MainActivity", "Erro ao enviar comando Tuya", e)
                    e.printStackTrace()
                }
            }
        }
    }
}

