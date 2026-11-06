package com.isharanotfound10.abcbotstatus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Bg = Color(0xFF080711)
private val Panel = Color(0xFF171426)
private val Panel2 = Color(0xFF211B35)
private val Purple = Color(0xFFB78CFF)
private val Muted = Color(0xFFAAA3BD)
private val Green = Color(0xFF55D99A)
private val Red = Color(0xFFFF7185)
private val Amber = Color(0xFFFFC56B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SecureTokenStore(this)
        val repositoryStore = RepositoryStore(this)
        setContent { BotStatusApp(store, repositoryStore) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotStatusApp(store: SecureTokenStore, repositoryStore: RepositoryStore) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(store.read()) }
    var repositoryUrl by remember { mutableStateOf(repositoryStore.read()) }
    val repository = remember(repositoryUrl) { parseGitHubRepository(repositoryUrl) }
    val repositoryLabel = repository?.let { "${it.first}/${it.second}" } ?: "No repository selected"
    var data by remember { mutableStateOf<DashboardData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTokenDialog by remember { mutableStateOf(token.isBlank() || repository == null) }
    var tokenDraft by remember { mutableStateOf("") }
    var repositoryDraft by remember { mutableStateOf(repositoryUrl) }
    var selectedTab by remember { mutableStateOf("Overview") }
    var confirmRerun by remember { mutableStateOf(false) }
    var rerunning by remember { mutableStateOf(false) }
    var lastRefresh by remember { mutableStateOf("") }

    suspend fun refresh() {
        val selectedRepository = parseGitHubRepository(repositoryUrl) ?: return
        if (token.isBlank()) return
        loading = true
        try {
            data = GitHubApi(token, selectedRepository.first, selectedRepository.second).loadDashboard()
            error = null
            lastRefresh = "Updated " + nowText()
        } catch (e: Exception) {
            error = e.message ?: "Unable to contact GitHub"
        } finally { loading = false }
    }

    LaunchedEffect(token, repositoryUrl) {
        if (token.isNotBlank() && repository != null) {
            while (true) {
                refresh()
                delay(20_000)
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Purple, secondary = Color(0xFF62D6D0),
        background = Bg, surface = Panel, onBackground = Color.White,
        onSurface = Color.White, error = Red
    )) {
        Scaffold(containerColor = Bg, topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ASITHA-MD BOT", fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                        Text(repositoryLabel, color = Muted, fontSize = 11.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { refresh() } }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Purple)
                    }
                    IconButton(onClick = { tokenDraft = ""; repositoryDraft = repositoryUrl; showTokenDialog = true }) {
                        Icon(Icons.Default.Settings, "Settings", tint = Purple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }, bottomBar = {
            NavigationBar(containerColor = Panel) {
                listOf("Overview", "Jobs", "History", "Artifacts").forEach { tab ->
                    val icon = when (tab) {
                        "Jobs" -> Icons.Default.Terminal
                        "History" -> Icons.Default.History
                        "Artifacts" -> Icons.Default.Inventory2
                        else -> Icons.Default.Dashboard
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icon, contentDescription = tab) },
                        label = { Text(tab, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Purple, selectedTextColor = Purple,
                            indicatorColor = Panel2, unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).background(Bg)) {
                if (error != null) {
                    Surface(color = Color(0xFF3A1826), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp)) {
                        Text(error!!, color = Color(0xFFFFB5C0), modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                    }
                }
                when (selectedTab) {
                    "Jobs" -> JobsScreen(data?.jobs.orEmpty())
                    "History" -> HistoryScreen(data?.history.orEmpty())
                    "Artifacts" -> ArtifactsScreen(data?.artifacts.orEmpty())
                    else -> OverviewScreen(data, loading, lastRefresh, rerunning, repositoryLabel, onRerun = { if (!rerunning) confirmRerun = true })
                }
            }
        }
    }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { if (token.isNotBlank()) showTokenDialog = false },
            title = { Text("Connect Asitha-MD BOT") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("1. REPOSITORY LINK", color = Purple, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text("Paste the link of the GitHub repository whose Actions you want to monitor. It must look exactly like this:", fontSize = 13.sp)
                    Surface(color = Panel2, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("https://github.com/USERNAME/REPOSITORY", color = Color(0xFFBFE8FF), fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                    }
                    Text("Replace USERNAME with the GitHub account/owner and REPOSITORY with the repository name. Example: https://github.com/octocat/hello-world", color = Muted, fontSize = 11.sp)
                    OutlinedTextField(
                        value = repositoryDraft, onValueChange = { repositoryDraft = it },
                        label = { Text("GitHub repository URL") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (repositoryDraft.isNotBlank() && parseGitHubRepository(repositoryDraft) == null) {
                        Text("Invalid link. Use https://github.com/USERNAME/REPOSITORY", color = Red, fontSize = 11.sp)
                    }
                    HorizontalDivider(color = Color(0xFF594A78).copy(alpha = 0.5f))
                    Text("2. CREATE A FINE-GRAINED TOKEN", color = Purple, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text("1. Open GitHub and sign in to your account.\n2. Go to Settings → Developer settings → Personal access tokens → Fine-grained tokens.\n3. Tap Generate new token.\n4. Give it a name, choose an expiry, and select your account as the resource owner.\n5. Under Repository access, choose Only select repositories, then select the repository entered above.\n6. Under Repository permissions, set the permissions below, then generate and copy the token.", fontSize = 12.sp, lineHeight = 18.sp)
                    Text("Open GitHub token settings", color = Color(0xFFBFE8FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("https://github.com/settings/personal-access-tokens/new", color = Muted, fontSize = 11.sp)
                    Surface(color = Panel2, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("REQUIRED PERMISSIONS", color = Purple, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("• Actions — Read and write", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("• Metadata — Read-only (GitHub requires this)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Do not grant access to all repositories or any extra permissions. Actions write access is needed for the Rerun all jobs button.", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Text("3. PASTE YOUR TOKEN", color = Purple, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    OutlinedTextField(
                        value = tokenDraft, onValueChange = { tokenDraft = it },
                        label = { Text("Fine-grained personal access token") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Your token is stored encrypted on this device. Never send it to anyone or include it in screenshots.", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val selectedRepository = parseGitHubRepository(repositoryDraft)
                    if (selectedRepository != null && tokenDraft.trim().isNotBlank()) {
                        repositoryStore.write("https://github.com/${selectedRepository.first}/${selectedRepository.second}")
                        repositoryUrl = "https://github.com/${selectedRepository.first}/${selectedRepository.second}"
                        store.write(tokenDraft.trim())
                        token = tokenDraft.trim()
                        showTokenDialog = false
                    }
                }, enabled = parseGitHubRepository(repositoryDraft) != null && tokenDraft.trim().isNotBlank()) { Text("Save securely") }
            },
            dismissButton = {
                if (token.isNotBlank()) TextButton(onClick = { showTokenDialog = false }) { Text("Cancel") }
            },
            containerColor = Panel
        )
    }

    if (confirmRerun) {
        AlertDialog(
            onDismissRequest = { confirmRerun = false },
            title = { Text("Rerun all jobs?") },
            text = { Text("This will restart every job in the latest completed workflow run in $repositoryLabel.") },
            confirmButton = {
                Button(enabled = !rerunning, onClick = {
                    confirmRerun = false
                    scope.launch {
                        rerunning = true
                        try {
                            val runId = data?.latest?.id ?: throw IllegalStateException("No workflow run found")
                            val selectedRepository = parseGitHubRepository(repositoryUrl) ?: throw IllegalStateException("Set a valid repository URL first")
                            GitHubApi(token, selectedRepository.first, selectedRepository.second).rerunAll(runId)
                            refresh()
                        } catch (e: Exception) { error = e.message ?: "Rerun failed" }
                        finally { rerunning = false }
                    }
                }) { Text(if (rerunning) "Starting…" else "Rerun all jobs") }
            },
            dismissButton = { TextButton(onClick = { confirmRerun = false }) { Text("Cancel") } },
            containerColor = Panel
        )
    }
}

@Composable
private fun OverviewScreen(data: DashboardData?, loading: Boolean, lastRefresh: String, rerunning: Boolean, repositoryLabel: String, onRerun: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("LIVE REPOSITORY", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text(if (loading) "SYNCING…" else "● LIVE", color = if (loading) Amber else Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            GlassCard {
                Text("ASITHA-MD BOT", fontSize = 19.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                val run = data?.latest
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(run?.status, run?.conclusion)
                    Spacer(Modifier.width(9.dp))
                    Text(statusLabel(run?.status, run?.conclusion), color = statusColor(run?.status, run?.conclusion), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.height(18.dp))
                InfoRow("⚙️  WORKFLOW", run?.name ?: "Loading…")
                InfoRow("🔢  RUN", run?.let { "#${it.number}" } ?: "—")
                InfoRow("🚀  TRIGGER", run?.event?.replaceFirstChar { it.uppercase() } ?: "—")
                InfoRow("🌿  BRANCH", run?.branch ?: "—")
                InfoRow("🔑  COMMIT", run?.sha?.take(7) ?: "—")
                InfoRow("🕒  UPDATED", run?.updatedAt?.let { formatTime(it) } ?: "—")
                if (run?.status == "completed") {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRerun, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Color(0xFF160D25))) {
                        Icon(Icons.Default.Replay, null)
                        Spacer(Modifier.width(8.dp))
                        Text("RERUN ALL JOBS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
                if (run == null) Text("Connect GitHub to load the latest workflow run.", color = Muted, fontSize = 12.sp)
            }
        }
        item {
            GlassCard {
                Text("REPOSITORY", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp)
                Text(data?.repo?.fullName ?: repositoryLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                InfoRow("DEFAULT BRANCH", data?.repo?.defaultBranch ?: "—")
                InfoRow("VISIBILITY", data?.repo?.visibility ?: "—")
                InfoRow("STARS / FORKS", data?.repo?.let { "${it.stars} / ${it.forks}" } ?: "—")
            }
        }
        item { Text(lastRefresh, color = Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun JobsScreen(jobs: List<JobInfo>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("LATEST RUN JOBS", color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp) }
        if (jobs.isEmpty()) item { EmptyState("No job information yet") }
        items(jobs) { job ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(job.status, job.conclusion)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(job.name, fontWeight = FontWeight.Bold)
                        Text(statusLabel(job.status, job.conclusion), color = statusColor(job.status, job.conclusion), fontSize = 11.sp)
                    }
                    Text(job.duration, color = Muted, fontSize = 11.sp)
                }
                job.steps.forEach { step ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(step.first, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stepStatusLabel(step.second), color = stepStatusColor(step.second), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(history: List<RunInfo>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("RECENT WORKFLOW RUNS", color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp) }
        if (history.isEmpty()) item { EmptyState("No run history yet") }
        items(history) { run ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(run.status, run.conclusion)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("#${run.number}  ${run.name}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${run.branch} • ${run.event} • ${formatTime(run.createdAt)}", color = Muted, fontSize = 10.sp)
                    }
                    Text(statusLabel(run.status, run.conclusion), color = statusColor(run.status, run.conclusion), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ArtifactsScreen(artifacts: List<ArtifactInfo>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("BUILD ARTIFACTS", color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp) }
        if (artifacts.isEmpty()) item { EmptyState("No artifacts found for recent runs") }
        items(artifacts) { item ->
            GlassCard {
                Text(item.name, fontWeight = FontWeight.Bold)
                Text("Run #${item.runNumber} • ${item.sizeMb} MB", color = Muted, fontSize = 11.sp)
                Text(if (item.expired) "Expired" else "Available on GitHub", color = if (item.expired) Red else Green, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Panel.copy(alpha = 0.94f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color(0xFF594A78).copy(alpha = 0.45f)),
        shadowElevation = 8.dp
    ) { Column(Modifier.fillMaxWidth().padding(18.dp), content = content) }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusDot(status: String?, conclusion: String?) {
    val color = statusColor(status, conclusion)
    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
}

@Composable
private fun EmptyState(text: String) {
    GlassCard { Text(text, color = Muted, fontSize = 13.sp) }
}

private fun statusLabel(status: String?, conclusion: String?): String = when (status) {
    "queued", "requested", "waiting", "pending" -> "Queued / Waiting"
    "in_progress" -> "Running"
    "completed" -> (conclusion ?: "completed").replace('_', ' ').replaceFirstChar { it.uppercase() }
    else -> "Unknown"
}

private fun statusColor(status: String?, conclusion: String?): Color = when {
    status == "in_progress" -> Green
    status in listOf("queued", "requested", "waiting", "pending") -> Amber
    status == "completed" && conclusion == "success" -> Green
    status == "completed" -> Red
    else -> Muted
}

internal fun stepStatusLabel(state: String): String = when (state.lowercase()) {
    "in_progress" -> "Running"
    "queued", "pending", "waiting", "requested" -> "Queued"
    "completed" -> "Completed"
    "success" -> "Success"
    "failure" -> "Failed"
    "cancelled" -> "Cancelled"
    "skipped" -> "Skipped"
    "timed_out" -> "Timed out"
    "action_required" -> "Action required"
    "neutral" -> "Neutral"
    "stale" -> "Stale"
    "unknown", "null", "" -> "Waiting"
    else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun stepStatusColor(state: String): Color = when (state.lowercase()) {
    "in_progress", "success", "completed" -> Green
    "queued", "pending", "waiting", "requested" -> Amber
    "failure", "timed_out", "action_required" -> Red
    else -> Muted
}

private fun nowText(): String = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now())
private fun formatTime(value: String): String = try {
    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
} catch (_: Exception) { value }


 
private fun parseGitHubRepository(input: String): Pair<String, String>? {
    val cleaned = input.trim().removeSuffix(".git").trimEnd('/')
    val match = Regex("^(?:https?://)?(?:www\\.)?github\\.com/([^/]+)/([^/?#]+)(?:[/?#].*)?$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        ?: return null
    val owner = match.groupValues[1]
    val repository = match.groupValues[2]
    if (owner.isBlank() || repository.isBlank() || owner == "." || repository == ".") return null
    return owner to repository
}

private class RepositoryStore(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("repository_settings", android.content.Context.MODE_PRIVATE)
    fun read(): String = prefs.getString("repository_url", "") ?: ""
    fun write(value: String) { prefs.edit().putString("repository_url", value).apply() }
}
