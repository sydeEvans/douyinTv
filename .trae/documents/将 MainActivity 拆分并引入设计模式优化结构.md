## 总体目标
- 将臃肿的 `MainActivity` 按职责拆分为可维护的模块，降低耦合与圈复杂度。
- 引入合适的设计模式（Strategy、State、Observer、Facade）以提升可扩展性与可测试性。
- 保持现有 GSY+Ijk 播放栈与功能不变，逐步迁移逻辑。

## 目标结构
### 分层目录与核心文件
- ui/
  - `ui/MainActivity.kt`：仅负责视图绑定、生命周期与按键分发（保留现有布局与控件引用）。
  - `ui/InfoOverlayController.kt`：标题/日期/进度叠层的显示与自动隐藏逻辑（来自 MainActivity.kt:431-449, 640-660, 662-669, 670-684）。
  - `ui/SeekHoldController.kt`：DPAD 长按快进/快退控制（来自 MainActivity.kt:553-586, 588-599）。
- player/
  - `player/PlayerFacade.kt`：统一封装 `StandardGSYVideoPlayer` 的播放、暂停、恢复、seek、回调注册（Facade模式）（来自 MainActivity.kt:191-216, 初始化回调与进度监听：120-139）。
  - `player/PlaybackWatchdog.kt`：加载超时、缓冲卡顿、进度停滞监控（Observer模式监听 PlayerFacade 事件）（来自 MainActivity.kt:232-299）。
  - `player/BitrateSelector.kt`：候选筛选策略（Strategy模式），默认实现保留 HEVC/AVC + 分辨率/码率与码率上限（来自 MainActivity.kt:341-384）。
  - `player/FallbackCoordinator.kt`：封装回退与换源流程（来自 MainActivity.kt:720-776）。
  - `player/Preloader.kt`：下一条 Range 预热（来自 MainActivity.kt:686-718）。
- data/
  - `data/FeedRepository.kt`：封装 `NetworkClient.api.getFeed` 与分页拉取（来自 MainActivity.kt:141-189）。
  - `data/TokenProvider.kt`：已存在，维持（data/TokenProvider.kt）。
- metrics/
  - `metrics/MetricsLogger.kt`：首帧耗时、回退/切源原因等日志输出与开关（来自 MainActivity.kt:778-787）。
- config/
  - `config/PlaybackConfig.kt`：统一 `ENABLE_METRICS_LOG`、`MAX_PREFERRED_BITRATE`、阈值倍率与时间常量。

## 设计模式落地
- Strategy：`BitrateSelector` 支持不同策略注入（默认/保守/高质量）；未来可根据网络估计动态切换。
- State：`PlaybackState`（Idle/Preparing/Playing/Buffering/Error）由 `PlayerFacade` 驱动，并被 `PlaybackWatchdog` 与 UI 观察。
- Observer：`PlayerEvent` 事件总线（接口回调），`Watchdog`/`OverlayController`/`MetricsLogger` 订阅。
- Facade：`PlayerFacade` 隐藏 GSY 细节，对外提供简单 API；后续若替换实现仅需修改 Facade。

## 接口草案（示例）
- `PlayerFacade`
  - `fun prepareAndPlay(url: String)` / `fun pause()` / `fun resume()` / `fun seekTo(ms: Long)`
  - `fun setEventListener(listener: PlayerEventListener)`
  - 发出事件：`onPrepared`、`onFirstFrame`、`onBufferingStart`、`onError(e: Throwable?)`、`onStateChanged(state)`
- `PlaybackWatchdog`
  - `start()` / `stop()`；监听 `PlayerFacade` 事件，按 `PlaybackConfig` 自适应超时并触发回退协调。
- `BitrateSelector`
  - `select(bitRates: List<BitRate>, preferHevc: Boolean): BitRate?`
- `FallbackCoordinator`
  - 输入当前候选与 URL 列表，输出下一候选或下一 URL 并驱动 `PlayerFacade` 重试。
- `Preloader`
  - `warmUp(url: String)`（Range 256KB 读取）。
- `MetricsLogger`
  - `logFirstFrame(ms: Long)`、`logFallback(reason: String)`、`logRetryAlt(reason: String)`、`logSkip(reason: String)`。
- `InfoOverlayController`
  - `show(title: String, dateText: String)` / `hide()` / `showSeek(deltaMs, targetMs, durationMs)`。
- `SeekHoldController`
  - `start(direction: Int)` / `stop()`；对 `PlayerFacade.seekTo` 进行节奏化调用，并更新 `InfoOverlayController`。
- `FeedRepository`
  - `suspend fun loadPage(): List<Aweme>` / 内含令牌处理与错误分流。

## 拆分与迁移步骤
1. 创建 `config/PlaybackConfig.kt`，迁移常量：`SEEK_*`、阈值、自适应倍率、`ENABLE_METRICS_LOG`、`MAX_PREFERRED_BITRATE`。
2. 创建 `metrics/MetricsLogger.kt`，把文件写入逻辑从 MainActivity 移出。
3. 创建 `player/BitrateSelector.kt` 并迁移 `chooseBestCandidate` 逻辑；在 `MainActivity` 处改为依赖该策略。
4. 创建 `player/PlayerFacade.kt`，封装 `StandardGSYVideoPlayer` 的 `setUp/start/pause/resume/seek` 与进度监听；把首帧事件在 Facade 内统一产出。
5. 创建 `player/PlaybackWatchdog.kt`，迁移看门狗协程；监听 `PlayerFacade` 事件以启停与超时处理，调用 `FallbackCoordinator`。
6. 创建 `player/FallbackCoordinator.kt`，迁移 `tryPlaybackFallbackOrSkip` 相关；依赖 `BitrateSelector` 与 `PlayerFacade`。
7. 创建 `player/Preloader.kt`，迁移 `warmUpNext/warmUpUrl`；由 `MainActivity` 或 Facade 触发。
8. 创建 `ui/InfoOverlayController.kt`、`ui/SeekHoldController.kt`，迁移叠层与长按快进快退逻辑。
9. 创建 `data/FeedRepository.kt`，迁移 `loadFeed` 与分页；`MainActivity` 只调用仓库并把结果交给 `PlayerFacade`。
10. 精简 `MainActivity`：保留视图绑定与键盘事件转发，把播放启动/切换、叠层显示与验证开关改为依赖 Facade/Controllers/Repository。

## 校验与回归
- 先跑 `./gradlew assembleDebug` 验证编译。
- 手动验证：起播首帧耗时、长按快进/快退、加载超时与缓冲回退、分页预热。
- 查看 `play_metrics.log` 的首帧与回退指标是否仍然正常记录。

## 风险与兼容
- 保持 GSY+Ijk 不变，Facade 仅包裹其 API；若某设备回调不一致，Watchdog 仍有状态兜底。
- 协程作用域：所有协程使用 `MainActivity.lifecycleScope` 注入，避免泄漏。
- 文件拆分后引用路径与包名统一置于 `com.example.douyintv` 下的子包，避免导入冲突。

## 交付内容
- 新增 10 个左右的类文件，重构 `MainActivity` 至仅负责 UI 层与装配。
- 提供简易 UML 关系说明（可在说明文档或注释块中给出）。

请确认以上拆分与模式方案，确认后我将开始按步骤实施，确保构建通过并进行回归验证。