**项目概览**

- 名称：`douyinTV`，Android Kotlin 应用，包名 `com.example.douyintv`
- 目标：在电视/遥控器场景浏览并播放抖音推荐视频流
- 构建：`compileSdk 34`、`minSdk 21`、`targetSdk 34`、`jvmTarget 1.8`
- 依赖：`androidx.media3:exoplayer/ui/common`、`retrofit2`+`converter-gson`、`okhttp3` 日志拦截、`kotlinx-coroutines-android`、`glide`

**功能与流程**

- 播放器初始化：`MainActivity` 中创建 `ExoPlayer`，绑定 `PlayerView`，设置 `REPEAT_MODE_ONE` 与 `playWhenReady`
- Feed 拉取：通过 `NetworkClient.api.getFeed(...)` 访问 Douyin `aweme/v1/web/tab/feed/` 接口
- 数据解析：`GsonConverterFactory` 将响应映射到 `FeedResponse/Aweme/Video/BitRate/PlayAddr` 等模型
- 视频 URL 选择：优先选择 `bit_rate[0].play_addr.url_list` 中含 `www.douyin.com` 的地址，若无则取首个地址
- 分页策略：当当前位置距离末尾 ≤ 2 时自动 `loadFeed()` 拉取下一页
- 交互控制：
  - `DPAD_UP/DPAD_DOWN` 切换上/下一条视频
  - `DPAD_LEFT/DPAD_RIGHT` 快退/快进，采用去抖合并 seek；底部显示时间轴与当前位置/总时长（播放状态下自动隐藏）
  - `DPAD_CENTER/ENTER` 播放/暂停切换；暂停时展示居中“暂停”标识，并常驻显示时间轴与当前时间（恢复播放后隐藏）
  - 每条视频开始播放的前 3 秒：在时间轴上方显示“标题 · 日期（M月d日）”，日期来自 `aweme_list[n].create_time`（服务端为秒级时间戳）；标题来自 `aweme_list[n].caption`（缺省时回退到 `desc`）。暂停时该信息与时间轴一起显示，恢复播放后隐藏
- 反馈与状态：`ProgressBar` 显示加载；异常通过 `Toast` 提示；`isLoading` 防重入

**网络层**

- Base URL：`https://www.douyin.com/`
- 拦截器：统一追加浏览器相关头、`Referer` 和较长的 `cookie`；日志拦截器设为 `BODY`
- 重要参数：`aweme_pc_rec_raw_data`、`msToken`、`a_bogus`、`x-secsdk-web-*`、`uifid` 等（当前在 `MainActivity.loadFeed` 中硬编码）

**数据模型**

- `FeedResponse`：`aweme_list`
- `Aweme`：`aweme_id`、`desc`、`video`
- `Video`：`bit_rate`（列表）、`play_addr`
- `BitRate`：`play_addr`
- `PlayAddr`：`url_list`
- 所有模型使用 `@SerializedName`，依赖 Gson 反射，需在混淆中保留这些类与属性

**UI 结构（概述）**
- `activity_main.xml` 包含 `PlayerView#player_view` 与 `ProgressBar#loading_bar`
- 入口 `MainActivity`：负责播放器生命周期、数据加载与按键事件分发
- 新增 UI：暂停标识 `TextView#pause_overlay`（居中）；时间轴容器 `LinearLayout#time_bar_container`（缩小高度与字号），含 `DefaultTimeBar#time_bar` 与 `TextView#time_text`
- 标题信息独立悬浮：`LinearLayout#info_overlay_container`（不在时间轴容器内），包含两行 `TextView#info_title` 与 `TextView#info_date`。根据时间轴显示状态动态调整与时间轴的固定间距

**构建与发布**
- Release 混淆：已启用 `minifyEnabled true`，并引用默认优化规则 + 项目规则
- 规则文件：`app/proguard-rules.pro`
- 快速构建：
  - 调试构建：`./gradlew app:assembleDebug`
  - 发布构建：`./gradlew app:assembleRelease`
  - 混淆映射：`app/build/outputs/mapping/release/mapping.txt`

**播放优化**
- 硬件加速：在 `AndroidManifest.xml` 启用 `android:hardwareAccelerated="true"`，并通过 `PlayerView` 使用 `surface_view` 提升视频渲染性能
- 预加载下一条视频：`playVideo(index)` 会构建两条播放列表（当前 + 下一条），`DPAD_DOWN` 在有预加载时直接 `seekToNextMediaItem()` 并滚动追加下一条，缩短切换等待时间

**ProGuard/R8 项目规则（摘要）**

- 保留属性：`Signature, RuntimeVisibleAnnotations, AnnotationDefault, EnclosingMethod, InnerClasses`
- 保留模型：`-keep class com.example.douyintv.data.** { *; }`
- 屏蔽告警：`retrofit2/**`、`okhttp3/**`、`okio/**`、`javax.annotation/**`、`org.checkerframework/**`
- Glide 模块：保留 `AppGlideModule`/`GlideModule` 派生类

**已知限制与建议**

- 硬编码令牌：`msToken/a_bogus/uifid/aweme_pc_rec_raw_data` 等写在代码中，具有过期与合规风险，建议改为外部配置或动态获取
- 域名筛选：仅挑选 `www.douyin.com` 域，部分视频可能需要其他可用域名或回源策略
- 错误处理：当前以 `Toast` 为主，可增强重试/退避与空列表占位
- 图片依赖：已引入 `Glide`，目前未在 UI 中使用，可后续用于封面/头像加载

**扩展方向**

- 参数管理：将敏感参数迁移到 `gradle.properties` 或安全存储，运行时注入
- 列表浏览：引入 `RecyclerView` + `SnapHelper` 实现更自然的纵向视频流
- 缓存与预加载：对下一条视频进行预加载以减少等待
- 错误与日志：统一错误上报与日志采样，便于定位线上问题

**API 速览**

- `DouyinApi.getFeed(...)`：GET `aweme/v1/web/tab/feed/`，含设备/浏览器/网络能力与鉴权相关 Query 参数
- 返回体：`FeedResponse`（`aweme_list`），每条 `Aweme.video.bit_rate[*].play_addr.url_list` 提供可播放地址

**项目规则（开发约定）**

- 修改/新增数据模型时，若需 Gson 反射，确保 `app/proguard-rules.pro` 保留对应类
- 所有 UI 更新在主线程进行（已用 `withContext(Dispatchers.Main)`）
- 网络参数与令牌不得硬编码到源码，改为配置化或动态生成
- 播放地址挑选逻辑需保证健壮性，必要时引入兜底域名或可用性检测
