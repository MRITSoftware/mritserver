package com.mritsoftware.mritserver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.mritsoftware.mritserver.R

class DeviceAdapter(
    private val devices: List<Any>,
    private val onDeviceClick: (Any) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.deviceCard)
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val deviceStatus: TextView = itemView.findViewById(R.id.deviceStatus)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceName.text = "Dispositivo ${position + 1}"
        holder.deviceStatus.text = "Status: Desconhecido"
        
        holder.cardView.setOnClickListener {
            onDeviceClick(device)
        }
    }
    
    override fun getItemCount() = devices.size
}
