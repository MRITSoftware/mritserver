package com.tuyaserver

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

object LogCollector {
    private val logs = ConcurrentLinkedQueue<String>()
    private const val MAX_LOGS = 1000 // Mantém últimos 1000 logs
    
    fun addLog(tag: String, message: String, level: String = "D") {
        val timestamp = System.currentTimeMillis()
        val logEntry = "[$timestamp] [$level] $tag: $message"
        logs.offer(logEntry)
        
        // Remove logs antigos se exceder o limite
        while (logs.size > MAX_LOGS) {
            logs.poll()
        }
        
        // Também envia para o Log padrão do Android
        when (level) {
            "E" -> Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "I" -> Log.i(tag, message)
            else -> Log.d(tag, message)
        }
    }
    
    fun getAllLogs(): String {
        return logs.joinToString("\n")
    }
    
    fun getFilteredLogs(filter: String): String {
        return logs.filter { it.contains(filter, ignoreCase = true) }
            .joinToString("\n")
    }
    
    fun clearLogs() {
        logs.clear()
    }
    
    fun getLogCount(): Int {
        return logs.size
    }
}

