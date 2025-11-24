package com.mritsoftware.mritserver.model

data class TuyaDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    var isOnline: Boolean = false,
    var isOn: Boolean = false,
    var brightness: Int = 100,
    var temperature: Int = 25
) {
    enum class DeviceType {
        LIGHT,
        SWITCH,
        SENSOR,
        THERMOSTAT,
        OTHER
    }
}

