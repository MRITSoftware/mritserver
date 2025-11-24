#!/usr/bin/env python3

import os
import json
import traceback
from typing import Optional, Dict, Any
from android import mActivity
from android.storage import app_storage_path

from flask import Flask, request, jsonify
import tinytuya

# =========================
# CONFIG & AUTO-SETUP
# =========================

BASE_DIR = app_storage_path()
CONFIG_PATH = os.path.join(BASE_DIR, "config.json")

def create_config_if_needed():
    """Cria o config.json pedindo o nome do site/tablet na primeira execução."""
    if not os.path.exists(CONFIG_PATH):
        # No Android, usar um nome padrão ou buscar do SharedPreferences
        site = "ANDROID_DEVICE"
        
        cfg = {
            "site_name": site
        }
        
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=4, ensure_ascii=False)
        
        print(f"[OK] config.json criado com site_name = {site}")

# cria se não existir
create_config_if_needed()

# carrega o config
with open(CONFIG_PATH, "r", encoding="utf-8") as f:
    cfg = json.load(f)

SITE_NAME: str = cfg.get("site_name", "SITE_DESCONHECIDO")

print(f"[INFO] Servidor local iniciado para SITE = {SITE_NAME}")

def log(msg: str) -> None:
    print(msg, flush=True)

# =========================
# DISCOVERY / CACHE DE IP
# =========================

DEVICE_CACHE: Dict[str, str] = {}

def scan_and_print_devices() -> None:
    """Faz um scan na rede e imprime todos os dispositivos Tuya encontrados."""
    log("[SCAN] Iniciando scan de dispositivos Tuya na rede...")
    
    try:
        devices = tinytuya.deviceScan()
        
        if not isinstance(devices, dict):
            log(f"[SCAN] Resultado inesperado de deviceScan(): {type(devices)}")
            return
        
        if not devices:
            log("[SCAN] Nenhum dispositivo Tuya encontrado.")
            return
        
        log(f"[SCAN] {len(devices)} dispositivo(s) encontrado(s):")
        for ip, dev in devices.items():
            gwid = dev.get("gwId")
            ver = dev.get("version") or dev.get("ver")
            log(f"[SCAN] gwId={gwid}  ip={ip}  ver={ver}")
    
    except Exception as e:
        log(f"[SCAN] Erro ao escanear dispositivos Tuya: {e}")
        traceback.print_exc()

def discover_tuya_ip(tuya_device_id: str) -> Optional[str]:
    """
    Tenta descobrir o IP LAN de um dispositivo Tuya pelo gwId (device_id),
    usando tinytuya.deviceScan() e guarda em cache.
    """
    # se já descobrimos antes, usa o cache
    if tuya_device_id in DEVICE_CACHE:
        ip_cached = DEVICE_CACHE[tuya_device_id]
        log(f"[DISCOVER] Usando IP em cache para {tuya_device_id}: {ip_cached}")
        return ip_cached
    
    log(f"[DISCOVER] Varrendo a rede para encontrar o device_id = {tuya_device_id} ...")
    
    try:
        devices = tinytuya.deviceScan()
        
        if not isinstance(devices, dict):
            log(f"[DISCOVER] Resultado inesperado de deviceScan(): {type(devices)}")
            return None
        
        log(f"[DISCOVER] deviceScan encontrou {len(devices)} dispositivo(s).")
        
        for ip, dev in devices.items():
            gwid = dev.get("gwId")
            dev_ip = dev.get("ip", ip)
            log(f"[DISCOVER] Achado gwId={gwid} ip={dev_ip}")
            if gwid == tuya_device_id:
                log(f"[DISCOVER] Encontrado! device_id={gwid} ip={dev_ip}")
                DEVICE_CACHE[tuya_device_id] = dev_ip
                return dev_ip
        
        log(f"[DISCOVER] Nenhum dispositivo encontrado com device_id = {tuya_device_id}")
        return None
    
    except Exception as e:
        log(f"[DISCOVER] Erro ao escanear dispositivos Tuya: {e}")
        traceback.print_exc()
        return None

# =========================
# TUYA
# =========================

def send_tuya_command(
    action: str,
    tuya_device_id: str,
    local_key: str,
    lan_ip: Optional[str]
) -> None:
    
    if not tuya_device_id:
        raise RuntimeError("Campo tuya_device_id é obrigatório")
    if not local_key:
        raise RuntimeError("Campo local_key é obrigatório")
    
    # Se não veio IP ou veio "auto", tenta descobrir
    if not lan_ip or str(lan_ip).lower() == "auto":
        log(f"[INFO] Nenhum lan_ip informado (ou 'auto'). Tentando descobrir IP do device {tuya_device_id}...")
        lan_ip = discover_tuya_ip(tuya_device_id)
        if not lan_ip:
            raise RuntimeError("Não foi possível descobrir o IP LAN do dispositivo Tuya.")
    
    # Garante que venha só IP, nada de 'http://'
    lan_ip = str(lan_ip).strip()
    if lan_ip.startswith("http://") or lan_ip.startswith("https://"):
        raise RuntimeError("lan_ip deve ser apenas o IP (ex: 192.168.0.50), sem http:// e sem porta.")
    
    log(f"[INFO] [{SITE_NAME}] Enviando '{action}' → {tuya_device_id} @ {lan_ip}")
    
    d = tinytuya.OutletDevice(tuya_device_id, lan_ip, local_key)
    d.set_version(3.4)
    
    if action == "on":
        resp = d.turn_on()
    elif action == "off":
        resp = d.turn_off()
    else:
        raise ValueError(f"Ação inválida: {action}")
    
    log(f"[DEBUG] Resposta do dispositivo: {resp}")

# =========================
# API HTTP
# =========================

app = Flask(__name__)

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "site": SITE_NAME}), 200

@app.route("/tuya/command", methods=["POST"])
def api_tuya_command():
    try:
        data: Dict[str, Any] = request.get_json(force=True, silent=False) or {}
        
        action = data.get("action")
        tuya_device_id = data.get("tuya_device_id")
        local_key = data.get("local_key")
        lan_ip = data.get("lan_ip")  # pode vir None, vazio ou "auto"
        
        if action not in ("on", "off"):
            return jsonify({"ok": False, "error": "action deve ser 'on' ou 'off'"}), 400
        
        send_tuya_command(
            action=action,
            tuya_device_id=tuya_device_id,
            local_key=local_key,
            lan_ip=lan_ip
        )
        
        return jsonify({"ok": True}), 200
    
    except Exception as e:
        err = str(e)
        log(f"[ERRO] API /tuya/command: {err}")
        traceback.print_exc()
        return jsonify({"ok": False, "error": err}), 500

def start_server(host="0.0.0.0", port=8000):
    """Inicia o servidor Flask"""
    log(f"[START] Servidor Tuya local rodando em http://{host}:{port} (SITE={SITE_NAME})")
    # Faz o scan inicial
    scan_and_print_devices()
    app.run(host=host, port=port, debug=False, use_reloader=False)

