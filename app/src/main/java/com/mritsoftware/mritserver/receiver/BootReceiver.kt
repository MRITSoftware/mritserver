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
        Log.d(TAG, "onReceive chamado com action: ${intent.action}")
        
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "=== BOOT COMPLETADO DETECTADO ===")
            Log.d(TAG, "Package: ${context.packageName}")
            Log.d(TAG, "Android Version: ${Build.VERSION.SDK_INT}")
            
            // Usar goAsync() para manter o receiver vivo enquanto processamos
            val pendingResult = goAsync()
            
            try {
                // IMPORTANTE: O servidor SEMPRE inicia, mesmo com tela bloqueada
                // Foreground services podem rodar mesmo quando o dispositivo está bloqueado
                Log.d(TAG, "Iniciando PythonServerService...")
                val serviceIntent = Intent(context, PythonServerService::class.java)
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                        Log.d(TAG, "startForegroundService chamado com sucesso")
                    } else {
                        context.startService(serviceIntent)
                        Log.d(TAG, "startService chamado com sucesso")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ERRO ao iniciar serviço: ${e.message}", e)
                }
                
                // Verificar se a tela está ligada/desbloqueada
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    powerManager.isInteractive
                } else {
                    @Suppress("DEPRECATION")
                    powerManager.isScreenOn
                }
                
                Log.d(TAG, "Tela está ${if (isScreenOn) "ligada" else "desligada/bloqueada"}")
                
                // Usar Handler em thread separada para não bloquear o receiver
                Thread {
                    try {
                        // Aguardar 5 segundos para o servidor iniciar
                        Thread.sleep(5000)
                        Log.d(TAG, "Aguardou 5 segundos, tentando abrir apps...")
                        
                        // Tentar abrir mritserver
                        try {
                            val mritserverIntent = Intent(context, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            context.startActivity(mritserverIntent)
                            Log.d(TAG, "MainActivity (mritserver) aberta com sucesso")
                        } catch (e: Exception) {
                            Log.w(TAG, "Não foi possível abrir mritserver: ${e.message}", e)
                        }
                        
                        // Aguardar mais 2 segundos antes de abrir gelafitgo
                        Thread.sleep(2000)
                        
                        // Tentar abrir gelafitgo
                        try {
                            val gelafitgoIntent = context.packageManager.getLaunchIntentForPackage("com.mrit.gelafitgo")
                            if (gelafitgoIntent != null) {
                                gelafitgoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                gelafitgoIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                context.startActivity(gelafitgoIntent)
                                Log.d(TAG, "Aplicativo gelafitgo aberto com sucesso")
                            } else {
                                Log.w(TAG, "Aplicativo gelafitgo não encontrado ou não instalado")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Não foi possível abrir gelafitgo: ${e.message}", e)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro na thread de abertura de apps: ${e.message}", e)
                    } finally {
                        // Finalizar o receiver
                        pendingResult.finish()
                    }
                }.start()
                
            } catch (e: Exception) {
                Log.e(TAG, "ERRO CRÍTICO no BootReceiver: ${e.message}", e)
                pendingResult.finish()
            }
        } else {
            Log.d(TAG, "Action não reconhecido: ${intent.action}")
        }
    }
}

