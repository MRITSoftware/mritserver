package com.tuyaserver

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var configManager: ConfigManager
    private lateinit var statusText: TextView
    private lateinit var siteNameText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        configManager = ConfigManager(this)
        
        statusText = findViewById(R.id.statusText)
        siteNameText = findViewById(R.id.siteNameText)
        
        val startButton: Button = findViewById(R.id.startButton)
        val stopButton: Button = findViewById(R.id.stopButton)
        val configButton: Button = findViewById(R.id.configButton)
        
        updateUI()
        
        startButton.setOnClickListener {
            startService(Intent(this, TuyaServerService::class.java))
            updateUI()
        }
        
        stopButton.setOnClickListener {
            stopService(Intent(this, TuyaServerService::class.java))
            updateUI()
        }
        
        configButton.setOnClickListener {
            showConfigDialog()
        }
    }
    
    private fun updateUI() {
        val siteName = configManager.getSiteName()
        siteNameText.text = "Site: $siteName"
        statusText.text = "Servidor: Parado"
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
                    stopService(Intent(this, TuyaServerService::class.java))
                    startService(Intent(this, TuyaServerService::class.java))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

