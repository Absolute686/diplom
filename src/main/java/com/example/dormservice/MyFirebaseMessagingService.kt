package com.example.dormservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.parse.ParseInstallation
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val channelId = "DormServiceChannel" // Переименовано с CHANNEL_ID на channelId

    override fun onNewToken(token: String) {
        Log.d("MyFCMService", "Refreshed token: $token")
        val userId = getCurrentUserId()
        if (userId != null) {
            saveTokenToDatabase(userId, token)
            val installation = ParseInstallation.getCurrentInstallation()
            installation.put("deviceToken", token as Any) // Явно приводим к Any
            installation.saveInBackground { e ->
                if (e != null) {
                    Log.e("MyFCMService", "Failed to update ParseInstallation: ${e.message}")
                } else {
                    Log.d("MyFCMService", "ParseInstallation updated with new token: $token")
                }
            }
        } else {
            Log.w("MyFCMService", "No Firebase user logged in, updating ParseInstallation anyway")
            val installation = ParseInstallation.getCurrentInstallation()
            installation.put("deviceToken", token as Any) // Явно приводим к Any
            installation.saveInBackground { e ->
                if (e != null) {
                    Log.e("MyFCMService", "Failed to update ParseInstallation: ${e.message}")
                } else {
                    Log.d("MyFCMService", "ParseInstallation updated with new token: $token")
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("MyFCMService", "Message received: ${remoteMessage.data}")
        val intent = Intent(this, AdminActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(this, channelId) // Обновлено на channelId
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(remoteMessage.notification?.title ?: "New Notification")
            .setContentText(remoteMessage.notification?.body ?: "You have a new message")
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notificationBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "DormService Notifications"
            val descriptionText = "Channel for DormService notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply { // Обновлено на channelId
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
                    Log.d("MyFCMService", "Token saved successfully to Firebase")
                }
                .addOnFailureListener { e ->
                    Log.e("MyFCMService", "Failed to save token to Firebase: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e("MyFCMService", "Failed to save token to Firebase database: ${e.message}", e)
        }
    }

    private fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }
}