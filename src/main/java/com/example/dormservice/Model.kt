package com.example.dormservice

import androidx.recyclerview.widget.DiffUtil

data class RequestData(
    val id: String = "",
    val userId: String = "",
    val description: String? = null,
    val category: String? = null,
    val status: String? = null,
    val timestamp: Long = 0,
    val photoUrl: String? = null
)

class RequestDiffCallback : DiffUtil.ItemCallback<RequestData>() {
    override fun areItemsTheSame(oldItem: RequestData, newItem: RequestData): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: RequestData, newItem: RequestData): Boolean = oldItem == newItem
}