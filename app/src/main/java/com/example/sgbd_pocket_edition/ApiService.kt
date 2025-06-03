// ApiService.kt
package com.example.sgbd_pocket_edition

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiService {

    companion object {
        // Cambia esta URL por la de tu servidor local o remoto
        // private const val BASE_URL ="http://192.168.1.64/sgbd_api"
         private const val BASE_URL = "http://10.0.2.2/sgbd_api" // Para emulador Android
        // Para dispositivo físico usa: "http://tu_ip_local/sgbd_api"
        // Ejemplo: "http://192.168.1.100/sgbd_api"

        // CORREGIDO: Era "API_Conexio.php" ahora es "API_Conexion.php"
        private const val CONNECT_ENDPOINT = "$BASE_URL/API_Conexion.php"
        private const val EXECUTE_QUERY_ENDPOINT = "$BASE_URL/AP_EjecucionSQL.php"
    }

    // Clase para la respuesta de conexión
    data class ConnectionResponse(
        val success: Boolean,
        val message: String,
        val sessionToken: String? = null,
        val tables: List<String>? = null,
        val database: String? = null
    )

    // Clase para la respuesta de consulta
    data class QueryResponse(
        val success: Boolean,
        val message: String? = null,
        val queryType: String? = null,
        val data: List<Map<String, Any>>? = null,
        val rowCount: Int? = null,
        val affectedRows: Int? = null,
        val executionTime: String? = null,
        val lastInsertId: String? = null,
        val errorType: String? = null
    )

    // Función para conectar a la base de datos
    suspend fun connectToDatabase(
        database: String,
        username: String,
        password: String
    ): ConnectionResponse = withContext(Dispatchers.IO) {
        try {
            Log.d("ApiService", "Connecting to: $CONNECT_ENDPOINT")
            Log.d("ApiService", "Database: $database, Username: $username")

            val url = URL(CONNECT_ENDPOINT)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            // Crear JSON con los datos de conexión
            val jsonData = JSONObject().apply {
                put("database", database)
                put("username", username)
                put("password", password)
            }

            Log.d("ApiService", "Sending JSON: $jsonData")

            // Enviar datos
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonData.toString())
            writer.flush()
            writer.close()

            // Leer respuesta
            val responseCode = connection.responseCode
            Log.d("ApiService", "Response Code: $responseCode")

            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = reader.readText()
            reader.close()

            Log.d("ApiService", "Connect Response: $response")

            // Verificar si la respuesta es JSON válido
            if (response.trim().startsWith("{")) {
                // Parsear respuesta JSON
                val jsonResponse = JSONObject(response)

                ConnectionResponse(
                    success = jsonResponse.getBoolean("success"),
                    message = jsonResponse.getString("message"),
                    sessionToken = jsonResponse.optString("session_token", null),
                    tables = if (jsonResponse.has("tables")) {
                        val tablesArray = jsonResponse.getJSONArray("tables")
                        (0 until tablesArray.length()).map { tablesArray.getString(it) }
                    } else null,
                    database = jsonResponse.optString("database", null)
                )
            } else {
                Log.e("ApiService", "Invalid JSON response: $response")
                ConnectionResponse(
                    success = false,
                    message = "Respuesta inválida del servidor: $response"
                )
            }

        } catch (e: Exception) {
            Log.e("ApiService", "Connection error: ${e.message}")
            e.printStackTrace()
            ConnectionResponse(
                success = false,
                message = "Error de conexión: ${e.message}"
            )
        }
    }

    // Función para ejecutar consultas SQL
    suspend fun executeQuery(
        database: String,
        sessionToken: String,
        query: String
    ): QueryResponse = withContext(Dispatchers.IO) {
        try {
            Log.d("ApiService", "Executing query: $query")

            val url = URL(EXECUTE_QUERY_ENDPOINT)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            // Crear JSON con los datos de la consulta
            val jsonData = JSONObject().apply {
                put("database", database)
                put("session_token", sessionToken)
                put("query", query)
            }

            // Enviar datos
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonData.toString())
            writer.flush()
            writer.close()

            // Leer respuesta
            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = reader.readText()
            reader.close()

            Log.d("ApiService", "Query Response: $response")

            // Verificar si la respuesta es JSON válido
            if (response.trim().startsWith("{")) {
                // Parsear respuesta JSON
                val jsonResponse = JSONObject(response)

                // Parsear datos si existen
                val data = if (jsonResponse.has("data")) {
                    val dataArray = jsonResponse.getJSONArray("data")
                    (0 until dataArray.length()).map { index ->
                        val item = dataArray.getJSONObject(index)
                        val map = mutableMapOf<String, Any>()
                        item.keys().forEach { key ->
                            map[key] = item.get(key)
                        }
                        map
                    }.toList()
                } else null

                QueryResponse(
                    success = jsonResponse.getBoolean("success"),
                    message = jsonResponse.optString("message", null),
                    queryType = jsonResponse.optString("query_type", null),
                    data = data,
                    rowCount = jsonResponse.optInt("row_count", -1).takeIf { it >= 0 },
                    affectedRows = jsonResponse.optInt("affected_rows", -1).takeIf { it >= 0 },
                    executionTime = jsonResponse.optString("execution_time", null),
                    lastInsertId = jsonResponse.optString("last_insert_id", null),
                    errorType = jsonResponse.optString("error_type", null)
                )
            } else {
                Log.e("ApiService", "Invalid JSON response: $response")
                QueryResponse(
                    success = false,
                    message = "Respuesta inválida del servidor: $response"
                )
            }

        } catch (e: Exception) {
            Log.e("ApiService", "Query error: ${e.message}")
            e.printStackTrace()
            QueryResponse(
                success = false,
                message = "Error en la consulta: ${e.message}"
            )
        }
    }
}