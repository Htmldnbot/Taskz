package com.example.data.remote

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.security.SecureRandom

sealed interface GoogleSignInTokenResult {
    data class Success(
        val idToken: String,
        val rawNonce: String,
        val email: String?,
        val displayName: String?
    ) : GoogleSignInTokenResult

    object Cancelled : GoogleSignInTokenResult
    data class Failure(val message: String) : GoogleSignInTokenResult
}

/**
 * Native Android Credential Manager wrapper for Google ID-token acquisition.
 * Generates a cryptographic raw nonce and passes its SHA-256 hex digest to Google Sign-In
 * so Supabase Auth can verify the nonce on POST /auth/v1/token?grant_type=id_token.
 */
class GoogleAuthManager {

    suspend fun requestGoogleIdToken(activity: Activity): GoogleSignInTokenResult {
        val webClientId = activity.getString(R.string.google_web_client_id).trim()
        if (webClientId.isBlank() || webClientId.startsWith("YOUR_")) {
            return GoogleSignInTokenResult.Failure(
                "Google Web Client ID is not configured in strings.xml."
            )
        }

        val rawNonce = generateRawNonce()
        val hashedNonce = sha256Hex(rawNonce)

        val googleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        return try {
            val credentialManager = CredentialManager.create(activity)
            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleCredential.idToken
                if (idToken.isBlank()) {
                    GoogleSignInTokenResult.Failure("Google Sign-In returned an empty ID token.")
                } else {
                    GoogleSignInTokenResult.Success(
                        idToken = idToken,
                        rawNonce = rawNonce,
                        email = googleCredential.id.takeIf { it.isNotBlank() },
                        displayName = googleCredential.displayName?.takeIf { it.isNotBlank() }
                    )
                }
            } else {
                GoogleSignInTokenResult.Failure("Unexpected credential type received from Google.")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInTokenResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInTokenResult.Failure(
                "No Google account found on this device. Please sign in to a Google account in device settings."
            )
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInTokenResult.Failure(
                "Failed to read Google ID token: ${e.message ?: "invalid format"}"
            )
        } catch (e: GetCredentialException) {
            val msg = e.errorMessage?.toString() ?: e.message ?: "Google Sign-In failed."
            if (msg.contains("16:") ||
                msg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
                msg.contains("Cannot find a matching credential", ignoreCase = true)
            ) {
                GoogleSignInTokenResult.Failure(
                    "Google Sign-In OAuth mismatch. Verify package com.taskz.app and signing SHA-1/SHA-256 in Google Cloud Console."
                )
            } else {
                GoogleSignInTokenResult.Failure(msg)
            }
        } catch (e: Exception) {
            GoogleSignInTokenResult.Failure(e.message ?: "Google Sign-In failed.")
        }
    }

    companion object {
        fun generateRawNonce(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }

        fun sha256Hex(rawNonce: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(rawNonce.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
