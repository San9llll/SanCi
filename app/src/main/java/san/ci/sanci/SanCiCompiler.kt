package san.ci.sanci

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 手机本地编译。
 *
 * ## 怎么编
 *
 * 不是在 App 里塞一套 JDK —— 那得几百 MB。而是**用 Termux 那套环境**：
 *
 * ```
 * ① 装依赖     pkg install openjdk-17 aapt2 apksigner zip unzip …
 * ② 解压项目   zip 里的 Android 工程 → 工作目录
 * ③ 跑编译     用环境里的 JDK / aapt2 / d8 / apksigner 走一遍
 * ④ 出包       APK 落到工作目录，界面上给出路径
 * ```
 *
 * Termux 那套是 Nakour 原来就有的（`term/Bootstrap`），这里只是调用它。
 *
 * ## 为什么用 shell 脚本而不是全 Kotlin
 *
 * 装包、解压、编译，这三件事本质上都是「调一堆命令行工具、看输出」。
 * 用脚本写改起来快得多，而且用户能直接在终端里手动跑同样的命令来排查。
 */
object SanCiCompiler {

    /** 工作目录：解压出来的工程放这儿。 */
    fun workDir(ctx: Context): File = File(ctx.filesDir, "workspace").apply { mkdirs() }

    /** 产出的 APK 放这儿。 */
    fun outputDir(ctx: Context): File = File(ctx.filesDir, "output").apply { mkdirs() }

    /**
     * 装编译需要的那些包。
     *
     * `pkg install` 走 Termux 的源，装到 `$PREFIX` 底下。
     * 每个包单独装、单独报，一个失败不影响后面的 —— 不然一个包装不上全卡住。
     */
    fun installDepsScript(): String = """
        #!/data/data/com.termux/files/usr/bin/bash
        set -o pipefail
        export PATH="${'$'}PREFIX/bin:${'$'}PREFIX/bin/applets:${'$'}PATH"

        echo "[1/2] 更新包索引…"
        yes | pkg update -y >/dev/null 2>&1 || true

        echo "[2/2] 装编译工具…"
        for p in openjdk-17 aapt2 apksigner zip unzip git; do
          printf '  %-14s ' "${'$'}p"
          if pkg install -y "${'$'}p" >/dev/null 2>&1; then
            echo "[OK]"
          else
            echo "[FAIL]"
          fi
        done
        echo "DEPINSTALL_DONE"
    """.trimIndent()

    /**
     * 编译一个已经解压好的 Gradle 工程。
     *
     * 优先用工程自带的 `gradlew`（那样用的是工程里锁定的 Gradle 版本，
     * 跟它在 CI 上跑出来的结果一致）；没有才退回系统 gradle。
     */
    fun buildScript(projectDir: String, outDir: String): String = """
        #!/data/data/com.termux/files/usr/bin/bash
        set -o pipefail
        export PATH="${'$'}PREFIX/bin:${'$'}PREFIX/bin/applets:${'$'}PATH"
        export JAVA_HOME="${'$'}PREFIX/opt/openjdk"
        export ANDROID_HOME="${'$'}PREFIX"
        cd "$projectDir" || { echo "进不去项目目录：$projectDir"; exit 1; }

        echo "=== 开始编译 ==="
        echo "项目：$(pwd)"
        echo

        if [ -x ./gradlew ]; then
          echo "用工程自带的 gradlew…"
          # zip 往返会丢可执行位，这里补一下
          chmod +x ./gradlew 2>/dev/null || true
          ./gradlew assembleDebug --no-daemon
        elif command -v gradle >/dev/null 2>&1; then
          echo "用系统 gradle…"
          gradle assembleDebug --no-daemon
        else
          echo "没找到 gradlew，也没装 gradle。"
          echo "先在设置里装一遍编译依赖，或者确认这个包是 Gradle 工程。"
          exit 1
        fi

        rc=${'$'}?
        echo
        echo "=== 编译结束（退出码 ${'$'}rc）==="

        # 把找到的 APK 收拢到输出目录，界面上好找
        mkdir -p "$outDir"
        found=0
        for f in $(find . -name "*.apk" -not -path "*/intermediates/*" 2>/dev/null | head -5); do
          cp -f "${'$'}f" "$outDir/" 2>/dev/null && echo "产出：$(basename ${'$'}f)" && found=1
        done
        [ "${'$'}found" = "0" ] && echo "没找到产出的 APK"
        echo "BUILD_DONE ${'$'}rc"
    """.trimIndent()

    /**
     * 在 Termux 环境里跑一段脚本，逐行把输出喂给 [onLine]。
     *
     * 用 `$PREFIX/bin/bash` 而不是系统 `sh` —— Termux 那些二进制靠 `$PREFIX` 找库，
     * 用系统 shell 起会报 `CANNOT LINK EXECUTABLE`。
     */
    suspend fun runInTermux(
        ctx: Context,
        script: String,
        onLine: (String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val prefix = san.ci.term.Bootstrap.prefix(ctx)
        val bash = File(prefix, "bin/bash")
        if (!bash.exists()) {
            onLine("[错误] Termux 环境还没装 —— 先在设置里初始化一次")
            return@withContext -1
        }

        runCatching {
            val pb = ProcessBuilder(bash.absolutePath, "-c", script)
            pb.redirectErrorStream(true)
            pb.directory(prefix)
            pb.environment().apply {
                put("PREFIX", prefix.absolutePath)
                put("PATH", prefix.absolutePath + "/bin:" + prefix.absolutePath + "/bin/applets")
                put("HOME", File(ctx.filesDir, "home").absolutePath)
                put("TMPDIR", File(ctx.filesDir, "tmp").absolutePath)
                put("LD_LIBRARY_PATH", prefix.absolutePath + "/lib")
                put("TERM", "xterm-256color")
            }
            val p = pb.start()

            // 逐行读。编译的输出是渐进的，攒完再显示的话用户要盯着空屏好几分钟。
            p.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { onLine(it) }
            }
            // 装 JDK 那种要拉几十 MB，给足十分钟
            p.waitFor(10, TimeUnit.MINUTES)
            val code = p.exitValue()
            p.destroy()
            code
        }.getOrElse {
            onLine("[错误] " + (it.message ?: "执行失败"))
            -1
        }
    }
}
