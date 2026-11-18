package org.app.core.feature.login

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException

/**
 * Requests an OAuth access token for Google Drive through the Identity AuthorizationClient.
 * The access token is short lived, so ask for it right before every Drive request.
 */
class DriveAuthorizer(context: Context) {

    sealed interface Result {
        data class Success(val accessToken: String, val email: String?) : Result
        data class NeedsConsent(val pendingIntent: PendingIntent) : Result
        data class Failure(val cause: Throwable?) : Result
    }

    private val client = Identity.getAuthorizationClient(context)
    private val driveScope = Scope(DriveScopes.DRIVE_FILE)

    suspend fun authorize(email: String? = null): Result = withContext(Dispatchers.IO) {
        authorizeBlocking(email)
    }

    /** Must not be called on the main thread. */
    fun authorizeBlocking(email: String? = null): Result {
        return try {
            val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(driveScope))
            if (!email.isNullOrBlank()) {
                builder.setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
            }
            toResult(Tasks.await(client.authorize(builder.build())))
        } catch (e: ExecutionException) {
            Result.Failure(e.cause ?: e)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /** Always shows the Google account chooser; the chosen email is in [android.accounts.AccountManager.KEY_ACCOUNT_NAME]. */
    fun accountChooserIntent(): Intent = AccountPicker.newChooseAccountIntent(
        AccountPicker.AccountChooserOptions.Builder()
            .setAllowableAccountsTypes(listOf(GOOGLE_ACCOUNT_TYPE))
            .setAlwaysShowAccountPicker(true)
            .build()
    )

    /** Parses the data returned by the consent screen launched from [Result.NeedsConsent]. */
    fun parseResult(data: Intent?): Result {
        if (data == null) return Result.Failure(null)
        return try {
            toResult(client.getAuthorizationResultFromIntent(data))
        } catch (e: ApiException) {
            Result.Failure(e)
        }
    }

    private fun toResult(result: AuthorizationResult): Result {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent ?: return Result.Failure(null)
            return Result.NeedsConsent(pendingIntent)
        }

        val token = result.accessToken
        if (token.isNullOrBlank()) return Result.Failure(null)

        // The user can untick the Drive permission on the consent screen
        if (!result.grantedScopes.contains(DriveScopes.DRIVE_FILE)) {
            return Result.Failure(IllegalStateException("Drive permission was not granted"))
        }
        return Result.Success(token, result.toGoogleSignInAccount()?.email)
    }

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
