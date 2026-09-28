package com.example.network

import android.util.Log
import com.example.auth.AccountStatus
import com.example.auth.AuthMethod
import com.example.auth.AuthResult
import com.example.auth.UserProfile
import com.example.model.RoleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android client network bridge to the ECOBRIDGES NestJS Backend Supabase Auth module.
 */
object NestJsAuthClient {
    private const val TAG = "NestJsAuthClient"
    private var nestJsBaseUrl: String = "http://10.0.2.2:3000"

    fun setBaseUrl(url: String) {
        nestJsBaseUrl = url.removeSuffix("/")
    }

    /**
     * Registered-role truth from the backend (service-role read of `profiles`).
     * Returns the role slug (`informal_collector` / `formal_recycler` /
     * `government_admin`) or null when the identifier is unknown or the
     * backend is unreachable. Tries the USB reverse tunnel first, then the
     * configured base URL, then the emulator loopback.
     */
    suspend fun lookupRegisteredRole(phone: String): String? = withContext(Dispatchers.IO) {
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return@withContext null
        val hosts = listOf("http://localhost:3000", nestJsBaseUrl, "http://10.0.2.2:3000").distinct()
        for (host in hosts) {
            try {
                val url = URL("$host/api/auth/role-lookup?identifier=$digits")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 2500
                    readTimeout = 2500
                }
                if (conn.responseCode in 200..299) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use(BufferedReader::readText))
                    val slug = json.optString("registeredRole", "").ifBlank { null }
                    Log.d(TAG, "Role lookup for $digits -> $slug via $host")
                    return@withContext slug
                }
            } catch (e: Exception) {
                Log.d(TAG, "Role lookup via $host unavailable: ${e.message}")
            }
        }
        return@withContext null
    }

    suspend fun requestOtp(phone: String, role: RoleType): Result<String>? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$nestJsBaseUrl/api/auth/otp/send")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
            }

            val body = JSONObject().apply {
                put("phoneNumber", phone.filter { it.isDigit() }.takeLast(10))
                put("role", role.name)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            if (conn.responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(resp)
                return@withContext Result.success(json.optString("message", "OTP Sent"))
            }
        } catch (e: Exception) {
            Log.d(TAG, "NestJS backend OTP request unavailable: ${e.message}")
        }
        return@withContext null
    }

    suspend fun verifyOtp(phone: String, otp: String, role: RoleType): AuthResult? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$nestJsBaseUrl/api/auth/otp/verify")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
            }

            val body = JSONObject().apply {
                put("phoneNumber", phone.filter { it.isDigit() }.takeLast(10))
                put("otp", otp)
                put("role", role.name)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            if (conn.responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(resp)
                val userObj = json.getJSONObject("user")
                val permissionsArray = userObj.optJSONArray("permissions")
                val permissionsList = mutableListOf<String>()
                if (permissionsArray != null) {
                    for (i in 0 until permissionsArray.length()) {
                        permissionsList.add(permissionsArray.getString(i))
                    }
                }

                val profile = UserProfile(
                    userId = userObj.getString("id"),
                    displayName = userObj.optString("displayName", "Authorized User"),
                    email = if (userObj.has("email") && !userObj.isNull("email")) userObj.getString("email") else null,
                    phoneNumber = if (userObj.has("phoneNumber") && !userObj.isNull("phoneNumber")) userObj.getString("phoneNumber") else null,
                    role = role,
                    accountStatus = AccountStatus.ACTIVE,
                    permissions = permissionsList,
                    statutoryIdentifier = userObj.optString("statutoryIdentifier", "AUTH-ID"),
                    entityName = "Authorized Aggregator Facility",
                    sessionToken = json.optString("accessToken", ""),
                    authMethod = AuthMethod.MOBILE_OTP
                )
                return@withContext AuthResult(isSuccess = true, userProfile = profile)
            }
        } catch (e: Exception) {
            Log.d(TAG, "NestJS backend verify OTP unavailable: ${e.message}")
        }
        return@withContext null
    }
}
