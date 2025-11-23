# Tuya Server Android

Servidor HTTP Android para controlar dispositivos Tuya localmente, convertido do código Python original.

## Funcionalidades

- ✅ Endpoint `/health` - Verifica status do servidor
- ✅ Endpoint `/tuya/command` - Envia comandos para dispositivos Tuya
- ✅ Gerenciamento de configuração (site_name)
- ✅ Serviço Android rodando em background
- ✅ Interface simples para iniciar/parar servidor

## 🚀 Como Usar (100% no GitHub)

### Opção 1: GitHub Codespaces (Recomendado)

1. **Abrir Codespace:**
   - Vá para https://github.com/MRITSoftware/mritserver
   - Clique no botão verde **"Code"**
   - Selecione a aba **"Codespaces"**
   - Clique em **"Create codespace on main"**
   - Aguarde o ambiente ser criado (pode levar alguns minutos na primeira vez)

2. **No Codespace:**
   - O ambiente já vem configurado com Android SDK e Gradle
   - Abra o terminal integrado (`` Ctrl+` `` ou View → Terminal)
   - Execute para sincronizar dependências:
     ```bash
     source ~/.bashrc
     ./gradlew build
     ```

3. **Editar código:**
   - Use o editor integrado do VS Code no navegador
   - Todas as extensões Kotlin/Java já estão instaladas
   - O código é salvo automaticamente no repositório

4. **Build e Test:**
   ```bash
   # Build do projeto
   ./gradlew build
   
   # Gerar APK
   ./gradlew assembleDebug
   
   # O APK estará em: app/build/outputs/apk/debug/app-debug.apk
   ```

### Opção 2: GitHub Actions (Build Automático)

- Toda vez que você fizer `git push`, o GitHub Actions vai:
  - Compilar o projeto automaticamente
  - Gerar o APK
  - Disponibilizar para download na aba **"Actions"** do repositório

### Opção 3: Clonar Localmente (se tiver Android Studio)

```bash
git clone https://github.com/MRITSoftware/mritserver.git
cd mritserver
# Abrir no Android Studio
```

### 📱 Usar o App no Dispositivo

1. **Baixe o APK gerado** (do Codespaces ou GitHub Actions)
2. **Instale no dispositivo Android** (permita instalação de fontes desconhecidas)
3. **Configure o site:** Na primeira execução, clique em "Configurar Site" e digite o nome (ex: `GELAFIT_SP01`)
4. **Inicie o servidor:** Clique em "Iniciar Servidor" - deve aparecer uma notificação permanente
5. **Descubra o IP:** Vá em Configurações → Sobre o telefone → Endereço IP
6. **Teste:** Acesse `http://[IP_DO_DISPOSITIVO]:8000/health` no navegador ou use curl/Postman

📖 **Guia completo de testes:** Veja [TESTE.md](TESTE.md) para instruções detalhadas!

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

