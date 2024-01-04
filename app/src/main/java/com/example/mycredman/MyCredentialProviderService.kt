package com.example.mycredman

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import androidx.credentials.webauthn.PublicKeyCredentialRequestOptions



class MyCredentialProviderService: androidx.credentials.provider.CredentialProviderService() {

    private val CREATE_PASSKEY_INTENT = "com.example.mycredman.action.CREATE_PASSKEY"
    private val PERSONAL_ACCOUNT_ID= "PERSONAL_ACCOUNT_ID"
    private val FAMILY_ACCOUNT_ID="FAMILY_ACCOUNT_ID"
    private val EXTRA_KEY_ACCOUNT_ID  = "com.example.mycredman.extra.EXTRA_KEY_ACCOUNT_ID"
    private val UNLOCK_PASSKEY_INTENT = "com.example.mycredman.action.GET_PASSKEY"
    private val unlockEntryTitle = "Authenticate to continue"

    // https://developer.android.com/training/sign-in/credential-provider#handle-queries-passkey-creation
    override fun onBeginCreateCredentialRequest(
        request: androidx.credentials.provider.BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<androidx.credentials.provider.BeginCreateCredentialResponse, androidx.credentials.exceptions.CreateCredentialException>,
    ) {
        val response: androidx.credentials.provider.BeginCreateCredentialResponse? = processCreateCredentialRequest(request)
        if (response != null) {
            callback.onResult(response)
        } else {
            callback.onError(CreateCredentialUnknownException())
        }

    }

    // https://developer.android.com/training/sign-in/credential-provider#handle-user-sign-in
    override fun onBeginGetCredentialRequest(
        request: androidx.credentials.provider.BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        if (isAppLocked()) {
            callback.onResult(BeginGetCredentialResponse(
                authenticationActions = mutableListOf(
                    AuthenticationAction(
                    unlockEntryTitle, createUnlockPendingIntent())
                )
            )
            )
            return
        }
        try {
            val response = processGetCredentialRequest(request)
            callback.onResult(response)
        } catch (e: GetCredentialException) {
            callback.onError(GetCredentialUnknownException())
        }
    }

    //TODO()
    private fun isAppLocked(): Boolean {
        return false
    }


    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        TODO("Not yet implemented")
    }

    fun processCreateCredentialRequest(request: androidx.credentials.provider.BeginCreateCredentialRequest): androidx.credentials.provider.BeginCreateCredentialResponse? {
        when (request) {
            is BeginCreatePublicKeyCredentialRequest -> {
                // Request is passkey type
                return handleCreatePasskeyQuery(request)
            }
        }
        // Request not supported
        return null
    }

    private fun handleCreatePasskeyQuery(
        request: BeginCreatePublicKeyCredentialRequest
    ): androidx.credentials.provider.BeginCreateCredentialResponse {

        // Adding two create entries - one for storing credentials to the 'Personal'
        // account, and one for storing them to the 'Family' account. These
        // accounts are local to this sample app only.
        val createEntries: MutableList<CreateEntry> = mutableListOf()
        createEntries.add( CreateEntry(
            PERSONAL_ACCOUNT_ID,
            createNewCreatePendingIntent(PERSONAL_ACCOUNT_ID, CREATE_PASSKEY_INTENT)
        ))

        createEntries.add( CreateEntry(
            FAMILY_ACCOUNT_ID,
            createNewCreatePendingIntent(FAMILY_ACCOUNT_ID, CREATE_PASSKEY_INTENT)
        ))

        return androidx.credentials.provider.BeginCreateCredentialResponse(createEntries)
    }

    private fun createNewCreatePendingIntent(accountId: String, action: String): PendingIntent {
        val intent = Intent(action).setPackage(this.packageName )

        // Add your local account ID as an extra to the intent, so that when
        // user selects this entry, the credential can be saved to this
        // account
        intent.putExtra(EXTRA_KEY_ACCOUNT_ID, accountId)

        val requestCode = (1..9999).random()

        return PendingIntent.getActivity(
            applicationContext, requestCode,
            intent, (
                    PendingIntent.FLAG_MUTABLE
                            or PendingIntent.FLAG_UPDATE_CURRENT
                    )
        )
    }
    private fun createUnlockPendingIntent(): PendingIntent {
        //TODO()
        val intent = Intent(UNLOCK_PASSKEY_INTENT).setPackage(this.packageName)
        val requestCode = (1..9999).random()
        return PendingIntent.getActivity(
            applicationContext, requestCode, intent, (
                    PendingIntent.FLAG_MUTABLE
                            or PendingIntent.FLAG_UPDATE_CURRENT
                    )
        )
    }

    companion object {
        // These intent actions are specified for corresponding activities
        // that are to be invoked through the PendingIntent(s)
        private const val GET_PASSKEY_INTENT_ACTION = "com.example.mycredman.action.GET_PASSKEY"
        private const val GET_PASSWORD_INTENT_ACTION = "PACKAGE_NAME.GET_PASSWORD"

    }


    fun processGetCredentialRequest(
        request: androidx.credentials.provider.BeginGetCredentialRequest
    ): BeginGetCredentialResponse {
        val credentialEntries: MutableList<CredentialEntry> = mutableListOf()

        for (option in request.beginGetCredentialOptions) {
            when (option) {
                is BeginGetPasswordOption -> {
                    throw GetCredentialUnsupportedException("Password not supported")
                }
                is BeginGetPublicKeyCredentialOption -> {
                    credentialEntries.addAll(
                        populatePasskeyData(
                            request.callingAppInfo!!,
                            option
                        )
                    )

                } else -> {
                Log.i("MyCredMan", "Request not supported")
            }
            }
        }
        return BeginGetCredentialResponse(credentialEntries)
    }

    private fun populatePasskeyData(
        callingAppInfo: CallingAppInfo,
        option: BeginGetPublicKeyCredentialOption
    ): List<CredentialEntry> {
        val passkeyEntries: MutableList<CredentialEntry> = mutableListOf()
        val request = PublicKeyCredentialRequestOptions(option.requestJson)
        // Get your credentials from database where you saved during creation flow

        val rpid = CredmanUtils.validateRpId(callingAppInfo,request.rpId)
        val creds = MyCredentialDataManager.load(this, rpid)
        Log.d("ProviderService", "requestJson:" + option.requestJson)
        Log.d("ProviderService", "rpid: $rpid")


        for (passkey in creds){
            Log.d("ProviderService", "Found Passkey: " +passkey.displayName)
            val data = Bundle()
            data.putString("credId", CredmanUtils.b64Encode(passkey.credentialId))
            passkeyEntries.add(
                PublicKeyCredentialEntry.Builder(
                    context = applicationContext,
                    username = passkey.displayName,
                    pendingIntent = createNewGetPendingIntent(
                        GET_PASSKEY_INTENT_ACTION,
                        data
                    ),
                    beginGetPublicKeyCredentialOption = option
                ).build()
            )
        }
        return passkeyEntries
    }

    private fun createNewGetPendingIntent(
        action: String,
        extra: Bundle? = null
    ): PendingIntent {
        val intent = Intent(action).setPackage(this.packageName)
        if (extra != null) {
            intent.putExtra("CREDENTIAL_DATA", extra)
        }

        val requestCode = (1..9999).random()

        return PendingIntent.getActivity(
            applicationContext, requestCode, intent,
            (PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

}