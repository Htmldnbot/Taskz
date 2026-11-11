package com.example.core.network

import com.example.core.datastore.UserSession
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

object FirebaseSessionHelper {

    fun isFirebaseJwt(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val parts = token.split(".")
        if (parts.size != 3) return false
        return try {
            val payload = String(
                android.util.Base64.decode(
                    parts[1],
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                ),
                Charsets.UTF_8
            )
            payload.contains("securetoken.google.com") ||
                    payload.contains("google.com") ||
                    payload.contains("firebase")
        } catch (_: Exception) {
            false
        }
    }

    fun isFirebaseSession(
        session: UserSession?,
        token: String? = session?.accessToken
    ): Boolean {
        if (Firebase.auth.currentUser != null) return true
        if (session?.refreshToken?.startsWith("firebase_") == true) return true
        if (session?.userId?.startsWith("phone_user_") == true) return true
        if (token?.startsWith("firebase_") == true) return true
        if (token?.startsWith("offline_") == true) return true
        if (isFirebaseJwt(token)) return true
        return false
    }

    fun isFirebaseSession(token: String?): Boolean {
        if (Firebase.auth.currentUser != null) return true
        if (token?.startsWith("firebase_") == true) return true
        if (token?.startsWith("offline_") == true) return true
        if (isFirebaseJwt(token)) return true
        return false
    }
}
