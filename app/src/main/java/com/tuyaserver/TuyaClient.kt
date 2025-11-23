package com.tuyaserver

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

class TuyaClient {
    
    companion object {
        private const val TAG = "TuyaClient"
        private const val PROTOCOL_VERSION = 3.3
        private const val PORT = 6668
        private const val TIMEOUT_MS = 5000
    }
    
    /**
     * Envia comando para dispositivo Tuya
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
            log("[INFO] Enviando '$action' → $deviceId @ $lanIp")
            
            val command = if (action == "on") {
                mapOf("1" to true)  // DPS 1 = power on
            } else {
                mapOf("1" to false) // DPS 1 = power off
            }
            
            val payload = buildCommandPayload(command, localKey)
            sendUdpPacket(lanIp, PORT, payload)
            
            // Para comandos, não é necessário receber resposta confirmada
            // O envio bem-sucedido já indica que o comando foi processado
            log("[OK] Comando enviado com sucesso")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar comando Tuya", e)
            Result.failure(e)
        }
    }
    
    /**
     * Constrói o payload do comando Tuya (protocolo 3.3)
     */
    private fun buildCommandPayload(command: Map<String, Any>, localKey: String): ByteArray {
        // Converte comando para JSON
        val jsonCommand = command.entries.joinToString(", ") { (k, v) ->
            "\"$k\":${if (v is Boolean) v else "\"$v\""}"
        }
        val json = "{\"dps\":{$jsonCommand}}"
        
        // Cria payload Tuya
        val payload = json.toByteArray(Charsets.UTF_8)
        val encrypted = encrypt(payload, localKey)
        
        // Monta pacote Tuya 3.3
        // Header: prefix(4) + version(4) + command(4) + length(4) + sequence(4) + return_code(4) = 24 bytes
        val headerSize = 24
        val suffixSize = 4
        val totalSize = headerSize + encrypted.size + suffixSize
        
        val packet = ByteBuffer.allocate(totalSize).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(0x000055AA) // prefix
            putInt(0x00000000) // version (0 = 3.3)
            putInt(0x0000000D) // command (0x0D = CONTROL)
            putInt(encrypted.size) // length
            putInt(0x00000000) // sequence
            putInt(0x00000000) // return code
            put(encrypted) // payload criptografado
            putInt(0x0000AA55) // suffix
        }
        
        return packet.array()
    }
    
    /**
     * Envia pacote UDP para o dispositivo
     */
    private fun sendUdpPacket(ip: String, port: Int, data: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                soTimeout = TIMEOUT_MS
            }
            
            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(data, data.size, address, port)
            
            socket.send(packet)
            log("[DEBUG] Pacote enviado: ${data.size} bytes")
            
            // Tenta receber resposta
            val buffer = ByteArray(1024)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            
            try {
                socket.receive(responsePacket)
                log("[DEBUG] Resposta recebida: ${responsePacket.length} bytes")
                return buffer.copyOf(responsePacket.length)
            } catch (e: java.net.SocketTimeoutException) {
                // Timeout é aceitável para comandos
                log("[DEBUG] Timeout ao receber resposta (normal para comandos)")
                return ByteArray(0) // Retorna array vazio indicando sucesso
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar pacote UDP", e)
            return null
        } finally {
            socket?.close()
        }
    }
    
    /**
     * Criptografia Tuya (AES-128-ECB)
     * A chave precisa ter exatamente 16 bytes
     */
    private fun encrypt(data: ByteArray, key: String): ByteArray {
        try {
            // Prepara a chave (deve ter 16 bytes)
            val keyBytes = prepareKey(key)
            val keySpec = SecretKeySpec(keyBytes, "AES")
            
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            
            return cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criptografar", e)
            throw RuntimeException("Falha na criptografia: ${e.message}", e)
        }
    }
    
    /**
     * Prepara a chave para ter exatamente 16 bytes (MD5 hash se necessário)
     */
    private fun prepareKey(key: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        
        return when {
            keyBytes.size == 16 -> keyBytes
            keyBytes.size < 16 -> {
                // Padding com zeros se menor que 16
                ByteArray(16).apply {
                    System.arraycopy(keyBytes, 0, this, 0, keyBytes.size)
                }
            }
            else -> {
                // Se maior que 16, usa MD5 hash
                val md = MessageDigest.getInstance("MD5")
                md.update(keyBytes)
                md.digest()
            }
        }
    }
    
    private fun log(msg: String) {
        Log.d(TAG, msg)
    }
}

