# 🔴 Problema: Página não carrega

## Possíveis Causas e Soluções

### 1. Servidor não está realmente escutando

**Verifique nos logs:**
```bash
adb logcat -s TuyaServerService:D | grep -E "START|SERVER|TEST"
```

**O que DEVE aparecer:**
```
[START] Iniciando servidor Netty...
[START] Comando start() executado
[SERVER] Servidor HTTP iniciado e escutando na porta 8000
[START] ✅ Servidor escutando em: http://0.0.0.0:8000
[TEST] ✅ Teste local bem-sucedido! Servidor respondendo na porta 8000
```

**Se NÃO aparecer:**
- O servidor não iniciou corretamente
- Pode haver erro no Netty no Android
- Verifique erros completos nos logs

### 2. Problema com Netty no Android

O Netty pode ter problemas em alguns dispositivos Android. **Sintomas:**
- Servidor inicia mas não responde
- Logs mostram que iniciou mas conexões falham
- Teste local falha

**Solução temporária:** Verifique se há atualizações do Ktor ou use outro engine.

### 3. Firewall bloqueando

**Android pode ter firewall ativo:**
- Verifique configurações de rede
- Desative temporariamente qualquer firewall/VPN
- Teste de outro dispositivo na mesma rede

### 4. IP incorreto

**Verifique:**
- Use o IP que aparece no app (copie com o botão)
- Certifique-se que é o IP da rede Wi-Fi (não dados móveis)
- Teste com `ping [IP]` primeiro

### 5. Porta ocupada

**Verifique se outra app está usando a porta 8000:**
```bash
# No dispositivo (se tiver terminal)
netstat -an | grep 8000
```

**Solução:** Pare outras apps que possam estar usando a porta.

### 6. Teste Local Primeiro

**Teste se o servidor responde localmente:**
- Use um app de terminal no Android (Termux)
- Execute: `curl http://127.0.0.1:8000/health`

**Se funcionar localmente mas não de outros dispositivos:**
- Problema de rede/firewall
- Não está na mesma rede Wi-Fi

### 7. Verificar Logs Completos

**Capture todos os logs:**
```bash
adb logcat -d > logs_completo.txt
```

**Procure por:**
- `Exception` ou `Error` relacionados ao Netty
- `BindException` - porta já em uso
- `SocketException` - problema de rede
- `[TEST]` - resultado do teste local

### 8. Solução Alternativa: Usar CIOServer

Se o Netty não funcionar, podemos trocar para CIOServer (outro engine do Ktor que funciona melhor no Android).

---

## Checklist de Debug

- [ ] Servidor mostra "Rodando" no app (verde)
- [ ] Notificação permanente aparece
- [ ] Logs mostram `[SERVER] Servidor HTTP iniciado`
- [ ] Logs mostram `[TEST] ✅ Teste local bem-sucedido`
- [ ] IP está correto (copiado do app)
- [ ] Mesma rede Wi-Fi
- [ ] Sem VPN ativa
- [ ] Teste local funciona (127.0.0.1)
- [ ] Porta 8000 não está ocupada

---

**Se nada funcionar, envie os logs completos:**
```bash
adb logcat -d | grep -i "TuyaServerService\|Netty\|Ktor\|Exception" > logs_servidor.txt
```

