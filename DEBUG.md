# 🔍 Guia de Debug - MRIT Server

## Problema: Endpoint não está respondendo

### 1. Verificar se o servidor está rodando

**No dispositivo Android:**
1. Abra o app "MRIT Server"
2. Verifique se o status mostra "Servidor: Rodando na porta 8000" (verde)
3. Verifique se há uma notificação permanente no topo

**Se não estiver rodando:**
- Clique em "Iniciar Servidor"
- Aguarde alguns segundos
- Verifique novamente o status

### 2. Verificar logs do servidor

**Via ADB (se tiver conectado):**
```bash
# Ver todos os logs do servidor
adb logcat | grep -i "TuyaServerService"

# Ver apenas erros
adb logcat | grep -i "ERROR\|FATAL\|Exception"

# Ver logs em tempo real
adb logcat -s TuyaServerService:D MainActivity:D
```

**O que procurar:**
- `[START] Servidor MRIT local rodando em http://0.0.0.0:8000`
- `[HTTP] GET /health recebido` (quando testar)
- `[HTTP] POST /tuya/command recebido` (quando testar)

### 3. Verificar IP do dispositivo

**No dispositivo:**
- Configurações → Sobre o telefone → Status → Endereço IP
- Ou use um app como "Fing" ou "Network Scanner"

**Importante:** O IP deve ser da rede local (ex: 192.168.1.x)

### 4. Testar conectividade básica

**No computador (mesma rede Wi-Fi):**
```bash
# Ping no dispositivo
ping [IP_DO_DISPOSITIVO]

# Teste se a porta está aberta
telnet [IP_DO_DISPOSITIVO] 8000
# Ou
nc -zv [IP_DO_DISPOSITIVO] 8000
```

### 5. Testar endpoint /health

**No navegador (mesma rede):**
```
http://[IP_DO_DISPOSITIVO]:8000/health
```

**Com curl:**
```bash
curl -v http://[IP_DO_DISPOSITIVO]:8000/health
```

**Resposta esperada:**
```json
{
  "status": "ok",
  "site": "SEU_SITE_NAME"
}
```

### 6. Verificar firewall/VPN

**Problemas comuns:**
- Firewall do dispositivo bloqueando conexões
- VPN ativa no dispositivo ou computador
- Rede Wi-Fi com isolamento de clientes (guest network)

**Solução:**
- Desative VPN temporariamente
- Verifique configurações de firewall
- Use a mesma rede Wi-Fi (não guest)

### 7. Testar com protocolo 3.4

**Exemplo de requisição:**
```bash
curl -X POST http://[IP_DO_DISPOSITIVO]:8000/tuya/command \
  -H "Content-Type: application/json" \
  -d '{
    "action": "on",
    "tuya_device_id": "SEU_DEVICE_ID",
    "local_key": "SUA_LOCAL_KEY",
    "lan_ip": "IP_DO_DISPOSITIVO_TUYA",
    "protocol_version": 3.4
  }'
```

**Ou sem especificar (padrão agora é 3.4):**
```bash
curl -X POST http://[IP_DO_DISPOSITIVO]:8000/tuya/command \
  -H "Content-Type: application/json" \
  -d '{
    "action": "on",
    "tuya_device_id": "SEU_DEVICE_ID",
    "local_key": "SUA_LOCAL_KEY",
    "lan_ip": "IP_DO_DISPOSITIVO_TUYA"
  }'
```

### 8. Verificar logs durante o teste

**Enquanto testa, rode em outro terminal:**
```bash
adb logcat -s TuyaServerService:D | grep -E "HTTP|ERRO|START|INFO"
```

**Você deve ver:**
```
[HTTP] GET /health recebido
[HTTP] GET /health respondido com sucesso
```

**Ou para /tuya/command:**
```
[HTTP] POST /tuya/command recebido
[HTTP] Request recebido: action=on, protocol=3.4
[HTTP] Enviando comando Tuya com protocolo 3.4
[INFO] Enviando 'on' → [device_id] @ [ip]
[INFO] Protocolo Tuya 3.4 usado
[OK] Comando enviado com sucesso
[HTTP] Comando Tuya enviado com sucesso
```

### 9. Problemas comuns e soluções

#### Servidor não inicia
- **Sintoma:** Status sempre mostra "Parado"
- **Solução:** 
  - Verifique logs: `adb logcat | grep -i "TuyaServerService"`
  - Verifique se há notificação
  - Reinicie o app

#### Endpoint não responde
- **Sintoma:** Timeout ou "Connection refused"
- **Soluções:**
  1. Verifique se está na mesma rede Wi-Fi
  2. Verifique o IP do dispositivo
  3. Verifique se o servidor está rodando (status verde)
  4. Verifique logs para erros

#### Erro "MissingForegroundServiceTypeException"
- **Sintoma:** App fecha ao iniciar servidor
- **Solução:** Atualize para a versão mais recente do APK

#### Protocolo não funciona
- **Sintoma:** Comando Tuya não executa
- **Soluções:**
  1. Tente protocolo 3.3: `"protocol_version": 3.3`
  2. Tente protocolo 3.4: `"protocol_version": 3.4`
  3. Verifique se device_id, local_key e lan_ip estão corretos
  4. Verifique logs do TuyaClient

### 10. Script de teste completo

Crie `test_server.sh`:

```bash
#!/bin/bash

DEVICE_IP="192.168.1.100"  # Altere para o IP do seu dispositivo

echo "🔍 Testando MRIT Server..."
echo ""

echo "1. Ping no dispositivo:"
ping -c 2 $DEVICE_IP
echo ""

echo "2. Teste de porta 8000:"
timeout 3 bash -c "echo > /dev/tcp/$DEVICE_IP/8000" && echo "✅ Porta 8000 aberta" || echo "❌ Porta 8000 fechada"
echo ""

echo "3. Health Check:"
curl -v -m 5 http://$DEVICE_IP:8000/health
echo ""

echo "4. Teste Tuya Command (protocolo 3.4):"
curl -v -X POST http://$DEVICE_IP:8000/tuya/command \
  -H "Content-Type: application/json" \
  -d '{
    "action": "on",
    "tuya_device_id": "SEU_DEVICE_ID",
    "local_key": "SUA_LOCAL_KEY",
    "lan_ip": "IP_DO_DISPOSITIVO_TUYA",
    "protocol_version": 3.4
  }'
echo ""

echo "✅ Teste concluído!"
```

Execute:
```bash
chmod +x test_server.sh
./test_server.sh
```

---

**Dica:** Se ainda não funcionar, envie os logs completos:
```bash
adb logcat -d > logs.txt
```

