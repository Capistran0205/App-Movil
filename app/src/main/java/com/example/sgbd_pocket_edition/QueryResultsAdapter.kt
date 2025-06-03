// QueryResultsAdapter.kt
package com.example.sgbd_pocket_edition

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QueryResultsAdapter : RecyclerView.Adapter<QueryResultsAdapter.ResultViewHolder>() {

    private var results: List<Map<String, Any>> = emptyList()
    private var columns: List<String> = emptyList()

    fun updateResults(newResults: List<Map<String, Any>>) {
        results = newResults
        columns = if (newResults.isNotEmpty()) {
            newResults.first().keys.toList()
        } else {
            emptyList()
        }
        notifyDataSetChanged()
    }

    fun clearResults() {
        results = emptyList()
        columns = emptyList()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = results.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val result = results[position]
        holder.bind(result, columns)
    }

    class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val primaryText: TextView = itemView.findViewById(android.R.id.text1)
        private val secondaryText: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(result: Map<String, Any>, columns: List<String>) {
            if (columns.isNotEmpty()) {
                // Mostrar la primera columna como texto principal
                val primaryValue = result[columns.first()]?.toString() ?: ""
                primaryText.text = "${columns.first()}: $primaryValue"

                // Mostrar el resto de columnas como texto secundario
                if (columns.size > 1) {
                    val secondaryValues = columns.drop(1).joinToString(" | ") { column ->
                        "$column: ${result[column]?.toString() ?: ""}"
                    }
                    secondaryText.text = secondaryValues
                    secondaryText.visibility = View.VISIBLE
                } else {
                    secondaryText.visibility = View.GONE
                }
            } else {
                primaryText.text = "Sin datos"
                secondaryText.visibility = View.GONE
            }
        }
    }
}