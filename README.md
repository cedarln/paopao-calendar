# 泡泡日历（paopao-calendar）

一个把「日历 + 待办 + 习惯 + 专注 + 纪念日」放在同一个应用里的 Android 客户端。
纯本地：不联网、无账号、数据只在设备上。

- 语言/UI：Kotlin + Jetpack Compose（Material 3）
- `applicationId`：`com.paopao.calendar`
- SDK：minSdk 24 / targetSdk 34 / compileSdk 35
- 版本：0.1.0（初版）

## 功能

| 模块 | 说明 |
| --- | --- |
| 日历 | 月 / 周 / 日三档视图，固定 6 行网格避免滑动时高度跳动；农历、节气、法定节假日与节日标签；月视图的外观/配色/内容三个轴可独立设置 |
| 日程 | 全天或定时、跨天、重复（每天/工作日/每周几/每月几号/每年/农历每年）、提醒（准时到提前一周）。「改这一次还是改所有次」由读取路径展开重复规则支撑，库里只存一条母记录 |
| 事项清单 | 四象限矩阵（重要度 × 紧急度）、列表、搜索与回收站（软删除保留 30 天）；象限是派生值，不单独存 |
| 习惯 | 每周目标、连续天数（今天没打卡不算断）、周进度点、归档 |
| 专注 | 番茄式计时，工作/休息时长可设，中断有记录 |
| 纪念日 | 倒数天数、按公历或农历每年重复；农历纪念日每年反查公历落点 |
| 桌面插件 | 5 个 Glance 插件：今日待办、四象限、月视图事项密度、习惯打卡、纪念日倒数。勾选与打卡可在桌面上就地完成 |
| 提醒 | AlarmManager 精确闹钟 + 通知；开机与更新后重排，拿不到精确闹钟授权时退化为非精确而不是静默失效 |

## 隐私

自己声明的权限只有这几项，且都不涉及数据外传：

- `POST_NOTIFICATIONS`（提醒通知，Android 13+ 运行时申请一次）
- `VIBRATE`（通知振动）
- `RECEIVE_BOOT_COMPLETED`（重启后重排闹钟）
- `SCHEDULE_EXACT_ALARM`（精确提醒；用户可在系统设置里撤销，撤销后降级）

最终 APK 的合并清单里还会多出两条，来自依赖而非本项目：

- `WAKE_LOCK` — 桌面插件把 `androidx.work` 带进来，WorkManager 跑后台任务时要拿部分唤醒锁。硬移除它换来的是运行期 `SecurityException`，所以保留。
- `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（`signature` 级）— `androidx.core` 为注册未导出接收器自动加的，不授予第三方应用。

`ACCESS_NETWORK_STATE` 和 `FOREGROUND_SERVICE` 同样由 `androidx.work` 带入，对纯本地应用没有意义，已在清单里用 `tools:node="remove"` 摘掉。

**刻意不申请**：`INTERNET`、`READ_PHONE_STATE`、`QUERY_ALL_PACKAGES`、`READ_MEDIA_*`、`RECORD_AUDIO`、`USE_EXACT_ALARM`、`READ_CALENDAR` / `WRITE_CALENDAR`。
与系统日历互通不在初版范围内，因此不申请日历读写权限。

## 构建

需要 JDK 17 与 Android SDK（`local.properties` 里配 `sdk.dir`，该文件不入库）。

```bash
./gradlew assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM 单元测试
```

安装到设备：`adb install -r app/build/outputs/apk/debug/app-debug.apk`

CI（`.github/workflows/ci.yml`）在每次 push 上跑单元测试与 debug 打包，APK 作为构建产物上传。
它不带 `android-actions/setup-android`，因为那个 action 要装的 `tools` SDK 包在 runner 的
cmdline-tools 里已经不存在；镜像自带的 SDK 加上 AGP 的按需补下载就够了。

## 目录结构

```
app/src/main/java/com/paopao/calendar/
├── AppContainer.kt          # 手写依赖图（单模块规模下不引 Hilt）
├── MainActivity.kt          # singleTask + 插件/通知的深链落点
├── data/                    # Room 实体与 DAO、仓储、DataStore 设置
├── domain/                  # 纯 Kotlin 领域层：农历、日历网格、重复规则、象限
├── reminder/                # 闹钟安排、广播接收、通知
├── ui/                      # Compose 界面：calendar / inventory / habit / focus / memorial / more / settings
└── widget/                  # Glance 桌面插件与就地操作回调
```

领域层不依赖 Android，所以农历与网格逻辑在 JVM 上直接测试：`app/src/test/`。

## 测试覆盖

`./gradlew testDebugUnitTest` 当前 44 个用例通过，集中在会算错的地方：

- 2024—2030 春节日期、闰月标记、农历月日数值化、节气与副标签优先级
- 月视图固定 42 格、周起始日切换、今天标记
- 重复规则编解码往返、脏数据退回「不重复」、跨天重复的展开、截止日截断
- 法定节日推导（除夕 + 初一至初三、清明、端午、中秋、国庆、劳动节）
- 象限派生的归边规则

## 已知状态与后续

初版是**编译与单元测试通过**的状态，还没有在真机或模拟器上跑过完整流程，以下是明确未完成的：

- 界面只在代码里成立，实际排版、手势与深色模式需要一轮设备验证
- `assembleRelease` 能跑通（R8 + 资源压缩，未签名产物 2.4MB，对比 debug 包 19MB），但没配签名密钥，这个包装不上设备；CI 只发布 debug 包
- 提醒窗口只排未来 14 天，靠冷启动/开机重排兜住，缺一个前台定时器补漏
- 调休补班数据源留成接口但返回空——放假安排每年由官方发布，硬编码会在下一年变成错误信息
- 与系统日历（ICS）互通、多设备同步、英文文案完善均未开始

## 关于来源

功能形态参考了同类「日历 + 四象限待办」应用，代码为独立实现，仓库内不含任何反编译产物或第三方应用的资源。
