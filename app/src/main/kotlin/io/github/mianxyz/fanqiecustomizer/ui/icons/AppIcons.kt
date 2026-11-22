package io.github.mianxyz.fanqiecustomizer.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * 本地最小图标集。
 *
 * 刻意不使用 androidx.compose.material:material-icons-extended：该产物约 34 MB，
 * 会把这个模块的 dex 从不足 1 MB 撑到 62 MB，而界面只用到 12 个图标。
 *
 * 路径数据取自 Google Material Symbols（Apache-2.0），原始 viewBox 为
 * "0 -960 960 960"。因此统一用一个 translationY = 960 的 group，
 * 把绘制坐标平移进 0..960 的视口。
 */
object AppIcons {

    // Authored category outlines: 24dp, matching the existing 1.5dp optical weight.
    /** 叠层玻璃面板 */
    val GlassLayers: ImageVector by lazy { categoryOutline("GlassLayers", "M4 8V5Q4 3 6 3H18Q20 3 20 5V15Q20 17 18 17H16 M8 7H13Q15 7 15 9V19Q15 21 13 21H5Q3 21 3 19V12Q3 10 5 10 M8 14L11 11 M8 18L11 15") }

    /** 悬浮圆角玻璃底栏 */
    val GlassDock: ImageVector by lazy { categoryOutline("GlassDock", "M4 12V5Q4 3 6 3H18Q20 3 20 5V12 M5 16H19Q21 16 21 18Q21 20 19 20H5Q3 20 3 18Q3 16 5 16Z M8 18H8.1 M12 18H12.1 M16 18H16.1") }

    /** 顶部栏与渐隐层次 */
    val TopFade: ImageVector by lazy { categoryOutline("TopFade", "M5 3H19Q21 3 21 5V8H3V5Q3 3 5 3Z M3 12H21 M5 16H19 M8 20H16") }

    /** 底部导航分区 */
    val NavigationTabs: ImageVector by lazy { categoryOutline("NavigationTabs", "M3 9V5Q3 3 5 3H19Q21 3 21 5V9 M3 13H21V19Q21 21 19 21H5Q3 21 3 19Z M9 13V21 M15 13V21") }

    /** 书城模块裁减 */
    val LayoutTrim: ImageVector by lazy { categoryOutline("LayoutTrim", "M10 4H19Q20 4 20 5V14 M14 20H5Q4 20 4 19V10 M9 4V9H4 M15 20V15H20 M13 8H16 M8 13V16") }

    /** 书城多频道书脊 */
    val BookChannels: ImageVector by lazy { categoryOutline("BookChannels", "M3 5H7V20H3Z M10 5H14V20H10Z M17 4L20 3L23 18L20 19Z M4 9H6 M11 9H13") }

    /** 手势确认 */
    val GestureConfirm: ImageVector by lazy { categoryOutline("GestureConfirm", "M7 12V5Q7 3 9 3Q11 3 11 5V11 M11 9Q13 7 15 9Q17 8 18 10Q21 10 21 13V16Q21 21 16 21H12Q10 21 9 19L4 14Q3 12 5 11Q6 11 7 12 M14 15L16 17L19 14") }

    /** 浮窗快捷箭头 */
    val FloatingShortcut: ImageVector by lazy { categoryOutline("FloatingShortcut", "M12 3H5Q3 3 3 5V19Q3 21 5 21H19Q21 21 21 19V12 M14 3H21V10 M21 3L12 12") }

    private fun categoryOutline(name: String, data: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            addPath(pathData = addPathNodes(data), fill = null,
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }.build()


    /** Material Symbols: dashboard */
    val Dashboard: ImageVector by lazy {
        ImageVector.Builder(
            name = "Dashboard",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M510-600v-210q0-12.75 8.63-21.38Q527.25-840 540-840h270q12.75 0 21.38 8.62Q840-822.75 840-810v210q0 12.75-8.62 21.37Q822.75-570 810-570H540q-12.75 0-21.37-8.63Q510-587.25 510-600ZM120-480v-330q0-12.75 8.63-21.38Q137.25-840 150-840h270q12.75 0 21.38 8.62Q450-822.75 450-810v330q0 12.75-8.62 21.37Q432.75-450 420-450H150q-12.75 0-21.37-8.63Q120-467.25 120-480Zm390 330v-330q0-12.75 8.63-21.38Q527.25-510 540-510h270q12.75 0 21.38 8.62Q840-492.75 840-480v330q0 12.75-8.62 21.37Q822.75-120 810-120H540q-12.75 0-21.37-8.63Q510-137.25 510-150Zm-390 0v-210q0-12.75 8.63-21.38Q137.25-390 150-390h270q12.75 0 21.38 8.62Q450-372.75 450-360v210q0 12.75-8.62 21.37Q432.75-120 420-120H150q-12.75 0-21.37-8.63Q120-137.25 120-150Zm60-360h210v-270H180v270Zm390 330h210v-270H570v270Zm0-450h210v-150H570v150ZM180-180h210v-150H180v150Zm210-330Zm180-120Zm0 180ZM390-330Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: sync */
    val Sync: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sync",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M220-477q0 63 23.5 109.5T307-287l30 21v-94q0-13 8.5-21.5T367-390q13 0 21.5 8.5T397-360v170q0 13-8.5 21.5T367-160H197q-13 0-21.5-8.5T167-190q0-13 8.5-21.5T197-220h100l-15-12q-64-51-93-111t-29-134q0-94 49.5-171.5T342-766q11-5 21 0t14 16q5 11 0 22.5T361-710q-64 34-102.5 96.5T220-477Zm520-6q0-48-23.5-97.5T655-668l-29-26v94q0 13-8.5 21.5T596-570q-13 0-21.5-8.5T566-600v-170q0-13 8.5-21.5T596-800h170q13 0 21.5 8.5T796-770q0 13-8.5 21.5T766-740H665l15 14q60 56 90 120t30 123q0 93-48 169.5T623-195q-11 6-22.5 1.5T584-210q-5-11 0-22.5t16-17.5q65-33 102.5-96T740-483Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: check_circle */
    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "CheckCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "m421-389-98-98q-9-9-22-9t-23 10q-9 9-9 22t9 22l122 123q9 9 21 9t21-9l239-239q10-10 10-23t-10-23q-10-9-23.5-8.5T635-603L421-389Zm59 309q-82 0-155-31.5t-127.5-86Q143-252 111.5-325T80-480q0-83 31.5-156t86-127Q252-817 325-848.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 82-31.5 155T763-197.5q-54 54.5-127 86T480-80Zm0-60q142 0 241-99.5T820-480q0-142-99-241t-241-99q-141 0-240.5 99T140-480q0 141 99.5 240.5T480-140Zm0-340Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: tune */
    val Tune: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tune",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M435.5-128.63Q427-137.25 427-150v-165q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v53h323q12.75 0 21.38 8.68 8.62 8.67 8.62 21.5 0 12.82-8.62 21.32-8.63 8.5-21.38 8.5H487v52q0 12.75-8.68 21.37-8.67 8.63-21.5 8.63-12.82 0-21.32-8.63ZM150-202q-12.75 0-21.37-8.68-8.63-8.67-8.63-21.5 0-12.82 8.63-21.32 8.62-8.5 21.37-8.5h187q12.75 0 21.38 8.68 8.62 8.67 8.62 21.5 0 12.82-8.62 21.32-8.63 8.5-21.38 8.5H150Zm165.5-174.63Q307-385.25 307-398v-52H150q-12.75 0-21.37-8.68-8.63-8.67-8.63-21.5 0-12.82 8.63-21.32 8.62-8.5 21.37-8.5h157v-54q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v166q0 12.75-8.68 21.37-8.67 8.63-21.5 8.63-12.82 0-21.32-8.63ZM457-450q-12.75 0-21.37-8.68-8.63-8.67-8.63-21.5 0-12.82 8.63-21.32 8.62-8.5 21.37-8.5h353q12.75 0 21.38 8.68 8.62 8.67 8.62 21.5 0 12.82-8.62 21.32-8.63 8.5-21.38 8.5H457Zm144.5-173.63Q593-632.25 593-645v-165q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v52h157q12.75 0 21.38 8.68 8.62 8.67 8.62 21.5 0 12.82-8.62 21.32-8.63 8.5-21.38 8.5H653v53q0 12.75-8.68 21.37-8.67 8.63-21.5 8.63-12.82 0-21.32-8.63ZM150-698q-12.75 0-21.37-8.68-8.63-8.67-8.63-21.5 0-12.82 8.63-21.32 8.62-8.5 21.37-8.5h353q12.75 0 21.38 8.68 8.62 8.67 8.62 21.5 0 12.82-8.62 21.32-8.63 8.5-21.38 8.5H150Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: palette */
    val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M480-80q-82 0-155-31.5t-127.5-86Q143-252 111.5-325T80-480q0-85 32-158t87.5-127q55.5-54 130-84.5T489-880q79 0 150 26.5T763.5-780q53.5 47 85 111.5T880-527q0 108-63 170.5T650-294h-75q-18 0-31 14t-13 31q0 27 14.5 46t14.5 44q0 38-21 58.5T480-80Zm0-400Zm-198 11q15-15 15-35t-15-35q-15-15-35-15t-35 15q-15 15-15 35t15 35q15 15 35 15t35-15Zm126-170q15-15 15-35t-15-35q-15-15-35-15t-35 15q-15 15-15 35t15 35q15 15 35 15t35-15Zm214 0q15-15 15-35t-15-35q-15-15-35-15t-35 15q-15 15-15 35t15 35q15 15 35 15t35-15Zm131 170q15-15 15-35t-15-35q-15-15-35-15t-35 15q-15 15-15 35t15 35q15 15 35 15t35-15ZM480-140q11 0 15.5-4.5T500-159q0-14-14.5-26T471-238q0-46 30-81t76-35h73q76 0 123-44.5T820-527q0-132-100-212.5T489-820q-146 0-247.5 98.5T140-480q0 141 99.5 240.5T480-140Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: info */
    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "Info",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M504.5-288.63q8.5-8.62 8.5-21.37v-180q0-12.75-8.68-21.38-8.67-8.62-21.5-8.62-12.82 0-21.32 8.62-8.5 8.63-8.5 21.38v180q0 12.75 8.68 21.37 8.67 8.63 21.5 8.63 12.82 0 21.32-8.63Zm-1-314.57q9.5-9.2 9.5-22.8 0-14.45-9.48-24.22-9.48-9.78-23.5-9.78t-23.52 9.78Q447-640.45 447-626q0 13.6 9.48 22.8 9.48 9.2 23.5 9.2t23.52-9.2ZM480.27-80q-82.74 0-155.5-31.5Q252-143 197.5-197.5t-86-127.34Q80-397.68 80-480.5t31.5-155.66Q143-709 197.5-763t127.34-85.5Q397.68-880 480.5-880t155.66 31.5Q709-817 763-763t85.5 127Q880-563 880-480.27q0 82.74-31.5 155.5Q817-252 763-197.68q-54 54.31-127 86Q563-80 480.27-80Zm.23-60Q622-140 721-239.5t99-241Q820-622 721.19-721T480-820q-141 0-240.5 98.81T140-480q0 141 99.5 240.5t241 99.5Zm-.5-340Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: article */
    val Article: ImageVector by lazy {
        ImageVector.Builder(
            name = "Article",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M180-120q-24 0-42-18t-18-42v-600q0-24 18-42t42-18h600q24 0 42 18t18 42v600q0 24-18 42t-42 18H180Zm0-60h600v-600H180v600Zm0-600v600-600Zm127 501h215q12.75 0 21.38-8.68 8.62-8.67 8.62-21.5 0-12.82-8.62-21.32-8.63-8.5-21.38-8.5H307q-12.75 0-21.37 8.68-8.63 8.67-8.63 21.5 0 12.82 8.63 21.32 8.62 8.5 21.37 8.5Zm0-171h346q12.75 0 21.38-8.68 8.62-8.67 8.62-21.5 0-12.82-8.62-21.32-8.63-8.5-21.38-8.5H307q-12.75 0-21.37 8.68-8.63 8.67-8.63 21.5 0 12.82 8.63 21.32 8.62 8.5 21.37 8.5Zm0-171h346q12.75 0 21.38-8.68 8.62-8.67 8.62-21.5 0-12.82-8.62-21.32-8.63-8.5-21.38-8.5H307q-12.75 0-21.37 8.68-8.63 8.67-8.63 21.5 0 12.82 8.63 21.32 8.62 8.5 21.37 8.5Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: open_in_new */
    val OpenInNew: ImageVector by lazy {
        ImageVector.Builder(
            name = "OpenInNew",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M180-120q-24 0-42-18t-18-42v-600q0-24 18-42t42-18h249q12.75 0 21.38 8.68 8.62 8.67 8.62 21.5 0 12.82-8.62 21.32-8.63 8.5-21.38 8.5H180v600h600v-249q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v249q0 24-18 42t-42 18H180Zm600-617L403-360q-9 9-21 8.5t-21-9.5q-9-9-9-21t9-21l377-377H549q-12.75 0-21.37-8.68-8.63-8.67-8.63-21.5 0-12.82 8.63-21.32 8.62-8.5 21.37-8.5h261q12.75 0 21.38 8.62Q840-822.75 840-810v261q0 12.75-8.68 21.37-8.67 8.63-21.5 8.63-12.82 0-21.32-8.63-8.5-8.62-8.5-21.37v-188Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: restart_alt */
    val RestartAlt: ImageVector by lazy {
        ImageVector.Builder(
            name = "RestartAlt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M418-126q-111-21-184.5-109T160-439q0-71 30-134t85-107q8-6 19-5.5t19 8.5q10 10 9 23t-13 23q-42 35-65.5 85.5T220-439q0 94 58 163.5T425-186q11 2 18.5 11t7.5 20q0 14-10 22.5t-23 6.5Zm126 0q-13 2-23-6.5T511-155q0-11 7.5-20t18.5-11q90-20 147-89.5T741-439q0-109-75.5-184.5T481-699h-20l39 39q8 8 8 21t-8 21q-9 9-22 9t-21-9l-91-90q-5-5-7-10t-2-11q0-6 2-11t7-10l91-91q8-8 21-8t22 8q8 9 8 22t-8 21l-39 39h20q134 0 227 93.5T801-439q0 116-73 204T544-126Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: cloud_off */
    val CloudOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "CloudOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M248-171q-88 0-148-59T40-377q0-80 50.5-134T217-577q2-14 6.5-31.5T236-640L91-785q-9-9-9-21t9-21q9-9 21-9t21 9l707 707q9 9 9 21t-9 21q-9 9-21.5 9T797-78l-94-93H248Zm0-60h397L285-591q-11 15-14.5 34t-3.5 37h-19q-62 0-105 39.5t-43 101q0 61.5 43 105T248-231Zm216-181Zm390 210-47-47q25-17 39-38t14-50q0-43-31-73.5T755-441h-67v-81q0-88-61-147.5T478.47-729q-28.47 0-60.97 9T358-691l-42-42q36-29 77.5-42.5T478-789q111 0 190.5 79T748-520v21q72-1 122 45t50 117q0 35-16.5 73.5T854-202ZM583-470Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: auto_awesome */
    val AutoAwesome: ImageVector by lazy {
        ImageVector.Builder(
            name = "AutoAwesome",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M772-658q-5 0-8.5-2t-5.5-7l-29-68-73-32q-5-2-7-5.5t-2-8.5q0-5 2-8.5t7-5.5l73-31 29-65q2-5 5.5-7t8.5-2q5 0 8.5 2t5.5 7l29 65 73 31q5 2 7 5.5t2 8.5q0 5-2 8.5t-7 5.5l-73 32-29 68q-2 5-5.5 7t-8.5 2Zm0 596q-4 0-8-2t-6-7l-29-65-72-31q-5-2-7-5.5t-2-8.5q0-5 2-8.5t7-5.5l72-31 29-69q2-5 5.5-7t8.5-2q5 0 8.5 2t5.5 7l29 69 72 31q5 2 7 5.5t2 8.5q0 5-2 8.5t-7 5.5l-72 31-29 65q-2 5-6 7t-8 2ZM333-235q-8 0-15.5-4.5T306-252l-65-139-140-63q-8-4-12.5-11.5T84-481q0-8 4.5-15.5T101-508l140-63 65-138q4-9 11.5-13.5T333-727q8 0 15.5 4.5T360-710l66 139 139 63q9 4 13.5 11.5T583-481q0 8-4.5 15.5T565-454l-139 63-66 139q-4 9-11.5 13t-15.5 4Zm0-107 48-96 98-43-98-43-48-96-47 96-99 43 99 43 47 96Zm0-139Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: bolt */
    val Bolt: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bolt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "m393-165 279-335H492l36-286-253 366h154l-36 255Zm-33-195H217q-18 0-26.5-16t2.5-31l338-488q8-11 20-15t24 1q12 5 19 16t5 24l-39 309h176q19 0 27 17t-4 32L388-66q-8 10-20.5 13T344-55q-11-5-17.5-16T322-95l38-265Zm113-115Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    // ── 分类 / 导航 / 动作图标（Material Symbols, Apache-2.0）──

    /** Material Symbols: dock_to_bottom */
    val DockToBottom: ImageVector by lazy {
        ImageVector.Builder(
            name = "DockToBottom",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M180-120q-24.75 0-42.37-17.63Q120-155.25 120-180v-600q0-24.75 17.63-42.38Q155.25-840 180-840h600q24.75 0 42.38 17.62Q840-804.75 840-780v600q0 24.75-17.62 42.37Q804.75-120 780-120H180Zm0-207v147h600v-147H180Zm0-60h600v-393H180v393Zm0 60v147-147Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: view_carousel */
    val ViewCarousel: ImageVector by lazy {
        ImageVector.Builder(
            name = "ViewCarousel",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M80-355v-250q0-28.88 20.59-49.44t49.5-20.56q28.91 0 49.41 20.56Q220-633.88 220-605v250q0 28.87-20.59 49.44Q178.82-285 149.91-285t-49.41-20.56Q80-326.13 80-355Zm260 155q-24 0-42-18t-18-42v-440q0-24 18-42t42-18h280q24 0 42 18t18 42v440q0 24-18 42t-42 18H340Zm400-155v-250q0-28.88 20.59-49.44t49.5-20.56q28.91 0 49.41 20.56Q880-633.88 880-605v250q0 28.87-20.59 49.44Q838.82-285 809.91-285t-49.41-20.56Q740-326.13 740-355Zm-400 95h280v-440H340v440Zm140-220Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: picture_in_picture_alt */
    val PictureInPicture: ImageVector by lazy {
        ImageVector.Builder(
            name = "PictureInPicture",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M140-160q-24 0-42-18t-18-42v-520q0-24 18-42t42-18h680q24 0 42 18t18 42v520q0 24-18 42t-42 18H140Zm0-60h680v-520H140v520Zm0 0v-520 520Zm336-45h275q12.75 0 21.38-8.63Q781-282.25 781-295v-197q0-12.75-8.62-21.38Q763.75-522 751-522H476q-12.75 0-21.37 8.62Q446-504.75 446-492v197q0 12.75 8.63 21.37Q463.25-265 476-265Zm30-60v-137h215v137H506Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: menu_book */
    val MenuBook: ImageVector by lazy {
        ImageVector.Builder(
            name = "MenuBook",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M248-300q53.57 0 104.28 12.5Q403-275 452-250v-427q-45-30-97.62-46.5Q301.76-740 248-740q-38 0-74.5 9.5T100-707v434q31-14 70.5-20.5T248-300Zm264 50q50-25 98-37.5T712-300q38 0 78.5 6t69.5 16v-429q-34-17-71.82-25-37.82-8-76.18-8-54 0-104.5 16.5T512-677v427Zm-44.5 77.5Q461-174 456-178q-47-29-99.85-45-52.84-16-108.15-16-36.54 0-71.77 9T106-208q-23.1 11-44.55-3Q40-225 40-251v-463q0-15 7-27.5T68-761q42-20 87.39-29.5 45.4-9.5 92.61-9.5 63 0 122.5 17T482-731q51-35 109.5-52T712-800q46.87 0 91.93 9.5Q849-781 891-761q14 7 21.5 19.5T920-714v463q0 27.89-22.5 42.45Q875-194 853-208q-34-14-69.23-22.5Q748.54-239 712-239q-54.27 0-106.14 16Q554-207 508-178q-5 4-11.5 5.5T482-171q-8 0-14.5-1.5ZM276-489Zm284-119q0-5.52 4.02-11.34 4.03-5.82 8.98-7.66 30-11 61.34-17 31.33-6 65.91-6 21.61 0 42.68 2.5T784-640q6 2 11 7.4 5 5.39 5 12.13 0 11.47-7.5 17.47-7.5 6-18.5 3-16.8-5-35.4-7.5Q720-610 700-610q-29 0-56 5.5T591-588q-14 5-22.5-.5T560-608Zm0 220q0-5.81 4.02-11.94 4.03-6.12 8.98-8.06 30-11 61.34-16.5 31.33-5.5 65.91-5.5 21.61 0 42.68 2.5T784-420q6 2 11 7.4 5 5.39 5 12.13 0 11.47-7.5 17.47-7.5 6-18.5 3-16.8-5-35.4-7.5Q720-390 700-390q-29 0-56 5t-53 16q-14 5-22.5 0t-8.5-19Zm0-110q0-5.52 4.02-11.34 4.03-5.82 8.98-7.66 30-11 61.34-17 31.33-6 65.91-6 21.61 0 42.68 2.5T784-530q6 2 11 7.4 5 5.39 5 12.13 0 11.47-7.5 17.47-7.5 6-18.5 3-16.8-5-35.4-7.5Q720-500 700-500q-29 0-56 5.5T591-478q-14 5-22.5-.5T560-498Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: chrome_reader_mode */
    val ChromeReaderMode: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChromeReaderMode",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M140-160q-24 0-42-18t-18-42v-520q0-24 18-42t42-18h680q24 0 42 18t18 42v520q0 24-18 42t-42 18H140Zm0-60h310v-520H140v520Zm370 0h310v-520H510v520Zm242-138q10.83 0 17.92-7.12 7.08-7.11 7.08-18 0-10.88-7.08-17.88-7.09-7-17.92-7H579q-10.83 0-17.92 7.12-7.08 7.11-7.08 18 0 10.88 7.08 17.88 7.09 7 17.92 7h173Zm0-109q10.83 0 17.92-7.12 7.08-7.11 7.08-18 0-10.88-7.08-17.88-7.09-7-17.92-7H579q-10.83 0-17.92 7.12-7.08 7.11-7.08 18 0 10.88 7.08 17.88 7.09 7 17.92 7h173Zm0-109q10.83 0 17.92-7.12 7.08-7.11 7.08-18 0-10.88-7.08-17.88-7.09-7-17.92-7H579q-10.83 0-17.92 7.12-7.08 7.11-7.08 18 0 10.88 7.08 17.88 7.09 7 17.92 7h173ZM140-220v-520 520Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: block */
    val Block: ImageVector by lazy {
        ImageVector.Builder(
            name = "Block",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M324-111.5Q251-143 197-197t-85.5-127Q80-397 80-480t31.5-156Q143-709 197-763t127-85.5Q397-880 480-880t156 31.5Q709-817 763-763t85.5 127Q880-563 880-480t-31.5 156Q817-251 763-197t-127 85.5Q563-80 480-80t-156-31.5ZM480-140q61.01 0 117.51-20.5Q654-181 699-220L220-699q-38 46-59 102.17T140-480q0 142.37 98.81 241.19Q337.63-140 480-140Zm259-121q37-45 59-101.49 22-56.5 22-117.51 0-142.38-98.81-241.19T480-820q-60.66 0-116.83 21T261-739l478 478ZM480-480Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: payments */
    val Payments: ImageVector by lazy {
        ImageVector.Builder(
            name = "Payments",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M540-420q-50 0-85-35t-35-85q0-50 35-85t85-35q50 0 85 35t35 85q0 50-35 85t-85 35ZM220-280q-24.75 0-42.37-17.63Q160-315.25 160-340v-400q0-24.75 17.63-42.38Q195.25-800 220-800h640q24.75 0 42.38 17.62Q920-764.75 920-740v400q0 24.75-17.62 42.37Q884.75-280 860-280H220Zm100-60h440q0-42 29-71t71-29v-200q-42 0-71-29t-29-71H320q0 42-29 71t-71 29v200q42 0 71 29t29 71Zm450 180H100q-24.75 0-42.37-17.63Q40-195.25 40-220v-430q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v430h670q12.75 0 21.38 8.68 8.62 8.67 8.62 21.5 0 12.82-8.62 21.32-8.63 8.5-21.38 8.5ZM220-340v-400 400Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: person */
    val Person: ImageVector by lazy {
        ImageVector.Builder(
            name = "Person",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M372-523q-42-42-42-108t42-108q42-42 108-42t108 42q42 42 42 108t-42 108q-42 42-108 42t-108-42ZM160-220v-34q0-38 19-65t49-41q67-30 128.5-45T480-420q62 0 123 15.5T731-360q31 14 50 41t19 65v34q0 25-17.5 42.5T740-160H220q-25 0-42.5-17.5T160-220Zm60 0h520v-34q0-16-9.5-30.5T707-306q-64-31-117-42.5T480-360q-57 0-111 11.5T252-306q-14 7-23 21.5t-9 30.5v34Zm324.5-346.5Q570-592 570-631t-25.5-64.5Q519-721 480-721t-64.5 25.5Q390-670 390-631t25.5 64.5Q441-541 480-541t64.5-25.5ZM480-631Zm0 411Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: tablet */
    val Tablet: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tablet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M100-160q-24.75 0-42.37-17.63Q40-195.25 40-220v-520q0-24.75 17.63-42.38Q75.25-800 100-800h760q24.75 0 42.38 17.62Q920-764.75 920-740v520q0 24.75-17.62 42.37Q884.75-160 860-160H100Zm30-580h-30v520h30v-520Zm60 520h580v-520H190v520Zm640-520v520h30v-520h-30Zm0 0h30-30Zm-700 0h-30 30Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: workspace_premium */
    val WorkspacePremium: ImageVector by lazy {
        ImageVector.Builder(
            name = "WorkspacePremium",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "m480-483-76 57q-5 3-9.5 0t-2.5-8l29-93-78-61q-5-3-2.5-8t7.5-5h94l31-96q2-5 7-5t7 5l30 96h95q5 0 7.5 5t-2.5 8l-78 61 28 93q2 5-2 8t-9 0l-76-57Zm0 364L284-53q-14 5-27-4.5T244-82v-262q-45-47-64.5-103T160-560q0-136 92-228t228-92q136 0 228 92t92 228q0 57-19.5 113T716-344v262q0 15-13 24.5T676-53l-196-66Zm184.5-256.5Q740-451 740-560t-75.5-184.5Q589-820 480-820t-184.5 75.5Q220-669 220-560t75.5 184.5Q371-300 480-300t184.5-75.5ZM304-124l176-55 176 55v-171q-40 29-86 42t-90 13q-44 0-90-13t-86-42v171Zm176-86Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: chevron_right */
    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M530-481 353-658q-9-9-8.5-21t9.5-21q9-9 21.5-9t21.5 9l198 198q5 5 7 10t2 11q0 6-2 11t-7 10L396-261q-9 9-21 8.5t-21-9.5q-9-9-9-21.5t9-21.5l176-176Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: arrow_back */
    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "m274-450 227 227q9 9 9 21t-9 21q-9 9-21 9t-21-9L181-459q-5-5-7-10t-2-11q0-6 2-11t7-10l278-278q9-9 21-9t21 9q9 9 9 21t-9 21L274-510h496q13 0 21.5 8.5T800-480q0 13-8.5 21.5T770-450H274Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: layers */
    val Layers: ImageVector by lazy {
        ImageVector.Builder(
            name = "Layers",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M151-386q-12-8.94-11.5-23.47T152.08-433q8.3-6 18.11-6 9.81 0 17.81 6l292 227 292-227q8.32-6 18.16-6t18.09 5.97q12 8.95 12.38 23.49Q821-395 809-386L517-159q-16.5 13-36.75 13T443-159L151-386Zm292 75L181-515q-23-17.88-23-46.94T181-609l262-204q16.5-13 36.75-13T517-813l262 204q23 17.88 23 46.94T779-515L517-311q-16.5 13-36.75 13T443-311Zm37-47 262-204-262-204-262 204 262 204Zm0-204Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: science */
    val Science: ImageVector by lazy {
        ImageVector.Builder(
            name = "Science",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M172-120q-41.78 0-59.39-39T124-230l248-280v-270h-52q-12.75 0-21.37-8.68-8.63-8.67-8.63-21.5 0-12.82 8.63-21.32 8.62-8.5 21.37-8.5h320q12.75 0 21.38 8.68 8.62 8.67 8.62 21.5 0 12.82-8.62 21.32-8.63 8.5-21.38 8.5h-52v270l248 280q29 32 11.39 71T788-120H172Zm-12-60h640L528-488v-292h-96v292L160-180Zm318-300Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: savings */
    val Savings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Savings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M230-120q-21 0-39.49-13.96Q172.02-147.93 166-168q-25-86-41.54-148.46-16.54-62.46-26.37-109.68-9.82-47.22-13.95-83.79Q80-546.49 80-580q0-92 64-156t156-64h200q27-36 68.5-58t91.5-22q25 0 42.5 17.5T720-820q0 6-1.5 12t-3.5 11q-4 11-7.5 22t-5.5 24l91 91h57q12.75 0 21.38 8.62Q880-642.75 880-630v227q0 10.24-5.5 18.12Q869-377 859-374l-91.93 30.3L713-163q-6.03 19.61-21.84 31.31Q675.34-120 655-120H540q-24.75 0-42.37-17.63Q480-155.25 480-180v-20h-80v20q0 24.75-17.62 42.37Q364.75-120 340-120H230Zm-5-60h115v-80h200v80h115l63-210 102-35v-175h-52L640-728q1-17 5.5-41.5T658-824q-43 11-76 33t-48 51H300q-66.29 0-113.14 46.86Q140-646.29 140-580q0 42 22 146.5T225-180Zm443.5-351.5Q680-543 680-560t-11.5-28.5Q657-600 640-600t-28.5 11.5Q600-577 600-560t11.5 28.5Q623-520 640-520t28.5-11.5ZM490-620q12.75 0 21.38-8.68 8.62-8.67 8.62-21.5 0-12.82-8.62-21.32-8.63-8.5-21.38-8.5H350q-12.75 0-21.37 8.68-8.63 8.67-8.63 21.5 0 12.82 8.63 21.32 8.62 8.5 21.37 8.5h140Zm-10 118Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: person_pin */
    val PersonPin: ImageVector by lazy {
        ImageVector.Builder(
            name = "PersonPin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M359-161H180q-24 0-42-18t-18-42v-600q0-25 18-42.5t42-17.5h600q24 0 42 17.5t18 42.5v600q0 24-18 42t-42 18H601L501-61q-5 5-10 7t-11 2q-6 0-11-2t-10-7L359-161Zm-179-97q60-56 136-90.5T480-383q88 0 164 34.5T780-258v-563H180v563Zm400-244q40-40 40-98t-40-98q-40-40-98-40t-98 40q-40 40-40 98t40 98q40 40 98 40t98-40ZM235-221h490v-9q-54-46-116-69.5T480-323q-67 0-129 23.5T235-230v9Zm192-324q-23-23-23-55t23-55q23-23 55-23t55 23q23 23 23 55t-23 55q-23 23-55 23t-55-23Zm53 5Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material Symbols: power_settings_new */
    val Power: ImageVector by lazy {
        ImageVector.Builder(
            name = "Power",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).group(translationY = 960f) {
            addPath(
                pathData = addPathNodes(
                    "M480-80q-82 0-155-31.5t-127.5-86Q143-252 111.5-325T80-480q0-75 26-142t72-120q9-11 21-10.5t20 8.5q8 8 10.5 20t-8.5 24q-38 45-59.5 101T140-480q0 141 99.5 240.5T480-140q142 0 241-99.5T820-480q0-63-21-119t-59-101q-11-12-9-24t10-20q8-8 20-8.5t21 10.5q46 53 72 120t26 142q0 82-31.5 155T763-197.5q-54 54.5-127 86T480-80Zm-21.5-368.5Q450-457 450-470v-380q0-13 8.5-21.5T480-880q13 0 21.5 8.5T510-850v380q0 13-8.5 21.5T480-440q-13 0-21.5-8.5Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }
}
