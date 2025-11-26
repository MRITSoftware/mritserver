package com.mritsoftware.mritserver

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mritsoftware.mritserver.R
import com.mritsoftware.mritserver.adapter.DeviceAdapter
import com.mritsoftware.mritserver.ui.DeviceDiscoveryActivity
import com.mritsoftware.mritserver.ui.SettingsActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: DeviceAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupToolbar()
        setupViews()
        setupRecyclerView()
        setupListeners()
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }
    
    private fun setupViews() {
        recyclerView = findViewById(R.id.devicesRecyclerView)
        fab = findViewById(R.id.fab)
    }
    
    private fun setupRecyclerView() {
        adapter = DeviceAdapter(emptyList()) { device ->
            // Handle device click
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun setupListeners() {
        fab.setOnClickListener {
            val intent = Intent(this, DeviceDiscoveryActivity::class.java)
            startActivity(intent)
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.menu_discover -> {
                val intent = Intent(this, DeviceDiscoveryActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
