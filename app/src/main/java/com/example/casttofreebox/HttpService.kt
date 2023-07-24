package com.example.casttofreebox

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


object HttpService {
    private val client: HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            expectSuccess = true
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 5000
        }
    }
    var freebox: Freebox? = null
    var appToken: String? = null
    var sessionToken: String? = null
    var track_id: Int? = null
    const val SESSION_TOKEN_HEADER = "X-Fbx-App-Auth"

    suspend fun httpSearchFreebox(): Boolean {
        return try {
            val response = client.get("http://mafreebox.freebox.fr/api_version")
            freebox = response.body()
            Log.d("MainActivity", "Found freebox")
            true
        } catch(e: Exception) {
            Log.d("MainActivity", "Error fetching Freebox server: $e")
            false
        }

    }

    private fun apiCall(api_url: String): String? {
        if(freebox == null) { return null }
        if(!freebox!!.https_available) { return null }
        val f = freebox!!
        return "http://mafreebox.freebox.fr${f.api_base_url}v${f.api_version.substringBefore(".")}/$api_url"
        //return "https://${f.api_domain}:${f.https_port}${f.api_base_url}v${f.api_version.substringBefore(".")}/$api_url"
    }

    suspend fun getAppToken(): Boolean {
        return try {
            val response = client.post(apiCall("login/authorize/")!!) {
                contentType(ContentType.Application.Json)
                setBody(TokenRequest(APP_ID, APP_NAME, APP_VERSION, DEVICE_NAME))
            }
            Log.d("MainActivity", "Response: $response")
            val content: FreeboxResponse = response.body()
            if(!content.success) { Log.d("MainActivity", "Error in request: ${content.error_code}, ${content.msg}") ; return false }
            appToken = content.result.app_token
            track_id = content.result.track_id
            Log.d("MainActivity", "Got app token: $appToken")
            true
        } catch(e: Exception) {
            Log.d("MainActivity", "Error in Token Request: ${e.message}")
            false
        }
    }

    suspend fun appTokenValid(): Int {
        if(track_id == null) { return -1 }
        return try {
            Log.d("MainActivity", "Verifying token validity")
            val response = client.get(apiCall("login/authorize/$track_id")!!)
            val content: FreeboxResponse = response.body()
            if(!content.success) {return -1}
            Log.d("MainActivity", "Status: ${content.result.status}")
            when(content.result.status) {
                "pending" -> 0
                "granted" -> 1
                else -> -1
            }
        } catch(e: Exception) {
            Log.d("MainActivity", "Error verifying token validity: ${e.message}")
            -1
        }
    }

    private fun getPassword(challenge: String): String {
        if (appToken == null) {
            return ""
        }
        val appTokenBytes = appToken!!.toByteArray(StandardCharsets.UTF_8)
        val challengeBytes = challenge.toByteArray(StandardCharsets.UTF_8)
        val secretKey = SecretKeySpec(appTokenBytes, "HmacSHA1")
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(challengeBytes)
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun getSessionToken(challenge: String? = null): Boolean {
        if(appToken == null) { return false }
        return try {
            val newChallenge = if (challenge != null) {
                challenge
            } else {
                val response = client.get(apiCall("login/")!!)
                response.body<FreeboxResponse>().result.challenge
            }
            val response = client.post(apiCall("login/session/")!!) {
                contentType(ContentType.Application.Json)
                setBody(SessionStartRequest(getPassword(newChallenge), APP_ID, APP_VERSION))
            }
            val content: FreeboxResponse = response.body()
            if(!content.success) { Log.d("MainActivity", "Error in request: ${content.error_code}, ${content.msg}") ; return false }
            sessionToken = content.result.session_token
            Log.d("MainActivity", "Got session token !")
            true
        } catch(e: Exception) {
            Log.d("MainActivity", "Error getting refresh token: ${e.message}")
            false
        }
    }

    suspend fun logout(): Boolean {
        if(sessionToken == null) { return true }
        return try {
            val response = client.post(apiCall("login/logout/")!!) {
                headers {
                    append(SESSION_TOKEN_HEADER, sessionToken!!)
                }
            }
            val content: FreeboxResponse = response.body()
            if(content.success) {
                Log.d("MainActivity", "Session Disconnected")
                true
            } else {
                Log.d("MainActivity", "Logout failed: ${content.error_code}, ${content.msg}")
                false
            }
        } catch(e: Exception) {
            Log.d("MainActivity", "Exception during logout: ${e.message}")
            false
        }
    }

    suspend fun getConfigAirmedia(): Boolean {
        if(sessionToken == null) { return false }
        return try {
            val response = client.get(apiCall("airmedia/config/")!!) {
                headers {
                    append(SESSION_TOKEN_HEADER, sessionToken!!)
                }
            }
            val content: FreeboxResponse = response.body()
            Log.d("MainActivity", "Airmedia Config Response: $response")
            Log.d("MainActivity", "Airmedia Config Body: ${response.body<String>()}")
            return content.result.enabled
        } catch(e: Exception) {
            Log.d("MainActivity", "Error fetching Airmedia Config: $e")
            false
        }
    }

    //Pas autorisé à faire ça
    suspend fun setConfigAirmedia(): Boolean {
        if(sessionToken == null) { return false }
        return try {
            val response = client.put(apiCall("airmedia/config/")!!) {
                contentType(ContentType.Application.Json)
                setBody(AirmediaConfig(true, "1111"))
                headers {
                    append(SESSION_TOKEN_HEADER, sessionToken!!)
                }
            }
            val content: FreeboxResponse = response.body()
            Log.d("MainActivity", "Airmedia Config Response: $response")
            Log.d("MainActivity", "Airmedia Config Body: ${response.body<String>()}")
            return content.result.enabled
        } catch(e: Exception) {
            Log.d("MainActivity", "Error setting Airmedia Config: $e")
            false
        }
    }

    suspend fun getRecieversAirmedia(): List<AirmediaReceiver> {
        if(sessionToken == null) { return emptyList() }
        return try {
            val response = client.get(apiCall("airmedia/receivers/")!!) {
                headers {
                    append(SESSION_TOKEN_HEADER, sessionToken!!)
                }
            }
            Log.d("MainActivity", "Airmedia Config Response: $response")
            Log.d("MainActivity", "Airmedia Config Body: ${response.body<String>()}")
            val content: AirmediaReceiverResponse = response.body()
            return content.result
        } catch(e: Exception) {
            Log.d("MainActivity", "Error fetching Airmedia Receivers: $e")
            emptyList()
        }
    }

    suspend fun freeboxPlayerAvailable(): Int {
        val receivers = getRecieversAirmedia()
        for(r in receivers) {
            if(r.name == "Freebox Player") {
                return if(!r.password_protected) {
                    1
                } else {
                    0
                }
            }
        }
        return -1
    }

    suspend fun playVideo(url: String): Boolean {
        if(freeboxPlayerAvailable() != 1) { return false }
        return try {
            val response = client.post(apiCall("airmedia/receivers/Freebox Player")!!) {
                contentType(ContentType.Application.Json)
                setBody(AirmediaReceiverRequest("start", "video", url))
                headers {
                    append(SESSION_TOKEN_HEADER, sessionToken!!)
                }
            }
            Log.d("MainActivity", "Play Video Response: $response")
            val content: FreeboxResponse = response.body()
            return content.success
        } catch(e: Exception) {
            Log.d("MainActivity", "Error Starting Video: $e")
            false
        }
    }

    suspend fun stopVideo(): Boolean {
        if(freeboxPlayerAvailable() != 1) { return false }
        return try {
            val response = client.post(apiCall("airmedia/receivers/Freebox Player")!!) {
                contentType(ContentType.Application.Json)
                setBody(AirmediaReceiverRequest("stop", "video"))
                headers {
                    append(SESSION_TOKEN_HEADER, sessionToken!!)
                }
            }
            Log.d("MainActivity", "Stop Video Response: $response")
            val content: FreeboxResponse = response.body()
            return content.success
        } catch(e: Exception) {
            Log.d("MainActivity", "Error Stopping Video: $e")
            false
        }
    }



    private const val APP_ID = "ctf"
    private const val APP_NAME = "Cast to Freebox"
    private const val APP_VERSION = "1.0"
    private const val DEVICE_NAME = "Smartphone"

    @Serializable
    data class TokenRequest(
        val app_id: String,
        val app_name: String,
        val app_version: String,
        val device_name: String
    )

    @Serializable
    data class SessionStartRequest(
        val password: String,
        val app_id: String,
        val app_version: String,
    )

    @Serializable
    data class FreeboxResponse(
        val success: Boolean = false,
        val result: FreeboxResult = FreeboxResult(),
        val msg: String = "",
        val error_code: String = ""
    )

    @Serializable
    data class FreeboxResult(
        val app_token: String = "",
        val session_token: String = "",
        val track_id: Int = 0,
        val status: String = "",
        val challenge: String = "",
        val enabled: Boolean = false
    )

    @Serializable
    data class AirmediaConfig(
        val enabled: Boolean,
        val password: String
    )

    @Serializable
    data class AirmediaReceiver(
        val name: String,
        val password_protected: Boolean,
        val capabilities: Capabilities
    )

    @Serializable
    data class Capabilities(
        val photo: Boolean = false,
        val audio: Boolean = false,
        val video: Boolean = false,
        val screen: Boolean = false
    )

    @Serializable
    data class AirmediaReceiverResponse(
        val success: Boolean = false,
        val result: List<AirmediaReceiver> = emptyList(),
        val msg: String = "",
        val error_code: String = ""
    )

    @Serializable
    data class AirmediaReceiverRequest(
        val action: String,  //start or stop
        val media_type: String,  //photo or video
        val media: String = "",
        val position: Int = 0,
        val password: String = "",
    )

    @Serializable
    data class Freebox(
        val uid: String,
        val device_name: String,
        val api_version: String,
        val api_base_url: String,
        val device_type: String,
        val api_domain: String,
        val https_available: Boolean,
        val https_port: Int
    )
}