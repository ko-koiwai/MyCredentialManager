package com.example.mycredman

import android.util.Log
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object CredmanUtils {

    fun appInfoToOrigin(info: androidx.credentials.provider.CallingAppInfo): String {

        // https://www.gstatic.com/gpm-passkeys-privileged-apps/apps.json .)
        val privilegedAllowlist = """
            {
  "apps": [
    {
      "type": "android",
      "info": {
        "package_name": "com.android.chrome",
        "signatures": [
          {
            "build": "release",
            "cert_fingerprint_sha256": "F0:FD:6C:5B:41:0F:25:CB:25:C3:B5:33:46:C8:97:2F:AE:30:F8:EE:74:11:DF:91:04:80:AD:6B:2D:60:DB:83"
          },
          {
            "build": "userdebug",
            "cert_fingerprint_sha256": "19:75:B2:F1:71:77:BC:89:A5:DF:F3:1F:9E:64:A6:CA:E2:81:A5:3D:C1:D1:D5:9B:1D:14:7F:E1:C8:2A:FA:00"
          }
        ]
      }
    },
    {
      "type": "android",
      "info": {
        "package_name": "com.chrome.beta",
        "signatures": [
          {
            "build": "release",
            "cert_fingerprint_sha256": "DA:63:3D:34:B6:9E:63:AE:21:03:B4:9D:53:CE:05:2F:C5:F7:F3:C5:3A:AB:94:FD:C2:A2:08:BD:FD:14:24:9C"
          },
          {
            "build": "release",
            "cert_fingerprint_sha256": "3D:7A:12:23:01:9A:A3:9D:9E:A0:E3:43:6A:B7:C0:89:6B:FB:4F:B6:79:F4:DE:5F:E7:C2:3F:32:6C:8F:99:4A"
          }
        ]
      }
    },
    {
      "type": "android",
      "info": {
        "package_name": "com.chrome.dev",
        "signatures": [
          {
            "build": "release",
            "cert_fingerprint_sha256": "90:44:EE:5F:EE:4B:BC:5E:21:DD:44:66:54:31:C4:EB:1F:1F:71:A3:27:16:A0:BC:92:7B:CB:B3:92:33:CA:BF"
          },
          {
            "build": "release",
            "cert_fingerprint_sha256": "3D:7A:12:23:01:9A:A3:9D:9E:A0:E3:43:6A:B7:C0:89:6B:FB:4F:B6:79:F4:DE:5F:E7:C2:3F:32:6C:8F:99:4A"
          }
        ]
      }
    },
    {
      "type": "android",
      "info": {
        "package_name": "com.chrome.canary",
        "signatures": [
          {
            "build": "release",
            "cert_fingerprint_sha256": "20:19:DF:A1:FB:23:EF:BF:70:C5:BC:D1:44:3C:5B:EA:B0:4F:3F:2F:F4:36:6E:9A:C1:E3:45:76:39:A2:4C:FC"
          }
        ]
      }
    }]}
        """.trimIndent()
        val cert = info.signingInfo.apkContentsSigners[0].toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val certHash = md.digest(cert)
        Log.d("MyCredMan","!!!+++ apkhash +++!!!: ${b64Encode(certHash)}"  )

        // This is the format for origin
        var origin: String
        try{
            origin = info.getOrigin(privilegedAllowlist)!! // go to the catch clause when null
        }catch(e:Exception ){
            Log.e("MyCredMan",e.toString()  )
            origin="android:apk-key-hash:${b64Encode(certHash)}"
        }
        Log.d("MyCredMan","!!!+++ origin +++!!!: ${origin}"  )

        return origin
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun b64Encode(data:ByteArray):String{
        // replace with import androidx.credentials.webauthn.WebAuthnUtils in future
        return Base64.UrlSafe.encode(data).replace("=","")
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun b64Decode(data:String?):ByteArray?{
        // replace with import androidx.credentials.webauthn.WebAuthnUtils in future
        if(data ==null || data.isEmpty()) return null
        return Base64.UrlSafe.decode(data)
    }

    fun validateRpId(info: androidx.credentials.provider.CallingAppInfo, rpid:String): String{
        var origin = appInfoToOrigin(info)
        val rpIdForRexEx = rpid.replace(".","""\.""")
        if (Regex("""^https://([A-Za-z0-9\-.]*\.)?"""+rpIdForRexEx+"""/.?""").matches(origin)){
            origin = rpid
        }
        return origin
    }
}