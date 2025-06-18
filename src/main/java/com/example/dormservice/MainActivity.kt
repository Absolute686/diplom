package com.example.dormservice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dormservice.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate started")
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d("MainActivity", "Layout inflated successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to inflate layout: ${e.message}", e)
            Toast.makeText(this, "Ошибка макета: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Проверка элементов layout
        if (binding.emailEditText == null || binding.passwordEditText == null ||
            binding.loginButton == null || binding.registerButton == null) {
            Log.e("MainActivity", "One or more layout elements are missing")
            Toast.makeText(this, "Ошибка: элементы интерфейса отсутствуют", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            auth = FirebaseAuth.getInstance()
            Log.d("MainActivity", "FirebaseAuth initialized")
        } catch (e: Exception) {
            Log.e("MainActivity", "FirebaseAuth initialization failed: ${e.message}", e)
            Toast.makeText(this, "Ошибка Firebase: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Проверка, если пользователь уже вошел
        try {
            if (auth.currentUser != null) {
                Log.d("MainActivity", "User already logged in: ${auth.currentUser?.uid}")
                auth.currentUser?.uid?.let { userId ->
                    lifecycleScope.launch {
                        saveFcmToken(userId)
                        checkUserRoleAndRedirect(userId)
                    }
                } ?: run {
                    Log.e("MainActivity", "User is logged in but UID is null")
                    Toast.makeText(this, "Ошибка: UID пользователя не найден", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.d("MainActivity", "No user logged in, showing login screen")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking current user: ${e.message}", e)
            Toast.makeText(this, "Ошибка проверки пользователя: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        binding.loginButton.setOnClickListener {
            try {
                if (!isNetworkAvailable()) {
                    Log.e("MainActivity", "No internet connection")
                    Toast.makeText(this, "Нет подключения к интернету", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val email = binding.emailEditText.text.toString().trim()
                val password = binding.passwordEditText.text.toString().trim()
                Log.d("MainActivity", "Email: $email, Password: $password")

                if (email.isEmpty() || password.isEmpty()) {
                    Log.w("MainActivity", "Login failed: Email or password is empty")
                    Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Log.d("MainActivity", "Attempting login with email: $email")
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        val userId = auth.currentUser?.uid
                        if (userId != null) {
                            Log.i("MainActivity", "Login successful for user: $userId")
                            lifecycleScope.launch {
                                saveFcmToken(userId)
                                checkUserRoleAndRedirect(userId)
                            }
                        } else {
                            Log.e("MainActivity", "Login successful but userId is null")
                            Toast.makeText(this, "Ошибка: пользователь не найден", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Login failed: ${e.message}", e)
                        Toast.makeText(this, "Ошибка входа: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } catch (e: Exception) {
                Log.e("MainActivity", "Login button error: ${e.message}", e)
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        binding.registerButton.setOnClickListener {
            try {
                if (!isNetworkAvailable()) {
                    Log.e("MainActivity", "No internet connection")
                    Toast.makeText(this, "Нет подключения к интернету", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val email = binding.emailEditText.text.toString().trim()
                val password = binding.passwordEditText.text.toString().trim()

                if (email.isEmpty() || password.isEmpty()) {
                    Log.w("MainActivity", "Registration failed: Email or password is empty")
                    Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Log.d("MainActivity", "Attempting registration with email: $email")
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        val userId = auth.currentUser?.uid
                        if (userId != null) {
                            Log.i("MainActivity", "Registration successful for user: $userId")
                            FirebaseDatabase.getInstance().reference.child("users").child(userId)
                                .setValue(mapOf("isAdmin" to false))
                                .addOnSuccessListener {
                                    Log.d("MainActivity", "User profile created for user: $userId")
                                    lifecycleScope.launch {
                                        saveFcmToken(userId)
                                        checkUserRoleAndRedirect(userId)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("MainActivity", "Failed to create user profile: ${e.message}", e)
                                    Toast.makeText(this, "Ошибка создания профиля: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        } else {
                            Log.e("MainActivity", "Registration successful but userId is null")
                            Toast.makeText(this, "Ошибка: пользователь не найден", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Registration failed: ${e.message}", e)
                        Toast.makeText(this, "Ошибка регистрации: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } catch (e: Exception) {
                Log.e("MainActivity", "Registration button error: ${e.message}", e)
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetworkInfo
            return activeNetwork != null && activeNetwork.isConnected
        } catch (e: Exception) {
            Log.e("MainActivity", "Network check failed: ${e.message}", e)
            return false
        }
    }

    private fun saveFcmToken(userId: String) {
        try {
            Log.d("MainActivity", "Saving FCM token for user: $userId")
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    if (token != null) {
                        Log.d("MainActivity", "Token retrieved: $token")
                        saveTokenToDatabase(userId, token)
                    } else {
                        Log.e("MainActivity", "Retrieved token is null")
                    }
                } else {
                    Log.e("MainActivity", "Failed to retrieve token: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to save FCM token: ${e.message}", e)
            Toast.makeText(this, "Ошибка сохранения токена: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveTokenToDatabase(userId: String, token: String) {
        try {
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(userId)
                .child("fcmToken")
                .setValue(token)
                .addOnSuccessListener {
                    Log.d("MainActivity", "Token saved successfully to Firebase")
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Failed to save token to Firebase: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to save token to Firebase database: ${e.message}", e)
        }
    }

    private fun checkUserRoleAndRedirect(userId: String) {
        Log.d("MainActivity", "Checking role for user: $userId")
        try {
            FirebaseDatabase.getInstance().reference.child("users").child(userId).child("isAdmin")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val isAdmin = snapshot.getValue(Boolean::class.java) ?: false
                            Log.d("MainActivity", "User role retrieved: isAdmin=$isAdmin")
                            val intent = if (isAdmin) {
                                Intent(this@MainActivity, AdminActivity::class.java)
                            } else {
                                Intent(this@MainActivity, RequestsActivity::class.java)
                            }
                            startActivity(intent)
                            finish()
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error in onDataChange: ${e.message}", e)
                            Toast.makeText(this@MainActivity, "Ошибка обработки роли: ${e.message}", Toast.LENGTH_LONG).show()
                            startActivity(Intent(this@MainActivity, RequestsActivity::class.java))
                            finish()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("MainActivity", "Failed to retrieve user role: ${error.message}")
                        Toast.makeText(this@MainActivity, "Ошибка: ${error.message}", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@MainActivity, RequestsActivity::class.java))
                        finish()
                    }
                })
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in checkUserRoleAndRedirect: ${e.message}", e)
            Toast.makeText(this, "Ошибка проверки роли: ${e.message}", Toast.LENGTH_LONG).show()
            startActivity(Intent(this@MainActivity, RequestsActivity::class.java))
            finish()
        }
    }
}