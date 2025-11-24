# 🧪 Como Testar o MRIT Server

## 1. Gerar o APK

### No GitHub Codespaces:
```bash
./gradlew assembleDebug
# O APK estará em: app/build/outputs/apk/debug/app-debug.apk
```

### No GitHub Actions:
- Faça um push para o repositório
- Vá na aba **"Actions"** do GitHub
- Baixe o APK gerado automaticamente

## 2. Instalar no Dispositivo Android

### Via ADB (se tiver conectado):
```bash
adb install app-debug.apk
```

### Via Transferência:
1. Transfira o APK para o dispositivo
2. Abra o arquivo no dispositivo
3. Permita "Instalar de fontes desconhecidas" se necessário
4. Instale o app

## 3. Configurar e Iniciar

1. **Abra o app "MRIT Server"**
2. **Configure o site:**
   - Clique em "Configurar Site"
   - Digite o nome (ex: `GELAFIT_SP01`)
   - Clique em "Salvar"

3. **Inicie o servidor:**
   - Clique em "Iniciar Servidor"
   - Deve aparecer uma notificação permanente
   - O status deve mudar para verde "Rodando na porta 8000"

## 4. Testar os Endpoints HTTP

### Descobrir o IP do dispositivo:
- Vá em **Configurações → Sobre o telefone → Status → Endereço IP**
- Ou use um app de rede para ver o IP local (ex: 192.168.1.100)

### Teste 1: Health Check
```bash
# No computador na mesma rede Wi-Fi:
curl http://192.168.1.100:8000/health
```

**Resposta esperada:**
```json
{
  "status": "ok",
  "site": "GELAFIT_SP01"
}
```

### Teste 2: Comando Tuya
```bash
curl -X POST http://192.168.1.100:8000/tuya/command \
  -H "Content-Type: application/json" \
  -d '{
    "action": "on",
    "tuya_device_id": "bf1234567890abcdef",
    "local_key": "abc123def456",
    "lan_ip": "192.168.1.50"
  }'
```

**Resposta esperada (sucesso):**
```json
{
  "ok": true
}
```

**Resposta esperada (erro):**
```json
{
  "ok": false,
  "error": "Mensagem de erro"
}
```

## 5. Testar com Postman ou Insomnia

### Health Check:
- **Método:** GET
- **URL:** `http://[IP_DO_DISPOSITIVO]:8000/health`
- **Headers:** Nenhum necessário

### Tuya Command:
- **Método:** POST
- **URL:** `http://[IP_DO_DISPOSITIVO]:8000/tuya/command`
- **Headers:**
  ```
  Content-Type: application/json
  ```
- **Body (JSON):**
  ```json
  {
    "action": "on",
    "tuya_device_id": "bf1234567890abcdef",
    "local_key": "abc123def456",
    "lan_ip": "192.168.1.50"
  }
  ```

## 6. Verificar Logs (Debug)

### Via ADB:
```bash
# Ver todos os logs do app
adb logcat | grep -i "TuyaServerService\|MRIT"

# Ver apenas erros
adb logcat | grep -i "ERROR\|FATAL"

# Ver logs em tempo real
adb logcat -s TuyaServerService:D MainActivity:D
```

### O que procurar nos logs:
- `[START] Servidor MRIT local rodando em http://0.0.0.0:8000`
- `[INFO] Enviando 'on' → [device_id] @ [ip]`
- `[OK] Comando enviado com sucesso`

## 7. Problemas Comuns

### Servidor não inicia:
- Verifique se há notificação permanente
- Verifique os logs com `adb logcat`
- Certifique-se de estar na mesma rede Wi-Fi

### Não consegue conectar:
- Verifique o firewall do dispositivo
- Verifique se o IP está correto
- Teste com `ping [IP_DO_DISPOSITIVO]` primeiro

### Comando Tuya não funciona:
- Verifique se o `tuya_device_id`, `local_key` e `lan_ip` estão corretos
- Verifique se o dispositivo Tuya está na mesma rede
- Veja os logs para erros específicos

## 8. Teste Completo (Script)

Crie um arquivo `test.sh`:

```bash
#!/bin/bash

# Configure o IP do seu dispositivo
DEVICE_IP="192.168.1.100"

echo "🧪 Testando MRIT Server..."
echo ""

echo "1. Health Check:"
curl -s http://$DEVICE_IP:8000/health | jq .
echo ""

echo "2. Comando Tuya (ON):"
curl -s -X POST http://$DEVICE_IP:8000/tuya/command \
  -H "Content-Type: application/json" \
  -d '{
    "action": "on",
    "tuya_device_id": "SEU_DEVICE_ID",
    "local_key": "SUA_LOCAL_KEY",
    "lan_ip": "IP_DO_DISPOSITIVO_TUYA"
  }' | jq .
echo ""

echo "✅ Teste concluído!"
```

Execute:
```bash
chmod +x test.sh
./test.sh
```

## 9. Testar no Navegador

Abra no navegador (mesma rede Wi-Fi):
```
http://[IP_DO_DISPOSITIVO]:8000/health
```

Deve mostrar o JSON com status e site.

---

**Dica:** Use o app **Fing** ou **Network Scanner** no Android para descobrir o IP do dispositivo facilmente!


