package com.example.dormservice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dormservice.databinding.ActivityAdminBinding
import com.example.dormservice.databinding.ItemAdminRequestBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.bumptech.glide.Glide
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class AdminRequestAdapter(private val onStatusUpdate: (RequestData, String) -> Unit) :
    ListAdapter<RequestData, AdminRequestAdapter.ViewHolder>(RequestDiffCallback()) {

    class ViewHolder(val binding: ItemAdminRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = getItem(position)
        Log.d("AdminRequestAdapter", "Binding request: $request")
        with(holder.binding) {
            descriptionTextView.text = request.description ?: "Описание отсутствует"
            categoryTextView.text = "Категория: ${request.category ?: "Не указана"}"
            statusTextView.text = "Статус: ${request.status ?: "Не указан"}"
            if (request.photoUrl != null) {
                Log.d("AdminRequestAdapter", "Loading photo for request ${request.id}: ${request.photoUrl}")
                Glide.with(root.context).load(request.photoUrl).into(photoImageView)
                photoImageView.visibility = android.view.View.VISIBLE
            } else {
                Log.d("AdminRequestAdapter", "No photo for request ${request.id}")
                photoImageView.visibility = android.view.View.GONE
            }

            val statuses = arrayOf("Новая", "В работе", "Завершена")
            val adapter = ArrayAdapter(root.context, R.layout.dropdown_item, statuses)
            statusAutoComplete.setAdapter(adapter)

            updateStatusButton.setOnClickListener {
                val newStatus = statusAutoComplete.text.toString()
                if (newStatus in statuses) {
                    Log.d("AdminRequestAdapter", "Updating status for request ${request.id} to $newStatus")
                    statusDropdown.error = null
                    onStatusUpdate(request, newStatus)
                } else {
                    Log.w("AdminRequestAdapter", "Invalid status selected: $newStatus")
                    statusDropdown.error = "Выберите статус"
                }
            }
        }
    }
}

class AdminActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminBinding
    private val adapter = AdminRequestAdapter { request, newStatus ->
        updateRequestStatus(request, newStatus)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityAdminBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d("AdminActivity", "Layout inflated successfully")
        } catch (e: Exception) {
            Log.e("AdminActivity", "Failed to inflate layout: ${e.message}", e)
            Toast.makeText(this, "Ошибка макета: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.adminRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.adminRecyclerView.adapter = adapter

        binding.logoutButton.setOnClickListener {
            Log.d("AdminActivity", "Logout button clicked")
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        loadRequests()
    }

    private fun loadRequests() {
        Log.d("AdminActivity", "Starting to load requests")
        FirebaseDatabase.getInstance().reference.child("requests")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val requests = mutableListOf<RequestData>()
                    Log.d("AdminActivity", "Received snapshot with ${snapshot.childrenCount} requests")
                    for (data in snapshot.children) {
                        val request = data.getValue(RequestData::class.java)?.copy(id = data.key!!)
                        if (request != null) {
                            Log.d("AdminActivity", "Loaded request: $request")
                            val photoRef =
                                FirebaseStorage.getInstance().reference.child("request_photos/${request.id}.jpg")
                            photoRef.downloadUrl.addOnSuccessListener { uri ->
                                Log.d("AdminActivity", "Photo URL retrieved for request ${request.id}: $uri")
                                requests.add(request.copy(photoUrl = uri.toString()))
                                adapter.submitList(requests.sortedByDescending { it.timestamp })
                            }.addOnFailureListener { e ->
                                Log.w("AdminActivity", "Failed to retrieve photo URL for request ${request.id}: ${e.message}")
                                requests.add(request.copy(photoUrl = null))
                                adapter.submitList(requests.sortedByDescending { it.timestamp })
                            }
                        } else {
                            Log.w("AdminActivity", "Failed to parse request: ${data.value}")
                        }
                    }
                    Log.d("AdminActivity", "Submitting ${requests.size} requests to adapter")
                    adapter.submitList(requests.sortedByDescending { it.timestamp })
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("AdminActivity", "Load requests cancelled: ${error.message}")
                    Toast.makeText(
                        this@AdminActivity,
                        "Ошибка загрузки заявок: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    private fun updateRequestStatus(request: RequestData, newStatus: String) {
        Log.d("AdminActivity", "Updating status for request ${request.id} to $newStatus")
        FirebaseDatabase.getInstance().reference.child("requests").child(request.id)
            .child("status").setValue(newStatus)
            .addOnSuccessListener {
                Log.i("AdminActivity", "Status updated successfully for request ${request.id}")
                Toast.makeText(this, "Статус обновлён", Toast.LENGTH_SHORT).show()
                sendNotification(
                    request.userId,
                    "Статус заявки изменён",
                    "Заявка: ${request.description ?: "Описание отсутствует"}, Новый статус: $newStatus"
                )
            }
            .addOnFailureListener { e ->
                Log.e("AdminActivity", "Failed to update status for request ${request.id}: ${e.message}")
                Toast.makeText(this, "Ошибка обновления статуса: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
    }

    private fun sendNotification(userId: String, title: String, body: String) {
        Log.d("AdminActivity", "Sending notification to user $userId: $title - $body")
        try {
            val client = OkHttpClient()
            val json = JSONObject().apply {
                put("where", JSONObject().apply {
                    put("userId", userId)
                })
                put("data", JSONObject().apply {
                    put("title", title)
                    put("alert", body)
                })
            }
            // Исправляем порядок аргументов
            val requestBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                json.toString()
            )
            val request = Request.Builder()
                .url("https://parseapi.back4app.com/parse/push")
                .post(requestBody)
                .addHeader("X-Parse-Application-Id", "ASA9CqeZej17oJxP8f3PfNOOsLoESFKOTH51mGYW")
                .addHeader("X-Parse-REST-API-Key", "r1apd1tig88KASA5BPn8iDdHGP0niZr5Z5Wb32Za")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: "No response body"
                    Log.d("AdminActivity", "Notification response: $responseBody")
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Log.i("AdminActivity", "Notification sent successfully to user $userId")
                            Toast.makeText(
                                this@AdminActivity,
                                "Уведомление отправлено",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Log.e("AdminActivity", "Notification failed with response: $responseBody")
                            Toast.makeText(
                                this@AdminActivity,
                                "Ошибка отправки уведомления: $responseBody",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("AdminActivity", "Notification failed: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@AdminActivity,
                            "Ошибка: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("AdminActivity", "Error in sendNotification: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(
                    this@AdminActivity,
                    "Ошибка: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}