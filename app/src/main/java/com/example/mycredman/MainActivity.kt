package com.example.mycredman


import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.webauthn.AuthenticatorAssertionResponse
import androidx.credentials.webauthn.AuthenticatorAttestationResponse
import androidx.credentials.webauthn.FidoPublicKeyCredential
import androidx.credentials.webauthn.PublicKeyCredentialCreationOptions
import androidx.credentials.webauthn.PublicKeyCredentialRequestOptions
import com.example.mycredman.ui.theme.MyCredManTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint

class MainActivity : AppCompatActivity() {

    private val EXTRA_KEY_ACCOUNT_ID  = "com.example.mycredman.extra.EXTRA_KEY_ACCOUNT_ID"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(intent != null && intent.action == "com.example.mycredman.action.CREATE_PASSKEY") {
            val request =
                PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)

            val accountId = intent.getStringExtra(EXTRA_KEY_ACCOUNT_ID)
            if (request != null && request.callingRequest is CreatePublicKeyCredentialRequest) {
                val publicKeyRequest: CreatePublicKeyCredentialRequest =
                    request.callingRequest as CreatePublicKeyCredentialRequest
                createPasskey(
                    publicKeyRequest.requestJson,
                    request.callingAppInfo,
                    publicKeyRequest.clientDataHash,
                    accountId
                )
            }
        }else if(intent.action == "com.example.mycredman.action.GET_PASSKEY"){
            val getRequest =
                PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
            val publicKeyRequests =
                getRequest!!.credentialOptions as List<GetPublicKeyCredentialOption>

            val requestInfo = intent.getBundleExtra("CREDENTIAL_DATA")

            publicKeyRequests.forEach { credentialOption ->
                Log.d("MainActivity", "requsetJson:${credentialOption.requestJson}")
            }

            val credIdEnc = requestInfo?.getString("credId")
            val requestJson = Json.decodeFromString<GetPublicKeyCredentialRequestJson>(publicKeyRequests[0].requestJson)
            Log.d("MainActivity", "onCreate rpid:${requestJson.rpId}")
            Log.d("MainActivity", "${credIdEnc}")

// Get the saved passkey from your database based on the credential ID
// from the publickeyRequest

// Decode the credential ID, private key and user ID

            val credId = CredmanUtils.b64Decode(credIdEnc)
            val rpid = CredmanUtils.validateRpId(getRequest.callingAppInfo,requestJson.rpId)
            val passkey = MyCredentialDataManager.load(this,rpid,credId!!)
            val privateKey = passkey!!.keyPair!!.private as ECPrivateKey
            val uid = passkey.userHandle
            val origin = CredmanUtils.appInfoToOrigin(getRequest.callingAppInfo)
            val packageName = getRequest.callingAppInfo.packageName
            val clientDataHash = publicKeyRequests[0].requestData.getByteArray("androidx.credentials.BUNDLE_KEY_CLIENT_DATA_HASH")
            Log.d("MainActivity","+++ clientDataHash: "+CredmanUtils.b64Encode(clientDataHash!!))

            validatePasskey(
                publicKeyRequests[0].requestJson,
                origin,
                packageName,
                uid,
                passkey.displayName,
                credId,
                privateKey,
                clientDataHash
            )
        }

    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onResume() {
        super.onResume()
        setContent {
            MyCredManTheme {
                val credentialList = MyCredentialDataManager.loadAll(this@MainActivity)
                if (credentialList.size > 0) {
                    Column {
                        TopAppBar(
                            title = { Text(text = "My Credential Manager") },
                        )
                        CredentialList(credentialList)

                    }
                } else {
                    Column {
                        TopAppBar(
                            title = { Text(text = "My Credential Manager") },
                        )
                        Text("No Credential Yet", color = MaterialTheme.colorScheme.primary)
                    }
                }

            }
        }
    }


    @Composable
    fun CredentialList(credentials: MutableList <MyCredentialDataManager.Credential>) {

        val intent = Intent(LocalContext.current,CredentialDetailsActivity::class.java)
        Column (
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            credentials.forEach {
                Column (
                    Modifier
                        .padding(12.dp)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable(onClick = {
                            Log.d("MainActivity", "onClick")
                            intent.putExtra("ServiceName", it.serviceName)
                            intent.putExtra("ServiceNameUrl", it.rpid)
                            intent.putExtra("ServiceNameId", it.displayName)
                            intent.putExtra("stringcredentialId", it.credentialId)
                            Log.d("MainActivity",it.credentialId.toString())
                            startActivity(intent)
                        })
                        .padding(16.dp)
                        .fillMaxWidth()
                )  {
                    Text(text = it.serviceName, color = MaterialTheme.colorScheme.primary, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "URL: ${it.rpid}", color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "ID:  ${it.displayName}", color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }


    @Preview
    @Composable
    fun PreviewCredentialEntry() {
        val credentials = mutableListOf<MyCredentialDataManager.Credential>(
            MyCredentialDataManager.Credential(serviceName = "Sample1", rpid = "www.example.com", displayName="apple1", credentialId = byteArrayOf(0x01)),
            MyCredentialDataManager.Credential(serviceName = "Sample1", rpid = "www.example.com", displayName="apple2", credentialId = byteArrayOf(0x02)),
            MyCredentialDataManager.Credential(serviceName = "Sample1", rpid = "www.example.com", displayName="apple3", credentialId = byteArrayOf(0x03)),

        )
        CredentialList(credentials)
    }

    // https://developer.android.com/training/sign-in/credential-provider#handle-passkey-credential
    private fun createPasskey(
        requestJson: String,
        callingAppInfo: androidx.credentials.provider.CallingAppInfo?,
        clientDataHash: ByteArray?,
        accountId: String?
    ) {
        Log.d("MainActivity", "===requestJson===: "+requestJson)

        val request = PublicKeyCredentialCreationOptions(requestJson)

        val biometricPrompt = BiometricPrompt(
            this,
            this.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(
                errorCode: Int, errString: CharSequence
            ) {
                super.onAuthenticationError(errorCode, errString)
                Log.e("MyCredMan", "onAuthenticationError"+errorCode.toString()+"  " +errString)
                TODO("inplement fallback in case BIOMETRIC is not available")

                finish()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.e("MyCredMan", "onAuthenticationFailed")

                finish()
            }

            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                super.onAuthenticationSucceeded(result)

                // Generate a credentialId
                val credentialId = ByteArray(32)
                SecureRandom().nextBytes(credentialId)

                // Generate a credential key pair
                val spec = ECGenParameterSpec("secp256r1")
                val keyPairGen = KeyPairGenerator.getInstance("EC")
                keyPairGen.initialize(spec)
                val keyPair = keyPairGen.genKeyPair()


                // check if rpid is a subdomain of origin
                val rpid = CredmanUtils.validateRpId(callingAppInfo!!,request.rp.id)
                Log.d("MainActivity", "===rpid === :" + rpid)

                // Save passkey in your database as per your own implementation

                MyCredentialDataManager.save(this@MainActivity, MyCredentialDataManager.Credential(
                    rpid = rpid,
                    serviceName = request.rp.name,
                    credentialId = credentialId,
                    displayName = request.user.displayName,
                    userHandle = request.user.id,
                    keyPair = keyPair
                ))

                // Create AuthenticatorAttestationResponse object to pass to
                // FidoPublicKeyCredential

                val response = AuthenticatorAttestationResponse(
                    requestOptions = request,
                    credentialId = credentialId,
                    credentialPublicKey = getPublicKeyFromKeyPair(keyPair), //CBOR
                    origin = CredmanUtils.appInfoToOrigin(callingAppInfo),
                    up = true,
                    uv = true,
                    be = true,
                    bs = true,
                    packageName = callingAppInfo.packageName,
                    clientDataHash = clientDataHash
                )

                val credential = FidoPublicKeyCredential(
                    rawId = credentialId, response = response , authenticatorAttachment = "platform"
                )

                //add easy accessors fields as defined in https://github.com/w3c/webauthn/pull/1887
                val credentialJson = populateEasyAccessorFields(credential.json(),rpid, keyPair,credentialId)

                val result = Intent()

                val createPublicKeyCredResponse =
                    CreatePublicKeyCredentialResponse(credentialJson)

                // Set the CreateCredentialResponse as the result of the Activity
                PendingIntentHandler.setCreateCredentialResponse(
                    result, createPublicKeyCredResponse
                )
                setResult(Activity.RESULT_OK, result)
                finish()
            }
        }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Use your screen lock")
            .setSubtitle("Create passkey for ${request.rp.name}")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                /* or BiometricManager.Authenticators.DEVICE_CREDENTIAL */
            )
            .setNegativeButtonText("Cancel")

            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun populateEasyAccessorFields(json: String, rpid:String , keyPair: KeyPair, credentialId: ByteArray):String{
        Log.d("MainActivity","=== populateEasyAccessorFields BEFORE === "+ json)
        val response = Json.decodeFromString<CreatePublicKeyCredentialResponseJson>(json)
        response.response.publicKeyAlgorithm = -7 // ES256
        response.response.publicKey = CredmanUtils.b64Encode(keyPair.public.encoded)
        response.response.authenticatorData = getAuthData(rpid, credentialId, keyPair)

        Log.d("MainActivity","=== populateEasyAccessorFields AFTER === "+ Json.encodeToString(response))
        return Json.encodeToString(response)

    }
    private fun getAuthData(rpid:String, credentialRawId:ByteArray, keyPair: KeyPair ):String{
        val AAGUID = "00000000000000000000000000000000"
        check(AAGUID.length % 2 == 0) { "AAGUID Must have an even length" }

        val rpIdHash:ByteArray = MessageDigest.getInstance("SHA-256")
            .digest(rpid.toByteArray())

        val flags: ByteArray = byteArrayOf(0x5d.toByte())
        val signCount:ByteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val aaguid = AAGUID.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        val credentialIdLength:ByteArray = byteArrayOf(0x00, credentialRawId.size.toByte()) // = 20 bytes
        // val credentialId
        val credentialPublicKey:ByteArray =getPublicKeyFromKeyPair(keyPair)

        val retVal = rpIdHash + flags + signCount + aaguid + credentialIdLength + credentialRawId + credentialPublicKey
        return CredmanUtils.b64Encode(retVal)
    //return "446A62A2EF738CC785FB325FE668786CFFBA2D895CB2985E9F6881332144EC73 5D 00000000 00000000000000000000000000000000 0014 E9D02198BBA75CDD9FD51C845CF4ED39C8DF5BCE A501020326200121582010FCE96113AFF9A663E985C8DB9C1BB638777C617071089EDB0F419CB9F99CE2225820E0E4F780F479955C2F1C1178C69A5AAB844A90F0C76DC6DBF81F5D72938391CD"
    }

    // https://developer.android.com/training/sign-in/credential-provider#passkeys-implement
    fun validatePasskey(requestJson:String, origin:String, packageName:String, uid:ByteArray, username:String, credId:ByteArray, privateKey: ECPrivateKey, clientDataHash: ByteArray?){
        val request = PublicKeyCredentialRequestOptions(requestJson)

        val biometricPrompt = BiometricPrompt(
            this,
            this.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int, errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e("MyCredMan", "onAuthenticationError"+errorCode.toString()+"  " +errString)
                    TODO("inplement fallback in case BIOMETRIC is not available")
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.e("MyCredMan", "onAuthenticationFailed")

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
//                        packageName = packageName
                        clientDataHash = clientDataHash
                    )

                    Log.d("MainActivity", "response.dataToSign(): ${CredmanUtils.b64Encode(response.dataToSign())}")

                    val sig = Signature.getInstance("SHA256withECDSA")
                    sig.initSign(privateKey)
                    sig.update(response.dataToSign())
                    response.signature = sig.sign()

                    val credential = FidoPublicKeyCredential(
                        rawId = credId, response = response
                        , authenticatorAttachment = "platform")

                    Log.d("MainActivity", "+++ credential.json(): "+ credential.json())
//                    var credentialJson = credential.json()

                    // add clientDataJSON to the response
                    val clientDataJSONb64 = getClientDataJSONb64(origin, CredmanUtils.b64Encode( request.challenge))
                    val delimiter = "response\":{"
                    val credentialJson = credential.json().substringBeforeLast(delimiter)+ delimiter +
                            "\"clientDataJSON\":\"$clientDataJSONb64\","+
                            credential.json().substringAfterLast(delimiter)

                    Log.d("MainActivity", "+++ credentialJson: "+ credentialJson)

                    val result = Intent()
                    val passkeyCredential = PublicKeyCredential(credentialJson)
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
            .setNegativeButtonText("Cancel") // this needs to be added when using BIOMETRIC
            .build()
        biometricPrompt.authenticate(promptInfo)
    }


    private fun getClientDataJSONb64(origin: String,challenge:String): String {

        val origin = origin.replace(Regex("/$"), "")

        val jsonString =
            "{\"type\":\"webauthn.get\",\"challenge\":\"$challenge\",\"origin\":\"$origin\",\"crossOrigin\":false}"
        val jsonByteArray = jsonString.toByteArray()
        Log.d("MainActivity","+++ ClientDataJSON: $jsonString")
        return CredmanUtils.b64Encode(jsonByteArray)
    }


    @Serializable
    private data class CreatePublicKeyCredentialResponseJson(
        //RegistrationResponseJSON
        val id:String,
        val rawId: String,
        val response: Response,
        val authenticatorAttachment: String?,
        val clientExtensionResults: EmptyClass = EmptyClass(),
        val type: String,
    ) {
        @Serializable
        data class Response(
            //AuthenticatorAttestationResponseJSON
            val clientDataJSON: String? = null,
            var authenticatorData: String? = null,
            val transports: List<String>? = arrayOf("internal").toList(),
            var publicKey: String? = null, // easy accessors fields
            var publicKeyAlgorithm: Long? =null, // easy accessors fields
            val attestationObject: String? // easy accessors fields
        )
        @Serializable
        class EmptyClass
    }

    private fun getPublicKeyFromKeyPair(keyPair: KeyPair?): ByteArray {
        // credentialPublicKey CBOR
        if (keyPair==null) return ByteArray(0)
        if (keyPair.public !is ECPublicKey) return ByteArray(0)

        val ecPubKey = keyPair.public as ECPublicKey
        val ecPoint: ECPoint = ecPubKey.w

        // for now, only covers ES256
        if (ecPoint.affineX.bitLength() > 256 || ecPoint.affineY.bitLength() > 256) return ByteArray(0)

        val byteX = bigIntToByteArray32(ecPoint.affineX)
        val byteY = bigIntToByteArray32(ecPoint.affineY)

        // refer to RFC9052 Section 7 for details
        return "A5010203262001215820".chunked(2).map { it.toInt(16).toByte() }.toByteArray() +
                byteX+
                "225820".chunked(2).map { it.toInt(16).toByte() }.toByteArray() +
                byteY
    }

    private fun bigIntToByteArray32(bigInteger: BigInteger):ByteArray{
        var ba = bigInteger.toByteArray()

        if(ba.size < 32) {
            // append zeros in front
            ba = ByteArray(32) + ba
        }
        // get the last 32 bytes as bigint conversion sometimes put extra zeros at front
        return ba.copyOfRange(ba.size - 32, ba.size)
    }


}

