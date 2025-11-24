package com.tuyaserver

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
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
 */
class TuyaClient(private val context: Context? = null) {
    
    companion object {
        private const val TAG = "TuyaClient"
        private const val PORT = 6668
        private const val TIMEOUT_MS = 5000
    }
    
    /**
     * Envia comando para dispositivo Tuya usando protocolo 3.4
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
            Log.d(TAG, "[INFO] Enviando '$action' → $deviceId @ $lanIp")
            
            // Cria payload JSON
            val dps = if (action == "on") {
                mapOf("1" to true)  // DPS 1 = power on
            } else {
                mapOf("1" to false) // DPS 1 = power off
            }
            
            // Protocolo 3.4 requer timestamp no payload
            val timestamp = (System.currentTimeMillis() / 1000).toInt()
            val jsonCommand = "{\"t\":$timestamp,\"dps\":{\"1\":${if (action == "on") "true" else "false"}}}"
            
            Log.d(TAG, "[DEBUG] JSON payload: $jsonCommand")
            
            // Criptografa payload
            val payload = jsonCommand.toByteArray(Charsets.UTF_8)
            Log.d(TAG, "[DEBUG] Payload antes de criptografar (${payload.size} bytes): ${payload.joinToString(" ") { "%02X".format(it) }}")
            
            val encrypted = encrypt(payload, localKey)
            Log.d(TAG, "[DEBUG] Payload criptografado (${encrypted.size} bytes): ${encrypted.take(32).joinToString(" ") { "%02X".format(it) }}...")
            
            // Cria pacote Tuya 3.4
            val packet = buildPacket(encrypted, timestamp)
            
            // Log detalhado do pacote
            Log.d(TAG, "[DEBUG] Pacote completo (${packet.size} bytes):")
            Log.d(TAG, "[DEBUG] Primeiros 32 bytes: ${packet.take(32).joinToString(" ") { "%02X".format(it) }}")
            Log.d(TAG, "[DEBUG] Prefix (bytes 0-3): ${packet.take(4).joinToString(" ") { "%02X".format(it) }}")
            Log.d(TAG, "[DEBUG] Version (bytes 4-7): ${packet.slice(4..7).joinToString(" ") { "%02X".format(it) }}")
            Log.d(TAG, "[DEBUG] Command (bytes 8-11): ${packet.slice(8..11).joinToString(" ") { "%02X".format(it) }}")
            Log.d(TAG, "[DEBUG] Length (bytes 12-15): ${packet.slice(12..15).joinToString(" ") { "%02X".format(it) }} (valor: ${encrypted.size})")
            Log.d(TAG, "[DEBUG] Sequence (bytes 16-19): ${packet.slice(16..19).joinToString(" ") { "%02X".format(it) }} (timestamp: $timestamp)")
            Log.d(TAG, "[DEBUG] Suffix (últimos 4 bytes): ${packet.takeLast(4).joinToString(" ") { "%02X".format(it) }}")
            
            // Envia via UDP
            val response = sendUdpPacket(lanIp, PORT, packet)
            
            if (response != null && response.isNotEmpty()) {
                Log.d(TAG, "[OK] Comando enviado com sucesso")
                Result.success(Unit)
            } else {
                Log.e(TAG, "[ERRO] Dispositivo não respondeu")
                Result.failure(RuntimeException("Dispositivo não respondeu. Verifique IP, Local Key e se está na mesma rede."))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[ERRO] Erro ao enviar comando: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Constrói pacote Tuya protocolo 3.4
     * Formato baseado no tinytuya Python
     * Header: prefix(4) + version(4) + command(4) + length(4) + sequence(4) + return_code(4) = 24 bytes
     * + payload criptografado + suffix(4)
     * 
     * IMPORTANTE: Tuya usa BIG-ENDIAN para inteiros no header (não little-endian!)
     */
    private fun buildPacket(encryptedPayload: ByteArray, sequence: Int): ByteArray {
        val headerSize = 24
        val suffixSize = 4
        val totalSize = headerSize + encryptedPayload.size + suffixSize
        
        val packet = ByteArray(totalSize)
        var offset = 0
        
        // Prefix: 0x000055AA (BIG-ENDIAN: 00 00 55 AA)
        val prefixBytes = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(0x000055AA)
        }.array()
        System.arraycopy(prefixBytes, 0, packet, offset, 4)
        offset += 4
        
        // Version: 0x00000000 (protocolo 3.4 ainda usa 0 no header)
        val versionBytes = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(0x00000000)
        }.array()
        System.arraycopy(versionBytes, 0, packet, offset, 4)
        offset += 4
        
        // Command: 0x0000000D (CONTROL)
        val commandBytes = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(0x0000000D)
        }.array()
        System.arraycopy(commandBytes, 0, packet, offset, 4)
        offset += 4
        
        // Length: tamanho do payload (BIG-ENDIAN)
        val lengthBytes = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(encryptedPayload.size)
        }.array()
        System.arraycopy(lengthBytes, 0, packet, offset, 4)
        offset += 4
        
        // Sequence: timestamp (BIG-ENDIAN)
        val sequenceBytes = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(sequence)
        }.array()
        System.arraycopy(sequenceBytes, 0, packet, offset, 4)
        offset += 4
        
        // Return code: 0x00000000
        val returnCodeBytes = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(0x00000000)
        }.array()
        System.arraycopy(returnCodeBytes, 0, packet, offset, 4)
        offset += 4
        
        // Payload criptografado
        System.arraycopy(encryptedPayload, 0, packet, offset, encryptedPayload.size)
        offset += encryptedPayload.size
        
        // Suffix: 0x0000AA55 (BIG-ENDIAN: 00 00 AA 55)
        val suffixBytes = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(0x0000AA55)
        }.array()
        System.arraycopy(suffixBytes, 0, packet, offset, 4)
        
        return packet
    }
    
    /**
     * Criptografa payload usando AES-128-ECB
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
        
        // Padding manual para múltiplo de 16
        val paddedData = if (data.size % 16 != 0) {
            val padding = 16 - (data.size % 16)
            ByteArray(data.size + padding) { i ->
                if (i < data.size) data[i] else padding.toByte()
            }
        } else {
            data
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
            socket.send(packet)
            
            // Tenta receber resposta
            val buffer = ByteArray(1024)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            
            try {
                socket.receive(responsePacket)
                val response = ByteArray(responsePacket.length)
                System.arraycopy(buffer, 0, response, 0, responsePacket.length)
                Log.d(TAG, "[UDP] Resposta recebida: ${response.size} bytes")
                return response
            } catch (e: java.net.SocketTimeoutException) {
                Log.w(TAG, "[UDP] Timeout aguardando resposta")
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

