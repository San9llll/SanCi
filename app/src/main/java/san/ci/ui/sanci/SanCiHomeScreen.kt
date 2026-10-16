package san.ci.ui.sanci

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import san.ci.sanci.SanCiBuildStore
import san.ci.sanci.SanCiCompiler
import san.ci.term.Bootstrap
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.util.zip.ZipFile

/**
 * SanCi 主页。
 *
 * ## 一件事：**导入 zip，一键编译**
 *
 * 流程：
 * ```
 * 选 zip → 解压到工作区 → 装编译依赖（如果没装）→ 跑 gradle → 出 APK
 * ```
 *
 * ## 首次进入
 *
 * 如果 Termux 环境还没装，这一页会顶上来一个「初始化终端」的引导 ——
 * 因为后面所有编译都得靠那个环境。装完才显示正常的主页内容。
 */
@Composable
fun SanCiHomeScreen(
    railWidth: Dp = 0.dp,
    onOpenProgress: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Termux 环境装没装。装完这里会变 true，页面自动从引导切到正常内容。
    var envReady by remember { mutableStateOf(Bootstrap.isInstalled(ctx)) }
    var installing by remember { mutableStateOf(false) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pickedUri = uri
        pickedName = uri.lastPathSegment?.substringAfterLast('/') ?: "导入的包.zip"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = railWidth)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text("SanCi", fontSize = 30.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            "在手机上编译 APK",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(22.dp))

        // ---------- 环境没装：引导 ----------
        if (!envReady) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("先初始化编译环境", style = MiuixTheme.textStyles.title4)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "编译要用的 JDK / aapt2 / 打包工具都在一个精简的 Linux 环境里。" +
                            "这是**一次性**的，之后编译都直接用它。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            installing = true
                            scope.launch {
                                // 装 Termux 环境（下载 + 解压 + 铺符号链接，几分钟）
                                Bootstrap.install(ctx) { line ->
                                    SanCiBuildStore.append(line)
                                }
                                envReady = Bootstrap.isInstalled(ctx)
                                installing = false
                                if (envReady) {
                                    // 环境好了，顺手把编译依赖也装上
                                    SanCiBuildStore.start("（初始化）")
                                    SanCiBuildStore.setStatus(SanCiBuildStore.Status.Preparing)
                                    SanCiCompiler.runInTermux(
                                        ctx, SanCiCompiler.installDepsScript(),
                                    ) { SanCiBuildStore.append(it) }
                                    SanCiBuildStore.finish(ok = true)
                                }
                            }
                        },
                        enabled = !installing,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (installing) "正在初始化…" else "开始初始化")
                    }
                    if (installing) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "装在后台跑，切到「进度」能看到输出。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            return@Column
        }

        // ---------- 环境好了：导入 + 编译 ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("导入项目", style = MiuixTheme.textStyles.title4)
                Spacer(Modifier.height(6.dp))
                Text(
                    pickySummary(pickedName),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        colors = ButtonDefaults.buttonColorsSecondary(),
                        modifier = Modifier.weight(1f),
                    ) { Text("选 zip") }
                    Button(
                        onClick = {
                            val uri = pickedUri ?: return@Button
                            val name = pickedName ?: "import.zip"
                            onOpenProgress()      // 跳到进度页，让用户看着
                            scope.launch { importAndBuild(ctx, uri, name) }
                        },
                        enabled = pickedUri != null && !SanCiBuildStore.running,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) { Text("一键编译") }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 快捷入口 ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("快捷入口", style = MiuixTheme.textStyles.title4)
                Spacer(Modifier.height(10.dp))
                HomeEntry("编译进度", "看正在跑的输出") { onOpenProgress() }
                Spacer(Modifier.height(8.dp))
                HomeEntry("装编译依赖", "JDK / aapt2 / apksigner…") {
                    scope.launch {
                        SanCiBuildStore.start("（装依赖）")
                        SanCiCompiler.runInTermux(
                            ctx, SanCiCompiler.installDepsScript(),
                        ) { SanCiBuildStore.append(it) }
                        SanCiBuildStore.finish(ok = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeEntry(title: String, summary: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .let { it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MiuixTheme.textStyles.title5)
                Text(
                    summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

private fun pickySummary(name: String?): String =
    if (name == null) "选一个 Android 工程的 zip，解压后直接编译。"
    else "已选：$name"

/** 解压 + 编译。整个过程一边跑一边把输出喂给进度页。 */
private suspend fun importAndBuild(ctx: android.content.Context, uri: Uri, name: String) {
    SanCiBuildStore.start(name)

    val work = SanCiCompiler.workDir(ctx)
    val out = SanCiCompiler.outputDir(ctx)
    // 每次编译清一次工作区，免得上次的产物混进来
    withContext(Dispatchers.IO) {
        runCatching { work.deleteRecursively(); work.mkdirs() }
    }

    // ① 解压
    SanCiBuildStore.setStatus(SanCiBuildStore.Status.Importing)
    SanCiBuildStore.append("解压 $name …")
    val unzipOk = withContext(Dispatchers.IO) {
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val tmp = File(ctx.cacheDir, "import.zip")
                tmp.outputStream().use { input.copyTo(it) }
                ZipFile(tmp).use { zf ->
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        val target = File(work, e.name)
                        // 防目录穿越：解压出来的路径必须还在工作区里
                        if (!target.canonicalPath.startsWith(work.canonicalPath)) continue
                        if (e.isDirectory) target.mkdirs()
                        else {
                            target.parentFile?.mkdirs()
                            zf.getInputStream(e).use { ins ->
                                target.outputStream().use { ins.copyTo(it) }
                            }
                        }
                    }
                }
                tmp.delete()
            }
            true
        }.getOrElse {
            SanCiBuildStore.append("[错误] 解压失败：" + (it.message ?: ""))
            false
        }
    }
    if (!unzipOk) {
        SanCiBuildStore.finish(ok = false)
        return
    }

    // ② 找工程根：gradlew 可能在解压出来的第一层子目录里
    val projectDir = withContext(Dispatchers.IO) {
        if (File(work, "gradlew").exists()) work
        else work.listFiles()?.firstOrNull { it.isDirectory && File(it, "gradlew").exists() } ?: work
    }
    SanCiBuildStore.append("工程目录：" + projectDir.absolutePath)
    SanCiBuildStore.append("")

    // ③ 编译
    SanCiBuildStore.setStatus(SanCiBuildStore.Status.Building)
    val code = SanCiCompiler.runInTermux(
        ctx,
        SanCiCompiler.buildScript(projectDir.absolutePath, out.absolutePath),
    ) { SanCiBuildStore.append(it) }

    // ④ 收尾：看输出目录里有没有 APK
    val apk = withContext(Dispatchers.IO) {
        out.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
    }
    SanCiBuildStore.finish(ok = code == 0 && apk != null, apkPath = apk?.absolutePath)
}
