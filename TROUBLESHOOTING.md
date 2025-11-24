# 🔧 Troubleshooting - Endpoint não carrega

## Verificação Rápida

### 1. Verifique os logs do servidor

**Com ADB conectado:**
```bash
adb logcat -s TuyaServerService:D | grep -E "START|SERVER|HTTP|ERRO"
```

**O que você DEVE ver:**
```
[START] Iniciando servidor Netty...
[SERVER] Servidor HTTP iniciado e escutando na porta 8000
[START] ✅ Servidor MRIT rodando!
```

**Se NÃO aparecer:**
- O servidor não está iniciando
- Verifique se há erros nos logs

### 2. Teste o endpoint raiz

Tente primeiro:
```
http://[IP_DO_DISPOSITIVO]:8000/
```

Deve retornar: `MRIT Server está rodando!`

### 3. Verifique se a porta está aberta

**No computador (mesma rede):**
```bash
# Linux/Mac
nc -zv [IP_DO_DISPOSITIVO] 8000

# Windows PowerShell
Test-NetConnection -ComputerName [IP_DO_DISPOSITIVO] -Port 8000
```

**Se não conectar:**
- Firewall bloqueando
- Servidor não está escutando
- IP incorreto

### 4. Teste com curl (verbose)

```bash
curl -v http://[IP_DO_DISPOSITIVO]:8000/health
```

**O que procurar:**
- `Connected to [IP]` - Conexão estabelecida
- `HTTP/1.1 200 OK` - Resposta do servidor
- Se aparecer `Connection refused` - Servidor não está escutando

### 5. Problemas Comuns

#### ❌ "Connection refused" ou timeout
**Causa:** Servidor não está escutando na porta

**Solução:**
1. Verifique logs: `adb logcat | grep TuyaServerService`
2. Procure por erros ao iniciar
3. Reinicie o servidor no app
4. Verifique se há outra app usando a porta 8000

#### ❌ Página não carrega no navegador
**Causa:** Firewall ou rede

**Solução:**
1. Desative VPN temporariamente
2. Verifique se está na mesma rede Wi-Fi
3. Tente de outro dispositivo na mesma rede
4. Use curl em vez do navegador

#### ❌ Servidor inicia mas não responde
**Causa:** Netty pode ter problema no Android

**Solução:**
1. Verifique logs completos
2. Tente reiniciar o dispositivo
3. Verifique se há atualizações do Android

### 6. Logs Completos para Debug

**Capture todos os logs:**
```bash
adb logcat -d > logs_completo.txt
```

**Procure por:**
- `[START]` - Inicialização
- `[SERVER]` - Status do servidor
- `[HTTP]` - Requisições recebidas
- `Exception` ou `Error` - Erros

### 7. Teste Local (no próprio dispositivo)

**Use um app de terminal Android:**
```bash
# Instale "Termux" ou similar
curl http://127.0.0.1:8000/health
# ou
curl http://localhost:8000/health
```

**Se funcionar localmente mas não de outros dispositivos:**
- Problema de rede/firewall
- Não está na mesma rede Wi-Fi

### 8. Verificar se o servidor está realmente rodando

**No app:**
- Status deve mostrar "Rodando na porta 8000" (verde)
- Deve haver notificação permanente

**Nos logs:**
- Deve aparecer `[SERVER] Servidor HTTP iniciado e escutando na porta 8000`

**Se não aparecer:**
- O servidor não iniciou corretamente
- Verifique erros nos logs

---

**Envie os logs se ainda não funcionar:**
```bash
adb logcat -d | grep -i "TuyaServerService\|Netty\|Ktor" > logs_servidor.txt
```


