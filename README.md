# 安卓考试测试壳

启动 App 后横屏显示考试接入首页。填写 `external_user_id`、`paper_kind`、`level_id`、`paper_id` 和 `request_id`，点击“创建考试并进入”调用 `POST /mock/open/exams`。仅在 HTTP 成功、`errcode=0` 且返回完整的 `data.examination_id` / `data.exam_token` 时进入现有考试 WebView。

首页按钮按提供的 `open_exam_test(1).html` 调用：

| 按钮 | 操作 |
| --- | --- |
| 换新 request_id | 本地生成新的 UUID 请求号，不请求接口 |
| 等级列表 | `GET /mock/open/levels?paper_kind=...` |
| 该等级模拟卷 | `GET /mock/open/levels/{level_id}/papers?external_user_id=...` |
| 权益 | `GET /mock/open/tenant/quota?external_user_id=...` |
| 创建考试并进入 | `POST /mock/open/exams`，正式考省略 `paper_id` |

查询结果在首页显示，可选中复制；查看返回的等级和试卷 ID 后填入对应字段。配置与 request_id 自动保存在本机，网络失败后重试保留同一个 request_id；主动换新后再次创建会重新扣次。按钮在请求期间禁用，查询接口不自动创建考试。返回首页后可点击“重新进入已创建场次”继续当前进程中已创建的考试，无需再次建场。

创建结果的场次 ID 和 token 保留为字符串，查询参数逐项编码，打开地址为 `https://staging-public.hskmock.com/hskmock_webview/index.html#/exam?examination_id=...&exam_token=...&title=...&paper_kind=...`。三种凭据传入方式、签名桥、保存退出继续沿用。

默认接口地址为 `https://staging-api.hskmock.com`。首页可修改创建/查询接口的 HTTPS 域名；现有考试 H5 的 API 地址仍由部署的网页配置，须与所选接口环境一致。不会通过未经约定的 URL 参数修改 H5 环境。

默认原生接口注入的完整开发文档见 [App 与 WebView 考试页开发对接文档](docs/native-bridge-development.md)，包含桥接口、H5 请求示例、签名规则、事件、保存退出和联调验收清单。

App 开发人员可先阅读 [App 端职责、WebView 传入值及来源说明](docs/app-side-webview-integration.md)，逐项查看需要准备的参数、取得方式、传入时机和当前实现位置。

## 项目结构

- `app/src/main`：首页配置、对外考试接口、WebView 页面及界面资源。
- `app/src/test`：创建/查询参数、响应解析、URL 编码、签名原文和时间戳检查。
- `app/src/androidTest`：启动、列表导航、网页恢复、音频与存储测试。
- `gradle` 与根目录 Gradle 文件：构建工具和依赖配置。
- `scripts`：故障设备日志采集工具。

`build`、`.gradle`、`.kotlin`、IDE 缓存和测试日志均为本机生成文件，已加入忽略规则。
清理后首次构建会重新生成缓存及 APK。

## 运行

首次克隆后，将 `integration.example.properties` 复制为 `integration.local.properties`，填写同一租户的 `integration.appId`、`integration.appKey`。本机配置已加入 `.gitignore`，不要提交实际密钥或 token。原生“接入配置”修改 appId/appKey；新首页通过创建接口获取场次，不需要预先填写固定场次 token。示例文件中的六个 token 仅用于旧场次兼容及测试。

未配置本机文件时使用无效占位值，仅供编译和代码检查；实际联调必须配置有效凭据。

1. 在 Android Studio 选择 `debug` 构建并运行，或执行 `./gradlew.bat assembleDebug` 后安装 `app/build/outputs/apk/debug/app-debug.apk`。
2. 启动 App，首页完整显示本次提供的测试 `appId`、`appKey`。在“接入配置”内可查看和修改；已保存的自定义凭据优先，点击“使用本次测试凭据”可恢复预置值，再点击保存。
3. 设备开启自动校时并连接互联网，填写首页参数，按需查询等级、模拟卷和权益，点击“创建考试并进入”。无需启动电脑服务、连接同一局域网或授予本地网络权限。
4. 在首页选择“URL 参数”“JS 注入”“原生接口注入”。考试页顶部联调栏默认隐藏，点击右上角“工具”可展开查看 URL、时间诊断和传入方式，点击“隐藏工具”收起。展开/收起不会重载 WebView。所选传入方式会保存在本机；切换传入方式时先保存进度，成功后重建 WebView，重新打开同场考试；保存失败可重试、取消或选择仍然切换。

MainActivity 和 ExamActivity 都声明并请求 `screenOrientation="landscape"`。为适配荣耀 Pad MagicOS 10 / Android 16，1.8 保留 compileSdk 37，将 targetSdk 调为 36，并在 application 配置 `android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY=true`，使用 Android 官方提供的大屏方向兼容退出机制。旧版 targetSdk 37 会让 Android 16 大屏忽略方向限制；仅打开系统自动旋转不能让应用锁定横屏。相关行为见 [Android 16 方向兼容配置](https://developer.android.com/about/versions/16/behavior-changes-16)。分屏、桌面窗口及厂商强制窗口策略下，页面仍按实际可用宽度自适应。

1.8 同时隐藏 H5 自身的 HSK 标题栏，回收标题栏预留的顶部空间。支持文档开始注入的 WebView 在首段页面脚本前添加样式，旧组件在加载回调中兼容注入；样式对后续 SPA 渲染仍生效。右上角原生“工具”入口保留。首页和时间诊断显示实际 App 版本、Android 版本及窗口方向，便于远程确认安装的是新包。

1.7 在考试 Activity 恢复到前台时设置 `FLAG_KEEP_SCREEN_ON`，防止答题和听力期间因无操作自动熄屏；离开考试页或切到后台时清除此标记，回到列表后按系统休眠策略执行。此能力不需要 WAKE_LOCK 权限。

debug 和 release 均使用 HTTPS，不允许明文请求；仅 debug 启用 WebView 调试。
按本次联调需求，App 支持凭据明文显示和传入网页。`ExamConfig.defaultCredentials` 的构建默认值来自本机 `integration.local.properties`，公开源码仅保留占位值。App 内修改后的凭据仍使用 Android Keystore 的 AES-GCM 密钥加密后存入应用私有存储，应用已禁用备份。清除数据或卸载会移除自定义配置，重新安装后使用本机文件构建时的默认值。首页已经通过对外建场接口取得新场次参数；固定场次仅保留供旧入口兼容和测试。

## 三种凭据传入方式

| App 选项 | 网页读取方式 | 说明 |
| --- | --- | --- |
| URL 参数 | 从 `location.hash` 的 query 读取 `app_id`、`app_key` | 在原有 `#/exam?...` 后追加，值单独 URL 编码；不会改变 `index.html` 的位置或场次 ID |
| JS 注入 | `window.JxwHostConfig.appId`、`window.JxwHostConfig.appKey` | 支持时在 document start 注入；旧 WebView 在页面开始/完成时兼容注入，并派发 `jxw-host-config-ready` 事件 |
| 原生接口注入 | `JSON.parse(window.JxwBridge.getCredentials())` | 返回 `{ appId, appKey }`，通过 `addJavascriptInterface` 在页面脚本运行前注册 |

`window.JxwBridge.getTransferMode()` 分别返回 `url`、`javascript`、`native`。只有 URL 模式在地址携带凭据，只有 JS 模式生成 `JxwHostConfig`，只有原生接口模式的 `getCredentials()` 返回凭据（其他模式返回 `{}`）。

当前部署的线上考试页仍通过 `JxwBridge.sign()` 签名，因此三种模式均保留现有签名、保存和页面事件接口。此开关切换的是原始凭据的传输渠道，线上试卷无需修改即可继续使用宿主签名。需要直接读取凭据的 H5 可按表中的接口接入。

## 加载 URL 与 timestamp 错误排查

点击考试页右上角“工具”，即可显示当前加载的完整 URL，可选中或点击“复制 URL”。URL 模式包含实际传入的 `app_id/app_key`，JS 与原生接口模式不追加这两个参数。工具栏默认收起，以便试卷使用完整内容区域。

“时间诊断”显示设备日期时间和时区、签名时间来源、服务器与设备的秒数差，以及最近一次签名的实际 `timestamp` 和完整 API 地址。试卷接口初始化失败会单独标为“试卷接口初始化失败”，保留服务端原始错误，区别于网页资源加载失败。

截图中的 `Invalid argument timestamp` 表示接口拒绝了签名时间戳。当前实现始终传 10 位 Unix 秒，同一个字符串同时用于签名原文和请求头。仅显示时区不同不会改变 Unix 秒；设备日期或绝对时间不正确才会造成偏差。

遇到含 `timestamp` 的初始化错误，App 自动通过同一 API 域名的 HTTPS HEAD 响应读取不缓存的 `Date`，校准宿主签名时钟并重试一次，避免循环重载。校准后用单调计时推进服务器时间，设备时间随后修改不会影响签名；校准只保留在当前进程内，6 小时后失效。不会修改系统时间、关闭证书校验、交卷或修改场次数据。

自动校时失败或重试后仍失败时，可点“校时并重试”，或在“日期和时间设置”开启自动校时。正常考试中的“检测服务器时间”只校准签名时钟，不重载试卷。时间接口根路径即使返回 HTTP 404，只要有有效且未缓存的 `Date` 仍可用于校准；重定向、缺少 Date、缓存 Age 大于 0 或过慢的响应不会被采用。

2026-10-29 的只读接口验证：同一组本次提供的凭据，使用当前 Unix 秒读取 HSK1 试卷成功（`errcode=0`）；把时间戳调慢一小时且重新正确签名后，返回 `errcode=6 / Signature verification failed, Invalid argument timestamp`。这确认时间偏差能复现截图中的错误；故障设备的实际偏差需查看新版时间诊断。

## 宿主桥

1.6 根据《模拟考试接口(3)(1).md》补充错误码和处理提示：`6` 鉴权失败、`3008` 场次归属、`3016` 已交卷、`3025` 凭证与租户不匹配。保留接口原始错误；仅鉴权时间戳错误才自动校时。签名案例、请求头和续考字段的核对结论见 [接口文档核对](docs/api-contract-review.md)。

WebView 在 `loadUrl` 前通过 `addJavascriptInterface` 注册 `window.JxwBridge`，首段业务脚本即可调用，无需等待页面加载完成。

- `sign(payload)` 同步返回 JSON 字符串。读取大写 `method`、原始 `requestUrl`、原始 `body`，按六行 `method\nrequestUrl\nbody\ntimestamp\nnonce\nappKey` 计算 UTF-8 MD5 大写值；空 body 保留空行，末尾不加换行。
- `timestamp` 每次取当前 Unix 秒；已校时时使用 HTTPS 校准时钟，否则使用设备时钟。`nonce` 每次生成 16 位字母数字串，返回小写字段 `appid`、`timestamp`、`nonce`、`sign`。不要关闭设备自动校时，也不要使用文档里的固定时间戳。
- 签名限定当前场次的 `/mock/open/exams/<ID>/...` 路径，WebView 导航限定测试站考试入口。签名过程不调用 `evaluateJavascript`；原始凭据通过所选的独立渠道提供。
- `notify(event, payload)` 读取 JSON 字符串，按场次 ID 处理事件：`exam_init_failed` 显示接口 `errmsg`，`answer_save_failed` 提示检查网络，`exam_submitted` 提示已交卷，`request_close` 关闭页面。宿主不解析成绩，也不打开成绩页。
- 系统返回先调用 `window.JxwExam.saveProgress()`，通过原生桥的 `progressSaved` 回调等待 Promise 完成后关闭。保存失败或超过 10 秒时可以重试或选择直接离开；返回流程不调用交卷接口。页面还未挂载保存函数时直接关闭。
- 切到后台时也尝试保存；系统强制停止进程仍需依赖考试页的自动保存和服务端续考记录。

## 已实现

- JavaScript、DOM Storage、Cookie、免手势播放音频、宽视口和自适应页面布局。
- 首页提供连接配置、创建参数、等级/模拟卷/权益查询及创建后进入考试。
- 考试内系统返回保存后关闭 WebView 并回到列表，不回退网页历史，不调用交卷接口；列表内返回退出 App。
- 不清除 WebView 存储，同场次重新打开时由考试页面恢复已保存的答案。
- 横竖屏切换和窗口尺寸变化保持当前 WebView；Activity 重建时恢复网页历史。
- 主页面加载失败显示重试入口，图片/音频等子资源失败不遮住整张试卷。
- 场次参数缺失时返回考试列表；系统网页组件无法初始化时显示提示。
- 网页渲染进程异常退出时保留考试 Activity，重试会创建新的 WebView。
- 原生凭据显示和配置、三种传入方式及手动切换、宿主签名桥、四类页面事件、异步保存关闭流程。

## 联调验收

1. 确认首页凭据与本次联调提供的值一致，分别选择三种传入方式；填写相应级别、试卷和用户参数后创建考试，确认打开对应级别标题和题目，而非“缺少考试参数”“当前环境无法完成签名”或“验签失败，无效参数 timestamp”；返回后仍显示考试列表。
2. 听力可以自动播放；若网页提示点按播放，点按后可以出声。
3. 作答一道题，按系统返回关闭，再打开相同场次，确认答案保留。
4. 确认返回关闭后仍能进入同场考试，考试没有变成已交卷。
5. 横竖屏切换，确认页面按可用宽度切换答题卡/底部操作栏且不重新开始听力。
6. 考试中点击“工具”再切换传入方式，确认先保存后重载，答案恢复；返回首页确认保留刚选的方式。只展开或收起工具栏时，页面不重载、答题状态不变。

构建与检查：`./gradlew.bat assembleDebug lintDebug testDebugUnitTest`。
构建需要 JDK 25；Android Studio 使用随附的 JBR。在本机 PowerShell 中可先执行 `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'`，避免终端默认 JDK 17 触发额外的 JDK 下载。
连接安卓设备或启动模拟器后可运行 `./gradlew.bat connectedDebugAndroidTest`。
自动化使用本地 HTML 和测试凭据验证三种传入渠道及特殊字符、配置显示和加密存储、模式切换、桥在首段脚本可用、签名字段、异步保存成功/失败、页面事件、音频与存储；实际题目、音频和服务器保存进度需按上述步骤联调。
如果设备测试任务受 UTP 依赖下载影响，可先构建 `assembleDebug assembleDebugAndroidTest`，再用 ADB 安装两份 APK 并执行 `adb shell am instrument -w com.example.nine_scholars_king_test.test/androidx.test.runner.AndroidJUnitRunner`。

## 远程设备闪退排查

点击图标后的首页不自动访问考试服务。只有点击查询或创建按钮才请求接口，创建成功后打开 WebView。
目前未取得故障设备日志，不能将首页启动退出归因于网络或网页组件。

在能连接故障设备的电脑上安装 Android platform-tools，复现退出后执行：

```powershell
powershell -File .\scripts\Collect-AppDiagnostics.ps1 -AdbPath "D:\Android\Sdk\platform-tools\adb.exe"
```

ADB 路径按当地安装位置修改。多台设备连接时加 `-Serial 设备序列号`。
结果保存在 `diagnostics`，包含设备型号、Android 版本、已安装 App 版本和系统崩溃日志。
脚本只读取诊断信息，不清除应用数据、不重新安装、不自动上传；系统崩溃日志可能包含其他应用的异常，分享前请检查内容。

## 定制设备没有可用 WebView

若进入考试提示系统网页组件启动失败，且开发者选项中的“WebView 实现”列表为空，
请让设备厂商检查固件里的 WebView 提供程序。列表为空不能单独区分组件缺失、被禁用或未通过校验。
普通浏览器能打开网页，也不代表它能作为这台设备的 WebView 提供程序。

1. 在连接该设备的电脑执行 `adb shell dumpsys webviewupdate`，查看当前组件、候选包和无效原因。
2. 同时执行 `adb shell getprop ro.build.version.release` 和 `adb shell getprop ro.product.cpu.abilist`，确认 Android 版本与 CPU 架构。
3. 由固件厂商恢复或更新系统认可的 WebView 包，并检查包名、签名、版本、架构及各用户的启用状态；必要时提供修复固件。
4. 修复后重启设备，确认系统能选中有效 WebView，再重新打开考试。

1.2 版的失败页面显示 Android 版本、架构、当前网页组件及最内层异常，并提供“网页组件设置”入口。
采集脚本也会保存 `webview-provider.txt`、`cpu-abis.txt` 和 `webview-startup-log.txt`，便于远程交给厂商定位。
这些诊断功能不能替代固件中的网页组件。
