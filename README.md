# Tuya Server Android

Servidor HTTP Android para controlar dispositivos Tuya localmente, convertido do código Python original.

## Funcionalidades

- ✅ Endpoint `/health` - Verifica status do servidor
- ✅ Endpoint `/tuya/command` - Envia comandos para dispositivos Tuya
- ✅ Gerenciamento de configuração (site_name)
- ✅ Serviço Android rodando em background
- ✅ Interface simples para iniciar/parar servidor

## Como Usar

1. Abra o projeto no Android Studio
2. Configure o nome do site através do botão "Configurar Site" na primeira execução
3. Clique em "Iniciar Servidor" para iniciar o servidor HTTP na porta 8000
4. O servidor ficará rodando em background mesmo se fechar o app

## API

### GET /health
Retorna status do servidor:
```json
{
  "status": "ok",
  "site": "GELAFIT_SP01"
}
```

### POST /tuya/command
Envia comando para dispositivo Tuya:
```json
{
  "action": "on",
  "tuya_device_id": "bf1234567890abcdef",
  "local_key": "abc123def456",
  "lan_ip": "192.168.1.100"
}
```

Resposta de sucesso:
```json
{
  "ok": true
}
```

Resposta de erro:
```json
{
  "ok": false,
  "error": "Mensagem de erro"
}
```

## Requisitos

- Android 7.0 (API 24) ou superior
- Permissões de Internet e Rede

## Tecnologias

- Kotlin
- Ktor (servidor HTTP)
- Android Service (background)
- UDP para comunicação Tuya

