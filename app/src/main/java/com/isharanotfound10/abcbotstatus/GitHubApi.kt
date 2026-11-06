package com.isharanotfound10.abcbotstatus

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val OWNER = "isharanotfound10-lab"
private const val REPO = "abc"
private const val BASE = "https://api.github.com"

data class RunInfo(val id: Long, val number: Int, val name: String, val status: String, val conclusion: String?, val event: String, val branch: String, val sha: String, val createdAt: String, val updatedAt: String, val url: String)
data class JobInfo(val name: String, val status: String, val conclusion: String?, val duration: String, val steps: List<Pair<String, String>>)
data class RepoInfo(val fullName: String, val defaultBranch: String, val visibility: String, val stars: Int, val forks: Int)
data class ArtifactInfo(val name: String, val sizeMb: String, val expired: Boolean, val runNumber: Int)
data class DashboardData(val latest: RunInfo?, val jobs: List<JobInfo>, val history: List<RunInfo>, val artifacts: List<ArtifactInfo>, val repo: RepoInfo?)

class GitHubApi(private val token: String) {
    private suspend fun get(path: String): JSONObject = request(path, "GET")
    private suspend fun request(path: String, method: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("GitHub API $code: ${JSONObject(message).optString("message", message).take(220)}")
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally { connection.disconnect() }
    }

    suspend fun loadDashboard(): DashboardData {
        val root = "/repos/$OWNER/$REPO"
        val repoJson = get(root)
        val runsJson = get("$root/actions/runs?per_page=15")
        val runsArray = runsJson.optJSONArray("workflow_runs") ?: JSONArray()
        val history = (0 until runsArray.length()).map { parseRun(runsArray.getJSONObject(it)) }
        val latest = history.firstOrNull()
        val jobs = if (latest != null) {
            val jobArray = get("$root/actions/runs/${latest.id}/jobs?per_page=100").optJSONArray("jobs") ?: JSONArray()
            (0 until jobArray.length()).map { parseJob(jobArray.getJSONObject(it)) }
        } else emptyList()
        val artifactsArray = get("$root/actions/artifacts?per_page=30").optJSONArray("artifacts") ?: JSONArray()
        val artifacts = (0 until artifactsArray.length()).map { i ->
            val a = artifactsArray.getJSONObject(i)
            ArtifactInfo(a.optString("name"), "%.1f".format(a.optLong("size_in_bytes") / 1048576.0), a.optBoolean("expired"), a.optJSONObject("workflow_run")?.optInt("run_number") ?: 0)
        }
        val repo = RepoInfo(repoJson.optString("full_name"), repoJson.optString("default_branch"), repoJson.optString("visibility"), repoJson.optInt("stargazers_count"), repoJson.optInt("forks_count"))
        return DashboardData(latest, jobs, history, artifacts, repo)
    }

    suspend fun rerunAll(runId: Long) {
        request("/repos/$OWNER/$REPO/actions/runs/$runId/rerun", "POST", JSONObject())
    }

    private fun parseRun(o: JSONObject) = RunInfo(
        o.optLong("id"), o.optInt("run_number"), o.optString("name"),
        o.optString("status"), o.optString("conclusion").takeIf { it.isNotBlank() && it != "null" },
        o.optString("event"), o.optString("head_branch"), o.optString("head_sha"),
        o.optString("created_at"), o.optString("updated_at"), o.optString("html_url")
    )

    private fun parseJob(o: JSONObject): JobInfo {
        val stepsArray = o.optJSONArray("steps") ?: JSONArray()
        val steps = (0 until stepsArray.length()).map { i ->
            val step = stepsArray.getJSONObject(i)
            step.optString("name") to (step.optString("conclusion").ifBlank { step.optString("status") })
        }
        val start = o.optString("started_at")
        val end = o.optString("completed_at")
        val duration = if (start.isNotBlank() && end.isNotBlank()) try {
            val seconds = (java.time.Instant.parse(end).toEpochMilli() - java.time.Instant.parse(start).toEpochMilli()) / 1000
            "${seconds / 60}m ${seconds % 60}s"
        } catch (_: Exception) { "—" } else "In progress"
        return JobInfo(o.optString("name"), o.optString("status"), o.optString("conclusion").takeIf { it.isNotBlank() && it != "null" }, duration, steps)
    }
}

class SecureTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_github", Context.MODE_PRIVATE)
    private val alias = "abc_bot_status_aes_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    fun write(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        prefs.edit().putString("token", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun read(): String = try {
        val packed = Base64.decode(prefs.getString("token", null), Base64.NO_WRAP)
        val iv = packed.copyOfRange(0, 12)
        val encrypted = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    } catch (_: Exception) { "" }
}
