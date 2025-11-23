package com.tuyaserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class TuyaServerService : Service() {
    
    companion object {
        private const val TAG = "TuyaServerService"
        private const val PORT = 8000
        private const val CHANNEL_ID = "TuyaServerChannel"
        private const val NOTIFICATION_ID = 1
    }
    
    private var server: NettyApplicationEngine? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var configManager: ConfigManager
    private val tuyaClient = TuyaClient()
    
    private fun getSiteName(): String {
        if (!::configManager.isInitialized) {
            configManager = ConfigManager(this)
        }
        return configManager.getSiteName()
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Serviço criado")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        configManager = ConfigManager(this)
        val siteName = getSiteName()
        
        Log.d(TAG, "[INFO] Servidor local iniciado para SITE = $siteName")
        
        startServer()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand chamado")
        return START_STICKY // Reinicia o serviço se for morto
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tuya Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servidor HTTP Tuya rodando em background"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tuya Server")
            .setContentText("Servidor rodando na porta $PORT")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startServer() {
        serviceScope.launch {
            try {
                server = embeddedServer(Netty, host = "0.0.0.0", port = PORT) {
                    install(ContentNegotiation) {
                        json(Json {
                            prettyPrint = true
                            isLenient = true
                            ignoreUnknownKeys = true
                        })
                    }
                    
                    routing {
                        get("/health") {
                            call.respond(
                                HttpStatusCode.OK,
                                HealthResponse(status = "ok", site = getSiteName())
                            )
                        }
                        
                        post("/tuya/command") {
                            try {
                                val request = call.receive<TuyaCommandRequest>()
                                
                                val action = request.action
                                if (action == null || action !in listOf("on", "off")) {
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        TuyaCommandResponse(
                                            ok = false,
                                            error = "action deve ser 'on' ou 'off'"
                                        )
                                    )
                                    return@post
                                }
                                
                                val result = tuyaClient.sendCommand(
                                    action = action,
                                    deviceId = request.tuya_device_id ?: "",
                                    localKey = request.local_key ?: "",
                                    lanIp = request.lan_ip ?: ""
                                )
                                
                                if (result.isSuccess) {
                                    call.respond(
                                        HttpStatusCode.OK,
                                        TuyaCommandResponse(ok = true, error = null)
                                    )
                                } else {
                                    val error = result.exceptionOrNull()?.message ?: "Erro desconhecido"
                                    Log.e(TAG, "[ERRO] API /tuya/command: $error")
                                    call.respond(
                                        HttpStatusCode.InternalServerError,
                                        TuyaCommandResponse(ok = false, error = error)
                                    )
                                }
                                
                            } catch (e: Exception) {
                                val error = e.message ?: "Erro desconhecido"
                                Log.e(TAG, "[ERRO] API /tuya/command: $error", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    TuyaCommandResponse(ok = false, error = error)
                                )
                            }
                        }
                    }
                }.start(wait = false)
                
                Log.d(TAG, "[START] Servidor Tuya local rodando em http://0.0.0.0:$PORT (SITE=${getSiteName()})")
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar servidor", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Serviço sendo destruído")
        
        serviceScope.launch {
            server?.stop(1000, 2000)
        }
    }
    
    @Serializable
    data class HealthResponse(
        val status: String,
        val site: String
    )
    
    @Serializable
    data class TuyaCommandRequest(
        val action: String? = null,
        val tuya_device_id: String? = null,
        val local_key: String? = null,
        val lan_ip: String? = null
    )
    
    @Serializable
    data class TuyaCommandResponse(
        val ok: Boolean,
        val error: String? = null
    )
}

