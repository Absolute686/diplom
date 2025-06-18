package com.example.dormservice

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.dormservice.databinding.ActivityAddRequestBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

class AddRequestActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddRequestBinding
    private var photoUri: Uri? = null
    private val categories = arrayOf("Ремонт", "Уборка", "Электрика", "Сантехника", "Другое")

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            photoUri = uri
            binding.selectPhotoButton.text = "Фото выбрано"
            binding.photoPreview?.setImageURI(uri)
            binding.photoPreview?.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.categoryAutoComplete?.setAdapter(adapter) ?: run {
            Log.e("AddRequestActivity", "Failed to set adapter on categoryAutoComplete")
        }

        binding.selectPhotoButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.submitRequestButton.setOnClickListener {
            val descriptionEditText = binding.descriptionEditText
            val description = (descriptionEditText as? EditText)?.text?.toString()?.trim() ?: run {
                Log.e("AddRequestActivity", "descriptionEditText text is null or not EditText")
                ""
            }
            val category = binding.categoryAutoComplete?.text?.toString() ?: ""

            if (description.isEmpty()) {
                binding.descriptionInputLayout?.error = "Введите описание"
                return@setOnClickListener
            }
            if (description.length < 10) {
                binding.descriptionInputLayout?.error = "Описание должно быть не короче 10 символов"
                return@setOnClickListener
            }
            if (category !in categories) {
                binding.categoryDropdown?.error = "Выберите категорию"
                return@setOnClickListener
            }

            binding.descriptionInputLayout?.error = null
            binding.categoryDropdown?.error = null
            uploadRequest(description, category)
        }
    }

    private fun uploadRequest(description: String, category: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Требуется вход в систему", Toast.LENGTH_SHORT).show()
            return
        }

        val requestId = UUID.randomUUID().toString()
        val database = FirebaseDatabase.getInstance().reference
        val request = mapOf(
            "userId" to userId,
            "description" to description,
            "category" to category,
            "status" to "Новая",
            "timestamp" to System.currentTimeMillis()
        )

        database.child("requests").child(requestId).setValue(request)
            .addOnSuccessListener {
                if (photoUri != null) {
                    val storageRef = FirebaseStorage.getInstance().reference.child("request_photos/$requestId.jpg")
                    storageRef.putFile(photoUri!!)
                        .addOnSuccessListener {
                            Log.d("AddRequestActivity", "Request uploaded with photo: $requestId")
                            Toast.makeText(this, "Заявка отправлена", Toast.LENGTH_SHORT).show()
                            sendNotificationToAdmin("Новая заявка", "Добавлена заявка: $category - $description")
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("AddRequestActivity", "Failed to upload photo: ${e.message}")
                            Toast.makeText(this, "Ошибка загрузки фото: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Log.d("AddRequestActivity", "Request uploaded without photo: $requestId")
                    Toast.makeText(this, "Заявка отправлена", Toast.LENGTH_SHORT).show()
                    sendNotificationToAdmin("Новая заявка", "Добавлена заявка: $category - $description")
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("AddRequestActivity", "Failed to upload request: ${e.message}")
                Toast.makeText(this, "Ошибка отправки заявки: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun sendNotificationToAdmin(title: String, body: String) {
        val adminId = "tq7TSjHvnrMuIqRzC641O3vHzzy2" // Замените на реальный ID администратора
        Log.d("AddRequestActivity", "Sending notification to admin $adminId: $title - $body")
        try {
            val client = OkHttpClient()
            val json = JSONObject().apply {
                put("where", JSONObject().apply {
                    put("userId", adminId)
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
                    Log.d("AddRequestActivity", "Back4App response: ${response.code} - $responseBody")
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Log.i("AddRequestActivity", "Notification sent successfully to admin $adminId")
                            Toast.makeText(this@AddRequestActivity, "Уведомление отправлено", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.e("AddRequestActivity", "Notification failed with response: $responseBody")
                            Toast.makeText(this@AddRequestActivity, "Ошибка отправки уведомления: ${response.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("AddRequestActivity", "Back4App request failed: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@AddRequestActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("AddRequestActivity", "Error in sendNotificationToAdmin: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this@AddRequestActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}