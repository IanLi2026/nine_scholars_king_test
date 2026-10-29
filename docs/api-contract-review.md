# 模拟考试接口文档核对

核对日期：2026-10-29。参考桌面上的《模拟考试接口(3)(1).md》，对照当前安卓宿主及线上考试页公开构建代码。

## 签名与时间戳

文档中的两个签名案例按 UTF-8 原文独立复算，均一致：

| 案例 | 文档与复算得到的 MD5 |
| --- | --- |
| GET 试卷，无 Body | `F300D23B6255C9A402AC604AEC720E4E` |
| POST 创建场次，文档紧凑 JSON | `ACD09BD2207A8D3D3CAEAA9F6C8AEEAB` |

当前宿主保留六行顺序、空 Body 的空行、RawQuery 的顺序与编码、JSON 正文的字段顺序与空白；末尾不添加换行。GET 示例已加入实际 `ExamSigner` 的回归测试；创建场次 POST 示例在本地复算，没有调用创建接口。

文档明确 `timestamp` 是 Unix 秒，与服务端差不超过 5 分钟，鉴权失败是 `errcode=6`。这与此前只读联调一致：当前时间读取试卷成功，调慢一小时并正确重新签名后返回 `Invalid argument timestamp`。现有 1.5 的时间诊断、HTTPS 校时和一次重试方案因此继续适用。

示例里的 `1692274431`、示例 appid/appKey 只用于算法核对，不替换用户提供的本次联调凭据。每次签名继续生成新的 16 位字母数字 nonce，不缓存或重复使用旧 sign。

## 已核对的请求细节

线上公开构建文件 `js/vendor-DnQrGPuh.js` 的请求构造与文档一致：

- 只发送鉴权头 `appid/timestamp/nonce/sign`，开放 API 的请求头、query、Body 不包含 appKey。
- query 用原有数组顺序编码；题目接口是 `exam_token` 在前、`topic_type` 在后。
- JSON 正文先序列化一次，同一份字符串用于签名和发送。
- 有 JSON Body 才添加 `Content-Type: application/json`。交卷调用不传 Body，XHR 适配器在 data 为 undefined 时移除 Content-Type。
- 保存时包含 `count_down/audio_index/audio_current_time/anchor_index/current_question_id/abbreviation_state` 等数字进度，避免省略导致服务端清空进度。
- 根据 `errcode === 0` 判断成功。此前实际成功响应的 errmsg 为 `Success`，与文档描述的空字符串不同，当前处理兼容这种差异。

这些请求细节核对基于公开前端代码；本次没有创建场次、保存真实答案或发起交卷。

## 1.6 补充的错误提示

| 错误码 | 原生错误页提示 |
| --- | --- |
| 6，含 timestamp | 保留原始错误和错误码，自动校时重试一次 |
| 6，其他鉴权错误 | 核对 appId/appKey 与租户凭据有效期 |
| 3008 | 场次不存在或不属于当前租户 |
| 3016 | 本场已交卷，需要宿主创建新场次 |
| 3025 | 核对同场 examination_id、exam_token 与 appId 的归属 |

未知错误码也保留原始 code 和 errmsg。带其他业务错误码的消息即使提到 timestamp，也不触发自动校时。

## 场次生命周期与当前范围

当前测试壳使用用户提供的六个固定场次，场次 ID 始终保留为字符串。返回只保存、不交卷。交卷后原场次不能重新加载试卷，重复点击同一固定链接无法开始新考试。

完整宿主后续可按文档中的外部用户查询模拟试卷：有未交卷的 examination_id/exam_token 时优先续考；没有时再用稳定的 request_id 创建新场次，避免重复扣次。本次没有扩展动态建场、权益、用量和成绩功能。

用户此前明确要求保留的 URL、JS、原生接口三种传入方式属于测试壳的凭据传输扩展。三种方式调用开放 API 时仍统一走宿主签名，不能用在网页地址里传 appKey 来替代接口鉴权。
