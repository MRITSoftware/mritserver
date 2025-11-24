package com.mritsoftware.mritserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.mritsoftware.mritserver.MainActivity
import com.mritsoftware.mritserver.service.PythonServerService

class BootReceiver : BroadcastReceiver() {
    
    private val TAG = "BootReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Boot completado detectado. Iniciando servidor...")
            
            // IMPORTANTE: O servidor SEMPRE inicia, mesmo com tela bloqueada
            // Foreground services podem rodar mesmo quando o dispositivo está bloqueado
            val serviceIntent = Intent(context, PythonServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Serviço PythonServerService iniciado (funciona mesmo com tela bloqueada)")
            
            // Verificar se a tela está ligada/desbloqueada antes de tentar abrir apps
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                powerManager.isInteractive
            } else {
                @Suppress("DEPRECATION")
                powerManager.isScreenOn
            }
            
            Log.d(TAG, "Tela está ${if (isScreenOn) "ligada" else "desligada/bloqueada"}")
            
            // Aguardar um pouco antes de abrir os apps para dar tempo do servidor iniciar
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // Tentar abrir apps mesmo com tela bloqueada (pode não funcionar em algumas versões do Android)
                try {
                    // Abrir a MainActivity do mritserver
                    val mritserverIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        // Tentar abrir mesmo com tela bloqueada
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(mritserverIntent)
                    Log.d(TAG, "Tentativa de abrir MainActivity (mritserver)")
                } catch (e: Exception) {
                    Log.w(TAG, "Não foi possível abrir mritserver (pode estar bloqueado): ${e.message}")
                }
                
                // Aguardar mais um pouco antes de abrir o gelafitgo
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val gelafitgoIntent = context.packageManager.getLaunchIntentForPackage("com.mrit.gelafitgo")
                        if (gelafitgoIntent != null) {
                            gelafitgoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            gelafitgoIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            context.startActivity(gelafitgoIntent)
                            Log.d(TAG, "Tentativa de abrir aplicativo gelafitgo")
                        } else {
                            Log.w(TAG, "Aplicativo gelafitgo não encontrado ou não instalado")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Não foi possível abrir gelafitgo (pode estar bloqueado): ${e.message}")
                    }
                }, 2000) // Aguardar mais 2 segundos antes de abrir o gelafitgo
            }, 5000) // Aguardar 5 segundos para o servidor iniciar
        }
    }
}

