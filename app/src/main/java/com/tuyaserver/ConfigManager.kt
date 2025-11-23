package com.tuyaserver

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException

class ConfigManager(private val context: Context) {
    
    private val configFileName = "config.json"
    
    fun getConfigFile(): File {
        return File(context.filesDir, configFileName)
    }
    
    fun createConfigIfNeeded(siteName: String) {
        saveConfig(siteName)
    }
    
    fun saveConfig(siteName: String) {
        val configFile = getConfigFile()
        try {
            val config = JSONObject().apply {
                put("site_name", siteName)
            }
            
            FileWriter(configFile).use { writer ->
                writer.write(config.toString(4))
            }
            
            log("[OK] config.json salvo com site_name = $siteName")
        } catch (e: IOException) {
            log("[ERRO] Falha ao salvar config.json: ${e.message}")
        }
    }
    
    fun loadConfig(): JSONObject {
        val configFile = getConfigFile()
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                JSONObject(content)
            } else {
                JSONObject().apply {
                    put("site_name", "SITE_DESCONHECIDO")
                }
            }
        } catch (e: Exception) {
            log("[ERRO] Falha ao carregar config.json: ${e.message}")
            JSONObject().apply {
                put("site_name", "SITE_DESCONHECIDO")
            }
        }
    }
    
    fun getSiteName(): String {
        val config = loadConfig()
        return config.optString("site_name", "SITE_DESCONHECIDO")
    }
    
    private fun log(msg: String) {
        android.util.Log.d("ConfigManager", msg)
    }
}

