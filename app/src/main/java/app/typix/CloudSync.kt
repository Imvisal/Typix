package app.typix

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Optional Google account sync of the keyboard SETTINGS (theme, colors, switches ...). Never touches typed text, clipboard or pinned clips.
 * Stored in Firestore at users/{uid}; the security rules only let that user read / write it.
 */
object CloudSync {
    private val main = Handler(Looper.getMainLooper())

    private fun ready(c: Context): Boolean = try {
        if (FirebaseApp.getApps(c).isEmpty()) FirebaseApp.initializeApp(c.applicationContext)
        FirebaseApp.getApps(c).isNotEmpty()
    } catch (e: Exception) { false }

    fun signedIn(c: Context) = ready(c) && FirebaseAuth.getInstance().currentUser != null
    fun account(c: Context): String? = if (!ready(c)) null else FirebaseAuth.getInstance().currentUser?.let { it.email ?: it.displayName ?: "Signed in" }

    private fun nice(e: Throwable?): String {
        val m = e?.message ?: return "Something went wrong"
        return when {
            m.contains("PERMISSION_DENIED", true) -> "Cloud access denied - check the Firestore rules"
            m.contains("UNAVAILABLE", true) || m.contains("network", true) || m.contains("offline", true) -> "No internet connection"
            m.contains("NOT_FOUND", true) -> "Create the Firestore database in the Firebase console first"
            else -> m.take(120)
        }
    }

    /** Google account picker -> Firebase sign-in -> restore settings from the account (or upload these if the account has none yet). done(null) = success. */
    fun signIn(a: Activity, done: (String?) -> Unit) {
        if (!ready(a)) { done("Firebase is not set up in this build"); return }
        val id = try { a.getString(a.resources.getIdentifier("default_web_client_id", "string", a.packageName)) } catch (e: Exception) { "" }
        if (id.isEmpty()) { done("Missing web client id"); return }
        val opt = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(id).setAutoSelectEnabled(false).build()
        val req = GetCredentialRequest.Builder().addCredentialOption(opt).build()
        CredentialManager.create(a).getCredentialAsync(a, req, null, { r -> main.post(r) }, object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
            override fun onResult(result: GetCredentialResponse) {
                val cred = result.credential
                if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { done("Unsupported sign-in"); return }
                val token = try { GoogleIdTokenCredential.createFrom(cred.data).idToken } catch (e: Exception) { done(nice(e)); return }
                FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                    .addOnSuccessListener {
                        download(a) { n, err ->
                            if (err != null) done(err)
                            else if (n == 0) upload(a) { e2 -> done(e2) } else done(null)
                        }
                    }
                    .addOnFailureListener { done(nice(it)) }
            }
            override fun onError(e: GetCredentialException) { done(if (e is GetCredentialCancellationException) "Sign-in cancelled" else nice(e)) }
        })
    }

    fun signOut(c: Context) { if (ready(c)) FirebaseAuth.getInstance().signOut() }

    /** Save the settings to the account. done(null) = success. */
    fun upload(c: Context, done: (String?) -> Unit) {
        if (!ready(c)) { done("Firebase is not set up in this build"); return }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { done("Not signed in"); return }
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf("settings" to Prefs.exportJson(c), "updated" to System.currentTimeMillis()))
            .addOnSuccessListener { done(null) }.addOnFailureListener { done(nice(it)) }
    }

    /** Restore the settings from the account. done(count, error): count = settings restored (0 = nothing saved yet). */
    fun download(c: Context, done: (Int, String?) -> Unit) {
        if (!ready(c)) { done(0, "Firebase is not set up in this build"); return }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { done(0, "Not signed in"); return }
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { s -> val t = s.getString("settings"); done(if (t == null) 0 else Prefs.importJson(c, t), null) }
            .addOnFailureListener { done(0, nice(it)) }
    }
}
