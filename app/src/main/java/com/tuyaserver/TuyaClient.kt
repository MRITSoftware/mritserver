package com.tuyaserver

import android.util.Log
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

class TuyaClient {
    
    companion object {
        private const val TAG = "TuyaClient"
        private const val DEFAULT_PROTOCOL_VERSION = 3.3 // Padrão 3.3 (como no Python)
        private const val PORT = 6668 // Porta para comandos
        private const val DISCOVERY_PORT = 6666 // Porta para descoberta
        private const val TIMEOUT_MS = 5000 // Timeout de 5s para dar tempo do dispositivo responder
        private const val DISCOVERY_TIMEOUT_MS = 5000 // 5s para descoberta
    }
    
    /**
     * Classe de dados para representar um dispositivo Tuya descoberto
     */
    data class DiscoveredDevice(
        val deviceId: String,
        val ip: String,
        val name: String? = null,
        val version: String? = null
    )
    
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
            log("[INFO] ========================================")
            log("[INFO] ENVIANDO COMANDO TUYA")
            log("[INFO] Device ID: $deviceId")
            log("[INFO] LAN IP: $lanIp")
            log("[INFO] Protocolo: $protocolVersion ${if (protocolVersion >= 3.4) "(3.4 - com timestamp)" else "(3.3)"}")
            log("[INFO] Ação: $action (sempre 'on' = ligar)")
            log("[INFO] Porta UDP: $PORT")
            log("[INFO] ========================================")
            
            // Comando sempre ligar (on) conforme solicitado
            // DPS 1 = power on/off
            // Tenta primeiro com formato numérico (1/0), que é mais comum
            // Se não funcionar, pode tentar booleano (true/false)
            val commandNumeric = if (action == "on") {
                mapOf("1" to 1)  // DPS 1 = power on (numérico: 1 = ligar, 0 = desligar)
            } else {
                mapOf("1" to 0)  // DPS 1 = power off
            }
            val commandBoolean = if (action == "on") {
                mapOf("1" to true)  // DPS 1 = power on (booleano)
            } else {
                mapOf("1" to false)  // DPS 1 = power off
            }
            
            // Tenta primeiro com o protocolo informado pelo usuário
            var lastResponse: ByteArray? = null
            var success = false
            val otherProtocol = if (protocolVersion == 3.3) 3.4 else 3.3
            
            // Tentativa 1: Protocolo informado pelo usuário, formato numérico
            log("[INFO] Tentativa 1: Protocolo $protocolVersion (informado), formato numérico, sequence=0")
            var payload = buildCommandPayload(commandNumeric, localKey, protocolVersion, sequenceZero = true)
            for (attempt in 1..2) {
                log("[INFO] Enviando tentativa $attempt de 2 com protocolo $protocolVersion")
                val response = sendUdpPacket(lanIp, PORT, payload)
                lastResponse = response
                if (response != null && response.isNotEmpty()) {
                    log("[DEBUG] ✅ Resposta recebida com protocolo $protocolVersion!")
                    success = true
                    break
                }
                if (attempt < 2) kotlinx.coroutines.delay(200)
            }
            
            // Tentativa 2: Outro protocolo (3.3 ou 3.4), formato numérico
            if (!success) {
                log("[INFO] Tentativa 2: Protocolo $otherProtocol (alternativo), formato numérico, sequence=0")
                payload = buildCommandPayload(commandNumeric, localKey, otherProtocol, sequenceZero = true)
                for (attempt in 1..2) {
                    log("[INFO] Enviando tentativa $attempt de 2 com protocolo $otherProtocol")
                    val response = sendUdpPacket(lanIp, PORT, payload)
                    lastResponse = response
                    if (response != null && response.isNotEmpty()) {
                        log("[DEBUG] ✅ Resposta recebida com protocolo $otherProtocol!")
                        success = true
                        break
                    }
                    if (attempt < 2) kotlinx.coroutines.delay(200)
                }
            }
            
            // Tentativa 3: Protocolo informado, formato booleano
            if (!success) {
                log("[INFO] Tentativa 3: Protocolo $protocolVersion (informado), formato booleano, sequence=0")
                payload = buildCommandPayload(commandBoolean, localKey, protocolVersion, sequenceZero = true)
                for (attempt in 1..2) {
                    log("[INFO] Enviando tentativa $attempt de 2 com formato booleano")
                    val response = sendUdpPacket(lanIp, PORT, payload)
                    lastResponse = response
                    if (response != null && response.isNotEmpty()) {
                        log("[DEBUG] ✅ Resposta recebida com formato booleano!")
                        success = true
                        break
                    }
                    if (attempt < 2) kotlinx.coroutines.delay(200)
                }
            }
            
            // Tentativa 4: Outro protocolo, formato booleano
            if (!success) {
                log("[INFO] Tentativa 4: Protocolo $otherProtocol (alternativo), formato booleano, sequence=0")
                payload = buildCommandPayload(commandBoolean, localKey, otherProtocol, sequenceZero = true)
                for (attempt in 1..2) {
                    log("[INFO] Enviando tentativa $attempt de 2 com protocolo $otherProtocol e formato booleano")
                    val response = sendUdpPacket(lanIp, PORT, payload)
                    lastResponse = response
                    if (response != null && response.isNotEmpty()) {
                        log("[DEBUG] ✅ Resposta recebida com protocolo $otherProtocol e formato booleano!")
                        success = true
                        break
                    }
                    if (attempt < 2) kotlinx.coroutines.delay(200)
                }
            }
            
            log("[INFO] Protocolo Tuya solicitado: $protocolVersion")
            if (success) {
                log("[OK] ✅ Comando enviado com sucesso e confirmado pelo dispositivo")
                Result.success(Unit)
            } else if (lastResponse != null) {
                log("[OK] ✅ Comando enviado (resposta vazia, mas pacote foi enviado)")
                Result.success(Unit)
            } else {
                log("[WARN] ⚠️ Comando enviado mas sem confirmação do dispositivo")
                // Ainda retorna sucesso pois o pacote foi enviado (alguns dispositivos não respondem)
                Result.success(Unit)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar comando Tuya", e)
            Result.failure(e)
        }
    }
    
    /**
     * Constrói o payload do comando Tuya (protocolo 3.3 ou 3.4)
     * @param sequenceZero Se true, usa sequence = 0 mesmo no protocolo 3.4
     */
    private fun buildCommandPayload(command: Map<String, Any>, localKey: String, protocolVersion: Double, sequenceZero: Boolean = true): ByteArray {
        // Converte comando para JSON
        val jsonCommand = command.entries.joinToString(", ") { (k, v) ->
            "\"$k\":${if (v is Boolean) v else if (v is Number) v else "\"$v\""}"
        }
        
        // Protocolo 3.4 requer timestamp no payload (em segundos)
        val timestamp = (System.currentTimeMillis() / 1000).toInt()
        val json = if (protocolVersion >= 3.4) {
            "{\"t\":$timestamp,\"dps\":{$jsonCommand}}"
        } else {
            "{\"dps\":{$jsonCommand}}"
        }
        
        log("[DEBUG] JSON payload: $json")
        
        // Cria payload Tuya
        val payload = json.toByteArray(Charsets.UTF_8)
        log("[DEBUG] Payload antes de criptografar: ${payload.size} bytes")
        log("[DEBUG] Payload hex: ${payload.joinToString(" ") { "%02X".format(it) }}")
        
        val encrypted = encrypt(payload, localKey)
        log("[DEBUG] Payload criptografado: ${encrypted.size} bytes")
        log("[DEBUG] Encrypted hex (primeiros 32): ${encrypted.take(32).joinToString(" ") { "%02X".format(it) }}")
        
        // Monta pacote Tuya 3.3 ou 3.4
        // Header: prefix(4) + version(4) + command(4) + length(4) + sequence(4) + return_code(4) = 24 bytes
        val headerSize = 24
        val suffixSize = 4
        val totalSize = headerSize + encrypted.size + suffixSize
        
        // Determina versão do protocolo no header
        // Protocolo 3.3: version = 0x00000000
        // Protocolo 3.4: version = 0x00000000 (mesmo valor, mas pode ter diferenças no payload)
        val protocolVersionInt = 0x00000000
        
        // Sequence number: muitos dispositivos Tuya precisam de sequence = 0
        val sequence = if (sequenceZero) {
            0x00000000 // Usa 0 (mais comum)
        } else if (protocolVersion >= 3.4) {
            timestamp // Usa timestamp (menos comum)
        } else {
            0x00000000
        }
        
        log("[DEBUG] ========================================")
        log("[DEBUG] PROTOCOLO TUYA $protocolVersion")
        log("[DEBUG] Timestamp: $timestamp (${System.currentTimeMillis() / 1000})")
        log("[DEBUG] Header version: 0x${protocolVersionInt.toString(16).padStart(8, '0')}")
        log("[DEBUG] Sequence: 0x${sequence.toString(16).padStart(8, '0')} (decimal: $sequence)")
        log("[DEBUG] Command: 0x0D (CONTROL)")
        log("[DEBUG] Encrypted size: ${encrypted.size} bytes")
        log("[DEBUG] ========================================")
        
        val packet = ByteBuffer.allocate(totalSize).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(0x000055AA) // prefix
            putInt(protocolVersionInt) // version (0 = 3.3 ou 3.4)
            putInt(0x0000000D) // command (0x0D = CONTROL)
            putInt(encrypted.size) // length
            putInt(sequence) // sequence (timestamp para 3.4)
            putInt(0x00000000) // return code
            put(encrypted) // payload criptografado
            putInt(0x0000AA55) // suffix
        }
        
        val packetArray = packet.array()
        log("[DEBUG] Pacote completo: ${packetArray.size} bytes")
        log("[DEBUG] Header completo (24 bytes): ${packetArray.take(24).joinToString(" ") { "%02X".format(it) }}")
        log("[DEBUG] Prefix: ${packetArray.take(4).joinToString(" ") { "%02X".format(it) }} (deve ser: 55 AA 00 00)")
        log("[DEBUG] Version: ${packetArray.slice(4..7).joinToString(" ") { "%02X".format(it) }}")
        log("[DEBUG] Command: ${packetArray.slice(8..11).joinToString(" ") { "%02X".format(it) }} (deve ser: 00 00 00 0D)")
        log("[DEBUG] Length: ${packetArray.slice(12..15).joinToString(" ") { "%02X".format(it) }} (${encrypted.size} bytes)")
        log("[DEBUG] Sequence: ${packetArray.slice(16..19).joinToString(" ") { "%02X".format(it) }}")
        log("[DEBUG] Return code: ${packetArray.slice(20..23).joinToString(" ") { "%02X".format(it) }}")
        log("[DEBUG] Suffix: ${packetArray.takeLast(4).joinToString(" ") { "%02X".format(it) }} (deve ser: AA 55 00 00)")
        
        return packetArray
    }
    
    /**
     * Envia pacote UDP para o dispositivo
     */
    private fun sendUdpPacket(ip: String, port: Int, data: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        try {
            log("[UDP] Criando socket UDP...")
            socket = DatagramSocket().apply {
                soTimeout = TIMEOUT_MS
                broadcast = false // Não usar broadcast
            }
            
            log("[UDP] Resolvendo endereço: $ip")
            val address = InetAddress.getByName(ip)
            log("[UDP] Endereço resolvido: ${address.hostAddress}")
            
            val packet = DatagramPacket(data, data.size, address, port)
            log("[UDP] Enviando pacote para ${address.hostAddress}:$port (${data.size} bytes)")
            
            socket.send(packet)
            log("[UDP] ✅ Pacote enviado com sucesso: ${data.size} bytes")
            log("[UDP] Hex completo do pacote: ${data.joinToString(" ") { "%02X".format(it) }}")
            
            // Tenta receber resposta
            val buffer = ByteArray(1024)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            
            try {
                log("[UDP] Aguardando resposta (timeout: ${TIMEOUT_MS}ms)...")
                socket.receive(responsePacket)
                log("[UDP] ✅ Resposta recebida: ${responsePacket.length} bytes de ${responsePacket.address.hostAddress}")
                log("[UDP] Hex da resposta: ${buffer.take(responsePacket.length).joinToString(" ") { "%02X".format(it) }}")
                return buffer.copyOf(responsePacket.length)
            } catch (e: java.net.SocketTimeoutException) {
                // Timeout é aceitável para comandos (alguns dispositivos não respondem)
                log("[UDP] ⏱️ Timeout ao receber resposta (normal para alguns comandos Tuya)")
                log("[UDP] Nota: Alguns dispositivos Tuya não enviam resposta de confirmação")
                return ByteArray(0) // Retorna array vazio indicando que o pacote foi enviado
            }
            
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "[UDP] ❌ Erro: IP não encontrado: $ip", e)
            return null
        } catch (e: java.net.SocketException) {
            Log.e(TAG, "[UDP] ❌ Erro de socket ao enviar pacote", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "[UDP] ❌ Erro ao enviar pacote UDP", e)
            e.printStackTrace()
            return null
        } finally {
            socket?.close()
            log("[UDP] Socket fechado")
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
    
    /**
     * Descobre dispositivos Tuya na rede local usando broadcast UDP
     * @return Lista de dispositivos encontrados
     */
    suspend fun discoverDevices(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<DiscoveredDevice>()
        var socket: DatagramSocket? = null
        
        try {
            log("[DISCOVERY] Iniciando descoberta de dispositivos Tuya...")
            
            socket = DatagramSocket().apply {
                soTimeout = DISCOVERY_TIMEOUT_MS
                broadcast = true // Permite broadcast
            }
            
            // Pacote de descoberta Tuya (formato simplificado)
            // Prefix: 0x000055AA, Command: 0x0000000A (DISCOVERY), Version: 0x00000000
            val discoveryPacket = ByteBuffer.allocate(20).apply {
                order(ByteOrder.BIG_ENDIAN)
                putInt(0x000055AA) // prefix
                putInt(0x00000000) // version
                putInt(0x0000000A) // command (0x0A = DISCOVERY)
                putInt(0x00000000) // length (0 para descoberta)
                putInt(0x00000000) // sequence
                putInt(0x0000AA55) // suffix
            }.array()
            
            log("[DISCOVERY] Enviando pacote de descoberta (broadcast)...")
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(discoveryPacket, discoveryPacket.size, broadcastAddress, DISCOVERY_PORT)
            socket.send(packet)
            
            log("[DISCOVERY] Aguardando respostas (timeout: ${DISCOVERY_TIMEOUT_MS}ms)...")
            
            // Recebe respostas até o timeout
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS) {
                try {
                    val buffer = ByteArray(1024)
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    socket.soTimeout = (DISCOVERY_TIMEOUT_MS - (System.currentTimeMillis() - startTime)).toInt().coerceAtLeast(100)
                    socket.receive(responsePacket)
                    
                    val deviceIp = responsePacket.address.hostAddress ?: continue
                    log("[DISCOVERY] Resposta recebida de $deviceIp: ${responsePacket.length} bytes")
                    
                    // Tenta extrair Device ID da resposta
                    val deviceId = extractDeviceIdFromResponse(buffer, responsePacket.length)
                    
                    if (deviceId != null && deviceId != "unknown") {
                        log("[DISCOVERY] ✅ Dispositivo encontrado: $deviceId @ $deviceIp")
                        devices.add(DiscoveredDevice(
                            deviceId = deviceId,
                            ip = deviceIp
                        ))
                    } else {
                        log("[DISCOVERY] ⚠️ Resposta recebida mas Device ID não identificado de $deviceIp")
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Timeout esperado quando não há mais respostas
                    break
                } catch (e: Exception) {
                    log("[DISCOVERY] Erro ao receber resposta: ${e.message}")
                }
            }
            
            log("[DISCOVERY] Descoberta concluída. ${devices.size} dispositivo(s) encontrado(s)")
            
        } catch (e: Exception) {
            Log.e(TAG, "[DISCOVERY] Erro na descoberta", e)
        } finally {
            socket?.close()
        }
        
        return@withContext devices
    }
    
    /**
     * Tenta extrair o Device ID da resposta de descoberta
     * O formato pode variar entre dispositivos
     */
    private fun extractDeviceIdFromResponse(data: ByteArray, length: Int): String? {
        try {
            // Tenta encontrar padrões comuns de Device ID (geralmente hex de 16 bytes = 32 chars)
            // Device ID geralmente está no payload após o header de 24 bytes
            if (length < 24) return null
            
            // Pula o header (24 bytes) e tenta ler o payload
            val payloadStart = 24
            if (length <= payloadStart) return null
            
            // Tenta decodificar como string (alguns dispositivos enviam Device ID como string)
            val payload = data.sliceArray(payloadStart until length)
            val payloadString = String(payload, Charsets.UTF_8).trim()
            
            // Procura por padrão de Device ID (geralmente hex de 16-32 caracteres)
            val deviceIdPattern = Regex("[0-9a-fA-F]{16,32}")
            val match = deviceIdPattern.find(payloadString)
            if (match != null) {
                return match.value.lowercase()
            }
            
            // Se não encontrou, tenta ler os primeiros bytes do payload como hex
            if (payload.size >= 16) {
                val hexId = payload.take(16).joinToString("") { "%02x".format(it) }
                return hexId
            }
            
        } catch (e: Exception) {
            log("[DISCOVERY] Erro ao extrair Device ID: ${e.message}")
        }
        
        return null
    }
    
    /**
     * Tenta descobrir se um IP específico é um dispositivo Tuya
     * Envia um pacote de descoberta diretamente para o IP
     */
    suspend fun probeDevice(ip: String): DiscoveredDevice? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        
        try {
            socket = DatagramSocket().apply {
                soTimeout = 500 // Timeout curto para escaneamento rápido
                broadcast = false
            }
            
            // Pacote de descoberta Tuya
            val discoveryPacket = ByteBuffer.allocate(20).apply {
                order(ByteOrder.BIG_ENDIAN)
                putInt(0x000055AA) // prefix
                putInt(0x00000000) // version
                putInt(0x0000000A) // command (0x0A = DISCOVERY)
                putInt(0x00000000) // length
                putInt(0x00000000) // sequence
                putInt(0x0000AA55) // suffix
            }.array()
            
            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(discoveryPacket, discoveryPacket.size, address, DISCOVERY_PORT)
            socket.send(packet)
            
            // Tenta receber resposta
            val buffer = ByteArray(1024)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)
            
            // Se recebeu resposta, é provavelmente um dispositivo Tuya
            val deviceId = extractDeviceIdFromResponse(buffer, responsePacket.length)
            
            return@withContext DiscoveredDevice(
                deviceId = deviceId ?: "unknown",
                ip = ip
            )
            
        } catch (e: java.net.SocketTimeoutException) {
            // Timeout = não é dispositivo Tuya ou não respondeu
            return@withContext null
        } catch (e: Exception) {
            // Erro = não é dispositivo Tuya
            return@withContext null
        } finally {
            socket?.close()
        }
    }
    
    private fun log(msg: String) {
        Log.d(TAG, msg)
    }
}

