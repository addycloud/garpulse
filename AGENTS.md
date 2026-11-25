# GarPulse — Agent 交接说明

任何 AI agent 开始工作前必读。包含项目现状、构建发布流程与历史踩坑。

## 项目是什么

Android 健康数据桌面小组件应用（Kotlin + Jetpack Compose + Glance）。
- applicationId `dev.easonhuang.garpulse`，minSdk 28 / targetSdk 36
- 数据源：Health Connect + 佳明 Personal API（国际区/国区，可分别启用并排序，HC 默认最低优先级）
- 多语言（en / zh-rCN），所有新增 UI 字符串必须两处同步加
- 文档注释全英文，代码风格与现有文件保持一致

## 环境与构建（本机 Windows）

- SDK：`D:\DevTools\android-sdk`；构建用 `./gradlew --offline`（依赖已缓存）
- 终端必须走直连：`unset HTTPS_PROXY HTTP_PROXY; export NO_PROXY='*'`（系统代理 127.0.0.1:7890 是死端口）
- debug 构建：`./gradlew :app:assembleDebug --offline`
- 正式包流程：`assembleRelease`（未签名）→ `zipalign -f 4` → `apksigner`（见下）
- apksigner 在 Git Bash 下必须调 `apksigner.bat`（无扩展名脚本不可执行）

## 签名（勿改）

无独立 keystore，历届 release 全部用 debug keystore 签：
```
zipalign -f 4 unsigned.apk aligned.apk
apksigner.bat sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --ks-key-alias androiddebugkey \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --min-sdk-version 24 --max-sdk-version 35 aligned.apk
apksigner.bat verify aligned.apk
```
⚠️ `--min-sdk-version 24` 不能省：省略时 v1 JAR 方案被自动跳过，vivo/三星报"包无效"(-103)。

## 发布纪律

- **不自动发布 release**：CI（ci.yml）每次 push 只产出 debug artifact；release.yml 为手动触发（workflow_dispatch）；gitee-mirror.yml 只镜像代码不发 release。
- 正式 release 必须等用户明确说"发布/推送 release"才做。
- 上传到 GitHub 的 APK 必须**回读比对 SHA-256**（曾发生上传截断导致三星装不上）。`browser_download_url` 有 CDN 缓存，校验要走 asset API 直链（`assets[0].url` + `Accept: application/octet-stream`）。
- GitHub API 用 fine-grained PAT：`C:\Users\Administrator\.workbuddy\github-pat.txt`（仅 garpulse 仓库，缺 Secrets 权限 → repo secret 无法经 API 写入）。
- Gitee 仓库 https://gitee.com/addycloud/GarPulse，需 repo secret `GITEE_TOKEN`（待用户配置）。

## 当前状态（2026-11-25）

- 最新正式 release：**v1.2.6**（tag v1.2.6）
- 测试用预发布：**v1.2.9-dev**（debug，15.06MB，SHA256 7567a2a0…；GitHub release 457567299 / Gitee release 1365117）
- v1.2.8 修复：密钥空输入不再静默（原“保存无任何反应”根因）、key 草稿 rememberSaveable、setApiKey 事务内回读长度并在 UI 校验、持久化 key_op_log（区内小字显示最近操作 + 诊断导出含 datastore 文件清单）、设置→重置数据源配置（resetAll）、garminIntent 维持 Garmin 模式
- v1.2.8 追加：设置→关于→获取最新安装包（正式版/测试版随时可下，双镜像择优）；检查更新/立即同步/区域开关/刷新间隔/定点时间全部有 snackbar 反馈；AppUpdateChecker 的 -dev 通道 GitHub+Gitee 都支持
- 排障抓手：设置→关于→导出诊断日志；「最近操作：」小字；key op log 无 "save ... pressed" 行 = 点击未进 saveKey
- 待办：GITEE_TOKEN secret；正式 v1.2.8 待用户验收后发布（用户明确说“发布”才发）

## 高频踩坑（历史已修，勿重蹈）

1) **`MutablePreferences.remove(Key<Long>)` 会拆箱崩溃**：`remove` 返回 `T`，`Key<Long>` 在调用点被编译成 `Number.longValue()`；键不存在时返回 null → NPE ��� 整个 edit 事务回滚（表现为"密钥保存毫无反应、store 完全没变"）。本项目的 metric_source store 必须用 `it -= key`（minusAssign，返回 Unit），禁止 `it.remove(key)`。已用 dexdump 验证 `setApiKey$prefs$1` 不再含 longValue。

1. `painterResource` 不能加载 AdaptiveIconDrawable（mipmap）→ 图标预览用「圆背景 Box + 前景 vector」组合。
2. Glance 每帧动画都会重跑 provideGlance：`refreshData` 有 `LAST_READ_AT` 10s 快照缓存，勿去掉（曾致刷新条第一下无反应）。
3. `material-icons-core` 不含很多图标（KeyboardArrowUp/Down、Refresh、Schedule、CloudDownload 等）→ 已用 TextButton/Text 或 CloudSync 替代，别直接引用。
4. SettingsScreen.kt 很大，批量文本替换易截断文件；大改前先 `git show HEAD:<path>` 备份尾部。
5. `chrono`/Java 时间：LocalDateTime 间用 `Duration.between`，无 `ChronoUnit.MINUTES.between(LocalDateTime,...)`。
6. dataStore：数据源状态在 `MetricSourcePreferences`（hc/global/cn enabled + order 数组），order 读取必须过 `normalizeOrder`（老安装只有部分 id，否则 ↑↓ 失效）。
7. 小组件点按规则：指标格恒有 clickable（无配置时打开 App 详情页）；背景/图片无目标=无 clickable；空数据状态整卡打开 MainActivity。
8. Edit/Write 工具写文件统一 LF；`.workbuddy/` 目录不要删。
9. 中文字符串资源：values 与 values-zh-rCN 双份，漏一份会在运行时崩。
10. 用户偏好：回复用简体中文；UI/文档面向普通用户，提示要简洁完整。

## 关键文件索引

- 设置页（数据源/更新/显示/导出）：`ui/settings/SettingsScreen.kt`
- 数据源偏好：`data/MetricSourcePreferences.kt`；佳明 API：`data/GarminPersonalApi.kt`（GarminRegion GLOBAL/CN）
- 数据路由：`data/HealthConnectManager.kt`、`data/GarminMetricRepository.kt`
- 小组件核心：`widget/ConfigurableWidget.kt`（渲染+refreshData）、`UniversalWidget.kt`（pin 兜底）、`WidgetPinCallback.kt`、`WidgetDraftStore.kt`、`WidgetUpdateScheduler.kt`（间隔+定点）
- 配置编辑器：`widget/MetricWidgetConfigActivity.kt`
- 更新检查：`update/AppUpdateChecker.kt`；CI：`.github/workflows/*.yml`
- 版本号：`app/build.gradle.kts`（versionCode/versionName，发布前记得 bump）

## 历史细节

按日记录在 `.workbuddy/memory/2026-11-*.md`（11-20 图标/体积/点按，11-22 版本发布/签名/上传，11-24 数据源/更新/发布），需要深挖时按日期读。
