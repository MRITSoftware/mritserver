package com.tuyaserver

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

class TuyaClient(private val context: Context? = null) {
    
    companion object {
        private const val TAG = "TuyaClient"
        private const val DEFAULT_PROTOCOL_VERSION = 3.4 // Padrão 3.4
        private const val PORT = 6668 // Porta para comandos
        private const val DISCOVERY_PORT = 6666 // Porta para descoberta
        private const val TIMEOUT_MS = 8000 // Timeout de 8s para dar tempo do dispositivo responder
        private const val DISCOVERY_TIMEOUT_MS = 10000 // 10s para descoberta (dispositivos podem demorar para responder)
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
            log("[INFO] ⚠️ VERIFIQUE: O IP está correto? No Python você usou 192.168.0.193")
            log("[INFO] Protocolo: $protocolVersion ${if (protocolVersion >= 3.4) "(3.4 - com timestamp)" else "(3.3)"}")
            log("[INFO] Ação: $action")
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
            
            // Para protocolo 3.4, tenta primeiro com timestamp no sequence (como tinytuya faz)
            // Tentativa 1: Protocolo 3.4 com timestamp no sequence (se for 3.4)
            if (protocolVersion >= 3.4) {
                log("[INFO] Tentativa 1: Protocolo $protocolVersion, formato numérico, sequence=timestamp (como tinytuya)")
                var payload = buildCommandPayload(commandNumeric, localKey, protocolVersion, sequenceZero = false)
                for (attempt in 1..3) {
                    log("[INFO] Enviando tentativa $attempt de 3 com protocolo $protocolVersion e timestamp no sequence")
                    val response = sendUdpPacket(lanIp, PORT, payload)
                    lastResponse = response
                    if (response != null && response.isNotEmpty()) {
                        log("[DEBUG] ✅ Resposta recebida com protocolo $protocolVersion e timestamp!")
                        success = true
                        break
                    }
                    if (attempt < 3) kotlinx.coroutines.delay(300)
                }
            }
            
            // Tentativa 2: Protocolo informado, formato numérico, sequence=0
            if (!success) {
                log("[INFO] Tentativa 2: Protocolo $protocolVersion (informado), formato numérico, sequence=0")
                var payload = buildCommandPayload(commandNumeric, localKey, protocolVersion, sequenceZero = true)
                for (attempt in 1..3) {
                    log("[INFO] Enviando tentativa $attempt de 3 com protocolo $protocolVersion")
                    val response = sendUdpPacket(lanIp, PORT, payload)
                    lastResponse = response
                    if (response != null && response.isNotEmpty()) {
                        log("[DEBUG] ✅ Resposta recebida com protocolo $protocolVersion!")
                        success = true
                        break
                    }
                    if (attempt < 3) kotlinx.coroutines.delay(300)
                }
            }
            
            // Tentativa 3: Outro protocolo (3.3 ou 3.4), formato numérico
            if (!success) {
                log("[INFO] Tentativa 3: Protocolo $otherProtocol (alternativo), formato numérico, sequence=0")
                var payload = buildCommandPayload(commandNumeric, localKey, otherProtocol, sequenceZero = true)
                for (attempt in 1..3) {
                    log("[INFO] Enviando tentativa $attempt de 3 com protocolo $otherProtocol")
                    val response = sendUdpPacket(lanIp, PORT, payload)
                    lastResponse = response
                    if (response != null && response.isNotEmpty()) {
                        log("[DEBUG] ✅ Resposta recebida com protocolo $otherProtocol!")
                        success = true
                        break
                    }
                    if (attempt < 3) kotlinx.coroutines.delay(300)
                }
            }
            
            // Tentativa 4: Protocolo informado, formato booleano
            if (!success) {
                log("[INFO] Tentativa 4: Protocolo $protocolVersion (informado), formato booleano, sequence=0")
                var payload = buildCommandPayload(commandBoolean, localKey, protocolVersion, sequenceZero = true)
                for (attempt in 1..2) {
                    log("[INFO] Enviando tentativa $attempt de 2 com formato booleano")
                    val response = sendUdpPacket(lanIp, PORT, payload)
                    lastResponse = response
                    if (response != null && response.isNotEmpty()) {
                        log("[DEBUG] ✅ Resposta recebida com formato booleano!")
                        success = true
                        break
                    }
                    if (attempt < 2) kotlinx.coroutines.delay(300)
                }
            }
            
            log("[INFO] Protocolo Tuya solicitado: $protocolVersion")
            if (success) {
                log("[OK] ✅ Comando enviado com sucesso e confirmado pelo dispositivo")
                Result.success(Unit)
            } else {
                log("[ERROR] ❌ Comando NÃO foi confirmado pelo dispositivo após todas as tentativas")
                log("[ERROR] Detalhes:")
                log("[ERROR]   - IP: $lanIp")
                log("[ERROR]   - Device ID: $deviceId")
                log("[ERROR]   - Protocolo tentado: $protocolVersion")
                log("[ERROR]   - Última resposta: ${if (lastResponse != null) "recebida mas vazia" else "nenhuma resposta"}")
                log("[ERROR] Possíveis causas:")
                log("[ERROR]   1. IP incorreto ou dispositivo offline")
                log("[ERROR]   2. Local Key incorreta")
                log("[ERROR]   3. Dispositivo em outra rede")
                log("[ERROR]   4. Formato do pacote incompatível com o dispositivo")
                // Retorna falha para que o usuário saiba que não funcionou
                Result.failure(RuntimeException("Falha: dispositivo não respondeu. Verifique IP ($lanIp), Local Key e se o dispositivo está na mesma rede."))
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
        
            // Sequence number: 
            // Protocolo 3.3: geralmente usa 0
            // Protocolo 3.4: pode usar timestamp ou 0, dependendo do dispositivo
            val sequence = if (sequenceZero) {
                0x00000000 // Usa 0 (mais comum)
            } else if (protocolVersion >= 3.4) {
                timestamp // Usa timestamp para 3.4 quando sequenceZero = false
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
        
        // Protocolo Tuya usa LITTLE_ENDIAN para inteiros de 32 bits
        val packet = ByteBuffer.allocate(totalSize).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(0x000055AA) // prefix (será escrito como AA 55 00 00 em little-endian)
            putInt(protocolVersionInt) // version (0 = 3.3 ou 3.4)
            putInt(0x0000000D) // command (0x0D = CONTROL)
            putInt(encrypted.size) // length
            putInt(sequence) // sequence (timestamp para 3.4)
            putInt(0x00000000) // return code
            put(encrypted) // payload criptografado
            putInt(0x0000AA55) // suffix (será escrito como 55 AA 00 00 em little-endian)
        }
        
        val packetArray = packet.array()
        log("[DEBUG] Pacote completo: ${packetArray.size} bytes")
        log("[DEBUG] Header completo (24 bytes): ${packetArray.take(24).joinToString(" ") { "%02X".format(it) }}")
        log("[DEBUG] Prefix: ${packetArray.take(4).joinToString(" ") { "%02X".format(it) }} (little-endian: AA 55 00 00 = 0x000055AA)")
        log("[DEBUG] Version: ${packetArray.slice(4..7).joinToString(" ") { "%02X".format(it) }}")
        log("[DEBUG] Command: ${packetArray.slice(8..11).joinToString(" ") { "%02X".format(it) }} (little-endian: 0D 00 00 00 = 0x0000000D)")
        log("[DEBUG] Length: ${packetArray.slice(12..15).joinToString(" ") { "%02X".format(it) }} (${encrypted.size} bytes, little-endian)")
        log("[DEBUG] Sequence: ${packetArray.slice(16..19).joinToString(" ") { "%02X".format(it) }} (little-endian)")
        log("[DEBUG] Return code: ${packetArray.slice(20..23).joinToString(" ") { "%02X".format(it) }}")
        log("[DEBUG] Suffix: ${packetArray.takeLast(4).joinToString(" ") { "%02X".format(it) }} (little-endian: 55 AA 00 00 = 0x0000AA55)")
        
        return packetArray
    }
    
    /**
     * Envia pacote UDP para o dispositivo
     */
    private fun sendUdpPacket(ip: String, port: Int, data: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        var multicastLock: WifiManager.MulticastLock? = null
        
        try {
            // No Android, pode precisar de MulticastLock mesmo para envio direto
            if (context != null) {
                try {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    multicastLock = wifiManager?.createMulticastLock("TuyaCommand")
                    multicastLock?.setReferenceCounted(true)
                    multicastLock?.acquire()
                    log("[UDP] MulticastLock adquirido para envio")
                } catch (e: Exception) {
                    log("[UDP] ⚠️ Erro ao adquirir MulticastLock: ${e.message}")
                }
            }
            
            log("[UDP] Criando socket UDP...")
            socket = DatagramSocket().apply {
                soTimeout = TIMEOUT_MS
                broadcast = false // Não usar broadcast para envio direto
                reuseAddress = true // Permite reutilizar endereço
            }
            
            log("[UDP] Resolvendo endereço: $ip")
            val address = try {
                InetAddress.getByName(ip)
            } catch (e: Exception) {
                log("[UDP] ❌ Erro ao resolver IP: ${e.message}")
                return null
            }
            
            if (address == null) {
                log("[UDP] ❌ Endereço IP inválido: $ip")
                return null
            }
            
            log("[UDP] Endereço resolvido: ${address.hostAddress}")
            log("[UDP] Socket local: ${socket.localAddress?.hostAddress}:${socket.localPort}")
            
            // Verifica se o IP é válido e está na mesma rede
            if (address.isLoopbackAddress) {
                log("[UDP] ⚠️ AVISO: IP é loopback (127.0.0.1) - não vai funcionar!")
            }
            if (address.isMulticastAddress) {
                log("[UDP] ⚠️ AVISO: IP é multicast - não vai funcionar!")
            }
            
            val packet = DatagramPacket(data, data.size, address, port)
            log("[UDP] Enviando pacote para ${address.hostAddress}:$port (${data.size} bytes)")
            log("[UDP] Primeiros 32 bytes do pacote: ${data.take(32).joinToString(" ") { "%02X".format(it) }}")
            
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
                // Timeout - dispositivo não respondeu
                log("[UDP] ⏱️ Timeout ao receber resposta após ${TIMEOUT_MS}ms")
                log("[UDP] ⚠️ Dispositivo não respondeu - pode ser que:")
                log("[UDP]   1. O pacote não foi recebido pelo dispositivo")
                log("[UDP]   2. O formato do pacote está incorreto")
                log("[UDP]   3. A chave local está incorreta")
                log("[UDP]   4. O dispositivo está offline ou em outra rede")
                // Retorna null para indicar que não houve resposta
                return null
            }
            
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "[UDP] ❌ Erro: IP não encontrado: $ip", e)
            e.printStackTrace()
            return null
        } catch (e: java.net.SocketException) {
            Log.e(TAG, "[UDP] ❌ Erro de socket ao enviar pacote: ${e.message}", e)
            e.printStackTrace()
            return null
        } catch (e: java.io.IOException) {
            Log.e(TAG, "[UDP] ❌ Erro de IO ao enviar pacote: ${e.message}", e)
            e.printStackTrace()
            return null
        } catch (e: Exception) {
            Log.e(TAG, "[UDP] ❌ Erro ao enviar pacote UDP: ${e.javaClass.simpleName}: ${e.message}", e)
            e.printStackTrace()
            return null
        } finally {
            socket?.close()
            multicastLock?.release()
            log("[UDP] Socket fechado e MulticastLock liberado")
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
        var multicastLock: WifiManager.MulticastLock? = null
        
        try {
            log("[DISCOVERY] Iniciando descoberta de dispositivos Tuya...")
            
            // No Android, precisa de MulticastLock para broadcast UDP funcionar
            if (context != null) {
                try {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    multicastLock = wifiManager?.createMulticastLock("TuyaDiscovery")
                    multicastLock?.setReferenceCounted(true)
                    multicastLock?.acquire()
                    log("[DISCOVERY] MulticastLock adquirido")
                } catch (e: Exception) {
                    log("[DISCOVERY] ⚠️ Erro ao adquirir MulticastLock: ${e.message}")
                }
            }
            
            socket = DatagramSocket().apply {
                soTimeout = DISCOVERY_TIMEOUT_MS
                broadcast = true // Permite broadcast
                reuseAddress = true // Permite reutilizar endereço
            }
            
            // Pacote de descoberta Tuya
            // Formato: prefix(4) + version(4) + command(4) + length(4) + sequence(4) + return_code(4) + suffix(4) = 28 bytes
            // Prefix: 0x000055AA, Command: 0x0000000A (DISCOVERY), Version: 0x00000000
            // Protocolo Tuya usa LITTLE_ENDIAN
            val discoveryPacket = ByteBuffer.allocate(28).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                putInt(0x000055AA) // prefix
                putInt(0x00000000) // version
                putInt(0x0000000A) // command (0x0A = DISCOVERY)
                putInt(0x00000000) // length (0 para descoberta)
                putInt(0x00000000) // sequence
                putInt(0x00000000) // return code
                putInt(0x0000AA55) // suffix
            }.array()
            
            log("[DISCOVERY] Pacote de descoberta criado: ${discoveryPacket.size} bytes")
            log("[DISCOVERY] Hex do pacote: ${discoveryPacket.joinToString(" ") { "%02X".format(it) }}")
            
            log("[DISCOVERY] Enviando pacote de descoberta (broadcast)...")
            
            // Tenta enviar para broadcast e também para IPs de rede local
            try {
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(discoveryPacket, discoveryPacket.size, broadcastAddress, DISCOVERY_PORT)
                socket.send(packet)
                log("[DISCOVERY] ✅ Pacote broadcast enviado para 255.255.255.255:$DISCOVERY_PORT")
            } catch (e: Exception) {
                log("[DISCOVERY] ⚠️ Erro ao enviar broadcast: ${e.message}")
            }
            
            // Também tenta enviar para o endereço de broadcast da rede local
            try {
                val localBroadcast = getLocalBroadcastAddress()
                if (localBroadcast != null) {
                    val packet = DatagramPacket(discoveryPacket, discoveryPacket.size, localBroadcast, DISCOVERY_PORT)
                    socket.send(packet)
                    log("[DISCOVERY] ✅ Pacote broadcast enviado para ${localBroadcast.hostAddress}:$DISCOVERY_PORT")
                }
            } catch (e: Exception) {
                log("[DISCOVERY] ⚠️ Erro ao enviar para broadcast local: ${e.message}")
            }
            
            log("[DISCOVERY] Aguardando respostas (timeout: ${DISCOVERY_TIMEOUT_MS}ms)...")
            log("[DISCOVERY] Escutando na porta ${socket.localPort}")
            
            // Recebe respostas até o timeout
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS) {
                try {
                    val remainingTime = DISCOVERY_TIMEOUT_MS - (System.currentTimeMillis() - startTime)
                    if (remainingTime <= 0) break
                    
                    val buffer = ByteArray(1024)
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    socket.soTimeout = remainingTime.coerceAtLeast(500).toInt() // Mínimo 500ms, converte para Int
                    socket.receive(responsePacket)
                    
                    val deviceIp = responsePacket.address.hostAddress ?: continue
                    log("[DISCOVERY] Resposta recebida de $deviceIp: ${responsePacket.length} bytes")
                    
                    // Tenta extrair Device ID da resposta
                    val deviceId = extractDeviceIdFromResponse(buffer, responsePacket.length)
                    
                    // Adiciona dispositivo mesmo se não conseguir extrair Device ID
                    // O IP é suficiente para enviar comandos
                    if (deviceId != null && deviceId != "unknown") {
                        log("[DISCOVERY] ✅ Dispositivo encontrado: $deviceId @ $deviceIp")
                        devices.add(DiscoveredDevice(
                            deviceId = deviceId,
                            ip = deviceIp
                        ))
                    } else {
                        // Gera um Device ID temporário baseado no IP para permitir uso
                        val tempDeviceId = deviceIp.replace(".", "").take(16).padEnd(16, '0')
                        log("[DISCOVERY] ⚠️ Device ID não identificado, usando temporário: $tempDeviceId @ $deviceIp")
                        log("[DISCOVERY] ✅ Dispositivo Tuya encontrado (sem Device ID): $deviceIp")
                        devices.add(DiscoveredDevice(
                            deviceId = tempDeviceId,
                            ip = deviceIp
                        ))
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
            e.printStackTrace()
        } finally {
            socket?.close()
            multicastLock?.release()
            log("[DISCOVERY] MulticastLock liberado")
        }
        
        return@withContext devices
    }
    
    /**
     * Obtém o endereço de broadcast da rede local
     */
    private fun getLocalBroadcastAddress(): InetAddress? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.interfaceAddresses
                    for (address in addresses) {
                        val broadcast = address.broadcast
                        if (broadcast != null) {
                            log("[DISCOVERY] Broadcast local encontrado: ${broadcast.hostAddress}")
                            return broadcast
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            log("[DISCOVERY] Erro ao obter broadcast local: ${e.message}")
            null
        }
    }
    
    /**
     * Tenta extrair o Device ID da resposta de descoberta
     * O formato pode variar entre dispositivos
     */
    private fun extractDeviceIdFromResponse(data: ByteArray, length: Int): String? {
        try {
            log("[DISCOVERY] Tentando extrair Device ID de ${length} bytes")
            log("[DISCOVERY] Primeiros 64 bytes: ${data.take(64).joinToString(" ") { "%02X".format(it) }}")
            
            // Header Tuya tem 24 bytes: prefix(4) + version(4) + command(4) + length(4) + sequence(4) + return_code(4)
            if (length < 24) {
                log("[DISCOVERY] Resposta muito curta (${length} bytes), mínimo 24 bytes")
                return null
            }
            
            // Verifica se é um pacote Tuya válido
            val prefix = (data[0].toInt() shl 24) or (data[1].toInt() shl 16) or (data[2].toInt() shl 8) or data[3].toInt()
            if (prefix != 0x000055AA) {
                log("[DISCOVERY] Prefix inválido: 0x${prefix.toString(16)}")
                return null
            }
            
            // Pula o header (24 bytes) e tenta ler o payload
            val payloadStart = 24
            if (length <= payloadStart) {
                log("[DISCOVERY] Sem payload após header")
                return null
            }
            
            val payload = data.sliceArray(payloadStart until length)
            log("[DISCOVERY] Payload: ${payload.size} bytes")
            log("[DISCOVERY] Payload hex: ${payload.take(64).joinToString(" ") { "%02X".format(it) }}")
            
            // Tenta decodificar como string (alguns dispositivos enviam Device ID como string)
            try {
                val payloadString = String(payload, Charsets.UTF_8).trim()
                log("[DISCOVERY] Payload como string: $payloadString")
                
                // Procura por padrão de Device ID (geralmente hex de 16-32 caracteres)
                val deviceIdPattern = Regex("[0-9a-fA-F]{16,32}")
                val match = deviceIdPattern.find(payloadString)
                if (match != null) {
                    log("[DISCOVERY] ✅ Device ID encontrado via regex: ${match.value.lowercase()}")
                    return match.value.lowercase()
                }
            } catch (e: Exception) {
                log("[DISCOVERY] Payload não é string válida: ${e.message}")
            }
            
            // Se não encontrou como string, tenta ler os primeiros bytes do payload como hex
            // Device ID geralmente tem 16 bytes (32 caracteres hex)
            if (payload.size >= 16) {
                val hexId = payload.take(16).joinToString("") { "%02x".format(it) }
                log("[DISCOVERY] ✅ Device ID extraído como hex: $hexId")
                return hexId
            }
            
            // Tenta ler como JSON se o payload for grande o suficiente
            if (payload.size > 20) {
                try {
                    val jsonString = String(payload, Charsets.UTF_8)
                    log("[DISCOVERY] Tentando parsear como JSON: $jsonString")
                    // Se for JSON, pode ter o device ID em algum campo
                    // Por enquanto, retorna null e deixa o dispositivo ser adicionado com IP
                } catch (e: Exception) {
                    // Não é JSON válido
                }
            }
            
        } catch (e: Exception) {
            log("[DISCOVERY] Erro ao extrair Device ID: ${e.message}")
            e.printStackTrace()
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
            
            // Pacote de descoberta Tuya (formato completo com 28 bytes)
            // Protocolo Tuya usa LITTLE_ENDIAN
            val discoveryPacket = ByteBuffer.allocate(28).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                putInt(0x000055AA) // prefix
                putInt(0x00000000) // version
                putInt(0x0000000A) // command (0x0A = DISCOVERY)
                putInt(0x00000000) // length
                putInt(0x00000000) // sequence
                putInt(0x00000000) // return code
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
        LogCollector.addLog(TAG, msg, "D")
    }
}

