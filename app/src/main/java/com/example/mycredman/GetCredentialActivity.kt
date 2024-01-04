package com.example.mycredman

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.webauthn.AuthenticatorAssertionResponse
import androidx.credentials.webauthn.FidoPublicKeyCredential
import androidx.credentials.webauthn.PublicKeyCredentialRequestOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec


class GetCredentialActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val getRequest =
            PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        val publicKeyRequests =
            getRequest?.credentialOptions as List<GetPublicKeyCredentialOption>

        val requestInfo = intent.getBundleExtra("CREDENTIAL_DATA")
        publicKeyRequests.forEach { credentialOption ->
//            credentialOption.requestData.keySet().forEach { key ->
//                val value = credentialOption.requestData.get(key)
//                Log.d("GetCredActivity", "key: $key, value: $value")
//            }
            Log.d("GetCredActivity", "requsetJson: ${credentialOption.requestJson}")
        }

        val credIdEnc = requestInfo?.getString("credId")
        val requestJson = Json.decodeFromString<GetPublicKeyCredentialRequestJson>(publicKeyRequests[0].requestJson)
        Log.d("GetCredActivity", "rpid: ${requestJson.rpId}")

// Get the saved passkey from your database based on the credential ID
// from the publickeyRequest
        val passkey = MyCredentialDataManager.load(this,requestJson.rpId,credIdEnc!!.toByteArray())

// Decode the credential ID, private key and user ID

        val credId = CredmanUtils.b64Decode(credIdEnc)
        val privateKey = CredmanUtils.b64Decode(passkey!!.keyPair!!.private.toString())
        val uid = CredmanUtils.b64Decode(credIdEnc)

        val origin = CredmanUtils.appInfoToOrigin(getRequest.callingAppInfo)
        val packageName = getRequest.callingAppInfo.packageName


        validatePasskey(
            publicKeyRequests[0].requestJson,
            origin,
            packageName,
            uid!!,
            passkey.displayName,
            credId!!,
            privateKey!!
        )



    }


    fun validatePasskey(requestJson:String, origin:String, packageName:String, uid:ByteArray, username:String, credId:ByteArray, privateKeyBytes:ByteArray){
        val request = PublicKeyCredentialRequestOptions(requestJson)
        val privateKey: ECPrivateKey = convertPrivateKey(privateKeyBytes)

        val biometricPrompt = BiometricPrompt(
            this,
            this.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(
                errorCode: Int, errString: CharSequence
            ) {
                super.onAuthenticationError(errorCode, errString)
                finish()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                finish()
            }

            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                super.onAuthenticationSucceeded(result)
                val response = AuthenticatorAssertionResponse(
                    requestOptions = request,
                    credentialId = credId,
                    origin = origin,
                    up = true,
                    uv = true,
                    be = true,
                    bs = true,
                    userHandle = uid,
                    packageName = packageName
                )

                val sig = Signature.getInstance("SHA256withECDSA")
                sig.initSign(privateKey)
                sig.update(response.dataToSign())
                response.signature = sig.sign()

                val credential = FidoPublicKeyCredential(
                    rawId = credId, response = response
                    , authenticatorAttachment = "platform")
                Log.d("GetCredActivity", "+++ credential.json(): "+ credential.json())
                val result = Intent()
                val passkeyCredential = PublicKeyCredential(credential.json())
                PendingIntentHandler.setGetCredentialResponse(
                    result, GetCredentialResponse(passkeyCredential)
                )
                setResult(RESULT_OK, result)
                finish()
            }
        }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Use your screen lock")
            .setSubtitle("Use passkey for ${request.rpId}")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                /* or BiometricManager.Authenticators.DEVICE_CREDENTIAL */
            )
            .build()
        biometricPrompt.authenticate(promptInfo)
    }



    fun convertPrivateKey(privatekey:ByteArray): ECPrivateKey {
        val curveName = "secp256r1"
        val privKeyBI = BigInteger(1, privatekey)
        val ecParameterSpec = getParametersForCurve(curveName)
        val privateKeySpec = ECPrivateKeySpec(privKeyBI, ecParameterSpec)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(privateKeySpec) as ECPrivateKey
    }
    fun getParametersForCurve(curveName: String): ECParameterSpec {
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec(curveName))
        return params.getParameterSpec(ECParameterSpec::class.java)
    }
}



@Serializable
data class GetPublicKeyCredentialRequestJson(
    val allowCredentials:Array<AllowCredential>? = null,
    val challenge:String,
    val rpId:String,
    val userVerification: String,
    val timeout: Int? = null
) {
    @Serializable
    data class AllowCredential(
        val id: String,
        val transports:Array<String>,
        val type: String
    )
}

