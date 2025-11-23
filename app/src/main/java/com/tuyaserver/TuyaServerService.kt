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
import androidx.core.app.ServiceCompat
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.IOException

class TuyaServerService : Service() {
    
    companion object {
        private const val TAG = "TuyaServerService"
        private const val PORT = 8000
        private const val CHANNEL_ID = "TuyaServerChannel"
        private const val NOTIFICATION_ID = 1
    }
    
    private var httpServer: HttpServer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var configManager: ConfigManager
    private lateinit var tuyaClient: TuyaClient
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
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
        
        // Inicia foreground service com tipo (Android 14+)
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        configManager = ConfigManager(this)
        tuyaClient = TuyaClient(this) // Passa o contexto para usar MulticastLock
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
                "MRIT Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servidor HTTP MRIT rodando em background"
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
            .setContentTitle("MRIT Server")
            .setContentText("Servidor rodando na porta $PORT")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startServer() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "[START] ========================================")
                LogCollector.addLog(TAG, "[START] ========================================", "I")
                Log.d(TAG, "[START] Iniciando servidor HTTP na porta $PORT...")
                LogCollector.addLog(TAG, "[START] Iniciando servidor HTTP na porta $PORT...", "I")
                
                httpServer = HttpServer(PORT, this@TuyaServerService)
                Log.d(TAG, "[START] HttpServer criado, iniciando...")
                
                // Timeout aumentado para 35 segundos (comando Tuya pode tentar múltiplos protocolos)
                val socketTimeout = 35000 // 35 segundos
                val started = httpServer?.start(socketTimeout, false)
                Log.d(TAG, "[START] httpServer.start() retornou: $started")
                
                if (httpServer?.isAlive == true) {
                    Log.d(TAG, "[START] ✅ Servidor NanoHTTPD iniciado e ALIVE na porta $PORT")
                } else {
                    Log.w(TAG, "[START] ⚠️ Servidor iniciado mas isAlive = false")
                }
                
                val siteName = try {
                    getSiteName()
                } catch (e: Exception) {
                    Log.w(TAG, "[START] Erro ao obter site name: ${e.message}")
                    "SITE_DESCONHECIDO"
                }
                
                Log.d(TAG, "[START] Servidor MRIT local rodando em http://0.0.0.0:$PORT (SITE=$siteName)")
                Log.d(TAG, "[START] ========================================")
                
                // Testa conexão local após 2 segundos
                kotlinx.coroutines.delay(2000)
                testLocalConnection()
                
            } catch (e: IOException) {
                Log.e(TAG, "[START] ❌ Erro IOException ao iniciar servidor: ${e.message}", e)
                e.printStackTrace()
                updateNotificationError("Erro ao iniciar: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "[START] ❌ Erro fatal ao iniciar servidor: ${e.javaClass.simpleName}", e)
                e.printStackTrace()
                updateNotificationError("Erro fatal: ${e.message}")
            }
        }
    }
    
    private fun updateNotificationError(message: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MRIT Server - Erro")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(false)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar notificação", e)
        }
    }
    
    private fun testLocalConnection() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "[TEST] Testando conexão local...")
                val url = java.net.URL("http://127.0.0.1:$PORT/health")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                if (responseCode == 200) {
                    Log.d(TAG, "[TEST] ✅ Teste local bem-sucedido! Resposta: $response")
                } else {
                    Log.w(TAG, "[TEST] ⚠️ Teste retornou código $responseCode: $response")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "[TEST] ❌ Teste local falhou: ${e.message}", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Serviço sendo destruído")
        
        try {
            httpServer?.stop()
            Log.d(TAG, "Servidor HTTP parado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar servidor", e)
        }
    }
    
    // Classe interna para o servidor HTTP
    private inner class HttpServer(port: Int, private val service: TuyaServerService) : NanoHTTPD(port) {
        
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method
            val remoteIp = session.remoteIpAddress
            
            Log.d(TAG, "[HTTP] $method $uri de $remoteIp")
            LogCollector.addLog(TAG, "[HTTP] $method $uri de $remoteIp", "I")
            
            return try {
                when {
                    uri == "/" && method == Method.GET -> {
                        Log.d(TAG, "[HTTP] GET / respondido")
                        val response = newFixedLengthResponse(Response.Status.OK, "text/plain", "MRIT Server está rodando!")
                        response.addHeader("Access-Control-Allow-Origin", "*")
                        response
                    }
                    
                    uri == "/health" && method == Method.GET -> {
                        Log.d(TAG, "[HTTP] GET /health recebido")
                        try {
                            val siteName = try {
                                service.getSiteName()
                            } catch (e: Exception) {
                                Log.w(TAG, "[HTTP] Erro ao obter site name, usando padrão: ${e.message}")
                                "SITE_DESCONHECIDO"
                            }
                            
                            Log.d(TAG, "[HTTP] Site name obtido: $siteName")
                            
                            val responseObj = HealthResponse(status = "ok", site = siteName)
                            Log.d(TAG, "[HTTP] HealthResponse criado: $responseObj")
                            
                            val jsonResponse = try {
                                json.encodeToString(serializer<HealthResponse>(), responseObj)
                            } catch (e: Exception) {
                                Log.e(TAG, "[HTTP] Erro ao serializar JSON: ${e.message}", e)
                                // Fallback: retorna JSON manual
                                "{\"status\":\"ok\",\"site\":\"$siteName\"}"
                            }
                            
                            Log.d(TAG, "[HTTP] JSON response: $jsonResponse")
                            val response = newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse)
                            response.addHeader("Access-Control-Allow-Origin", "*")
                            response.addHeader("Content-Type", "application/json; charset=utf-8")
                            Log.d(TAG, "[HTTP] ✅ GET /health respondido com sucesso")
                            response
                        } catch (e: Exception) {
                            Log.e(TAG, "[HTTP] ❌ Erro ao responder /health", e)
                            e.printStackTrace()
                            // Retorna JSON simples mesmo em caso de erro
                            val errorJson = "{\"status\":\"error\",\"error\":\"${e.message ?: "Erro desconhecido"}\"}"
                            val response = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", errorJson)
                            response.addHeader("Content-Type", "application/json; charset=utf-8")
                            response.addHeader("Access-Control-Allow-Origin", "*")
                            response
                        }
                    }
                    
                    uri == "/tuya/command" && method == Method.POST -> {
                        try {
                            // Lê o body do POST diretamente
                            val bodyString = try {
                                val contentLengthHeader = session.headers["content-length"]
                                Log.d(TAG, "[HTTP] Content-Length: $contentLengthHeader")
                                
                                if (contentLengthHeader != null) {
                                    val contentLength = contentLengthHeader.toIntOrNull() ?: 0
                                    if (contentLength > 0 && contentLength < 100000) { // Limite de 100KB
                                        val bodyBytes = ByteArray(contentLength)
                                        var totalRead = 0
                                        val inputStream = session.inputStream
                                        
                                        while (totalRead < contentLength) {
                                            val bytesRead = inputStream.read(
                                                bodyBytes, 
                                                totalRead, 
                                                contentLength - totalRead
                                            )
                                            if (bytesRead == -1) {
                                                Log.w(TAG, "[HTTP] Stream terminou antes de ler tudo. Lido: $totalRead/$contentLength")
                                                break
                                            }
                                            totalRead += bytesRead
                                        }
                                        
                                        if (totalRead > 0) {
                                            String(bodyBytes, 0, totalRead, Charsets.UTF_8)
                                        } else {
                                            ""
                                        }
                                    } else {
                                        // Sem content-length ou muito grande, tenta ler tudo
                                        val reader = java.io.BufferedReader(
                                            java.io.InputStreamReader(session.inputStream, Charsets.UTF_8)
                                        )
                                        reader.readText()
                                    }
                                } else {
                                    // Sem content-length, tenta ler tudo
                                    val reader = java.io.BufferedReader(
                                        java.io.InputStreamReader(session.inputStream, Charsets.UTF_8)
                                    )
                                    reader.readText()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "[HTTP] Erro ao ler body: ${e.message}", e)
                                e.printStackTrace()
                                throw e
                            }
                            
                            if (bodyString.isEmpty()) {
                                throw Exception("Body vazio")
                            }
                            
                            Log.d(TAG, "[HTTP] POST /tuya/command recebido (${bodyString.length} bytes): $bodyString")
                        
                        val request = json.decodeFromString<TuyaCommandRequest>(bodyString)
                        Log.d(TAG, "[HTTP] Request parseado: action=${request.action}, protocol=${request.protocol_version}")
                        
                        val action = request.action
                        if (action == null || action !in listOf("on", "off")) {
                            val errorResponse = TuyaCommandResponse(
                                ok = false,
                                error = "action deve ser 'on' ou 'off'"
                            )
                            val jsonError = json.encodeToString(serializer<TuyaCommandResponse>(), errorResponse)
                            Log.d(TAG, "[HTTP] POST /tuya/command respondido com erro: Ação inválida")
                            val response = newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", jsonError)
                            response.addHeader("Content-Type", "application/json; charset=utf-8")
                            return response
                        }
                        
                        val protocolVersion = request.protocol_version ?: 3.4 // Padrão 3.4
                        
                        if (protocolVersion != 3.3 && protocolVersion != 3.4) {
                            val errorResponse = TuyaCommandResponse(
                                ok = false,
                                error = "protocol_version deve ser 3.3 ou 3.4"
                            )
                            val jsonError = json.encodeToString(serializer<TuyaCommandResponse>(), errorResponse)
                            Log.d(TAG, "[HTTP] POST /tuya/command respondido com erro: Versão de protocolo inválida")
                            val response = newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", jsonError)
                            response.addHeader("Content-Type", "application/json; charset=utf-8")
                            return response
                        }
                        
                        Log.d(TAG, "[HTTP] Enviando comando Tuya com protocolo $protocolVersion")
                        val lanIp = request.lan_ip ?: ""
                        LogCollector.addLog(TAG, "[HTTP] Enviando comando Tuya: action=$action, IP=$lanIp, protocol=$protocolVersion", "I")
                        
                        // sendCommand é suspend, então precisa ser chamado dentro de runBlocking
                        val result = runBlocking {
                            tuyaClient.sendCommand(
                                action = action,
                                deviceId = request.tuya_device_id ?: "",
                                localKey = request.local_key ?: "",
                                lanIp = request.lan_ip ?: "",
                                protocolVersion = protocolVersion
                            )
                        }
                        
                        if (result.isSuccess) {
                            Log.d(TAG, "[HTTP] Comando Tuya enviado com sucesso")
                            LogCollector.addLog(TAG, "[HTTP] ✅ Comando Tuya enviado com sucesso", "I")
                            val successResponse = TuyaCommandResponse(ok = true, error = null)
                            val jsonSuccess = json.encodeToString(serializer<TuyaCommandResponse>(), successResponse)
                            val response = newFixedLengthResponse(Response.Status.OK, "application/json", jsonSuccess)
                            response.addHeader("Content-Type", "application/json; charset=utf-8")
                            response
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Erro desconhecido"
                            Log.e(TAG, "[HTTP] Erro ao enviar comando Tuya: $error")
                            LogCollector.addLog(TAG, "[HTTP] ❌ Erro ao enviar comando Tuya: $error", "E")
                            val errorResponse = TuyaCommandResponse(ok = false, error = error)
                            val jsonError = json.encodeToString(serializer<TuyaCommandResponse>(), errorResponse)
                            val response = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", jsonError)
                            response.addHeader("Content-Type", "application/json; charset=utf-8")
                            response
                        }
                    } catch (e: Exception) {
                        val error = e.message ?: "Erro desconhecido"
                        Log.e(TAG, "[HTTP] Erro ao processar /tuya/command: $error", e)
                        e.printStackTrace()
                        val errorResponse = TuyaCommandResponse(ok = false, error = error)
                        val jsonError = json.encodeToString(serializer<TuyaCommandResponse>(), errorResponse)
                        val response = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", jsonError)
                        response.addHeader("Content-Type", "application/json; charset=utf-8")
                        response
                    }
                    }
                    
                    else -> {
                        Log.w(TAG, "[HTTP] Rota não encontrada: $method $uri")
                        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[HTTP] Erro geral no servidor: ${e.message}", e)
                e.printStackTrace()
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Internal Server Error: ${e.message}")
            }
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
        val lan_ip: String? = null,
        val protocol_version: Double? = null
    )
    
    @Serializable
    data class TuyaCommandResponse(
        val ok: Boolean,
        val error: String? = null
    )
}
