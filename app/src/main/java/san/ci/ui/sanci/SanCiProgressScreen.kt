package san.ci.ui.sanci

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import san.ci.sanci.SanCiBuildStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 进度页 —— 编译输出。
 *
 * 就是一个滚动的日志窗口，把 [SanCiBuildStore.log] 里的东西逐行画出来。
 * 编译在跑的时候会**自动跟着滚到底**；跑完了就不滚了 —— 不然用户往上翻历史
 * 会被一直拽回去。
 */
@Composable
fun SanCiProgressScreen(railWidth: Dp = 0.dp) {
    val listState = rememberLazyListState()
    val running = SanCiBuildStore.running

    // 只在跑的时候自动滚。跑完就不动，让用户自己翻。
    LaunchedEffect(SanCiBuildStore.log.size, running) {
        if (running && SanCiBuildStore.log.isNotEmpty()) {
            listState.animateScrollToItem(SanCiBuildStore.log.size - 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = railWidth)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("进度", fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            val task = SanCiBuildStore.task
            if (task != null) {
                StatusPill(task.status)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            SanCiBuildStore.task?.zipName ?: "还没有任务",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth().weight(1f)) {
            if (SanCiBuildStore.log.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "空。去主页导入一个 zip 开始编译。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    items(SanCiBuildStore.log.size) { i ->
                        Text(
                            text = SanCiBuildStore.log[i],
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SanCiBuildStore.log[i].let { line ->
                                when {
                                    line.contains("[错误]") || line.contains("FAIL") ->
                                        MiuixTheme.colorScheme.error
                                    line.contains("[OK]") || line.contains("成功") ->
                                        MiuixTheme.colorScheme.primary
                                    else -> MiuixTheme.colorScheme.onSurface
                                }
                            },
                        )
                    }
                }
            }
        }

        // 编完给一句结论，省得用户去日志里翻
        val task = SanCiBuildStore.task
        if (task != null && !running) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    when (task.status) {
                        SanCiBuildStore.Status.Success -> {
                            Text("编译完成", style = MiuixTheme.textStyles.title5)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                task.apkPath?.let { "产物：$it" } ?: "产物在 App 的 output 目录里",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }

                        SanCiBuildStore.Status.Failed -> {
                            Text(
                                "编译失败",
                                style = MiuixTheme.textStyles.title5,
                                color = MiuixTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "上面最后几行是原因。如果提示找不到 gradlew，先回主页装一遍编译依赖。",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

/** 状态小圆点 + 文字。 */
@Composable
private fun StatusPill(status: SanCiBuildStore.Status) {
    val tint = when (status) {
        SanCiBuildStore.Status.Success -> MiuixTheme.colorScheme.primary
        SanCiBuildStore.Status.Failed -> MiuixTheme.colorScheme.error
        SanCiBuildStore.Status.Idle -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        else -> MiuixTheme.colorScheme.primary
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(6.dp))
        Text(status.label, fontSize = 11.sp, color = tint)
    }
}
