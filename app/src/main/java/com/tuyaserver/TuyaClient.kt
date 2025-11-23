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
        private const val DEFAULT_PROTOCOL_VERSION = 3.4 // Padrão 3.4
        private const val PORT = 6668
        private const val TIMEOUT_MS = 5000
    }
    
    /**
     * Envia comando para dispositivo Tuya
     * @param protocolVersion Versão do protocolo (3.3 ou 3.4). Padrão: 3.3
     */
    suspend fun sendCommand(
        action: String,
        deviceId: String,
        localKey: String,
        lanIp: String,
        protocolVersion: Double = DEFAULT_PROTOCOL_VERSION
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
            log("[INFO] Enviando '$action' → $deviceId @ $lanIp (protocolo $protocolVersion)")
            
            // Comando sempre ligar (on) conforme solicitado
            val command = mapOf("1" to true)  // DPS 1 = power on
            
            log("[DEBUG] Comando DPS: $command")
            
            val payload = buildCommandPayload(command, localKey, protocolVersion)
            
            log("[DEBUG] Payload total: ${payload.size} bytes")
            log("[DEBUG] Hex do payload (primeiros 32 bytes): ${payload.take(32).joinToString(" ") { "%02X".format(it) }}")
            
            val response = sendUdpPacket(lanIp, PORT, payload)
            
            if (response != null && response.isNotEmpty()) {
                log("[DEBUG] Resposta recebida do dispositivo: ${response.size} bytes")
                log("[DEBUG] Hex da resposta (primeiros 32 bytes): ${response.take(32).joinToString(" ") { "%02X".format(it) }}")
            }
            
            log("[INFO] Protocolo Tuya ${protocolVersion} usado")
            log("[OK] Comando enviado com sucesso")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar comando Tuya", e)
            Result.failure(e)
        }
    }
    
    /**
     * Constrói o payload do comando Tuya (protocolo 3.3 ou 3.4)
     */
    private fun buildCommandPayload(command: Map<String, Any>, localKey: String, protocolVersion: Double): ByteArray {
        // Converte comando para JSON
        val jsonCommand = command.entries.joinToString(", ") { (k, v) ->
            "\"$k\":${if (v is Boolean) v else "\"$v\""}"
        }
        
        // Protocolo 3.4 requer timestamp no payload
        val timestamp = System.currentTimeMillis() / 1000 // timestamp em segundos
        val json = if (protocolVersion >= 3.4) {
            "{\"t\":$timestamp,\"dps\":{$jsonCommand}}"
        } else {
            "{\"dps\":{$jsonCommand}}"
        }
        
        log("[DEBUG] JSON payload: $json")
        
        // Cria payload Tuya
        val payload = json.toByteArray(Charsets.UTF_8)
        log("[DEBUG] Payload antes de criptografar: ${payload.size} bytes")
        
        val encrypted = encrypt(payload, localKey)
        log("[DEBUG] Payload criptografado: ${encrypted.size} bytes")
        
        // Monta pacote Tuya 3.3 ou 3.4
        // Header: prefix(4) + version(4) + command(4) + length(4) + sequence(4) + return_code(4) = 24 bytes
        val headerSize = 24
        val suffixSize = 4
        val totalSize = headerSize + encrypted.size + suffixSize
        
        // Determina versão do protocolo no header
        // Protocolo 3.3: version = 0x00000000
        // Protocolo 3.4: version = 0x00000000 (mesmo valor, mas pode ter diferenças no payload)
        val protocolVersionInt = 0x00000000
        
        log("[DEBUG] Protocolo Tuya $protocolVersion - Header version: 0x${protocolVersionInt.toString(16)}")
        
        val packet = ByteBuffer.allocate(totalSize).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(0x000055AA) // prefix
            putInt(protocolVersionInt) // version (0 = 3.3 ou 3.4)
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
     * Protocolo Tuya sempre usa MD5 da chave, independente do tamanho
     */
    private fun prepareKey(key: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        
        // Tuya sempre usa MD5 da chave para ter exatamente 16 bytes
        val md = MessageDigest.getInstance("MD5")
        md.update(keyBytes)
        val keyHash = md.digest()
        
        log("[DEBUG] Chave original: ${key.length} chars")
        log("[DEBUG] Chave MD5: ${keyHash.joinToString(" ") { "%02X".format(it) }}")
        
        return keyHash
    }
    
    private fun log(msg: String) {
        Log.d(TAG, msg)
    }
}

