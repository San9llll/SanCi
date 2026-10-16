package san.ci.ui.sanci

import top.yukonga.miuix.kmp.basic.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

/**
 * CI 页 —— 查一个 GitHub 仓库最近的构建。
 *
 * ## 为什么只有读，没有写
 *
 * 这一页只是**看一眼上游编译得怎么样**：某个仓库最近几次 Actions 是绿是红。
 * 不做触发、不做重跑 —— 那些要写权限的 token，不该塞在一个看状态的页面里。
 *
 * ## 实现
 *
 * 直接打 GitHub 的公开 API（`/repos/{owner}/{repo}/actions/runs`）。
 * 公开仓库不需要 token；私有仓库会返回 404，界面上会说清楚。
 * **用的是匿名请求**，所以有速率限制（每小时 60 次），够看状态用了。
 */
@Composable
fun SanCiCiScreen(railWidth: Dp = 0.dp) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var repo by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val runs = remember { mutableStateListOf<CiRun>() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = railWidth)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text("CI", fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "看一个仓库最近的构建",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(18.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("仓库", style = MiuixTheme.textStyles.title4)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = repo,
                    onValueChange = { repo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "owner/repo",
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            loading = true
                            error = null
                            runs.clear()
                            scope.launch {
                                val result = fetchRuns(repo.trim())
                                result.first?.let { runs.addAll(it) }
                                error = result.second
                                loading = false
                            }
                        },
                        enabled = repo.contains("/") && !loading,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (loading) "查询中…" else "查询") }

                    TextButton(
                        text = "网页打开",
                        onClick = {
                            if (repo.isNotBlank()) {
                                uriHandler.openUri("https://github.com/${repo.trim()}/actions")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        error?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        it,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (runs.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("最近 ${runs.size} 次", style = MiuixTheme.textStyles.title4)
                    Spacer(Modifier.height(10.dp))
                    runs.forEach { run ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 状态点：绿=成功、红=失败、灰=进行中/取消
                            val tint = when (run.conclusion) {
                                "success" -> MiuixTheme.colorScheme.primary
                                "failure" -> MiuixTheme.colorScheme.error
                                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                            }
                            Text("●", fontSize = 12.sp, color = tint)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(run.name, style = MiuixTheme.textStyles.title4)
                                Text(
                                    run.time + " · " + run.sha,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一条构建记录。 */
private data class CiRun(val name: String, val conclusion: String, val sha: String, val time: String)

/**
 * 拉最近的构建。
 *
 * 返回 (记录, 错误信息) —— 两个都可能同时有东西，界面上分别显示。
 */
private suspend fun fetchRuns(repo: String): Pair<List<CiRun>?, String?> =
    withContext(Dispatchers.IO) {
        if (!repo.contains("/")) return@withContext null to "格式是 owner/repo"

        runCatching {
            val url = URL("https://api.github.com/repos/$repo/actions/runs?per_page=8")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SanCi")
            }
            val code = conn.responseCode
            if (code == 404) {
                conn.disconnect()
                return@runCatching null to "找不到这个仓库（拼错了，或者是私有的 —— 私有仓库要 token，这一页不做）"
            }
            if (code == 403) {
                conn.disconnect()
                return@runCatching null to "GitHub 限流了（匿名请求每小时 60 次），过一会儿再试"
            }
            if (code != 200) {
                conn.disconnect()
                return@runCatching null to "请求失败：HTTP $code"
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr: JSONArray = org.json.JSONObject(body).getJSONArray("workflow_runs")
            val list = ArrayList<CiRun>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    CiRun(
                        name = o.optString("name", "workflow"),
                        conclusion = o.optString("conclusion", ""),
                        sha = o.optString("head_sha", "").take(7),
                        time = o.optString("created_at", "").replace("T", " ").take(16),
                    )
                )
            }
            list to null
        }.getOrElse { null to ("网络错误：" + (it.message ?: "")) }
    }
