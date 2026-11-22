<div align="center">
  <img src="res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="Pulp 图标" />
  <h1>Pulp · 番茄 UI 自定义</h1>
  <p>Pulp 6.0 · versionCode 100 · LSPosed 模块</p>
</div>

一款专为 **番茄免费小说** 打造的 libxposed API 102 界面定制与净化模块。

支持液态玻璃与全局分级玻璃视觉定制、精简页面模块与入口、阅读器广告与短剧推广拦截、系统级桌面卡片弹窗屏蔽、本地 VIP 状态与书籍编号工具。共提供 **90 项独立可调设置**（82 个布尔开关 + 8 个整型参数），默认向后兼容，可按需自由组合。

## ✨ Pulp 6.0 更新亮点

- **液态玻璃底栏（Android 13+）**：支持悬浮、贴底、液态三种样式；液态模式具备静息柔雾药丸、整条底栏按压微膨胀、滑块果冻形变与可调 Q 弹度（0–100%），修复切页串屏、开关书闪白、隐藏 Tab 幽灵点击及书架多选编辑栏重叠穿透问题。
- **全局分级玻璃与推荐榜增强（Android 12+）**：支持推荐流书卡文字区封面氛围模糊、书城/「我的」页顶部组件可选液态玻璃、推荐榜固定磨砂与**自定义底图替换**（支持跨进程热更新与一键恢复默认）；新增「推荐榜多列均分铺满」与「推荐榜边缘轻微羽化」独立开关，消除推荐榜左右竖状白边与顶部搜索栏下沿浅青色截断线。
- **阅读器短剧推广与桌面卡片拦截**：新增阅读器内段间与整页短剧推广的排版前模型与请求门拦截（避免正文留白），及阅读器短剧推广挂件拦截；增强「禁止添加桌面卡片提示」，前置拦截宿主系统级桌面卡片创建与厂商订阅卡权限引导。
- **「我的」页面与性能优化**：新增「隐藏会员中心」与平板专属「隐藏我的催更」，修复平板布局转发与 7.4.x 九宫格隐藏问题；优化冷启动与列表滑动性能（关闭总开关跳过业务热 Hook、非基线符号表按当前版本懒加载、移除全局 `View.draw` 拦截与滚动期全树扫描）。
- **多版本符号适配**：按宿主包名与 `versionCode` 路由独立符号映射表，覆盖国内版 4 个版本与 Google Play 版 2 个版本。
- **Compose 界面打磨**：15 个功能页一级分类全部重绘独立线性图标，修复进入二级分类返回后列表滚回顶部的问题。

## 📱 兼容性要求

| 项目 | 要求 |
|---|---|
| 目标应用 | 番茄免费小说 |
| 应用包名 | `com.dragon.read` / `com.dragon.read.oversea.gp` |
| 适配版本 | 国内 `7.3.9.32` / `7.4.1.32` / `7.4.3.32` / `7.4.5.32`；谷歌 `7.3.9.32` / `7.4.1.33` |
| Android 版本 | Android 9 及以上（全局玻璃需 Android 12+，液态玻璃底栏需 Android 13+） |
| 框架要求 | 支持 libxposed API 102 的 LSPosed 框架（亦支持免 Root 内嵌框架后备通道） |
| 模块包名 | `io.github.mianxyz.fanqiecustomizer` |

**适配说明：** 模块按包名与 `versionCode` 匹配对应符号表（国内 `7.3.9.32` 保留原符号直通），全部已登记的类、方法、字段与资源均经过真实 APK DEX 与资源表静态核验。各版本实机覆盖与已知边界记录在 `CHANGELOG.md` 中；静态定义存在不代表未实测场景已 100% 验证。

## 🧩 功能列表

### 🪟 界面美化与玻璃视觉

- **底部导航栏玻璃**：支持「悬浮玻璃 / 贴底玻璃 / 液态玻璃」三种形态，提供不透明度、模糊强度、圆角、左右边距、底部间距、悬浮底栏长度（50–100%）与液态滑块 Q 弹度（0–100%）精细调节，支持横向滑动切页。
- **全局玻璃**：
  - 书籍文字区封面氛围模糊玻璃。
  - 搜索栏、卡片与弹窗分级玻璃。
  - 书城搜索与分类按钮、「我的」页顶部组件可选液态玻璃。
  - 推荐榜底部固定毛玻璃，支持**选择自定义底图**（跨进程热更新）与一键恢复默认磨砂。
  - 推荐榜边缘轻微透明羽化（默认关闭，开启全局玻璃即自动清除宿主左右竖状白边）。
  - 推荐榜多列均分铺满（默认关闭，平板宽屏按需开启，手机端保持原生每页 8 本横向滑动）。
- **顶部与交互**：书城顶部滚动羽化、下拉刷新二次确认防误触、沉浸隐藏状态栏。

### 🏠 首页与书城

- 分别隐藏底部导航入口（自动保留至少一个可用入口）。
- 自定义默认启动的底部导航页。
- 分别隐藏 11 个书城频道（自动保留至少一个可用频道，单频道自动折叠频道栏）。
- 隐藏顶部搜索栏。
- 禁止添加桌面卡片提示（覆盖宿主路由及系统级创建卡片/厂商订阅卡权限引导）。
- 隐藏福利浮动图标、发帖快捷按钮、自动短剧续播浮窗。

### 📚 书架

- 自动展开全部书籍。
- 隐藏金币相关控件。
- 书架多选编辑模式自动暂停并隐藏玻璃底栏，确保底部操作按钮正常点击不穿透。

### 👤 我的页面

支持分别隐藏：

- 收益记录与收益卡片。
- 常玩游戏。
- 10 项功能入口：我的订单、浏览记录、我的消息、创作中心、我的预约、我的点赞、我的下载、游戏中心、反馈与帮助、**会员中心**（保留「我的书币」）。

平板布局（`sw >= 600dp`）还可分别隐藏 9 项专属入口：

- 视频创作、兴趣偏好、我的笔记、看过的人、番茄指南、我的钱包、活动中心、我的公益、**我的催更**。

### 📖 阅读界面与广告净化

- 屏蔽启动开屏广告。
- 隐藏已适配的阅读广告组件（含阅读器段间与整页短剧推广前置拦截，避免正文留白）。
- 屏蔽阅读页直播弹窗。
- 隐藏原生横幅广告及其占位区域（含阅读器短剧推广挂件）。
- 隐藏章节末尾免广告入口、快捷听书入口、章末小游戏入口。
- 免广告无限听书与自动阅读不中断（本地授权判断）。

### 🔎 书籍编号工具与实用功能

- 显示并一键复制书籍内部编号，支持通过编号搜索直达书籍及应用内工具入口。
- 本地 VIP 状态判断（仅影响本地逻辑）。
- 显示接口返回的原始阅读计数字段。
- 诊断已发现的 AB 配置名称、输出功能命中日志。
- 关闭自动更新弹窗、隐藏或恢复桌面模块图标。

### 💝 赞赏

- 如果觉得我做的还不错的话，就给我买杯奶茶吧(＾∇＾)🧋

### 🔌 免 Root 安装（LSPatch / FPA）

除标准 LSPosed 外，模块同时兼容内嵌进宿主 APK 的免 Root 框架。两条通道并存，互不影响：

| 顺序 | 通道 | 何时使用 |
|---|---|---|
| 1 | `XposedService` 远程偏好 / 远端文件 | 标准 LSPosed / Root 环境，**主通道** |
| 2 | `SettingsProvider`（`io.github.mianxyz.fanqiecustomizer.settings`） | 内嵌框架，同步读取配置与只读推荐榜底图，首启即生效 |
| 3 | `SettingsResponder` 有序广播 | Provider 不可用时的第二顺位 |
| 4 | `EmbeddedPrefs` 共享文件 | 前两者均不可用时的最后一层后备 |

Hook 侧仅在远程偏好完全为空时回退 2→3→4，避免 Root 环境下旧文件覆盖框架最新配置。全部 **90 项设置**均通过白名单校验同步，模块自身外观主题键 `glass_level` 留在本地不同步。

**功能限制说明：**

- 广告隐藏仅覆盖已适配的组件与推广位，不保证屏蔽全部未知或服务端新增动态广告模板。
- 原始阅读计数字段由服务器下发，不代表实时真实在读人数。
- 下架书籍能否访问取决于服务端状态及本地缓存。
- 本地 VIP 判断不会开通服务端会员权益。
- 免广告送爱发电礼物功能暂未加入。

## 🔒 隐私说明

模块不包含任何网络请求、数据上报或用户行为统计代码。

诊断日志仅记录受限的技术类名、已登记配置名称与布局指标，不主动输出：

- 书籍名称或书籍编号。
- 用户账号与个人敏感信息。
- 完整异常堆栈正文或原始 Intent 数据。

## 🎨 设置界面（Kotlin + Jetpack Compose）

设置界面基于 Kotlin + Jetpack Compose（Material 3 Expressive）构建，通过 `ConfigBridge` 与 Java Hook 层共享同一份 `SharedPreferences("settings")` 及 `SettingsApp.sync()` 同步通道；自定义推荐榜底图以私有压缩文件 + 内容 SHA-256 修订号形式经 libxposed API 102 远端文件（或 `SettingsProvider` 后备）跨进程传输，不向宿主传递相册临时 URI。

### 视觉规范

| 项目 | 实现 |
|---|---|
| 层级 | 页面底 `surfaceContainer` → 卡片 `surfaceContainerLowest`，无投影、无硬边框，仅靠明度差分层 |
| 强调色 | 番茄橙 `#EF5B33`（深色模式 `#FF8A65`），仅用于开关、图标高亮与激活态 |
| 圆角 | 分组卡片 `RoundedCornerShape(24.dp)`；徽章、标签与分段器为全圆角胶囊 |
| 异形组件 | 首页状态徽章采用 12 瓣花瓣波浪圆（`ScallopedShape`） |
| 进度 | M3 Expressive `LinearWavyProgressIndicator` 表示框架连接状态 |
| 导航 | 悬浮胶囊底栏，激活项带药丸高亮并展开文字；功能页一级列表保留返回滚动位置 |
| 字体 | 大标题微衬线（`FontFamily.Serif`）；正文与列表无衬线（`FontFamily.SansSerif`） |
| 界面玻璃 | 主题页提供「无 / 标准 / 极致」三档（默认「无」），基于 Haze 叠加实时模糊与高光描边 |

### 关键依赖

| 依赖 | 版本 | 说明 |
|---|---|---|
| Android Gradle Plugin | 9.1.1 | `material3 1.5.0-rc01` 要求 `minAndroidGradlePluginVersion=9.1.0` 与 `minCompileSdk=37` |
| Gradle | 9.3.1 | AGP 9.1.1 要求的最低版本 |
| compileSdk | 37 | 同上 |
| Kotlin | 2.4.21 | AGP 9 内置 Kotlin 与 Compose 编译器插件 |
| Compose BOM | 2026.09.00 | 统一 `androidx.compose.*` 依赖版本 |
| material3 | `1.5.0-rc01` | 提供 `LinearWavyProgressIndicator` 等 Expressive 组件 |
| Haze | 2.1.0 | 界面液态玻璃（`hazeBlur` + `HazeInput.Backdrop`） |
| 图标 | 本地 `AppIcons` | 全部轻量级内置矢量路径（含 15 个独立重绘的一级分类图标），不引入庞大的 `material-icons-extended` |

## 🛠️ 构建与测试

### Gradle 构建（推荐）

```powershell
$env:JAVA_HOME    = 'C:\Program Files\Zulu\zulu-25'   # JDK 21+
$env:ANDROID_HOME = '<your-sdk-root>'                 # 需安装 platform 37 与 build-tools 37.0.0
.\gradlew.bat :app:assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

**Release 签名配置**

Release 签名信息从本地 `local.properties` 读取（已由 `.gitignore` 忽略，密钥不进入仓库）：

```properties
pulp.storeFile=D\\:\\path\\to\\keystore.jks
pulp.storePassword=******
pulp.keyAlias=your-alias
pulp.keyPassword=******
```

四项缺失时 `assembleRelease` 会产出未签名 APK 而非中断构建。同时构建双包：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease
```

**传统纯 Java 界面构建（保留）**

原 `SettingsActivity.java` 保留在 `legacy-java/`，可通过 `build.ps1` 单独构建：

```powershell
.\build.ps1 -SdkRoot <your-sdk-root> -KeyStorePath <path-to-keystore>
```

### 纯 Java 规则与契约测试

`tests/` 下包含 **57 个独立纯 Java 测试类**（无 JUnit 依赖），覆盖 90 键默认值与同步白名单契约、121 项符号解析检查、UI 图标唯一性与滚动状态契约、液态弹簧数值收敛性、多版本反射过滤以及冻结源码等价校验。

编译生产源码后执行 `test.ps1` 或逐类运行：

```powershell
$jdk = 'C:\Program Files\Zulu\zulu-25'
$sdk = '<your-sdk-root>'
$androidJar = Get-ChildItem $sdk -Recurse -Filter android.jar | Select-Object -First 1 -ExpandProperty FullName
$cp = "$androidJar;libs\api-102.0.0.jar;libs\service-102.0.0.jar;libs\interface-102.0.0.jar"

& "$jdk\bin\javac.exe" --release 17 -encoding UTF-8 -cp $cp -d build\classes (Get-ChildItem src -Recurse -Filter *.java)
.\test.ps1 -SdkRoot $sdk -JdkRoot $jdk
```

同时可运行 DEX/资源定向校验器自检（11 项）：

```powershell
python -B tools/compat/validator_selftest.py
```

其中 `SettingsKeyContractTest` 与 `SettingsDefaultsContractTest` 共同锁死核心配置不变量：

> Compose 界面声明的每一个布尔与整型键，都必须与 `Rules.allSettingKeys()` 和 `PrivacyRules.settingKey` 严格一致，确保 LSPosed 远程偏好与免 Root 后备通道不会静默丢弃开关，且模块本地键 `glass_level` 绝不泄露到宿主进程。
