// MainActivity.kt
package com.example.sgbd_pocket_edition

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerDatabases: Spinner
    private lateinit var editTextUser: EditText
    private lateinit var editTextPassword: EditText  // Corregido: eliminado "as"
    private lateinit var btnConnect: Button
    private lateinit var btnClose: Button

    private val apiService = ApiService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializar vistas
        initViews()
        setupSpinner()
        setupButtons()
    }

    private fun initViews() {
        spinnerDatabases = findViewById(R.id.spinnerDatabases)
        editTextUser = findViewById(R.id.editTextUser)
        editTextPassword = findViewById(R.id.editTextPassword)
        btnConnect = findViewById(R.id.btnConnect)
        btnClose = findViewById(R.id.btnClose)
    }

    private fun setupSpinner() {
        // Lista de bases de datos disponibles (puedes hacer esto dinámico)
        val databases = arrayOf("estudiantes", "tienda", "puntoventa", "bd_sistema_ventas")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, databases)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDatabases.adapter = adapter
    }

    private fun setupButtons() {
        btnConnect.setOnClickListener {
            connectToDatabase()
        }

        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun connectToDatabase() {
        val database = spinnerDatabases.selectedItem.toString()
        val username = editTextUser.text.toString().trim()
        val password = editTextPassword.text.toString().trim()

        // Validaciones básicas
        if (username.isEmpty()) {
            Toast.makeText(this, "Por favor ingrese el usuario", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Por favor ingrese la contraseña", Toast.LENGTH_SHORT).show()
            return
        }

        // Deshabilitar botón durante la conexión
        btnConnect.isEnabled = false
        btnConnect.text = "Conectando..."

        // Realizar conexión en background
        lifecycleScope.launch {
            try {
                val response = apiService.connectToDatabase(database, username, password)

                if (response.success) {
                    // Conexión exitosa
                    Toast.makeText(this@MainActivity, "Conexión exitosa", Toast.LENGTH_SHORT).show()

                    // Pasar los datos a la siguiente actividad
                    val intent = Intent(this@MainActivity, MainActivity2::class.java).apply {
                        putExtra("database", response.database)
                        putExtra("session_token", response.sessionToken)
                        putExtra("username", username)
                        putStringArrayListExtra("tables", ArrayList(response.tables ?: emptyList()))
                    }
                    startActivity(intent)

                } else {
                    // Error en la conexión
                    Toast.makeText(this@MainActivity, response.message, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // Rehabilitar botón
                btnConnect.isEnabled = true
                btnConnect.text = "Connect"
            }
        }
    }
}