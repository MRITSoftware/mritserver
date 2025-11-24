package com.mritsoftware.mritserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mritsoftware.mritserver.MainActivity
import com.mritsoftware.mritserver.service.PythonServerService

class BootReceiver : BroadcastReceiver() {
    
    private val TAG = "BootReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Boot completado detectado. Iniciando servidor e aplicativo...")
            
            // Iniciar o serviço do servidor Python
            val serviceIntent = Intent(context, PythonServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Serviço PythonServerService iniciado")
            
            // Aguardar um pouco antes de abrir os apps para dar tempo do servidor iniciar
            // Usar um Handler para atrasar a abertura dos apps
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // Abrir a MainActivity do mritserver automaticamente
                val mritserverIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(mritserverIntent)
                Log.d(TAG, "MainActivity (mritserver) aberta automaticamente")
                
                // Aguardar mais um pouco antes de abrir o gelafitgo
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // Abrir o aplicativo gelafitgo
                    try {
                        val gelafitgoIntent = context.packageManager.getLaunchIntentForPackage("com.mrit.gelafitgo")
                        if (gelafitgoIntent != null) {
                            gelafitgoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            gelafitgoIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            context.startActivity(gelafitgoIntent)
                            Log.d(TAG, "Aplicativo gelafitgo (com.mrit.gelafitgo) aberto automaticamente")
                        } else {
                            Log.w(TAG, "Aplicativo gelafitgo não encontrado ou não instalado")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao abrir aplicativo gelafitgo", e)
                    }
                }, 2000) // Aguardar mais 2 segundos antes de abrir o gelafitgo
            }, 5000) // Aguardar 5 segundos para o servidor iniciar
        }
    }
}

