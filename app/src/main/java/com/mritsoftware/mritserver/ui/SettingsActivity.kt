package com.mritsoftware.mritserver.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mritsoftware.mritserver.R
import com.mritsoftware.mritserver.service.FlaskService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var testButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var flaskService: FlaskService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        flaskService = FlaskService(this)
        
        setupToolbar()
        setupViews()
        setupListeners()
    }
    
    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configurações"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun setupViews() {
        serverUrlInput = findViewById(R.id.serverUrlInput)
        testButton = findViewById(R.id.testButton)
        saveButton = findViewById(R.id.saveButton)
        
        // Preencher com URL atual
        serverUrlInput.setText(flaskService.getServerUrl())
    }
    
    private fun setupListeners() {
        testButton.setOnClickListener {
            testConnection()
        }
        
        saveButton.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun testConnection() {
        val url = serverUrlInput.text?.toString()?.trim() ?: ""
        
        if (url.isBlank()) {
            Toast.makeText(this, "Digite a URL do servidor", Toast.LENGTH_SHORT).show()
            return
        }
        
        testButton.isEnabled = false
        testButton.text = "Testando..."
        
        CoroutineScope(Dispatchers.Main).launch {
            val isValid = flaskService.checkServerHealth()
            
            testButton.isEnabled = true
            testButton.text = "Testar Conexão"
            
            if (isValid) {
                val siteInfo = flaskService.getSiteInfo()
                Toast.makeText(
                    this@SettingsActivity,
                    "Conexão OK! Site: ${siteInfo ?: "N/A"}",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@SettingsActivity,
                    "Erro ao conectar. Verifique a URL e se o servidor está rodando.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun saveSettings() {
        val url = serverUrlInput.text?.toString()?.trim() ?: ""
        
        if (url.isBlank()) {
            Toast.makeText(this, "Digite a URL do servidor", Toast.LENGTH_SHORT).show()
            return
        }
        
        flaskService.setServerUrl(url)
        Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show()
        finish()
    }
}

