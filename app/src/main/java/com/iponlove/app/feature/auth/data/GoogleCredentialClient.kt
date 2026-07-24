package com.iponlove.app.feature.auth.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of asking Credential Manager for a Google ID token (ADR-0050). */
sealed interface GoogleCredentialResult {
    /** [nonce] is the *raw* nonce — Supabase re-hashes it to match the token's claim. */
    data class Success(val idToken: String, val nonce: String) : GoogleCredentialResult
    /** The user dismissed the picker — stay silent, show nothing. */
    data object Cancelled : GoogleCredentialResult
    data class Failure(val error: com.iponlove.app.feature.auth.domain.model.AuthError) :
        GoogleCredentialResult
}

/**
 * Thin Android wrapper over the Jetpack Credential Manager for Google Sign-In (ADR-0050 decision 2).
 * Requests a Google ID token on-device; the token then rides the normal
 * ViewModel→UseCase→Repository spine into `signInWith(IDToken)`. Deliberately not `compose-auth`,
 * which would put the auth call in the Composable and break layering.
 *
 * Takes an [Activity] per call (Credential Manager needs a UI context) and **never stores it**.
 */
@Singleton
class GoogleCredentialClient @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val credentialManager = CredentialManager.create(appContext)

    /**
     * @param webClientId the **Web** OAuth client id (server client id) — never the Android client
     *   id, the classic Credential Manager failure trap (ADR-0050 decision 8).
     */
    suspend fun getIdToken(activity: Activity, webClientId: String): GoogleCredentialResult {
        // Fresh per request. Google embeds the SHA-256 hash of this nonce in the ID token; we hand
        // the raw value to Supabase, which hashes and compares — binding the token to this attempt.
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = sha256(rawNonce)

        // The button-flow option, not GetGoogleIdOption: it's purpose-built for a "Continue with
        // Google" tap and always launches the full account chooser (exactly ADR-0050 decision 7's
        // "picker always shows; explicit action" — and no clearCredentialState() plumbing on
        // sign-out). GetGoogleIdOption drives the automatic one-tap bottom sheet instead, which
        // threw NoCredentialException on-device even with valid accounts + config (2026-07-24).
        val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                GoogleCredentialResult.Success(idToken = token, nonce = rawNonce)
            } else {
                // Some other credential type came back — we only asked for a Google ID token.
                Log.w(TAG, "Unexpected credential type: ${credential.type}")
                GoogleCredentialResult.Failure(
                    com.iponlove.app.feature.auth.domain.model.AuthError.GOOGLE_SIGN_IN_FAILED,
                )
            }
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google credential request failed", e)
            when (val error = GoogleSignInErrorMapper.classify(e.javaClass.simpleName, e.message)) {
                null -> GoogleCredentialResult.Cancelled
                else -> GoogleCredentialResult.Failure(error)
            }
        } catch (e: GoogleIdTokenParsingException) {
            Log.w(TAG, "Google ID token parse failed", e)
            GoogleCredentialResult.Failure(
                com.iponlove.app.feature.auth.domain.model.AuthError.GOOGLE_SIGN_IN_FAILED,
            )
        }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "GoogleCredentialClient"
    }
}
