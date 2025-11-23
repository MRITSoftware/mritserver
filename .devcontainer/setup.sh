#!/bin/bash

set -e

echo "🚀 Configurando ambiente Android no Codespaces..."

# Instalar Android SDK via command line tools
ANDROID_HOME=/opt/android-sdk
ANDROID_SDK_VERSION=commandlinetools-linux-9477386_latest.zip
ANDROID_SDK_URL="https://dl.google.com/android/repository/${ANDROID_SDK_VERSION}"

echo "📥 Baixando Android SDK Command Line Tools..."
mkdir -p ${ANDROID_HOME}
cd /tmp
wget -q ${ANDROID_SDK_URL} -O android-sdk.zip
unzip -q android-sdk.zip -d ${ANDROID_HOME}
rm android-sdk.zip

# Configurar variáveis de ambiente
export ANDROID_HOME=${ANDROID_HOME}
export PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/bin:${ANDROID_HOME}/platform-tools

# Aceitar licenças e instalar componentes
echo "📦 Instalando componentes Android SDK..."
yes | sdkmanager --sdk_root=${ANDROID_HOME} --licenses || true

# Instalar componentes necessários
sdkmanager --sdk_root=${ANDROID_HOME} \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0" \
  "cmdline-tools;latest"

# Instalar Gradle se não estiver instalado
if ! command -v gradle &> /dev/null; then
    echo "📦 Instalando Gradle..."
    wget -q https://services.gradle.org/distributions/gradle-8.4-bin.zip -O /tmp/gradle.zip
    unzip -q /tmp/gradle.zip -d /opt
    mv /opt/gradle-8.4 /opt/gradle
    export PATH=${PATH}:/opt/gradle/bin
    rm /tmp/gradle.zip
fi

# Adicionar ao bashrc
echo "" >> ~/.bashrc
echo "# Android SDK" >> ~/.bashrc
echo "export ANDROID_HOME=${ANDROID_HOME}" >> ~/.bashrc
echo "export PATH=\${PATH}:\${ANDROID_HOME}/cmdline-tools/bin:\${ANDROID_HOME}/platform-tools" >> ~/.bashrc
echo "export PATH=\${PATH}:/opt/gradle/bin" >> ~/.bashrc

echo "✅ Ambiente configurado!"
echo "📝 ANDROID_HOME=${ANDROID_HOME}"
echo "📝 Para usar: source ~/.bashrc"

