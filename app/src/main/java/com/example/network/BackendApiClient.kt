package com.example.network

import android.util.Log
import com.example.data.OfferInput
import com.example.data.ReferencePriceInput
import com.example.model.MaterialCategory
import com.example.model.PriceRecord
import com.example.model.RecyclerOffer
import com.example.model.RoleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class BackendDemoUser(
    val id: String,
    val email: String?,
    val phoneNumber: String?,
    val displayName: String,
    val role: RoleType,
    val statutoryIdentifier: String,
    val entityName: String,
    val permissions: List<String>,
    val isDemoAccount: Boolean = true,
    val facilityId: String? = null
)

data class BackendDemoLoginResult(
    val success: Boolean,
    val accessToken: String?,
    val user: BackendDemoUser?,
    val errorMessage: String? = null
)

sealed class BackendOfferResult {
    data class Success(val offer: RecyclerOffer) : BackendOfferResult()
    data class Conflict(val message: String, val serverVersion: Long?) : BackendOfferResult()
    data class Rejected(val message: String) : BackendOfferResult()
    data class Failed(val message: String) : BackendOfferResult()
}

sealed class BackendPriceResult {
    data class Success(val price: PriceRecord) : BackendPriceResult()
    data class Conflict(val message: String, val serverVersion: Long?) : BackendPriceResult()
    data class Rejected(val message: String) : BackendPriceResult()
    data class Failed(val message: String) : BackendPriceResult()
}

/**
 * Communicates with the ECOBRIDGES NestJS backend for demo authentication,
 * cloud offer persistence, and price management.
 */
object BackendApiClient {
    private const val TAG = "BackendApiClient"

    // Default to Android Emulator loopback alias (10.0.2.2). Overridable at runtime.
    private var nestJsBaseUrl: String = "http://10.0.2.2:3000"

    private var tokenProvider: (() -> String?)? = null

    fun configureEndpoint(baseUrl: String) {
        nestJsBaseUrl = baseUrl.removeSuffix("/")
        Log.i(TAG, "Configured NestJS Base URL: $nestJsBaseUrl")
    }

    fun configureTokenProvider(provider: (() -> String?)?) {
        tokenProvider = provider
    }

    private fun applyAuth(conn: HttpURLConnection, explicitToken: String? = null) {
        val token = explicitToken ?: tokenProvider?.invoke()?.takeIf { it.isNotBlank() } ?: return
        conn.setRequestProperty("Authorization", "Bearer $token")
    }

    /**
     * Executes backend-mediated demo login.
     * Gated by server configuration; validates OTP 123456 and returns a signed JWT.
     */
    suspend fun demoLogin(
        phoneNumber: String,
        otp: String,
        role: RoleType?
    ): BackendDemoLoginResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$nestJsBaseUrl/api/auth/demo-login")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
            }

            val body = JSONObject().apply {
                put("phoneNumber", phoneNumber)
                put("otp", otp)
                role?.let { put("role", it.name) }
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                val token = json.getString("accessToken")
                val userObj = json.getJSONObject("user")

                val roleParsed = try {
                    RoleType.valueOf(userObj.getString("role"))
                } catch (e: Exception) {
                    RoleType.INFORMAL_COLLECTOR
                }

                val perms = mutableListOf<String>()
                val permsArr = userObj.optJSONArray("permissions")
                if (permsArr != null) {
                    for (i in 0 until permsArr.length()) {
                        perms.add(permsArr.getString(i))
                    }
                }

                val user = BackendDemoUser(
                    id = userObj.getString("id"),
                    email = userObj.optString("email").takeIf { it.isNotBlank() },
                    phoneNumber = userObj.optString("phoneNumber").takeIf { it.isNotBlank() },
                    displayName = userObj.optString("displayName", "Demo User"),
                    role = roleParsed,
                    statutoryIdentifier = userObj.optString("statutoryIdentifier", ""),
                    entityName = userObj.optString("entityName", ""),
                    permissions = perms,
                    isDemoAccount = userObj.optBoolean("isDemoAccount", true),
                    facilityId = userObj.optString("facilityId").takeIf { it.isNotBlank() }
                )
                return@withContext BackendDemoLoginResult(
                    success = true,
                    accessToken = token,
                    user = user
                )
            } else {
                val errorStream = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                val errorMsg = try {
                    val errJson = errorStream?.let { JSONObject(it) }
                    errJson?.optString("message") ?: "HTTP $code"
                } catch (e: Exception) {
                    "HTTP $code: ${errorStream ?: "Unknown error"}"
                }
                Log.w(TAG, "Demo login failed ($code): $errorMsg")
                return@withContext BackendDemoLoginResult(
                    success = false,
                    accessToken = null,
                    user = null,
                    errorMessage = errorMsg
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Demo login network error: ${e.message}")
            return@withContext BackendDemoLoginResult(
                success = false,
                accessToken = null,
                user = null,
                errorMessage = e.message ?: "Could not connect to backend server"
            )
        }
    }

    /**
     * Persists a recycler offer directly to Supabase via the NestJS backend.
     */
    suspend fun saveRecyclerOffer(
        input: OfferInput,
        token: String? = null
    ): BackendOfferResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$nestJsBaseUrl/api/prices/offers")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                applyAuth(this, token)
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
            }

            val body = JSONObject().apply {
                input.offerId.takeIf { it.isNotBlank() }?.let { put("offerId", it) }
                put("recyclerId", input.recyclerId)
                input.recyclerName.takeIf { it.isNotBlank() }?.let { put("recyclerName", it) }
                put("category", input.category.name)
                put("materialName", input.materialName)
                put("grade", input.grade)
                put("pricePerKg", input.pricePerKg)
                put("currency", input.currency)
                input.minQuantityKg?.let { put("minQuantityKg", it) }
                input.maxQuantityKg?.let { put("maxQuantityKg", it) }
                put("condition", input.condition)
                put("status", input.status)
                put("notes", input.notes)
                if (input.baseVersion > 0L) {
                    put("baseVersion", input.baseVersion)
                }
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)

                val category = try {
                    MaterialCategory.valueOf(json.getString("category"))
                } catch (e: Exception) {
                    input.category
                }

                val saved = RecyclerOffer(
                    offerId = json.getString("offerId"),
                    recyclerId = json.getString("recyclerId"),
                    recyclerName = json.optString("recyclerName", input.recyclerName),
                    category = category,
                    materialName = json.getString("materialName"),
                    grade = json.optString("grade", input.grade),
                    pricePerKg = json.getDouble("pricePerKg"),
                    currency = json.optString("currency", "INR"),
                    minQuantityKg = if (json.isNull("minQuantityKg")) null else json.optDouble("minQuantityKg"),
                    maxQuantityKg = if (json.isNull("maxQuantityKg")) null else json.optDouble("maxQuantityKg"),
                    condition = json.optString("condition", input.condition),
                    status = json.optString("status", input.status),
                    notes = json.optString("notes", input.notes),
                    effectiveFrom = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    updatedBy = json.optString("updatedBy", ""),
                    version = json.optLong("version", input.baseVersion + 1L)
                )
                return@withContext BackendOfferResult.Success(saved)
            } else if (code == 409) {
                val errText = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                return@withContext BackendOfferResult.Conflict(
                    message = errText ?: "Conflict on server. Please reload.",
                    serverVersion = null
                )
            } else if (code == 403) {
                val errText = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                return@withContext BackendOfferResult.Rejected(
                    errText ?: "Permission denied for this facility."
                )
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                return@withContext BackendOfferResult.Failed(
                    errText ?: "HTTP $code server error"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Save offer network error: ${e.message}")
            return@withContext BackendOfferResult.Failed(e.message ?: "Network failure")
        }
    }

    /**
     * Retrieves recycler offers from the central server.
     */
    suspend fun fetchRecyclerOffers(
        token: String? = null,
        category: String? = null,
        recyclerId: String? = null
    ): List<RecyclerOffer> = withContext(Dispatchers.IO) {
        try {
            val params = mutableListOf<String>()
            category?.let { params.add("category=$it") }
            recyclerId?.let { params.add("recyclerId=$it") }
            val queryStr = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""

            val url = URL("$nestJsBaseUrl/api/prices/offers$queryStr")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                applyAuth(this, token)
                connectTimeout = 4000
                readTimeout = 4000
            }

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val arr = JSONArray(responseText)
                val list = mutableListOf<RecyclerOffer>()
                for (i in 0 until arr.length()) {
                    val json = arr.getJSONObject(i)
                    val cat = try {
                        MaterialCategory.valueOf(json.getString("category"))
                    } catch (e: Exception) {
                        MaterialCategory.PCB_BOARDS
                    }
                    list.add(
                        RecyclerOffer(
                            offerId = json.getString("offerId"),
                            recyclerId = json.getString("recyclerId"),
                            recyclerName = json.optString("recyclerName", ""),
                            category = cat,
                            materialName = json.getString("materialName"),
                            grade = json.optString("grade", ""),
                            pricePerKg = json.getDouble("pricePerKg"),
                            currency = json.optString("currency", "INR"),
                            minQuantityKg = if (json.isNull("minQuantityKg")) null else json.optDouble("minQuantityKg"),
                            maxQuantityKg = if (json.isNull("maxQuantityKg")) null else json.optDouble("maxQuantityKg"),
                            condition = json.optString("condition", ""),
                            status = json.optString("status", "ACTIVE"),
                            notes = json.optString("notes", ""),
                            effectiveFrom = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            updatedBy = json.optString("updatedBy", ""),
                            version = json.optLong("version", 1L)
                        )
                    )
                }
                return@withContext list
            }
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Fetch offers error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Persists an admin-managed reference price to Supabase via the NestJS backend.
     */
    suspend fun saveReferencePrice(
        input: ReferencePriceInput,
        token: String? = null
    ): BackendPriceResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$nestJsBaseUrl/api/prices/reference")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                applyAuth(this, token)
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
            }

            val body = JSONObject().apply {
                input.priceId?.takeIf { it.isNotBlank() }?.let { put("priceId", it) }
                put("category", input.category.name)
                put("materialName", input.materialName)
                put("grade", input.grade)
                put("pricePerKg", input.pricePerKg)
                put("marketMin", input.marketMin)
                put("marketMax", input.marketMax)
                put("currency", input.currency)
                input.effectiveDate?.let { put("effectiveDate", it) }
                put("isActive", input.isActive)
                if (input.baseVersion > 0L) {
                    put("baseVersion", input.baseVersion)
                }
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)

                val cat = try {
                    MaterialCategory.valueOf(json.getString("category"))
                } catch (e: Exception) {
                    input.category
                }

                val saved = PriceRecord(
                    priceId = json.getString("priceId"),
                    category = cat,
                    subCategory = json.getString("materialName"),
                    location = "National Reference (CPCB)",
                    prevailingBuyRate = json.getDouble("pricePerKg"),
                    marketMin = json.getDouble("marketMin"),
                    marketMax = json.getDouble("marketMax"),
                    trend = "FLAT",
                    trendPercentage = 0.0,
                    unit = "₹/kg",
                    dateUpdated = json.optString("effectiveDate", ""),
                    keyMetals = emptyList(),
                    grade = json.optString("grade", input.grade),
                    currency = json.optString("currency", "INR"),
                    isActive = json.optBoolean("isActive", true),
                    effectiveDate = json.optString("effectiveDate", input.effectiveDate ?: ""),
                    updatedAt = System.currentTimeMillis(),
                    updatedBy = json.optString("updatedByLabel", "Government Admin"),
                    version = json.optLong("version", input.baseVersion + 1L)
                )
                return@withContext BackendPriceResult.Success(saved)
            } else if (code == 409) {
                val errText = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                return@withContext BackendPriceResult.Conflict(
                    message = errText ?: "Conflict on server. Please reload.",
                    serverVersion = null
                )
            } else if (code == 403) {
                val errText = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                return@withContext BackendPriceResult.Rejected(
                    errText ?: "Only government administrators may modify reference prices."
                )
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                return@withContext BackendPriceResult.Failed(
                    errText ?: "HTTP $code server error"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Save reference price error: ${e.message}")
            return@withContext BackendPriceResult.Failed(e.message ?: "Network failure")
        }
    }

    /**
     * Retrieves reference prices from the central server.
     */
    suspend fun fetchReferencePrices(
        token: String? = null,
        category: String? = null
    ): List<PriceRecord> = withContext(Dispatchers.IO) {
        try {
            val queryStr = category?.let { "?category=$it" } ?: ""
            val url = URL("$nestJsBaseUrl/api/prices/reference$queryStr")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                applyAuth(this, token)
                connectTimeout = 4000
                readTimeout = 4000
            }

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val arr = JSONArray(responseText)
                val list = mutableListOf<PriceRecord>()
                for (i in 0 until arr.length()) {
                    val json = arr.getJSONObject(i)
                    val cat = try {
                        MaterialCategory.valueOf(json.getString("category"))
                    } catch (e: Exception) {
                        MaterialCategory.PCB_BOARDS
                    }
                    list.add(
                        PriceRecord(
                            priceId = json.getString("priceId"),
                            category = cat,
                            subCategory = json.getString("materialName"),
                            location = "National Reference (CPCB)",
                            prevailingBuyRate = json.getDouble("pricePerKg"),
                            marketMin = json.getDouble("marketMin"),
                            marketMax = json.getDouble("marketMax"),
                            trend = "FLAT",
                            trendPercentage = 0.0,
                            unit = "₹/kg",
                            dateUpdated = json.optString("effectiveDate", ""),
                            keyMetals = emptyList(),
                            grade = json.optString("grade", ""),
                            currency = json.optString("currency", "INR"),
                            isActive = json.optBoolean("isActive", true),
                            effectiveDate = json.optString("effectiveDate", ""),
                            updatedAt = System.currentTimeMillis(),
                            updatedBy = json.optString("updatedByLabel", ""),
                            version = json.optLong("version", 1L)
                        )
                    )
                }
                return@withContext list
            }
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Fetch reference prices error: ${e.message}")
            emptyList()
        }
    }
}
