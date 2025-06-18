package com.example.dormservice

import android.app.Application
import com.parse.Parse
import com.parse.ParseInstallation
import com.parse.ParseCloud
import android.util.Log
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.messaging.FirebaseMessaging
import java.util.HashMap

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MyApplication", "onCreate started at ${getCurrentTime()}")
        try {
            if (!isNetworkAvailable()) {
                Log.e("MyApplication", "No internet connection, skipping Parse initialization")
                return
            }

            Parse.initialize(
                Parse.Configuration.Builder(this)
                    .applicationId("ASA9CqeZej17oJxP8f3PfNOOsLoESFKOTH51mGYW")
                    .clientKey("Z0zpi8l3B2G4kYZ0HR65bra2kWVh6k0ux5n0zPNe")
                    .server("https://parseapi.back4app.com/")
                    .enableLocalDataStore()
                    .build()
            )
            Log.d("MyApplication", "Parse initialized successfully")

            val installation = ParseInstallation.getCurrentInstallation()
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e("MyApplication", "Failed to get FCM token: ${task.exception?.message}")
                    return@addOnCompleteListener
                }
                val fcmToken = task.result ?: ""
                if (fcmToken.isEmpty()) {
                    Log.e("MyApplication", "FCM token is empty - possible issue with google-services.json")
                } else {
                    Log.d("MyApplication", "FCM token received: $fcmToken")
                }

                installation.saveInBackground { e ->
                    if (e != null) {
                        Log.e("MyApplication", "Failed to save ParseInstallation: ${e.message}, code=${e.code}", e)
                    } else {
                        val deviceToken = installation.getString("deviceToken") ?: "null"
                        Log.d("MyApplication", "ParseInstallation saved successfully: objectId=${installation.objectId}, deviceToken=$deviceToken")

                        val params = HashMap<String, String>()
                        params["message"] = "Test notification at ${getCurrentTime()}"
                        ParseCloud.callFunctionInBackground<String>("sendPushNotification", params) { result, e ->
                            if (e != null) {
                                Log.e("MyApplication", "Push failed: ${e.message}")
                            } else {
                                Log.d("MyApplication", "Push result: $result")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MyApplication", "Parse initialization failed: ${e.message}, code=${(e as? com.parse.ParseException)?.code}", e)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getCurrentTime(): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    }
}