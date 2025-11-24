package com.mritsoftware.mritserver.service

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PythonServerService : Service() {
    
    private val TAG = "PythonServerService"
    private var pythonThread: Thread? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service criado")
        
        // Inicializar Python se ainda não foi inicializado
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
            Log.d(TAG, "Python inicializado")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPythonServer()
        return START_STICKY // Serviço será reiniciado se for morto
    }
    
    private fun startPythonServer() {
        if (pythonThread?.isAlive == true) {
            Log.d(TAG, "Servidor Python já está rodando")
            return
        }
        
        // Atualizar site_name se necessário
        updateSiteName()
        
        pythonThread = Thread {
            try {
                val python = Python.getInstance()
                val module = python.getModule("tuya_server")
                
                Log.d(TAG, "Iniciando servidor Flask Python...")
                
                // Iniciar servidor Flask em thread separada
                module.callAttr("start_server", "0.0.0.0", 8000)
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar servidor Python", e)
            }
        }
        
        pythonThread?.start()
        Log.d(TAG, "Thread do servidor Python iniciada")
    }
    
    private fun updateSiteName() {
        try {
            val prefs = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
            val siteName = prefs.getString("site_name", "ANDROID_DEVICE") ?: "ANDROID_DEVICE"
            
            val python = Python.getInstance()
            val module = python.getModule("tuya_server")
            
            // Atualizar site_name no Python
            val configPath = module.callAttr("create_config_if_needed")
            
            Log.d(TAG, "Site name configurado: $siteName")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar site name", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Parar thread do servidor
        pythonThread?.interrupt()
        pythonThread = null
        Log.d(TAG, "Service destruído")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    fun isServerRunning(): Boolean {
        return pythonThread?.isAlive == true
    }
}

