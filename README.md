# Tuya Server Android

Servidor HTTP Android para controlar dispositivos Tuya localmente, convertido do código Python original.

## Funcionalidades

- ✅ Endpoint `/health` - Verifica status do servidor
- ✅ Endpoint `/tuya/command` - Envia comandos para dispositivos Tuya
- ✅ Gerenciamento de configuração (site_name)
- ✅ Serviço Android rodando em background
- ✅ Interface simples para iniciar/parar servidor

## Como Usar

### Clonar o Repositório

```bash
git clone https://github.com/MRITSoftware/mritserver.git
cd mritserver
```

### Configurar no Android Studio

1. Abra o Android Studio
2. Selecione "Open" e escolha a pasta `mritserver`
3. Aguarde o Gradle sincronizar (baixar dependências)
4. Conecte um dispositivo Android ou inicie um emulador
5. Execute o app (Shift+F10 ou botão Run)

### Usar o App

1. Na primeira execução, clique em "Configurar Site" e digite o nome (ex: `GELAFIT_SP01`)
2. Clique em "Iniciar Servidor" para iniciar o servidor HTTP na porta 8000
3. O servidor ficará rodando em background mesmo se fechar o app
4. Para parar, abra o app novamente e clique em "Parar Servidor"

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

