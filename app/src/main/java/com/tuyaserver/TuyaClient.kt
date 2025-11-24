package com.tuyaserver

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.tuyaserver.LogCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * Cliente Tuya nativo para Android
 * Implementa protocolo 3.4 baseado no tinytuya Python
 * Replica exatamente o comportamento do tinytuya.OutletDevice
 */
class TuyaClient(private val context: Context? = null) {
    
    companion object {
        private const val TAG = "TuyaClient"
        private const val PORT = 6668
        private const val TIMEOUT_MS = 8000 // Aumentado para 8 segundos
    }
    
    /**
     * Envia comando para dispositivo Tuya usando protocolo 3.4
     * Replica o comportamento do tinytuya.OutletDevice.turn_on() / turn_off()
     */
    suspend fun sendCommand(
        action: String,
        deviceId: String,
        localKey: String,
        lanIp: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        
        if (deviceId.isBlank()) {
            return@withContext Result.failure(RuntimeException("Campo tuya_device_id é obrigatório"))
        }
        if (localKey.isBlank()) {
            return@withContext Result.failure(RuntimeException("Campo local_key é obrigatório"))
        }
        if (lanIp.isBlank()) {
            return@withContext Result.failure(RuntimeException("Campo lan_ip é obrigatório"))
        }
        
        if (action !in listOf("on", "off")) {
            return@withContext Result.failure(IllegalArgumentException("Ação inválida: $action"))
        }
        
        try {
            Log.d(TAG, "[INFO] ========================================")
            LogCollector.addLog(TAG, "[INFO] ========================================", "I")
            Log.d(TAG, "[INFO] Enviando '$action' → $deviceId @ $lanIp (protocolo 3.4)")
            LogCollector.addLog(TAG, "[INFO] Enviando '$action' → $deviceId @ $lanIp (protocolo 3.4)", "I")
            Log.d(TAG, "[INFO] Local Key: ${localKey.take(5)}... (${localKey.length} chars)")
            LogCollector.addLog(TAG, "[INFO] Local Key: ${localKey.take(5)}... (${localKey.length} chars)", "I")
            
            // Protocolo 3.4: cria payload com timestamp
            val timestamp = (System.currentTimeMillis() / 1000).toInt()
            val dpsValue = if (action == "on") 1 else 0
            
            // Formato JSON exato do tinytuya para protocolo 3.4
            val jsonCommand = "{\"t\":$timestamp,\"dps\":{\"1\":$dpsValue}}"
            
            Log.d(TAG, "[DEBUG] JSON payload: $jsonCommand")
            LogCollector.addLog(TAG, "[DEBUG] JSON payload: $jsonCommand", "D")
            
            // Criptografa payload
            val payload = jsonCommand.toByteArray(Charsets.UTF_8)
            Log.d(TAG, "[DEBUG] Payload antes de criptografar: ${payload.size} bytes")
            LogCollector.addLog(TAG, "[DEBUG] Payload antes de criptografar: ${payload.size} bytes", "D")
            Log.d(TAG, "[DEBUG] Payload hex: ${payload.joinToString(" ") { "%02X".format(it) }}")
            
            val encrypted = encrypt(payload, localKey)
            Log.d(TAG, "[DEBUG] Payload criptografado: ${encrypted.size} bytes")
            LogCollector.addLog(TAG, "[DEBUG] Payload criptografado: ${encrypted.size} bytes", "D")
            Log.d(TAG, "[DEBUG] Encrypted hex (primeiros 32): ${encrypted.take(32).joinToString(" ") { "%02X".format(it) }}")
            LogCollector.addLog(TAG, "[DEBUG] Encrypted hex (primeiros 32): ${encrypted.take(32).joinToString(" ") { "%02X".format(it) }}", "D")
            
            // Tenta múltiplas vezes (como tinytuya faz)
            var success = false
            for (attempt in 1..3) {
                Log.d(TAG, "[INFO] ===== Tentativa $attempt de 3 =====")
                
                // Tenta com sequence = timestamp (protocolo 3.4)
                Log.d(TAG, "[INFO] Tentando com sequence=timestamp ($timestamp)")
                val packet1 = buildPacket(encrypted, timestamp, useTimestamp = true)
                Log.d(TAG, "[DEBUG] Pacote criado: ${packet1.size} bytes")
                Log.d(TAG, "[DEBUG] Header (24 bytes): ${packet1.take(24).joinToString(" ") { "%02X".format(it) }}")
                Log.d(TAG, "[DEBUG] Prefix: ${packet1.take(4).joinToString(" ") { "%02X".format(it) }}")
                Log.d(TAG, "[DEBUG] Suffix: ${packet1.takeLast(4).joinToString(" ") { "%02X".format(it) }}")
                
                val response1 = sendUdpPacket(lanIp, PORT, packet1)
                if (response1 != null && response1.isNotEmpty()) {
                    Log.d(TAG, "[OK] ✅ Comando enviado com sucesso (tentativa $attempt, sequence=timestamp)")
                    LogCollector.addLog(TAG, "[OK] ✅ Comando enviado com sucesso (tentativa $attempt, sequence=timestamp)", "I")
                    Log.d(TAG, "[OK] Resposta recebida: ${response1.size} bytes")
                    LogCollector.addLog(TAG, "[OK] Resposta recebida: ${response1.size} bytes", "I")
                    success = true
                    break
                } else {
                    Log.w(TAG, "[WARN] Sem resposta (sequence=timestamp)")
                    LogCollector.addLog(TAG, "[WARN] Sem resposta (sequence=timestamp)", "W")
                }
                
                delay(300)
                
                // Tenta com sequence = 0
                Log.d(TAG, "[INFO] Tentando com sequence=0")
                LogCollector.addLog(TAG, "[INFO] Tentando com sequence=0", "I")
                val packet2 = buildPacket(encrypted, 0, useTimestamp = false)
                val response2 = sendUdpPacket(lanIp, PORT, packet2)
                if (response2 != null && response2.isNotEmpty()) {
                    Log.d(TAG, "[OK] ✅ Comando enviado com sucesso (tentativa $attempt, sequence=0)")
                    LogCollector.addLog(TAG, "[OK] ✅ Comando enviado com sucesso (tentativa $attempt, sequence=0)", "I")
                    Log.d(TAG, "[OK] Resposta recebida: ${response2.size} bytes")
                    LogCollector.addLog(TAG, "[OK] Resposta recebida: ${response2.size} bytes", "I")
                    success = true
                    break
                } else {
                    Log.w(TAG, "[WARN] Sem resposta (sequence=0)")
                    LogCollector.addLog(TAG, "[WARN] Sem resposta (sequence=0)", "W")
                }
                
                delay(300)
            }
            
            Log.d(TAG, "[INFO] ========================================")
            
            if (success) {
                Result.success(Unit)
            } else {
                Log.e(TAG, "[ERRO] Dispositivo não respondeu após 3 tentativas")
                Result.failure(RuntimeException("Dispositivo não respondeu. Verifique IP ($lanIp), Local Key e se está na mesma rede."))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[ERRO] Erro ao enviar comando: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Constrói pacote Tuya protocolo 3.4
     * Formato baseado no tinytuya Python (big-endian para inteiros)
     */
    private fun buildPacket(encryptedPayload: ByteArray, sequence: Int, useTimestamp: Boolean): ByteArray {
        val headerSize = 24
        val suffixSize = 4
        val totalSize = headerSize + encryptedPayload.size + suffixSize
        
        val packet = ByteArray(totalSize)
        var offset = 0
        
        // Prefix: 0x000055AA (BIG-ENDIAN: 00 00 55 AA)
        packet[offset++] = 0x00
        packet[offset++] = 0x00
        packet[offset++] = 0x55.toByte()
        packet[offset++] = 0xAA.toByte()
        
        // Version: 0x00000000
        packet[offset++] = 0x00
        packet[offset++] = 0x00
        packet[offset++] = 0x00
        packet[offset++] = 0x00
        
        // Command: 0x0000000D (CONTROL)
        packet[offset++] = 0x00
        packet[offset++] = 0x00
        packet[offset++] = 0x00
        packet[offset++] = 0x0D.toByte()
        
        // Length: tamanho do payload (BIG-ENDIAN)
        val length = encryptedPayload.size
        packet[offset++] = ((length shr 24) and 0xFF).toByte()
        packet[offset++] = ((length shr 16) and 0xFF).toByte()
        packet[offset++] = ((length shr 8) and 0xFF).toByte()
        packet[offset++] = (length and 0xFF).toByte()
        
        // Sequence: timestamp ou 0 (BIG-ENDIAN)
        val seq = if (useTimestamp) sequence else 0
        packet[offset++] = ((seq shr 24) and 0xFF).toByte()
        packet[offset++] = ((seq shr 16) and 0xFF).toByte()
        packet[offset++] = ((seq shr 8) and 0xFF).toByte()
        packet[offset++] = (seq and 0xFF).toByte()
        
        // Return code: 0x00000000
        packet[offset++] = 0x00
        packet[offset++] = 0x00
        packet[offset++] = 0x00
        packet[offset++] = 0x00
        
        // Payload criptografado
        System.arraycopy(encryptedPayload, 0, packet, offset, encryptedPayload.size)
        offset += encryptedPayload.size
        
        // Suffix: 0x0000AA55 (BIG-ENDIAN: 00 00 AA 55)
        packet[offset++] = 0x00
        packet[offset++] = 0x00
        packet[offset++] = 0xAA.toByte()
        packet[offset++] = 0x55.toByte()
        
        return packet
    }
    
    /**
     * Criptografa payload usando AES-128-ECB
     * Replica exatamente o comportamento do tinytuya
     */
    private fun encrypt(data: ByteArray, key: String): ByteArray {
        // Tuya usa MD5 da chave para ter exatamente 16 bytes
        val md = MessageDigest.getInstance("MD5")
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        md.update(keyBytes)
        val keyHash = md.digest()
        
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val secretKey = SecretKeySpec(keyHash, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        // Padding PKCS7 manual (múltiplo de 16)
        val padding = 16 - (data.size % 16)
        val paddedData = ByteArray(data.size + padding)
        System.arraycopy(data, 0, paddedData, 0, data.size)
        for (i in data.size until paddedData.size) {
            paddedData[i] = padding.toByte()
        }
        
        return cipher.doFinal(paddedData)
    }
    
    /**
     * Envia pacote UDP e aguarda resposta
     */
    private fun sendUdpPacket(ip: String, port: Int, data: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        var multicastLock: WifiManager.MulticastLock? = null
        
        try {
            // MulticastLock para Android
            if (context != null) {
                try {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    multicastLock = wifiManager?.createMulticastLock("TuyaCommand")
                    multicastLock?.setReferenceCounted(true)
                    multicastLock?.acquire()
                } catch (e: Exception) {
                    Log.w(TAG, "Erro ao adquirir MulticastLock: ${e.message}")
                }
            }
            
            socket = DatagramSocket().apply {
                soTimeout = TIMEOUT_MS
                broadcast = true
            }
            
            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(data, data.size, address, port)
            
            Log.d(TAG, "[UDP] Enviando ${data.size} bytes para $ip:$port")
            LogCollector.addLog(TAG, "[UDP] Enviando ${data.size} bytes para $ip:$port", "I")
            Log.d(TAG, "[UDP] Socket local: ${socket.localAddress?.hostAddress}:${socket.localPort}")
            LogCollector.addLog(TAG, "[UDP] Socket local: ${socket.localAddress?.hostAddress}:${socket.localPort}", "D")
            
            socket.send(packet)
            Log.d(TAG, "[UDP] ✅ Pacote enviado com sucesso")
            LogCollector.addLog(TAG, "[UDP] ✅ Pacote enviado com sucesso", "I")
            
            // Tenta receber resposta
            val buffer = ByteArray(1024)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            
            try {
                Log.d(TAG, "[UDP] Aguardando resposta (timeout: ${TIMEOUT_MS}ms)...")
                LogCollector.addLog(TAG, "[UDP] Aguardando resposta (timeout: ${TIMEOUT_MS}ms)...", "D")
                socket.receive(responsePacket)
                val response = ByteArray(responsePacket.length)
                System.arraycopy(buffer, 0, response, 0, responsePacket.length)
                Log.d(TAG, "[UDP] ✅ Resposta recebida: ${response.size} bytes de ${responsePacket.address.hostAddress}")
                LogCollector.addLog(TAG, "[UDP] ✅ Resposta recebida: ${response.size} bytes de ${responsePacket.address.hostAddress}", "I")
                return response
            } catch (e: java.net.SocketTimeoutException) {
                Log.w(TAG, "[UDP] ⏱️ Timeout aguardando resposta")
                LogCollector.addLog(TAG, "[UDP] ⏱️ Timeout aguardando resposta", "W")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[UDP] Erro: ${e.message}", e)
            return null
        } finally {
            socket?.close()
            multicastLock?.release()
        }
    }
}
