# 🚀 Guia Rápido - GitHub Codespaces

## Primeiro Acesso

1. **Abrir Codespace:**
   - Vá para: https://github.com/MRITSoftware/mritserver
   - Clique em **"Code"** → **"Codespaces"** → **"Create codespace on main"**
   - Aguarde ~2-3 minutos (primeira vez)

2. **Configurar Ambiente:**
   ```bash
   # O setup.sh roda automaticamente, mas se precisar:
   source ~/.bashrc
   ```

3. **Verificar Instalação:**
   ```bash
   java -version
   gradle -version
   echo $ANDROID_HOME
   ```

## Comandos Úteis

### Build do Projeto
```bash
./gradlew build
```

### Gerar APK
```bash
./gradlew assembleDebug
# APK em: app/build/outputs/apk/debug/app-debug.apk
```

### Limpar Build
```bash
./gradlew clean
```

### Ver Dependências
```bash
./gradlew dependencies
```

## Estrutura do Projeto

```
mritserver/
├── app/
│   ├── src/main/java/com/tuyaserver/
│   │   ├── ConfigManager.kt      # Gerencia config.json
│   │   ├── TuyaClient.kt         # Cliente Tuya
│   │   ├── TuyaServerService.kt # Servidor HTTP
│   │   └── MainActivity.kt      # Interface Android
│   └── build.gradle.kts          # Dependências
├── .devcontainer/                # Config Codespaces
└── .github/workflows/            # CI/CD
```

## Editar Código

- Use o editor integrado do VS Code
- Extensões Kotlin/Java já instaladas
- Auto-complete e syntax highlighting funcionam

## Download do APK

Após gerar o APK:
1. Clique com botão direito em `app/build/outputs/apk/debug/app-debug.apk`
2. Selecione "Download"
3. Instale no dispositivo Android

## Troubleshooting

### Gradle não encontrado
```bash
source ~/.bashrc
export PATH=$PATH:/opt/gradle/bin
```

### Android SDK não configurado
```bash
export ANDROID_HOME=/opt/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/bin
```

### Erro de permissão
```bash
chmod +x gradlew
```

