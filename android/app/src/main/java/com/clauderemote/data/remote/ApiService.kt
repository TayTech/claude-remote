package com.clauderemote.data.remote

import com.clauderemote.data.model.HealthResponse
import com.clauderemote.data.model.HistoryMessage
import com.clauderemote.data.model.Project
import com.clauderemote.data.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

class ApiService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var baseUrl: String = ""
    @Volatile
    private var apiKey: String = ""

    fun setBaseUrl(host: String, port: Int, key: String) {
        baseUrl = "http://$host:$port"
        apiKey = key
    }

    suspend fun getHealth(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val health = json.decodeFromString<HealthResponse>(body)
                Result.success(health)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProjects(): Result<List<Project>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/projects")
                .header("X-API-Key", apiKey)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val projects = json.decodeFromString<List<Project>>(body)
                Result.success(projects)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessions(projectId: String): Result<List<Session>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/projects/$projectId/sessions")
                .header("X-API-Key", apiKey)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val sessions = json.decodeFromString<List<Session>>(body)
                Result.success(sessions)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessionHistory(projectId: String, sessionId: String): Result<List<HistoryMessage>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/projects/$projectId/sessions/$sessionId/history")
                .header("X-API-Key", apiKey)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val history = json.decodeFromString<List<HistoryMessage>>(body)
                Result.success(history)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
