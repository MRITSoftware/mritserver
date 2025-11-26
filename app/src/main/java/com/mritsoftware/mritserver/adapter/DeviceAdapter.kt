package com.mritsoftware.mritserver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import androidx.recyclerview.widget.RecyclerView
import com.mritsoftware.mritserver.R
import com.mritsoftware.mritserver.model.TuyaDevice

class DeviceAdapter(
    private val devices: MutableList<TuyaDevice>,
    private val onDeviceClick: (TuyaDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.deviceCard)
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val deviceStatus: TextView = itemView.findViewById(R.id.deviceStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        
        // Mostrar apenas os últimos 5 caracteres do ID
        val deviceIdFull = device.id
        val deviceIdMasked = if (deviceIdFull.length > 5) {
            "***${deviceIdFull.takeLast(5)}"
        } else {
            deviceIdFull
        }
        holder.deviceName.text = deviceIdMasked
        
        holder.deviceStatus.text = if (device.isOnline) "Online" else "Offline"
        val statusColor = if (device.isOnline) {
            holder.itemView.context.getColor(com.mritsoftware.mritserver.R.color.status_online)
        } else {
            holder.itemView.context.getColor(com.mritsoftware.mritserver.R.color.status_offline)
        }
        holder.deviceStatus.setTextColor(statusColor)
        
        // Atualizar indicador de status
        val statusIndicator = holder.itemView.findViewById<android.view.View>(com.mritsoftware.mritserver.R.id.statusIndicator)
        if (statusIndicator != null) {
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(statusColor)
            }
            statusIndicator.background = drawable
        }
        
        holder.cardView.setOnClickListener {
            onDeviceClick(device)
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

