package com.mritsoftware.mritserver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.mritsoftware.mritserver.R
import com.mritsoftware.mritserver.model.TuyaDevice

class DeviceAdapter(
    private val devices: MutableList<TuyaDevice>,
    private val onDeviceToggle: (TuyaDevice, Boolean) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.deviceCard)
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val deviceType: TextView = itemView.findViewById(R.id.deviceType)
        val deviceStatus: TextView = itemView.findViewById(R.id.deviceStatus)
        val deviceSwitch: Switch = itemView.findViewById(R.id.deviceSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        
        holder.deviceName.text = device.name
        holder.deviceType.text = device.type.name
        holder.deviceStatus.text = if (device.isOnline) "Online" else "Offline"
        holder.deviceSwitch.isChecked = device.isOn
        holder.deviceSwitch.isEnabled = device.isOnline
        
        holder.deviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            device.isOn = isChecked
            onDeviceToggle(device, isChecked)
        }
        
        holder.cardView.alpha = if (device.isOnline) 1.0f else 0.6f
    }

    override fun getItemCount() = devices.size
    
    fun updateDevices(newDevices: List<TuyaDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }
}

