package san.ci.ui.nav

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings

/** SanCi 的四个一级页面。 */
enum class Dest(val label: String, val icon: ImageVector) {
    /** 导入 zip、一键编译 */
    Home("主页", MiuixIcons.Home),

    /** 编译输出、进度 */
    Progress("进度", MiuixIcons.Layers),

    /** GitHub Actions 的运行状况 */
    Ci("CI", MiuixIcons.GridView),

    Settings("设置", MiuixIcons.Settings),
}
