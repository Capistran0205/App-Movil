// MainActivity2.kt
package com.example.sgbd_pocket_edition

import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity2 : AppCompatActivity() {

    // Usaremos spinnerDatabases según lo definido en el XML.
    private lateinit var spinnerTables: Spinner
    private lateinit var etSqlQuery: EditText
    private lateinit var btnExecute: Button
    private lateinit var btnClear: Button // Cambiar de btnConnect a btnClear
    private lateinit var btnDisconnect: Button
    private lateinit var recyclerViewResults: RecyclerView

    private val apiService = ApiService()
    private var database: String = ""
    private var sessionToken: String = ""
    private var username: String = ""
    private var tables: List<String> = emptyList()

    private lateinit var resultsAdapter: QueryResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main2)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Obtener datos del intent
        getIntentData()

        // Inicializar vistas (se usa spinnerDatabases como está definido en el XML)
        initViews()
        setupSpinner()
        setupRecyclerView()
        setupButtons()
    }

    private fun getIntentData() {
        database = intent.getStringExtra("database") ?: ""
        sessionToken = intent.getStringExtra("session_token") ?: ""
        username = intent.getStringExtra("username") ?: ""
        tables = intent.getStringArrayListExtra("tables") ?: emptyList()
    }

    private fun initViews() {
        // Usamos R.id.spinnerDatabases puesto que el XML define "spinnerDatabases"
        spinnerTables = findViewById(R.id.spinnerTables)
        etSqlQuery = findViewById(R.id.etSqlQuery)
        btnExecute = findViewById(R.id.btnExecute)
        btnClear = findViewById(R.id.btnClear) // Botón "BORRAR"
        btnDisconnect = findViewById(R.id.btnDisconnect)
        recyclerViewResults = findViewById(R.id.recyclerViewResults)
    }

    private fun setupSpinner() {
        // Configurar spinner con las tablas disponibles
        val tablesArray = tables.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tablesArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTables.adapter = adapter

        // Listener para cuando se selecciona una tabla
        spinnerTables.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long
            ) {
                val selectedTable = tablesArray[position]
                // Genera una consulta SELECT automática al seleccionar una tabla
                etSqlQuery.setText("SELECT * FROM $selectedTable LIMIT 10;")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecyclerView() {
        resultsAdapter = QueryResultsAdapter()
        recyclerViewResults.layoutManager = LinearLayoutManager(this)
        recyclerViewResults.adapter = resultsAdapter
    }

    private fun setupButtons() {
        btnExecute.setOnClickListener {
            executeQuery()
        }

        btnClear.setOnClickListener { // Botón "BORRAR"
            clearQuery()
        }

        btnDisconnect.setOnClickListener {
            finish()
        }
    }

    private fun executeQuery() {
        val query = etSqlQuery.text.toString().trim()

        if (query.isEmpty()) {
            Toast.makeText(this, "Por favor ingrese una consulta SQL", Toast.LENGTH_SHORT).show()
            return
        }

        // Deshabilitar botón durante la ejecución
        btnExecute.isEnabled = false
        btnExecute.text = "Ejecutando..."

        lifecycleScope.launch {
            try {
                val response = apiService.executeQuery(database, sessionToken, query)

                if (response.success) {
                    // Mostrar resultados
                    displayResults(response)

                    val message = when (response.queryType) {
                        "SELECT" -> "Consulta ejecutada. ${response.rowCount} filas encontradas"
                        "INSERT", "UPDATE", "DELETE" -> "Consulta ejecutada. ${response.affectedRows} filas afectadas"
                        else -> "Consulta ejecutada correctamente"
                    }
                    Toast.makeText(
                        this@MainActivity2,
                        "$message (${response.executionTime})",
                        Toast.LENGTH_LONG
                    ).show()

                } else {
                    // Error en la consulta
                    Toast.makeText(
                        this@MainActivity2,
                        response.message ?: "Error desconocido",
                        Toast.LENGTH_LONG
                    ).show()
                    resultsAdapter.clearResults()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity2,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                resultsAdapter.clearResults()
            } finally {
                // Rehabilitar botón
                btnExecute.isEnabled = true
                btnExecute.text = "Ejecutar Consulta"
            }
        }
    }

    private fun displayResults(response: ApiService.QueryResponse) {
        when (response.queryType) {
            "SELECT", "SHOW", "DESCRIBE", "EXPLAIN" -> {
                response.data?.let { data ->
                    resultsAdapter.updateResults(data)
                } ?: run {
                    resultsAdapter.clearResults()
                }
            }
            "INSERT", "UPDATE", "DELETE" -> {
                val resultMap = mapOf(
                    "Tipo" to (response.queryType ?: ""),
                    "Filas afectadas" to (response.affectedRows?.toString() ?: "0"),
                    "Tiempo de ejecución" to (response.executionTime ?: ""),
                    "Último ID" to (response.lastInsertId ?: "N/A")
                )
                resultsAdapter.updateResults(listOf(resultMap))
            }
            else -> {
                val resultMap = mapOf(
                    "Tipo de consulta" to (response.queryType ?: ""),
                    "Estado" to "Ejecutado correctamente",
                    "Tiempo" to (response.executionTime ?: "")
                )
                resultsAdapter.updateResults(listOf(resultMap))
            }
        }
    }

    private fun clearQuery() {
        etSqlQuery.setText("")
        resultsAdapter.clearResults()
    }
}