# 泡泡日历（paopao-calendar）

一个把「日历 + 待办 + 习惯 + 专注 + 纪念日 + 训练」放在同一个应用里的 Android 客户端。
数据全在设备上：不联网同步、无账号、无广告统计；唯一的出网入口是设置页上那个必须按下才动的「检查更新」。

- 语言/UI：Kotlin + Jetpack Compose（Material 3）
- `applicationId`：`com.paopao.calendar`
- SDK：minSdk 24 / targetSdk 34 / compileSdk 35
- 版本：0.2.0（数据库 schema 从 1 迁到 2，装了 0.1.0 的设备升级不会丢数据）

## 功能

| 模块 | 说明 |
| --- | --- |
| 日历 | 月 / 周 / 日三档视图，固定 6 行网格避免滑动时高度跳动；农历、节气、法定节假日与节日标签；月视图的外观/配色/内容三个轴可独立设置 |
| 日程 | 全天或定时、跨天、重复（每天/工作日/每周几/农历每年）。「改这一次还是改所有次」由读取路径展开重复规则支撑，库里只存一条母记录 |
| 事项清单 | 四象限矩阵（重要度 × 紧急度）、列表、搜索与回收站（软删除保留 30 天）；象限是派生值，不单独存 |
| 习惯 | 每周目标、连续天数（今天没打卡不算断）、周进度点、归档 |
| 专注 | 番茄式计时，工作/休息时长可设，中断有记录 |
| 纪念日 | 倒数天数、按公历或农历每年重复；农历纪念日每年反查公历落点 |
| 训练 | 计划（部位 + 每周安排 + 动作清单）、30 条动作百科（要领与避坑）、按组推进的工作/休息计时器与语音提示、时长/热量/连续天数统计、热力图；粘一段健身文案可离线正则解析成动作清单 |
| 日历里的训练 | 日视图直接列出当天排到的计划并一键打卡（打过就从待打卡里消失），月视图标记「这天练过」 |
| 桌面插件 | 6 个 Glance 插件：今日待办、四象限、月视图事项密度、习惯打卡、纪念日倒数、今日训练。勾选与打卡可在桌面上就地完成 |
| 提醒 | AlarmManager 精确闹钟 + 通知；开机与更新后重排，拿不到精确闹钟授权时退化为非精确而不是静默失效 |
| 应用内更新 | 设置页查 GitHub 最新 Release → 下载 APK（校验 sha256）→ 拉起系统安装器，全程不跳浏览器 |

## 隐私

自己声明的权限只有这几项：

- `POST_NOTIFICATIONS`（提醒通知，Android 13+ 运行时申请一次）
- `VIBRATE`（通知振动）
- `RECEIVE_BOOT_COMPLETED`（重启后重排闹钟）
- `SCHEDULE_EXACT_ALARM`（精确提醒；用户可在系统设置里撤销，撤销后降级）
- `INTERNET` + `REQUEST_INSTALL_PACKAGES`（只为「应用内更新」服务，见下）

数据不出设备：日程、事项、习惯、专注、纪念日、训练全部写进本机 Room 库，没有账号、没有同步、没有埋点，也没有任何第三方 SDK。

**唯一的网络通道**是 `update/UpdateChecker.kt`，三条边界由 `NetworkBoundaryTest` 守着而不是靠注释自觉：

1. 全工程只有这个文件允许出现网络 API；
2. 只有设置页可以引用它 —— 没有启动时自动检查、没有后台轮询、不接到插件或提醒链路；
3. 它匿名查询 `api.github.com/repos/cedarln/paopao-calendar/releases/latest`，读公开数据，不带 token，不上传任何设备信息。下载走系统 `DownloadManager`，安装走 `FileProvider` 单条 URI 授权；`file_paths.xml` 只开放应用专属外部存储，数据库与偏好设置不在名单上。

代价是这条链路要求仓库可见：私有仓库的 `releases/latest` 匿名访问是 404，所以本仓库是公开的。

最终 APK 的合并清单里还会多出两条，来自依赖而非本项目：

- `WAKE_LOCK` — 桌面插件把 `androidx.work` 带进来，WorkManager 跑后台任务时要拿部分唤醒锁。硬移除它换来的是运行期 `SecurityException`，所以保留。
- `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（`signature` 级）— `androidx.core` 为注册未导出接收器自动加的，不授予第三方应用。

`ACCESS_NETWORK_STATE` 和 `FOREGROUND_SERVICE` 同样由 `androidx.work` 带入，我们自己从不查连通性（更新那一下交给 DownloadManager 判断就够了），已在清单里用 `tools:node="remove"` 摘掉。

**刻意不申请**：`READ_PHONE_STATE`、`QUERY_ALL_PACKAGES`、`READ_MEDIA_*`、`RECORD_AUDIO`、`USE_EXACT_ALARM`、`READ_CALENDAR` / `WRITE_CALENDAR`。
与系统日历互通不在本版范围内，因此不申请日历读写权限。语音提示用系统 TTS，不申请录音权限，也不打包任何音频文件。

## 构建

需要 JDK 17 与 Android SDK（`local.properties` 里配 `sdk.dir`，该文件不入库）。

```bash
./gradlew assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # 有签名材料时产物是 app-release.apk，否则 app-release-unsigned.apk
./gradlew testDebugUnitTest    # JVM 单元测试
```

安装到设备：`adb install -r app/build/outputs/apk/debug/app-debug.apk`

CI（`.github/workflows/ci.yml`）在每次 push 上跑单元测试、debug 与 release 打包，两个 APK 都作为构建产物上传。
它不带 `android-actions/setup-android`，因为那个 action 要装的 `tools` SDK 包在 runner 的
cmdline-tools 里已经不存在；镜像自带的 SDK 加上 AGP 的按需补下载就够了。

### 签名

release 只从 `signing/keystore.properties` 读签名材料，`signing/` 整个目录不入库：

```properties
storeFile=signing/paopao-release.keystore
storePassword=<口令>
keyAlias=paopao-release
keyPassword=<口令>
```

新机器上生成一份：

```bash
keytool -genkeypair -keystore signing/paopao-release.keystore -storetype PKCS12 \
  -alias paopao-release -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASSWORD" -keypass "$PASSWORD" -dname "CN=paopao-calendar, OU=release, O=cedarln, L=Wuhan, C=CN"
```

`-keypass` 必须和 `-storepass` 一致：PKCS12 不支持每条目独立口令，写成两个的话构建时在
`packageRelease` 报 `Given final block not properly padded`，看起来像 keystore 损坏，其实是口令不对。

没有 `signing/keystore.properties` 时 `assembleRelease` 照样构建，只是产出未签名包，装不上设备。
CI 从仓库 Secrets（`PAOPAO_KEYSTORE_BASE64`、`PAOPAO_KEYSTORE_PASSWORD`、`PAOPAO_KEY_PASSWORD`）
把它还原出来，所以 `main` 每次 push 的 Artifacts 里都有可直接安装的签名包；fork 的 PR 读不到
Secrets，退回未签名包但不算构建失败——能不能编译不该取决于私有凭据。

签名方案是 APK Signature Scheme v2；minSdk 24 已经不需要 v1（JAR）签名。

> `signing/paopao-release.keystore` 和口令**只存在本机**。换用同一包名覆盖安装必须要同一个 keystore，
> 弄丢就只能换 `applicationId` 重装。请自行备份到仓库之外。

## 目录结构

```
app/src/main/java/com/paopao/calendar/
├── AppContainer.kt          # 手写依赖图（单模块规模下不引 Hilt）
├── MainActivity.kt          # singleTask + 插件/通知的深链落点
├── data/                    # Room 实体与 DAO、仓储、DataStore 设置、1→2 迁移
├── domain/                  # 纯 Kotlin 领域层：农历、日历网格、重复规则、象限、训练算法与动作库
├── reminder/                # 闹钟安排、广播接收、通知
├── ui/                      # Compose 界面：calendar / inventory / habit / focus / memorial / training / more / settings
├── update/                  # 应用内更新（全工程唯一允许出网的文件）
└── widget/                  # Glance 桌面插件与就地操作回调
```

领域层不依赖 Android，所以农历、网格、时长估算、连续天数、文案解析这些会算错的东西在 JVM 上直接测试：`app/src/test/`。

## 测试覆盖

`./gradlew testDebugUnitTest` 当前 87 个用例通过，集中在会算错的地方：

- 2024—2030 春节日期、闰月标记、农历月日数值化、节气与副标签优先级
- 月视图固定 42 格、周起始日切换、今天标记
- 重复规则编解码往返、脏数据退回「不重复」、跨天重复的展开、截止日截断
- 法定节日推导（除夕 + 初一至初三、清明、端午、中秋、国庆、劳动节）
- 象限派生的归边规则
- 动作库条数与部位分布、时长/热量/连续天数口径、JS 周几编号到掩码的转换、某天是否排课
- 按组展开的工作/休息节拍（末组不休息、`restSec=0` 塌陷、同名动作各自保留休息）
- 正则文案解析：`N 组 M 次`、`N 秒`、组间休息、噪声词与识别不到时的空结果
- 网络边界：只有 `update/UpdateChecker.kt` 碰网络；版本号比较按数字段；只装 `-release.apk`；下载被系统挂起时不伪装成进度

## 已知状态与后续

本版是**编译与单元测试通过**的状态，还没有在真机或模拟器上跑过完整流程，以下是明确未完成的：

- 界面只在代码里成立，实际排版、手势与深色模式需要一轮设备验证
- release 包已签名（R8 + 资源压缩），但 R8 之后的**运行时**行为一次都没验证过——keep 规则只是按 Room/Glance 的常识写的，真机若出现类找不到或插件空白，先怀疑混淆
- 「检查更新」这条链路在开发机上从未真跑过：匿名接口返回、DownloadManager 的挂起态、`FileProvider` 授权给安装器，都需要一次设备验证
- 训练模块没有做老数据导入：微信云开发里 `weekDays` 用 JS `getDay()`（周日=0）、日期是不补零的 `YYYY-M-D`，这两个口径转换已经写进 `TrainingMath` 并有测试，等真做导入时接上即可
- 提醒窗口只排未来 14 天，靠冷启动/开机重排兜住，缺一个前台定时器补漏
- 调休补班数据源留成接口但返回空——放假安排每年由官方发布，硬编码会在下一年变成错误信息
- 与系统日历（ICS）互通、多设备同步、英文文案完善均未开始

## 关于来源

- 日历与待办的功能形态参考了同类应用，代码为独立实现，仓库内不含任何反编译产物或第三方应用的资源。
- 训练模块（动作库、时长与热量估算、连续天数、文案解析、计时节拍）来自作者自己的微信小程序 `fit-miniapp`，逐条重写为 Kotlin；未搬 openid、组队、勋章、周榜与分享海报这些社交部分。
