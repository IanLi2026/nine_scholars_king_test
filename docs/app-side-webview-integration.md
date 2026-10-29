# App 端职责、WebView 传入值及来源说明

- 版本：1.0
- 日期：2026-10-29
- 对应实现：安卓考试测试壳 1.7
- 适用方式：默认原生接口注入，`native`

本文面向 App 开发人员，说明 App 要做哪些工作、什么时候把什么值交给 WebView，以及这些值从哪里来。完整的 H5 接口定义与请求示例见 [开发对接文档](native-bridge-development.md)。

## 1. App 端需要完成的工作

| 阶段 | App 要做的事情 | 完成结果 |
| --- | --- | --- |
| 准备环境 | 配置考试页地址和 API 环境；确认原生模式 | 页面、凭据、场次属于同一环境 |
| 准备凭据 | 读取租户的 appId/appKey，处理本机配置保存及读取失败 | App 有可用的签名凭据 |
| 准备场次 | 取得同一场的 examination_id、exam_token，准备标题和考试类型 | 能拼出考试页 URL |
| 创建 WebView | 开启 JavaScript、DOM Storage、听力播放、宽视口等设置 | 页面运行环境就绪 |
| 保持考试常亮 | 考试 Activity onResume 设置 FLAG_KEEP_SCREEN_ON，onPause 清除 | 前台答题不自动熄屏，离开后恢复系统休眠策略 |
| 注入接口 | 在 loadUrl 前注册 window.JxwBridge | 页面首段业务脚本可以调用 App |
| 打开页面 | 把场次参数放入 hash 路由，调用 loadUrl | H5 得到当前场次参数 |
| 提供签名 | 每次收到 sign 请求时生成 timestamp、nonce 和 sign | H5 得到本次 API 请求的鉴权字段 |
| 处理事件 | 接收初始化失败、保存失败、交卷成功和关闭事件 | App 显示提示或关闭 WebView |
| 退出保存 | 用户返回时调用 H5 保存函数并等待结果 | 保存成功后退出，失败时允许重试或直接离开 |
| 处理时间 | 显示时间诊断，必要时读取 HTTPS 服务器时间校准签名时钟 | 降低设备时间不准造成的验签失败 |

当前 App 提供考试宿主能力。试卷请求、听力、答题、答案保存和交卷请求由 H5 发起。App 不根据答题内容计算成绩。

## 2. 传入 WebView 的值分三个时机

### 2.1 打开考试页时：通过 URL 传入场次参数

| 字段 | 类型 | 当前测试壳的值从哪里来 | 正式宿主的值从哪里来 |
| --- | --- | --- | --- |
| 考试页基础地址 | string | ExamConfig.PAGE_URL 中配置的测试站地址 | 部署与环境配置 |
| examination_id | string | 用户提供的六个考试链接，已配置到 ExamConfig.exams | 创建考试响应，或未交卷考试的续考信息 |
| exam_token | string | 本机 integration.local.properties 中与选中场次配套的 token，经 BuildConfig 供给 | 与 examination_id 同一条创建响应或续考信息 |
| title | string | 所选列表条目的 title，例如 HSK1 | 业务侧确定的展示标题，可参考已选试卷名称 |
| paper_kind | string | 当前固定为 mock | 业务侧选择的类型；本文模拟考创建接口固定为 mock |

当前代码中，用户点击首页条目后，MainActivity 用 Intent Extra `exam_id` 传递选中的场次 ID；ExamActivity 再从 ExamConfig.exams 找到该场次的完整配置。这个内部 Extra 的值与 URL 中的 examination_id 相同。

默认原生模式打开的 URL：

```text
https://staging-public.hskmock.com/hskmock_webview/index.html#/exam?examination_id=<场次ID>&exam_token=<场次凭证>&title=HSK1&paper_kind=mock
```

App 构造地址时保留 `index.html#/exam`，场次 ID 全程保持字符串。标题及其他可能含特殊字符的 query 值需要单独 URL 编码。当前测试条目的标题、ID 和 token 均已按提供的链接配置。

默认原生模式的 URL 不追加 appId/appKey，它们通过后面的原生接口提供。

### 2.2 网页主动读取时：通过 getCredentials 返回原始凭据

| 字段 | 类型 | 来源 | App 交给 H5 的时机 |
| --- | --- | --- | --- |
| appId | string | 租户对接方提供的应用标识；当前从本机保存值或预置配置读取 | H5 调用 getCredentials 时 |
| appKey | string | 租户对接方提供的密钥；当前从本机保存值或预置配置读取 | H5 调用 getCredentials 时 |
| 模式值 native | string | 已保存的传入方式；没有有效保存值时默认 native | H5 调用 getTransferMode 时 |

App 返回的凭据格式是 JSON 字符串：

```json
{
  "appId": "<租户应用标识>",
  "appKey": "<租户密钥>"
}
```

H5 读取方式：

```javascript
const mode = window.JxwBridge.getTransferMode();
const credentials = JSON.parse(window.JxwBridge.getCredentials());
// credentials.appId
// credentials.appKey
```

这是测试壳的主动读取能力，不是在页面加载时自动创建 window.appId 或 window.appKey。只有原生模式且凭据可用时才返回这两个字段，其他情况返回字符串 `"{}"`。

**当前线上考试页正常鉴权直接调用 sign，不需要先读取 appKey。**

### 2.3 每次接口请求前：通过 sign 返回本次鉴权字段

| 返回字段 | 类型 | 值从哪里来 | 是否每次重新生成 |
| --- | --- | --- | --- |
| appid | string | 复制当前签名凭据中的 appId | 通常保持同一租户值 |
| timestamp | string | 当前 Unix 秒；有有效校准时使用服务器校准时钟，否则使用设备时钟 | 是 |
| nonce | string | App 使用 SecureRandom 生成的 16 位字母数字串 | 是 |
| sign | string | App 使用请求信息、timestamp、nonce 和 appKey 计算的 MD5 大写值 | 是 |

返回格式是 JSON 字符串：

```json
{
  "appid": "<租户应用标识>",
  "timestamp": "<当前Unix秒>",
  "nonce": "<本次16位随机串>",
  "sign": "<本次32位MD5大写签名>"
}
```

appId 与 appid 是同一个应用标识：原始凭据接口使用 `appId`，签名返回值及 HTTP 请求头使用小写 `appid`。

App 把这四项返回给 H5；H5 将它们放入考试 API 请求头。不是通过给 loadUrl 配置一次请求头来覆盖后续所有考试 API 请求。

## 3. appId、appKey 具体如何取得

### 3.1 当前测试壳的读取顺序

```text
原生“接入配置”已保存的值
            ↓ 没有保存值时
ExamConfig.defaultCredentials 引用的 BuildConfig 默认值
            ↑ 构建时读取
不入库的 integration.local.properties
```

当前优先级由以下代码确定：

```kotlin
fun loadOrDefault(): HostCredentials = load() ?: ExamConfig.defaultCredentials
```

本机配置中的值来自本次联调时用户提供的 app_id 和 app_key。Gradle 从 integration.local.properties 读取并生成 BuildConfig，ExamConfig.defaultCredentials 再引用为 HostCredentials(appId, appKey)。公开仓库只提供 integration.example.properties 模板和无效占位值，不包含真实密钥或 token。它们不是由设备、学员账号或 WebView 自动生成的。

创建 WebView 时，App 将读取到的凭据绑定到这次创建的 ExamHostBridge。后续 sign 调用使用这组已绑定的值，不是每次都重新读取本机配置；更换凭据时应重新创建桥及 WebView，使新配置生效。

用户在“接入配置”修改并保存后，HostCredentialsStore 使用 Android Keystore 的 AES-GCM 密钥加密保存；之后优先读取这些自定义值。“使用本次测试凭据”按钮会把配置输入框恢复为预置值，仍需点击保存才覆盖本机配置。

没有保存值时才使用默认值。若已有配置解密或读取失败，当前 App 提示配置不可用，不应把读取异常误当作“没有配置”并悄悄切换到其他租户。

### 3.2 正式宿主的凭据来源

appid/appKey 由租户对接方发放，同一租户共用，不按考生单独发放。正式宿主按双方约定的配置与签名方案取得并管理凭据。

现有《模拟考试接口》没有提供“查询或领取 appKey”的接口。创建考试响应也不会返回 appKey。文档中的演示密钥和演示时间戳仅用于核对算法，不作为正式运行配置。

## 4. examination_id、exam_token 如何取得

### 4.1 当前测试壳

本次提供的六个 URL 已经包含建好的场次。当前 App 使用 ExamConfig.exams 中的 examinationId，以及 integration.local.properties 经 BuildConfig 提供的配套 token，不在本机重新创建考试，也不重新生成 token。

同一场的 ID 与 token 应成对使用，并与配置的租户凭据匹配。examination_id 是场次 ID，不能用 paper_id、level_id 或 HSK 等级号代替。

### 4.2 正式宿主：先查续考，再按需创建

以下是正式接入的数据来源说明，不表示当前测试壳已经实现动态建场。

| 业务输入 | 来源 |
| --- | --- |
| external_user_id | 宿主业务系统中的外部用户标识 |
| level_id | 开放等级列表中的 level_id，不直接使用 HSK 等级号 |
| paper_id | 所选等级下的模拟试卷列表 |
| paper_kind | 模拟考创建接口固定为 mock |
| request_id | 宿主业务侧为一次创建考试操作生成并保存的租户内幂等键 |

正式宿主业务侧，例如 App 配套服务端，可以按以下流程取得场次：

1. 取得已授权的等级及试卷信息。
2. 按外部用户查询 `GET /mock/open/levels/{level_id}/papers?external_user_id=...`。
3. 如果选中的试卷条目带有未交卷场次的 examination_id 和 exam_token，使用该对值续考。
4. 没有可续考场次时，由宿主业务侧调用 `POST /mock/open/exams` 创建模拟考。
5. 读取创建响应的 data.examination_id 和 data.exam_token，再交给 App 打开 WebView。

创建请求示例：

```json
{
  "external_user_id": "<宿主用户标识>",
  "level_id": 1,
  "paper_id": 24,
  "paper_kind": "mock",
  "request_id": "<同一次创建操作的稳定幂等键>"
}
```

响应中使用的字段：

```json
{
  "errcode": 0,
  "errmsg": "",
  "data": {
    "examination_id": "<服务器生成的场次ID>",
    "exam_token": "<服务器生成的同场凭证>",
    "paper_id": 24,
    "level_id": 1,
    "paper_kind": "mock"
  }
}
```

当前注入给考试 H5 的桥只签当前场次 `/mock/open/exams/<ID>/...` 下的请求，不提供创建场次、查询等级或权益的通用签名入口。正式宿主的建场能力需要独立实现或由配套服务端提供。

## 5. sign 使用的请求信息从哪里来

App 每次签名还要接收以下三个字段，它们由准备发送请求的 H5 提供：

| H5 传给 App 的字段 | 由谁构造 | App 如何处理 |
| --- | --- | --- |
| method | H5 根据目标接口确定，如 GET 或 POST | 校验为大写 GET/POST，按原值参与签名 |
| requestUrl | H5 拼接 API 路径及已编码 query | 校验属于当前场次，保留顺序与编码参与签名 |
| body | H5 对实际请求正文序列化一次；无正文时传空字符串 | 保留原始字符串参与签名 |

H5 调用示例：

```javascript
const signed = JSON.parse(window.JxwBridge.sign(JSON.stringify({
  method: 'GET',
  requestUrl: `/mock/open/exams/${examinationId}/paper?exam_token=${encodeURIComponent(examToken)}`,
  body: ''
})));
```

App 收到请求后执行：

```text
1. 取得本次使用的 appId/appKey。
2. 取得当前 timestamp，单位为 Unix 秒。
3. 生成新的 16 位 nonce。
4. 按 Method、RequestUrl、RequestBody、Timestamp、Nonce、appKey 六段用 \n 拼接。
5. 对 UTF-8 字节计算 MD5，转换为 32 位大写十六进制。
6. 返回 appid、timestamp、nonce、sign 的 JSON 字符串。
```

无 Body 时仍保留第三段空行；appKey 后不加换行。App 不重排 query，不重新序列化正文，不缓存旧 sign。网页发送请求时使用与签名相同的 URL、原始正文及 timestamp。

开放 API 请求头由 H5 设置为四项鉴权字段。appKey 不作为开放 API 的请求头、query 或 Body 发送。

## 6. timestamp 与 nonce 的来源

### 6.1 timestamp

没有有效服务器校准时，来源是 Android 设备时钟：

```kotlin
val timestamp = (System.currentTimeMillis() / 1000).toString()
```

当前 App 的桥实际读取 HostSigningClock.clock.read().timestamp，其选择规则为：

```text
有有效的进程内服务器时间校准 → 使用校准时钟
没有校准，或校准已过期       → 使用设备当前时钟
```

服务器时间来自 `https://staging-api.hskmock.com` 的 HTTPS HEAD 响应 Date 头。校准后用 SystemClock.elapsedRealtime 推进时间，当前有效期为进程内 6 小时；进程重启后需要重新校准。

timestamp 是 10 位秒级字符串，不是当前时区显示的小时数，也不是 13 位毫秒。签名串和请求头使用完全相同的值。服务器允许的时间偏差不超过 5 分钟。

### 6.2 nonce

来源是 App 的 SecureRandom，每次签名重新生成，字符只取 A-Z、a-z、0-9，总长度 16 位。相同一秒内多次请求仍使用不同 nonce。

## 7. App 原生注入的最小实现顺序

以下片段说明当前项目中的关键顺序，事件和保存处理函数位于 ExamActivity；完整的 WebView 设置及生命周期管理见源码。

```kotlin
val credentials = HostCredentialsStore(this).loadOrDefault()
val exam = ExamConfig.exams.first { it.examinationId == selectedExamId }

val bridge = ExamHostBridge(
    examId = exam.examinationId,
    credentials = credentials,
    onEvent = { event, payload ->
        runOnUiThread { handlePageEvent(event, payload) }
    },
    onProgressSaved = { requestId, success ->
        runOnUiThread { completeProgressSave(requestId, success) }
    },
    transferMode = CredentialTransferMode.NATIVE
)

// WebView 的 JavaScript、DOM Storage、音频等应已配置完成。
webView.addJavascriptInterface(bridge, "JxwBridge")
webView.loadUrl(exam.url)
```

App 需要先注册桥，再调用 loadUrl。sign 在桥线程同步返回，处理 UI、关闭页面等事件时转到主线程。sign 内部不调用 evaluateJavascript，以免造成相互等待。

## 8. 保存和页面事件中的值从哪里来

| 字段 | 来源 | 用途 |
| --- | --- | --- |
| 保存 requestId | App 为本次“等待网页保存”生成 UUID | 交给宿主注入脚本，匹配 progressSaved 回调 |
| 保存 success | H5 的 saveProgress Promise 结果是否严格为 true | App 决定关闭或提示保存失败 |
| 事件 examination_id | H5 当前场次参数 | App 校验是否为当前打开的场次 |
| 事件 status | 交卷接口返回的 scoring/scored | App 记录交卷成功或关闭页面 |
| 事件 errcode/errmsg | API 错误；网络或签名异常也可由 H5 包装 | App 显示错误及必要的处理提示 |
| 答案与进度 | H5 当前作答状态及服务端续考记录 | H5 保存到考试 API，App 不自行生成 |

三个容易混淆的标识应分别处理：

| 标识 | 生成方 | 更新规则 |
| --- | --- | --- |
| 创建考试的 request_id | 宿主业务侧 | 同一次创建操作的网络重试保持不变，避免重复建场或扣次 |
| 签名 nonce | App 签名器 | 每次 API 签名重新生成，包括重试 |
| 保存回调 requestId | App 页面控制流程 | 每次发起宿主等待保存操作时生成，只用于本机回调匹配 |

App 返回流程调用 window.JxwExam.saveProgress()，等待成功后关闭，超时为 10 秒。返回和切后台不主动交卷。当前壳在页面尚未注册保存函数时会直接关闭，因此 H5 需要及时挂载该函数。

status 由服务器决定，App 不自行把考试改成 scored，也不把按返回视为已交卷。收到有效的 exam_submitted 后，App 标记已交卷；收到有效的 request_close 后关闭考试页。

## 9. App 端交付核对表

- [ ] 凭据来源已确定，appId/appKey 与当前环境、租户一致。
- [ ] 场次 ID 和 token 来自同一场；当前测试壳与正式动态建场方案的区别已明确。
- [ ] examination_id 保持字符串，不用试卷 ID 或等级号替换。
- [ ] URL 只放当前原生方式需要的场次参数，保留 index.html#/exam。
- [ ] WebView 创建后先注册 JxwBridge，再加载网页。
- [ ] getCredentials 返回 JSON 字符串；sign 另外返回本次四项鉴权字段。
- [ ] 每次 API 请求前重新签名，timestamp 使用秒、nonce 重新生成、原文不改写。
- [ ] UI 及页面事件在主线程处理，签名函数不反向等待网页执行脚本。
- [ ] 返回保存、失败重试、交卷后关闭及时间戳错误处理已接入。
- [ ] 横屏、工具栏默认隐藏、URL 及时间诊断入口可使用。

## 10. 当前取值实现位置

| 文件 | 与“值从哪里来”相关的实现 |
| --- | --- |
| [ExamConfig.kt](../app/src/main/java/com/example/nine_scholars_king_test/ExamConfig.kt) | 页面/API 地址、用户提供的六个场次及 BuildConfig 中本机配置的引用 |
| [integration.example.properties](../integration.example.properties) | 本机集成配置模板，复制后填写凭据及 token；实际本机文件不入库 |
| [HostCredentialsStore.kt](../app/src/main/java/com/example/nine_scholars_king_test/HostCredentialsStore.kt) | 优先读取本机保存凭据，没有值时使用默认配置 |
| [MainActivity.kt](../app/src/main/java/com/example/nine_scholars_king_test/MainActivity.kt) | 用户选择场次，通过 Intent Extra 传给考试 Activity |
| [CredentialTransfer.kt](../app/src/main/java/com/example/nine_scholars_king_test/CredentialTransfer.kt) | 当前传入方式及首次默认 native |
| [ExamActivity.kt](../app/src/main/java/com/example/nine_scholars_king_test/ExamActivity.kt) | 打开页面、注入桥、保存回调标识、事件与校时流程 |
| [ExamHostBridge.kt](../app/src/main/java/com/example/nine_scholars_king_test/ExamHostBridge.kt) | 原始凭据返回、接收 H5 签名参数、返回四项鉴权字段 |
| [ExamSigner.kt](../app/src/main/java/com/example/nine_scholars_king_test/ExamSigner.kt) | 随机 nonce 和 MD5 sign 的生成 |
| [SigningClock.kt](../app/src/main/java/com/example/nine_scholars_king_test/SigningClock.kt) | timestamp 使用设备时间还是校准时间 |
| [ServerTimeSync.kt](../app/src/main/java/com/example/nine_scholars_king_test/ServerTimeSync.kt) | HTTPS Date 校时样本的取得 |

本文只描述默认原生方式的 App 端职责，不修改运行代码，也不增加当前测试壳尚未实现的建场功能。H5 调用、完整事件字段和联调示例参见 [开发对接文档](native-bridge-development.md)，接口原始规则核对见 [接口文档核对](api-contract-review.md)。
