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
            try {
                stopService(Intent(this, TuyaServerService::class.java))
                updateUI()
            } catch (e: Exception) {
                statusText.text = "Erro: ${e.message}"
            }
        }
        
        configButton.setOnClickListener {
            showConfigDialog()
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
        if (isRunning) {
            statusText.text = "Servidor: Rodando na porta 8000"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            statusText.text = "Servidor: Parado"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
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
}

