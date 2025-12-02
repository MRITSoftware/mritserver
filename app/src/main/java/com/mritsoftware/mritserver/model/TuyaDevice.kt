package com.mritsoftware.mritserver.model

import java.io.Serializable

data class TuyaDevice(
    val id: String, // tuya_device_id (gwId)
    val name: String,
    val type: DeviceType,
    var localKey: String? = null, // local_key necessária para comandos
    var lanIp: String? = null, // IP local (opcional, pode ser "auto")
    var isOnline: Boolean = false,
    var isOn: Boolean = false,
    var brightness: Int = 100,
    var temperature: Int = 25,
    // Versão do protocolo Tuya (3.1, 3.2, 3.3, 3.4, etc), quando conhecida
    var protocolVersion: String? = null
) : Serializable {
    enum class DeviceType {
        LIGHT,
        SWITCH,
        SENSOR,
        THERMOSTAT,
        OTHER
    }
}

