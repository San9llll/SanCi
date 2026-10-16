package san.ci.sanci

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 一次编译任务的全部状态。
 *
 * ## 为什么放全局
 *
 * 编译在后台跑（可能要十几分钟），而用户会在「主页」发起、切到「进度」看输出、
 * 甚至切出去干别的。如果状态挂在某个页面里，一离开就没了。
 *
 * 所以它放在这个单例里 —— 跟 Nakour 的 `AgentTaskStore` 是同一个思路：
 * **状态活在这儿，界面只是"看"。**
 */
object SanCiBuildStore {

    /** 编译进行到哪一步。 */
    enum class Status(val label: String) {
        Idle("待机"),
        Preparing("准备环境"),
        Importing("导入项目"),
        Building("编译中"),
        Success("编译完成"),
        Failed("编译失败"),
    }

    /** 当前任务。没有任务时是 null。 */
    var task: BuildTask? by mutableStateOf(null)
        private set

    /** 编译输出。逐行追加，界面上滚动显示。 */
    val log = mutableStateListOf<String>()

    /** 追加一行输出。空行会被保留 —— 编译日志里的空行是分段用的。 */
    fun append(line: String) {
        log.add(line)
        // 只留最近两千行。gradle 的日志能刷几万行，全留着会把内存吃光，
        // 而且用户也只会看最后那段。
        while (log.size > 2000) log.removeAt(0)
    }

    /** 开一个新任务。会清掉上次的日志。 */
    fun start(zipName: String) {
        log.clear()
        progress = 0f
        task = BuildTask(zipName = zipName, status = Status.Preparing)
    }

    /** 装环境的进度（0~1）。Bootstrap 的回调会给这个数。 */
    var progress: Float by mutableStateOf(0f)
        private set

    fun setProgress(v: Float) {
        progress = v.coerceIn(0f, 1f)
    }

    fun setStatus(status: Status) {
        task = task?.copy(status = status)
    }

    /** 编译结束。 */
    fun finish(ok: Boolean, apkPath: String? = null) {
        task = task?.copy(
            status = if (ok) Status.Success else Status.Failed,
            apkPath = apkPath,
        )
    }

    /** 用户手动停掉。 */
    fun reset() {
        task = null
        log.clear()
    }

    /** 现在有没有在跑。界面用它决定按钮显不显示、要不要自动滚。 */
    val running: Boolean
        get() = task?.status.let {
            it == Status.Preparing || it == Status.Importing || it == Status.Building
        }
}

/** 一次编译任务的记录。 */
data class BuildTask(
    /** 导入的 zip 名，用来显示"正在编哪个包"。 */
    val zipName: String,
    val status: SanCiBuildStore.Status,
    /** 编译成功时产出的 APK 路径。 */
    val apkPath: String? = null,
)
