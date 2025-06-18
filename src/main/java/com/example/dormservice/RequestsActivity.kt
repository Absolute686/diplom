package com.example.dormservice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dormservice.databinding.ActivityRequestsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dormservice.databinding.ItemRequestBinding
import android.view.LayoutInflater
import android.view.ViewGroup
import com.bumptech.glide.Glide
import android.widget.Toast

class RequestsAdapter : ListAdapter<RequestData, RequestsAdapter.ViewHolder>(RequestDiffCallback()) {
    class ViewHolder(val binding: ItemRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = getItem(position)
        with(holder.binding) {
            descriptionTextView.text = request.description ?: "Описание отсутствует"
            categoryTextView.text = "Категория: ${request.category ?: "Не указана"}"
            statusTextView.text = "Статус: ${request.status ?: "Не указан"}"
            if (request.photoUrl != null) {
                Glide.with(root.context).load(request.photoUrl).into(photoImageView)
                photoImageView.visibility = View.VISIBLE
            } else {
                photoImageView.visibility = View.GONE
            }
        }
    }
}

class RequestsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRequestsBinding
    private val adapter = RequestsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityRequestsBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d("RequestsActivity", "Layout inflated successfully")
        } catch (e: Exception) {
            Log.e("RequestsActivity", "Failed to inflate layout: ${e.message}", e)
            Toast.makeText(this, "Ошибка макета: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.addRequestButton.setOnClickListener {
            Log.d("RequestsActivity", "Add request button clicked")
            startActivity(Intent(this, AddRequestActivity::class.java))
        }

        binding.logoutButton.setOnClickListener {
            Log.d("RequestsActivity", "Logout button clicked")
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        loadRequests()
    }

    private fun loadRequests() {
        Log.d("RequestsActivity", "Starting to load requests")
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e("RequestsActivity", "User not authenticated")
            Toast.makeText(this, "Требуется вход в систему", Toast.LENGTH_LONG).show()
            return
        }

        FirebaseDatabase.getInstance().reference.child("requests")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val requests = mutableListOf<RequestData>()
                    Log.d("RequestsActivity", "Received snapshot with ${snapshot.childrenCount} requests")
                    for (data in snapshot.children) {
                        val request = data.getValue(RequestData::class.java)?.copy(id = data.key!!)
                        request?.let {
                            if (it.userId == userId) { // Фильтр по текущему пользователю
                                Log.d("RequestsActivity", "Loaded request: $request")
                                val photoRef = FirebaseStorage.getInstance().reference.child("request_photos/${it.id}.jpg")
                                photoRef.downloadUrl.addOnSuccessListener { uri ->
                                    Log.d("RequestsActivity", "Photo URL retrieved for request ${it.id}: $uri")
                                    requests.add(it.copy(photoUrl = uri.toString()))
                                    adapter.submitList(requests.sortedByDescending { it.timestamp })
                                }.addOnFailureListener { e ->
                                    Log.w("RequestsActivity", "Failed to retrieve photo URL for request ${it.id}: ${e.message}")
                                    requests.add(it.copy(photoUrl = null))
                                    adapter.submitList(requests.sortedByDescending { it.timestamp })
                                }
                            }
                        } ?: Log.w("RequestsActivity", "Failed to parse request: ${data.value}")
                    }
                    Log.d("RequestsActivity", "Submitting ${requests.size} requests to adapter")
                    adapter.submitList(requests.sortedByDescending { it.timestamp })
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("RequestsActivity", "Load requests cancelled: ${error.message}")
                    Toast.makeText(this@RequestsActivity, "Ошибка: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
    }
}