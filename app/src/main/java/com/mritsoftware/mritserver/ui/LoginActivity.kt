package com.mritsoftware.mritserver.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mritsoftware.mritserver.R

class LoginActivity : AppCompatActivity() {
    
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        setupViews()
        setupListeners()
    }
    
    private fun setupViews() {
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
    }
    
    private fun setupListeners() {
        loginButton.setOnClickListener {
            val username = usernameInput.text?.toString()?.trim() ?: ""
            val password = passwordInput.text?.toString()?.trim() ?: ""
            
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Por favor, preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Handle login
            Toast.makeText(this, "Login realizado", Toast.LENGTH_SHORT).show()
        }
    }
}
