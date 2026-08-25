# FandUI 研究状态与事实记录

> 用途：这是 FandUI 源码/API 追踪与设计设定的**持久工作记忆**，专门保存已确认事实、证据坐标、已否定路线、设计决定和未决问题；它不是只在阶段结束时生成一次的总结。  
> 恢复规则：发生上下文压缩、模型切换或换会话后，必须先完整读取本文件，以“最新且优先级最高的后端决定”和状态词恢复现场，再继续第一个未完成项；不得仅凭旧对话重建结论。  
> 更新规则：每得到一项会影响代码选择的源码/API 事实就立即落盘，不积攒到阶段结束；若临时文件可能消失，至少记录可重取坐标、版本/提交、哈希和关键字面结果。  
> 状态：第一阶段设计已获用户确认并进入实现；公共 API、Core runtime、Skija text-block、PNG/SVG 事务式资源管线、现成 `lwjgl-nanovg`/`NanoVGGL3` OpenGL renderer、Backdrop Blur，以及三版 Fabric runtime/Screen/HUD/input/resource reload bridge 均已实现。共享完整 Demo Screen、纯 Core 交互回归和三版本真实 Demo 运行均已完成；2026-08-23 审计发现的首发 API 正确性问题已经修正，README/API Guide/Javadoc、`0.1.0` 预发布 ABI baseline 和三版本内嵌 API 一致性门禁已经闭合。1.20.1 右半屏纯 Blur HUD 已完成第一批 GPU ROI、mask 与零透明度优化。2026-08-24 横向审计已闭合组件树原子替换、自定义字体 generation/native 环境淘汰、动态 renderer diagnostics、首批交互控件、预设图标和受限 SVG 支持；三个版本共用的 F9 综合测试 Screen 已通过 Core、全量门禁以及 1.20.1/1.21.4/26.2 真实客户端重开和 resize。每帧同步 `glGet*` 导致的 OpenGL stall 已改为三版宿主规范状态交接；关闭诊断断言后的 1.21.4 JFR 中 P50/P95/P99 分别降至 `0.358647/0.699874/0.871980 ms`，诊断断言路径仍保留。Slider 按住连续拖动已改为每帧最新 MOVE + 单个可持续重定向的视觉平滑驱动，测试 UI 不再在每个拖动值上重排状态文字；有效实机 JFR 中动画启动 `41 -> 1`、整页 Layout 帧 `240 -> 2`。Core 事件路由已复用 path/state scratch，监听器缓存命中不再创建复合 key；嵌套派发保持独立状态和回调上下文失效语义。
> 最后更新：2026-08-24 23:02（Asia/Shanghai）

## 0. 快速恢复点

### 当前目标

设计并最终实现 Fabric 客户端 UI 库 Mod **FandUI**：

- 项目名：`FandUI`
- Mod ID：`fandui`
- Maven Group：`cn.fandmc`
- 根包：`cn.fandmc.fandui`
- 所有 Java 类必须位于 `cn.fandmc.fandui` 或其子包
- 目标版本：Minecraft `1.20.1`、`1.21.4`、`26.2`
- 每个版本单独构建和发布 JAR，共享稳定公共 UI API
- 第一阶段设计已于 2026-08-22 获用户确认，工程实现正在进行
- 不执行未经用户要求的 Git commit、分支创建或 push

### 最新且优先级最高的后端决定

| Minecraft | Java | FandUI 渲染后端 | 宿主 |
|---|---:|---|---|
| 1.20.1 | 17 | OpenGL | Minecraft/Blaze3D 当前 OpenGL context 与主目标 |
| 1.21.4 | 21 | OpenGL | Minecraft/Blaze3D 当前 OpenGL context 与主目标 |
| 26.2 | 25 | OpenGL（仅实际后端为 OpenGL 时） | Minecraft 当前 `GlBackend`、OpenGL context 与主目标 |

旧的“1.20.1/1.21.4 也必须使用 Vulkan”和后续“仅 26.2 强制 Vulkan”方案均已废止，旧 `VulkanHost` SPI 与 `MojangVulkanHost262` 也已废止。当前统一内部宿主抽象为 `RenderHost`：

- `Blaze3dOpenGlHost1201`
- `Blaze3dOpenGlHost1214`
- `Blaze3dOpenGlHost262`

三个版本共用一个 OpenGL renderer 实现和各自独立的 Minecraft 状态桥，均复用 Minecraft 当前 context 与主颜色目标，不创建第二窗口、context 或 Swapchain。26.2 沿用 Minecraft 自己的图形后端配置，FandUI 不增加启动参数、不改选项，也不强制支持 Vulkan；仅在实际 `DeviceInfo.backendName() == "OpenGL"` 时启用。Minecraft 的 `DEFAULT` 模式当前先尝试 OpenGL、失败后才尝试 Vulkan；若实际后端为 Vulkan，FandUI 只报告“当前 renderer 不可用”并跳过绘制，不自行切换后端。

选择单一 OpenGL renderer 而不是同时维护 Mojang 高层 GPU renderer，是当前 KISS/YAGNI 决定：26.2 public `DepthStencilState` 不能表达 NanoVG 所需的 front/back stencil winding，而 OpenGL 路径已经能复用主颜色纹理并提供完整 stencil。将来若明确要求 Vulkan 模式可用，必须作为单独设计和验收目标重新评估，不能把当前 GL 实现包装成“后端无关”。

### 当前工程状态与下一步

- 已从核验模板复制 Gradle `9.5.1` Wrapper，四个文件的 SHA-256 与第 4 节记录完全一致；
- 已创建根 Gradle 配置和 8 个计划模块，三版 Java entrypoint、Fabric metadata、required Mixin、共享 OpenGL probe 及其单元测试均已构建；
- 4 个 `hs_err_pid*.log` 的成因已经定位；用户于 2026-08-21 23:46 明确要求清理后已全部删除，根目录复查数量为 `0`；
- 已完成第一轮本地源码、映射、依赖缓存和版本元数据探查；
- 用户指定公共 API 设计参考 `C:/Users/winme/Desktop/FandServer` 中的 FandAPI；只读参考与 FandUI 公共 API 第一版签名草案均已完成，见第 7 节；
- 公共 API 草案已通过第一阶段确认；基础值、style/theme、event payload、component tree、Canvas/resource/text/session/focus/animation contracts，以及 `FandUI.runtime()`、Screen/HUD definitions、`TextController`/`ScrollController` 和首批基础组件均已进入 Java 17 源码；
- 纯 Java `CoreUiRuntime`、Screen/HUD session、组件递归绑定、immutable frame cache、capture/target/bubble、focus、pointer capture、animation 和跨线程/回调内关闭语义已实现；Core 当前 `10 suites / 52 tests / 0 failures / 0 errors / 0 skipped`，并覆盖 renderer 从 available 转 unavailable 时以 `FAILED` 关闭已有 Screen/HUD；
- 26.2 Vulkan D24S8/Stencil internal hook 路线已废止，不进入实现或发布门槛；源码事实保留在 6.4-6.5.1；
- 26.2 public `RenderTarget.getColorTextureView()`、public `GlTextureView.glId()`、OpenGL backend 字面值、target resize 代际、状态缓存边界和 GL debug 入口已确认；当前 GPU go/no-go 是三版 OpenGL 主颜色重挂接、自有 D24S8 和状态恢复运行原型；
- 三版 vanilla 开发客户端在显式 GL probe/state assertion 下均完成最终 GUI hook、初始 attach、resize 后重新 attach、正常停止前资源释放和零 GL error 验证；三版相同 fixture 的最终结果均为 `28 batches / 31 draw calls / 854x480`，分别使用 FBO `2/3/4`；本机 RenderDoc 未安装，实际 OpenGL 抓帧仍未开始；
- 运行时 renderer 已迁移为版本匹配的 LWJGL `NanoVGGL3`；路径回放、复用 layer/mask、渐变缓存、外部文字/图片纹理、Backdrop Blur、状态恢复和 Minecraft 主颜色目标重挂接均在 `fandui-render-opengl`，临时 FUDL/FUBT、自有 JNI/CMake 和 batch shader 已从源码/构建移除；
- 共享 `FandUiDemoScreen` 已实现中英日法混排与 Emoji、圆角半透明 Blur 面板、描边、内置 PNG、至少三层裁剪、Button、TextInput 和 ScrollContainer；仅 `-Dfandui.demo.screen=true` 启用，首次自动打开并可用 `F8` 重开，默认发布运行不注册其资源或按键；
- 共享 `FandUiTestScreen` 已覆盖首批全部控件、预设/inline SVG、SVG resource image、渐变、描边、三层圆角裁剪、Backdrop Blur、滚动与响应式布局；仅 `-Dfandui.test.ui=true` 启用，首次自动打开并由三个 Fabric bridge 分别以 `F9` 上升沿重开；
- Gradle `9.5.1` + Loom `1.17.19` 的最新全量结果为 `42` 个 XML suite、`203 tests / 0 failures / 0 errors / 0 skipped`（历史首批结果为 `37 suites / 159 tests`）；严格 japicmp 检查相对 `0.1.0` 预发布 baseline 只报告已接受的 additive API，未报告 source/binary incompatibility；baseline 不因常规构建自动替换。三个发布 JAR 的内嵌公共 API class 数量随首批控件、diagnostics 和 SVG API 增至 `246`，逐 class SHA-256 仍完全一致。

### 当前实现待办

- [x] 纯 Java runtime/session/focus/animation/event 与失败隔离；
- [x] `UiSceneFrame -> immutable DisplayList -> NanoVGGL3 -> OpenGL` 正式运行链路；
- [x] 三版本 runtime bootstrap、Screen/HUD/input/resource reload bridge（交互 Screen 与 IME 的真实输入覆盖归演示/验收项）；
- [x] 按目标 LWJGL 版本分发 NanoVG native，并由 Skija 平台构件提供文字 native；
- [x] Skija text-block、确定性 fallback、NanoVG 外部文字 image quad 与 OpenGL A8/RGBA texture cache；
- [x] PNG 严格解码、resource generation 事务切换与 OpenGL image texture 生命周期；
- [x] 标准 `Text`、`Button`、`ScrollContainer` 组件；
- [x] 标准 `TextInput` 组件；
- [x] 运行时 renderer 切换到现成 `lwjgl-nanovg`/`NanoVGGL3`，并删除临时 vendored NanoVG、JNI、CMake、FUDL/FUBT 与自有 batch shader；
- [x] 共享演示 Screen/HUD 源码与纯 Core 验证：中英混排、fallback、Emoji、圆角/描边、图片、嵌套裁剪和完整交互；
- [x] 三版本演示 Screen/HUD 真实运行验收；
- [x] API 冻结前修正：style margin/通用字段语义、transform-aware hit-test/focus/clip、Row/Column flex，以及 `Stack`/`ThemeScope`；
- [x] 输入基础补齐：Clipboard、pointer enter/leave、hit-test policy、cursor、完整 TextInput 编辑行为；
- [x] 发布 API 工程化：排除 internal Javadoc/source、补齐公开 Javadoc/可编译示例、建立 `0.1.0` 预发布二进制 baseline 和三版本嵌入 API 字节一致性门禁；
- [ ] 三版本 GUI scale、窗口 resize、资源 reload、IME、滚动及连续运行显存压力验收（resize/reload 已覆盖，IME 与长期压力仍待完成）；
- [ ] RenderDoc 抓帧与 GL debug/state restoration 最终证据；
- [ ] Sodium/Iris/Indium 及 Lithium/FerriteCore/Krypton 的版本组合运行矩阵；
- [ ] Linux/macOS/Windows arm64 等 native classifier、干净机提取与发布验证；
- [x] `0.1.0` 预发布 API baseline、公开 Javadoc/使用文档和严格 source/binary 兼容审计；baseline 只有在明确接受 API 变更时才允许通过 `updateApiBaseline` 更新。
- [x] 单子组件保持“始终恰有一个 child”，并提供失败不丢旧 child 的原子替换语义；
- [x] 将 `registerFont` 发布的 generation 字体快照真正接入 Skija `TypefaceFontProvider`，覆盖 reload、旧 layout raster/editor geometry 和 native 环境淘汰；
- [x] 增加独立、动态、任意线程可读的 renderer diagnostics，不继续膨胀稳定 input capabilities；
- [x] 首批高层控件：Checkbox、Slider、ProgressIndicator、ToggleSwitch、Dropdown，共享 control state、主题 token、键盘/指针语义；
- [x] 预设矢量图标、inline `Icon`/`SvgIcon` 与安全有界 SVG path 解析；
- [x] SVG image resource reload：自动/显式格式提示、worker 栅格化、premultiplied RGBA8、texture key 与原子 generation；
- [x] 三版本 F9 综合测试 Screen：共享实现、Core 回归、三版编译、全量门禁、真实 F9 重开和窗口 resize 均已完成；
- [ ] 横向性能证据：OpenGL 每帧 `glGet*` stall 已移出正常路径，Core route path/state scratch 与 listener cache key 已收敛；`CallbackContext`、hit-test、DisplayList 重建、文字/图片/gradient/blur cache 和长期 native/GPU 生命周期仍待继续量化；
- [ ] 三版本一致性门禁：相同公共 API、PRESS/REPEAT/RELEASE、click count、resize/GUI Scale、reload；真实客户端项目继续单列验收。

## 1. 文档更新协议

每次获得新事实或完成实现时，必须同步更新本文件：

1. 更新顶部时间；
2. 在“API/源码事实表”补充符号、版本、来源和验证方式；
3. 把未知项从“待验证”移动为“已确认”或“已否定”；
4. 设计改变时保留旧结论并标记“已废止”，不能静默覆盖；
5. 在“研究日志”写下实际命令/文件、关键字面结果和结论；
6. 实现开始后更新“工程状态”和“实施进度”。

每条版本相关事实至少尽量包含：

- Minecraft/Fabric/依赖版本；
- 符号 owner、方法名和 descriptor 或源码签名；
- 来源坐标（本地路径、Maven 坐标或上游 commit）；
- 使用的核验方式，例如源码读取、`javap -p -s -c`、JAR metadata、运行测试或 RenderDoc；
- 关键字面结果，以及它对设计的直接约束。

本文件是研究事实的单一来源。最终面向使用者的 README、API 文档和 ADR 可以从这里提炼，但不得反向覆盖这里的证据状态。临时推断必须标为“待验证”，需求必须与“已确认源码事实”分开记录。

状态词只使用：

- **已确认**：由本地源码、映射、构建元数据、依赖源码或实际运行证明；
- **设计决定**：已选定但尚未经过实现验证；
- **待验证**：仍需查证，不能写成事实；
- **已否定**：探查或原型已经证明不可用；
- **已废止**：曾采用，后被更新需求或决策替代；
- **已完成**：代码或测试已实际执行成功。

## 2. 固定产品边界

FandUI 是供其他 Mod 使用的 UI 基础库，不是独立 HUD Mod。目标能力：

- UI 组件树；
- 约束布局；
- 样式与主题；
- 鼠标、键盘、文本、焦点和滚动事件；
- 动画系统；
- Skija 字体排版与 CPU 文字栅格化；
- NanoVG 路径语义；
- 三版本 OpenGL 渲染后端；
- Minecraft Screen 与 HUD 接入。

公共 API 必须与以下具体类型隔离：Minecraft、Fabric、Blaze3D、Skija、NanoVG、LWJGL、OpenGL、Vulkan。

当前固定渲染流程为：

```text
Component Tree
  -> Layout Tree / Snapshot
  -> Immutable DisplayList
  -> NanoVGGL3 Path Tessellation / Command Replay
  -> Skija Text + Image OpenGL Texture Cache
  -> Path Clip / Gradient / Backdrop Blur Auxiliary Passes
  -> OpenGL
  -> Minecraft UI RenderTarget
```

共同约束：

- 使用与目标 Minecraft/LWJGL 对齐的 `org.lwjgl:lwjgl-nanovg` 和 `NanoVGGL3`；不再维护 vendored NanoVG C core、自有 JNI、FUDL/FUBT 或自有 batch shader；
- NanoVGGL3 负责标准路径、填充、描边、图片和矩形 Scissor；FandUI 只以同一 OpenGL context 内的有界临时 layer/mask、渐变查找纹理和 Blur pass 补齐公共 Canvas2D 语义；
- 不包含 Vulkan renderer，也不做 Vulkan/OpenGL 跨 API 合成；
- 不创建独立于 Minecraft renderer 的 Swapchain；
- 所有 GPU 操作都在 Render Thread；
- 使用 premultiplied alpha；
- 正确处理目标色彩空间、Scissor、Stencil、窗口 resize、GUI scale、资源 reload 和设备重建。

### 2.1 可行性结论

**总体结论：架构可行，三版本 OpenGL GPU go/no-go 和首个可运行 MVP 已完成。** 公共 UI、布局、DisplayList、Skija text-block raster、PNG/SVG 资源、现成 NanoVGGL3 renderer、Backdrop Blur 和三版 Minecraft 主目标接入均已有构建、运行或像素读回证据；SVG 资源当前已有 Core 单元像素证据，真实资源包/不同 SVG 资产的客户端验收仍待补充。剩余发布风险集中在 RenderDoc、优化 Mod 组合、长期性能/显存和跨平台验收。

| 范围 | 结论 | 放行条件 |
|---|---|---|
| 纯 Java API/core/layout | 已完成首版 | FandAPI 参考、实现、架构测试、Java 17 consumer、`0.1.0` 预发布 ABI baseline 和三版内嵌 API 一致性均已通过 |
| Skija 文字 | 已完成 Windows x64 首版 | 确定性 fallback、A8/RGBA premultiplied raster、异步去重与真实像素读回已通过；其余平台和压力仍待验收 |
| NanoVGGL3 renderer | 已完成首版 | 三版匹配 LWJGL binding/native；路径、临时 layer/mask clip、多 stop gradient、外部纹理和 Blur 像素/状态回归已通过 |
| 1.20.1 OpenGL | GPU 路径已通过 | 主颜色重挂接、自有 D24S8、resize/state assertion 和真实像素读回通过；优化 Mod/RenderDoc 待验收 |
| 1.21.4 OpenGL | GPU 路径已通过 | 与 1.20.1 相同，FBO `3` 的真实像素读回通过；优化 Mod/RenderDoc 待验收 |
| 26.2 OpenGL | GPU 路径已通过 | 实际 `GlBackend`、FBO `4`、clip-control 和 sampler 状态恢复的真实像素读回通过；RenderDoc 待验收 |

硬性停止条件：任一版本无法复用 Minecraft 当前 OpenGL context 与主颜色目标，或必须创建第二窗口/context、复制主颜色做离屏合成、接管世界 renderer 时，该版本不进入发布矩阵。26.2 实际后端不是 OpenGL 时该次启动不提供 FandUI 绘制；不暗改 Minecraft 设置，也不实现隐式 Vulkan fallback。

## 3. 计划模块与依赖边界

```text
fandui-api

fandui-canvas          -> fandui-api
fandui-core            -> fandui-api + fandui-canvas
fandui-text-skija      -> fandui-api + fandui-canvas + fandui-core
fandui-render-opengl   -> fandui-canvas + fandui-core
fandui-fabric-1.20.1   -> api + canvas + core + text-skija + render-opengl
fandui-fabric-1.21.4   -> api + canvas + core + text-skija + render-opengl
fandui-fabric-26.2     -> api + canvas + core + text-skija + render-opengl
```

图中 `A -> B` 表示 A 直接依赖 B；精确 Gradle 依赖按下表冻结：

| 模块 | 直接项目依赖 | 外部/平台依赖边界 |
|---|---|---|
| `fandui-api` | 无 | 无；纯 Java |
| `fandui-canvas` | `fandui-api` | 纯 Java immutable DisplayList/recording；不依赖 Minecraft/Fabric/native |
| `fandui-core` | `fandui-api`, `fandui-canvas` | 无平台类型；纯 Java |
| `fandui-text-skija` | `fandui-api`, `fandui-canvas`, `fandui-core` | 只在本模块接触 Skija |
| `fandui-render-opengl` | `fandui-canvas`, `fandui-core` | LWJGL OpenGL + NanoVG binding；三版 Blaze3D 状态桥和匹配版本的 NanoVG native 由各版本模块注入 |
| `fandui-fabric-*` | API/core/canvas/text + 对应唯一 renderer | 每版独立 Loom、Minecraft/Fabric、Mixin、metadata 与入口 |

计划模块：

1. `fandui-api`
2. `fandui-core`
3. `fandui-text-skija`
4. `fandui-canvas`
5. `fandui-render-opengl`
6. `fandui-fabric-1.20.1`
7. `fandui-fabric-1.21.4`
8. `fandui-fabric-26.2`

约束：

- Gradle 多模块 + Fabric Loom；
- 不使用 Architectury；
- 不用大量条件编译共享版本代码；
- `fandui-api` 和 `fandui-core` 保持纯 Java；
- 三个 Fabric 模块独立保存映射、Mixin、事件接入和 metadata；
- native 源暂归 `fandui-canvas`，按 OS/CPU/ABI 产出带版本或内容哈希的构件；是否需要单独 native 子模块待构建原型后决定，当前不提前扩模块。

## 4. 已核验构建与依赖事实

核验日期：2026-08-21。

| 项目 | 1.20.1 | 1.21.4 | 26.2 | 状态 |
|---|---|---|---|---|
| Java toolchain | 17 | 21 | 25 | 已确认 |
| LWJGL | 3.3.1 | 3.3.3 | 3.4.1 | 已确认 |
| Fabric Loader | 0.19.3 | 0.19.3 | 0.19.3 | 已确认当前稳定元数据 |
| Fabric API | 0.92.11+1.20.1 | 0.119.4+1.21.4 | 0.158.0+26.2 | 已确认当前元数据 |

实际会用到的 Fabric API 子模块版本：

| 子模块 | 1.20.1 | 1.21.4 | 26.2 | 状态 |
|---|---|---|---|---|
| `fabric-rendering-v1` | `3.0.9+1802ada577` | `10.2.1+0d31b09f04` | `25.3.2+515ac5339e` | 已确认 |
| `fabric-screen-api-v1` | `2.0.9+1802ada577` | `2.0.38+7feeb73304` | `5.2.0+58e078ad9e` | 已确认 |
| `fabric-resource-loader-v0` | `0.11.12+fb82e9d777` | `3.1.1+360374ac04` | `3.3.20+4fc5413f9e` | 已确认 |
| `fabric-lifecycle-events-v1` | `2.2.23+1802ada577` | `2.5.4+bf2a60eb04` | `4.1.3+4575b05f9e` | 已确认 |
| `fabric-resource-loader-v1` | 不适用 | 不适用 | `2.0.13+9edec1269e` | 已确认 |

上表来自对应 Fabric API 聚合 JAR 内嵌模块 metadata，并与已下载的模块源码/二进制 JAR 文件名交叉核对；它们是当前研究基线，不等同于尚未完成的 Loom 构建锁定。

其他事实：

- Loom Maven `1.18.0-alpha.16` 的 runtime variant 要求 Gradle Plugin API `9.7.0`，与 Wrapper `9.5.1` 的组合已实测否定；本项目锁定已实测空构建通过的 `1.17.19`；
- Gradle Wrapper 锁定 `9.5.1`，已从第 5 节来源复制、复核四个文件哈希，并成功执行 `projects` 与 `buildAll`；
- 26.2 客户端 SHA-1：`2dc72797acbc1b63fc16a11c4ac393605f453754`；
- 26.2 无可用 Yarn 命名层；读取到的 intermediary JAR 只有 Tiny 头部，实际工程计划使用官方命名；
- Skija Maven Central 当前 release 为 `0.143.17`（metadata `lastUpdated=20260629103741`）；shared/source 与六个桌面 native JAR 已下载到临时研究目录并核验。它是当前候选锁定版本，仍待三版 Loom 构建确认；shared POM 的必需传递依赖为 `io.github.humbleui:types:0.2.0`；
- `skija-shared:0.143.17` POM声明 Java source/target 11，实测 `FontMgr.class` class-file major 为 `52`，可被本项目 Java 17/21/25 toolchain 加载；
- NanoVG 上游 C 核心计划固定提交 `ce3bf745eb2d2dbc14a50bf2446783f691ac4353`；
- 26.2 官方版本元数据声明 Java 25、LWJGL `3.4.1`，并发布 `linux`、`macos`、`macos-arm64`、`windows`、`windows-arm64`、`windows-x86` native classifier；精确 Vulkan 平台边界见 6.4.1。

## 5. 本地证据位置

这些路径仅用于研究，不是 FandUI 的运行时依赖：

| 内容 | 本地路径 | 状态 |
|---|---|---|
| 1.21.4 客户端源码/映射 | `C:/Users/winme/Desktop/git/Skija-Client-Base/minecraft-clientOnly-a172fc6613-1.21.4-loom.mappings.1_21_4.layered+hash.2198-v2` | 已读取 |
| 1.20.1 反编译源码 JAR | `C:/Users/winme/.gradle/caches/forge_gradle/minecraft_user_repo/mcp/1.20.1-20230612.114412/joined/decompile/output.jar` | 已读取 |
| 26.2 官方命名 JAR | `C:/Users/winme/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar` | 已读取 |
| 26.2 官方版本元数据 | `C:/Users/winme/.gradle/caches/fabric-loom/26.2/mojang_minecraft_info.json` | 已读取 |
| Gradle 9.5.1 Wrapper 来源 | `C:/Users/winme/Desktop/git/FandInfinity/template-mod-template-26.2/gradle/wrapper` | 已复制到仓库并逐文件复核 SHA-256 |
| Skija shared 0.143.16 | `C:/Users/winme/.gradle/caches/modules-2/files-2.1/io.github.humbleui/skija-shared/0.143.16` | 已读取 |
| Skija 0.143.17 shared/source 与六平台构件 | `%TEMP%/FandUI-api-research/skija-*-0.143.17*.jar` | 已下载、哈希并执行 Windows x64 探针 |
| LWJGL NanoVG 缓存 | `C:/Users/winme/.gradle/caches/modules-2/files-2.1/org.lwjgl/lwjgl-nanovg` | 已读取 |
| Fabric API 探查临时目录 | `%TEMP%/FandUI-api-research` | 已读取；可由下表 Maven 坐标重新取得 |
| NanoVG 上游参考源码 | `%TEMP%/FandUI-api-research/nanovg_gl.h`、`nanovg.c`、`nanovg.h` | 已读取；固定 commit 见 8.1 |
| LWJGL Vulkan `3.4.1` source/macOS natives | `%TEMP%/FandUI-api-research/lwjgl-vulkan-3.4.1-*.jar` | 已读取；源码 SHA-256 `7AA852539D538F1BE8C9C80FB39101E1A11E5A7E2C29AE7B018D2A3A9E764DDD`，两个 native SHA-1 与 Mojang metadata 一致 |

后续每个关键 API 必须记录到下一节，尽量补齐 owner、descriptor、来源文件/JAR 和验证方式。

## 6. Minecraft/Fabric API 与渲染时序事实

### 6.1 跨版本符号表

| 版本 | 符号/行为 | 已确认结果 | 尚缺信息 |
|---|---|---|---|
| 1.20.1 | `GameRenderer.render(float,long,boolean)` | descriptor `(FJZ)V`；最终 `GuiGraphics.flush()` 完成后返回 | 稳定 Mixin selector |
| 1.20.1 | `Minecraft.runTick(boolean)` | descriptor `(Z)V`；调用一次 `GameRenderer.render` 后解绑主目标、blit、更新 display | 稳定 Mixin selector |
| 1.20.1 | `HudRenderCallback.onHudRender(GuiGraphics,float)` | `net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback`；在 `InGameHud.render` TAIL 调用 | 运行组合测试 |
| 1.20.1 | Screen/input | render 回调与原版输入 descriptor、短路语义已确认，见 6.1.1 | 运行组合测试 |
| 1.20.1 | `RenderTarget.getColorTextureId()` | public，返回 `GL_TEXTURE_2D` 颜色 attachment；resize 会销毁重建 | 运行原型 |
| 1.21.4 | `GameRenderer.render(DeltaTracker,boolean)` | descriptor `(Lnet/minecraft/client/DeltaTracker;Z)V`；最终 GUI flush 后返回 | 稳定 Mixin selector |
| 1.21.4 | `Minecraft.runTick(boolean)` | descriptor `(Z)V`；随后 `unbindWrite`、`blitToScreen`、`Window.updateDisplay` | 稳定 Mixin selector |
| 1.21.4 | `HudLayerRegistrationCallback.register(LayeredDrawerWrapper)` | 构造 HUD 时注册；优先使用，旧 HUD callback 已弃用；精确顺序见 6.1.3 | 与第三方 Mod 的运行组合测试 |
| 1.21.4 | Screen/input | render 回调与原版输入 descriptor、短路语义已确认，见 6.1.1 | 运行组合测试 |
| 1.21.4 | `RenderTarget.getColorTextureId()` | public，返回 `GL_TEXTURE_2D` 颜色 attachment；resize 会销毁重建 | 运行原型 |
| 26.2 | `GameRenderer.render(DeltaTracker,boolean)` | descriptor `(Lnet/minecraft/client/DeltaTracker;Z)V`；依次调用 `GuiRenderer.render()`、`GuiRenderer.endFrame()` | 最窄 Mixin selector 已选，仍需最小启动验证 |
| 26.2 | `Minecraft.renderFrame(boolean)` | descriptor `(Z)V`；上述 render 返回后处理目标 blit，随后 `CommandEncoder.submit()`、`GpuSurface.present()` | 最小启动验证 |
| 26.2 | HUD API | `HudElement.extractRenderState(GuiGraphicsExtractor,DeltaTracker)`；静态 registry 的签名、顺序和失败语义已确认，见 6.1.3 | 与第三方 Mod 的运行组合测试 |
| 26.2 | Screen API | `beforeExtract -> afterBackground -> afterForeground -> afterExtract` 的精确触发位置已确认 | GPU 提交点组合验证 |
| 26.2 | 输入 API | `KeyEvent`、`CharacterEvent`、`MouseButtonEvent`、`PreeditEvent` 字段和派发语义已确认 | IME 与重复键运行测试 |

### 6.1.1 Fabric Screen 与输入精确接口

三版 `Screen` 生命周期共同语义已经由 Fabric Mixin 源码确认：

- `ScreenEvents.BEFORE_INIT`/`AFTER_INIT` 分别位于原版 init 的 HEAD/TAIL；resize 也触发同一对事件；
- 每次 init/resize 都会先丢弃并重建该 screen 实例的 remove、tick、render/extract、keyboard 和 mouse event 对象，然后才触发 `BEFORE_INIT`。因此按 screen 注册的监听器只活到下一次 re-init，必须在每次 `BEFORE_INIT` 或 `AFTER_INIT` 重新注册；
- 1.20.1/1.21.4 原版 init descriptor 为 `(Lnet/minecraft/client/Minecraft;II)V`；26.2 为 `(II)V`，Fabric 仍向全局 init callback 传入 `Minecraft.getInstance()`；
- remove 回调位于原版 `Screen.removed()V` 返回之后；tick 回调直接包围原版 `Screen.tick()V`；
- 1.20.1/1.21.4 的 `beforeRender/afterRender` 都是 `(Screen,GuiGraphics,int mouseX,int mouseY,float tickDelta)V`，直接包围 `Screen.renderWithTooltip(GuiGraphics,int,int,float)V`，所以 after 位于 tooltip 之后；
- 26.2 的提取顺序为 `beforeExtract -> extractBackground -> afterBackground -> extractRenderState -> afterForeground -> extractDeferredElements -> afterExtract`。普通 screen 的 `afterForeground` 位于 `Screen.extractRenderState` 之后；container screen 改在 `AbstractContainerScreen.extractContents` 之后，recipe-book screen 改在 recipe book 提取之后；tooltip/其他 deferred element 仍在 `afterForeground` 之后、`afterExtract` 之前。

Minecraft `Screen`/`GuiEventListener` 输入调用面的 named descriptor：

| 输入 | 1.20.1 | 1.21.4 | 26.2 |
|---|---|---|---|
| key press/release | `(III)Z` | `(III)Z` | `(Lnet/minecraft/client/input/KeyEvent;)Z` |
| committed character | `(CI)Z` | `(CI)Z` | `(Lnet/minecraft/client/input/CharacterEvent;)Z` |
| IME preedit | 不存在 | 不存在 | `(Lnet/minecraft/client/input/PreeditEvent;)Z` |
| mouse move | `(DD)V` | `(DD)V` | `(DD)V` |
| mouse click | `(DDI)Z` | `(DDI)Z` | `(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z`；第二参数为 double-click |
| mouse release | `(DDI)Z` | `(DDI)Z` | `(Lnet/minecraft/client/input/MouseButtonEvent;)Z` |
| mouse drag | `(DDIDD)Z` | `(DDIDD)Z` | `(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z` |
| mouse scroll | `(DDD)Z`，只有 vertical amount | `(DDDD)Z`，horizontal + vertical | `(DDDD)Z`，horizontal + vertical |

26.2 输入 record 的字面字段为：

- `KeyEvent(int key, int scancode, int modifiers)`，accessor 为 `key()/scancode()/modifiers()`；
- `CharacterEvent(int codepoint)`，只有完整 Unicode code point，没有 modifiers；`codepointAsString()` 使用 `Character.toString(int)`；
- `MouseButtonInfo(int button, int modifiers)`；`MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo)`，并提供 `button()`/`modifiers()` 代理；
- `PreeditEvent(String fullText, int caretPosition, List<String> blocks, int focusedBlock)`；`GuiEventListener.preeditUpdated(PreeditEvent)` 是独立于 committed character 的 IME composition 通道。native preedit size 为 0 时 `PreeditEvent.createFromCallback` 返回 `null`，`KeyboardHandler` 仍把该值提交给 `preeditUpdated`，表示清除当前 composition；Fabric Screen API 目前没有对应 preedit callback。

输入派发和归一化约束：

- 三版 GLFW action `1`（press）和 `2`（repeat）都会调用 screen 的 `keyPressed`；action `0` 调用 `keyReleased`。旧版 Fabric key callback 只有 key/scancode/modifiers，26.2 `KeyEvent` 也不携带 action，因此桥接层不能读取原生 action 字段。当前 FandUI 在 Core 共享 `KeyInputState` 中按每个 Screen 的生命周期派生稳定转移：同一 key 第一次 `keyPressed` 为 `PRESS`，未收到 release 前的后续回调为 `REPEAT`，`keyReleased` 为 `RELEASE`，`removed` 清空状态；这覆盖标准 GLFW repeat 派发，不宣称从宿主事件中读取额外 action 字段；
- 1.20.1/1.21.4 的 GLFW code point 若不在 BMP，会被拆为 high/low surrogate 两次 `charTyped(char,int)`；26.2 `CharacterEvent` 保留单个完整 code point。旧版桥若输出 code point/string 事件，必须在同一次 handler 派发内组合代理对并对孤立代理项给出确定诊断，具体公共事件形状留到 API spike；
- mouse x/y 和 drag delta 在调用 screen 前都从窗口坐标按 `guiScaledWidth/screenWidth`、`guiScaledHeight/screenHeight` 转为 GUI 逻辑坐标；26.2 `MouseButtonEvent.x/y` 已是该缩放后的值；
- scroll amount 是应用 discrete-scroll 与 wheel-sensitivity 后的值。1.20.1 原版 `Screen.mouseScrolled` 只接收 vertical，但该版 Fabric `ScreenMouseEvents` 会另行计算并传出 horizontal amount；1.21.4/26.2 原版和 Fabric 都传 horizontal + vertical；
- 26.2 double-click 只有在距上次 **已消费** click 小于 `250 ms`、screen identity 相同且 button 相同时为 true；
- Fabric Screen API 三版都没有 mouse-move callback；1.20.1/1.21.4 还没有 drag 或 char callback，26.2 新增 drag 与 char callback，但仍没有 preedit callback。FandUI 自有 Screen 基类因此直接覆写该版完整输入方法；Fabric 回调只用于挂载到非 FandUI screen 的可选观察能力，不能充当跨版本共同最小输入 API。

Fabric allow/before/after 的取消语义：

- 所有 `AllowX` 使用 AND/短路语义：任一 callback 返回 false 就跳过原版 screen 方法和对应 before/after，并向 Minecraft 返回 true，把输入视为已处理；
- 1.20.1/1.21.4 的 key、click、release、scroll 为 `Allow -> Before -> 原版调用 -> After`；After 无论原版 boolean 结果如何都会调用，且不能改写结果；
- 26.2 keyboard/char 保持上述规则；mouse click/release/drag/scroll 的 After 返回 boolean，按注册顺序观察 `consumed = 原版结果 OR 先前 After 结果`，最终只能把未消费提升为已消费，不能把 true 改回 false；
- callback 包围的是同一个捕获到的 screen 实例；事件期间切换当前 screen 不会把 After 自动改派给新 screen。

证据：`fabric-screen-api-v1:2.0.9+1802ada577`、`2.0.38+7feeb73304`、`5.2.0+58e078ad9e` 的 API、`ScreenEventFactory`、`ScreenMixin`、keyboard/mouse handler mixin 与 GUI mixin 源码；1.20.1 official-mapped Forge/Minecraft source 仅用于 named descriptor 与原版派发交叉核对，Fabric 行为以 Fabric source 为准；1.21.4 named source 目录见第 5 节；26.2 official-named JAR 以 `javap -p -s -c -l` 核对 `Screen`、`GuiEventListener`、四个 input record 和 handler bytecode。

### 6.1.2 Resource reload 精确差异

- 1.20.1/1.21.4 注册 owner 均为 `net.fabricmc.fabric.api.resource.ResourceManagerHelper`：`get(PackType.CLIENT_RESOURCES).registerReloadListener(IdentifiableResourceReloadListener)`；listener 的 `getFabricId()` 返回该版 `ResourceLocation`；
- 1.20.1 `SimpleResourceReloadListener<T>` 的精确阶段签名为 `load(ResourceManager, ProfilerFiller, Executor)` 与 `apply(T, ResourceManager, ProfilerFiller, Executor)`，两者都返回 `CompletableFuture`；
- 1.21.4 同一 Fabric interface 已改为 `load(ResourceManager, Executor)` 与 `apply(T, ResourceManager, Executor)`，不再接收 `ProfilerFiller`；底层 `PreparableReloadListener.reload` descriptor 为 `(PreparationBarrier,ResourceManager,Executor,Executor)CompletableFuture`；
- 26.2 优先使用 v1 owner `net.fabricmc.fabric.api.resource.v1.ResourceLoader`：`get(PackType.CLIENT_RESOURCES).registerReloadListener(Identifier, PreparableReloadListener)`；MC listener descriptor 为 `(SharedState,Executor,PreparationBarrier,Executor)CompletableFuture`，`SharedState.resourceManager()` 为 public；
- 三个版本桥都先读取资源为纯 Java不可变 bytes/metadata，再把 Skija registry 构建串行提交给唯一 text worker；只有新 generation 完整成功后才在 apply 阶段原子切换，失败继续保留旧 generation。26.2 为等待 text-worker future 而直接实现 `PreparableReloadListener`，不在 Fabric prepare worker 上创建 Skija 对象。

证据：已直接读取 1.20.1 `fabric-resource-loader-v0:0.11.12+fb82e9d777`、1.21.4 `fabric-resource-loader-v0:3.1.1+360374ac04` 和 26.2 `fabric-resource-loader-v1:2.0.13+9edec1269e` 源码，并用 1.21.4/26.2 named Minecraft JAR 的 `javap -p -s` 交叉核对底层 descriptor。

### 6.1.3 HUD 注册与提取接口

| 版本 | 首选入口与精确签名 | 源码顺序/失败语义 | FandUI 约束 |
|---|---|---|---|
| 1.20.1 | `net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.onHudRender(GuiGraphics,float)` | Fabric 在 `InGameHud.render(GuiGraphics,float)` TAIL 调用，已在整个 vanilla HUD 之后 | 回调只采集 FandUI HUD snapshot；最终 GPU pass 仍在已选统一提交点 |
| 1.21.4 | `HudLayerRegistrationCallback.register(LayeredDrawerWrapper)`；layer 为 `LayeredDraw.Layer.render(GuiGraphics,DeltaTracker)`，descriptor `(GuiGraphics,DeltaTracker)V` | callback 在 `InGameHud` 构造 RETURN 调用一次；ID 重复或相对 anchor 不存在时抛 `IllegalArgumentException`；弃用的 `HudRenderCallback(GuiGraphics,DeltaTracker)` 仍位于 render TAIL | 在 client 初始化注册 identified layer；不使用 deprecated callback 作为主入口 |
| 26.2 | `HudElement.extractRenderState(GuiGraphicsExtractor,DeltaTracker)`；静态 `HudElementRegistry.addFirst/addLast/attachElementBefore/attachElementAfter/removeElement/replaceElement` | registry 用 identified root list 包围 vanilla extract 调用；重复 ID或相对 anchor 不存在时抛 `IllegalArgumentException`；无同步保护 | 在 client 初始化期间注册；element 只采集 immutable UI 输入，不持有 extractor 或 Minecraft 类型 |

1.21.4 的 `IdentifiedLayer` 顺序以及 26.2 的 `VanillaHudElements` 顺序都以 first drawn = bottom、last drawn = top 定义；两版均提供 `SUBTITLES` 作为最后一个 vanilla anchor。对同一 anchor 连续 `attach...After` 时，后注册项被插到更靠近 anchor 的位置，因此第三方共享 anchor 的相对顺序依赖注册顺序，FandUI 不承诺跨 Mod 的稳定层间排序。

设计决定：MVP 的标准 HUD mount 使用各版“vanilla HUD 之后”的采集位置；1.21.4/26.2 相对 `SUBTITLES` 注册以继承标准 HUD 可见性条件。由于 FandUI 的路径/text 实际统一在最终 post-GUI pass 提交，该 Fabric 层位置只控制采集时机、逻辑顺序和 F1/Screen 可见性，不能提供与 vanilla 或第三方 `GuiGraphics` draw 的像素级交错。需要 always-visible 或不同层策略时留到公共 API spike 明确，不在 bridge 中增加隐式第二次 GPU 提交。

证据：`fabric-rendering-v1:3.0.9+1802ada577` 的 `HudRenderCallback`/`InGameHudMixin`；`10.2.1+0d31b09f04` 的 `HudLayerRegistrationCallback`、`LayeredDrawerWrapper`、`IdentifiedLayer`、`LayeredDrawerWrapperImpl` 和 `InGameHudMixin`；`25.3.2+515ac5339e` 的 `HudElement`、`HudElementRegistry`、`HudElementRegistryImpl`、`HudMixin` 与 `SubtitleOverlayMixin`。1.21.4 `LayeredDraw.Layer` descriptor 和 26.2 `Hud.extractRenderState` 条件顺序另由 named JAR `javap` 交叉核对。

### 6.2 1.20.1 接入决定

新增已确认源码事实：

- Mojang mapping 将 `GameRenderer.render(float,long,boolean)` 映射到 obfuscated `a(FJZ)V`，Forge SRG 为 `m_109093_`；
- `Minecraft.runTick(boolean)` 映射到 obfuscated `f(Z)V`，Forge SRG 为 `m_91383_`；
- `RenderTarget.getColorTextureId()` 是 public，Forge SRG 为 `m_83975_()`；
- 主颜色纹理是 `GL_TEXTURE_2D`、internal format `GL_RGBA8`，挂在 `GL_COLOR_ATTACHMENT0`；
- 主 depth 使用 `GL_DEPTH_COMPONENT`/float，只挂在 `GL_DEPTH_ATTACHMENT`，没有 stencil；
- `RenderTarget.resize` 先销毁 framebuffer、颜色纹理和 depth 纹理，再创建新 handle。

- `Blaze3dOpenGlHost1201` 位于版本模块；
- Screen/HUD 回调负责采集 UI 和生命周期，最终在已核验 GUI flush 之后合并提交一次；
- 复用当前 OpenGL context；
- 计划复用 Minecraft 主颜色纹理，并为其创建 FandUI framebuffer view + 自有 D24S8；
- 不创建第二颜色目标，不做离屏颜色合成；
- 绘制结束恢复 framebuffer、viewport、program、VAO、buffer、texture unit、blend、depth、stencil、scissor 与写掩码；
- 优先通过 Blaze3D 已跟踪状态入口修改状态，避免其缓存与真实 OpenGL 状态分叉。

因此颜色 handle 访问和 resize 代际已由源码确认。仍待验证：同一颜色纹理重挂到 FandUI framebuffer 后的 D24S8 completeness、运行时颜色 probe、状态恢复和 Iris 自定义 framebuffer。

### 6.3 1.21.4 接入决定

新增已确认源码事实：

- `RenderTarget.frameBufferId` 为 public，颜色/depth handle 为 protected，但 public `getColorTextureId()`/`getDepthTextureId()` 可用；
- 主颜色纹理为 `GL_TEXTURE_2D + GL_RGBA8`，主 depth 为 float depth-only texture；
- `resize(int,int)` 会执行 `destroyBuffers()` 后 `createBuffers()`，颜色 handle 会变化；
- `GameRenderer.render(DeltaTracker,boolean)` 的最后一条 UI 提交是第二次 `GuiGraphics.flush()`，随后只恢复 model-view 并结束 frame resource pool；
- `Minecraft.runTick(boolean)` 在该方法返回后执行 `mainRenderTarget.unbindWrite()`、`blitToScreen()`、`Window.updateDisplay()`。

- `Blaze3dOpenGlHost1214` 独立实现，不复用 1.20.1 的 Minecraft 源码层代码；
- GPU 目标与状态隔离策略同 1.20.1；
- HUD 接入使用该版本优先 API；
- 待验证项与 1.20.1 相同，并额外确认 Sodium/Iris 下 layer 和目标所有权。

### 6.3.1 旧版 OpenGL 状态与 D24S8 边界

两个旧版的 `com.mojang.blaze3d.platform.GlStateManager` 已逐项读取，不能按同一状态镜像实现：

- 1.20.1 缓存 blend enable/factors、depth enable/func/mask、cull、polygon offset、color logic、统一 stencil func/mask/op、scissor enable、active texture、12 个 `GL_TEXTURE_2D` binding、viewport 字段和 color mask；`_glBindFramebuffer`、`_glUseProgram`、`_glBindBuffer`、`_glBindVertexArray`、`_scissorBox`、`_blendEquation` 都直接调用 GL，不维护对应完整镜像；
- 1.20.1 没有 stencil-test enable/disable wrapper，也没有 front/back 分离 stencil wrapper；`getBoundFramebuffer()` 通过 `glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)` 查询真实状态；
- 1.21.4 在上述基础上新增独立 `READ_FRAMEBUFFER`/`DRAW_FRAMEBUFFER` 缓存，并由 `_glBindFramebuffer(GL_READ_FRAMEBUFFER|GL_DRAW_FRAMEBUFFER|GL_FRAMEBUFFER, id)` 更新；program、VAO、buffer、scissor box、blend equation 和 front/back stencil 仍没有完整状态镜像；
- 两版 `_stencilFunc(func, ref, mask)` 都存在同一字面条件：将 `func` 分别与缓存的 func/ref/mask 比较，而不是分别比较三个参数。后端不能依赖该方法的 no-op 判定来证明真实 stencil 状态；运行原型必须直接查询并校验 front/back 实值。

旧版目标接入决定：

- FandUI 创建独立 framebuffer object，颜色 attachment 直接引用 Minecraft 当前主颜色 `GL_TEXTURE_2D`，depth/stencil attachment 使用 FandUI 自有 `GL_DEPTH24_STENCIL8` renderbuffer；
- D24S8 在 FandUI 中不采样，因此 KISS 选择 renderbuffer，不创建不需要的 depth/stencil texture；
- framebuffer 与 D24S8 按 target generation 重建；颜色 handle 或尺寸变化后不得复用旧 attachment；
- 除 front/back 分离 stencil 外，凡 Blaze3D 有受跟踪入口的状态都通过 `GlStateManager`/`RenderSystem` 修改并经同一路径恢复；
- NanoVG 非凸 fill 的 winding 需要 front `INCR_WRAP`、back `DECR_WRAP`。该版 Blaze3D 只有统一 `_stencilOp` 等状态入口，没有 front/back 分离入口；FandUI 因而只对这组状态调用裸 `glStencilOpSeparate`，进入前读取 front/back 的 func/op/mask，结束后也用裸 GL逐项恢复。这样不会让 Blaze3D 的统一 stencil 缓存被 FandUI 改写后留在错误值；
- 状态守卫在进入 pass 时读取所有实际会触碰的真实 GL 值。两版共同经 Blaze3D 入口恢复 enable/factors、depth、color mask、viewport、active texture/texture binding；1.21.4 的 read/draw framebuffer 必须经 `_glBindFramebuffer` 恢复以同步新增缓存；
- 未被缓存或无法表达的 program、VAO、array buffer、blend equation、scissor box、stencil-test enable、front/back stencil func/op/write mask、cull/front-face、polygon mode、depth range、sampler binding，以及上传使用的 pixel-unpack buffer/alignment/row-length/skip 状态，按进入时的真实 GL 值精确恢复。FandUI 只保存实际会修改的 texture unit 和状态，不做全 context 枚举；
- D24S8 每帧初始化优先使用 `glClearBufferfi(GL_DEPTH_STENCIL, ...)`，避免依赖并污染全局 clear depth/stencil 值；最终快照集合和恢复后逐项断言由 GL debug 原型冻结；
- prototype 必须在 Minecraft 原版、Sodium 和 Iris 组合下验证 framebuffer completeness、目标所有权、完整状态恢复和 resize。

证据：`C:/Users/winme/Desktop/git/Skija-Client-Base/minecraft-clientOnly-a172fc6613-1.21.4-loom.mappings.1_21_4.layered+hash.2198-v2/com/mojang/blaze3d/platform/GlStateManager.java`；字段/入口由源码读取和 `rg` 定位。该结论不是对 1.20.1 类结构的推定。

### 6.3.2 目标颜色空间与 HDR 边界

当前三个目标版本的源码路径都是 SDR 8-bit UNORM，不能把“平台显示器支持 HDR”误写为“Minecraft 当前 render target 支持 HDR”：

- 1.20.1 `RenderTarget.createBuffers` 与 1.21.4 `MainTarget.allocateColorAttachment` 都以 internal format `GL_RGBA8`（`32856`）创建主颜色纹理；两套源码中均未发现 `GL_FRAMEBUFFER_SRGB`、`GL_SRGB8*`、常量 `36281/35905/35907` 或 HDR/colorspace 配置路径；
- 26.2 `MainTarget(int,int)` 明确以 `GpuFormat.RGBA8_UNORM` 构造主目标；该版 `GpuFormat` 没有 sRGB texture format；
- 26.2 `VulkanGpuSurface.pickSwapchainSurfaceFormat` 只接受 `colorSpace == 0`（`VK_COLOR_SPACE_SRGB_NONLINEAR_KHR`）且 format 为 `37` 或 `44`（`VK_FORMAT_R8G8B8A8_UNORM` / `VK_FORMAT_B8G8R8A8_UNORM`）；`GpuSurface.Configuration` 只有 width、height、presentMode，`configure` 将 `imageColorSpace` 固定为 `0`，没有 HDR surface negotiation；
- 因此当前 FandUI 颜色按宿主的 display-encoded sRGB 数值约定写入 UNORM 目标，premultiplied source-over 在宿主当前数值域中执行，不额外应用 transfer function；旧版状态守卫仍保存/恢复真实 `GL_FRAMEBUFFER_SRGB` enable，以免改变第三方 Mod 状态；
- RenderHost 每个 target generation 都验证实际颜色 format。当前仅接受上述已证明格式；若未来版本或第三方 renderer 提供 sRGB attachment、FP16、10-bit 或 HDR colorspace，必须增加经过像素探针/RenderDoc 证明的显式策略，当前版本不猜测转换。

这满足“正确处理 sRGB/HDR”的当前版本边界：匹配现有 SDR 路径，并明确诊断未知目标；不是虚构一个原版尚不存在的 HDR pass。

### 6.4 26.2 已核验 GPU API（Vulkan 路线已废止，保留源码证据）

本节与 6.4.1 中的 Vulkan 事实是 2026-08-21 需求变更前完成的源码研究，继续保留用于解释为什么没有采用该路线，但不再构成 FandUI 的实现要求、测试前置或发布门槛。当前 26.2 OpenGL 接入见 6.5.2。

| API/事实 | 结果 | 状态 |
|---|---|---|
| `GpuDevice` | 存在 | 已确认 |
| `CommandEncoder` | 存在 | 已确认 |
| `RenderPass` | 存在 | 已确认 |
| `RenderPipeline` | 存在 | 已确认 |
| `GpuBuffer` | 存在 | 已确认 |
| `GpuTexture` | 存在 | 已确认 |
| `BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA` | 存在 | 已确认 |
| Scissor | 高层 API可表达 | 已确认 |
| texture upload | 高层 API可表达 | 已确认 |
| indexed/multi-draw | 高层 API可表达 | 已确认 |
| `DepthStencilState` | 当前只包含 depth，不能表达完整 stencil | 已确认 |
| `MainTarget` | `RGBA8_UNORM + D32_FLOAT` | 已确认 |
| `GpuFormat.D24_UNORM_S8_UINT` | 存在 | 已确认 |
| `CommandEncoder.verifyDepthTexture` | 接受 depth aspect + render attachment + copy-dst 条件 | 已确认 |
| `DeviceInfo.backendName()` | 当前 Vulkan 实现返回字面值 `Vulkan` | 已确认 |
| `GpuDevice.createCommandEncoder()` | 每次返回新的高层 wrapper | 已确认 |
| `VulkanDevice.createCommandEncoder()` | 返回设备持有的同一个 `VulkanCommandEncoder` backend | 已确认 |
| `VulkanCommandEncoder.createRenderPass` | dynamic rendering 只填写 `pDepthAttachment`，没有 `pStencilAttachment` | 已确认 |
| `VulkanRenderPipeline.compile` | 只写 depth test/write/compare；没有 stencil state | 已确认 |
| `VulkanRenderPipeline.compile` attachment format | 有 depth 时硬编码 Vulkan format 数值 `126`（`VK_FORMAT_D32_SFLOAT`），无 stencil format | 已确认 |
| `VulkanGpuTexture` 初始 image barrier aspect | 非颜色格式统一取 depth bit `2`；D24S8 不含 stencil bit | 已确认 |
| `VulkanGpuTextureView` 默认 view aspect | 非颜色格式统一取 depth bit `2`；D24S8 view 不含 stencil bit | 已确认 |
| `VulkanConst.formatAspectMask(D24S8)` | 能正确组合 depth `2` 与 stencil `4`，返回 `6` | 已确认 |
| `RenderSystem.initRenderer(GpuDevice)` | `DEVICE` 非空时直接抛 `IllegalStateException`，当前版本只允许初始化一次 | 已确认 |
| `RenderSystem.shutdownRenderer()` | 关闭共享 GPU 资源后调用 `GpuDevice.close()`；不会把静态 `DEVICE` 重新置空 | 已确认 |
| `GpuTexture`/`GpuTextureView`/`GpuBuffer` | 都是 `AutoCloseable`；Vulkan 实现的 `close()` 进入 Mojang command encoder 的延迟销毁路径 | 已确认 |
| `VulkanCommandEncoder.MAX_SUBMITS_IN_FLIGHT` | 常量为 `2`；使用两个 command pool 和两槽 `DestructionQueue` | 已确认 |

### 6.4.1 26.2 官方 Vulkan 平台与能力边界（已废止路线的事实记录）

这里严格区分三层结论：**发行元数据存在 classifier**、**Mojang/LWJGL 源码允许进入该路径**、**真实 OS/GPU 已运行通过**。前两层不自动证明第三层。

26.2 官方元数据的 Java 组件为 `java-runtime-epsilon`、major `25`，LWJGL 为 `3.4.1`。图形相关 LWJGL 模块的 native 发行矩阵如下：

| OS/CPU classifier | Mojang metadata 中的 native | Vulkan loader 来源 | FandUI 计划发布交集 | 证据状态 |
|---|---|---|---|---|
| Windows x64 (`windows`) | 除 `lwjgl-vulkan` 外 11 个 LWJGL 模块 | LWJGL 加载系统 `vulkan-1` | 是 | metadata/source 已确认；FandUI 真机待测 |
| Windows arm64 (`windows-arm64`) | 除 `lwjgl-vulkan` 外 11 个模块 | 系统 `vulkan-1` | 是 | metadata/source 已确认；FandUI 真机待测 |
| Windows x86 (`windows-x86`) | 除 `lwjgl-vulkan` 外 11 个模块 | 系统 `vulkan-1` | 否；Skija `0.143.17` 无 Windows x86 构件 | 只确认 Minecraft 发行物存在，不列入 FandUI 矩阵 |
| Linux x64 (`linux`) | 除 `lwjgl-vulkan` 外 11 个模块 | 系统 `libvulkan.so.1` | 是 | metadata/source 已确认；FandUI 真机待测 |
| Linux arm64 | 官方 26.2 metadata 无该 classifier | 无官方发行基线 | 否；即使 Skija 有构件也不扩大 Minecraft 基线 | 已确认 metadata 缺失 |
| macOS x64 (`macos`) | 12 个模块，含 `lwjgl-vulkan` native | 优先使用随 JAR 分发的 `libMoltenVK.dylib` | 是 | metadata/JAR/source 已确认；FandUI 真机待测 |
| macOS arm64 (`macos-arm64`) | 12 个模块，含 `lwjgl-vulkan` native | 优先使用随 JAR 分发的 `libMoltenVK.dylib` | 是 | metadata/JAR/source 已确认；FandUI 真机待测 |

metadata 的 OS rule 只按 Windows/Linux/macOS 过滤，同一 OS 的多个架构 JAR 会同时进入发行集合，再由 LWJGL 选择匹配资源。因此上表是**构件边界**，不是 Mojang 对所有列出的 CPU/JVM/GPU 组合都给出的运行保证。版本 metadata 中 Windows `10.0.17134` 只出现在特定 ZGC JVM 参数规则里，不能解释为 Vulkan 最低 Windows 版本。

`VulkanBackend`/`VulkanInstance`/`VulkanPhysicalDevice` 的实际 capability gate 已由 26.2 official-named JAR 字节码确认：

- 启动先要求 `NativeLibrariesBootstrap.isVulkanLoaderAvailable()` 与 `GLFWVulkan.glfwVulkanSupported()`；loader 缺失分别产生字面诊断 `Vulkan loader library is missing` 或 `Vulkan is not supported`；
- instance 的 `VkApplicationInfo.apiVersion` 固定为 Vulkan `1.2`；物理设备必须报告 `apiVersion >= VK_API_VERSION_1_2`；
- 五个强制 device extension 为 `VK_KHR_dynamic_rendering`、`VK_KHR_push_descriptor`、`VK_KHR_synchronization2`、`VK_EXT_vertex_attribute_divisor`、`VK_KHR_swapchain`；
- 九个强制 feature 为 `multiDrawIndirect`、`fillModeNonSolid`、`samplerAnisotropy`、`shaderDrawParameters`、`timelineSemaphore`、`hostQueryReset`、`synchronization2`、`dynamicRendering`、`vertexAttributeInstanceRateDivisor`；
- graphics queue family 必须同时具有 graphics + compute flags，并通过 `glfwGetPhysicalDevicePresentationSupport`；surface 直接把同一 graphics queue 用作 present queue。独立 compute/transfer queue 找不到时分别回退到 graphics/compute queue；
- `VulkanUtils.KNOWN_PROBLEMATIC_DEVICES` 字面包含 58 个 `driverID=14`（MoltenVK）、`vendorID=32902`（Intel）的 device tuple，命中即跳过；候选中优先第一个 suitable discrete GPU；
- `VK_EXT_multi_draw` + `multiDraw`、AMD/NVIDIA checkpoint extension 是可选增强，不属于 capability gate；
- macOS 在支持 `VK_KHR_portability_enumeration` 时启用该 instance extension 和 create flag `1`；设备暴露 `VK_KHR_portability_subset` 时再加入 device extension。LWJGL 官方 source 明确优先加载 bundled MoltenVK，失败后才尝试 `libvulkan.1.dylib`；
- 请求 `VK_LAYER_KHRONOS_validation` 但系统未枚举到时仅记录 warning，backend 不因此失败。

原版存在明确 OpenGL fallback，不能把“设置 Vulkan”当成实际 backend 证明：

| `PreferredGraphicsApi` | `getBackendsToTry()` 字面顺序 |
|---|---|
| `DEFAULT` | `GlBackend` -> `VulkanBackend` |
| `OPENGL` | `GlBackend` -> `VulkanBackend` |
| `VULKAN` | `VulkanBackend` -> `GlBackend` |

启动参数名为 `--graphicsBackend <default|opengl|vulkan>`，validation 开关为 `--vulkanValidation`；每个 backend 创建抛出 `BackendCreationException` 后，`Minecraft` 会关闭失败窗口并继续数组中的下一个 backend。旧 Vulkan 方案曾要求初始化后断言实际 backend 为 `Vulkan`；该要求已经废止。当前 GL 方案只检查实际字面结果是否为 `OpenGL`。

当前主机的历史独立 probe：`vulkaninfo --summary` 退出码为 `0`，Windows loader 报告 instance `1.4.321`，GTX 760/驱动 `475.14` 报告 device API `1.2.175`。上述五个强制 extension 中唯独缺少 `VK_KHR_dynamic_rendering`，对应 `dynamicRendering` feature 也未出现；其余八个 feature 与四个 extension 均存在。因此该 GPU 不满足 Mojang 26.2 Vulkan gate。probe 同时报告 `%VULKAN_SDK%` 指向的 validation JSON 文件缺失，当前未枚举 `VK_LAYER_KHRONOS_validation`；这些结果只解释旧路线为何不可落地，不再是 FandUI 当前 GL 原型或发布的前置条件。

证据：`mojang_minecraft_info.json` SHA-256 `EE529FA1D4096ECD824CD91A2A6E4118D96C82A5F26C8CA34E0F2AC19815273C`；26.2 official-named JAR SHA-256 `B86636EE31ACD4BD13EFC0A9BD9230C13E6B8D01A253682FEB25F1433E969E8D`；`javap -p -s -c` 核对 `PreferredGraphicsApi`、`Minecraft`、`Main`、`NativeLibrariesBootstrap` 与六个 Vulkan owner；LWJGL `VK.java` source 和 Mojang macOS native JAR；本机 `vulkaninfo` 实际输出。跨平台真机仍归 P1/P2 的 clean-machine/组合测试，不在本节伪装为已通过。

### 6.4.2 调试工具版本与可复现入口

核验日期为 2026-08-21。这里完成的是**测试工具版本锁定和源码入口确认**，不是实际 GPU 验收；P0 的 RenderDoc/GL debug 运行证明仍保持未完成。

| 用途 | 锁定基线 | 已确认事实 | 平台边界 |
|---|---|---|---|
| RenderDoc | `v1.45`，commit `2fc0bc04cb95499635f63986a55bc6f67849dd9f`，发布于 2026-07-02 | GitHub signed tag 与官方 builds 页面一致；官方稳定构件为 Windows x86/x64 installer/zip 和 Linux x64 tarball | 官方稳定页没有 macOS 构件；不能把 RenderDoc 写成 macOS 验收前置 |
| Vulkan ValidationLayers | tag `vulkan-sdk-1.4.357.0`，commit `f4874eee15c78d7bdb2b7e60659d539f14741500` | KhronosGroup 最新 release/tag 已交叉核对 | 仅为已废止路线的历史基线，不再安装或作为 FandUI 验收条件 |
| OpenGL debug | Minecraft 1.20.1 的 LWJGL `3.3.1`；1.21.4 的 LWJGL `3.3.3`；26.2 的 LWJGL `3.4.1` | 三版 Mojang `GlDebug.enableDebugCallback` 都优先 `GL_KHR_debug`，再回退 `GL_ARB_debug_output`；26.2 精确参数来源已核验 | 实际测试记录必须附 `GL_VERSION`、`GL_VENDOR`、`GL_RENDERER`、context flags 和最终选择的扩展路径 |

RenderDoc 官方稳定构件已用 HTTP HEAD 和 builds 页面链接交叉确认：Windows x64 MSI `85843968` bytes、Windows x64 ZIP `97805614` bytes、Linux x64 tarball `81282951` bytes。GitHub release 只托管源码和签名并明确把二进制下载指向 `renderdoc.org/builds`；因此不能根据 GitHub asset 数量误判平台支持。RenderDoc 作为 Windows/Linux 的帧级证据工具；macOS 使用平台 GPU capture，具体工具版本在 macOS 真机矩阵中记录。

以下 26.2 Vulkan 启动约束已废止，只保留复现旧研究所需的字面入口：

- 启动参数固定为 `--graphicsBackend vulkan --vulkanValidation`，并在初始化后再次断言实际 `DeviceInfo.backendName()` 为字面值 `Vulkan`；
- validation 日志必须证明 `VK_LAYER_KHRONOS_validation` 实际枚举并启用，不能仅凭传入 `--vulkanValidation` 判定成功；
- LWJGL `3.4.1` 的 `Configuration.VULKAN_LIBRARY_NAME` 精确 JVM property key 是 `org.lwjgl.vulkan.libname`。需要隔离 loader 时，在 `VK` 首次初始化前使用 `-Dorg.lwjgl.vulkan.libname=<absolute-loader-path>`；该入口只用于测试重现和诊断，不改变 FandUI 的 device/queue 所有权；
- 旧路线曾要求 RenderDoc capture 同时检查 attachment、draw/event marker、barrier/submit/present owner；当前 OpenGL 路线不执行这组 Vulkan 验收。

当前 26.2 启动不增加 `--graphicsBackend` 参数，沿用 Minecraft 配置。实际运行证明 Fabric client entrypoint 执行时 `RenderSystem.getDevice()` 尚未初始化，因此入口只创建不触碰 GL 的 runtime/native worker；在已有 `RenderTarget` 的最终 GUI hook 中才检查 `DeviceInfo.backendName()`。字面值为 `OpenGL` 才提交 FandUI pass，其他值把 runtime 转为 `RENDERER_UNAVAILABLE`、以 `FAILED` 关闭已存在的 Screen/HUD，并只输出一次诊断。FandUI 不重启窗口、不修改 option，也不从 Vulkan 路径切换到 OpenGL；因此这里的“沿用 MC 默认”是沿用其选择流程，不等于承诺支持该流程最终选出的每一种 backend。

三版 OpenGL debug 都复用 Mojang callback，不另装第二套 callback。1.20.1/1.21.4 的 `Options.glDebugVerbosity` 初值都是 `1`，`Minecraft` 初始化时把它传给 renderer。26.2 也以 `1` 初始化并从 `options.txt` 的 `glDebugVerbosity` 读取；`Minecraft` 构造 `GpuDebugOptions(int logLevel, boolean synchronousLogs, boolean useLabels, boolean useValidationLayers)`，其中 `logLevel` 就是该字段，`GlDevice` 再调用 `GlDebug.enableDebugCallback(logLevel, synchronousLogs, extensions)`。

26.2 `enableDebugCallback` 在 `logLevel <= 0` 时返回 `null`；否则优先 `GL_KHR_debug`，回退 `GL_ARB_debug_output`。KHR severity 顺序为 `HIGH -> MEDIUM -> LOW -> NOTIFICATION`，ARB 为前三档；`logLevel = 4` 因而覆盖 KHR 的全部四档，并覆盖 ARB 可表达的全部三档。最近消息仍由大小为 `10` 的 `EvictingQueue` 保存。三版测试 profile 都把 `glDebugVerbosity` 固定为 `4`；验收报告还必须在 FandUI pass 前后记录 `glGetError` 和状态守卫断言，以免十条环形缓冲覆盖早期错误。

当前 Windows 主机状态已实测：`renderdoccmd` 不在 PATH，`C:/Program Files/RenderDoc` 不存在，因此当前 OpenGL 抓帧环境尚未就绪。`vulkaninfo.exe` 与失效 SDK/layer 注册项只属于已废止路线的历史环境记录，不再阻塞 FandUI。

证据：RenderDoc GitHub Releases/API、signed tag 与 `https://renderdoc.org/builds`；LunarG `latest/windows.json`、`latest/linux.json`、`latest/mac.json`；Khronos ValidationLayers GitHub release/tag；LWJGL core `3.4.1` SHA-256 `9B1C3A3A078C2377219ECB8A2662730B3DECE07C10592CF1D12F957286037B69` 的 `Configuration` 字节码；1.20.1/1.21.4 Mojang `GlDebug`/`Options`/`Minecraft` 源码；26.2 official-named JAR 中 `GpuDebugOptions`、`GlDebug`、`GlDevice`、`Options`、`Minecraft` 的 `javap -p -s -c`；本机 PATH、环境变量、注册表和文件存在性探针。

### 6.5 26.2 Vulkan 接入决定（已废止）

以下内容保留为旧设计记录，不创建其中的 host、资源或 Mixin hook。

- `MojangVulkanHost262` 复用 Minecraft `GpuDevice`、当前 `CommandEncoder` 和主颜色目标；
- 高层 `CommandEncoder` wrapper 没有全局“当前实例”；宿主在接入点调用 `GpuDevice.createCommandEncoder()`，其 Vulkan backend 实际复用设备唯一 `VulkanCommandEncoder` 和当前 command buffer；
- 在 `GuiRenderer` 完成后、最终 submit/present 前编码 FandUI pass；
- 颜色 attachment 是 Minecraft 当前 UI 主目标；
- D24S8 为 FandUI 自有，仅用于 UI clip/fill；不修改 Minecraft 主 D32 depth；
- image layout、barrier、queue submit 和 present 继续由 Mojang 管理；
- 不修改 device 创建参数，不接管世界 renderer；
- 运行时通过版本内实现验证 `GpuDevice.getDeviceInfo().backendName()` 字面为 `Vulkan`；原版即使选择 `VULKAN` 也会在失败后尝试 `GlBackend`，所以非 Vulkan 时禁用绘制并给出明确启动诊断，不实例化 FandUI OpenGL renderer；
- 高层 API缺少 stencil state、正确 D24S8 view 和 stencil attachment，必须由 `cn.fandmc.fandui.internal` 中的窄适配层访问 Vulkan 实现细节；
- internal 适配限定为带 `fandui` 标记的资源/pipeline，包含三个 hook：
  1. D24S8 image 初始 barrier 与 image view 使用 `depth|stencil` aspect `6`；
  2. dynamic rendering 对同一 D24S8 attachment 同时填写 `pDepthAttachment` 与 `pStencilAttachment`；
  3. FandUI pipeline 编译时把 depth/stencil attachment format 设为 D24S8，并按内部 stencil state 配置 front/back op；
- hook 不得改变非 FandUI pipeline，不得自行 barrier 策略、submit 或 present。具体 Mixin 注入点需原型和 Vulkan validation 证明后才能冻结。

### 6.5.1 26.2 Vulkan internal hook 精确源码位置（已废止）

以下 owner、descriptor 与目标调用数已由 26.2 official-named JAR 的 `javap -p -s -c` 确认；它们是旧需求下的 selector 草案，不再安排启动或 validation 验证：

1. **D24S8 image 与 view aspect**
   - `VulkanGpuTexture.<init>(VulkanDevice,int,String,GpuFormat,int,int,int,int)V` 在初始 `VkImageMemoryBarrier.subresourceRange.aspectMask(int)` 唯一调用处使用 `hasColorAspect ? 1 : 2`；
   - `VulkanGpuTextureView.<init>(VulkanDevice,VulkanGpuTexture,int,int)V` 在 `VkImageViewCreateInfo.subresourceRange.aspectMask(int)` 唯一调用处使用同一判断；
   - Mojang 已有 public `VulkanConst.formatAspectMask(GpuFormat)`，会组合 color `1`、depth `2`、stencil `4`，D24S8 结果为 `6`。两个 `@ModifyArg(require=1)` 仅在 texture label 以保留前缀 `FandUI/` 开头且 format 为 `D24_UNORM_S8_UINT` 时改用该方法，其他资源保留原值。
2. **dynamic rendering stencil attachment**
   - `VulkanCommandEncoder.createRenderPass(RenderPassDescriptor)RenderPassBackend` 在唯一 `VkRenderingInfo.pDepthAttachment(VkRenderingAttachmentInfo)` 调用后立即执行 `vkCmdBeginRenderingKHR`；
   - 对 descriptor 的 depth view 属于上述 FandUI D24S8 时，窄 redirect 先保留原 `pDepthAttachment`，再令 `pStencilAttachment` 指向同一个 `VkRenderingAttachmentInfo`。该 struct 由 `calloc` 创建，clear depth 时 `VkClearDepthStencilValue.stencil` 保持字面 `0`，正好初始化 winding scratch；不匹配时只执行原调用。
3. **pipeline D24S8 format 与 stencil state**
   - `VulkanRenderPipeline.compile(VulkanDevice,VulkanBindGroupLayout,RenderPipeline,long,long)VulkanRenderPipeline` 在 `VkPipelineRenderingCreateInfoKHR.depthAttachmentFormat(int)` 的第一个调用处硬编码 `126`（D32）；FandUI pipeline 改为 `VulkanConst.toVk(D24_UNORM_S8_UINT) == 129`，并同时设置 `stencilAttachmentFormat(129)`；后续为 without-depth pipeline 写 `0` 的调用不改；
   - 同一方法在唯一 `VkPipelineDepthStencilStateCreateInfo.depthCompareOp(int)` 调用处完成 public depth state。对 location namespace 为 `fandui` 且已登记 internal stencil descriptor 的 pipeline，redirect 在保留 depth compare 后设置 `stencilTestEnable` 及 front/back `VkStencilOpState` 的 compare/op/masks/reference；普通 pipeline 不改；
   - NanoVG pass 使用有限 pipeline variant 表达固定 stencil op/compare/mask；MVP 不依赖该版未启用的 extended dynamic stencil state。`VulkanRenderPass.hasDepth` 已确认会选择 `withDepthPipeline`，FandUI pass 始终提供 D24S8 depth attachment。

旧方案把三个关注点落在五个窄注入点（texture、view、render pass、pipeline format、pipeline state），并计划以 FandUI 自有 label/namespace 限定影响范围。当前工程不会创建这些 Mixin、资源或 pipeline；因此也不存在这组 selector 失配时阻止 26.2 GL bridge 启动的关系。

### 6.5.2 26.2 OpenGL 接入决定（当前）

已由 26.2 official-named JAR 字节码确认：

- `PreferredGraphicsApi.DEFAULT` 当前按 `GlBackend -> VulkanBackend` 尝试，FandUI 不增加或改写 `--graphicsBackend`；
- `GlBackend.getName()` 与 `GlHeuristics.createDeviceInfo(...)` 写入的 backend 字面值均为 `OpenGL`；
- `RenderTarget.getColorTextureView()` 是 public，实际 OpenGL 实现 `com.mojang.blaze3d.opengl.GlTextureView` 也是 public，并公开 `glId()I`、`texture()` 与 `fboMipLevel()I`；不需要 Mixin accessor 读取主颜色 handle；
- `RenderTarget.resize(II)V` 在 Render Thread 依次 `destroyBuffers()`、`createBuffers(II)`，会关闭并替换 color texture/view；host 以 view identity、`glId`、尺寸和 format 组成 target generation；
- `GlStateManager._glBindFramebuffer(II)V` 会同步维护独立 `readFbo`/`writeFbo` 缓存，并公开 `getFrameBuffer(I)I`；program、VAO、buffer、scissor box 和 stencil 仍没有可依赖的完整缓存；
- `GlStateManager.BLEND` 与 `COLOR_MASK` 都是 8 槽数组，分别跟踪 draw-buffer blend/color-mask；状态守卫必须按实际修改过的 draw-buffer 槽保存和恢复，不能只假设槽 0；
- 26.2 `GlStateManager` 没有任何 stencil wrapper；public `_glUseProgram`、`_glBindVertexArray`、`_glBindBuffer` 只是直接调用 GL，并不维护对应缓存，因此这几类状态以真实 `glGet*` 值为准；
- `GameRenderer.render(DeltaTracker,Z)V` 中 `GuiRenderer.endFrame()` 后仍是最终 FandUI 锚点，`Minecraft.renderFrame(Z)V` 之后才执行 encoder `submit()` 和 surface `present()`；OpenGL command encoder 的 GPU 操作是当前 context 上的 GL 命令，FandUI 不调用其 `submit()`。

当前设计决定：

- `Blaze3dOpenGlHost262` 先断言 Render Thread、实际 backend 字面值 `OpenGL`、color view 为 `GlTextureView`、format 为 `RGBA8_UNORM` 且尺寸有效；任一条件不满足则跳过并给出一次性诊断；
- 复用 `GlTextureView.glId()` 指向的 Minecraft 主颜色纹理，创建 FandUI 自有 framebuffer object 和 `GL_DEPTH24_STENCIL8` renderbuffer；不创建第二颜色纹理，不复制或合成主颜色；
- FBO/D24S8 随 target generation 重建。旧 generation 的自有 GL object 在 Render Thread 删除，不关闭或修改 Minecraft 的 `GpuTexture`/`GpuTextureView`；
- framebuffer 绑定与恢复走 26.2 `GlStateManager._glBindFramebuffer`，以同步 Mojang cache；其余状态按“有可靠 wrapper 则经 wrapper、否则读取真实 GL 值并精确恢复”的规则处理；front/back stencil 继续使用裸 `glStencil*Separate` 并裸 GL恢复；
- 三个版本共用 `fandui-render-opengl` 的 shader、buffer、texture、batch 和 NanoVG stencil 语义；版本模块只实现 target acquisition、状态桥、生命周期和诊断；
- FandUI 构件不声明 Vulkan 依赖、不直接引用或调用 Vulkan API、不创建 Vulkan resource，原 5 个 Vulkan internal hook 全部取消。Minecraft 自身是否因用户配置探测/加载 Vulkan 不属于 FandUI 的行为声明。

这一接入目前是“源码接口与状态边界已确认、运行原型待验证”。完整状态快照候选已经由受影响状态集合冻结；仍需用原型验证 FBO completeness、resize、逐项恢复断言以及 Sodium/Iris 下的目标所有权。

### 6.6 三版本提交锚点与 Mixin selector 草案

映射策略已由空构建实测收口：1.20.1 与 1.21.4 使用 Loom `net.fabricmc.fabric-loom-remap` + `officialMojangMappings()`；26.2 本身为官方命名环境，使用 `net.fabricmc.fabric-loom` no-remap 插件且不声明 `mappings`。Loom 1.17.19 的插件描述符分别指向 `LoomRemapGradlePlugin` 与 `LoomNoRemapGradlePlugin`；在 no-remap 环境调用 `officialMojangMappings()` 会明确抛出 `Cannot use Mojang mappings in a non-obfuscated environment`。

#### 1.20.1 与 1.21.4

旧版最终 GPU 提交锚点选择 `Minecraft.runTick(boolean)` 中调用 `RenderTarget.unbindWrite()` **之前**，而不是 `GameRenderer.render` 的 `TAIL`：

- 1.20.1 `GameRenderer.render` 返回后，`Minecraft.runTick` 仍可能绘制并 flush FPS profiler 叠层；在 `unbindWrite` 前注入才能保证 FandUI 位于所有原版 UI 写入之后；
- 1.21.4 没有这段 FPS pie 绘制，但同一锚点仍精确位于 `GameRenderer.render` 之后、`unbindWrite -> blitToScreen -> updateDisplay` 之前；
- 两版 `runTick` descriptor 都是 `(Z)V`，`RenderTarget.unbindWrite()V` 在该方法中各出现一次；
- selector 草案：`method = "runTick(Z)V"`，`@At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;unbindWrite()V", shift = BEFORE)`，并设置 `require = 1`；
- hook 首先检查该版本的 `noRender`、目标尺寸和窗口/target 有效性。无可呈现目标时跳过，不创建替代窗口或第二目标；
- FandUI pass 完成后，原版继续执行既有 unbind/blit/display 流程。

#### 26.2

26.2 选择 `GameRenderer.render(DeltaTracker,boolean)` 中 `GuiRenderer.endFrame()` 调用之后：

- 精确 descriptor：`(Lnet/minecraft/client/DeltaTracker;Z)V`；
- `GuiRenderer.render()` 后紧接唯一一次 `GuiRenderer.endFrame()`；其后才是 `RenderBuffers.endFrame()`、`CrossFrameResourcePool.endFrame()` 和方法返回；
- selector 草案：`@At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V", shift = AFTER)`，并设置 `require = 1`；
- 此时使用 public `GameRenderer.mainRenderTarget()` 取得实际颜色目标，检查其 public color view 为 `GlTextureView` 并读取 `glId()`；FandUI 直接在当前 context 编码 GL 命令，不调用 `GpuDevice.createCommandEncoder()`；
- `Minecraft.renderFrame(boolean)` 在 `GameRenderer.render` 返回后才执行主颜色目标到 `GpuSurface` 的 blit，随后才调用 `CommandEncoder.submit()` 和 `GpuSurface.present()`。

以上 selector 状态为**设计决定**，owner、descriptor、目标调用数和顺序为**已确认**。只有三个最小客户端均实际启动，Mixin audit 显示各命中一次，且截图/RenderDoc 证明 pass 顺序正确后，才能把“稳定 selector”对应 P0 项标为完成。

固定在最终 GUI 后只提交一次的直接结果是：FandUI HUD 是 post-GUI overlay，能保证各 FandUI layer 间的稳定顺序，但不能承诺把一次 FandUI draw 精确插入两个原版/第三方 HUD layer 之间。版本桥的 Fabric HUD callback/layer 只负责采集状态和 DisplayList；若未来产品要求与 vanilla layer 逐层交错，必须单独修改渲染时序设计，当前 API 不作虚假承诺。

## 7. 公共 API 边界草案

以下名称是 FandUI 自有设计名，不是对 Minecraft 类名的猜测。第一版签名已经形成；用户评审和首次 API 编译探针通过后再冻结。

本轮 API spike 已按用户要求只读参考 `C:/Users/winme/Desktop/FandServer` 中的 FandAPI 设计。本节固定依赖隔离、行为边界和可评审的第一版签名；用户确认前，候选类型名均不视为已冻结 API。

### 7.1 FandAPI 参考结果与采用边界

只读参考基线为 FandServer commit `9f39e90772306222f927d005d7520dc86246ee2f`；下列参考文件在读取时均未出现在对应路径的 `git status --short` 中。已确认的可复用模式：

- `Fand.server()` 只提供只读静态入口，实际实例由 `internal.FandRuntime` 在 bootstrap 时一次绑定，未初始化访问立即抛出状态异常；FandUI 采用同一方向，以 `FandUI.runtime()` 返回纯 API `UiRuntime`，绑定入口只放在 `cn.fandmc.fandui.internal`；
- FandAPI 把不可变定义和 live handle 分开，例如 `Gui` 与 `GuiView`。FandUI 对应区分 Screen/HUD 定义与运行会话，定义不持有宿主或 GPU 资源，会话负责 active/close/invalidate/focus 等生命周期；
- `EventSubscription`、`ServiceRegistration` 等注册句柄统一实现 `AutoCloseable`，重复注销为 no-op，并且旧句柄只能移除自己安装的对象。FandUI 的 HUD mount、事件监听和资源注册统一遵守同一精确所有权规则；
- `Gui` 在构建边界 clone 数组并 `Map.copyOf`，FandAPI 广泛使用 record、不可变集合和构造期校验。FandUI 的几何、颜色、输入快照和 display data 使用不可变值；包含可演进可选项的定义使用隐藏字段的 final class + builder，避免给 public record 增加 component 造成二进制破坏；
- 每个 API package 使用 `@NullMarked`，可空引用显式标注；非空返回缺失值使用 `Optional` 或空集合。FandUI 采用 JSpecify `1.0.0` 作为 `compileOnlyApi` 空值契约，运行时不依赖它执行逻辑；
- `ApiArchitectureTest` 会拒绝 public API 源码引用实现与 Minecraft package。FandUI 采用更强的双层检查：源码规则阻止 `net.minecraft`、`net.fabricmc`、`com.mojang.blaze3d`、Skija、NanoVG 和 LWJGL package，编译后再扫描 class descriptor/signature/annotation/constant pool，避免只检查 import 的漏网情况；
- FandAPI 通过 interface default method 为新增能力提供兼容默认值。FandUI 只在存在正确保守语义时使用 default method；必需渲染能力不返回“看似成功”的空实现，而通过显式 availability 和异常暴露不可用状态。

不直接复制的部分：FandAPI `gui` 是服务端 inventory GUI，不是 retained-mode client UI；其 `EventBus`、反射注解 listener、通用 `ServiceRegistry` 和大量 registry surface 也不进入 FandUI MVP。FandUI 组件事件使用场景树内的 capture/target/bubble，扩展点使用窄接口，避免为当前需求引入全局服务容器。

参考证据 SHA-256：

| 文件 | SHA-256 |
|---|---|
| `CODING_STANDARDS.md` | `0708031C101A0D9C9FF282410B6F892416A50F45F7B54C9D84837ACE1429EF31` |
| `fand-api/.../Fand.java` | `58C146BE16F63BA9E7C23E28DB1AABADDEB98D93D3198772024D6CF89BB87189` |
| `fand-api/.../internal/FandRuntime.java` | `43B9462532715E16BBCC8D3647D3930C23E23F252836CC3DDCBD002B7F92D4CC` |
| `fand-api/.../ApiArchitectureTest.java` | `270B2B62799492EC72A64D534606E7F6A2E59BE02F4AA6875B5B00A96797443E` |
| `fand-api/.../service/ServiceRegistration.java` | `D1E703B5001406267F7BF76B4408A04FA275E0D377749BF3DA06E9937CC0884D` |
| `fand-api/.../event/EventSubscription.java` | `84F3A1A6DDE96BA9CAA30ADE5F256F29F3D7FEE092028500312004E32452F797` |
| `fand-api/.../gui/Gui.java` | `2A07A6F273955FC1AB7E601AD93BB738D979F6E7E9453FF804CFB41DE7A40637` |
| `fand-api/.../gui/GuiView.java` | `705C7BB1108C10DB11968C2F1E580233EE89AA55291AD9DDDA1FE37BA2436E62` |

### 7.2 第一版 API 结构决定

- `fandui-api` 以 Java 17 `--release` 编译；同一 FandUI release 的三个版本 JAR 必须嵌入字节一致的 API classes，版本桥分别使用 Java 17/21/25；
- 根入口候选固定为 `cn.fandmc.fandui.api.FandUI.runtime()`，返回 `UiRuntime`。`UiRuntime` 提供线程安全的 availability/capabilities 快照以及 `screens()`、`hud()`、`resources()`、`text()` 四个窄服务；不暴露通用 service locator；
- Screen 定义与 live `ScreenSession` 分离，HUD 定义与 live `HudRegistration` 分离；所有 live handle 都有 `active()` 和幂等 `close()`，关闭旧 handle 不得影响后来复用同一 key 的对象；
- 组件公开名使用 `UiComponent`/`UiContainer`，不使用简单名 `Component`，避免与 Minecraft 文本 `Component` 形成高频 import 冲突；
- 组件树在 UI thread 可变，布局和绘制只消费提交时生成的不可变 snapshot。外部状态变化通过 session/component invalidation 触发新 snapshot，不允许 renderer 直接读取 mutable component；
- `Canvas2D` 是 paint callback 期间有效的 recording scope；不得被缓存，所有调用只写 DisplayList，不立即访问 GPU；
- MVP 提供显式 standard components、约束布局和 `CanvasComponent`。自定义组件 SPI 只保留 measure/paint/event 三个正交回调，不公开 renderer hook、native handle 或任意后端命令注入；
- API 版本按 semver；删除或改变 public descriptor 只进 major。minor 只能新增类型、重载或具有正确默认语义的 interface default method；API baseline 使用二进制兼容检查，禁止把 `record` 用作未来很可能增加字段的 options/config 类型。

公共 interface 不等于全部可由调用方实现。明确的扩展点只有 `UiComponent`/`UiContainer` subclass、`StyleResolver`、`Easing`、事件/listener callback 和 `ResourceSource`；`UiRuntime`/service/session/registration、`ImageRef`、`TextLayout`、`MeasureScope`/`Placeable`/`MeasureResult`、`PaintScope`/`Canvas2D`/`CanvasState` 和 `EventContext` 都是 framework-owned handle/scope。把外部实现的 opaque handle 传回 FandUI 时立即抛 `IllegalArgumentException`；特别是 Canvas 按 runtime provenance + generation 校验 image/text handle，绝不按第三方伪造的 accessor 直接访问 GPU。

| 领域 | 候选公共类型 | 稳定语义 |
|---|---|---|
| 场景 | `FandUI`、`UiRuntime`、`UiScreen`、`ScreenSession`、`HudLayer`、`HudRegistration` | 定义/live handle 分离；打开 Screen、挂载 HUD、幂等关闭 |
| 组件 | `UiComponent`、`UiContainer`、`ComponentContext` | retained component tree；组件不持有宿主或 GPU handle |
| 布局 | `Constraints`、`Size`、`Rect`、`Insets`、`MeasureScope`、`Placeable` | 逻辑像素，measure/layout 分离 |
| 样式 | `Style`、`Theme`、`Color`、`Paint`、`Border` | 自有不可变值类型 |
| 事件 | `PointerEvent`、`KeyEvent`、`TextInputEvent`、`ScrollEvent`、`FocusEvent` | capture -> target -> bubble |
| 焦点 | `FocusManager`、`FocusDirection` | 单场景焦点、Tab/方向导航、pointer capture |
| 动画 | `AnimationManager`、`AnimationSpec`、`AnimationHandle`、`Easing` | session-scoped 单调时钟；测试时钟只在 core internal 注入 |
| Canvas | `Canvas2D`、`Path`、`StrokeStyle` | 只记录命令，不立即触发 GPU |
| 资源/文字 | `ResourceService`、`ResourceSource`、`ImageRef`、`TextService`、`TextStyle` | `UiKey`/bytes/纯 Java metrics，不暴露平台资源类 |

明确禁止出现在 `fandui-api` 的类型：

- Fabric event/callback；
- Minecraft Screen、GUI context、ResourceLocation、RenderTarget；
- Skija Typeface/Paragraph/Surface/Pixmap；
- NanoVG context/image id/native pointer；
- LWJGL `MemoryStack`、native handle；
- OpenGL object id；
- Vulkan instance/device/queue/image/command buffer；
- Mixin accessor。

内部 `RenderHost` 只接收纯 Java帧描述与不可变 DisplayList，不提供 GPU handle getter。具体 host 与 renderer 在 Fabric/后端模块内协作。

核心不变量：

- mutable component tree 只能产出 immutable layout snapshot/DisplayList；
- 命中测试使用与绘制相同的已提交布局快照；
- UI 坐标为逻辑像素，编译批时才应用 GUI/framebuffer scale；
- CPU 结果跨线程时不可变；
- GPU 资源只在 Render Thread 访问；
- API 二进制检查必须阻止第三方类型泄漏。

### 7.3 Runtime、Screen 与 HUD 第一版签名

以下为设计签名，不是已经创建的源码。包名是公共契约的一部分，所有 implementation/binder 仍放在 `cn.fandmc.fandui.internal` 或版本模块。

```java
package cn.fandmc.fandui.api;

public final class FandUI {
    public static UiRuntime runtime();
}

public interface UiRuntime {
    UiAvailability availability();
    UiCapabilities capabilities();
    boolean isUiThread();
    java.util.concurrent.CompletableFuture<Void> execute(Runnable action);
    cn.fandmc.fandui.api.screen.ScreenService screens();
    cn.fandmc.fandui.api.hud.HudService hud();
    cn.fandmc.fandui.api.resource.ResourceService resources();
    cn.fandmc.fandui.api.text.TextService text();
}

public final class UiCapabilities {
    public boolean imeComposition();
    public boolean distinctKeyRepeat();
}

public final class UiUnavailableException extends IllegalStateException {
    public UiUnavailableException(UiAvailability availability);
    public UiAvailability availability();
}

public record UiAvailability(UiRuntimeState state, String detail) {
    public boolean available();
}

public enum UiRuntimeState {
    STARTING,
    AVAILABLE,
    RENDERER_UNAVAILABLE,
    FAILED,
    STOPPED
}

public record UiKey(String namespace, String value) {
    public static UiKey of(String namespace, String value);
    public static UiKey parse(String value);
}
```

`FandUI.runtime()` 采用 FandAPI 的 fail-fast binder 语义：Fabric client entrypoint 完成绑定后返回同一 `UiRuntime`；绑定前访问抛 `IllegalStateException`。不增加可写 singleton setter，也不通过 `ServiceLoader` 猜测多个 runtime。`UiAvailability` 是任意线程可读的不可变快照：

- `AVAILABLE` 才允许创建 Screen/HUD session；
- 26.2 实际 backend 非 OpenGL 时为 `RENDERER_UNAVAILABLE`，runtime、诊断和 `execute` 仍可使用；
- `FAILED` 表示 FandUI 初始化失败，`detail` 保存稳定、可记录但不承诺本地化的原因；
- `STOPPED` 后不再接受新任务或 mount；
- `execute` 可从任意线程调用，在 UI thread 上按提交顺序执行；若调用方已经位于 UI thread 则立即执行。任务异常或 runtime 已停止时 future 异常完成。

`UiKey` 是 FandUI 自有 namespaced key，namespace 只接受 `[a-z0-9_.-]+`，value 只接受 `[a-z0-9/._-]+`，`parse` 要求单个 `namespace:value`。它用于 HUD、组件和资源身份，不依赖 Adventure `Key` 或 Minecraft `ResourceLocation`。

Screen API：

```java
package cn.fandmc.fandui.api.screen;

public interface ScreenService {
    ScreenSession open(UiScreen screen);
    java.util.Optional<ScreenSession> current();
}

public final class UiScreen {
    public static Builder builder(
            String title,
            cn.fandmc.fandui.api.component.UiComponent root);

    public String title();
    public cn.fandmc.fandui.api.component.UiComponent root();
    public boolean pausesGame();
    public boolean closesOnEscape();
    public ScreenBackground background();
    public cn.fandmc.fandui.api.style.Theme theme();

    public static final class Builder {
        public Builder pausesGame(boolean value);
        public Builder closesOnEscape(boolean value);
        public Builder background(ScreenBackground value);
        public Builder theme(cn.fandmc.fandui.api.style.Theme value);
        public UiScreen build();
    }
}

public enum ScreenBackground {
    DEFAULT,
    NONE
}

public interface ScreenSession extends cn.fandmc.fandui.api.session.UiSession {
    UiScreen screen();
}
```

`title` 是无宿主类型的 narration/debug 标题，不替代根树中的可见 `TextComponent`。`ScreenBackground.DEFAULT` 请求对应版本的 Minecraft 标准 Screen 背景，`NONE` 不绘制背景；MVP 不公开会随版本变化的 panorama/dirt/blur 细分。默认值固定为 `pausesGame=false`、`closesOnEscape=true`、`background=DEFAULT`、`theme=Theme.defaults()`。

HUD API：

```java
package cn.fandmc.fandui.api.hud;

public interface HudService {
    HudRegistration mount(HudLayer layer);
    java.util.Optional<HudRegistration> find(cn.fandmc.fandui.api.UiKey key);
    java.util.List<HudRegistration> mounted();
}

public final class HudLayer {
    public static Builder builder(
            cn.fandmc.fandui.api.UiKey key,
            cn.fandmc.fandui.api.component.UiComponent root);

    public cn.fandmc.fandui.api.UiKey key();
    public cn.fandmc.fandui.api.component.UiComponent root();
    public int order();
    public cn.fandmc.fandui.api.style.Theme theme();

    public static final class Builder {
        public Builder order(int value);
        public Builder theme(cn.fandmc.fandui.api.style.Theme value);
        public HudLayer build();
    }
}

public interface HudRegistration extends cn.fandmc.fandui.api.session.UiSession {
    cn.fandmc.fandui.api.UiKey key();
    HudLayer layer();
}
```

HUD `order` 只定义 FandUI 内部顺序：较小值先画，同值按 `UiKey` 字面顺序稳定排序；默认 `order=0`、`theme=Theme.defaults()`。标准 mount 继承 vanilla HUD 可见性，MVP 不公开 always-visible、与 vanilla layer 像素级交错或鼠标锁定时的交互模式。重复 active key 直接抛 `IllegalStateException`，不隐式替换已有层。

Screen/HUD 共用 live session：

```java
package cn.fandmc.fandui.api.session;

public interface UiSession extends AutoCloseable {
    cn.fandmc.fandui.api.component.UiComponent root();
    boolean active();
    UiViewport viewport();
    cn.fandmc.fandui.api.focus.FocusManager focus();
    cn.fandmc.fandui.api.style.Theme theme();
    cn.fandmc.fandui.api.animation.AnimationManager animations();
    java.util.Optional<cn.fandmc.fandui.api.component.UiComponent> find(
            cn.fandmc.fandui.api.UiKey key);
    void invalidate();
    java.util.Optional<SessionCloseReason> closeReason();
    cn.fandmc.fandui.api.event.EventRegistration onClose(SessionCloseListener listener);
    @Override void close();
}

public record UiViewport(
        float logicalWidth,
        float logicalHeight,
        int framebufferWidth,
        int framebufferHeight,
        float devicePixelRatio) {}

public enum SessionCloseReason {
    API,
    ESCAPE,
    REPLACED,
    HOST,
    SHUTDOWN,
    FAILED
}

@FunctionalInterface
public interface SessionCloseListener {
    void closed(UiSession session, SessionCloseReason reason);
}
```

`open`、`mount`、树变更、`invalidate`、focus 操作和所有回调都要求 UI thread，错误线程抛 `IllegalStateException`。`active`、`viewport`、`closeReason` 是任意线程可读快照；`close` 可从任意线程调用，第一次调用立即把 handle 标为 inactive 并将实际 detach 排入 UI thread，后续调用为 no-op。外部 Minecraft Screen 替换、窗口关闭或 runtime shutdown 也必须恰好触发一次 close listener。

`UiScreen`/`HudLayer` 是不可变配置 wrapper，但其 `root()` 明确是 retained mutable tree，不宣称深度不可变。builder 不 attach 或复制 root；同一 `UiComponent` 实例在任一时刻只能属于一个 active session/tree，重复挂载或形成 parent cycle 立即失败。同一定义可在旧 session 完全 detach 后再次 open/mount，并保留 root/controller 状态；需要全新状态时由调用方构建新 root 和新定义，不在 API 内隐式 clone 任意组件子类。

`UiSession.find` 只查询当前 live tree 的显式 key，要求 UI thread，并依赖同 session key 唯一性返回至多一个组件。打开新的 FandUI Screen 会以 `REPLACED` 关闭旧 FandUI Screen；Minecraft 打开非 FandUI Screen 时以 `HOST` 关闭当前 session。`ScreenService.current()` 仅返回当前 active FandUI Screen。

渲染不可用、错误线程、重复 key、重复挂载和闭合后的 mutation 都是编程/运行状态错误，使用 typed `UiUnavailableException` 或 `IllegalStateException`；不返回 `null`、boolean sentinel 或伪 active handle。任一未处理的 component measure/paint/lifecycle callback 异常都会放弃该次未完成 snapshot、保留上一完整帧完成当前提交，并在本轮回调退出后以 `FAILED` 恰好关闭该 session；不增加未定义阈值、自动重试或局部吞错。

### 7.4 组件树、约束布局与标准组件

`UiComponent` 是 retained tree 的唯一节点基类。公共基类负责 identity、parent/attachment、dirty propagation、style 和 listener ownership；custom component 只覆写 framework callback。这样 core 不需要识别第三方 subclass，也不会把 renderer SPI 暴露给 Mod。

```java
package cn.fandmc.fandui.api.component;

public abstract class UiComponent {
    protected UiComponent();
    protected UiComponent(cn.fandmc.fandui.api.UiKey key);

    public final java.util.Optional<cn.fandmc.fandui.api.UiKey> key();
    public final java.util.Optional<UiContainer> parent();
    public final cn.fandmc.fandui.api.style.StyleResolver style();
    public final void setStyle(cn.fandmc.fandui.api.style.StyleResolver style);
    public final boolean visible();
    public final void setVisible(boolean visible);
    public final boolean enabled();
    public final void setEnabled(boolean enabled);
    public final boolean focusable();
    public final void setFocusable(boolean focusable);
    public final int tabIndex();
    public final void setTabIndex(int tabIndex);

    public final <E extends cn.fandmc.fandui.api.event.UiEvent>
    cn.fandmc.fandui.api.event.EventRegistration on(
            Class<E> type,
            cn.fandmc.fandui.api.event.EventRoute route,
            cn.fandmc.fandui.api.event.EventHandler<E> handler);

    public abstract cn.fandmc.fandui.api.layout.MeasureResult measure(
            cn.fandmc.fandui.api.layout.MeasureScope scope,
            cn.fandmc.fandui.api.layout.Constraints constraints);

    public void paint(PaintScope scope);
    public void attached(ComponentContext context);
    public void detached(ComponentContext context);

    protected final void invalidateLayout();
    protected final void invalidatePaint();
}

public abstract class UiContainer extends UiComponent {
    protected UiContainer();
    protected UiContainer(cn.fandmc.fandui.api.UiKey key);
    public final java.util.List<UiComponent> children();
    public final void add(UiComponent child);
    public final void add(int index, UiComponent child);
    public final boolean remove(UiComponent child);
    public final UiComponent remove(int index);
    public final void clear();
}

public interface ComponentContext {
    cn.fandmc.fandui.api.session.UiSession session();
    cn.fandmc.fandui.api.style.Theme theme();
}

public interface PaintScope {
    cn.fandmc.fandui.api.canvas.Canvas2D canvas();
    cn.fandmc.fandui.api.layout.Rect bounds();
    cn.fandmc.fandui.api.style.Style style();
    cn.fandmc.fandui.api.style.Theme theme();
    long frameTimeNanos();
}
```

规则：

- `children()` 返回不可修改快照；只有 `UiContainer` 的显式 mutation 能改变 parent 关系；
- key 可缺省，但同一 active session 内显式 key 必须唯一。identity 默认使用对象身份，不根据相等值重建组件；
- `UiComponent` 默认无 key、`visible=true`、`enabled=true`、`focusable=false`、`tabIndex=0`，custom component 的默认 resolver 为 `StyleResolver.fixed(Style.defaults())`；标准组件 builder 可安装自身基于 theme token 的 resolver；
- attached component 的 setter、children mutation 和 listener registration 仅允许 UI thread；每个 setter 自动传播最窄 dirty flag；
- `measure`、`paint`、`attached`、`detached` 不得自行并发、阻塞、等待 future 或发起 GPU 操作；framework callback 中修改当前树会排到本轮 callback 结束后并影响下一 snapshot；
- `attached`/`detached` 对每次 attachment 恰好成对一次；同一实例不支持同时挂在两棵树；
- listener registration 归属 component，component detach 时保持注册以便同一对象重新挂载；显式 `EventRegistration.close()` 才永久删除该 listener。

约束布局 API：

```java
package cn.fandmc.fandui.api.layout;

public record Size(float width, float height) {}
public record Point(float x, float y) {}
public record Rect(float x, float y, float width, float height) {}
public record Insets(float left, float top, float right, float bottom) {}

public record Constraints(
        float minWidth,
        float maxWidth,
        float minHeight,
        float maxHeight) {
    public static Constraints tight(Size size);
    public static Constraints loose(Size maximum);
    public Size constrain(Size size);
}

public interface MeasureScope {
    Placeable measure(
            cn.fandmc.fandui.api.component.UiComponent child,
            Constraints constraints);
    MeasureResult layout(
            float width,
            float height,
            java.util.function.Consumer<PlacementScope> placements);
    LayoutDirection direction();
}

public interface PlacementScope {
    void place(Placeable child, float x, float y);
    void place(Placeable child, float x, float y, int zIndex);
}

public interface Placeable {
    cn.fandmc.fandui.api.component.UiComponent component();
    Size size();
    java.util.OptionalDouble baseline(TextBaseline baseline);
}

public interface MeasureResult {
    Size size();
}

public enum LayoutDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT }
public enum TextBaseline { ALPHABETIC, IDEOGRAPHIC }
public enum Axis { HORIZONTAL, VERTICAL }
public enum MainAxisAlignment { START, CENTER, END, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }
public enum CrossAxisAlignment { START, CENTER, END, STRETCH, BASELINE }
```

所有几何值使用 logical pixel。`Size`、`Point`、`Rect` 和 `Insets` 拒绝 NaN/无穷；尺寸和 inset 不得为负。`Constraints` 的 minimum 必须为有限非负数，maximum 可为正无穷，且 `min <= max`。父组件只可通过当前 `MeasureScope` 测量直接 child；同一 child 每个 measure pass 最多测量一次，返回的 `Placeable` 只能在对应 `PlacementScope` 使用。未 placement 的 child 不参与 paint、hit test 或焦点遍历。

`layout(width,height,...)` 检查结果仍位于入参 constraints 内，placement consumer 当场执行并立即冻结结果，不保留用户 lambda。z-index 只改变同一 parent 内的 paint/hit-test 顺序；同值保持 children 顺序。命中测试严格使用已提交 layout snapshot 和最终 transform/clip，绝不在 input path 临时重新 measure。

每个 Screen/HUD session 的 root 都以 `Constraints.tight(viewport logical size)` 测量并放在 scene `(0,0)`；需要内容内缩或居中时由 root 容器显式完成。GUI Scale、framebuffer resize 或逻辑 viewport 变化会更新 `UiViewport`、使 root layout 失效并生成新 snapshot；纯 device-pixel ratio 变化也使 text raster 和 GPU clip generation 失效，但不把物理像素暴露为组件布局单位。

MVP 标准组件固定为：

| 类型 | 职责与最小状态 |
|---|---|
| `Box` | 单 child、padding/alignment/background/border 的基础容器 |
| `Row` / `Column` | 主轴 gap、alignment、child grow/shrink 的线性布局 |
| `Stack` | 多 child 重叠与 alignment/z-index |
| `ConstrainedBox` | 对 child 叠加 min/max/fixed 约束 |
| `Spacer` | 固定或可扩展空白 |
| `TextComponent` | string、`TextStyle`、wrap/max-lines/ellipsis；异步 text result 未就绪时沿用上一完整 layout |
| `ImageComponent` | `ImageRef`、fit、alignment、sampling；资源未就绪时使用确定 placeholder 尺寸 |
| `Button` | 单 child、hover/pressed/focus/disabled 状态和 action |
| `TextField` | `TextController`、selection、caret、committed text、可用时的 IME composition 与水平滚动 |
| `ScrollContainer` | 单 child、axis、viewport clip、wheel/drag 和 `ScrollController` |
| `CanvasComponent` | 显式 measure callback + `Canvas2D` paint callback，作为第三方自定义绘制入口 |
| `ThemeScope` | 只替换 subtree theme，不创建额外 render surface |

标准组件使用 final class + builder 创建，所有 builder 共享可选 `key(UiKey)`，key 在 build 后不可修改；运行中其他属性使用 `setX` 修改并自动 invalidation。不通过几十个构造器重载冻结未来选项。MVP 不引入 virtual list、reconciliation、CSS selector、constraint solver、data binding 或 Compose 风格 compiler plugin。大量列表只有真实性能样本证明需要时再增加虚拟化组件。

控制器签名：

```java
package cn.fandmc.fandui.api.component.control;

public final class TextController {
    public static TextController create();
    public static TextController create(String initialText);
    public String text();
    public TextSelection selection();
    public void setText(String text);
    public void setSelection(TextSelection selection);
    public void replaceSelection(String replacement);
    public void replace(TextSelection range, String replacement);
    public void selectAll();
    public cn.fandmc.fandui.api.event.EventRegistration onChange(Runnable listener);
}

public record TextSelection(int anchorUtf16, int focusUtf16) {
    public int startUtf16();
    public int endUtf16();
    public boolean collapsed();
}

public final class ScrollController {
    public static ScrollController create();
    public static ScrollController create(double initialOffset);
    public double offset();
    public java.util.OptionalDouble maximumOffset();
    public void scrollTo(double offset);
    public void scrollBy(double delta);
    public cn.fandmc.fandui.api.event.EventRegistration onChange(Runnable listener);
}
```

`TextController` 的 text、selection 和 composition index 统一使用 Java UTF-16 code-unit offset，与 Skija Paragraph 已验证的 index 单位一致；`TextSelection` 自身校验非负，controller 再检查不超过当前 text length、位于 code-unit boundary 且不得拆开 surrogate pair。`setText` 把 caret 原子移动到新文本末尾；`replaceSelection` 和 `replace(range, replacement)` 都替换规范化范围并把 caret 放在插入文本末尾，后者供搜索替换、自动补全和外部编辑器直接指定区间。IME composition 是当前 `TextField` attachment 的临时状态，不写入可跨 detach 复用的 controller；只有 committed text 进入 controller。

`ScrollController` 使用非负、有限的 logical-pixel double offset；未绑定时 `maximumOffset()` 为空且保留 requested offset，绑定或 layout extent 改变时在下一次提交前原子 clamp 到 `[0, maximum]`。两个 controller 都是 UI-thread confined live state，同一时刻最多绑定一个 active `TextField`/`ScrollContainer`，detach 后保留值；所有实际值变化在 mutation 完成后同步调用按注册顺序排列的 listener，重复值不通知，listener 内的嵌套 mutation 排到当前通知批次结束后。registration 可幂等关闭并只移除自身 listener。

### 7.5 样式与主题

公开颜色是 straight/unpremultiplied RGBA；`Canvas2D` 写 DisplayList 时才转换为 premultiplied 语义。公开类型不让调用方预乘两次。

```java
package cn.fandmc.fandui.api.style;

public record Color(float red, float green, float blue, float alpha) {
    public static Color rgb(int rgb);
    public static Color argb(int argb);
    public Color withAlpha(float alpha);
}

public sealed interface Paint permits SolidPaint, LinearGradient, RadialGradient {}
public record SolidPaint(Color color) implements Paint {}
public record GradientStop(float offset, Color color) {}
public record LinearGradient(
        cn.fandmc.fandui.api.layout.Point start,
        cn.fandmc.fandui.api.layout.Point end,
        java.util.List<GradientStop> stops) implements Paint {}
public record RadialGradient(
        cn.fandmc.fandui.api.layout.Point center,
        float innerRadius,
        float outerRadius,
        java.util.List<GradientStop> stops) implements Paint {}

public record CornerRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {}
public record Border(float width, Paint paint) {}
public record Transform2D(float m00, float m01, float m10, float m11, float tx, float ty) {}
public enum ClipMode { NONE, BOUNDS, ROUNDED_BOUNDS }

public final class Style {
    public static Style defaults();
    public static Builder builder();
    public static Builder builder(Style base);
    public cn.fandmc.fandui.api.layout.Insets margin();
    public cn.fandmc.fandui.api.layout.Insets padding();
    public Paint background();
    public Border border();
    public CornerRadii cornerRadii();
    public float opacity();
    public Transform2D transform();
    public ClipMode clip();

    public static final class Builder {
        public Builder margin(cn.fandmc.fandui.api.layout.Insets value);
        public Builder padding(cn.fandmc.fandui.api.layout.Insets value);
        public Builder background(Paint value);
        public Builder border(Border value);
        public Builder cornerRadii(CornerRadii value);
        public Builder opacity(float value);
        public Builder transform(Transform2D value);
        public Builder clip(ClipMode value);
        public Style build();
    }
}

@FunctionalInterface
public interface StyleResolver {
    Style resolve(Theme theme, VisualState state);
    public static StyleResolver fixed(Style style);
}

public final class VisualState {
    public boolean hovered();
    public boolean pressed();
    public boolean focused();
    public boolean disabled();
}

public final class ThemeToken<T> {
    public static <T> ThemeToken<T> of(
            cn.fandmc.fandui.api.UiKey key,
            Class<T> type,
            T defaultValue);
    public cn.fandmc.fandui.api.UiKey key();
    public Class<T> type();
    public T defaultValue();
}

public final class Theme {
    public static Theme defaults();
    public static Builder builder();
    public static Builder builder(Theme base);
    public <T> T value(ThemeToken<T> token);

    public static final class Builder {
        public <T> Builder value(ThemeToken<T> token, T value);
        public Theme build();
    }
}
```

`Style` 是完整 resolved style，而不是稀疏 CSS declaration；`StyleResolver` 按 `Theme + VisualState` 返回完整值并按输入 identity/state revision 缓存。`VisualState` 由 framework 创建且不可由调用方实现，这样 minor 版本仍可增加只读状态 accessor。每个标准组件只公开并读取自己拥有的少量 `ThemeToken` 常量，不建立全局可写 token registry；component 显式 resolver 覆盖该组件默认，`ThemeScope` 只改变 token lookup。具体 token 与对应标准组件签名一起进入首次 Java 17 API 编译探针。没有 selector specificity、全局 mutable theme 或反射字段绑定。

gradient stop 在构造时复制、按 offset 验证为 `0..1` 非递减且至少两个；颜色/opacity 范围为 `0..1`，transform 所有值有限。`Style.defaults()` 固定为零 margin/padding/radius、透明 background、零宽透明 border、opacity `1`、identity transform 和 `ClipMode.NONE`；`builder()` 从该值开始，`builder(base)` 做值复制。`Paint` 是 renderer 支持集合，所以使用 sealed hierarchy；第三方复杂效果通过 `CanvasComponent` 组合现有命令，不注册任意 shader。

`Theme` 和 `Style` 都不可变。`ThemeToken` 以 `(UiKey, Class<T>)` 作为逻辑身份，key/type/default 均非空；相同 key 使用不同 type 是配置错误。`Theme.Builder.value` 在写入时用 token type 做运行时校验；自定义 token value 必须是不可变对象，FandUI 不尝试猜测或深拷贝任意第三方类型。`Theme.value` 没有 override 时返回该 token 的 default value。

`UiScreen.Builder` 和 `HudLayer.Builder` 的 `theme(Theme)` 默认 `Theme.defaults()`；`UiSession.theme()` 返回定义在 mount/open 时捕获的根 theme。MVP 不在 live session 上增加根 theme setter；需要运行时切换局部主题时由 `ThemeScope` 的 setter 触发 subtree style/layout invalidation，并只在下一完整 snapshot 原子可见。

### 7.6 输入事件、焦点与动画

事件 payload 不包含 mutable propagation 状态；同一不可变 payload 沿路径传递，当前 target/phase/consume/pointer capture 放在 callback-scope `EventContext` 中：

```java
package cn.fandmc.fandui.api.event;

public interface UiEvent {
    long timestampNanos();
}

@FunctionalInterface
public interface EventHandler<E extends UiEvent> {
    void handle(E event, EventContext context);
}

public interface EventContext {
    EventPhase phase();
    cn.fandmc.fandui.api.component.UiComponent target();
    cn.fandmc.fandui.api.component.UiComponent currentTarget();
    java.util.Optional<cn.fandmc.fandui.api.layout.Point> sceneToLocal(
            cn.fandmc.fandui.api.layout.Point scenePosition);
    boolean consumed();
    void preventDefault();
    void stopPropagation();
    void stopImmediatePropagation();
    void consume();
    void requestFocus();
    void capturePointer();
    void releasePointer();
}

public interface EventRegistration extends AutoCloseable {
    boolean active();
    @Override void close();
}

public enum EventRoute { CAPTURE, BUBBLE }
public enum EventPhase { CAPTURE, TARGET, BUBBLE }

public final class PointerEvent implements UiEvent {
    public PointerAction action();
    public cn.fandmc.fandui.api.layout.Point scenePosition();
    public cn.fandmc.fandui.api.layout.Point sceneDelta();
    public java.util.Optional<PointerButton> changedButton();
    public java.util.Set<PointerButton> buttons();
    public int clickCount();
    public java.util.Set<KeyModifier> modifiers();
    @Override public long timestampNanos();
}

public final class ScrollEvent implements UiEvent {
    public double horizontalLines();
    public double verticalLines();
    public cn.fandmc.fandui.api.layout.Point scenePosition();
    public java.util.Set<KeyModifier> modifiers();
    @Override public long timestampNanos();
}

public final class KeyEvent implements UiEvent {
    public KeyCode key();
    public int scanCode();
    public KeyAction action();
    public java.util.Set<KeyModifier> modifiers();
    @Override public long timestampNanos();
}

public final class TextInputEvent implements UiEvent {
    public String text();
    @Override public long timestampNanos();
}

public final class TextCompositionEvent implements UiEvent {
    public boolean active();
    public String fullText();
    public int caretUtf16();
    public java.util.List<String> blocks();
    public java.util.OptionalInt focusedBlock();
    @Override public long timestampNanos();
}

public final class FocusEvent implements UiEvent {
    public FocusAction action();
    public FocusCause cause();
    public java.util.Optional<cn.fandmc.fandui.api.component.UiComponent> related();
    @Override public long timestampNanos();
}

public final class KeyCode {
    public static KeyCode of(String canonicalName);
    public String canonicalName();
}

public final class Keys {
    public static final KeyCode UNKNOWN;
    public static final KeyCode SPACE;
    public static final KeyCode ENTER;
    public static final KeyCode TAB;
    public static final KeyCode ESCAPE;
    public static final KeyCode BACKSPACE;
    public static final KeyCode DELETE;
    public static final KeyCode INSERT;
    public static final KeyCode HOME;
    public static final KeyCode END;
    public static final KeyCode PAGE_UP;
    public static final KeyCode PAGE_DOWN;
    public static final KeyCode LEFT;
    public static final KeyCode RIGHT;
    public static final KeyCode UP;
    public static final KeyCode DOWN;
    public static KeyCode letter(char asciiLetter);
    public static KeyCode digit(int digit);
    public static KeyCode function(int number);
    public static KeyCode keypadDigit(int digit);
}

public final class PointerButton {
    public static final PointerButton PRIMARY;
    public static final PointerButton SECONDARY;
    public static final PointerButton MIDDLE;
    public static PointerButton of(int index);
    public int index();
}

public enum PointerAction { MOVE, DOWN, UP, CANCEL }
public enum KeyAction { PRESS, REPEAT, RELEASE }
public enum KeyModifier { SHIFT, CONTROL, ALT, SUPER, CAPS_LOCK, NUM_LOCK }
public enum FocusAction { GAINED, LOST }
public enum FocusCause { POINTER, KEYBOARD, PROGRAMMATIC, CLEAR, DETACH, SESSION_CLOSED }
```

dispatch 顺序固定为 root-to-parent capture、target capture、target bubble、parent-to-root bubble。`preventDefault` 只阻止 Button/TextField/Scroll 等内建默认动作；`stopPropagation` 阻止进入下一个 component；`stopImmediatePropagation` 还停止当前 component 后续 listener；`consume` 同时 prevent default 和 stop propagation，并让版本桥向 Minecraft 返回 handled。`EventContext` 仅在当前 callback 期间有效，`sceneToLocal` 使用 current target 的已提交逆变换；transform 不可逆时返回 empty。回调结束后调用任何 context 方法都抛 `IllegalStateException`。旧 registration 关闭只移除自身 listener，重复关闭为 no-op。

输入 payload 采用 final class + accessor 而不是 wide public record，允许 minor 版本增加非抽象 accessor：

| 类型 | 第一版稳定字段/语义 |
|---|---|
| `PointerEvent` | `PointerAction`、scene logical position/delta、变化 button、按下 button 集合、click count、modifiers、timestamp；current-target local position 通过 `EventContext.sceneToLocal` 获取 |
| `ScrollEvent` | horizontal/vertical line delta、scene position、modifiers、timestamp；current-target local position 由 context 变换，ScrollContainer 再按自身 theme token 的 line extent 转为 logical pixel |
| `KeyEvent` | FandUI `KeyCode`、`KeyAction(PRESS/REPEAT/RELEASE)`、modifiers、timestamp；不公开 GLFW 数值常量 |
| `TextInputEvent` | 已提交 Unicode string、timestamp；旧版代理对在版本桥内合并，孤立代理项诊断并丢弃 |
| `TextCompositionEvent` | active/clear、full text、UTF-16 caret、block list、focused block、timestamp；仅 capability 支持时派发 |
| `FocusEvent` | `GAINED/LOST`、`FocusCause`、related component、timestamp |

`KeyCode` 是以稳定 canonical name 做 value equality 的 FandUI value class；名称只接受小写 ASCII 的点分层级。`Keys` 为常用 navigation/editing 键提供常量，并用经过范围校验的工厂覆盖字母、数字、function 和 keypad。未知键映射为 `Keys.UNKNOWN`；`KeyEvent.scanCode()` 暴露宿主物理 scan code 的不透明整数，未知时为 `-1`，不把 GLFW 或 Minecraft 类型带入公共 API，也不承诺该数值跨平台/键盘布局稳定。`PointerButton` 以从零开始的稳定 button index 做 value equality，前三个常量分别对应主键、次键和中键；不丢失额外鼠标键身份。事件中的 `List`/`Set` 均为不可修改快照。三版 click count 都由 FandUI 用同一 button、位置容差和 `250 ms` 单调时间窗口归一化，不依赖版本各自的 Screen 内部字段。

26.2 `PreeditEvent` 映射到 `TextCompositionEvent`；收到 native clear (`null`) 时派发规范化的 `active=false`、空 text/blocks、caret `0`、空 focused block。active composition 的 caret 和 block index 都在构造时校验；caret 使用 UTF-16 code-unit offset。1.20.1/1.21.4 的 `UiCapabilities.imeComposition()` 为 false 且不伪造 preedit；committed text 在三版保持一致。repeat action 由三个版本 Screen bridge 共同调用的 Core `KeyInputState` 派生，三版 `UiCapabilities.distinctKeyRepeat()` 均为 true；26.2 与两个旧版使用同一语义，不再把 26.2 误记为不可达。

焦点 API：

```java
package cn.fandmc.fandui.api.focus;

public interface FocusManager {
    java.util.Optional<cn.fandmc.fandui.api.component.UiComponent> focused();
    boolean request(cn.fandmc.fandui.api.component.UiComponent component);
    boolean move(FocusDirection direction);
    void clear();
}

public enum FocusDirection {
    FORWARD, BACKWARD, UP, DOWN, LEFT, RIGHT
}
```

默认 Tab traversal 先按非负 `tabIndex` 升序，再按已提交 layout/paint 顺序；负值从 traversal 中排除。方向导航使用同一 layout snapshot 的可见 bounds。不可见、disabled、detached 或 non-focusable component 的 `request` 返回 false；其他状态错误仍抛异常。pointer capture 在单鼠标模型下归当前 component 所有，up/cancel、component detach 或 session close 必须释放并派发 cancel，避免 stuck pressed state。

动画 API 每个 session 独立，不公开 GPU timing 或 Minecraft tick：

```java
package cn.fandmc.fandui.api.animation;

public interface AnimationManager {
    AnimationHandle start(AnimationSpec spec, AnimationFrameListener listener);
}

public interface AnimationHandle extends AutoCloseable {
    boolean active();
    java.util.concurrent.CompletableFuture<AnimationEndReason> completion();
    @Override void close();
}

@FunctionalInterface
public interface AnimationFrameListener {
    void frame(double progress);
}

@FunctionalInterface
public interface Easing {
    double transform(double progress);
}

public final class Easings {
    public static final Easing LINEAR;
    public static final Easing EASE_IN;
    public static final Easing EASE_OUT;
    public static final Easing EASE_IN_OUT;
}

public final class AnimationSpec {
    public static Builder duration(java.time.Duration duration);
    public java.time.Duration duration();
    public java.time.Duration delay();
    public Easing easing();
    public int iterations();
    public boolean infinite();
    public boolean alternate();

    public static final class Builder {
        public Builder delay(java.time.Duration delay);
        public Builder easing(Easing easing);
        public Builder iterations(int iterations);
        public Builder infinite();
        public Builder alternate(boolean value);
        public AnimationSpec build();
    }
}

public enum AnimationEndReason { COMPLETED, CANCELLED, SESSION_CLOSED, FAILED }
```

`UiSession.animations()` 返回该 manager。进度由实现注入的 monotonic clock 推进，首帧为 `0`、正常末帧恰好为 `1`；duration 必须为正、delay 非负，iteration 至少 1，独立 builder 方法表达 infinite。`Easing.transform` 输入固定在 `[0,1]`，必须返回有限值并满足端点 `0 -> 0`、`1 -> 1`；中间值允许 overshoot，listener 收到 easing 后的值。默认值为 zero delay、`Easings.LINEAR`、一次、不反向；`iterations(n)` 清除 infinite 标记，`infinite()` 进入无限模式，此时 `iterations()` 返回规范化占位值 `1` 且不参与终止判断。listener 只在 UI thread 调用，修改 component 属性会触发相应 invalidation；close 幂等取消。测试时钟是 core internal dependency，不作为 Mod-facing singleton 暴露。MVP 不增加 keyframe graph、physics solver 或跨 session timeline。

### 7.7 Canvas2D API

`Canvas2D` 仅存在于 `PaintScope` callback 期间，方法只追加 backend-neutral DisplayList command。它不暴露 NanoVG context、GL object、shader、framebuffer 或 native memory；callback 返回后使用同一 scope 立即抛 `IllegalStateException`。

```java
package cn.fandmc.fandui.api.canvas;

public interface Canvas2D {
    CanvasState save();
    void translate(float x, float y);
    void scale(float x, float y);
    void rotate(float radians);
    void transform(cn.fandmc.fandui.api.style.Transform2D transform);
    void setCompositeOperation(CompositeOperation operation);

    void scissor(cn.fandmc.fandui.api.layout.Rect rect);
    void intersectScissor(cn.fandmc.fandui.api.layout.Rect rect);
    void resetScissor();
    void clip(Path path);

    void fillRect(
            cn.fandmc.fandui.api.layout.Rect rect,
            cn.fandmc.fandui.api.style.Paint paint);
    void fillRoundedRect(
            cn.fandmc.fandui.api.layout.Rect rect,
            cn.fandmc.fandui.api.style.CornerRadii radii,
            cn.fandmc.fandui.api.style.Paint paint);
    void fill(Path path, cn.fandmc.fandui.api.style.Paint paint);
    void stroke(Path path, cn.fandmc.fandui.api.style.Paint paint, StrokeStyle style);

    void drawImage(
            cn.fandmc.fandui.api.resource.ImageRef image,
            cn.fandmc.fandui.api.layout.Rect destination,
            ImageSampling sampling,
            float opacity);
    void drawImage(
            cn.fandmc.fandui.api.resource.ImageRef image,
            cn.fandmc.fandui.api.layout.Rect source,
            cn.fandmc.fandui.api.layout.Rect destination,
            ImageSampling sampling,
            float opacity);
    void drawText(
            cn.fandmc.fandui.api.text.TextLayout text,
            cn.fandmc.fandui.api.layout.Point origin);
}

public interface CanvasState extends AutoCloseable {
    @Override void close();
}

public final class Path {
    public static PathBuilder builder();
    public cn.fandmc.fandui.api.layout.Rect bounds();
}

public final class PathBuilder {
    public PathBuilder moveTo(float x, float y);
    public PathBuilder lineTo(float x, float y);
    public PathBuilder quadTo(float controlX, float controlY, float x, float y);
    public PathBuilder bezierTo(
            float control1X, float control1Y,
            float control2X, float control2Y,
            float x, float y);
    public PathBuilder arc(
            float centerX, float centerY, float radius,
            float startRadians, float endRadians, ArcDirection direction);
    public PathBuilder rect(cn.fandmc.fandui.api.layout.Rect rect);
    public PathBuilder roundedRect(
            cn.fandmc.fandui.api.layout.Rect rect,
            cn.fandmc.fandui.api.style.CornerRadii radii);
    public PathBuilder close();
    public PathBuilder winding(PathWinding winding);
    public Path build();
}

public final class StrokeStyle {
    public static Builder width(float width);
    public float width();
    public LineCap cap();
    public LineJoin join();
    public float miterLimit();

    public static final class Builder {
        public Builder cap(LineCap value);
        public Builder join(LineJoin value);
        public Builder miterLimit(float value);
        public StrokeStyle build();
    }
}

public enum ArcDirection { CLOCKWISE, COUNTER_CLOCKWISE }
public enum PathWinding { SOLID, HOLE }
public enum LineCap { BUTT, ROUND, SQUARE }
public enum LineJoin { MITER, ROUND, BEVEL }
public enum ImageSampling { NEAREST, LINEAR }
public enum CompositeOperation {
    SOURCE_OVER,
    SOURCE_IN,
    SOURCE_OUT,
    SOURCE_ATOP,
    DESTINATION_OVER,
    DESTINATION_IN,
    DESTINATION_OUT,
    DESTINATION_ATOP,
    LIGHTER,
    COPY,
    XOR
}
```

`save()` 立即 push 完整 Canvas state，返回的 `CanvasState.close()` 恰好 restore 一次；重复 close 为 no-op。paint callback 结束时存在未关闭 state、restore 越界、非有限坐标、非法 path 或 clip depth 超限都会使本次 DisplayList 构建失败并保留上一完整 snapshot。

角度统一为 radians；path 坐标、stroke width、scissor、image destination 和 text origin 都是当前 transform 前的 logical pixel。public straight-alpha `Color` 在记录 paint 时转换为 premultiplied。`scissor`/`intersectScissor` 保留 NanoVG transform/extent 语义，只有后端证明轴对齐等价时才额外使用 hardware scissor；`clip(Path)` 使用第 8.6 节的 D24S8 path-clip stack。

`Path` 构建后不可变且可跨 frame/线程共享；`PathBuilder` mutable、单线程、build 后仍可继续追加并生成新的 copy。MVP 不加入 dashed stroke、custom shader、mesh upload、raw triangle、GPU filter 或离屏 layer API；这些都需要独立像素/资源生命周期设计。

### 7.8 资源与文字 API

资源 public identity 和 CPU source 都不包含 Minecraft 类型。`UiKey` 直接映射资源包中的 `assets/<namespace>/<value>`；例如 `demo:textures/ui/panel.png`。显式 runtime registration 在 active 期间遮蔽同 key 的资源包内容，关闭后恢复资源包解析。

```java
package cn.fandmc.fandui.api.resource;

public interface ResourceService {
    long generation();
    ImageRef image(cn.fandmc.fandui.api.UiKey key);
    cn.fandmc.fandui.api.text.FontFamily font(cn.fandmc.fandui.api.UiKey key);
    ResourceRegistration registerImage(
            cn.fandmc.fandui.api.UiKey key,
            ResourceSource source);
    ResourceRegistration registerFont(
            cn.fandmc.fandui.api.UiKey key,
            ResourceSource source);
    cn.fandmc.fandui.api.event.EventRegistration onReload(ResourceReloadListener listener);
}

@FunctionalInterface
public interface ResourceSource {
    byte[] load() throws java.io.IOException;
    public static ResourceSource bytes(byte[] bytes);
}

public interface ResourceRegistration extends AutoCloseable {
    cn.fandmc.fandui.api.UiKey key();
    ResourceKind kind();
    boolean active();
    @Override void close();
}

public interface ImageRef {
    cn.fandmc.fandui.api.UiKey key();
    ResourceState state();
    java.util.Optional<ImageInfo> info();
}

public final class ImageInfo {
    public ImageInfo(int width, int height);
    public int width();
    public int height();
}
public enum ResourceKind { IMAGE, FONT }
public enum ResourceState { UNRESOLVED, LOADING, READY, MISSING, FAILED }

@FunctionalInterface
public interface ResourceReloadListener {
    void reloaded(long oldGeneration, long newGeneration);
}
```

`image`/`font` 返回按 key intern 的稳定 API ref，不触发同步 I/O；实际 bytes 在 resource reload prepare 阶段读取。`ResourceSource.load` 在专用 reload worker 串行调用，每次调用必须返回调用方独占的 bytes；`bytes` 工厂在注册时防御性复制。registration 创建要求 UI thread；`close` 可从任意线程调用，立即把自身标为 inactive 并把精确移除排入 UI thread，重复 close 为 no-op。重复显式 `(kind,key)` 注册直接失败，不使用 last-wins。

新 resource generation 只有在全部必需 source、图片 decode、字体注册和 fallback 验证成功后才原子 apply；全局 reload 失败时不递增 generation，也不调用成功 listener。已有旧有效内容的 ref 继续保持旧 generation 的 `READY` 与旧 metadata，不能因候选 generation 失败而谎报 `FAILED`；从未成功解析的 ref 才按本次确定结果进入 `MISSING`（不存在）或 `FAILED`（读取、解码或校验失败）。`ImageRef.info()` 是任意线程可读快照，width/height 必须为正；`ImageInfo` 是不可变 value class，并实现基于 width/height 的 value equality。ref 从不暴露 texture id、pixels 或 decoder object。

历史结论（已被 2026-08-24 实现取代）：首版 image codec 只接受 static PNG。当前资源 codec 仍拒绝 JPEG、WebP、GIF/APNG animation 和视频，但新增了受限 SVG：reload worker 通过 `ResourceFormat` 提示或 signature 自动选择 SVG，安全解析后以 Java2D 一次性栅格化为 premultiplied RGBA8；编码上限 2 MiB、最大边长 4096、解码上限 64 MiB。inline SVG 则保持矢量 path，不创建纹理。PNG 原有 signature/chunk/CRC/APNG/尺寸预算校验不变。

文字 API 保存 shaping/layout 请求和纯 Java metrics，Skija 对象完全停留在 `fandui-text-skija`：

```java
package cn.fandmc.fandui.api.text;

public interface TextService {
    java.util.concurrent.CompletableFuture<TextLayout> layout(TextRequest request);
}

public record FontFamily(cn.fandmc.fandui.api.UiKey key) {}
public record FontWeight(int value) {
    public static final FontWeight NORMAL = new FontWeight(400);
    public static final FontWeight BOLD = new FontWeight(700);
}

public final class FontFamilies {
    public static final FontFamily DEFAULT =
            new FontFamily(cn.fandmc.fandui.api.UiKey.of("fandui", "font/default"));
}

public final class TextStyle {
    public static Builder builder(float fontSize);
    public java.util.List<FontFamily> families();
    public float fontSize();
    public FontWeight weight();
    public FontSlant slant();
    public cn.fandmc.fandui.api.style.Color color();
    public float lineHeight();
    public float letterSpacing();
    public float wordSpacing();
    public String locale();

    public static final class Builder {
        public Builder families(java.util.List<FontFamily> families);
        public Builder weight(FontWeight weight);
        public Builder slant(FontSlant slant);
        public Builder color(cn.fandmc.fandui.api.style.Color color);
        public Builder lineHeight(float lineHeight);
        public Builder letterSpacing(float letterSpacing);
        public Builder wordSpacing(float wordSpacing);
        public Builder locale(String locale);
        public TextStyle build();
    }
}

public final class TextRequest {
    public static Builder builder(String text, TextStyle style);
    public String text();
    public TextStyle style();
    public float maxWidth();
    public int maxLines();
    public TextWrap wrap();
    public TextOverflow overflow();
    public TextAlignment alignment();
    public TextDirection direction();

    public static final class Builder {
        public Builder maxWidth(float maxWidth);
        public Builder maxLines(int maxLines);
        public Builder wrap(TextWrap wrap);
        public Builder overflow(TextOverflow overflow);
        public Builder alignment(TextAlignment alignment);
        public Builder direction(TextDirection direction);
        public TextRequest build();
    }
}

public interface TextLayout {
    TextRequest request();
    long resourceGeneration();
    cn.fandmc.fandui.api.layout.Size size();
    float alphabeticBaseline();
    float ideographicBaseline();
    java.util.List<TextLine> lines();
    int unresolvedGlyphs();
}

public final class TextLine {
    public TextLine(
            int startUtf16,
            int endUtf16,
            float width,
            float height,
            float baseline);
    public int startUtf16();
    public int endUtf16();
    public float width();
    public float height();
    public float baseline();
}

public enum FontSlant { UPRIGHT, ITALIC, OBLIQUE }
public enum TextWrap { NONE, WORD, CHARACTER }
public enum TextOverflow { CLIP, ELLIPSIS }
public enum TextAlignment { START, CENTER, END, JUSTIFY }
public enum TextDirection { AUTO, LEFT_TO_RIGHT, RIGHT_TO_LEFT }
```

`FontFamilies.DEFAULT` 是不可注册、不可由资源包覆盖的保留逻辑 family，固定展开为 `FandUI Sans SC -> FandUI Emoji`。调用方显式 families 放在这两个 bundled correctness fallback 之前；fallback 始终追加，不能被移除。`TextService` 不读取 session/theme；标准 `TextComponent` 若要使用主题字体，必须从该组件公开的 `ThemeToken<TextStyle>` 解析出完整 `TextStyle` 后再创建 request，因此相同 request 在不同 session 中仍有唯一缓存语义。`FontWeight` 只接受 `1..1000`。font size 必须为正；line height 的 `0` 表示使用字体 metrics，显式值必须为正；spacing 可正可负。上述值与 max width 使用 logical pixel，除允许的 `maxWidth=+infinity` 外均要求有限。locale 使用规范化 BCP-47 tag。

`TextStyle.builder(fontSize)` 默认 families `[FontFamilies.DEFAULT]`、weight `NORMAL`、slant `UPRIGHT`、不透明白色、metrics line height、零 spacing 和 locale `und`。`TextRequest.builder(text,style)` 默认 max width `+infinity`、max lines `Integer.MAX_VALUE`、wrap `WORD`、overflow `CLIP`、alignment `START`、direction `AUTO`；max lines 至少为 `1`。`TextLine` 是不可变 value class，并实现基于全部五个稳定字段的 value equality；改用 final class 而不是 record，避免 minor 版本新增派生 accessor 时破坏 record component 契约。

`TextService.layout` 可从任意线程调用，按完整第 9.3 节 cache key 去重；每个调用方得到独立 dependent future，取消或手动完成该 future 不改变共享 cache job。future 在 text worker 完成并只返回 immutable Java metrics，不携带 Skija/native/GPU 对象。`TextLayout` 的 line range 使用 UTF-16 offset，集合防御性复制；`unresolvedGlyphs != 0` 视为诊断失败，标准 TextComponent 不静默显示 tofu 作为验收成功。

resource reload 后，旧 `TextLayout` 仍只属于其记录的 generation。已提交 snapshot 会持有旧 generation 直到替代 snapshot 完整可用；在新 snapshot 中绘制旧 layout 会令该 snapshot pending，并按其 `request()` 自动排队重排，不把旧 metrics 与新纹理混用。首次没有历史 snapshot 时，未就绪 Text/Image component 只跳过自身 content，但仍保留确定的约束尺寸和背景，避免阻塞 Render Thread。

普通 text block 先以请求颜色 raster 成 RGBA premultiplied，再按第 9.3 节规则分类到 A8 或 RGBA atlas。`Canvas2D.drawText` 只接受 `TextLayout`，因此自定义组件必须显式处理 asynchronous layout；标准 TextComponent 封装 request、future、generation invalidation 和 baseline。

### 7.9 公共 API 兼容、发布与测试门槛

公共 surface 仅为 `cn.fandmc.fandui.api.**`。`cn.fandmc.fandui.core`、`canvas` implementation、`text` implementation、`render`、`fabric` 和 `internal` 都不是 semver API，即使 class 为 public 也只供模块间实现使用，并在发布文档标为 internal。

发布形态：

- 独立发布纯 Java `cn.fandmc:fandui-api:<fandui-version>`，compile target Java 17；
- 三个 Fabric Mod JAR 分别发布带 Minecraft suffix/classifier 的构件，并各自嵌入同一构建产出的 API/core/canvas/text/renderer classes；
- 消费 Mod 对 `fandui-api` 使用 compile-only/mod-compile-only，对运行时 Fabric metadata 声明 `depends: { "fandui": ">=<required-version>" }`，不把 API JAR再次 include 进自身 JAR；
- 同一 release 的三个 Mod JAR 在 CI 解出 `cn/fandmc/fandui/api/**` 后逐文件 SHA-256 必须完全一致；
- FandUI 版本使用 semver。Minecraft 目标版本是构件维度，不修改公共 API 版本语义。

兼容规则：

- patch 不改变 public/protected descriptor 或行为前置条件；
- minor 可新增类型、overload、final class accessor，或具有真实保守默认语义的 interface default method；
- minor 不给 public interface 增加 abstract method，不给 public record 增加 component，不改变 enum 既有常量含义/顺序，不收紧合法输入；
- public API 删除、rename、descriptor 改变、class/interface 互换或既有返回语义破坏只进入 major；
- deprecation 至少保留一个 minor release，移除仍只进入 major；
- options/config 使用 final class + builder；record 只用于 `Point/Size/Rect/Color/UiKey` 等形状已闭合的值；
- `List/Set/Map/byte[]` 在所有 public 边界防御性复制；callback-scope 对象明确标记生命周期并做 use-after-scope 检查。

实现阶段增加四道 API gate：

1. source architecture test 拒绝 public API 引用 `net.minecraft`、`net.fabricmc`、`com.mojang.blaze3d`、`io.github.humbleui.skija`、`org.lwjgl`、NanoVG binding、renderer 或 Mixin package；
2. compiled class scan 检查 descriptor、generic signature、annotation、exception、record component 和 constant pool，平台 package 零引用；根 facade 对 `cn.fandmc.fandui.internal` binder 的实现引用单独 allowlist，但 public signature 仍为零；
3. 使用 japicmp `0.26.1` 对 `fandui-api/api-baseline/<version>/` 中明确冻结的 baseline 运行 source/binary 兼容 diff；缺失依赖必须通过显式 old/new classpath 解析，不使用 `--ignore-missing-classes`；
4. 编译并运行一个只依赖 `fandui-api` 的 Java 17 consumer fixture，再用同一 fixture 分别加载三个版本构件，验证 linkage 和相同 public behavior。

API unit tests至少覆盖 key/geometry/range validation、defensive copies、tree cycle/duplicate attachment、dirty propagation、measure-once、placement/z-order、event phase/stop semantics、pointer capture cleanup、focus traversal、animation endpoint/cancel、resource registration exact ownership、UTF-16 text range、stale generation 和 close idempotency。实现测试使用 JUnit 5；具体版本随 Gradle 构建元数据验证后锁定。

### 7.10 第一版线程与失败矩阵

| API 操作 | 允许线程 | 失败语义 |
|---|---|---|
| `FandUI.runtime`、availability/capabilities snapshot | 任意线程 | bootstrap 前 `IllegalStateException` |
| `UiRuntime.execute`、`TextService.layout` | 任意线程 | future 异常完成 |
| Screen open、HUD mount、resource register、attached tree mutation | UI thread | 错误线程/状态抛异常 |
| session `close`、registration `close` | 任意线程 | 首次标记 inactive 并排队 detach；重复 no-op |
| measure/paint/event/focus/animation callback | UI thread，同 session 串行 | 当前 snapshot 失败，上一完整 snapshot 保留 |
| ResourceSource load、Skija layout/raster | 各自单 worker | 新 generation/request 失败，不污染旧结果 |
| Canvas/NanoVG compile | 单 canvas worker | session-local compile failure，旧 compiled frame 完成本帧后以 `FAILED` 关闭该 session |
| GPU upload/draw/delete | Render Thread | 资源级输入错误拒绝对应新 snapshot；context/target/state restore 失败把 runtime 转为 `FAILED` 并关闭全部 session |

`UiCapabilities` 是任意线程可读、runtime 生命周期内稳定的快照。`UiUnavailableException.availability()` 保存拒绝 open/mount 时的精确状态，异常 message 使用该 snapshot 的 `detail`；普通参数、线程和 ownership 错误仍使用 `IllegalArgumentException` 或 `IllegalStateException`，不混入 renderer availability。

失败隔离边界固定为 session、resource request、runtime 三层：component/DisplayList/NanoVG 输入错误只关闭产生它的 session；单个 image/text load 或 layout 失败通过 ref/future 暴露，标准组件保留上一成功内容且 session 继续；OpenGL context 丢失、主 target 不匹配或状态无法完整恢复属于 renderer invariant 破坏，runtime 原子进入 `FAILED`，停止后续 GPU 工作并以 `FAILED` 关闭全部 session。任何层都不在 Render Thread 同步重试 I/O、text 或 native compile。

## 8. NanoVG renderer 设定与已废止 JNI 方案

### 8.0 当前生效实现

**状态：已完成。** 运行时使用三个 Minecraft 版本各自匹配的 `org.lwjgl:lwjgl-nanovg` 与公开 `NanoVGGL3` backend：1.20.1 为 `3.3.2`、1.21.4 为 `3.3.3`、26.2 为 `3.4.1`。公共 API、Core 和 DisplayList 不引用 LWJGL/NanoVG/OpenGL 类型。

- `NanoVgGl3Renderer` 在 Minecraft 当前 OpenGL context 和颜色目标上回放 immutable DisplayList；不创建第二 context、窗口或 Swapchain；
- 标准 path/fill/stroke/image/scissor 直接使用 NanoVG；任意 path clip 使用按 target 尺寸复用且有显存预算的颜色 layer 与 alpha mask 合成；
- 多 stop gradient 使用有界 premultiplied RGBA lookup texture cache；Skija A8 texture 通过 GL swizzle 映射为 NanoVG 可采样 RGBA，并以 `NVG_IMAGE_NODELETE` 保持 FandUI cache 所有权；
- backdrop blur 使用区域化、多级抗混叠降采样、可分离模糊和形状 mask，同一 target 尺寸复用资源，resize 时自动重建；
- vendored NanoVG C core、自有 JNI/CMake/native classifier、FUDL/FUBT 和自有 batch shader 已从源码与构建删除。

下文 8.1 的上游 stencil/premultiplied 研究仍是像素语义依据；8.2-8.6 中关于自定义 `NVGparams`、JNI 和 batch ABI 的路线是保留的历史设计，已废止，不得作为当前实现输入。

### 8.1 固定参考源码与已核验绘制语义

NanoVG C 核心固定研究基线：上游 commit `ce3bf745eb2d2dbc14a50bf2446783f691ac4353`。

| 文件 | SHA-256 |
|---|---|
| `nanovg_gl.h` | `D23547851190DF222EA0270D05A5CE18D7C885BBB63B70DEC794806EB5719F7C` |
| `nanovg.c` | `D70F33B6DECFA0F7606A30C36957A5E7E4E3C8053416649C0686D2A694B855B6` |
| `nanovg.h` | `736401D6A8B9CB52EB1E2A43F3103A47C7C5C12558D159AE8740F6F4D4475E22` |

这里读取 `nanovg_gl.h` 用于确认 NanoVGGL3 renderer contract、stencil 和 premultiplied alpha 语义；当前运行时正是通过版本匹配的 LWJGL `NanoVGGL3` 调用该 backend。

已从参考 backend 确认：

- 非凸 fill 先关闭颜色写入，对 front face 执行 `INCR_WRAP`、back face 执行 `DECR_WRAP`，形成 winding stencil；
- 抗锯齿 fringe 只在 stencil `EQUAL 0` 时绘制；cover quad 在 stencil `NOTEQUAL 0` 时绘制，并以 `ZERO` 清除本次 scratch；
- convex fill 直接绘制 fill 与 fringe，不走 stencil cover；
- 启用 `NVG_STENCIL_STROKES` 时，第一遍 stroke 在 stencil `EQUAL 0` 下绘制并 `INCR`，第二遍绘制 AA fringe，第三遍关闭颜色写入并用 `ZERO` 清除 stencil；
- NanoVG scissor 不是单纯的硬件矩形裁剪。`glnvg__convertPaint` 将 inverse scissor transform、extent 和按 fringe 归一化的 scale 写入 fragment uniform；硬件 Scissor 只可在证明等价时作为优化，或作为更粗的拒绝区域；
- `glnvg__convertPaint` 对 paint 的 inner/outer color 做 premultiplied alpha；默认正常混合对应 `ONE, ONE_MINUS_SRC_ALPHA`；
- image shader 区分已预乘 RGBA、未预乘 RGBA 和 alpha texture，FandUI batch protocol 不能把三者折叠成同一种采样语义。

这组语义是三版本共享 OpenGL 后端的 golden reference。实现可以改变资源和命令表达方式，但像素结果、draw 顺序、stencil 清理和 premultiplied alpha 语义必须一致。

### 8.2 Renderer contract 与限制

LWJGL NanoVG 3.4.1 中 `nvgCreateInternal`、`nvgInternalParams`、`nvgDeleteInternal` 是私有绑定，Java 侧没有公开 `NVGparams`。因此 Java 代码不能只用公开 LWJGL NanoVG API 注册自定义 renderer。

#### 8.2.1 现成 LWJGL NanoVGGL3 复核与采用边界

2026-08-22 按用户提出的“已有完整 NanoVG 库”重新核对实际依赖和公开 API，结论如下：

- 三个 Fabric 模块的 `runtimeClasspath` 实际解析出的 LWJGL core 分别为：1.20.1 `3.3.2`、1.21.4 `3.3.3`、26.2 `3.4.1`；三版 Minecraft 均未携带 `org.lwjgl:lwjgl-nanovg`，若采用必须由 FandUI 额外分发与各版匹配的 Java binding/native；
- LWJGL `NanoVGGL3.nvgCreate()`、`nvgDelete()` 和 `nvglCreateImageFromHandle()` 是可直接使用的完整 OpenGL 3 backend；它会在当前 OpenGL context/FBO 上发出 GL draw，因此在当前 OpenGL 产品范围内不存在“必须为 Vulkan 重写 renderer”的理由；
- 公开 NanoVG clip API 只有 `nvgScissor`、`nvgIntersectScissor`、`nvgResetScissor`，上游明确说明相交结果始终为矩形；没有任意 path clip API。直接改用 GL3 backend 会丢失当前 `Canvas2D.clip(Path)` 的嵌套 convex/non-convex depth+stencil 语义；
- 上游 `nvgLinearGradient` 与 `nvgRadialGradient` 各只接收 inner/outer 两种颜色，而 FandUI `DisplayPaint` 已支持并验证 `2..256` 个有序 stop；直接回放只能丢弃中间 stop，或另行实现渐变纹理生成与缓存；
- `nvglCreateImageFromHandleGL3` 在上游实现中无条件把借入纹理登记为 `NVG_TEXTURE_RGBA`。它不能正确描述当前 `GL_R8` Skija A8 文字纹理；可以改为由 NanoVG 自己上传 alpha image，但这会替换现有 texture cache 所有权和资源代际边界，不是零成本换 binding；
- LWJGL 3.4.1 仍把 `nvgCreateInternal`、`nvgInternalParams`、`nvgDeleteInternal` 的函数地址保留为 package-private 字段，且不公开 `NVGparams` struct binding；公开 Java API不能注册 FandUI 当前的 batch callbacks。

**已废止决定：** 当时曾决定保留自有 C core/JNI 以避免丢失任意路径裁剪、多 stop 渐变和 A8 纹理语义。后续实现证明这些语义可在 stock NanoVGGL3 周围以临时 layer/mask、lookup texture 和 texture swizzle 补齐，并通过既有像素/state 门禁，因此当前路线已迁移到 8.0，旧 JNI/batch 路线不再维护。

固定源码中的 `NVGparams` 已确认包含：texture create/delete/update/size、viewport、cancel、flush、fill、stroke、triangles 和 delete callbacks。`renderFill/renderStroke` 接收已经 tessellate 的 `NVGpath`，每条 path 分别给出 fill/stroke `NVGvertex` span；`NVGvertex` 固定语义为四个 float：`x,y,u,v`。该 contract 没有 index 数据。

参考 fragment uniform 的完整语义为：12-float scissor matrix、12-float paint matrix、两个 premultiplied RGBA color、scissor extent/scale、paint extent、radius、feather、stroke multiplier/threshold、texture type 和 shader type。FandUI 序列化这些字段的语义值，不复制编译器相关的 C struct padding。

### 8.3 JNI 调用模型

> **已废止历史设计：** 本节至 8.6 仅用于解释早期 FUDL/FUBT 产物和迁移原因；当前仓库不存在这些运行时组件。

- 自有 JNI 库编译固定版本的 NanoVG C 核心；
- native 层构造 `NVGparams` 并实现 renderer callbacks；
- MVP 只有一个 canvas compiler worker，并独占一个 native NanoVG context；context handle 只存在于 `cn.fandmc.fandui.internal`，不进入公共 API。只有 profiling 证明 tessellation 是瓶颈且资源映射可保持确定性时才增加 worker；
- Java 输入与输出都使用 direct `ByteBuffer`，native 入口检查 direct/address/capacity、ABI、总长度、每条记录长度、计数上限、有限 float、资源 ID和栈深；
- DisplayList 编码为带 magic、独立 major/minor ABI、total length 和 command count 的二进制命令流；
- 正常帧用一次 JNI 调用消费整份 DisplayList；输出 buffer 不足时返回 typed `OUTPUT_TOO_SMALL + requiredBytes`，Java 扩容后重试并记录高水位，不允许 native 越界写；
- callbacks 只向调用方提供的输出 buffer 写 vertex/uniform/batch/texture-update section，不逐次回调 Java；
- 输出 buffer 归属 Java 的有界池；编译完成后以只读不可变视图交给 Render Thread，消费完成才归还，避免 native arena 与跨帧并发产生悬空地址；
- 单一 OpenGL renderer 消费 backend-neutral batch，三个版本桥不得复制 batch 编译逻辑；
- 图片和 Skija atlas 通过统一 texture key 解析；
- 只合并 texture、pipeline、clip、blend 相同且顺序相邻的 batch。

JNI 返回值使用固定宽度 status/size，不以 Java exception 表示可预期的 buffer grow。错误至少区分：`BAD_MAGIC`、`ABI_MISMATCH`、`TRUNCATED`、`INVALID_OPCODE`、`INVALID_NUMBER`、`LIMIT_EXCEEDED`、`OUTPUT_TOO_SMALL` 和 `NANOVG_FAILURE`。失败帧调用 `nvgCancelFrame` 并保留上一份完整 compiled frame，不提交半成品。

### 8.4 二进制 ABI 草案

共同规则：little-endian、IEEE-754 float32、记录起点 8-byte 对齐、所有 offset 相对 buffer 起点、所有区间先检查 `offset + count * stride <= totalBytes`。不序列化 Java enum ordinal、native pointer、`size_t` 或未经固定宽度转换的 C enum。

Java -> native command stream：

- 固定 48-byte header：4-byte ASCII magic `FUDL`、`u16 major`、`u16 minor`、`u32 headerBytes`、`u32 totalBytes`、`u32 commandCount`、`u32 flags`、`u64 frameId`、`f32 logicalWidth`、`f32 logicalHeight`、NanoVG 所需的标量 `f32 devicePixelRatio`、必须为 `0` 的 `u32 reserved`；首条 record 因而自然 8-byte 对齐；
- `devicePixelRatio` 直接取 Minecraft 当前单一 GUI scale；物理 target width/height 由 RenderHost 提供，不从 float 反推。若宿主出现无法用同一标量解释的 X/Y 映射，当前 bridge 诊断并跳过该帧，不能把 NanoVG fringe/tessellation 偷换成两个比例；
- major 必须完全相同；producer minor 不得高于 consumer minor。未知 opcode 一律返回 `INVALID_OPCODE`，不能静默跳过会改变像素结果的绘制命令；minor 只用于双方都认识的尾部字段/新增 opcode 协商；
- 每条 command 以 `u16 opcode`、`u16 flags`、`u32 recordBytes` 开头；`recordBytes` 包含 header、payload 和尾部对齐 padding，且至少为 8、必须为 8 的倍数；
- opcode 覆盖 save/restore、transform、composite、scissor、path begin/move/line/bezier/arc/rect/rounded-rect/close/winding、fill/stroke paint、fill/stroke、image triangle 和 FandUI path clip push/pop；
- 每个会产出 draw 的 record 显式携带原始 DisplayList 的 `u32 commandIndex`；text command 不进入 JNI stream，由 Java text compiler 使用同一 command index 生成批。

已实现的 FUDL `1.0` opcode 空间与固定记录如下；所有枚举均由显式映射产生，不写 Java ordinal：

- state/transform：`SAVE=0x0001`、`RESTORE=0x0002`、`TRANSLATE=0x0010`、`SCALE=0x0011`、`ROTATE=0x0012`、`TRANSFORM=0x0013`、`SET_COMPOSITE=0x0014`、`SET_GLOBAL_ALPHA=0x0015`；
- scissor：`SCISSOR=0x0020`、`INTERSECT_SCISSOR=0x0021`、`RESET_SCISSOR=0x0022`；
- path：`BEGIN_PATH=0x0100`，其后依次预留 `MOVE_TO` 到 `PATH_WINDING` 的 `0x0101..0x0109`；
- paint/style：`SET_FILL_PAINT=0x0200`、`SET_STROKE_PAINT=0x0201`、`SET_STROKE_STYLE=0x0202`；
- draw：`FILL=0x0300`、`STROKE=0x0301`、`CLIP_PUSH=0x0302`、`DRAW_IMAGE=0x0303`；
- paint record 固定 32-byte prefix，随后每个 premultiplied stop 为 24 bytes；solid 恰好一个 stop，linear/radial 为 `2..256` 个 stop。Java 流保留全部 stop，native callback 不得只保留首尾颜色；
- `DRAW_IMAGE` 固定 80 bytes，包含 `u64 textureKey`、实际 `u32 width/height`、source-region flag、源/目标矩形、sampling 与 opacity。宽高来自当前 `ImageRef.info()`；没有已解析尺寸时 encoder 立即拒绝，不能让 native 猜测纹理尺寸；
- v1.0 limits 固定为总长 `64 MiB`、records `1,000,000`、save depth `1024`、path clip depth `8`、gradient stops `256`。

`Path` 不公开 element 集合。API artifact 内新增中立的 `cn.fandmc.fandui.internal.canvas.InternalPath`/`PathVisitor`，由 immutable `Path` 回放 move/line/quad/bezier/arc/rect/rounded-rect/close/winding；该桥不包含 renderer、JNI、NanoVG 或 LWJGL 类型，FUDL encoder 位于 `fandui-canvas` 的 internal package。

Native -> Java batch stream：

- 固定 48-byte header：4-byte ASCII magic `FUBT`、独立 batch `u16 major/minor`、`u32 status`、`u32 headerBytes`、`u32 totalBytes`、`u32 sectionCount`、原样 `u64 frameId`、`u64 requiredBytes` 和保留的 `u64`；`OUTPUT_TOO_SMALL` 时 `requiredBytes` 给出完整所需容量；
- section directory entry 固定 24 bytes：`u32 type`、`u32 flags`、`u64 offset`、`u32 count`、`u32 stride`，明确给出 vertex、uniform、batch 和 texture update section；
- vertex record 固定 16 bytes，对应 `x,y,u,v`；
- uniform record 固定 176 bytes：42 个 float32 语义字段加两个稳定 `u32` texture/shader type；
- batch record 包含 `orderKey`、pass kind、primitive topology、vertex first/count、uniform index、texture key、sampler/image mode、blend factors、fragment scissor、path clip depth 和写掩码；
- NanoVG contract 原生没有 index，MVP 的 `indexCount=0`，使用 triangle fan/strip/list 的非索引 draw。只有 profiling 证明重索引有收益时才扩 minor ABI；
- `orderKey` 固定为 `((u64) commandIndex << 3) | subpass`，每条 DisplayList command 有 8 个 subpass slot；同一 fill/stroke 的 winding/fringe/cover/clear 子 pass 连续，同一 key 下的多个 native draw span 保持输出顺序，Java text batch 按同一 key space 稳定归并，禁止跨 command 重排。

图片的 native `int image` 只作为 NanoVG context 内部句柄；输出使用 FandUI 64-bit texture key。texture callbacks 维护 width/height/type/flags 与外部 key，并生成 upload/update/delete 记录，不在 native 层创建 OpenGL texture。

### 8.5 批语义

批 pass 至少区分：convex fill、winding accumulate、fill fringe、fill cover+stencil clear、plain stroke、stencil stroke base、stencil stroke fringe、stencil stroke clear、image triangles、clip push 和 clip pop。blend factor 使用 FandUI 固定枚举映射全部 `NVGblendFactor`，默认仍为 premultiplied source-over。

Scissor matrix/extent/scale 始终进入 fragment uniform。只有最终变换后的裁剪可证明为 device-axis-aligned rectangle 时，backend 才同时设置等价硬件 Scissor；旋转/斜切状态不能仅靠硬件矩形代替。

### 8.6 Stencil/clip 方案

- NanoVG scissor 状态按其 transform/extent 语义保存；可证明等价的轴对齐矩形才折叠为硬件 Scissor 交集；
- 任意路径 clip 使用 FandUI D24S8；
- depth 分量以可精确重复写入的离散值编码嵌套 path clip depth，常规 draw 使用 `EQUAL currentDepth` 且禁止 depth write；
- stencil 分量保留给 NanoVG 非凸 fill 的 winding/scratch；
- convex push clip：只在 depth 等于父层级的路径内部写入子层级；
- non-convex push clip：先在父 depth 下用 stencil winding 得到路径内部，再由 cover pass 写子 depth 并清 stencil；
- pop clip：绘制 clip bounds/fullscreen cover，depth compare 只接受当前子层级，并把通过的像素写回父层级；无需保留和重放原路径几何；
- 每帧只清 FandUI 自有 D24S8，不触碰 Minecraft 主 depth。

该方案避免把 8 位 stencil 同时分给嵌套层级和 winding。path clip 边缘在 MVP 中是 binary coverage；若产品实际需要抗锯齿 clip mask，必须另做经测量的 mask 方案，不能假装 depth 能保存部分 coverage。当前整体仍是设计决定，必须用多层 convex/non-convex、push/pop sibling、scissor+path 组合在三个 OpenGL 版本原型中证明。

## 9. Skija 文字管线设定

### 9.1 版本、API 与像素探针

当前研究基线：`io.github.humbleui:skija-shared:0.143.17`。实际 Java package 为 `io.github.humbleui.skija`，不能写成其他历史 package。

已确认的精确 API 能力：

- `FontMgr.makeFromData(Data[, int])`、`FontMgr.matchFamilyStyleCharacter(...)`；
- `Typeface.getTableTags()`、`Font.getPath(short)`；其中源码明确说明 bitmap glyph 的 `getPath` 返回 `null`；
- `TypefaceFontProvider.registerTypeface(Typeface[, String])`；
- `FontCollection.setAssetFontManager/setDynamicFontManager/setDefaultFontManager/setEnableFallback`；
- `ParagraphBuilder.addText/build`、`Paragraph.layout(float)`、`Paragraph.paint(Canvas,float,float)`；
- `Paragraph.getLineMetrics/getAlphabeticBaseline/getIdeographicBaseline/getUnresolvedGlyphsCount`；
- `Shaper.shape(..., RunHandler)`，`RunHandler.commitRun` 可收到 run font、glyph id、position 和 cluster；
- `ImageInfo(width,height,ColorType,ColorAlphaType)`；
- `ColorType.ALPHA_8`、`ColorType.RGBA_8888`、`ColorAlphaType.PREMUL`；
- raster `Surface.makeRaster/makeRasterDirect/makeRasterN32Premul`；
- `Surface.peekPixels/readPixels` 与 `Pixmap.getRowBytes/computeByteSize/getBuffer`。

2026-08-21 Windows x64 内存内 JShell 探针（Java 25，Skija `0.143.17`）：

| 探针 | 字面结果 | 状态 |
|---|---|---|
| `ALPHA_8 + PREMUL`，96x64，Segoe UI `FandUI` | `peek=true`，`rowBytes=96`，`bytes=6144`，非零像素 `813` | 已完成 |
| `RGBA_8888 + PREMUL`，96x96，Segoe UI Emoji U+1F600 | alpha 像素 `4172`，其中彩色像素 `4057` | 已完成 |
| `Typeface.getTableTags()` on Segoe UI Emoji | 包含 `COLR`、`CPAL` | 已完成 |
| 中英 Emoji 混排 paragraph，wrap width 170 | `unresolved=0`，`lines=3`，height `111.0` | 已完成 |
| 混排 paragraph RGBA 像素 | alpha 像素 `3129`，彩色像素 `810`，任一 RGB 分量大于 alpha 的 premul 违规 `0` | 已完成 |
| `LineMetrics` index 单位 | 文本 Java UTF-16 length `25`，最终 line end index `25` | 已完成 |

最后一项确认 Paragraph 的 range/line index 必须按 Java UTF-16 code-unit 下标处理，不能用 Unicode code point index 或 UTF-8 byte offset。`LineMetrics.getHeight()` 是包含当前行的累计 paragraph 高度；单行高度使用 `getLineHeight()`。

### 9.2 固定职责与线程边界

Skija 负责：

- TTF/OTF 字体加载；
- 中文字体回退；
- Unicode shaping；
- 换行和 baseline；
- CPU 文字栅格化；
- 普通文字 mask 和彩色 Emoji bitmap；
- 文本布局与纹理缓存键材料。

Skija `0.143.17` 源码没有对 `FontCollection`、`Paragraph`、`Surface` 等对象给出一般性的跨线程安全保证。FandUI 当前设计决定是由单个 text worker 串行拥有全部 Skija native 对象和 layout/raster cache；只把纯 Java不可变 metrics 与像素 buffer 交给 Render Thread。`Library.load()` 自身是 synchronized，不代表其余对象可并发访问。所有 `Managed`/`RefCnt` 对象显式 close，不依赖 Cleaner 时机。

字体注册顺序：资源包字体通过 `Data -> FontMgr.makeFromData -> TypefaceFontProvider.registerTypeface(alias)` 进入 dynamic/asset manager；系统 `FontMgr` 只作为 default fallback。`TextStyle` 明确给出主题主字体、CJK fallback 和 Emoji fallback 家族，并设置 locale/direction；不得依赖各 OS隐式 fallback 顺序来生成发布验收截图。

### 9.3 文本块 raster 与 atlas

**已废止草案**：早期计划从 Paragraph 结果重新构造逐 glyph A8/RGBA atlas。当前 API不公开 Paragraph 最终 fallback run 列表；另用 Shaper 重排会产生与 Paragraph 换行、fallback 或 bidi 不一致的风险。MVP 不实现这套重复排版。

现行 KISS 管线：

管线：

```text
ResourceSource/UiKey
  -> 字节内容哈希与字体注册
  -> request 显式字体族 + FandUI Sans SC + FandUI Emoji
  -> Paragraph shaping / wrapping / baseline / UTF-16 metrics
  -> CPU RGBA premultiplied text-block raster
  -> 纯单色块提取 alpha，进入 A8 text-block atlas
  -> 含彩色 glyph 的完整块进入 RGBA premultiplied text-block atlas
  -> Render Thread 增量上传
  -> TextBatch
```

每个 cache miss 由 Skija 按请求文字颜色 raster 为 RGBA premultiplied。像素若全部符合“请求颜色乘 alpha”的单色关系，则只保留 alpha channel，最终颜色由 GPU uniform 提供；一旦发现彩色像素，则保留整个 RGBA block，使混排中的普通字形颜色与 Emoji palette 都保持 Skija 结果。该分类算法必须用不同文字颜色、COLR/CPAL、bitmap Emoji 和边缘 AA corpus 继续验证；分类不确定时保留 RGBA，优先正确性。

布局缓存键包含：文本、字体栈和各字体内容哈希、字号、weight/style、locale、direction、wrap width、line height、letter/word spacing、font features、max lines/ellipsis 和 shaping 选项。

raster 缓存键在布局键基础上增加：量化 device scale、edging/hinting/subpixel 配置、像素 padding、颜色模式。A8 block 的最终文字颜色不进入 GPU atlas key；RGBA block 的文字颜色进入 key。GPU entry 另带 backend/device generation 和 atlas page generation。

文本块 atlas 必须有显式 byte budget、LRU eviction 和 generation；淘汰只移除未来引用，实际 GPU 资源按宿主延迟销毁。页尺寸、预算、大块独立 texture 阈值均由原型数据决定，不提前写死。

### 9.4 Skija native 构件

Maven Central `0.143.17` 已确认六个桌面 classifier 均存在，JAR 内资源路径和 `skija.version` 与 OS/arch 对应：

| 构件 | SHA-256 |
|---|---|
| `skija-shared-0.143.17.jar` | `6213E04A09853FF4A2A2DDC63554981BC5F57AA82CC795F4CD9167ACA7EDB042` |
| `skija-windows-x64-0.143.17.jar` | `7178C082EC5EE800353F8740E6A3EEE743240CFD9B1ED1494BAEBE4034B269BD` |
| `skija-windows-arm64-0.143.17.jar` | `E866E381C01EDD22C285EA5AC24755B8CA6F9B6BF54C75073B368DF567A622D0` |
| `skija-linux-x64-0.143.17.jar` | `8CB4AD7D9952016CC90FCA1831711380AC202FAE6AFB0F40519BE4F9B456BC67` |
| `skija-linux-arm64-0.143.17.jar` | `937F117A87B974A3734B75DFE8081FA643D566A2DAEF085F33200095400B5C68` |
| `skija-macos-x64-0.143.17.jar` | `54BD501D7DA225B15A7ADED416BFC7F33D360526A8AF5DD20966864EA3805087` |
| `skija-macos-arm64-0.143.17.jar` | `0010E077BFE8FEC4ADFA776EB85C47C91F04F07EEC1123ABC9B6CCB2BA7A1E20` |

Windows JAR 包含 `skija.dll` 和 `icudtl.dat`；Linux 包含 `libskija.so`；macOS 包含 `libskija.dylib`。每个 classifier 都含字面值 `0.143.17` 的 `skija.version`。Skija `Library` 默认解压到 `java.io.tmpdir/skija_<version>_<arch>` 后 `System.load`，满足“native 文件带版本或内容哈希”中的版本要求。

`skija-shared:0.143.17` 内嵌 POM 明确声明运行依赖 `io.github.humbleui:types:0.2.0`。漏掉它时，native `_nAfterLoad` 解析 `io.github.humbleui.types.IPoint` 产生 `NoClassDefFoundError`，当前 Windows x64 native 随后会使 Java 17/21/25 都发生 `EXCEPTION_ACCESS_VIOLATION`，而不是干净地抛回 Java。加入 POM 指定依赖后，三套 JDK 均正常退出。因此三个 Fabric runtime 必须显式验证传递依赖存在；不能只把 shared/native 两个 Skija JAR 手工放入 classpath。

待验证：

- `0.143.17` 在三个版本 Loom runtime classpath 的依赖冲突；
- 六种桌面 native classifier 在对应真实 OS/CPU 上的加载；当前只执行 Windows x64；
- text worker 的 reload/close/并发压力测试；
- 非白文字颜色、复杂 Emoji、bitmap/SVG/COLR 字体的 RGBA 分类 corpus；
- atlas 页尺寸、预算、淘汰策略和实际显存基线。

### 9.5 CJK/Emoji 候选、许可与确定性 fallback

MVP 字体候选已经锁定为未修改的上游发行文件：

| 用途 | 上游坐标 | 文件大小 | SHA-256 | Git blob |
|---|---|---:|---|---|
| 中文/Latin | `notofonts/noto-cjk` tag `Sans2.004`，commit `523d033d6cb47f4a80c58a35753646f5c3608a78`，`Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf` | `16,437,364` | `2C76254F6FC379FDDFCE0A7E84FB5385BB135D3E399294F6EEB6680D0365B74B` | `dc15562470b4f842321894787a0d066879ccff8b` |
| 彩色 Emoji | `googlefonts/noto-emoji` annotated tag `v2.051`，peeled commit `8998f5dd683424a73e2314a8c1f1e359c19e8742`，`fonts/NotoColorEmoji.ttf` | `10,673,480` | `72A635CB3D2F3524C51620CDDE406B217204E8A6A06C6A096FF8ED4B5FD6E27B` | `943741df1e37aa3ee3b5afc9129843fd8d81908c` |

两份字体都使用 SIL Open Font License 1.1。对应上游 `LICENSE` 已逐字节比对：Noto CJK 为 `4,301` bytes / SHA-256 `6A73F9541C2DE74158C0E7CF6B0A58EF774F5A780BF191F2D7EC9CC53EFE2BF2`，Noto Color Emoji 为 `4,330` bytes / SHA-256 `500BB1CCF43DF7BBB522112F9133A52B16E1C35E809632F5D8609B179152DE5B`。许可允许字体随软件原样捆绑和再分发，但发布构件必须同时携带版权/许可文本；当前不做字体修改或子集化。两个字体原始合计 `27,110,844` bytes，MVP 接受该 correctness-first 成本，实际 JAR 体积仍归发布构建测量。

确定性规则：

- 字节按上述 SHA-256 校验后，以运行时别名 `FandUI Sans SC`、`FandUI Emoji` 注册到仅含捆绑字体的 `TypefaceFontProvider`；别名不改写字体文件；
- 调用 Skija `TextStyle.setFontFamilies` 前，FandUI 已把 request 中的逻辑 family 解析成显式“资源字体 -> `FandUI Sans SC` -> `FandUI Emoji`”列表，并固定 locale/direction；主题若参与，只能在标准组件生成 request 之前解析为显式 `TextStyle`，text worker 不读取 session；发布截图和缓存键不依赖 OS 字体枚举顺序；
- `FontCollection.defaultFallback(codePoint, ...)` 对当前只注册到 asset provider 的这组字体返回 `null`，因此它不作为 FandUI 的 fallback 解析入口。实际路径以显式 family stack + `Paragraph` 的 unresolved 结果为准；
- 字体内容 SHA-256、family stack、Skija version、locale/direction 和 shaping 参数都进入布局缓存键；`FontMgr.useSystemRenderingParams(false)` 消除系统 raster 参数差异。

Windows x64 上，同一编译产物分别由 Zulu Java `17.0.16`、`21.0.8`、`25.0.2` 运行，三次退出码均为 `0` 且字面输出完全一致：CJK face 对 `A`/`中` 的 glyph ID 为 `34/9544`、对 U+1F600 为 `0`；Emoji face 对前两者为 `0/0`、对 U+1F600 为 `883`。显式 stack 解析为 `[Noto Sans CJK SC, Noto Color Emoji]`；中英混排、U+1F600、`👩‍💻`、`❤️`、`🏳️‍🌈` 全部 `unresolved=0`。逐样本彩色像素分别为 `0/995/548/915/1036`，所有样本 premultiplied-alpha 违规均为 `0`。这证明当前候选在 Windows x64 和三套目标 JDK 上满足确定性 fallback 与彩色 Emoji MVP；其他五个 native 平台仍归跨平台项。

## 10. 每帧时序与资源生命周期

### 每帧顺序

```text
Minecraft/Fabric events
  -> 版本桥归一化输入
  -> capture/target/bubble + focus/state
  -> 单调时钟推进 animation
  -> dirty style/layout
  -> immutable layout snapshot
  -> immutable DisplayList
  -> Skija layout/raster 与 upload queue
  -> 一次 NanoVG JNI tessellation
  -> 合并 path/image/text batches
  -> RenderHost.beginFrame（Render Thread）
  -> GPU uploads
  -> OpenGL draw
  -> RenderHost.endFrame
  -> Minecraft blit/submit/present
```

如果当帧 CPU 结果未完整就绪，沿用上一份完整快照，不能提交半构建 DisplayList。

布局需要的新文字 metrics、NanoVG compiled frame 或 resource generation 尚未完成时，该 frame 保持上一份完整结果；首次无旧结果时只跳过未就绪节点，不阻塞 Render Thread。text worker 与 canvas compiler 各自单线程串行拥有 native context，Render Thread 只消费不可变输出并执行上传/绘制。

### 资源代际

- **Device generation**：启动时捕获 GPU device/context identity；identity 改变会使全部 backend 资源立即失效。MVP 在版本 host 证明可恢复流程前把 runtime 转为 `FAILED`，不伪造进程内热重建；
- **Target generation**：framebuffer size、颜色 handle 或 format 改变时重建 target view 和 D24S8；
- **Scale generation**：GUI scale 导致逻辑 viewport 变化时重算布局；仅 device-pixel ratio 变化时保留逻辑 layout，但使 text raster、fringe、clip 和对应 GPU key 失效；
- **Resource generation**：resource reload 创建新字体/图片注册表并原子切换；
- **Frame generation**：upload buffer 槽、compiled frame 与 atlas 引用只在 GPU/Render Thread 已不再使用后回收；精确 fence/轮转策略由三版运行原型冻结。

### 10.1 26.2 OpenGL context、目标与资源销毁

已确认的当前版本生命周期：

- `RenderSystem.initRenderer(GpuDevice)` 只允许静态 `DEVICE` 从 `null` 初始化一次；`shutdownRenderer()` 关闭 device 后不把该字段置回 `null`。因此 26.2 当前源码没有同一 JVM 内热替换/重建设备的受支持路径；
- window surface 的 `configure`/suboptimal 恢复不等于 device rebuild。`GameRenderer.render` 比较窗口 render-state 尺寸与 `mainRenderTarget` 尺寸，不一致时调用 `RenderTarget.resize`；
- `RenderTarget.resize` 在 Render Thread 依次执行 `destroyBuffers()` 与 `createBuffers()`，主颜色/depth texture 和 view 都会更换。这是 target generation 变化；
- public `RenderTarget.getColorTextureView()` 在 OpenGL backend 返回 public `GlTextureView`，其 `glId()` 可作为 target generation 的颜色 handle；FandUI 只观察该 view，不持有或关闭它；
- `Minecraft.close()` 先关闭各 renderer/resource manager，再关闭 `GpuSurface`，最后调用 `RenderSystem.shutdownRenderer()`。Fabric `ClientLifecycleEvents.CLIENT_STOPPING` 在更早的客户端停止阶段触发，用于在 context 销毁前删除 FandUI 自有 framebuffer、renderbuffer、program、VAO、buffer 和 texture；
- target view/handle/尺寸变化时，在下一次 FandUI pass 开始前删除旧自有 FBO/D24S8 并建立新 generation；删除动作只在 Render Thread，且不调用 `glFinish`；
- FandUI 不创建 Mojang `GpuTexture`、`GpuBuffer` 或 command encoder 资源，不参与 Mojang backend 的 submit/fence/延迟销毁机制；先用普通动态 buffer，不提前引入 persistent mapping；
- 若未来源码出现 OpenGL context identity 变化，旧 generation 立即失效并停止使用；在运行原型证明可恢复前，不臆造当前版本不存在的 context 热重建流程。

已废止 Vulkan 路线的两槽 destruction queue、queue-idle shutdown 等事实仍保留在 6.4，不能再用来解释当前 FandUI GL 资源生命周期。

native 分发规则：

- 文件名带 ABI version 和 SHA-256 前缀；
- 提取到内容寻址目录；
- 加载前复核 SHA-256；
- 构件记录 target triple、编译器、NanoVG commit 和 ABI version；
- OS/CPU 支持范围由 Minecraft、Skija 和自有 JNI 三者实际可运行交集决定。

## 11. 优化 Mod 兼容状态

“构件与 metadata 范围匹配”只说明 Fabric Loader 可以尝试启动，不等于运行兼容。下表是 2026-08-21 锁定的测试输入；版本后括号为 Modrinth version ID：

| Mod | 1.20.1 | 1.21.4 | 26.2 | 静态风险结论 |
|---|---|---|---|---|
| Sodium | `0.5.13` (`OihdIimA`) | `0.6.13` (`c3YkZvne`) | standalone 用 `0.9.2-alpha.4` (`a9YZH3ip`)；Iris 组合用 `0.9.1` (`2Yom1N68`) | 中；触及 runTick/GUI/target/viewport，但未命中 FandUI 的精确提交锚点 |
| Iris | `1.7.6` (`s5eFLITc`) | `1.8.8` (`Ca054sTe`) | `1.11.2` (`oaD6KQls`) | 高；触及 GameRenderer、RenderTarget、GlStateManager 和 HUD，必须分别测 shader pack 开/关 |
| Indium | `1.0.36` (`nQHYSjxO`) | 无该 MC 版本构件；Sodium 0.6 已依赖 Fabric Renderer API | 无该 MC 版本构件 | 中；1.20.1 同时测 Sodium + Indium 及 Sodium + Iris + Indium |
| VulkanMod | `0.5.2` (`ZhvilXaM`)，硬冲突 | `0.5.6` (`RAtVo03C`)，硬冲突 | 无 `26.2` 构件；原版实际 Vulkan backend 由运行时诊断停用绘制 | 1.20.1/1.21.4 替换 OpenGL renderer，FandUI metadata 声明 `breaks: { "vulkanmod": "*" }` |
| Lithium | `0.11.4` (`iEcXOkz4`) | `0.15.3` (`u8pHPXJl`) | `0.25.3` (`f7vZ0VWU`) | 低；未发现提交锚点/GL target mixin，仍做单独与全组合 smoke test |
| FerriteCore | `6.0.1` (`unerR5MN`) | `7.1.3` (`7KqeXPRS`) | `9.0.0` (`d5ddUdiB`) | 低；旧两版 `Minecraft` mixin 只注入 renderer 构造点，不触及每帧锚点；重点测 reload |
| Krypton | `0.2.3` (`jiDwS0W1`) | `0.2.8` (`Acz3ttTp`) | `0.3.1` (`5WeL0Nkz`) | 低；未发现 GUI/FBO/GL mixin，检查线程与生命周期即可 |

精确组合约束来自实际 `fabric.mod.json`，不是按版本号猜测：

- 1.20.1 Iris 依赖 `sodium: 0.5.x`，Indium 依赖 `sodium: ~0.5.11`；Sodium `0.5.13` 只 break `iris <1.7.6` 和 `indium <1.0.36`，因此表中三个最新版范围互相允许。Modrinth 安装依赖分别指向 Sodium `0.5.12-beta.2`/`0.5.11`，运行矩阵额外保留这两个推荐配对，但 Fabric metadata 的最新版组合也必须实测；
- 1.21.4 Iris 依赖 `sodium: 0.6.x`，Sodium `0.6.13` 只 break `iris <1.8.7`，所以 Iris `1.8.8` 匹配。Sodium 本身已依赖 `fabric-renderer-api-v1`，且 Indium 没有该版本发布；
- 26.2 Iris `1.11.2` 依赖 `sodium: 0.9.x`。Sodium `0.9.2-alpha.4` 明确 break `iris <=1.11.2`，不能组成测试实例；Iris 的 Modrinth dependency 精确指向 Sodium `0.9.1`，后者只 break `iris <=1.11.1`，所以冻结为 `0.9.1 + Iris 1.11.2`；
- 26.2 Sodium/Iris JAR 的部分 Minecraft 版本约束只存在于 Modrinth game-version metadata，JAR 内没有同等精确的 `minecraft` depends；构建测试不得仅凭 Fabric Loader 接受就扩展版本范围。

Mixin 常量池的静态锚点扫描确认：旧两版 Sodium 在 `Minecraft.runTick` 的 `HEAD/RETURN` 注入，FandUI 位于中间唯一 `RenderTarget.unbindWrite` 之前；26.2 Sodium console hook 位于 `GuiRenderer.render()` 前，FandUI 位于后续 `GuiRenderer.endFrame()` 后。三版选定 Iris `MixinGameRenderer` 均未命中 FandUI 的精确 invoke anchor，但 Iris 会跟踪 program/FBO/viewport 和 RenderTarget。FandUI 状态守卫因此以真实 GL 值做快照/断言，同时优先通过 `GlStateManager` wrapper 变更和恢复 program、viewport、FBO 等状态，使 Sodium/Iris 注入的缓存也同步；只有无 wrapper 的 front/back stencil 分离状态使用裸 GL。

选定的 21 个运行输入（19 个主输入 + 2 个 1.20.1 Modrinth 推荐 Sodium 配对）已下载到 `%TEMP%/FandUI-api-research/mods`，逐个通过 Modrinth SHA-512 后再计算本地 SHA-256：

| 文件 | bytes | SHA-256 |
|---|---:|---|
| `sodium-fabric-0.5.13+mc1.20.1.jar` | `971552` | `688C26029CE69F0B1F7CF936866656CA6723228AC46C4AEF5B5F793B1E6ABD22` |
| `sodium-fabric-0.5.12-beta.2+mc1.20.1.jar` | `971376` | `48E11F72EBD44FCC233E9FC199BE43A28FEB78E80C6DDC723D6E8A0DDBC21C0B` |
| `sodium-fabric-0.5.11+mc1.20.1.jar` | `968419` | `5413BAAA260A2EADCEE4221B8E7105A8E2A9B5B2C84B73F704F2740319AAB7DF` |
| `sodium-fabric-0.6.13+mc1.21.4.jar` | `1306799` | `92B79623FC00F0F948005A0AE416CB42A51646F2423B4036E27E867E4001A47E` |
| `sodium-fabric-0.9.2-alpha.4+mc26.2.jar` | `1880490` | `A6D1CED177A1C57147E8C1C8043650D5F93E1F2824C9952F3FB67AE05633A0A7` |
| `sodium-fabric-0.9.1+mc26.2.jar` | `1834384` | `DE406C7A0CA5E748DFBE44740278400882A44E3109E2584B243EC02D4003344B` |
| `iris-1.7.6+mc1.20.1.jar` | `2726899` | `9EB15E563E0C9AE6EFF15B7863F8432DD290CF9F724C7373E9B15B14AA829ED5` |
| `iris-fabric-1.8.8+mc1.21.4.jar` | `2682702` | `70571B23D4DE17AE380515FB4C9DADD82D96F7EC1B573553858A07BB80445DA9` |
| `iris-fabric-1.11.2+mc26.2.jar` | `2820763` | `DF0E2CCDDAEA17B191EDA32B21C979E131BC9D4EF4F831113B50B461FC4A3804` |
| `indium-1.0.36+mc1.20.1.jar` | `105382` | `8434D0ABC8826E2540C26741FEA45A1AB9D48F650746DE282B36850FA1D01999` |
| `lithium-fabric-mc1.20.1-0.11.4.jar` | `691483` | `76133EFE92AEA907CF75562D9E26517DEB170E2580E1ECD4979DA2C8902DE782` |
| `lithium-fabric-0.15.3+mc1.21.4.jar` | `797555` | `153891E8D6988FEDFFA5098851A725A137C3E5CA04A89A8DF409FEB313623EB4` |
| `lithium-fabric-0.25.3+mc26.2.jar` | `912850` | `FDDE92E238E8075F89AD7F701F2A3D5854AF88BA9A67657184A4407B104AC563` |
| `ferritecore-6.0.1-fabric.jar` | `125197` | `C7BA1118A05B2DA900D1C369FBE1017A3E3EC3CAFE5E51398B851279B9BECF24` |
| `ferritecore-7.1.3-fabric.jar` | `109879` | `89350947F7A135B75541689BA85D70424F5DA604B17B72661D744F1D7F302B71` |
| `ferritecore-9.0.0-fabric.jar` | `72677` | `213966C72ED967ACC7392BEB28A866FBA301FF56B9976C2E7801C2DB7DE6BF22` |
| `krypton-0.2.3.jar` | `185895` | `69AD1810206CE12181B1B083640F044E7E250781C36C96C655EAC36209057698` |
| `krypton-0.2.8.jar` | `161045` | `94F195819B24E5DA64EFFDC9DA15CDD84836CC75E8FF0FD098BAB6BC2F49E3FE` |
| `krypton-0.3.1.jar` | `268842` | `5EA8901561973D29E51E751469D52D92100F348AB461E1186F67012E93420C48` |
| `VulkanMod_1.20.1-0.5.2.jar` | `18997701` | `0419AF972EFA289816E536203E63CE2CAF4E3FDE4AA0504D3DBD5AD4A533FEEA` |
| `VulkanMod_1.21.4-0.5.6.jar` | `19810358` | `E18A9AE1B4987824D29711C475CD1B86C2D44E919240FAE103B4B1330DEDB8A6` |

仅对已证明的 renderer 所有权硬冲突写 Fabric `breaks`，其余结果以实际组合测试为准。

## 12. 待验证清单

### P0：开始完整实现前必须解决

- [x] Gradle `9.5.1` + Loom `1.17.19` 的 8 模块配置与空构建；`1.18.0-alpha.16` 因要求 Gradle Plugin API `9.7.0` 已否定；
- [x] Java 17/21/25 带实际入口源码和 Fabric metadata 的最小编译/JAR 验证；class major 分别为 61/65/69，共享 renderer 为 61；
- [x] 三版本最终渲染接入点的 owner、descriptor 与主调用顺序；
- [x] 三版本稳定 Mixin selector/ordinal 的最小启动验证；三版 vanilla 开发客户端最终 GUI hook 均各命中一次；
- [x] 1.20.1 主颜色纹理重挂接 + 自有 D24S8 + resize 原型；vanilla 开发客户端真实状态恢复断言与 GL debug 零错误；
- [x] 1.21.4 主颜色纹理重挂接 + 自有 D24S8 + resize 原型；vanilla 开发客户端真实状态恢复断言与 GL debug 零错误；
- [x] 26.2 `GlTextureView.glId()` 主颜色重挂接 + 自有 D24S8 + resize 原型；实际 backend 字面值为 `OpenGL`；
- [x] 26.2 OpenGL 状态守卫、Mojang cache 同步与恢复后真实状态断言；vanilla 开发客户端零 probe failure/GL debug error；
- [x] 26.2 Vulkan 高层 D24S8、pipeline stencil 与 image/view/pass internal hook 已废止，不进入实现；
- [x] 26.2 上述三个 internal 关注点的 owner、descriptor、唯一调用位置与限定标记策略；
- [ ] 三版本 RenderDoc/GL debug 证明主颜色目标、FandUI pass 顺序和完整状态恢复；静态架构检查继续要求 FandUI 自有代码不引用/调用 Vulkan API，NanoVGGL3 是当前明确采用的 renderer；
- [x] depth-as-clip、stencil-as-winding 的非凸嵌套原型；三版真实 batch consumer 均通过 `22 batches / 25 draw calls` 与三点像素读回。

### P1：Demo 前必须解决

- [x] 三版本 Screen/HUD/input callback 的完整包名、descriptor、取消语义与事件顺序；
- [ ] 三版本 Screen/HUD/input bridge 的运行组合测试，包括 resize re-init、repeat、代理对、26.2 IME preedit、double-click 和第三方同 anchor 顺序；
- [x] 三版本 resource reload 注册 owner、阶段签名与跨版差异；
- [x] 旧版 Blaze3D 状态缓存与真实 GL 状态的源码边界；
- [ ] 旧版状态守卫在原版、Sodium、Iris 下的运行恢复断言；
- [x] 26.2 device/target/GL context 生命周期、target generation 与关闭顺序源码语义；当前版本无进程内 device rebuild；
- [x] 26.2 FandUI 自有 GL 资源删除顺序与 shutdown/resize 运行验证；resize 后重新 attach，`Stopping!` 后不再分配且 context 存活时记录资源释放；
- [x] 三版本源码目标格式、sRGB 数值约定及当前无 HDR negotiation 的边界；
- [ ] 三版本运行时实际目标格式、颜色 probe 与未知格式诊断；
- [x] NanoVG JNI 可重现构建与 ABI 校验；Windows x64 `/MT`、`/Brepro`、C ABI、JNI direct-buffer、worker ownership 和 byte-level golden 均已通过；
- [x] static PNG decode/premultiplied RGBA golden corpus；
- [x] Skija `0.143.17` 精确 API、六桌面 classifier 存在性与 Windows x64 A8/RGBA/paragraph 探针；
- [ ] Skija 六平台实际加载、Loom runtime 冲突与 text worker 压力测试；
- [x] CJK/Emoji 字体许可、候选版本/哈希、三目标 JDK 的显式字体栈与确定性 fallback；
- [ ] Sodium/Iris/Indium 对目标和时序的影响。
- [x] 面向 Mod 使用者的公共 API Javadoc、README quickstart 与生命周期示例；所有公开顶层 API 类型已文档化，quickstart 由 Java 17 consumer test 实际编译；

### P2：发布前必须解决

- [x] 26.2 官方 Vulkan 的平台/架构发行边界、loader、capability gate 与原版 fallback；该项是已废止路线的保留研究，不再构成发布门槛；
- [ ] 所有 native classifier 的干净机加载测试；
- [x] RenderDoc/GL debug 的测试版本与可复现入口；Vulkan validation 仅保留为已废止路线的历史记录，实际 GL 运行证明仍归 P0/P1 验收项；
- [x] Java 17 API 编译探针、源码/class 平台与 internal 类型泄漏扫描、japicmp `0.26.1` 的 `0.1.0` 预发布 baseline，以及三版本 JAR 的 `214` 个 API class 逐文件 SHA-256 一致性；
- [x] 兼容矩阵中每个 Mod 的具体版本、组合约束、Modrinth version ID 和构件哈希锁定；
- [ ] 30 分钟压力测试后的显存/atlas/platform baseline 和阈值。

## 13. MVP 顺序与验收摘要

### 实施顺序

1. 空 Gradle 多模块构建矩阵；
2. 三版本 OpenGL 三角形 + 主颜色重挂接 + D24S8/Stencil 风险原型；
3. 公共 API、component/layout/style/event/focus/animation；
4. immutable DisplayList；
5. NanoVG JNI 与 batch protocol；
6. 单一 OpenGL renderer 与三个版本状态桥；
7. Skija text/fallback/atlas；
8. 三个 Fabric bridge；
9. Demo Screen/HUD；
10. RenderDoc、GL debug、兼容、显存和发布验收。

### Demo 必须覆盖

- 中英混排和明确字体 fallback；
- 圆角背景、描边、图片；
- Scissor 与嵌套 path clip；
- 按钮 hover/click；
- 文本输入和焦点；
- 滚动容器；
- GUI scale 和窗口 resize；
- resource reload；
- Screen/HUD 挂载与卸载；
- 连续运行无随帧数或 reload 次数单调增长的显存占用。

### GPU 验收

- 三版本：GL debug 无 FandUI 错误；FandUI 构件不声明 Vulkan 依赖且 FandUI 自有代码不引用/调用 Vulkan API；最终 renderer 使用与各版 LWJGL 对齐的 `NanoVGGL3`，不再分发临时 vendored NanoVG/JNI renderer；
- 26.2：实际 backend 字面值为 `OpenGL`，FandUI 不修改 Minecraft backend option，不调用 command encoder `submit()` 或 surface `present()`；
- RenderDoc 中可识别独立 FandUI pass，并证明所有 FandUI draw 都位于最终 GUI 之后、Minecraft blit/present 之前；
- 三版本 RenderDoc 均确认颜色 attachment 是 Minecraft 当前 UI 主目标，D24S8 是 FandUI 自有资源；
- premultiplied alpha 无黑边，Scissor/Stencil 至少 8 层嵌套正确；
- 资源缓存有明确预算和稳定平台期。具体显存数值阈值在原型测得基线后写入，当前不虚构固定值。

### 第一阶段 12 项交付复核

| 用户要求 | 文档位置 | 当前状态 |
|---:|---|---|
| 1. 可行性结论 | 2.1 | 已完成；三版本 OpenGL GPU go/no-go 与首个可运行 MVP 已通过，发布级组合/压力仍待验收 |
| 2. 仓库模块图 | 3 | 已完成；按新后端决定收敛为 8 模块与单一 OpenGL renderer |
| 3. 公共 API 边界 | 7 | 已完成；FandAPI 参考、首发基础契约、线程/所有权/失败语义、Javadoc 和 ABI 门禁均已实现验证 |
| 4. 三版本宿主接入 | 6.2-6.6 | 已改为三版 OpenGL；最终 hook、target attach、resize/reload、状态恢复和正常关闭均已真实运行 |
| 5. NanoVG backend | 8 | 已完成；采用版本匹配的 `lwjgl-nanovg`/NanoVGGL3，并以 layer/mask、gradient cache 和 swizzle 补齐 Canvas2D 语义 |
| 6. Skija 字体纹理管线 | 9 | 设计已完成并有 Windows x64 像素探针；跨平台待测 |
| 7. 每帧渲染时序 | 10 | 已完成；异步未就绪时只使用上一完整 snapshot |
| 8. 资源生命周期 | 10 | 已完成首版；三版 GL 资源按 context/target/frame/resource 代际管理，resize/reload/关闭顺序已运行验证 |
| 9. 优化 Mod 兼容矩阵 | 11 | 初版已完成；实际组合版本与结果保持待测 |
| 10. MVP 实施顺序 | 13 | 已完成；构建矩阵和 GPU 风险原型优先于 API 实现 |
| 11. 测试与验收标准 | 13 | 已完成；具体显存阈值待 prototype baseline 后填写 |
| 12. 尚需验证事实 | 12 | 已完成；按 P0/P1/P2 分级且未把未知项写成事实 |

第一阶段 12 项设计交付已经完成并由用户确认，现已进入实现。带源码全仓构建、三版本 vanilla Demo、主要 GPU 路径与首发 API 工程门禁已经通过；RenderDoc、优化 Mod 组合、长期压力和跨平台事实仍按第 12 节逐项验证。4 个 JVM crash log 已删除且复查数量为 `0`。

### 进入实现的设计确认门槛

用户确认第一阶段设计，表示接受以下当前实现基线；这不是对 18 个待运行事实的“已通过”声明：

1. 三版本只实现一个共享 OpenGL renderer；1.20.1/1.21.4 复用 Minecraft 当前 OpenGL context，26.2 沿用 Minecraft backend 配置且仅在实际 backend 为 `OpenGL` 时启用 FandUI；
2. 仓库使用第 3 节的 8 模块 Gradle/Loom 边界，公共 API 以 Java 17 编译，三个版本 JAR 单独发布并嵌入逐文件一致的 API classes；
3. **已废止基线**：早期 NanoVG 方案通过自有 JNI 和批协议消费固定 C core。用户要求优先采用现成库后，最终基线改为版本匹配的 `lwjgl-nanovg`/`NanoVGGL3`；仅在 FandUI 上层组合 stock backend 缺少的 path clip 和多 stop gradient 语义；
4. Skija 负责显式字体栈、Paragraph shaping/layout、CPU text-block raster 与 A8/RGBA atlas 输入；首版 bundled correctness fallback 为已锁定的 Noto CJK/Emoji 文件；
5. 第 7 节的 component/layout/style/event/focus/animation/Canvas/resource/text 边界作为第一版实现输入，但在 Java 17 编译探针通过前仍可做保持语义的小幅签名修正，不提前承诺发布后的二进制冻结；
6. 实施严格按“空构建矩阵 -> 三版本 OpenGL 主颜色重挂接/D24S8/resize/state restore 风险原型 -> API/core/canvas/text -> Fabric bridge -> Demo/验收”推进；GPU hard-stop 条件触发时先回到设计，不用第二 context、离屏跨 API 合成或接管世界 renderer 绕过；
7. 图片资源支持 static PNG 与受限 SVG；两者均在 reload worker 规范化为 premultiplied RGBA8，文字/图片/路径均按当前宿主 display-encoded SDR UNORM 数值约定处理；未知 target format 明确诊断并跳过该次 FandUI 提交；
8. 第 12 节所有未勾选项继续保留为发布或阶段 gate，源码路径存在、构建成功和单机截图都不替代真实启动、GL debug、RenderDoc、组合兼容和压力证据。

收到用户明确“确认第一阶段设计并进入实现”后，下一动作固定为复制已发现的 Gradle 9.5.1 Wrapper、创建 8 模块骨架，并只执行 P0 最小构建与 GPU 风险原型；不执行 Git commit、分支或 push。

## 14. 实施进度

| 工作项 | 状态 | 结果/下一步 |
|---|---|---|
| 需求与后端修订 | 已完成 | 三版本只实现 OpenGL；26.2 沿用 Minecraft 配置，实际 backend 非 OpenGL 时跳过 FandUI 绘制 |
| 第一轮源码/元数据探查 | 已完成 | 结果已记录在第 4-6 节 |
| 持久研究记录 | 已完成 | 本文件 |
| Fabric Screen/HUD/input 精确核验 | 已完成 | 生命周期、descriptor、短路/consumed、repeat、Unicode/IME、HUD anchor 顺序见 6.1.1/6.1.3 |
| Resource reload 精确签名 | 已完成 | 1.20.1 profiler 参数、1.21.4 无 profiler、26.2 SharedState/v1 注册已确认 |
| NanoVG 参考 backend 语义核验 | 已完成 | 固定 commit、源码哈希、fill/stroke/scissor/image 语义已记录 |
| NanoVG renderer | 已完成 | 三版匹配的 `lwjgl-nanovg`/`NanoVGGL3` 正式运行；临时 FUDL/FUBT、JNI/CMake/native classifier 与自有 batch shader 已从源码和构建移除 |
| Skija API/native/像素探针 | 已完成 | `0.143.17`、六构件哈希、Windows x64 A8/RGBA/paragraph 结果已记录 |
| 目标颜色空间源码核验 | 已完成 | 三版 SDR 8-bit UNORM；26.2 swapchain 限定 UNORM + SRGB_NONLINEAR；当前无 HDR negotiation |
| 26.2 Vulkan internal hook 源码定位 | 已废止 | 三个关注点、五个窄调用点作为历史证据保留，不进入实现 |
| 26.2 Vulkan 平台/capability 核验 | 已废止 | 事实保留，但不再是 FandUI 运行或发布前置 |
| 26.2 OpenGL 接入源码核验 | 已完成 | backend/handle/resize/提交窗口、8 槽状态边界和 `GpuDebugOptions -> GlDebug` 入口已确认；运行原型归 P0 |
| 调试工具基线 | 已完成 | RenderDoc 1.45 和三版 GL debug 路径已锁定；ValidationLayers 只属旧路线历史；本机尚无 RenderDoc，实际抓帧未开始 |
| 优化 Mod 测试版本锁定 | 已完成 | 7 个项目、三目标版本、21 个主/配对构件及静态 Mixin 风险已记录；运行组合仍归 P1 |
| 第一阶段 12 项设计复核 | 已完成 | 交付索引见第 13 节；所有未运行事实仍保留待验证状态 |
| 第一阶段设计确认 | 已完成 | 用户于 2026-08-22 明确确认并要求进入实现 |
| Gradle 构建矩阵 | 已完成 | Gradle 9.5.1 + Loom 1.17.19；空构建和带源码 `buildAll` 均退出 0，三版 class major 与嵌套 JAR 已审计 |
| GPU 风险原型 | 已完成 | 三版 vanilla 的主颜色重挂接、D24S8、resize、状态断言、非凸嵌套 batch 与像素读回均通过；P0 只余 RenderDoc 抓帧 |
| CJK/Emoji 字体 fallback 探针 | 已完成 | Noto CJK `Sans2.004` + Noto Color Emoji `v2.051`；OFL、哈希、coverage、复杂序列及 Java 17/21/25 一致性已确认 |
| 公共 API 设计 | 首发基础层已闭合 | FandAPI 只读参考、稳定入口、定义/会话、布局/组件、样式、输入、资源、文字、focus/animation 已编译；公开签名隔离、Java 17 consumer、Javadoc、ABI baseline 与三版字节一致性门禁均通过 |
| Core/Canvas/Text 实现 | MVP 已完成 | runtime/session/focus/animation/event、transform-aware layout/hit-test、flex/stack/theme scope、SceneCompiler、Skija text-block、PNG resource worker、完整单行 `TextInput` 与共享 Demo 已完成回归 |
| Fabric bridges | 已完成首版 | 三版最终 GUI OpenGL host、Screen/HUD/input/clipboard/resource reload bridge、resize 与关闭释放均已实现并完成 vanilla 真实运行 |
| Demo/验收 | 进行中 | 三版本完整 Demo 的 GUI Scale、resize、resource reload 与交互已实测；RenderDoc、优化 Mod 组合、长期性能/显存和跨平台仍待补 |

## 15. 研究日志

### 2026-08-21：第一轮研究汇总

- 确认三个 Java/LWJGL/Fabric 构建版本；
- 确认 1.20.1/1.21.4 GUI flush 与最终 blit 之间的接入窗口；
- 确认 26.2 GUI、encoder submit 和 surface present 的相对时序；
- 确认 26.2 高层 GPU API以及 stencil state 缺口；
- 确认 LWJGL 未公开 NanoVG `NVGparams`，选择自有 JNI；
- 确认 Skija 字体加载、font collection、paragraph/shaping 和 raster 能力；
- 确认旧版 `vulkanmod` Mod ID；
- 用户随后将 1.20.1/1.21.4 明确改为 OpenGL，旧全 Vulkan 方案标记废止。

### 2026-08-21：建立持久记录

- 创建 `RESEARCH.md`；
- 约定后续每次 API 探查、设计变化、实现与测试均实时更新本文件；
- 尚未创建代码或 Gradle 工程，未执行 Git 操作。

### 2026-08-21 20:27：P0 精确入口与 26.2 Stencil 缺口

- 用 1.20.1 Mojang mapping、Forge SRG mapping 和反编译源码交叉确认 `GameRenderer.render(FJZ)V`、`Minecraft.runTick(Z)V`、最终 flush/blit 顺序及 public 颜色纹理 getter；
- 从 1.21.4 layered mappings 源码和 named class JAR 确认 `GameRenderer.render(DeltaTracker,Z)V`、主目标生命周期及 `GL_RGBA8`/depth-only attachments；
- 用 `javap -p -s -c` 检查 26.2 官方命名 JAR，确认 `GameRenderer.render(DeltaTracker,Z)V` 和 `Minecraft.renderFrame(Z)V` 的 render -> blit -> submit -> present 顺序；
- 确认 Vulkan backend 的 device 级 `VulkanCommandEncoder` 被复用，高层 wrapper 可在接入点重新取得而不产生第二个队列/设备；
- 确认 public API 当前不能直接实现 stencil：缺少 stencil pipeline state、`pStencilAttachment`、D24S8 depth+stencil view/aspect 和正确 pipeline attachment format；
- 将 26.2 internal 适配从单一 pipeline hook 修订为三个 FandUI-only hook，仍保持 Mojang 管理 command buffer 生命周期、submit 与 present。

### 2026-08-21 20:46：持久记忆协议与第二批 API 事实

- 用户明确本文件专门用于持续记录 API 接口追踪和各项设定，防止上下文压缩造成缺失；因此强化了单一事实源、恢复顺序和逐事实落盘规则；
- 从 Fabric API 聚合 JAR metadata 和模块源码 JAR核对三版本 rendering、screen、resource loader、lifecycle 子模块精确版本；
- 直接读取三版本 HUD/Screen API 和 26.2 resource loader 源码，记录 render 与 extract 模型差异，以及旧版缺少 char-typed callback 的版本桥策略；
- 从 1.21.4 `GlStateManager` 源码确认受跟踪 OpenGL 状态和统一 stencil API边界，确定 D24S8 renderbuffer 与 front/back 分离 stencil 的窄裸 GL策略；
- 重新计算固定 NanoVG 三个参考源码文件的 SHA-256，并逐行核对非凸 fill、stencil stroke、fragment scissor、premultiplied color 和 image texture type 语义；
- 本轮只修改 `RESEARCH.md`，没有创建 Gradle/Java/native/Fabric 工程，也没有执行 Git 操作。

### 2026-08-21 20:55：三版本最终提交锚点

- 读取 1.20.1 `GameRenderer.m_109093_` 和 `Minecraft.m_91383_` 完整尾部，确认 `GameRenderer` 最终 flush 后，`Minecraft` 仍可能绘制并 flush FPS profiler，之后才 unbind/blit；
- 读取 1.21.4 `GameRenderer.render` 与 `Minecraft.runTick` 完整尾部，确认最终 GUI flush、resource pool end-frame、target unbind、blit 和 display 的顺序；
- 用 `javap -p -s -c` 读取 26.2 `GameRenderer.render` 与 `Minecraft.renderFrame`，确认唯一 `GuiRenderer.endFrame()`、public `mainRenderTarget()`、surface blit、encoder submit 与 present 的精确顺序；
- 旧版 selector 因此统一选为 `runTick(Z)V` 中 `RenderTarget.unbindWrite()V` 之前；26.2 选择 `GameRenderer.render(DeltaTracker,Z)V` 中 `GuiRenderer.endFrame()V` 之后；
- selector 尚未进入 Mixin 配置或运行客户端，状态保持“设计决定/待最小启动验证”。

### 2026-08-21 21:01：26.2 设备与延迟销毁生命周期

- 用 `javap -p -s -c` 核对 `RenderSystem.initRenderer/shutdownRenderer`、`Minecraft.close/renderFrame`、`RenderTarget.resize/destroyBuffers/createBuffers`；
- 确认 device 只初始化一次，surface reconfigure 和主目标 resize 不替换 device；当前源码不存在进程内 device rebuild；
- 核对 `VulkanGpuTexture`、`VulkanGpuTextureView`、`VulkanGpuBuffer.Direct` 的 `close/destroy`，确认公开 close 进入 Mojang 延迟销毁队列；
- 核对 `VulkanCommandEncoder` 构造、submit、destroy 和 `DestructionQueue`，确认最多两提交在途、等待完成后轮转销毁、shutdown 时 queue-idle 后彻底清空；
- 读取三版本 Fabric lifecycle source；`CLIENT_STOPPING` 可作为 FandUI 主动关闭资源的公共生命周期入口，最终仍需客户端退出实测确认调用线程和一次性语义。

### 2026-08-21 21:13：Skija 0.143.17 API、native 与像素探针

- 从 Maven Central metadata 确认 `skija-shared:0.143.17` 是当前 release，下载 shared/source 和 Windows/Linux/macOS 的 x64/arm64 六个 native JAR 到临时研究目录；
- 记录七个 JAR 的 SHA-256、native resource 路径和字面 `skija.version=0.143.17`；确认 shared POM target 11、class major 52；
- 用 `javap` 与 source JAR核对 font provider/fallback、Paragraph、LineMetrics、Shaper/RunHandler、ALPHA_8、RGBA_8888 PREMUL、Surface/Pixmap 精确 API；
- 用 Windows x64 + Java 25 JShell 直接加载 native：A8 普通文字、COLR/CPAL 彩色 Emoji、中英 Emoji 混排、换行/基线、rowBytes、premultiplied alpha 均得到字面成功结果；
- 实测 Paragraph 的 range/line index 等于 Java UTF-16 code-unit 下标，而不是 code point 或 UTF-8 byte offset；
- 因 Paragraph 不公开最终 fallback run，废止 MVP 逐 glyph 重排草案，改为不可变 text-block A8/RGBA atlas；逐 glyph 优化只有性能数据证明必要时再评估；
- 探针只在内存内 JShell 执行，仓库仍只有 `RESEARCH.md`。

### 2026-08-21 21:28：NanoVG ABI 收口与旧版 GL 状态边界

- 复核 NanoVG ABI 草案，固定 `FUDL`/`FUBT` 48-byte header、24-byte section directory、8-byte record 对齐、严格 major/minor 协商和未知 opcode 失败语义；修正了“未知命令可跳过”与 `INVALID_OPCODE` 的矛盾；
- 固定 `orderKey = ((u64) commandIndex << 3) | subpass`，每条命令保留 8 个子 pass，并要求 JNI 输入中的绘制 record 显式携带原始 DisplayList command index；
- 从 1.20.1 Forge 反编译源码 JAR直接读取 `GlStateManager.java`，并与 1.21.4 layered-mappings 源码逐项比较；确认 1.21.4 新增 read/draw framebuffer 缓存，而两版都不完整缓存 program、VAO、buffer、blend equation、scissor box 或 front/back stencil；
- 两版 `_stencilFunc` 均存在将 `func` 与三个缓存字段比较的字面条件，状态守卫因此以真实 GL 查询和恢复后断言为准，不能把 wrapper no-op 判定当作证明；
- 用户指定后续 API 设计参考 `C:/Users/winme/Desktop/FandServer` 的 FandAPI；该工作已登记为当前第一阶段事实核验之后的独立任务，本轮未读取该仓库；
- 本轮仍只修改 `RESEARCH.md`，没有创建 Gradle/Java/native/Fabric 工程，也没有执行 Git 操作。

### 2026-08-21 21:42：资源重载、颜色空间与 26.2 hook 定位

- 读取 1.20.1/1.21.4 `fabric-resource-loader-v0` 和 26.2 `fabric-resource-loader-v1` 源码，并以 named Minecraft JAR 的 `javap` 核对 descriptor；确认 1.20.1 load/apply 带 `ProfilerFiller`、1.21.4 已移除、26.2 改为 `SharedState`；
- 固定 resource generation 流程为“读取 immutable bytes -> 唯一 text worker 构建 Skija registry -> apply 原子切换”，reload 失败继续使用上一完整 generation；
- 扫描两个旧版源码中的 sRGB/HDR 符号与数值常量，确认主颜色纹理均为 `GL_RGBA8`；核对 26.2 `MainTarget`、`GpuFormat`、`GpuSurface.Configuration` 和 `VulkanGpuSurface`，确认主目标为 `RGBA8_UNORM`，swapchain 仅选择 format 37/44 + colorSpace 0，当前没有 HDR negotiation；
- 用 `javap -p -s -c` 定位 26.2 `VulkanGpuTexture`、`VulkanGpuTextureView`、`VulkanCommandEncoder.createRenderPass` 和 `VulkanRenderPipeline.compile` 的五个窄注入点；确认现有 `VulkanConst.formatAspectMask(D24S8)=6`、`toVk(D24S8)=129`；
- 源码位置核验不等于运行可用：所有 Mixin selector、D24S8 attachment、pipeline stencil state、validation 和 RenderDoc 项仍保留未完成；
- 本轮仍只修改 `RESEARCH.md`，没有创建工程代码或执行 Git 操作。

### 2026-08-21 21:50：第一阶段 12 项设计复核

- 增加条件可行性结论与硬性停止条件，明确 1.20.1/1.21.4 OpenGL 和 26.2 Mojang Vulkan 各自的 go/no-go 原型；
- 将模块图改为无歧义的 `模块 -> 直接依赖` 表达，固定 API/canvas/core/text/renderer/version bridge 的依赖方向；
- 修正 NanoVG `FUDL` header：使用其原生语义所需的单一 `devicePixelRatio`，物理 target 尺寸留给 RenderHost，不虚构 X/Y 两套 fringe scale；
- 固定 MVP 为一个 canvas compiler worker + 一个 text worker，各自串行拥有 native context；异步结果未就绪时只消费上一完整 snapshot；
- 明确最终一次 pass 的 HUD 能力是 post-GUI overlay，不承诺与 vanilla/第三方 HUD layer 逐层交错；
- 新增 12 项交付索引，逐项链接可行性、模块、API 边界、三版宿主、NanoVG、Skija、时序、生命周期、兼容、MVP、验收和待验证清单；
- 严格 UTF-8 解码、代码围栏配对和遗留路线扫描通过；仓库仍只有 `RESEARCH.md`，没有 `.git`、Gradle 或源码文件。

### 2026-08-21 22:22：Screen、输入与 HUD 精确接口

- 完整读取三版 `fabric-screen-api-v1` 的 API、event factory、Screen/keyboard/mouse/GUI mixin，确认 per-screen event 会在每次 init/resize 前重建，以及 remove、tick、render/extract 的实际包围位置；
- 用 1.20.1 official-mapped source、1.21.4 named source 和 26.2 official-named JAR 的 `javap -p -s -c -l` 核对 key/char/preedit/mouse 的全部调用 descriptor、GUI 坐标换算、scroll 归一化和 26.2 `250 ms` double-click 条件；
- 确认三版 Screen 层都无法从 key callback 区分 GLFW press 与 repeat；旧版 supplementary code point 会拆为两次 UTF-16 `charTyped`，26.2 则以 `CharacterEvent(int codepoint)` 保真；
- 确认 26.2 新增可为 `null` 的 `PreeditEvent` 清除通知与 `GuiEventListener.preeditUpdated`，而 Fabric Screen API 尚未暴露 preedit callback；
- 核对 1.20.1 HUD tail callback、1.21.4 identified `LayeredDraw` 注册、26.2 static `HudElementRegistry`，并固定 MVP 标准 HUD mount 在最后 vanilla anchor 后采集；最终仍只提交一次 post-GUI FandUI GPU pass；
- P1 的源码接口核验项已完成，新增运行组合测试项；本轮仍只修改 `RESEARCH.md`，没有创建工程代码或执行 Git 操作。

### 2026-08-21 22:40：26.2 Vulkan 平台、能力门槛与回退

- 结构化解析 26.2 Mojang metadata，确认 Java 25、LWJGL 3.4.1 和六种官方 native classifier；除 macOS 外 `lwjgl-vulkan` 不带 loader native，Windows/Linux 分别由 LWJGL 加载 `vulkan-1`/`libvulkan.so.1`；
- 下载并复核 Mojang metadata 指向的两个 macOS `lwjgl-vulkan` native JAR，SHA-1 完全匹配，内部实际为 x64/arm64 `libMoltenVK.dylib`；读取 LWJGL 3.4.1 `VK.java` source 确认 bundled MoltenVK 优先与 loader fallback；
- 用 `javap -p -s -c` 核对 Vulkan instance/device/physical-device/surface 字节码，冻结 Vulkan 1.2、五 extension、九 feature、combined graphics-compute-present queue、58 个 Intel MoltenVK blocklist tuple 与 portability 路径；
- 核对 `PreferredGraphicsApi.getBackendsToTry()` 与 `Minecraft` backend 创建循环，确认 `VULKAN` 仍会在失败后尝试 OpenGL；FandUI 必须验证实际 `DeviceInfo.backendName()`，不能只读选项；
- 本机 `vulkaninfo` 证明 loader 与 Vulkan 1.2 device 存在，但 GTX 760 缺少 `VK_KHR_dynamic_rendering`/`dynamicRendering`，不满足 Mojang gate；validation layer 还因失效 SDK JSON 路径不可用；
- 完成 P2 的官方发行/源码平台边界项；跨平台真机、干净机 native 和 validation 仍独立保持未完成。本轮只更新 `RESEARCH.md`，没有创建工程代码或执行 Git 操作。

### 2026-08-21 22:53：调试工具版本与入口锁定

- 通过 RenderDoc GitHub release/signed tag、官方 builds 页面和构件 HEAD 交叉锁定 `v1.45`、提交、发布日期、Windows/Linux 构件与 macOS 缺口；
- 通过 Khronos ValidationLayers release/tag 和 LunarG 各平台 latest JSON 锁定 VVL `vulkan-sdk-1.4.357.0`、Windows SDK `1.4.357.0` 及 Linux/macOS SDK `1.4.357.1`；
- 用 LWJGL 3.4.1 `Configuration` 字节码确认 Vulkan loader 覆盖属性为 `org.lwjgl.vulkan.libname`；
- 读取 1.20.1/1.21.4 `GlDebug`、`Options` 与 renderer 初始化源码，确认两版都复用 Mojang callback，优先 KHR、回退 ARB，并以 `glDebugVerbosity:4` 覆盖全部可用 severity；
- 本机实测 RenderDoc 缺失，Vulkan SDK 1.4.321.1 的目录和九个 layer JSON 均已不存在；没有改动系统环境或注册表，实际抓帧/validation 保留未完成；
- P2 未完成项由 5 个降为 4 个，总未完成项由 22 个降为 21 个。本轮仍只更新 `RESEARCH.md`，没有创建 Gradle/Java/native/Fabric 工程，也没有执行 Git 操作。

### 2026-08-21 23:18：最终收敛为 Minecraft OpenGL 路径

- 用户取消 Vulkan 强制要求，决定使用 Minecraft 默认选择流程或 OpenGL；当前实现范围收敛为三版共用单一 `fandui-render-opengl`，删除 Vulkan renderer 模块、host、internal hook 和 validation 发布门槛；
- 26.2 不改 `preferredGraphicsBackend` 或启动参数。已确认 `DEFAULT` 的尝试顺序为 `GlBackend -> VulkanBackend`；FandUI 仅在实际 `DeviceInfo.backendName() == "OpenGL"` 时启用，实际 Vulkan 模式只诊断并跳过绘制；
- 用 26.2 official-named `minecraft-client.jar` 的 `javap -p -s -c` 确认 `GpuDebugOptions(IZZZ)V`，以及 `Options.glDebugVerbosity -> Minecraft -> GlDevice -> GlDebug.enableDebugCallback(IZSet)` 的完整调用链；默认值为 `1`，测试值 `4` 覆盖 KHR 四档/ARB 三档，最近日志队列大小为 `10`；
- 确认 26.2 `GlStateManager` 的 `BLEND` 和 `COLOR_MASK` 均有 8 槽，没有 stencil wrapper；`_glUseProgram`、`_glBindVertexArray`、`_glBindBuffer` 不维护对应缓存，状态守卫必须读取和恢复真实 GL 值；
- 修复当前生命周期、调试验收和进度表中的 Vulkan 现在时。第 12 节仍有 20 个未完成项；本轮只修改 `RESEARCH.md`，没有创建 Gradle/Java/native/Fabric 工程，也没有执行 Git 操作。

### 2026-08-21 23:29：CJK/Emoji 字体与 fallback 冻结

- 从上游 tag/commit、GitHub contents metadata 和本地 `git hash-object` 交叉确认 Noto Sans CJK SC `Sans2.004` 与 Noto Color Emoji `v2.051` 的精确文件；记录大小、Git blob 与 SHA-256；
- 逐字节比对两个 tag 的 SIL OFL 1.1 `LICENSE`，确认本地许可文本哈希与上游一致；MVP 选择原样捆绑字体与许可，不引入字体子集化工具链；
- 首轮独立 Java 探针因 classpath 漏掉 Skija POM 明确要求的 `io.github.humbleui:types:0.2.0`，在 `_nAfterLoad` 解析 `IPoint` 后使 Java 17/21/25 都崩溃；补齐该依赖后问题消失，冻结为三个 Fabric runtime 的强制依赖检查；
- 崩溃分别生成根目录 `hs_err_pid3164.log`、`hs_err_pid33340.log`、`hs_err_pid5124.log`，早先 JShell 崩溃已有 `hs_err_pid33272.log`；这些非交付日志保留到获得删除确认；
- 用同一 `FontProbe` 在 Java 17/21/25 验证明确的两字体 family stack、glyph coverage、中英混排、单 Emoji、ZWJ/VS16 序列、RGBA 彩色像素和 premultiplied alpha；三套输出逐字相同且退出码均为 `0`；
- 确认 asset provider 下 `FontCollection.defaultFallback(codePoint, ...)` 返回 `null`，FandUI 不依赖该入口，固定使用显式 `TextStyle` family stack 和 `Paragraph.getUnresolvedGlyphsCount()` 验证。P1 字体许可/fallback 项完成，总未完成项由 20 降为 19。

### 2026-08-21 23:37：优化 Mod 版本与组合约束锁定

- 从 Modrinth API按 Fabric loader 和精确 Minecraft game version 筛选 Sodium、Iris、Indium、Lithium、FerriteCore、Krypton、VulkanMod；记录 18 个主版本 ID，并补充 Iris/Indium 推荐的三个 Sodium version ID；
- 下载 21 个实际 JAR 到临时研究目录，逐个校验 Modrinth SHA-512，再记录本地大小与 SHA-256；结构化读取每个 `fabric.mod.json` 的 id、version、environment、depends、breaks 和 nested jars；
- 冻结 1.20.1 最新 Sodium/Iris/Indium 可按 Fabric range 共存，1.21.4 不使用 Indium，26.2 Iris 必须使用 Sodium 0.9.1 而不能使用会 break Iris 1.11.2 的 0.9.2-alpha.4；
- 用 `javap -p -s -c -v` 扫描 Sodium/Iris/FerriteCore 的相关 Mixin 注解和常量池：未发现选定版本直接命中 FandUI 的最终 invoke anchor，但 Iris program/FBO/viewport/RenderTarget 跟踪使其保持高风险；据此强化“真实 GL 快照 + wrapper 变更/恢复”的状态桥规则；
- 完成 P2 的具体版本锁定项，总未完成项由 19 降为 18。没有启动这些 Mod，所有兼容单元格仍是静态候选而非运行通过。

### 2026-08-21 23:47：清理探针日志并启动公共 API 设计

- 用户明确要求删除根目录 4 个 `hs_err_pid*.log`；已按精确路径删除并复查 `remaining_crash_logs=0`，不影响 `RESEARCH.md` 或临时研究目录；
- 再次确认当前后端目标是不强制 Vulkan：1.20.1/1.21.4 使用 Minecraft 当前 OpenGL context，26.2 沿用 Minecraft backend 配置且 FandUI 不改选项或启动参数；
- 先前约定的后端/API 事实核验已经完成，公共 API 设计从本节点开始。下一步只读梳理 `C:/Users/winme/Desktop/FandServer` 中的 FandAPI，再把可评审签名写入第 7 节；第一阶段仍不创建 Gradle 或 Java 项目文件。

### 2026-08-21 23:56：FandAPI 第一轮参考完成

- 在 FandServer commit `9f39e90772306222f927d005d7520dc86246ee2f` 上只读检查 `fand-api` 入口、runtime binder、architecture test、GUI definition/live view、event/service registration 和编码规范；参考路径没有产生工作区改动；
- 第 7.1 节记录 8 个参考文件 SHA-256，冻结采用的入口绑定、定义/会话分离、幂等注册所有权、不可变边界、JSpecify 空值契约和双层 API 泄漏检查；
- 明确不复制服务端 inventory GUI、反射事件总线、通用 service locator 和大规模 registry surface；FandUI API common baseline 固定为 Java 17，三个版本 JAR 使用相同 API class；
- 第 7.2 节已开始第一版结构设计，下一步冻结 `UiRuntime`、Screen/HUD live handle、组件 measure/paint/event SPI 和线程/失败语义。

### 2026-08-22 00:24：公共 API 第一版签名完成

- 将 `UiCapabilities` 与 `UiUnavailableException` 合并到根 API 签名，将 theme、animation manager 和按 key 查询合并到 Screen/HUD/session 主签名，不再以“后续增加”描述第一版已有方法；
- 补齐 `Style`、`Theme`、`AnimationSpec`、`StrokeStyle`、`TextStyle` 和 `TextRequest` 的 builder surface，并补出 `ThemeToken` accessor、`FontFamilies.DEFAULT` 与 `Easings` 常量；
- 将可能在 minor 版本增加派生 accessor 的 `ImageInfo`、`TextLine` 从 record 改为不可变 final value class；固定 `FontWeight.NORMAL=400`、`BOLD=700`；
- 修正 resource reload 事务语义：候选 generation 失败不污染旧 `READY` 内容，只有从未成功解析的 ref 才进入 `MISSING/FAILED`；
- 清理顶部恢复点、可行性表、交付复核和进度表中的旧状态。当前 API 草案已可评审，但尚未生成 Java 源码；首次编译、架构扫描和二进制 baseline 仍按第 12 节进入实现阶段。

### 2026-08-22 00:37：公共 API 契约与跨章节复核完成

- 补齐 pointer/scroll/key/text/composition/focus 事件 accessor、稳定 key/button value、modifier/action 枚举和 `EventContext.sceneToLocal`；事件只保存 scene 坐标，current-target local 坐标由 callback scope 的已提交逆变换计算；
- 补齐 `TextController`、有向 UTF-16 selection 与单轴 `ScrollController` 的绑定、detach、clamp、通知和线程规则；固定 root viewport constraints、基础组件默认值、definition 的 shallow-immutable 边界和 framework-owned handle provenance；
- 将 `VisualState` 收敛为 framework-created final snapshot，固定 Style/Theme 默认值与 token 身份、animation duration/easing、三层失败隔离和 opaque handle 校验规则；
- 消除全局 `TextService` 对 session theme 的隐式依赖：`FontFamilies.DEFAULT` 只展开 bundled CJK/Emoji fallback，主题必须先解析成完整 `TextStyle`；图片资源固定为 static PNG/受限 SVG + ImageIO/Java2D + RGBA8 premultiplied；
- 移除会让物理比例影响逻辑布局的 `MeasureScope.devicePixelRatio()`，明确 scale/device generation 与当前 OpenGL 无热重建边界；同步修正 Skija pipeline 和 P1/P2 验收措辞；
- 重新复核第一阶段 12 项交付与第 12 节清单，未完成项仍为 P0 `8`、P1 `7`、P2 `3`，合计 `18`。本轮仍只修改 `RESEARCH.md`，没有创建 Gradle、Java、native、Fabric 或 Mixin 文件，也没有执行 Git 操作。

### 2026-08-22 00:40：实现前设计确认门槛显式化

- 在第 13 节补充 8 条确认基线，覆盖当前 OpenGL 后端范围、8 模块发布形态、自有 NanoVG JNI、Skija text-block、API 冻结时点、GPU-first 实施顺序、PNG/颜色边界和证据门槛；
- 明确“确认设计”不等于 18 个构建/运行事项已经通过；确认后的首个动作仅为复制 Gradle 9.5.1 Wrapper、创建模块骨架并执行 P0 最小构建/GPU 风险原型；
- 原始工作方式要求确认后进入实现，当前继续保持纯设计状态。本轮没有创建工程或执行 Git 操作。

### 2026-08-22 00:50：进入实现与首轮构建版本纠正

- 用户明确确认第一阶段设计并要求进入实现；已解除“只设计、不创建文件”的阶段门槛；
- 从 `C:/Users/winme/Desktop/git/FandInfinity/template-mod-template-26.2` 复制 Gradle `9.5.1` Wrapper，重新计算的四个 SHA-256 与第 4 节预先记录值逐字一致；
- 已创建根配置和 8 个模块脚本；纯 Java 模块以 Java 17 为共同基线，三个 Loom 模块分别选择 Java 17、21、25，尚未把首次配置成功写成完成；
- 第一次 `./gradlew.bat projects --stacktrace` 因根脚本声明核心插件 `maven-publish apply false` 失败；Gradle 9.5.1 明确报告该声明是无效空操作，删除后继续；
- 第二次相同命令进入插件变体解析后证明 Loom `1.18.0-alpha.16` 要求 `org.gradle.plugin.api-version=9.7.0`，与 Wrapper `9.5.1` 不匹配；该组合标为**已否定**；
- 26.2 本地模板使用 Loom 1.17 系列，本机已缓存稳定版 `1.17.19`；构建锁定暂改为 `1.17.19` 并等待同一命令实测，尚未标为已确认。

### 2026-08-22 01:04：8 模块空构建矩阵通过

- 读取 Loom `1.17.19` JAR 内 `META-INF/gradle-plugins/*.properties` 与插件字节码，确认 `net.fabricmc.fabric-loom-remap -> LoomRemapGradlePlugin`，`net.fabricmc.fabric-loom -> LoomNoRemapGradlePlugin`；
- 1.20.1/1.21.4 改用 remap 插件和 `officialMojangMappings()`；26.2 使用 no-remap 插件、不声明 mappings，并按该模式提供的普通 `implementation` 配置声明 Loader/Fabric API；
- Loom 必须注入 `LoomLocalRemappedMods` 等项目仓库，因此撤销了会阻止插件工作的 `FAIL_ON_PROJECT_REPOS`，依赖仓库仍在根脚本集中声明；
- `JAVA_HOME=C:/Program Files/Zulu/zulu-25 ./gradlew.bat projects --stacktrace` 退出码 `0`，输出完整列出 8 个计划模块；
- 相同环境下 `./gradlew.bat buildAll --stacktrace` 退出码 `0`，字面结果 `BUILD SUCCESSFUL in 1m 6s`、`28 actionable tasks: 28 executed`；1.20.1/1.21.4 均执行 `remapJar/remapSourcesJar`，26.2 执行普通 jar 流程；
- 当前所有 `compileJava` 为 `NO-SOURCE`，所以只将“空构建矩阵”标为已完成；Java 17/21/25 实际源码编译、Fabric metadata、启动和 Mixin selector 均继续保持待验证。

### 2026-08-22 01:18：三版本带源码构建与发布 JAR 审计通过

- 新增共享 `RenderHost`/`OpenGlTarget`，平台模块只返回 borrowed color texture handle、mip、尺寸和 generation token，不把 Minecraft/Fabric 类型带入 renderer；
- 新增显式开关 `-Dfandui.openglProbe=true` 的临时 GPU go/no-go probe：直接重挂 Minecraft `GL_TEXTURE_2D` 主颜色、创建自有 `GL_DEPTH24_STENCIL8` renderbuffer、检查 framebuffer completeness，并用两阶段 stencil + premultiplied alpha 三角形验证写入；默认不绘制；
- probe 捕获并恢复它实际修改的 draw/read FBO、renderbuffer、program、VAO、viewport、polygon mode、blend/depth/stencil/scissor/cull、颜色/深度 mask 和 front/back stencil；默认恢复后再次查询真实 GL 状态并逐字段断言；
- 三版本均新增 client entrypoint、`fabric.mod.json`、required Mixin 配置和第 6.6 节 selector；26.2 host 每帧读取实际 `DeviceInfo.backendName()`，非 OpenGL 时不进入 probe；
- 首次 `clean buildAll` 的三个版本 `compileJava` 均成功，唯一失败是 Gradle 9.5.1 测试 worker要求显式 `junit-platform-launcher`；补齐后 `:fandui-render-opengl:test` 退出 0，2 个 target value-object 测试通过；
- 修正后 `./gradlew.bat buildAll --stacktrace` 退出码 `0`，字面结果 `BUILD SUCCESSFUL in 14s`、`38 actionable tasks: 14 executed, 24 up-to-date`；
- 最终 Fabric JAR 分别为 24652、24656、25192 bytes，均含对应 entrypoint/Mixin 和 API/canvas/core/text/render-opengl 五个 nested JAR；展开后的 metadata 版本均为 `0.1.0-SNAPSHOT`；
- 实测 class-file major：1.20.1 entry `61`、1.21.4 entry `65`、26.2 entry `69`、共享 `OpenGlProbe` `61`。带源码最小编译/JAR 项标为已完成；真实客户端启动、Mixin 命中和 GPU 行为仍未标完成。

### 2026-08-22 01:37：1.20.1 首次 GPU 运行发现并修正状态恢复问题

- 使用 Java 17 启动 `:fandui-fabric-1.20.1:runClient`，通过 `JAVA_TOOL_OPTIONS` 显式设置 `fandui.openglProbe=true` 与 `fandui.openglProbe.assertState=true`；Fabric Loader 0.19.3 识别 FandUI、五个 nested JAR 和 required Mixin；
- Render Thread 字面日志确认 entrypoint 初始化、`runTick(Z)V` 中最终 GUI hook 命中、主颜色 target `854x480`、自有 FBO `2`、运行时 internal format `0x8058` (`GL_RGBA8`)；这证明 selector、颜色纹理重挂接、D24S8 completeness、shader/stencil draw 均已实际执行；
- GL debug 同时报告大量 `GL_INVALID_ENUM: Polygon modes for <face> are disabled in the current profile`。根因是恢复阶段分别调用 `glPolygonMode(GL_FRONT, ...)` 和 `glPolygonMode(GL_BACK, ...)`；core profile 只接受 `GL_FRONT_AND_BACK`。首轮运行因此不计作无 GL error 通过；
- 正常请求窗口关闭后，日志在 `Stopping!` 之后再次出现 target attach，证明 `CLIENT_STOPPING` 之后仍可能经过最终 GUI hook；只在 lifecycle callback 删除资源会导致 shutdown 后重新分配；
- 已把 polygon mode 恢复改为先验证 front/back 查询值一致，再单次 `glPolygonMode(GL_FRONT_AND_BACK, value)`；三个版本桥均增加 `stopping` gate，并在删除资源前置位，阻止关闭后的再提交；
- 修复后共享测试、三版 `compileJava` 和 1.20.1 完整 build 均退出 0；该次因本机同时运行另一 Minecraft 实例、TinyRemapper 冷读依赖，字面耗时 `8m 15s`。无 GL error 与 shutdown 不重建仍等待第二次 1.20.1 运行验证。

### 2026-08-22 01:54：1.20.1 GPU 风险原型通过

- 第二次运行证明该 core profile 的 `GL_POLYGON_MODE` 查询是单值；用长度 2 数组读取时第二槽保持初始 0，不能解释为独立 front/back mode。probe 按预期 fail-closed，只报告一次 `OpenGL core profile returned distinct front/back polygon modes`，没有继续提交；
- 将 snapshot 改为 `glGetInteger(GL_POLYGON_MODE)` 单值，并只用 `glPolygonMode(GL_FRONT_AND_BACK, value)` 恢复；共享测试和三版 compile 再次退出 0；
- 第三次 `:fandui-fabric-1.20.1:runClient` 在显式 probe/state assertion 下，初始 target 日志为 `854x480, FBO 2, format 0x8058`；
- 通过 Win32 `MoveWindow` 把唯一 FandUI 开发客户端窗口改为 `1100x700`，Minecraft framebuffer generation 变为 `1357x828`，probe 重建为 `FBO 5`，格式仍为 `0x8058`；没有创建第二颜色纹理或复制主颜色；
- 正常关闭后命令退出码 `0`，字面 `BUILD SUCCESSFUL in 6m 47s`。对全量 `latest.log` 结构化计数：hook `1`、attach `2`、probe failure `0`、OpenGL debug `0`、`GL_INVALID_ENUM` `0`、`Stopping!` 后 attach `0`、根目录 crash log `0`；
- 因此 P0 的 1.20.1 主颜色重挂接、自有 D24S8、resize 和 vanilla 状态恢复原型标为已完成。Sodium/Iris 组合、RenderDoc 和非凸 winding 仍按原清单保持待验证；
- 三版关闭路径另增加 `FandUI OpenGL probe resources released` 成功日志，供后续 1.21.4/26.2 运行直接证明删除发生在 context 销毁前。

### 2026-08-22 02:01：1.21.4 GPU 风险原型通过

- 使用 Java 21 和同一组显式 probe/state assertion 参数启动 `:fandui-fabric-1.21.4:runClient`；Fabric Loader `0.19.3`、Fabric API `0.119.4+1.21.4`、required Mixin 与五个 nested shared JAR 均正常加载；
- 最终 GUI hook 各运行仅记录一次，初始主颜色 target 为 `854x480`、自有 FBO `3`、运行时 internal format `0x8058` (`GL_RGBA8`)；
- 仅对 PID `29528` 的 `Minecraft* 1.21.4` 开发窗口调用 Win32 `SetWindowPos`，resize 后 Minecraft target 为 `1528x942`，probe 重建为 FBO `6`，格式保持 `0x8058`；
- 正常关闭后命令退出码 `0`，字面 `BUILD SUCCESSFUL in 6m 4s`。全量 `latest.log` 结构化计数：hook `1`、attach `2`、probe failure `0`、OpenGL error/debug `0`、`Stopping!` 后 attach `0`、资源释放 `1`；
- 工作区递归复查 `crash-reports` 为 `0`。因此 1.21.4 selector、主颜色重挂接、自有 D24S8、resize、状态恢复与关闭释放原型标为已完成。

### 2026-08-22 02:08：26.2 GPU 风险原型通过

- 使用官方版本要求的 Java 25 启动 `:fandui-fabric-26.2:runClient`；Fabric Loader `0.19.3`、Fabric API `0.158.0+26.2`、no-remap entrypoint 与 required Mixin 均正常加载；
- Minecraft 先检查本机 Vulkan 能力并因 GTX 760 缺少 `VK_KHR_dynamic_rendering`/`dynamicRendering` 选择原版 OpenGL 路径；关键字面结果为 `Using graphics backend OpenGL`，FandUI 未增加或修改 backend option；
- 最终 GUI hook 各运行仅记录一次，初始 `GlTextureView.glId()` 主颜色 target 为 `854x480`、自有 FBO `4`、运行时 internal format `0x8058` (`GL_RGBA8`)；
- 仅对 PID `18096` 的 `Minecraft* 26.2` 开发窗口调用 Win32 `SetWindowPos`，resize 后 target 为 `1448x919`，probe 重建为 FBO `23`，格式保持 `0x8058`；
- 正常关闭后命令退出码 `0`，字面 `BUILD SUCCESSFUL in 4m 55s`。结构化计数：OpenGL backend `1`、hook `1`、attach `2`、probe failure `0`、`GL_INVALID_*` `0`、OpenGL debug error `0`、`Stopping!` 后 attach `0`、资源释放 `1`；
- 工作区递归复查 `crash-reports` 与 `hs_err_pid*.log` 均为 `0`。三版本 GPU 风险原型至此全部通过；仍未完成的 P0 是 RenderDoc 证明与非凸嵌套 stencil/winding 原型。

### 2026-08-22 02:55：公共基础 API、Layout 与 DisplayList 首个纵向切片通过

- `fandui-api` 新增 Java 17 纯 Java value/contracts：`UiKey`、geometry/constraints、style/theme、Canvas path、resource/text handles、input payload、focus/animation/session contracts，以及 retained `UiComponent`/`UiContainer`；所有公开集合和 byte source 均做防御性复制；
- component tree 已实现单 parent、拒绝 self/cycle/重复 attachment、不可修改 children snapshot、精确 listener registration ownership，以及位于 `cn.fandmc.fandui.internal` 的 UI-thread/dirty binding；未把 Minecraft/Fabric/renderer 类型带入 API；
- `fandui-canvas` 新增 callback-scope `RecordingCanvas2D` 和 immutable `DisplayList`；公开 straight-alpha paint 在记录时转为 `PremultipliedColor`，save/restore 严格 LIFO、重复 handle close 为 no-op、use-after-scope 明确失败、path clip 最大 `8` 层；
- `fandui-core` 新增 `LayoutEngine`/`LayoutSnapshot`/`LayoutNode`：仅测量 direct child、每轮每 child 至多一次、framework-owned `MeasureResult`/`Placeable` 校验、未 placement child 排除、同 z-index 按原 children 顺序稳定排序，并基于冻结 scene bounds 命中测试；
- API 架构测试同时扫描源码和 class constant pool，确认 `cn.fandmc.fandui.api.**` 对 Minecraft、Fabric、Blaze3D、Skija、LWJGL、Mixin/NanoVG binding 零引用；全仓 `src/main/java` 包名前缀检查通过；
- 使用当前 JDK compiler 的 `--release 17` 编译并加载只依赖 `fandui-api` 的 consumer fixture 成功。API `20`、Canvas `4`、Core `4`、OpenGL `2`，合计 `30` tests，failure/error/skipped 均为 `0`；
- `JAVA_HOME=Zulu 25 ./gradlew.bat buildAll --console=plain` 退出码 `0`，字面 `BUILD SUCCESSFUL in 8m 28s`、`50 actionable tasks: 30 executed, 20 up-to-date`；
- 三个最终 Fabric JAR 大小为 `169688`、`169691`、`170234` bytes，均嵌入五个 shared JAR。三个 nested `fandui-api-0.1.0-SNAPSHOT.jar` SHA-256 均为 `EE42A4A81A382798B3EC8D70D56BDFB4DA4FC616C6B3DDE1E20F39425E857D7C`，各含 `127` 个 `cn/fandmc/fandui/api/**.class`，逐文件哈希完全一致；
- 本轮仍未实现 runtime/Screen/HUD service、controller、标准组件、Text/资源 worker、NanoVG JNI 或真实 DisplayList renderer；Javadoc 任务成功但存在缺少公开注释警告，已加入 P1，不把当前纵向切片写成完整 MVP。

### 2026-08-22 03:24：Runtime 定义、控制器、基础组件与 SceneCompiler 补记

- `fandui-api` 已增加 `FandUI.runtime()`、`UiRuntime`、availability/capabilities、Screen/HUD definition/service/session contracts；`FandUiRuntimeBinder` 保持在 `cn.fandmc.fandui.internal`，公共签名未泄漏 Minecraft/Fabric/renderer 类型。Fabric 侧的真实 runtime 与 session 生命周期仍待实现，不能把 API definition 写成已接入；
- `TextController` 与 `ScrollController` 已实现精确 binding owner、幂等 registration close、监听器批次内嵌套 mutation 延后；文字 selection/mutation 校验 UTF-16 surrogate 边界，scroll position/maximum 采用有限非负值与 maximum clamp；
- 首批标准组件已加入 `Box`、`Spacer`、`ConstrainedBox`、`CanvasComponent`、`Row`、`Column`。为让组件 measure 读取布局轮次冻结的解析结果，`MeasureScope` 明确提供 `style()` 与 `theme()`；当前线性布局只覆盖 MVP 的基础排列，不提前实现 grow/shrink；
- `fandui-core` 已增加 `SceneCompiler`：按冻结 LayoutSnapshot 调用组件 paint，父子 opacity 逐层相乘，每组件隔离 Canvas 状态，callback 返回后 `PaintScope`/`Canvas2D` 立即失效，未关闭 `CanvasState` 时整份候选 DisplayList 失败；
- 2026-08-22 03:22 使用 `JAVA_HOME=C:/Program Files/Zulu/zulu-25` 执行 `./gradlew.bat :fandui-api:test :fandui-canvas:test :fandui-core:test --console=plain`，退出码 `0`，字面结果 `BUILD SUCCESSFUL in 11s`；当前保留的 API/Canvas/Core/OpenGL XML 合计 `43` tests，failure/error/skipped 均为 `0`；
- 02:55 日志末尾的“仍未实现 runtime/Screen/HUD service、controller、标准组件”现按本条纠正为：公共定义、controller 和首批组件已完成，实际 runtime/service bridge 与其余交互组件仍待实现。当前继续实施 FUDL encoder/validator、NanoVG JNI 与 backend-neutral batch 原型。

### 2026-08-22 03:39：Java FUDL 1.0 encoder/validator 通过

- `fandui-api` 新增 `cn.fandmc.fandui.internal.canvas.InternalPath`/`PathVisitor`，由 `Path` 在 API artifact 内部回放中立几何指令；canvas 模块不使用 reflection、不复制 Path builder 状态，也没有把 renderer/native 类型暴露给公共 Canvas2D contract；
- `fandui-canvas` 新增 `cn.fandmc.fandui.canvas.internal.fudl`：48-byte `FUDL` header、显式 version/opcode 映射、direct read-only `ByteBuffer` encoder、64 MiB/1M records/1024 save/256 stops 限制，以及逐 record 的结构、有限数值、reserved、stack、path 和 clip-depth validator；
- encoder 将每个 path element、paint、stroke style、scissor、transform、composite 与 image 编为 8-byte 对齐记录；Skija text command 按设计留在 Java，但后续 native draw 的 `commandIndex` 保持原 DisplayList 索引，供统一 `orderKey` 稳定归并；
- native 图片映射研究发现原 64-byte image record 缺少纹理宽高，无法把 source pixel rect 正确转换为 NanoVG image pattern。FUDL `1.0` 在尚未发布前修正为 80-byte `DRAW_IMAGE`，要求 `ImageRef.info()` 提供实际正宽高；该事实已同步到 8.4，不保留错误的 64-byte 实现；
- `FudlEncoderTest` 覆盖全部 9 种 path element、三 stop premultiplied gradient、image region/sampling/texture key、clip save/restore、text omission command index、direct/read-only/alignment，以及 bad magic、unknown opcode、NaN、truncation和 zero texture key；
- 先后执行 `./gradlew.bat :fandui-api:test :fandui-canvas:test --rerun-tasks --console=plain` 与修正后的 `:fandui-canvas:test --rerun-tasks`，最终退出码 `0`，字面 `BUILD SUCCESSFUL in 20s`，没有新编译警告。下一步固定 native source/license 并实现独立 C validator、`NVGparams` callbacks、FUBT writer 与 JNI direct-buffer boundary。

### 2026-08-22 04:31：NanoVG native backend、JNI 与 FUBT 纵向切片通过

- 固定 NanoVG upstream commit `ce3bf745eb2d2dbc14a50bf2446783f691ac4353`，vendor 文件哈希继续以第 8 节清单为准；native backend 直接提供自有 `NVGparams` callbacks，没有链接或调用 `NanoVGGL2`、`NanoVGGL3`、`NanoVGGLES`、OpenGL 或 Vulkan API；
- native C validator 独立解析 FUDL 1.0，不信任 Java validator；NanoVG 回放覆盖 path、非凸 fill、三遍 stencil stroke、scissor、最多 8 层 path clip、图片 source/destination 映射及任意数量 gradient stop；
- FUBT 1.0 固定为四个对齐 section，记录尺寸为 `FuVertex=16`、`FuUniform=176`、`FuBatch=96`、`FuPaintStop=24`；native 保留 DisplayList `commandIndex` 形成稳定 `orderKey`，供后续与 Skija text batches 归并；
- JNI 只接受 direct `ByteBuffer`，Java `NanoVgCompiler` 负责 grow-and-retry 并在返回前通过独立 FUBT validator；native context 使用显式 handle 创建/销毁，非法 handle、heap buffer、截断输入和过小输出均 fail closed；
- Gradle 新增 `configureNativeWindowsX64`、`buildNativeWindowsX64`、`nativeCTestWindowsX64` 与 `nativeTest`。2026-08-22 04:17 的首次端到端运行结果为 CTest `1/1`、JNI `2/2`、`BUILD SUCCESSFUL in 22s`；
- Windows x86_64 DLL 使用静态 MSVC runtime `/MT`。`dumpbin /dependents` 只列出 `KERNEL32.dll`，`dumpbin /exports` 只列出 `nCreate`、`nCompile`、`nDestroy` 三个 JNI 符号；
- 首次跨目录复现发现未加确定性链接选项的两个 178688-byte DLL 仅 PE/COFF timestamp 两处字节不同，SHA-256 分别为 `B4E7B0E0F4F0B0E8295DD183A9A158B4FD267F0FC3C2785E3E39CF06D7D9B721` 与 `4F157910645B1D4F61576DB2279453886B9317B165FC1F13531B6B1542C448E2`；该状态已否定，不能作为发布哈希。

### 2026-08-22 04:39：Windows native 发布产物可重现性闭合

- MSVC DLL 与 native test 链接增加 `/Brepro`，未清理或覆盖前述证据目录；在全新 `windows-x86_64-repro3`、`windows-x86_64-repro4` 目录并行独立 configure/build 后，两个 DLL 均为 `178688` bytes、SHA-256 均为 `85A758C0E94F558A4E54BBB870C7BDCD6BBFC1EA13153731362E043041CADDF5`；
- 两个 native test EXE 均为 `216576` bytes、SHA-256 均为 `B2B834F05F8867C4792EB5002EF57F8A61530D55E79B24E9F5AA8122EB280517`；两个 DLL import library 也逐字节一致，两个独立 CTest 都为 `1/1`；
- 不发布的 `fandui_nanovg_core.lib` 因各次 MSVC object/archive 元数据仍不逐字节相同；它不会分发，且 `/Brepro` 最终 linker 已证明对不同 core archive 生成完全相同的 DLL/EXE。当前发布验收只承诺最终 native 文件可重现，不把中间静态库写成已通过；
- 通过 Gradle 对规范目录重新执行 `:fandui-canvas:nativeTest --rerun-tasks`，退出码 `0`，字面 `BUILD SUCCESSFUL in 28s`；CTest `1/1`、JNI XML `2 tests / 0 failures / 0 errors / 0 skipped`；
- 当前规范 DLL 为 `fandui-canvas/build/native/windows-x86_64-ninja/fandui-nanovg-ce3bf745-fudl1-windows-x86_64.dll`，大小 `178688`，SHA-256 `85A758C0E94F558A4E54BBB870C7BDCD6BBFC1EA13153731362E043041CADDF5`。再次检查依赖仍仅 `KERNEL32.dll`、导出仍仅 3 个 JNI 符号、全工作区 crash log 计数仍为 `0`；
- 下一步增加 FUDL/FUBT byte-level golden 并重跑共享回归；协议稳定性闭合后立即实现 `fandui-render-opengl` 的真实 FUBT consumer，不继续扩大协议 surface。

### 2026-08-22 04:47：FUDL/FUBT 1.0 byte-level golden 通过

- 新增最小固定 fixture：frame id `0x0102030405060708`、viewport `64x32`、DPR `2`，在 `(1,2,3,4)` 绘制 straight-alpha `(1,0.5,0.25,0.5)` 矩形；该 fixture 同时约束 header、record opcode/length/alignment、little-endian、premultiplied color 与 native batch 输出；
- Java FUDL golden 锁定完整 `160` bytes 的逐字节十六进制内容，不只验证字段；任何 ABI、padding、枚举映射或编码顺序变化都会直接失败；
- JNI FUBT golden 锁定总长 `760`、SHA-256 `98D3AED21390ABE2AEADE09186D10EF5AF3012AD3C4005DBB6352FB708CD2F4D`，以及 sections：vertices `(offset=144,count=14,stride=16)`、uniforms `(368,1,176)`、batches `(544,2,96)`、paint stops `(736,1,24)`；
- 执行 `:fandui-api:test :fandui-canvas:test :fandui-core:test :fandui-canvas:nativeTest`，退出码 `0`，字面 `BUILD SUCCESSFUL in 12s`；独立 CTest 仍为 `1/1`。下一实现边界固定为读取已验证 FUBT 的 OpenGL consumer。

### 2026-08-22 04:52：真实 OpenGL FUBT consumer 首版完成

- `fandui-render-opengl` 新增 `OpenGlBatchRenderer`：直接消费已验证只读 FUBT sections，使用 Minecraft borrowed RGBA8 color texture、自有 D24S8、streaming VBO、GLSL 150 program 与 paint-stop texture buffer；不创建第二颜色纹理、context 或 swapchain；
- fragment shader保留 NanoVG inverse scissor matrix、extent/scale、paint matrix、round-rect distance、stroke threshold、texture type 和 premultiplied alpha 语义；paint-stop buffer以两个 `RGB32F` texel表示一个 24-byte stop，shader按 `paintStopFirst/count` 插值全部 `1..256` 个 stop；
- FUBT pass 显式映射 triangle list/strip/fan、11 种 blend factor、硬件 scissor top-left logical 到 bottom-left device 转换、非凸 winding、fill fringe/cover clear、三遍 stencil stroke；
- path clip 使用每帧清零的自有 D24S8：root depth `0`，convex push 先以 parent-depth 生成临时 stencil，再二次 draw 写 child depth；non-convex push 先 winding、cover 写 child depth并清 stencil；pop 先从 child depth生成 stencil，再写回 parent depth。convex push/pop因此各产生两个实际 draw；
- `GlStateSnapshot` 扩充为同时捕获/恢复 array/texture buffer binding、texture unit 0 的 2D binding、texture unit 1 的 buffer texture binding、active texture、scissor box、depth function；仍以恢复后重新 capture 的逐字段比较做运行断言；
- 图片 batch 通过 `OpenGlTextureResolver` 解析 FandUI 64-bit key；缺失或失效 texture fail closed。renderer只借用 texture，不改变其 filter/wrap；sampling mode是 resolver contract的一部分；
- 新增纯 Java tests 固定 logical/physical viewport tolerance、硬件 scissor clamp/Y flip、全部 topology/blend 映射和 clip pass draw-count；`fandui-render-opengl` 当前 `6 tests / 0 failures / 0 errors / 0 skipped`。三版本 bridge compile 同时通过。

### 2026-08-22 04:58：1.20.1 真实 batch consumer 与像素读回通过

- 显式使用 `-Dfandui.openglBatchProbe=true`、规范 native DLL 路径与 `fandui.opengl.assertState=true` 启动 1.20.1；普通 target-only probe保持 disabled，确保成功结果来自 DisplayList -> FUDL -> JNI NanoVG -> FUBT -> `OpenGlBatchRenderer` 路径；
- batch fixture覆盖三 stop gradient、rounded background、convex outer clip、带 hole 的 non-convex nested clip、clip 内 stencil stroke、restore 后 sibling 和另一个带 hole 的非凸 fill；运行时日志为 `22 batches / 25 draw calls / 854x480 / FBO 2`；
- renderer完成并恢复宿主状态后，probe重新绑定 borrowed target读回三个内部像素：nested clip content 为 opaque red、non-convex clip hole保留背景、pop 后 sibling 为 opaque yellow；每通道容差 `8/255`，三点均通过后才打印成功日志；读回阶段自身也执行完整状态恢复断言；
- 通过 Win32 `WM_CLOSE` 正常关闭唯一 1.20.1 开发窗口，Gradle退出码 `0`，字面 `BUILD SUCCESSFUL in 4m 46s`；结构化日志计数：batch success `1`、probe failure `0`、`GL_INVALID_*` `0`、OpenGL debug error `0`、release `1`、`Stopping!` 后 render `0`、crash log `0`；
- 本机同时没有可用 OpenAL device，Minecraft记录一次声音系统停用；它发生在 batch probe成功之后，未影响 Render Thread、像素验证或退出，不能计为 FandUI失败。下一步以相同 fixture验证 1.21.4 与 26.2 OpenGL backend。

### 2026-08-22 05:04：1.21.4 真实 batch consumer 与像素读回通过

- 使用完全相同的规范 native DLL、FUDL fixture、FUBT consumer、像素读回和 GL state assertion 启动 1.21.4；Fabric bridge只负责取得 current target，不复制 renderer逻辑；
- 运行时字面日志为 `22 batches / 25 draw calls / 854x480 / FBO 3`，这同时证明三 stop、非凸 winding、stencil stroke、两层 clip push/pop及三个读回像素通过；
- 通过唯一 `Minecraft* 1.21.4` 窗口的 `WM_CLOSE` 正常退出，Gradle退出码 `0`，字面 `BUILD SUCCESSFUL in 4m 13s`；结构化计数：batch success `1`、probe failure `0`、`GL_INVALID_*` `0`、OpenGL debug error `0`、release `1`、`Stopping!` 后 render `0`、crash log `0`；
- 本机 OpenAL device仍被占用并由 Minecraft主动停用声音，与 1.20.1 一致，不影响这组 renderer 证据。下一步验证 26.2 实际 OpenGL backend。

### 2026-08-22 05:18：26.2 clip-depth 隔离修复与真实 batch consumer 通过

- 26.2 首次真实 batch probe 已提交 `22 batches / 25 draw calls`，但 nested clip content 读回期望 RGB `235,20,15`、实际为 `239,50,61`；补充捕获/恢复并在 FandUI pass 内关闭 `GL_COLOR_LOGIC_OP`、`GL_FRAMEBUFFER_SRGB` 和 `GL_DITHER` 后结果不变，因此该假设被实际复跑否定；
- 对 official-named `minecraft-client.jar` 的 `com.mojang.blaze3d.opengl.GlDevice` 执行 `javap -p -c`，确认其在 `GL_ARB_clip_control` 可用时调用 `ARBClipControl.glClipControl(36001,37727)`，即 `GL_LOWER_LEFT + GL_ZERO_TO_ONE`。FandUI 顶点着色器固定输出 `depthValue * 2 - 1`，依赖 `GL_NEGATIVE_ONE_TO_ONE`；实际冲突是 clip depth mode，不是 Y origin；
- `GlStateSnapshot` 现按 capability 捕获 `GL_CLIP_ORIGIN`、`GL_CLIP_DEPTH_MODE` 和双值 `GL_DEPTH_RANGE`，恢复后把三者纳入真实状态逐项断言；`OpenGlBatchRenderer.beginFrame()` 临时设为 `GL_LOWER_LEFT + GL_NEGATIVE_ONE_TO_ONE` 和 depth range `[0,1]`，结束后还原宿主值；没有改变 shader、batch ABI、Minecraft bridge 或公共 API；
- 共享 OpenGL tests 与三版本 `compileJava` 退出 `0`。干净 26.2 复跑实际 backend 为 OpenGL，字面日志为 `22 batches / 25 draw calls / 854x480 / FBO 4`；该成功日志只在三个像素读回全部通过后产生，且启用了 `fandui.opengl.assertState=true`；
- 通过唯一 `Minecraft* 26.2` 窗口的 `WM_CLOSE` 正常退出，Gradle 返回 `0`、字面 `BUILD SUCCESSFUL in 52s`。单一新 `latest.log` 为 `12190` bytes、SHA-256 `E3CC0B32C5311DA8994B2A271FF1CDEFF909B1A24F1DCB17B649CE7444C88E69`，计数为 backend `1`、hook `1`、batch success `1`、probe failure `0`、state mismatch `0`、`GL_INVALID_*` `0`、GL debug error `0`、stopping `1`、release `1`、NUL `0`；
- 启动前发现一个遗留 26.2 客户端占用旧日志；混合日志没有作为验收证据，两窗口均正常关闭后重新做了上述单进程干净复跑。工作区 `crash-reports` 与 `hs_err_pid*.log` 复查仍为 `0`。至此三版非凸 winding、stencil stroke、depth clip push/pop 与真实颜色目标 batch 路径均通过；P0 只余 RenderDoc 抓帧证明。

### 2026-08-22 05:25：clip-depth 修复后的全量回归与制品审计通过

- 首次把 `nativeTest` 与 `buildAll` 合并执行时，Gradle 在 task validation 阶段因 `configureNativeWindowsX64.vcvarsPath` 未配置而失败；Java 编译或 native 编译均未报错。由现有 CMake cache 反查本机实际 MSVC 为 `F:/VisualStudio/18/BuildTools`，按构建脚本既定入口设置 `FANDUI_VCVARS=F:/VisualStudio/18/BuildTools/VC/Auxiliary/Build/vcvars64.bat` 后原命令成功，不修改 build logic；
- `:fandui-canvas:nativeCTestWindowsX64` 为 `1/1`，JNI native tests 为 `3/3`；随后 `buildAll` 字面 `BUILD SUCCESSFUL in 17s`、`55 actionable tasks: 3 executed, 52 up-to-date`。全仓当前保留的 `18` 个 XML suite 合计 `57 tests / 0 failures / 0 errors / 0 skipped`；
- 规范 DLL 仍为 `178688` bytes，发布哈希继续保持 `85A758C0E94F558A4E54BBB870C7BDCD6BBFC1EA13153731362E043041CADDF5`，因此 Java-only clip-depth 修复未改变 native ABI 或发布二进制；
- 三个发布 JAR 分别为：1.20.1 `305449` bytes / SHA-256 `DC3E227FA5903BA6E7FAEBA49A6DE953B275F75AA0C41DCF28FD2135FA5C40E5`，1.21.4 `305451` / `55737A8013D8CCE15BA16176EF837C162221C86698AB356BFFD6F0C83182F6D1`，26.2 `306001` / `56FAA9F3D16483660C9970EE6DCB215284809C1E475958D1E2455821DEC9DE45`；每个均嵌入 `5` 个 shared JAR；
- 三个 nested `fandui-api-0.1.0-SNAPSHOT.jar` 都是 `178258` bytes、SHA-256 `4E2D2AAF980B50D0CD613FAA0F12646EBB1ABA464E1605C11ECB3190637B2F94`；各含 `167` 个 `cn/fandmc/fandui/api/**.class`，class 名集合相同且逐文件 SHA-256 mismatch 为 `0`。下一纵向切片为实际 Fabric runtime/session、Screen/HUD/input/resource reload bridge，不把当前 GPU probe 当作完整 UI runtime。

### 2026-08-22 06:01：纯 Java Core runtime 纵向切片通过

- 新增 `cn.fandmc.fandui.core.runtime` 实现：`CoreUiRuntime`、Screen/HUD live session、UI thread dispatcher/host、`UiSceneFrame`、focus、animation 与 event dispatcher；公共 API 仍不依赖 Minecraft、Fabric、LWJGL、OpenGL、Skija 或 NanoVG 类型；
- session 递归绑定组件树并验证 identity/key 唯一性；layout 与 DisplayList 只在完整候选成功后原子替换，失败帧沿用上一完整 DisplayList 并以 `FAILED` 关闭对应 session；
- 事件按 capture -> target -> bubble 分发，callback-scope `EventContext` 在返回后失效；已实现 hit-test、scene-to-local 逆变换、focus traversal/方向导航、hover/pressed、pointer capture 与 detach cancel；
- animation 使用 session-scoped 单调时间，close 时以 `SESSION_CLOSED` 完成；跨线程 close 立即把 handle 标为 inactive，实际 detach 排入 UI thread；attached/event 回调内 close 会等最外层 callback 退出后排队清理，后续传播停止且 detached 恰好一次；
- 窄化命令 `./gradlew.bat :fandui-core:test --rerun-tasks --console=plain` 退出码 `0`，Core 共 `17 tests / 0 failures / 0 errors / 0 skipped`；随后 API/Canvas/Core/OpenGL 四模块回归退出码 `0`，18 个 XML suite 合计 `61 tests / 0 failures / 0 errors / 0 skipped`；
- 下一步固定为正式串联 `UiSceneFrame -> FudlEncoder -> NanoVgCompiler -> OpenGlBatchRenderer`，再由三个 Fabric bridge 提供 viewport、target、输入和生命周期。

### 2026-08-22 06:24：异步 NanoVG 到 OpenGL 正式 pipeline 通过

- `DisplayList.combine` 按 HUD/Screen 稳定顺序合并完整 DisplayList，单元素保持原 identity，最大 clip depth 取最大值；每个 SceneCompiler 输出本身以 save/restore 闭合，因此列表边界不会泄漏 Canvas state；
- 新增 `NanoVgCompilerWorker`：native context 只在专用 daemon thread 创建、编译和销毁；队列最多保留一个 active 和一个最新 pending，较旧 pending future 明确取消，frame id 必须单调递增；native `Error` 会完成当前 future 并终止 worker，close 会等待 context 确实销毁；
- 新增 `OpenGlUiPipeline`，正式连接 `UiSceneFrame -> DisplayList.combine -> FudlEncoder -> NanoVgCompilerWorker -> FubtFrame -> OpenGlBatchRenderer`；Render Thread 不等待编译，场景未变化不重复提交，新结果未完成时沿用同 viewport 的上一完整 FUBT，resize/minimize 时不把旧尺寸结果提交到新 target；
- 编译结果按 request id 原子发布；过期失败不污染已有新请求，当前请求失败抛出带 cause 的 `OpenGlRenderException`；FUDL encoding、texture key resolution 和 GPU draw 仍在 Render Thread，NanoVG tessellation 独占 worker；
- 新增纯 Java测试覆盖合并、worker ownership/latest-wins、同场景去重、上一帧回退、resize/minimize、当前/过期失败和 close；真实 DLL worker 测试也通过。C ABI `1/1`、JNI `4/4`；全仓 21 个 XML suite 合计 `70 tests / 0 failures / 0 errors / 0 skipped`；
- `buildAll :fandui-canvas:nativeTest` 字面 `BUILD SUCCESSFUL in 5m 13s`、`55 actionable tasks: 24 executed, 31 up-to-date`；规范 DLL 仍为 `178688` bytes / SHA-256 `85A758C0E94F558A4E54BBB870C7BDCD6BBFC1EA13153731362E043041CADDF5`，工作区 crash artifact 为 `0`；
- 当前三版 JAR 为：1.20.1 `365037` bytes / `7B0C7808F4C0C14F8262422624D05D3CDF65A30EEF216B2658EDC1EAACE10A87`，1.21.4 `365039` / `0E9F9B05CB01C872876EF33855B22447254214D5F912D12C8067DAAFAC2B0A50`，26.2 `365589` / `53A0A3132626E86B7B9B34330CA72C8C4BEF7EDD982337DD1BDE13370EBFCF66`；这些尚未内嵌 native，classifier/extraction 仍按实现待办保留；
- 下一步固定为三个 Fabric runtime bootstrap、`FandUI.runtime()` binder、Screen/HUD/input/resource reload bridge，并把本 pipeline 替换现有仅用于 GPU 验证的 probe 调用路径。

### 2026-08-22 06:56：1.20.1 Fabric runtime bridge 首次真实启动通过

- `FandUiClient1201` 已从 probe-only 入口改为正式 bootstrap：创建并绑定 `CoreUiRuntime`、`CoreResourceService`、单调时钟和 Minecraft Render Thread dispatcher；使用 `HudRenderCallback` 记录当前帧 HUD 是否实际提取，最终 GUI hook 合并 HUD/Screen `UiSceneFrame` 并提交 `OpenGlUiPipeline`；显式 probe 仍由各自 property 独立控制，probe 失败不会污染正式 runtime；
- 新增 `MinecraftScreenHost1201` 与 `FandUiScreen1201`。后者覆盖已核验的 1.20.1 Screen 签名，归一化 press/release、UTF-16 surrogate、pointer move/down/up/drag、vertical scroll、modifier、Tab traversal、Escape/HOST close、pause/background 语义；Minecraft/Fabric/LWJGL 类型仍只存在于版本模块；
- 资源桥已按已核验接口注册 `ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(SimpleSynchronousResourceReloadListener)`；当前 apply 只推进 `CoreResourceService` generation 并通知 listener，图片/字体候选加载仍属于后续 resource pipeline，未写成已完成；
- `./gradlew.bat :fandui-fabric-1.20.1:compileJava --rerun-tasks --console=plain` 字面 `BUILD SUCCESSFUL in 29s`。这证明 `Screen.render/renderBackground`、旧输入、`Minecraft.screen/setScreen`、HUD 与 reload listener 的 named 签名均与实际 Loom classpath 一致；
- 首次用 Java 17 运行 Gradle 在配置期被 Loom 1.17.19 拒绝，字面原因是插件需要 JVM 21+；纠正为 Java 25 运行 Gradle后，1.20.1 Java 17 run configuration 正常启动。这是 Gradle host JVM 与目标模块 toolchain 的区别，不改变 1.20.1 发布 class major；
- 使用规范 DLL property 启动真实 1.20.1 开发客户端，日志确认 `FandUI 1.20.1 initialized in state AVAILABLE`、最终 GUI hook 命中；通过唯一窗口 `WM_CLOSE` 正常停止后日志确认 `FandUI runtime and OpenGL resources released`，Gradle字面 `BUILD SUCCESSFUL in 1m 32s`；
- 本次 `latest.log` 为 `13071` bytes、SHA-256 `C9DA7020376DEF115FD9BF4EC833963D781C9D155C9F91DB555D5016A43EDB94`；FandUI error 为 `0`，crash artifact 为 `0`。开发账号 `401` 与本机 OpenAL device 失败仍是环境噪声，未影响 runtime、render hook 或正常停止；本次没有挂载 UI frame，因此正式 pipeline 的可见 Screen/输入行为留到演示层运行验收。

### 2026-08-22 07:22：三版本 Fabric runtime bridge 编译与启动闭合

- 1.21.4 独立桥使用 `HudLayerRegistrationCallback` 在 `IdentifiedLayer.SUBTITLES` 后设置当前帧 HUD marker，Screen 适配该版四参数 `mouseScrolled` 和四参数 `renderBackground`；资源桥使用该版 `ResourceManagerHelper`。真实启动日志确认 runtime `AVAILABLE`、最终 GUI hook、resource generation `1`、正常停止和资源释放，Gradle字面 `BUILD SUCCESSFUL in 1m 39s`；`latest.log` 为 `10622` bytes / SHA-256 `D5C76C38E68669C0AE2976E55C35D5B50249BD71A6366F8243F838D90D20B190`；
- 26.2 独立桥使用 `Minecraft.gui.screen()/setScreen()`、`GuiGraphicsExtractor`、`KeyEvent`/`CharacterEvent`/`MouseButtonEvent`/`PreeditEvent`、`HudElementRegistry` 和 v1 `ResourceLoader`。IME preedit 的 caret 已由 `PreeditEvent.createFromCallback` bytecode证明是 UTF-16 index；`null` 被归一化为 inactive composition；
- 26.2 首次运行在 client entrypoint 调用 `RenderSystem.getDevice()` 时失败，字面异常为 `Can't getDevice() before it was initialized`。这证明 backend 判定必须延迟到设备和最终目标已存在的 GUI hook；修正后入口只创建 runtime/native worker，hook 再判定实际 backend。失败生成的唯一 crash report 为 `17330` bytes / SHA-256 `CD3D5C7343A939B59C6B3BA5A5F80C183BE27560C0BC518A8146C712589F38A8`，记录事实后已按用户既有清理要求删除；
- 26.2 纠正后真实启动日志依次确认 runtime `AVAILABLE`（backend detection deferred）、Minecraft 实际选择 `OpenGL`、最终 GUI hook、resource generation `1`、正常停止和资源释放，Gradle字面 `BUILD SUCCESSFUL in 2m 18s`；`latest.log` 为 `12216` bytes / SHA-256 `D279BE5D16A0B3138EA304E0A5525021A1AC657C29794BB4428272292A0A11E9`；当前 crash artifact 复查为 `0`；
- 三版联合 `compileJava` 字面 `BUILD SUCCESSFUL in 36s`。随后 Core 增加 renderer-loss 测试，保证 `AVAILABLE -> RENDERER_UNAVAILABLE` 会以 `FAILED` 关闭已有 Screen/HUD，避免 active handle 永久不可见；Core 与三版再编译字面 `BUILD SUCCESSFUL in 35s`；当前工作区 `23` 个 XML suite 合计 `78 tests / 0 failures / 0 errors / 0 skipped`；
- 三版当前都只在显式 `-Dfandui.nativeLibrary=<absolute path>` 下进入 `AVAILABLE`；发布 JAR 尚未内嵌或自动提取 native，这一临时启动条件将在下一个实现切片移除。可见 Screen、鼠标/键盘/IME 与 HUD 内容仍需后续演示层运行覆盖，不能由本轮无 UI 帧冒烟替代。

### 2026-08-22 07:28：native 自动提取与 classifier 实现开始

- 当前切片固定只发布已经可重现验证的 `windows-x86_64` classifier；Linux、macOS、Windows arm64 等未生成的 native 不伪装为已支持，运行时使用明确的 unsupported-platform 诊断；
- 提取路径固定包含完整 SHA-256 与 classifier，进程内锁和文件锁共同串行化写入，临时文件完整校验后原子替换目标；已有缓存每次解析先校验，损坏时从内嵌资源修复；
- `-Dfandui.nativeLibrary=<path>` 保留为显式开发覆盖，并先于平台选择执行；正常发布运行不再要求该 property；
- 计划验证：重复解析、并发、损坏缓存、资源哈希不匹配、临时文件清理、规范 native 与发布副本逐字节一致、JNI 从 classifier 自动提取加载，以及三版最终 JAR 内嵌 classifier。当前均为待运行，不提前标记通过。

### 2026-08-22 07:36：Windows x64 native 发布与自动提取闭合

- 新增 `NativeLibraryExtractor` 与 `NanoVgNativeLibrary`：缓存目录为 `<cache>/<完整 SHA-256>/<classifier>/<versioned file>`，进程内 monitor 与跨进程文件锁共同保护提取；缓存命中先重新计算 SHA-256，临时文件通过内容校验后原子替换；`fandui.nativeCacheDirectory` 可显式覆盖缓存根目录；
- `fandui.nativeLibrary` 保持开发覆盖语义，覆盖路径只要求存在，不强制等于发布哈希，便于验证本地 native 改动；内嵌发布资源始终按硬编码 catalog 哈希校验；`System.load` 的 `LinkageError`/`SecurityException` 已收口为可诊断的 runtime 初始化失败；
- 新增 `fandui-canvas-0.1.0-SNAPSHOT-natives-windows-x86_64.jar`，三版最终 Fabric JAR 的 `fabric.mod.json.jars` 均含 6 个嵌套构件，其中该 classifier 内 DLL 为 `178688` bytes / SHA-256 `85A758C0E94F558A4E54BBB870C7BDCD6BBFC1EA13153731362E043041CADDF5`；
- Gradle `verifyPackagedNativeWindowsX64` 先运行 CTest，再以 `Files.mismatch` 要求规范重建 DLL 与发布副本逐字节一致；`nativeTest` 默认不再注入 property，而是把 classifier 放入测试 classpath 后走真实提取；仍允许 `-Pfandui.nativeLibrary` 显式覆盖；
- 纯 Java提取器测试覆盖首次/重复解析、16 次并发解析只打开资源一次、损坏缓存修复、资源哈希失败后的临时文件清理、开发覆盖优先级和不支持平台诊断；首次运行 `6/6` 通过；
- 无 `fandui.nativeLibrary` 的 `:fandui-canvas:nativeTest --rerun-tasks` 字面 `BUILD SUCCESSFUL in 29s`，独立 CTest `1/1`、JNI `4/4`；canvas 当前合计 `23 tests / 0 failures / 0 errors / 0 skipped`。实际提取 DLL 的路径包含完整发布哈希，文件大小与哈希再次一致；
- 三版无 property 的真实 Fabric 启动仍待本切片下一步验证；Linux/macOS/Windows arm64 classifier 没有产物，保持明确 unsupported，不计入已完成平台。

### 2026-08-22 07:51：三版本无 native property 的真实启动闭合

- 三版均在未设置 `fandui.nativeLibrary`/`JAVA_TOOL_OPTIONS` 的条件下启动；Gradle 由 JDK 25 驱动，Loom 按模块 toolchain 分别以 Java 17、21、25 启动实际客户端。直接用 Java 17 驱动 Gradle 会在配置阶段被 Loom 1.17.19 的 Java 21 最低要求拒绝，这不改变 1.20.1 游戏进程和发布 class 的 Java 17 约束；
- 1.20.1 日志确认 `AVAILABLE=1`、最终 GUI hook `1`、首次补齐的 resource generation `1`、正常 release `1`、FandUI error/native failure `0`；`WM_CLOSE` 后 Gradle字面 `BUILD SUCCESSFUL in 1m 16s`。日志 `13155` bytes / SHA-256 `CED1739E2A4B207BC7037C86A2D9F02BC5DDC4D67050A7BAA8CA7FE3D62E1BA4`；
- 1.21.4 同项计数均为 `1/1/1/1`，FandUI error/native failure `0`；Gradle字面 `BUILD SUCCESSFUL in 1m 29s`。日志 `10622` bytes / SHA-256 `523FC08BF7ECC2F6F0DC0450A167843F46A07289A9E88DE9C8BA67D9285986F1`；
- 26.2 日志确认 Minecraft 实际选择 `OpenGL`，随后 FandUI 最终 GUI hook、generation `1` 和 release 各 `1`，FandUI error/native failure `0`；Gradle字面 `BUILD SUCCESSFUL in 4m 48s`。日志 `12216` bytes / SHA-256 `B5B5AC610A43345A945CD6F7E442DB308A4A570E443EC068C97B1C32E2C62899`；
- 三版本机 OpenAL device 失败仍是既有环境噪声；它没有改变 FandUI 状态或关闭路径。工作区 crash artifact 再次复查为 `0`；
- Windows x64 native 自动提取/校验/classifier 切片现已完成。下一实现项转为 `fandui-text-skija` 的确定性字体 fallback、text-block raster 和纹理上传输入；其他平台 native 干净机验证保持发布前待办。

### 2026-08-22 08:15：Skija text-block 实现开始

- 重新读取现有 `TextService`/`TextLayout`/`TextRequest` 契约、Core resource generation、三版 bootstrap 和第 9 节文字设计；本轮不扩张 `cn.fandmc.fandui.api.**`，文字像素继续作为实现模块内部集成边界；
- 以实际 `skija-shared:0.143.17` class/source JAR 核对 `Paragraph`、`LineMetrics`、`ParagraphStyle`、`TextStyle`、`FontCollection`、`TypefaceFontProvider`、`Surface` 与 `Pixmap` 签名；Maven Central 再次确认 Windows x64 native JAR 为 `10557374` bytes / SHA-256 `7178C082EC5EE800353F8740E6A3EEE743240CFD9B1ED1494BAEBE4034B269BD`；
- 当前实现边界固定为一个 text worker 串行拥有全部 Skija native 对象；调用方只获得 immutable layout 与只读 A8/RGBA premultiplied pixels。layout/raster 请求分别做 in-flight 去重，每个调用方仍得到独立 dependent future；
- 三版构建开始显式嵌入 `skija-shared:0.143.17`、POM 要求的 `types:0.2.0` 和 `skija-windows-x64:0.143.17`。其他平台 Skija artifact 已有存在性/哈希证据，但 NanoVG classifier 仍只有 Windows x64，因此本轮不把其他平台写成可运行支持；
- 后续验证固定覆盖：中英混排、bundled fallback、Emoji/ZWJ、UTF-16 行范围、wrap/baseline、非白/半透明 A8 分类、RGBA premultiplied、并发/取消、generation invalidation、close 和有界 cache。当前这些仍为待运行，不提前标记通过。

### 2026-08-22 08:36：Skija text-block 单元闭合并接入三版本

- Skija 真实 native 测试已覆盖中英混排、bundled 中文/Emoji fallback、ZWJ、UTF-16 行范围、wrap/ellipsis/baseline、A8 与 RGBA premultiplied、并发去重、调用方取消隔离、generation、close 和有界 LRU；`TextRequest` 同时拒绝未配对 UTF-16 surrogate；
- 新增的 A8 颜色复用断言首次发现：Skia 的抗锯齿 alpha 会随绘制 RGB 产生细微变化，而纹理摘要已按设计忽略普通文字 RGB，导致同键不同像素。实现现先用保留请求 alpha 的固定白色生成和识别普通字形；只有检测到彩色字形时才按请求颜色重绘 RGBA。修正后的窄化测试通过，A8 同键现在也保证逐字节同像素；
- 三个 `FandUiClient*` 已各自创建一个 `SkijaTextService(resources::generation)` 并注入 `CoreUiRuntime`，移除启动期 `UnavailableTextService`；关闭顺序固定为 runtime stop -> NanoVG/OpenGL pipeline close -> Skija worker close -> OpenGL probes close；
- `:fandui-api:test :fandui-text-skija:test` 与三个 Fabric `compileJava` 联合命令字面 `BUILD SUCCESSFUL in 1m 7s`、`16 actionable tasks: 16 executed`，因此 Java 17/21/25 编译边界和三个 Loom classpath 已确认。发布 JAR 审计与三版本真实启动仍在本切片内继续，不提前记为通过。

### 2026-08-22 08:52：Skija 发布打包与三版本真实启动闭合

- 设置既有 `FANDUI_VCVARS=F:/VisualStudio/18/BuildTools/VC/Auxiliary/Build/vcvars64.bat` 后执行 `buildAll --rerun-tasks`，字面 `BUILD SUCCESSFUL in 5m 33s`、`56 actionable tasks: 56 executed`；现存 `25` 个 XML suite 合计 `89 tests / 0 failures / 0 errors / 0 skipped`，Javadoc 只有既有缺失注释 warning；
- 三个发布 JAR 均含 `9` 个 nested JAR：五个共享 FandUI Java 模块、NanoVG `windows-x86_64` classifier、`skija-shared:0.143.17`、`types:0.2.0`、`skija-windows-x64:0.143.17`。1.20.1 为 `34900152` bytes / SHA-256 `241723F89CCDFE958763E2050E4A3FDE1FBAFB227155C21D67DC6654F7195BB7`；1.21.4 为 `34900570` / `F99F07E8BD0F351302E7FFFF9CD4F796B186A9FDFE24CC4EC956DEACA75209BB`；26.2 为 `34901365` / `2AE7E40D2AB031DD39C2F22D1FB5DA42F411A969BEAE3BF39CDEC27090A28D63`；
- 逐层重开外层 JAR 和 nested JAR 后，`SkijaTextService.class` 存在；Noto Sans CJK SC 为 `16437364` bytes / `2C76254F6FC379FDDFCE0A7E84FB5385BB135D3E399294F6EEB6680D0365B74B`，Noto Color Emoji 为 `10673480` / `72A635CB3D2F3524C51620CDDE406B217204E8A6A06C6A096FF8ED4B5FD6E27B`；NanoVG DLL 为 `178688` / `85A758C0E94F558A4E54BBB870C7BDCD6BBFC1EA13153731362E043041CADDF5`，Skija DLL 为 `12866048` / `36CFED116271A1AE58192CE86F5E50D0AAEAA35FAFAEB55120AC51B0CCE4ADBB`；
- 三版都在清除 `FANDUI_VCVARS`、`JAVA_TOOL_OPTIONS` 和 `_JAVA_OPTIONS` 的新进程环境中运行 `runClient`，没有 `fandui.nativeLibrary` 覆盖。1.20.1、1.21.4、26.2 分别字面 `BUILD SUCCESSFUL in 1m 31s`、`1m 30s`、`3m 57s`；每版结构化计数均为 runtime `AVAILABLE=1`、最终 GUI hook `1`、resource generation `1`、Skija release `1`、renderer release `1`、FandUI error `0`、native failure `0`；
- 26.2 先记录 GPU 缺少 `VK_KHR_dynamic_rendering`/`dynamicRendering`，随后 Minecraft 明确选择 `Using graphics backend OpenGL`，FandUI 沿用该 backend；没有创建 Vulkan 或第二图形上下文。三个窗口都以 `WM_CLOSE` 正常退出；OpenAL device 失败仍为本机既有环境噪声；
- 三份 `latest.log`：1.20.1 为 `13232` bytes / `538AD59EFBE08BDA6E213D61F456D816A66F18F963D4E479E534567B52DE5E0F`，1.21.4 为 `10698` / `B6588F854318627611A4697C58AF7AC5C58F1820A5E4A575BF3CF373EEB3F786`，26.2 为 `12293` / `71756863E562BBA51A17A3B95A7B929915A18D831018EC4C5777BB715CC32750`；工作区 crash artifact 复查仍为 `0`；
- 下一纵向切片固定为把 `DisplayCommand.DrawText` 接入 FUDL：从 `SkijaTextService` 异步取得 A8/RGBA raster，在 Render Thread 建立有界 OpenGL texture cache，处理 generation/resize/reload/close，再让 NanoVG image quad 使用对应 texture key。当前 `FudlEncoder` 仍忽略 `DrawText`，因此本轮只证明文字服务可用和生命周期完整，不把可见文字渲染写成已完成。

### 2026-08-22 09:53：FUDL/FUBT 1.1、文字纹理与三版本像素闭合

- FUDL/FUBT 升至 `1.1`：`DRAW_IMAGE` record 为 `96` bytes，增加 alpha texture flag 和 premultiplied modulation RGBA；`DisplayCommand.DrawText` 现在以 NanoVG image quad 编码。更新后的 FUBT golden 仍为 `760` bytes，SHA-256 为 `DBDF542C99186C5ED2AEFFF65A5735D35BD25A8179C0AAA2D3B5C1357BF3CD2F`；
- `OpenGlUiPipeline` 已串联异步 `SkijaTextService.raster`：按 `TextLayout` identity 去重；场景替换取消 dependent future；所有 raster 完成前继续显示上一完整帧；raster、encode 或 compile 任一步失败都不提交半帧；每个唯一 layout 只保留一个 raster，并由缓存检测 64-bit texture key 碰撞；
- `OpenGlTextTextureCache` 默认预算 `64 MiB`，按真实像素字节计费并使用 LRU；当前帧纹理固定不可驱逐，A8 上传为 `GL_R8`，彩色文字上传为 `GL_RGBA8`。上传使用 `MemoryUtil.memAlloc` direct staging 并在 `finally` 释放，所有入口受 Render Thread confinement 约束，关闭幂等且恢复上传相关 GL 状态；
- 首次真实文字上传把 heap `ByteBuffer` 传入 LWJGL `GL11C.nglTexImage2D`，NVIDIA 驱动由此崩溃。已以 direct staging 修复并补充测试；生成的 `hs_err_pid18780.log` 在记录根因后按用户既有清理要求删除，当前全工作区 crash artifact 复查为 `0`；
- 26.2 首次 A8 像素读回为黑色。实际原因是 Mojang GL backend 绑定的 sampler object 覆盖 texture object 参数；FandUI pass 现临时解绑 texture unit `0/1` 的 sampler，并把 sampler binding 纳入 `GlStateSnapshot` 捕获、恢复和断言；
- `OpenGlBatchProbe` 现验证 7 个读回点：嵌套裁剪内容、非凸 clip hole、clip pop 后 sibling、A8 调色上半行/透明下半行、RGBA premultiplied 上半行/下半行。1.20.1、1.21.4、26.2 均通过相同 `28 batches / 31 draw calls / 854x480` fixture，FBO 分别为 `2/3/4`；26.2 启用 `fandui.opengl.assertState=true` 且状态恢复无差异；
- 三个独立 native 构建目录产生逐字节一致的 `fandui-nanovg-ce3bf745-fudl1-windows-x86_64.dll`：`179200` bytes，SHA-256 `59C31C10DBD0186B9C13B5C76B30BDC8AB8358F90013AC9885DE6BCCF84DC546`；classifier JAR SHA-256 为 `ED38C3F109512EA3EF7B0B776256D6745935A7C547C13123985194958D950954`；
- 最终全仓命令 `$env:FANDUI_VCVARS = "F:/VisualStudio/18/BuildTools/VC/Auxiliary/Build/vcvars64.bat"; ./gradlew.bat buildAll :fandui-canvas:nativeTest --rerun-tasks --console=plain` 字面 `BUILD SUCCESSFUL in 2m 34s`，`62 actionable tasks: 62 executed`。当前 XML 报告为 `27` 个 suite、`104 tests / 0 failures / 0 errors / 0 skipped`；
- 最终发布 JAR：1.20.1 为 `34925408` bytes / `A18000B7BB34D966A27F787619F0681DAFE1816B3E4656DF0002A9128B7E0B9B`，1.21.4 为 `34925827` / `1AC861A6259FB09480E04FB0F822CC5D5BBC050197806F846A0E7214A903F517`，26.2 为 `34926629` / `EF6738A8C331B46A0915386CB6679076EB8561F5F076CF855B2878784496B4E2`。每个外层 JAR 含 `9` 个 nested JAR；三个内嵌 API JAR SHA-256 均为 `795A0130CF6469BBFFDF79FD95329AA38AF6D73BC53A70C06FB84042DB46FE82`，各含 `183` 个 class，class-name manifest SHA-256 均为 `FAD66E2E42528D4467004467064F6D37F12A997AB2CC2EF773BF38F1EBAED568`；
- 下一产品切片为 PNG decode、resource generation 原子发布和 OpenGL image texture cache；完成后再实现标准 `Text`/`Button`/`TextInput`/`ScrollContainer` 与三版本演示 Screen/HUD。

### 2026-08-22 18:24：PNG 事务资源与前两版图片像素探针闭合

- `fandui-core.resource` 新增 `ResourceLookup`、`ResourceReloadException`、`ImageRaster`、`PngImageDecoder` 并重写 `CoreResourceService`：专用 daemon worker 读取 immutable bytes，严格验证 PNG signature/chunk/CRC/APNG/trailing data/尺寸预算，经 ImageIO 规范化为 sRGB ARGB，再以整数公式转成 RGBA8 premultiplied；canonical pixels 生成 SHA-256 与 64-bit texture key；
- reload 以完整 candidate generation 为事务边界：必需显式资源失败时拒绝 candidate，不推进 generation；已有 READY 图片继续引用旧 generation；framework-owned `ImageRef` 进行 provenance 检查；close 确实停止 `FandUI-Resource-Reload` worker；
- `fandui-render-opengl` 新增统一 direct staging uploader、图片 LRU cache 和 raster resolver；图片 generation 纳入 `SceneKey`，编译结果固定携带对应图片/文字 raster 集，上传或 key collision 失败不发布半帧；minimize、空帧、generation 切换和 close 均释放 active pins/GL texture；
- 单元测试覆盖 premultiply golden、canonical key、错误 signature/APNG/CRC/trailing data/预算、candidate rollback、旧 READY 保留、missing、LRU/预算/采样/碰撞/线程、部分上传清理和 image/text key 跨域碰撞。`:fandui-core:test :fandui-render-opengl:test` 及三个 Fabric `compileJava` 联合命令均字面 `BUILD SUCCESSFUL`；
- `OpenGlBatchProbe` 增加 16x16 RGBA 图片与两个读回点：opaque 行期望 RGB `30,120,200`，alpha 128 的 premultiplied 行期望 RGB `100,15,45`。1.20.1 与 1.21.4 均通过 `32 batches / 35 draw calls / 854x480`，FBO 分别为 `2/3`；1.21.4 还在真实运行中触发第二次资源 reload，generation 从 `1` 原子推进到 `2` 后同样正常释放；
- 1.21.4 通过标题严格匹配的窗口发送正常 close，日志依次出现 `Stopping!`、Skija release、resource service release、OpenGL/runtime release，Gradle session 返回退出码 `0`，字面 `BUILD SUCCESSFUL in 11m 18s`。26.2 和最终全仓结果见后续 18:33 记录。

### 2026-08-22 18:24：现成 NanoVGGL3 能力复核

- 执行三个 Fabric 模块 `runtimeClasspath` 依赖报告，实际 LWJGL 分别为 1.20.1 `3.3.2`、1.21.4 `3.3.3`、26.2 `3.4.1`，且三版均无 `lwjgl-nanovg`；
- 读取 LWJGL `3.3.1`/`3.4.1` generated binding 与上游固定 commit 的 `nanovg.h`/`nanovg_gl.h`，确认 GL3 backend 可直接在当前 FBO 绘制，但公开接口没有 path clip、自定义 `NVGparams` 或多 stop gradient；外部 texture handle 固定登记为 RGBA；
- 因此否定“直接换 `NanoVGGL3` 且保持像素/API 语义不变”的假设。当前只保留固定上游 C core 和薄 callbacks，不自行重写 path/tessellation；详细采用边界见 8.2.1。

### 2026-08-22 18:33：图片资源切片三版运行与全仓回归闭合

- 26.2 在 `-Dfandui.openglBatchProbe=true -Dfandui.opengl.assertState=true` 下实际选择 Minecraft `OpenGL` backend，相同图片 fixture 字面为 `32 batches / 35 draw calls / 854x480 / FBO 4`；只向命令行含 `F:/FandUI` 的开发客户端发送正常 close，保留系统中另一个独立 26.2 客户端；日志确认 Skija、resource service、runtime/OpenGL 依次释放，Gradle退出码 `0`、字面 `BUILD SUCCESSFUL in 1m 31s`；
- 三版最新日志均且仅有一次 `32 batches / 35 draw calls` 成功、一次最终 release，probe failure、state mismatch、`GL_INVALID_*` 均为 `0`。日志分别为：1.20.1 `13845` bytes / SHA-256 `94D647238A1E6A8B59461C97779ABF202D4FD24C7D8676DE1AC29CCCF596F85D`；1.21.4 `11059` / `AB8F715E43649A8D23248260193716F1317A281F51852B65FF5C5EA50EC517ED`；26.2 `10686` / `732C111BC511B5449ADA4A5C98EF4C1CC19ADE7E04C02FF18DE6A1C0E66E6942`；
- 执行 `$env:FANDUI_VCVARS='F:/VisualStudio/18/BuildTools/VC/Auxiliary/Build/vcvars64.bat'; ./gradlew.bat buildAll :fandui-canvas:nativeTest --rerun-tasks --console=plain`，CTest `1/1`，Gradle 字面 `BUILD SUCCESSFUL in 2m 23s`、`62 actionable tasks: 62 executed`；结构化 XML 汇总为 `29 suites / 119 tests / 0 failures / 0 errors / 0 skipped`；
- 三个发布 JAR：1.20.1 `34966038` bytes / SHA-256 `8B903A24729708302AC66E40F237888A260C355DB530CF718A88AC4A9D8553C9`；1.21.4 `34966470` / `C56524BFA757A6ABD064A0462416A97A6F30B26D2E240ED17970029EF81FA4DF`；26.2 `34967201` / `55258F4600169E3151E97675DE3189DD77200C4123E35E130C2B2531873CDF06`；
- 每个发布 JAR 重开后均有 `9` 个 nested JAR；内嵌 `fandui-api` 都是 `178317` bytes / SHA-256 `795A0130CF6469BBFFDF79FD95329AA38AF6D73BC53A70C06FB84042DB46FE82`，含 `183` 个 FandUI class（`167` 个 `api` + `16` 个 `internal`），因此三版本公共 API 字节一致；
- 全工作区 `hs_err_pid*.log`、`replay_pid*.log`、`*.dmp` 与 `crash-reports` 文件复查为 `0`。图片资源纵向切片完成，下一项为标准 `Text`、`Button`、`TextInput`、`ScrollContainer`。

### 2026-08-22 19:07：NanoVG 现成库迁移决定修正

- 用户明确要求优先采用现成 NanoVG 库。早期自有 C core/JNI 的直接原因是当时仍要求自定义 Vulkan renderer；当前产品边界已经改为沿用 Minecraft 的实际 OpenGL backend，该前提已经失效；
- 发布目标改为按 Minecraft 实际 LWJGL 版本分发 `org.lwjgl:lwjgl-nanovg`：1.20.1 对齐 `3.3.2`、1.21.4 对齐 `3.3.3`、26.2 对齐 `3.4.1`，并使用公开 `NanoVG`/`NanoVGGL3` API。公共 API 继续保持对 LWJGL/NanoVG 类型零引用；
- 重新逐行核对上游 `nanovg_gl.h` 的 `glnvg__renderFlush`、`glnvg__fill` 与 `glnvg__stroke`：GL3 backend 在 flush 时设置完整 `0xffffffff` stencil mask，非凸 fill 和 stencil stroke 使用并清零全部 8 位 stencil，结束单次调用时还会禁用 stencil test。因此外部保留一个 stencil bit 的拼接方案不成立；
- 任意 `clip(Path)` 的功能等价迁移采用同一 OpenGL context 内的临时颜色层与路径 alpha mask，再合成回 Minecraft 当前颜色目标；矩形嵌套裁剪继续直接映射到 NanoVG scissor。临时层按 target 尺寸复用并受显存预算限制，不创建第二 context、窗口或 swapchain；
- 多 stop linear/radial gradient 改为有界的 premultiplied RGBA 查找纹理，由 `nvgImagePattern` 使用；Skija A8 文字纹理通过 `GL_R8` swizzle 暴露为 RGBA sample，外部纹理以 `NVG_IMAGE_NODELETE` 借给 NanoVG，所有权仍在现有 FandUI LRU cache；
- 当前运行时仍是已验证的 FUDL/FUBT 旧链，以上是迁移中的目标状态，不标记为已实现。切换门槛固定为现有 9 个像素读回点、完整 GL state restore、三版本真实启动和显存压力均通过；随后删除 vendored NanoVG、CMake/JNI、classifier、FUDL/FUBT 与自有 batch shader，不长期保留双 renderer。

### 2026-08-22 19:21：Text、Button 与 ScrollContainer 窄化闭合

- `ComponentContext` 现提供 session 级 `TextService` 与“下一 UI turn”调度；`Text` 采用 latest-request-wins，不阻塞首帧，新结果等待时保留上一完整布局，过期 future 会取消，完成结果只在后续 UI turn 原子发布；
- baseline 已从 `MeasureResult` 经 `Placeable`、内部 measured node 一直冻结到 `LayoutNode`，`Text` 同时发布 alphabetic 与 ideographic baseline；绘制限制在组件 bounds；
- `Button` 为单 child、主键点击、pointer capture、Enter/Space 激活和禁用门控；hover/pressed 状态按命中节点祖先链解析，child 被命中时父按钮仍得到正确视觉状态；
- `ScrollContainer` 为单 child vertical/horizontal viewport，支持 controller offset、extent clamp、wheel 和主键 drag；组件通过内部 clip requirement 强制 bounds clip，`LayoutSnapshot.hitTest` 同时检查祖先裁剪，viewport 外的 child 不再接收事件；detach 后 controller 解除单 owner binding、清除 maximum 但保留最终 offset；
- 执行 `./gradlew.bat :fandui-api:test :fandui-core:test --rerun-tasks --console=plain`，退出码 `0`，字面 `BUILD SUCCESSFUL in 25s`、`9 actionable tasks: 9 executed`。测试 XML 汇总为 `19 suites / 65 tests / 0 failures / 0 errors / 0 skipped`；
- 下一实现项为 `TextInput`：controller 单绑定、UTF-16 selection/edit、caret/selection 几何、pointer hit-test、键盘/已提交文本/IME composition；Skija `Paragraph` 继续只留在 text worker，UI thread 只接收不可变几何结果。

### 2026-08-22 20:14：TextInput、Skija 编辑几何与三版编译闭合

- 公共纯 Java 文字契约新增 `TextAffinity`、`TextPosition`、`TextRange`、`TextRangeGeometry`、`TextGeometry`，`TextService.hitTest(...)` 与 `geometry(...)` 只返回 immutable Java 值；Skija `Paragraph` 继续由单一 text worker 独占，UI thread 不持有 native paragraph；
- `TextInput` 已实现 `TextController` 单 owner 绑定、UTF-16 selection/edit、surrogate-safe 左右移动和删除、pointer click/drag selection、focus/pointer capture、Enter submit、Ctrl/Super+A、IME composition preview/block underline/commit、caret/selection/composition 绘制及单行水平滚动；异步 visual 仅在 layout 与 geometry 同时完成后原子发布，等待期间保留上一完整 visual；
- 本轮修正末尾 caret 的滚动上限，使 `caret.x + caretWidth` 完整进入 viewport；空 `TextInputEvent` 会结束活动 composition 且不修改 controller；过期 pointer hit future 在 controller 外部变化或交互取消时确实取消；删除未参与决策的 `pointerReleased` 状态；Skija `hitTest`/`geometry` 对 foreign layout 与 `raster` 一致返回 failed future；
- 实际 Skija tag 为 `0.143.17`，Git commit 为 `66d99dd9a977ca72b5f99a4ee43a16567f35241c`；核对源码坐标为 `platform/cc/paragraph/Paragraph.cc`、`shared/java/paragraph/Paragraph.java`，底层命中逻辑进入 Skia `TextLine::getGlyphPositionAtCoordinate`；
- 对 `A😀中文B` 的真实探针发现：`maxLines=1`、`wrap=NONE` 时按精确 intrinsic width 二次 `Paragraph.layout` 会把末尾 `B` 排到范围外，修正前 line UTF-16 range 为 `0..5`、修正后为 `0..6`。实现使用 `paragraph.layout(Math.nextUp(desiredWidth + 1.0f))`，并保留原因注释；
- 新增空文本测试又确认 Skija 对空 paragraph 的 `getLongestLine()` 和 unresolved glyph count 使用负哨兵。实现仅在请求文本为空时把这些 native 哨兵归一为 `0`，非空文本仍执行严格校验；空 caret 的 `-0.0` 同时在 native 边界归一为 `0.0`；
- 测试覆盖新增空文本、末尾 caret、RTL/Bidi、空 IME commit、过期 hit-test 取消及 controller 双绑定。`./gradlew.bat :fandui-api:test :fandui-core:test :fandui-text-skija:test --rerun-tasks --console=plain` 字面 `BUILD SUCCESSFUL in 1m 3s`，XML 汇总 `20 suites / 78 tests / 0 failures / 0 errors / 0 skipped`；
- `./gradlew.bat :fandui-fabric-1.20.1:compileJava :fandui-fabric-1.21.4:compileJava :fandui-fabric-26.2:compileJava --rerun-tasks --console=plain` 字面 `BUILD SUCCESSFUL in 1m 56s`、`8 actionable tasks: 8 executed`。下一项进入现成 `lwjgl-nanovg`/`NanoVGGL3` renderer 迁移，旧 renderer 在像素/state/三版运行/显存门槛全部通过前保持可回退但不长期共存。

### 2026-08-22：本机运行验证暂停（用户明确要求）

- 从本条记录起，不再执行任何 `runClient`、Minecraft 开发客户端、真实窗口探针或其他会启动游戏进程的任务；该限制优先于后续章节中遗留的“真实启动验证”计划；
- 允许继续执行的验证仅限 Gradle 编译、单元测试、静态引用审计、JAR 内容与哈希检查，以及不启动 Minecraft 的 native linkage test；
- 已检查当前进程命令行，结果为 `NO_FANDUI_JAVA_OR_GRADLE_PROCESS`，没有残留的 FandUI Java/Gradle 客户端进程；系统中用户自行运行的其他 Minecraft 进程不触碰；
- 后续涉及真实 GPU、窗口、Mixin 运行命中或游戏内像素的结论只保留既有已验证记录，不在本限制有效期间重新运行。新增实现仅以无客户端验证推进，并明确保留其运行时验收缺口。

### 2026-08-22：本机运行限制解除与 backdrop blur 契约

- 用户已明确解除上一条本机运行限制，后续允许按验收需要启动 FandUI 开发客户端；仍控制并行度，避免同时运行多个 Minecraft 客户端；
- 新增目标 API 形态固定为 `Style.builder().background(Color.rgb(0x20AFFF).withAlpha(0.42f)).cornerRadius(12.0f).backdropBlur(18.0f).border(1.0f, Color.rgb(0x8EDCFF).withAlpha(0.55f)).build()`；本条只记录设计契约，`backdropBlur` 此刻尚未实现；
- `backdropBlur(radius)` 定义为采样并模糊组件后方已经绘制的颜色，随后以组件圆角轮廓裁切，再绘制半透明背景、边框和清晰的子内容；它不是 box shadow，也不模糊组件自己的文字或控件；
- 性能边界固定为 Render Thread 上复用离屏纹理/FBO、可分离模糊、半径量化、有界显存预算和尺寸变化时重建；公共 API、Core 与 DisplayList 不暴露 OpenGL、LWJGL 或 NanoVG 类型；
- 最新 `./gradlew.bat buildAll --rerun-tasks --console=plain --max-workers=2` 在 `:fandui-fabric-1.20.1:remapJar` 与 `:fandui-fabric-1.21.4:remapJar` 失败，字面根因为 Gradle 写 build-cache 时 `java.io.IOException: 磁盘空间不足`；检查确认 `C:` 可用 `0 MiB`、`F:` 仍约 `84.7 GiB`，不是 Java 编译或测试失败。后续先用 `--no-build-cache`，必要时把 `GRADLE_USER_HOME` 定向到 `F:`，不擅自删除用户缓存。

### 2026-08-22 23:34：右下角纯 backdrop blur 开发 HUD

- 新增 property-gated 开发 fixture：仅在 `-Dfandui.demo.blur=true` 时挂载 `fandui:demo/blur` HUD；三个 Fabric 版本共用 `cn.fandmc.fandui.internal.demo.BlurDemoHud`，发布运行默认不显示任何 FandUI HUD；
- 效果节点为 `50x50` 逻辑像素 `Spacer`，样式只有 `backdropBlur(18.0f)`。两个无 paint callback 的 `ConstrainedBox` 把它放在右下角并保留 `12` 逻辑像素边距；未设置 background、border、corner radius、image 或 text；
- `BlurDemoHudTest` 在 `320x180` viewport 下确认效果节点 scene bounds 为 `258,118,50,50`，DisplayList 恰有一条 `BackdropBlur`，且 `FillRect`、`FillRoundedRect`、`FillPath`、`StrokePath`、`DrawImage`、`DrawImageRegion`、`DrawText` 均不存在；
- `./gradlew.bat :fandui-core:test --rerun-tasks --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 1m 18s`；三版 `compileJava` 联合命令字面 `BUILD SUCCESSFUL in 1m 50s`、`8 actionable tasks: 8 executed`。下一条记录实际 1.20.1 客户端挂载、渲染与关闭结果。
- 以 `JAVA_TOOL_OPTIONS=-Dfandui.demo.blur=true -Dfandui.opengl.assertState=true` 启动 1.20.1，普通 target probe 与 batch probe 均保持 disabled；Render Thread 字面确认开发 HUD 已挂载、runtime 为 `AVAILABLE`、最终 GUI hook 命中；进入本地世界后正式 pipeline 在 Minecraft 当前 `1920x1121` 颜色目标、`FBO 4` 上提交 `1 batch / 4 draw calls`。客户端留给用户交互检查近景、远景与移动画面；关闭、释放和最终 crash artifact 结果待窗口结束后补记。

### 2026-08-23：backdrop blur 小范围时域闪烁缓解（后续结论已更正）

- 用户在移动视角和远景观察中发现右下角 blur 出现类似摩尔纹的闪烁，树叶等高频内容最明显；根因定位到一次性大倍率降采样会跨过高频世界像素，导致每帧采样集合随相机亚像素移动跳变；
- `NanoVgBackdropBlur` 现固定先用硬件 blit 做首级 `2x` 降采样，后续每一级以 `3x3 tent` 预滤波后再 `2x`，然后执行既有可分离模糊；仍只复用两张纹理，显存预算与关闭所有权不变；
- `NanoVgBackdropBlurTest` 覆盖首级 blit、后续 tent pass、draw-call 计数、尺寸规划和显存预算；`:fandui-render-opengl:test` 与三个 Fabric `compileJava` 联合命令字面 `BUILD SUCCESSFUL in 30s`、`15 actionable tasks: 15 executed`；
- 真实 NVIDIA OpenGL 3.3 context 中 shader 编译和执行成功，运行日志先后确认 `854x480 / FBO 4 / 1 batch / 5 draw calls` 与 resize 后 `1920x1121 / FBO 8 / 1 batch / 6 draw calls`，GL 状态断言无差异；用户当时在 `50x50` 小范围内观察后确认改善。后续右半屏压力测试证明该范围不足以判断全场景时域现象，不能继续把本条记为“根治”；最终由用户判定剩余草地/树叶等闪烁属于 Minecraft 场景自身，本轮不再修改 Blur 采样语义。

### 2026-08-23：viewport、GUI Scale 与界面尺寸自动同步实现

- 三个 Fabric bridge 的最终 GUI pass 均重新读取 `Window.getGuiScaledWidth/Height()`、`Window.getGuiScale()` 与当前 Minecraft 颜色目标尺寸；Screen 回调提供的鼠标/拖拽/滚轮坐标本身已是 Minecraft 逻辑坐标，因此组件开发者不需要注册窗口事件或自行换算 framebuffer 坐标；
- `AbstractCoreSession.prepareFrame` 现把 viewport、resource generation、LayoutSnapshot 与 DisplayList 作为同一完整帧提交：候选布局/绘制失败时回滚 staged viewport/generation；旧 DisplayList 只在 viewport 和资源代际都完全兼容时回退，resize 失败不再产生“旧画面 + 新 viewport”的错误组合；
- 逻辑宽高变化会自动完整重布局和重绘；纯 framebuffer 尺寸或 DPR 变化只发布新 `UiViewport`，复用 Core 的 immutable LayoutSnapshot/DisplayList。OpenGL pipeline 仍按完整 viewport 更新像素目标；同 DPR 的 resize 复用图片/文字 raster 和未完成文字任务，GUI Scale/DPR 改变才重新栅格文字；
- `Text` 会因新逻辑 maxWidth 自动取消旧异步排版并 latest-wins 重排；`TextInput` 在 measure 中重算内容宽度与水平 offset；`ScrollContainer` 重算 maximum 并夹取 controller offset；FBO、路径裁剪层、共享 mask 与 blur pool 均按颜色 handle、目标代际和物理尺寸自动重建；
- 新增回归覆盖：仅 framebuffer 变化、仅 DPR 变化、逻辑 resize、连续 resize、失败帧原子性、异步文字 retarget、GUI Scale 文字重栅格、Text reflow、TextInput 横向偏移、ScrollContainer clamp，以及右下角 blur 在 `320x180`、`640x360`、`200x100` 下始终保持 `50x50` 和 `12px` 边距；测试代码均未调用开发者侧 `invalidate()`；
- 当前窄化结果：`:fandui-core:test` 字面 `BUILD SUCCESSFUL in 31s`，`:fandui-render-opengl:test` 字面 `BUILD SUCCESSFUL in 37s`。三版本联合编译、更新后客户端 resize/GUI Scale 实机验证、最终 `buildAll` 与发布 JAR 审计仍待本切片后续完成。

### 2026-08-23：resize 后 backdrop blur 跨帧消失修复

- 用户确认更精确的复现条件：冷启动时任意窗口尺寸和 GUI Scale 均可显示，问题只发生在同一进程内切换尺寸之后；此前仅证明 resize 首帧执行 blur 命令，未覆盖后续帧；
- 根因是 `NanoVgFramebuffers` 用颜色 texture ID、尺寸和 `RenderTarget` 对象地址缓存借用 FBO。Minecraft resize 会在同一个 `RenderTarget` 对象中删除并重建颜色纹理，OpenGL 又允许复用相同 texture ID；缓存键因此可能保持不变，而 FBO attachment 仍引用已被替换的旧纹理对象，后续 FandUI pass 不再进入最终 present 的颜色附件；
- 正式修复不再依赖外部生命周期代际是否精确：复用路径每帧对 root FBO 重新执行一次 `glFramebufferTexture2D`，把 Minecraft 当前颜色纹理重新附着；尺寸、clip 层或共享 mask 变化时才重建自有 depth/stencil、mask、clip 和 blur 资源，因此没有每帧纹理/FBO 分配；
- 临时 GPU 探针在 resize/GUI Scale 往返后采样第 `1/2/3/10/60` 帧，确认 source、mask 和 composite 持续有效；例如 `854x480 / DPR 2` 第 60 帧仍有 `9945/10404` 个 RGB 像素改变、最大通道差 `129`。用户随后在游戏内确认“完美解决”；探针代码与 `glReadPixels` 已从正式路径删除；
- 1.20.1 开发客户端通过正常窗口关闭，日志确认 Skija、资源服务、runtime 与 OpenGL 资源全部释放，Gradle `runClient` 字面 `BUILD SUCCESSFUL in 2m 21s`。测试期间临时修改的开发运行目录 `guiScale` 已恢复为 `1`。

### 2026-08-23 14:39：共享完整 Demo Screen 与三版开发入口

- 用户在游戏内确认 resize 后 Blur 跨帧消失问题“完美解决”，该缺陷关闭，不再重复探查；
- 新增 `cn.fandmc.fandui.internal.demo.FandUiDemoScreen`，仅在 `-Dfandui.demo.screen=true` 时安装。开发 fixture 首次 client tick 自动打开，关闭后可用 `F8` 重开；开关关闭时不注册内置图片、按键或 Screen，不改变库 Mod 的默认运行行为；
- 三个 Fabric 模块只保存薄入口。此处记录的初版 1.20.1/1.21.4 `KeyBindingHelper` 与 26.2 直接构造 `KeyMapping` 方案，已在 15:31 的真实 Screen 重开验证中被“直接轮询 F8 上升沿”取代；初版依赖事实仍保留为历史证据，不再描述当前实现；
- Demo 使用一个共享响应式组件树：最大 `620x430`、最外层 `12` 逻辑像素 inset、`18` 半径 backdrop blur、半透明深色背景、蔚蓝描边、内置 `24x16` PNG、圆角 panel/scroll/card 嵌套裁剪、Button、TextInput 和 vertical ScrollContainer。初版 Arabic 样例因当前 bundled fallback 未覆盖而出现缺字，实机视觉检查后改为 French；当前可见内容包含中文、English、日本語、French 和彩色 Emoji；
- `FandUiDemoScreenTest` 覆盖内置 PNG 走正式 `CoreResourceService` 解码为 `READY 24x16`、`800x600` 和 `320x180` 响应式 panel bounds、DisplayList 中 Blur/图片/圆角填充/描边以及 `maximumClipDepth >= 3`，并通过真实 `CoreScreenSession` event route 验证 Button click、中文 `TextInputEvent` 和 scroll offset；
- 第一次三版联合编译只在 26.2 因旧 `KeyBindingHelper` 包不存在而失败；按上述实际字节码事实修正后，`:fandui-fabric-26.2:compileJava` 字面 `BUILD SUCCESSFUL in 12s`。最终 `:fandui-core:test :fandui-render-opengl:test` 加三个 Fabric `compileJava` 的联合命令字面 `BUILD SUCCESSFUL in 19s`、`17 actionable tasks: 17 executed`；Core 为 `10 suites / 52 tests`，OpenGL renderer 为 `11 suites / 40 tests`，均为零 failure/error/skipped；
- 下一项只串行启动一个 1.20.1 开发客户端，验证真实 Skija 混排/Emoji、图片上传、嵌套裁剪、按钮、输入、滚动、F8 重开、GUI Scale/resize 与 reload；未完成前不把“三版本真实 Demo 验收”勾选为完成。

### 2026-08-23 15:31：1.21.4 Demo、F8 与文字设备像素对齐

- 1.20.1 实机已确认完整 Demo 首帧、resize 后重挂接、按钮和文字输入状态变化；有效截图为 `fandui-fabric-1.20.1/run/screenshots/2026-08-23_14.50.25.png` 与 `2026-08-23_14.52.02.png`。初始日志为 `854x480 / 39 batches / 64 draw calls / FBO 2`，resize 后为 `1920x1121 / 53 batches / 78 draw calls / FBO 11`，资源推进到 generation `1`，没有 FandUI GL 状态断言差异；
- 初版 `KeyMapping.consumeClick()` 在任意原版 Screen 打开时不能可靠记录 F8。直接对三个当前 Loom Minecraft JAR 执行 `javap`：1.20.1/1.21.4 均确认 `InputConstants.isKeyDown(long,int)` 与 `Window.getWindow()`；26.2 确认 `InputConstants.isKeyDown(Window,int)` 与 `Window.handle()`。三个薄 bridge 现仅在 Demo property 启用时每 tick 轮询固定 F8，并以 `demoKeyWasDown` 做上升沿判定，不再注册发布按键；
- 三版本联合 `compileJava --rerun-tasks --no-build-cache --max-workers=2` 字面 `BUILD SUCCESSFUL in 20s`、`8 actionable tasks: 8 executed`。修改后的 1.21.4 实机先自动打开一次，定向发送 `Esc` 后再发送 F8，日志出现第二次 `FandUI opened the full Screen development demo`；截图 `artifacts/fandui-1.21.4-f8-reopened.png` 的 SHA-256 为 `9AAF130B16CA28797749CC51E439B798ADDCF17E3AB6442A8A2117AA4CE2659F`；
- 同一 1.21.4 进程从 `854x480 / 39 batches / 64 draw calls / FBO 3` resize 到 `1262x828 / 53 batches / 78 draw calls / FBO 12`，共享面板保持最大 `620x430` 并重新居中；随后用户调整到 `1920x1121` 时 renderer 继续重挂到 `FBO 6`。正常窗口关闭确认 Skija、resource service、runtime 与 OpenGL 资源依次释放，Gradle `runClient` 退出 `0`；
- 用户指出状态行“等待操作 / 已加入 1 次”无论是否点击都比其他文字略糊。该行与卡片共用同一 `13px` 样式，唯一关键差异是位于 `Row` 的 `CrossAxisAlignment.CENTER`，其 `(available-child)/2` 可产生半像素平移；文字已经按 DPR 栅格，后续外部纹理在线性采样下落在非整数设备像素才造成局部模糊；
- 正式修复新增 `NanoVgPixelAlignment`：只对文字外部纹理、只在 NanoVG 当前 transform 为纯平移时，把带 raster padding 的纹理左上角吸附到 `1 / devicePixelRatio` 网格；普通图片及缩放/旋转文字保持原坐标语义。`Player` 复用 `float[6]` transform 与 `float[2]` offset scratch，不产生每条文字的临时对象；
- 新增 5 个像素对齐测试，覆盖 DPR 1、DPR 2、已对齐坐标、旋转/缩放跳过及参数校验。`:fandui-render-opengl:test :fandui-fabric-1.21.4:compileJava` 字面 `BUILD SUCCESSFUL in 28s`；renderer XML 为 `12 suites / 45 tests / 0 failures / 0 errors / 0 skipped`。更新客户端后用户实机确认“果然不糊了”，该缺陷闭合。

### 2026-08-23 15:35：26.2 完整 Demo 实机验收

- 以 Java 25 和 `-Dfandui.demo.screen=true -Dfandui.demo.blur=true -Dfandui.opengl.assertState=true` 启动 26.2。Minecraft 字面检测到 NVIDIA GTX 760 缺少 `VK_KHR_dynamic_rendering` 与 `dynamicRendering` feature，随后由原版选择 `Using graphics backend OpenGL, using drivers: 3.3.0 NVIDIA 475.14`；FandUI 没有创建、修改或切换图形后端；
- FandUI 初始挂接为 `854x480 / 39 batches / 65 draw calls / FBO 4`，用户 resize 后为 `1920x1121 / 53 batches / 80 draw calls / FBO 29`，再切回为 `854x480 / 53 batches / 78 draw calls / FBO 7`；F8 上升沿入口累计产生 `6` 次真实打开事件，说明任意 Screen 下的重开路径可用；
- 用户执行资源重载后日志从 generation `1` 推进到 generation `2`，重载完成后 Demo 继续打开和绘制；最终用户明确确认“26.2实测成功”；
- 只向命令行包含 `F:/FandUI/fandui-fabric-26.2` 的开发客户端 `PID 36536` 发送正常 `WM_CLOSE`，保留用户自己的 26.2 `PID 19948`。停止日志确认 Skija、resource service、runtime/OpenGL 依次释放，Gradle 字面 `BUILD SUCCESSFUL in 2m 54s`、退出 `0`；
- 最新 26.2 日志为 `15961` bytes / SHA-256 `043CA5B4914F343A69D293E63421E482AB9C0CEED55A065C8D8A7B33E4EA9F`；结构化复查为 attach `3`、generation 2 `1`、最终 release `1`、FandUI failure/state mismatch/`GL_INVALID_*` 合计 `0`。至此 1.20.1、1.21.4、26.2 的共享 Demo Screen/HUD 真实运行验收完成。

### 2026-08-23 16:01：公共 API 完整性审计与后续路线

本轮按用户要求只做审计和路线整理，不修改 Java 实现。审计对象为当前工作区 `fandui-api`、真实 Core/Fabric consumer、共享 Demo，以及只读参考 `C:/Users/winme/Desktop/FandServer/fand-api` 的入口、builder、definition/registration 分离、文档和架构门禁模式。

#### 已有公共能力盘点

- `fandui-api/src/main/java` 当前共有 `167` 个 Java 文件，其中 `cn.fandmc.fandui.api.**` 为 `152` 个、`cn.fandmc.fandui.internal.**` 为 `15` 个；API package 中检测到 `135` 个 public type source；
- 已有根入口/runtime、Screen/HUD definition 与 live session、可变组件树、约束 measure/place SPI、style/theme、capture/target/bubble 事件、focus、animation、Canvas2D/path、图片/字体资源、Skija text service；
- 已有标准组件：`Box`、`Row`、`Column`、`ConstrainedBox`、`Spacer`、`Text`、`Image`、`Button`、`TextInput`、`ScrollContainer`、`CanvasComponent`；
- `ApiArchitectureTest` 的源码与 class constant-pool 门禁未发现 Minecraft、Fabric、Blaze3D、Skija、LWJGL 或 NanoVG 类型进入 `cn.fandmc.fandui.api.**`；Java 17 consumer gate 已存在；
- FandAPI 中值得继续沿用的不是 801 个领域类型本身，而是单根入口、窄服务、不可变 definition 与 live registration 分离、精确所有权的 `AutoCloseable` handle、防御性复制、JSpecify 以及实现包隔离。FandUI 当前总体方向与这些规律一致。

> 历史记录说明：下面的审计表保留最初发现时的证据和修正方向；其中已标记为后续日志已完成的条目不得再当作当前缺口。当前状态以本文件顶部“当前实现待办”和末尾最新日期条目为准。

#### API 冻结前必须修正的已确认问题（历史快照）

| 优先级 | 问题与源码证据 | 影响 | 修正方向 |
|---|---|---|---|
| P0 | `Style.margin` 只有 `Style` 内的字段/getter/builder；对非 build 输出源码搜索没有任何布局消费点 | 开发者调用成功但布局完全不变，是公开 API 的静默假能力 | 冻结前明确 margin 所有权并实现；若决定由 wrapper 组件承担，则删除该字段而不是保留空语义 |
| P0 | `SceneCompiler` 会执行 `Style.transform`；`LayoutSnapshot.hitTest/contains` 和 `LayoutEngine.freeze` 仍只使用未变换的轴对齐 `sceneBounds`，方向焦点也使用该 bounds | 平移、缩放或旋转后的控件在旧位置响应，视觉位置可能点不中；与第 7.4 节“最终 transform/clip 命中”契约冲突 | snapshot 保存组合 transform/inverse 和最终 clip；paint、hit-test、scene/local conversion、方向焦点共用同一几何事实 |
| P0 | `Spacer.expanded()` 在 `LinearLayout` 的顺序测量中不是 flex；真实 JShell fixture `Row[10px, expanded, 10px] @ 100px` 得到 `left=10, flex=90, right=0` | “扩展空白”吞掉后续兄弟；原设计承诺的 grow/shrink 尚未实现 | 增加显式 flex parent-data/wrapper，先测非 flex child，再按 factor 分配一次测量 flex child；不要继续把 expand boolean 当 flex |
| P0 | 第 7.4 节原定 MVP 的 `Stack`、Row/Column child grow/shrink、`ThemeScope` 均不存在 | 重叠 UI、合理自适应布局和局部主题只能手写自定义容器，API 丰富度与原设计不符 | 先实现这三个小而正交的基础能力，再做高级控件；`Stack` 同时补齐标准 z-index/position 语义 |
| P0 | `AbstractCoreSession.prepareFrame` 固定传入 `LayoutDirection.LEFT_TO_RIGHT`，Screen/HUD/Theme 没有布局方向入口 | 已公开 RTL enum，但组件布局永远无法切成 RTL；文字方向和组件方向彼此割裂 | 在 Screen/HUD definition 或局部 direction scope 提供稳定入口，并把 resolved direction 写入 snapshot |
| P0 | `TextInput` 只处理全选、方向/Home/End、删除与提交；runtime 没有 Clipboard service，因此 Ctrl/Super+C/X/V 不存在 | 作为标准文本框缺少最基本桌面编辑行为 | 增加平台隔离的 clipboard 字符串服务，并补 copy/cut/paste；随后补 placeholder、read-only、password、max length/filter 和明确 validation 回调 |
| P0 | 当前没有 pointer enter/leave、cursor shape 或 hit-test policy；hit-test 永远选择最上层可见节点 | tooltip/hover 业务逻辑收不到可靠离开事件；装饰层会挡住后方交互；未来 `Stack`/overlay 无法正确组合 | 增加 enter/leave 派生事件、`HitTestBehavior` 候选值和 cursor 契约；事件仍复用现有 route，不另建全局事件总线 |
| P0 | `Path` public class 实现 `cn.fandmc.fandui.internal.canvas.InternalPath`，且公开 `replay(PathVisitor)`；生成的 API Javadoc包含全部 internal package | internal 类型已经出现在 public class descriptor/method surface，发布文档也把实现桥暴露给使用者 | 冻结前把 renderer 读取通道变成真正的内部访问方式，Javadoc/source publication 排除 internal；增加“public/protected signature 不引用 internal”门禁 |
| P0 | 当前没有实际二进制 baseline 工具；`fandui-api` 的 135 个 public type source 中只有 13 个 source 含 Javadoc，而 FandAPI 当前快照为 801/662 | 一旦发布 0.1，现有偶然 descriptor 会被锁死，使用者也无法从文档判断线程、ownership 和 callback scope | 完成 API 减法后再生成 baseline；补齐 public type/method Javadoc、线程/失败语义、最小 consumer 示例和三版本 API class 字节一致性检查 |

#### 首发前还需明确的行为契约

- `Style` 当前不是所有组件统一消费：`Box`、`Button`、`TextInput`、`ScrollContainer` 会自行处理 background/border/padding，`Row`、`Column`、`Text`、`Image`、`CanvasComponent` 等不会统一绘制这些字段。必须选择“Style 字段全组件通用”或“组件声明自己支持的字段”之一，并用 Javadoc/测试锁定，不能继续靠调用者猜测；
- 1.20.1/1.21.4 的 Screen bridge 把 `PointerEvent.clickCount` 固定为 `1`，26.2 才从宿主传入 double-click。若保留跨版本 click count 契约，应在 Core 按时间、按钮和距离统一计算；
- `MinecraftScreenHost*#close` 当前把宿主 Screen 设为 `null`，不会返回打开 FandUI 前的 Screen。首发应至少自动恢复 parent；多页应用再以窄的 push/replace/back API 表达导航，公共层不暴露 Minecraft Screen；
- HUD 当前是 render-only layer，没有 mouse/key/focus 输入桥。首发文档必须明确；若要交互 HUD，应增加显式 input mode，默认 click-through，避免挂载一个 HUD 就抢走游戏输入；
- `registerImage/registerFont` 只登记 source 和 registration revision，本身不加载、不推进 generation；公共 `ResourceService` 也没有请求 reload 的入口。需明确“仅初始化阶段注册”或者实现安全的异步增量装载，不能让运行期注册长期停在 unresolved；
- `ImageRef` 只暴露 state/info，没有失败原因或 ready future；这可在后续以新增 default method/新类型增强，但至少先文档化当前 reload 与失败观察方式；
- focused component 运行中变为 hidden/disabled/non-focusable 时，当前 focus 不会立即迁移或清除；在增加表单控件前应统一修正焦点不变量。

#### 推荐新增 API：按价值分层

**首发基础层（先做）**

1. `Flex/Flexible` parent-data、`Stack/Positioned`、`ThemeScope`，以及 transform-aware geometry；
2. `ClipboardService`、pointer enter/leave、hit-test policy、cursor shape；
3. 完整单行 `TextInput` options：placeholder、read-only、password masking、length/filter/validation、copy/cut/paste；
4. Screen parent restoration 和明确的 HUD input policy；
5. API Javadoc、quick-start、线程/ownership 文档、binary baseline、internal surface 清理。

**丰富组件层（基础层稳定后）**

1. Overlay 基础：`OverlayService/OverlayEntry` 候选、anchor placement、modal barrier、outside-click/Escape dismiss、focus trap 与关闭后焦点恢复；Tooltip、Dialog、Dropdown/ContextMenu 都建立在它上面，不各自实现一套浮层；
2. 表单控件：Checkbox、Toggle/Switch、RadioGroup、Slider、Select、ProgressIndicator；共享 control state、theme token、键盘行为，不复制 Button 事件逻辑；
3. 布局与大量数据：Wrap、Grid、可见 scrollbar；得到真实性能样本后再实现 builder-driven virtual `ListView`，要求 stable key、item extent/cache policy 和 component reuse；
4. 文字：rich spans、link/selection、underline/strike、Minecraft 语言资源驱动但不暴露 Minecraft 类型的 localized text；多行编辑器在单行编辑语义稳定后再做；
5. Accessibility/Semantics：role、label/value/hint、enabled/checked/selected 状态、focus order，并在三个版本 bridge 映射到 Minecraft narration；
6. 动画易用层：数值/颜色/transform tween、sequence/parallel 和 transition helper，底层仍复用现有 session-scoped `AnimationManager`。

**YAGNI 暂缓**

- Compose/React 式 reconciliation、compiler plugin 或完整响应式 data binding；
- CSS selector/cascade 引擎和通用 Cassowary constraint solver；
- 任意 shader/raw triangle/raw GPU handle；
- WebView、APNG/GIF 动画图片、任意视频；高级 SVG filter/gradient/text/外部资源仍暂缓（基础 inline/resource SVG 已在 2026-08-24 实现）；
- 在没有大列表基准前实现复杂 recycler；
- 为尚未要求的 Vulkan 模式重新设计 renderer。

#### 高性能与发布工作顺序

1. 先为上述 P0 行为写失败测试并修正确性，尤其是 margin、transform hit-test、flex、focus 和 clipboard；已有 API 语义不正确时不做表面控件堆叠；
2. 用设置页、聊天/日志长列表、交互 HUD 三个 consumer fixture 驱动 API，要求常见调用不需要自定义 measure/paint；共享 Demo 扩展为这些真实用例，而不是再增加只展示绘图原语的卡片；
3. 建立 100/1000 component、hover churn、每 tick text update、scroll 和 blur 的 JFR/JMH 基线。当前每次 visual-state 变化会令整 session layout/paint dirty，`UiContainer.children()` 也会复制列表；只有 profiling 证明瓶颈后再做 resolved-style cache、subtree dirty layout/display-list reuse，避免提前引入 reconciliation；
4. 完成连续运行 GPU/CPU cache 压力：固定 viewport、反复 resize、GUI Scale 往返、resource reload、不同文本/图片 churn，记录 GL object、显存、native heap 和 Java heap，而不只观察任务管理器；
5. 依次验证 Sodium/Iris/Indium 与 Lithium/FerriteCore/Krypton，RenderDoc/GL debug，Windows/Linux/macOS 与 x64/arm64 native classifier、干净机启动和正常关闭；
6. 最后冻结 `0.1` baseline、生成 sources/Javadoc/POM、补 README、使用指南、兼容矩阵、变更日志和许可证/第三方 notices，再构建三个独立发布 JAR。

本轮只读验证记录：`rg` 全量 public signature/关键词扫描；直接读取 `Style`、`LinearLayout`、`LayoutSnapshot`、`SceneCompiler`、`CoreEventDispatcher`、三版 Screen bridge、resource service；JShell flex fixture 得到字面 `left=Size[width=10.0, height=4.0] flex=Size[width=90.0, height=10.0] right=Size[width=0.0, height=4.0]`。`./gradlew.bat :fandui-api:test :fandui-core:test --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 38s`；XML 汇总为 `22 suites / 82 tests / 0 failures / 0 errors / 0 skipped`。没有启动 Minecraft，也没有修改 Java 源码。

### 2026-08-23 16:35：首发 API 修复第一批，布局基础语义

- 用户要求将审计确认的首发阻断项全部修复；本批先以失败测试锁定 margin、flex、stack、局部主题和方向，再修改实现；首次测试按预期因缺少新类型和方向 getter 编译失败；
- `Style.margin` 现在由 `LayoutEngine` 统一作为外部盒模型消费：父布局看到含 margin 的 `Placeable`，组件 `LayoutNode.size/sceneBounds` 仍只表示自身 border box，绘制原点自动跳过 margin，baseline 对父布局包含 top margin；
- `LinearLayout` 改为先测固定子项，再统一分配 flex 子项；`Spacer.expanded()` 被兼容识别为 tight flex，不再让 `Row[10, expanded, 10]` 得到最后一项宽度 0；新增 `Flexible`、`FlexFit`，支持显式 grow/shrink/basis/fit；
- 新增 `Stack` 与 `Positioned`，提供叠放、边缘定位、显式宽高与 z-index；新增 `ThemeScope` 与 `DirectionScope`，`LayoutEngine` 在递归测量时解析局部 Theme 和 `LayoutDirection` 并写入 `LayoutNode`；
- `UiScreen`、`HudLayer` 新增默认 LTR 的 `layoutDirection` builder 入口，Core session 不再硬编码 LTR；
- 验证命令 `./gradlew.bat :fandui-api:test :fandui-core:test --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 19s`。未启动 Minecraft 客户端。

### 2026-08-23 17:10：首发 API 修复第二批，统一几何与文本编辑

- `Transform2D` 新增 identity/translate/scale/rotation、矩阵串接、点映射和安全求逆；`LayoutEngine.freeze` 现在为每个节点计算与实际 Canvas 调用顺序一致的组合 local-to-scene transform、可逆 scene-to-local transform 和四角变换后的 AABB；
- `LayoutSnapshot` 的 hit-test、scene/local conversion、祖先 bounds/rounded clip 全部读取该组合几何；奇异矩阵不可命中，方向焦点继续使用已变换的最终 AABB；`AbstractCoreSession` 删除了另一套手工逆变换；
- focus eligibility 现在检查组件及全部祖先的 visible/enabled；focused 节点或祖先变为 hidden/disabled/non-focusable 时，以新增 `FocusCause.INELIGIBLE` 同步清焦，不等待下一帧；
- 新增平台隔离的 `api.input.ClipboardService`，由三个 Fabric bridge 复用经真实 JAR `javap` 确认存在的 `Minecraft.keyboardHandler#getClipboard/setClipboard`；API/Core 不引用 Minecraft 类型；
- `TextInput` 新增 Ctrl/Super+C/X/V、placeholder 与独立 placeholder style、read-only、password masking、按 Unicode code point 计数的 maxLength、replacement filter 和完整 candidate validator；密码掩码保持 UTF-16 长度，因此已有 selection/caret geometry offset 仍一致；
- `PointerInputState` 统一按同按钮、500ms 和 4px 距离计算连续 click count，1.20.1、1.21.4、26.2 bridge 不再分别硬编码 1 或读取宿主 double-click；
- API/Core 测试命令字面 `BUILD SUCCESSFUL in 42s`；三个 Fabric `compileJava` 联合命令字面 `BUILD SUCCESSFUL in 26s`。未启动 Minecraft 客户端。

### 2026-08-23 17:42：首发 API 修复第三批，Pointer、Cursor 与 HUD 输入边界

- 新增 `HitTestBehavior.OPAQUE/PASS_THROUGH/IGNORE_SUBTREE`；命中候选现在可让装饰层穿透，也可整体排除不可交互子树，不再强制最上层 visible node 截获输入；
- Core 从实际 hover path 派生 `PointerAction.ENTER/LEAVE`，仍复用既有 capture/target/bubble 路由；pointer capture、MOVE route 和 enter/leave 各自保持明确语义；
- 新增平台隔离的 `CursorShape`/`CursorHost`。GLFW cursor 创建、切换和释放集中在 `fandui-render-opengl` 的 `GlfwCursorHost`，三个版本桥不复制 native cursor 所有权；
- HUD 新增 `HudInputMode`，默认 `PASS_THROUGH`；只有显式 `INTERACTIVE` 的 layer 才接受 `HudRegistration.dispatch(UiEvent)`，挂载 HUD 不会自动抢占游戏输入；
- Pointer 修改后的 Core 测试字面 `BUILD SUCCESSFUL in 14s`，Core 为 `65 tests / 0 failures`；renderer 与三版本 `compileJava` 字面 `BUILD SUCCESSFUL in 24s`。未启动 Minecraft 客户端。

### 2026-08-23 17:55：Screen parent 恢复与运行期资源契约

- 三个 `MinecraftScreenHost*` 在打开第一张 FandUI Screen 时保存既有 Minecraft Screen；FandUI -> FandUI replacement 保留最初 parent，Escape/API close 恢复该 parent，外部 host replacement 只按 `HOST` 关闭 session，不反向覆盖宿主选择；
- `CoreScreenSession.hostClosed(ESCAPE)` 现在通知 host；三个 `FandUiScreen*#onClose` 在 session 成功处理关闭后不再调用 `super.onClose()` 把已恢复的 parent 改成 `null`，异常路径仍保留宿主兜底；
- `ResourceService.reload()` 复用最近一次平台 `ResourceLookup`，因此运行期 `registerImage/registerFont` 后可由调用方显式触发完整事务 reload；成功原子推进 generation，candidate 失败仍保留旧 READY snapshot；
- `ImageRef.failure()` 公开当前 missing/failed snapshot 的实际 source/decode 原因，Core image snapshot 不再丢弃异常；
- `./gradlew.bat :fandui-api:test :fandui-core:test --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 19s`；renderer 与三版本 bridge 联合 `compileJava` 字面 `BUILD SUCCESSFUL in 17s`。未启动 Minecraft 客户端。

### 2026-08-23 18:16：公开 API 边界、Javadoc 与使用指南闭合

- `Path` 不再实现 public descriptor 中的 internal interface；几何回放正式定义为平台中立的公开 `api.canvas.PathVisitor`。`ScrollContainer` 的强制 viewport clip 同样迁移为窄的公开 `ContentClipProvider`，删除三个已被替代的 internal 类型；
- `ApiArchitectureTest` 现在通过 reflection 检查公开/受保护 superclass、interface、field、constructor、method、generic signature、throws、record component、annotation 与 permitted subclass，任何 `cn.fandmc.fandui.internal` 类型进入公开签名都会失败；
- 所有公开顶层 API 类型与 15 个 package 都已有 Javadoc；根契约明确 UI thread、callback scope、handle ownership、资源事务失败与文字 future 语义。`Style` 固定为 Core 统一消费 margin/transform/opacity/clip/blur，Box/control 显式消费 padding/background/border 的边界；
- 新增根 `README.md` 与 `docs/API-GUIDE.md`，记录真实版本/Java/渲染支持、模块关系、Screen/HUD/资源/文字/输入/resize 规则和可运行 quickstart；测试直接从 Markdown 提取 quickstart 并用 Java 17 编译；
- API Javadoc 与 sources JAR 只发布 `cn.fandmc.fandui.api.**`。JDK doclint 保留 HTML/link/reference 等检查，机械的 enum/record/accessor missing 检查由“所有公开顶层类型必须有类型文档”的语法树门禁替代；产物任务重新打开 Javadoc 和 sources JAR，确认 internal doc/source entry 均为 `0`；
- `./gradlew.bat :fandui-api:check --rerun-tasks --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 18s`；API 为 `13 suites / 33 tests / 0 failures`。未启动 Minecraft 客户端。

### 2026-08-23 18:35：0.1 API baseline、发布门禁与文档事实闭合

- `fandui-api` 新增确定性的 `publicApiJar`、需显式调用的 `updateApiBaseline` 和 japicmp `0.26.1` `checkApiCompatibility`。检查同时传入 old/new compile classpath，不使用 `--ignore-missing-classes`；严格命令字面 `Comparing source compatibility ...`、`No changes.`、退出码 `0`；
- `0.1.0` 预发布 baseline 位于 `fandui-api/api-baseline/0.1.0/fandui-api-0.1.0.jar`，为 `252070` bytes / SHA-256 `97F53ABC641AE0F801A0164B2EA048B1B289136EE7E12597A692B3FD870C8A1E`；当前 deterministic public API JAR 大小和哈希逐字节相同。HTML 报告位于 `fandui-api/build/reports/api-compatibility.html`；
- 根 `verifyEmbeddedApiConsistency` 会打开实际发布 JAR，定位唯一 `META-INF/jars/fandui-api-*.jar`，并逐 class SHA-256 对比规范 API JAR。首次运行暴露 Gradle 9.5.1/Groovy 不能对 `ZipFile$ZipEntryIterator.eachRemaining` 直接使用 closure；改为明确 `Enumeration` 循环后重跑成功，字面 `Verified 214 identical public API classes in all Fabric JARs`；报告位于 `build/reports/api-class-consistency.txt`；
- 首次 `buildAll` 又发现只有 API Javadoc 显式使用 UTF-8，其他模块在 Windows 默认 GBK 下解析 Demo 中日文失败。根 `subprojects` 现统一 Javadoc `encoding/charSet/docEncoding=UTF-8`，API 模块删除重复设置；纠正后 `./gradlew.bat buildAll --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 27s`、`59 actionable tasks: 25 executed, 34 up-to-date`；
- 全仓测试 XML 汇总为 `37 suites / 159 tests / 0 failures / 0 errors / 0 skipped`。API Javadoc 中不存在 `cn/fandmc/fandui/internal` 目录，API sources JAR 中 internal entry 为 `0`；
- 当前发布 API 产物：binary `272524` bytes / `1D6C8807707165E9ED5D9989BE759E39EC135443A3B69D6123903DCA2B424DE7`，sources `111715` / `2F6B860AB1C7894CD0907140476DF1A490A51CCC2AB939CB7B82EA4165BB6B83`，Javadoc `735433` / `2D6ED5ED6B0EA71D498769400FFF9C523B162670CD52BC1557951B65A583AADF`；
- 当前三个独立 Fabric JAR：1.20.1 为 `35381021` bytes / `C3283CFF5267613E69D01A2F8281DC4CD049730263E2A8DA3D0861F1D0DE41C7`；1.21.4 为 `35383102` / `5D40D86ABA75A72DAB5D98BA6DAABEEB801D166A8A8A3F8E1B50F73253C5C817`；26.2 为 `35312743` / `E73789DF1C1F875C93919C45DFF14AA48CC25CAD2FE1E634ABFB37EE1180265D`；
- 根 README 新增 API 兼容门禁命令、报告位置和 baseline 更新规则；本文件的顶部恢复点、当前渲染流程、模块依赖、NanoVG 章节、P1/P2 门禁和实施进度已与现有 NanoVGGL3 代码校正。旧自有 JNI/FUDL/FUBT 章节保留并明确标记为已废止，不再可能在上下文压缩后被误当成当前路线；
- 本轮文档与发布门禁闭合期间没有启动 Minecraft。按用户指定顺序，下一项才是独立的 1.20.1 右半屏纯 backdrop blur HUD 性能压测。

### 2026-08-23 18:46：1.20.1 右半屏纯 Blur HUD 压测实现闭合

- 新增独立内部 fixture `cn.fandmc.fandui.internal.demo.BlurStressHud`，只由 `-Dfandui.demo.blurStress=true` 启用，key 为 `fandui:demo/blur-stress`；原 `-Dfandui.demo.blur=true` 的右下角 `50x50` fixture 保持不变；
- 只有 1.20.1 bridge 挂载新 stress fixture，且 stress property 优先于旧 fixture，因此两个 property 同时存在时不会叠加两次 Blur 干扰性能结论；1.21.4/26.2 的运行行为未扩大；
- 组件树只有一个 `Row` 和两个等权 expanded `Spacer`。右侧 Spacer 只有 `Style.backdropBlur(18.0f)`，没有 background、border、corner、text、image 或其他 paint；它自动占当前逻辑 viewport 的右半宽和全部高度，resize/GUI Scale 后由既有 session viewport invalidation 重算，不要求开发者 mutation；
- `BlurStressHudTest` 证明 `320x180` 时 scene bounds 为 `160,0,160,180`，`640x360` 为 `320,0,320,360`，奇数逻辑宽 `321x101` 为 `160.5,0,160.5,101`；DisplayList 恰有一条 `BackdropBlur`，所有 foreground draw command 均为 `0`；
- 稳定 viewport 下 Core 复用 immutable LayoutSnapshot/DisplayList；renderer 复用两张 2x 降采样 RGBA target，Blur pass 只 scissor 到受影响区域，没有新增逐帧日志、`glFinish`、同步 readback 或纹理分配。DPR 2、radius 18 的静态 plan 为 downsample 4，预计报告 `1 operation / 5 draw calls`，另有一次硬件 blit；最终字面计数以客户端 target attach 日志为准；
- 窄回归 `:fandui-core:test :fandui-render-opengl:test :fandui-fabric-1.20.1:build --rerun-tasks` 字面 `BUILD SUCCESSFUL in 30s`；随后 `buildAll` 字面 `BUILD SUCCESSFUL in 28s`，严格 ABI 仍为 `No changes`，三版嵌入 `214` 个一致 API class；全仓 XML 汇总更新为 `38 suites / 161 tests / 0 failures / 0 errors / 0 skipped`；
- 新 1.20.1 JAR 为 `35382465` bytes / SHA-256 `DF5789518301C2AF877F00C458EF70C038BF40A19F8F8E136B283B6D99073E1A`；已重开 `fandui-core` JAR确认存在 `cn/fandmc/fandui/internal/demo/BlurStressHud.class`。本条记录时尚未启动客户端。

### 2026-08-23 18:49：1.20.1 右半屏 Blur 压测客户端已就绪

- 启动命令为 `JAVA_TOOL_OPTIONS=-Dfandui.demo.blurStress=true -Dfandui.opengl.assertState=true` 加 `:fandui-fabric-1.20.1:runClient --no-build-cache --console=plain --max-workers=2`；没有启用旧 `fandui.demo.blur`、完整 Demo Screen、target probe 或 batch probe；
- 1.20.1 游戏进程为 Java 17 PID `39404`，外层 Gradle wrapper PID `20168`；用户已有的其他 Minecraft/Java 进程未触碰；
- 启动日志字面确认 `FandUI mounted the right-half-screen backdrop-blur stress HUD`、runtime `AVAILABLE`、target/batch probe 均 `disabled`、LWJGL `3.3.2-snapshot`、`FandUI final GUI render hook observed for Minecraft 1.20.1`、resource generation `1`；
- 客户端当前停在主菜单。1.20.1 的 HUD callback 只在世界 HUD 实际绘制时令 stress frame 可见，因此尚无 target attach 与 draw-call 字面结果；窗口保留给用户进入存档后移动视角、观察远景/树叶并比较 FPS。登录 401、Realms token 和 vanilla 缺 sound/shader sampler 警告与 FandUI renderer 无关。

### 2026-08-23 19:21：Blur 第一批 GPU 性能优化与真实运行闭合

- 右半屏实测首先补齐旧记录：旧实现进入世界后在 `1920x1121 / FBO 4` 字面提交 `1 batch / 5 draw calls`；用户用大范围观察确认树叶、玻璃、楼梯、花草、草径边界乃至普通草地均可见场景闪烁，并说明此前 `50x50` 范围过小而误判。用户最终将剩余现象判定为 Minecraft 自身问题并要求停止该方向，故本批只优化性能，不再改变滤波语义；
- `NanoVgBackdropBlur` 为每个 downsample stage 计算“最终影响区 + Gaussian/tent 保守支撑区”，并用 Scissor 限制首级 blit 和后续 shader。对实际 `1920x1121`、DPR `2`、radius `18`、右半屏 bounds，两级预滤波覆盖由 `673440` 降至 `344296` pixels，确定减少 `48.88%`；奇数高度 `1121 -> 561 -> 281` 已由纯规划测试锁定；
- 零圆角、无活动 Scissor、轴对齐的常见 Blur 现在由 composite shader 直接计算精确亚像素矩形 coverage，不再生成/清空/采样一张全分辨率 NanoVG mask。blur-only target 因此少一次 draw、少一次 composite 纹理读取，并省去 RGBA8+D24S8 `8 bytes/pixel`：本次目标为 `17218560 bytes / 16.42 MiB`，4K 为 `63.28 MiB`；圆角、旋转或活动 Scissor 仍走原 NanoVG mask，语义不缩水，mask 改为首次确需时事务式延迟分配；
- 当前 global alpha 为 `0` 时整条 Blur GPU 工作直接跳过；复用 `Player.transformScratch`，不再为每条 Blur 每帧创建一个 `float[6]`。公共 API、DisplayList 与三版本 bridge descriptor 均未改变；
- renderer 窄测试字面 `BUILD SUCCESSFUL in 47s`；全量 `buildAll --rerun-tasks --no-build-cache --max-workers=2` 字面 `BUILD SUCCESSFUL in 2m 9s / 59 actionable tasks: 59 executed`，严格 API 比较仍为 `No changes`，三版仍为 `214` 个一致 API class；当前 XML 汇总为 `168 tests / 0 failures / 0 errors / 0 skipped`；
- 新 1.20.1 JAR 为 `35386548` bytes / SHA-256 `1F9293F42E4F9FAB05FB581E8C76640D5278794E2DDD47DA7B3333824B084F43`。旧 PID `39404`/wrapper `20168` 正常停止后，以相同两个 property 重启为游戏 PID `31400`/wrapper `39364`；进入同一世界后日志字面确认 `1920x1121 / FBO 4 / 1 batch / 4 draw calls`，resize 到 `854x480 / FBO 7` 后仍为 `4 draw calls`，未出现 FandUI error、`GL_INVALID_*` 或 state mismatch；
- draw-call 确定从 `5 -> 4`（`-20%`），结合 ROI、mask pass 与纹理读取减少，当前对 Blur 自身 GPU 时间的保守预估为 `30%-50%`；这不是整机 FPS 承诺，下一阶段以全 renderer/Core profiling 分别测量 CPU、GPU、分配和 cache 命中后再给整体收益。

### 2026-08-23 22:56：三版本 KeyAction.REPEAT 语义闭合

- 实际 1.20.1、1.21.4 与 26.2 Screen bridge 均确认：宿主 `keyPressed`/`KeyEvent` 不携带可直接读取的 GLFW action；此前三个 bridge 都固定发 `KeyAction.PRESS`，因此公共 `REPEAT` 不可达。
- 新增 `cn.fandmc.fandui.core.input.KeyInputState`。它按 Screen 生命周期维护 `KeyCode` pressed set：首次 press 返回 `PRESS`，同 key 未 release 的后续回调返回 `REPEAT`，release 返回 `RELEASE`，`removed()` 清空集合，三个版本接入完全相同。
- 三个 runtime capability 均改为 `UiCapabilities.of(..., true)`；26.2 保留 `imeComposition=true`，1.20.1/1.21.4 保留 `imeComposition=false`。公共 API descriptor 未增加字段或类型。
- `KeyInputStateTest` 覆盖 press/repeat/release、未知 release 幂等和 clear 重置。待完成的三版本实际长按客户端验收仍保留在 P1 清单；本次只运行 Core/API/三版编译，不启动客户端。

### 2026-08-24：TextController 与 KeyEvent 输入 API 缺口修复

- `TextController.replace(TextSelection, String)` 公开已有内部任意范围替换能力，保持 UTF-16 boundary、surrogate 校验、监听器批次和 caret 语义；搜索替换、自动补全和外部编辑器不再需要访问 internal state。
- `KeyEvent` 增加 `scanCode()` 与五参数构造器，旧四参数构造器保留并以 `-1` 表示未知；1.20.1、1.21.4 传入 Screen 的 `scanCode`，26.2 传入 `KeyEvent.scancode()`，三版一致。
- API 测试覆盖任意范围替换、caret 位置、旧构造器兼容、scan code 保留和非法 sentinel。新增方法均为兼容性允许的 additive API，不更新 `0.1.0` baseline。

### 2026-08-24：横向 API/正确性/性能审计批次启动

- 用户要求停止“一次只找一个”的串行排查。本批审计范围固定为公共 API、组件树/Core、resource/Skija、OpenGL renderer、1.20.1/1.21.4/26.2 三个 bridge；每次修改仍以证据和测试闭合，但发现项集中列出并按依赖顺序连续处理；
- 已完成第一组低风险高频路径修复：所有现有标准组件 setter 在写字段前执行 mutation-thread preflight；`UiContainer.children()` 改为结构变化时更新的 immutable snapshot；focus tab 排序从潜在 O(n²) 的 `indexOf` 比较改为稳定单键排序；event listener resolution、GLFW `KeyCode`/modifier、animation tick snapshot 均增加结构变化时失效的缓存；
- 窄验证命令 `./gradlew.bat :fandui-api:test :fandui-core:test --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 43s`；这些修改尚未进入全仓 `--rerun-tasks` 与 `buildAll` 结论；
- **已确认正确性缺口**：`Box`、`Button`、`ScrollContainer`、`Flexible`、`Positioned`、`ConstrainedBox`、`ThemeScope`、`DirectionScope` 继承公开 `remove/clear`，可被变为空容器；多个 `setChild` 先 remove 后 add，新 child 已有 owner 等失败会丢失旧 child。需要公共树 mutation 的 preflight + 原子 replace，并由单子组件锁定最小 child 数；
- **已确认资源缺口**：`CoreResourceService.registerFont/reload` 已发布字体 bytes，但三个 bridge 只把 `resources::generation` 交给 `SkijaTextService`；`SkijaTextEngine` 仅注册 bundled CJK/Emoji，逻辑自定义 family alias 没有真实 typeface。需要 generation snapshot、Skija provider environment、旧 layout 对旧 generation 的可重建语义和有界 native environment cache；
- **已确认诊断缺口**：稳定 `UiCapabilities` 只有 IME/repeat；缺少动态 backend、blur/stencil、目标格式/尺寸、最大纹理和 unavailable reason。26.2 的实际 backend 在最终 GUI hook 才能确认，因此诊断必须是动态 immutable snapshot，不能在 bootstrap 固化；
- **已确认高层 API 缺口**：标准组件仍没有 Checkbox/Toggle、Slider、ProgressIndicator、overlay/dialog/tooltip、Grid/Wrap/virtual list、semantics/accessibility 和 rich spans。首批只实现 Checkbox、Slider、ProgressIndicator，并抽共享 control state/listener，避免复制 Button 的状态机；
- **待 profiling 风险**：`GlStateSnapshot.recapture()` 每次提交存在大量同步 `glGet*`；Core event path/context、NanoVG clip/layer 仍有短命对象；text/image/gradient/blur cache 在 resize、resource churn 和长时间运行下尚缺 JFR/native/GPU 计数。没有测量证据前不删除宿主状态恢复，也不声称整机 FPS 收益；
- 三版本规则继续同时适用：公共 API/核心实现完全共享；三个 Screen bridge 的 key repeat 已统一，后续 click count、resize/GUI Scale、reload 和控件输入测试必须覆盖三版编译与一致性门禁，不能以单版实现代替。

### 2026-08-24：组件树、字体环境与动态 Renderer Diagnostics 闭合

- 单子 wrapper 现在以 `minimumChildren=maximumChildren=1` 保持结构不变量，统一通过 `UiContainer.replace`/`setChild` 原子替换。失败的 parent/key/attach 校验会恢复旧 child、parent 和 immutable `children()` snapshot；普通 `Row/Column/Stack` 仍允许空状态。`clear()` 对单子 wrapper 的拒绝是刻意契约，不要求开发者手工 remove+add；
- `structureChanging` 只处理 UI 生命周期回调对同一容器的重入，不承担并发同步。挂载树 mutation 已由 `ComponentBinding.assertUiThread()` 强制 UI 线程，新增真实跨线程测试确认第二线程 `add` 抛 `IllegalStateException` 且结构不变；未来并行渲染继续消费 immutable `UiSceneFrame/DisplayList`，不把 `ArrayList` 配合 `AtomicBoolean` 伪装成线程安全树；
- `FontResourceSnapshot` 在构造与导出两端都深复制字体 bytes。`SkijaTextLayout` 保存 snapshot 而非 native handle；每个 Paragraph/raster 操作持有 `FontEnvironment.Lease`，LRU 上限为 `4`，淘汰只针对没有活跃 lease 的环境，忙碌环境延迟关闭；旧 layout 后续按 snapshot fingerprint 重建。新增回归同时覆盖旧 layout 的 raster、`hitTest`、`geometry`，以及无效字体 reload 保持旧 generation/layout 可用；
- 公共 API 新增 `UiRendererBackend`、`UiColorFormat`、`UiDiagnostics` 和兼容 default `UiRuntime.diagnostics()`；Core 以 `AtomicReference` 发布任意线程可读 snapshot。三个 bridge 只在实际 FandUI pass 成功后上报 target ready、framebuffer 尺寸、`RGBA8_UNORM`、Stencil、Backdrop Blur 和缓存后的 `GL_MAX_TEXTURE_SIZE`；resize/target loss/failure/shutdown 清除 target-dependent 字段；
- 26.2 继续在最终 GUI hook 读取实际 `DeviceInfo.backendName()`。OpenGL 走共享 renderer；Vulkan/未知 backend 发布对应诊断和原因后进入 `RENDERER_UNAVAILABLE`，不创建 fallback renderer。1.20.1/1.21.4 在 bootstrap 已知 OpenGL，但在 target 实际验证前保持 detached diagnostics；
- 窄验证：`./gradlew.bat :fandui-api:test :fandui-core:test :fandui-text-skija:test --rerun-tasks --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 21s`、`14 actionable tasks: 14 executed`；`./gradlew.bat :fandui-render-opengl:test :fandui-fabric-1.20.1:compileJava :fandui-fabric-1.21.4:compileJava :fandui-fabric-26.2:compileJava --rerun-tasks --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 20s`、`15 actionable tasks: 15 executed`。全仓 `test/buildAll` 尚待本批最后执行。

### 2026-08-24：字体 lease 与组件结构并发边界补强

- `FontEnvironment` 新增可重入安全的 lease 计数和延迟关闭：淘汰扫描会跳过仍被 Paragraph/raster 操作使用的环境，lease 归还后再执行 native `FontCollection`、provider 和 custom typeface 关闭；`TextLayout` 仍只保存 immutable `FontResourceSnapshot`，所以布局对象本身不会悬挂 native 引用，也无需依赖不可控的 finalizer。`withEnvironment` 在 layout、raster、hit-test、geometry、font validation 五条路径统一套用 lease；
- `UiContainer` 的结构 mutation 现在经过全局串行锁，`childrenSnapshot` 和 `UiComponent.parent` 以 volatile 发布；同容器生命周期回调重入标记改为 `ThreadLocal`，因此未来并发渲染只能读取 immutable snapshot，不能并发破坏可变树。已挂载树仍由 `ComponentBinding.assertUiThread()` 拒绝跨线程 mutation；
- required single-child wrapper 的 `clear()` 继续拒绝，以免动态 UI 暴露非法空中间树；异常信息明确建议 `setChild(...)`，该操作通过 `replace` 原子完成。普通可空容器保留 `clear()`。API Guide 已同步写明两种语义；
- 回归新增未挂载容器双线程并发 add，验证 200 个 child 全部保留；字体旧 generation 回归继续覆盖环境超过四代后的旧 layout raster/editor geometry，并用反射门禁确认 `SkijaTextLayout` 不持有 Skija native 字段。窄命令 `./gradlew.bat :fandui-api:test :fandui-text-skija:test --rerun-tasks --console=plain --max-workers=2` 已通过，完整 `test/check/buildAll` 待本批最后执行。

### 2026-08-24：字体缓存竞态与 required-child 正向契约补强

- 字体环境缓存新增独立 `fontEnvironmentLock`：环境 fingerprint 查找、退休环境剔除、lease 获取和 LRU 扫描在同一临界区完成；因此未来即使调用入口并行化，也不会在“查找到环境”与“获取 lease”之间拿到已退休的 `FontCollection`。lease 释放和 native close 仍保持延迟关闭，`SkijaTextLayout` 继续只持有不可变字体 bytes snapshot；trim 失败时会回收刚取得的 lease，并保留原始异常及关闭异常的 suppressed 链。
- `FontEnvironment.Lease.close()` 使用 `AtomicBoolean`，跨线程重复关闭也只释放一次；`cacheStats()` 和 engine shutdown 对环境 map 使用同一锁。Skija engine 的 native Paragraph 操作仍由专属 worker thread 执行，锁只保护环境所有权，不把 Skija 本身宣称为通用多线程 API。
- `UiContainerTest` 新增 required single-child 的成功 `setChild()` 路径回归；`clear()` 继续明确拒绝，避免动态树短暂暴露非法空 child，业务动态替换使用原子 `replace(0, child)`。
- `./gradlew.bat :fandui-api:test :fandui-text-skija:test --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`BUILD SUCCESSFUL`；`40 suites / 188 tests / 0 failures / 0 errors / 0 skipped`。
- `./gradlew.bat check --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`BUILD SUCCESSFUL`，`Verified 224 identical public API classes in all Fabric JARs`，japicmp source/binary incompatibility `0`，未更新 baseline。
- `./gradlew.bat buildAll --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`BUILD SUCCESSFUL`，`59 actionable tasks`。未启动 Minecraft，未执行 Git commit/branch/push。

### 2026-08-24：本批验证与文档同步

- `./gradlew.bat test --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`27 actionable tasks`，`BUILD SUCCESSFUL`；全仓测试 XML 汇总为 `40 suites / 188 tests / 0 failures / 0 errors / 0 skipped`（Fabric 三版无 test source）；
- `./gradlew.bat check --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`41 actionable tasks`，`BUILD SUCCESSFUL`；API Javadoc、published API documentation、embedded API consistency 均通过，三版 JAR 中逐 class 一致公共 API 数量为 `224`。japicmp 报告本轮 additive API，source/binary incompatibility 错误数为 `0`，未执行 baseline 更新；
- `./gradlew.bat buildAll --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`59 actionable tasks`，`BUILD SUCCESSFUL`。本轮没有启动 Minecraft 客户端，也没有执行 Git commit/branch/push；
- API Guide 已补充首批控件和三项边界：字体 lease/快照、未挂载树串行结构 mutation、required single-child `clear()` 的 `setChild` 原子替换契约。

### 2026-08-24：SVG 公共 API 与资源管线实现

- 公共 API 新增 `ResourceFormat` 与兼容的 `ResourceSource.format()` default 方法；`ResourceSource.svg(byte[]/String)`、`ResourceSource.png(byte[])` 使用防御性复制并发布显式格式提示，原有 lambda 与 `bytes(...)` 保持 `AUTO` 行为；三版 Fabric bridge 的 resource-pack lambda 无需改动，AUTO 会按 PNG signature 或 UTF-8 `<svg` 根元素选择解码器。
- `IconDefinition.fromSvg`/`SvgIcon` 已覆盖有界 DOM 解析：禁止 DOCTYPE、外部实体、外部 DTD/schema；支持 `path`、基本形状、变换、viewBox 非零原点、继承 opacity、solid/rgb/rgba 颜色、stroke cap/join 和 `M/L/H/V/C/S/Q/T/A/Z`。`Icon` 直接回放 immutable Canvas path；`Icons` 提供常用检查、关闭、箭头、菜单、搜索、信息、警告、播放/暂停图标。
- Core 新增 `ImageDecoder`、`SvgImageDecoder` 和共享 `ImageDecodeSupport`。SVG 只在 dedicated reload worker 上使用 Java2D/Path2D 栅格化；root 的正像素 `width`/`height`（可带 `px`）优先作为 intrinsic size，缺失或相对单位时按 viewBox 比例补齐，二者都不可用时使用 viewBox 向上取整，限制为编码 2 MiB、最大边长 4096、解码 64 MiB；输出与 PNG 相同的 premultiplied RGBA8、SHA-256 cache key、texture key 和 OpenGL texture LRU，不在 render thread 创建 Java2D 对象或重复栅格化。
- 明确不支持的 SVG 资源特性：外部资源、`image`/`use`/`text`、filter、渐变引用、动画、脚本和外部 CSS。需要无损缩放的图标使用 inline `SvgIcon`；需要渐变/滤镜的资源转为 PNG 或使用 Canvas API。未知/损坏 SVG 会使候选 generation 回滚并保留旧 READY snapshot。
- 新增回归：`ResourceSourceTest` 检查格式提示与防御性复制；`CoreResourceServiceTest` 检查显式 SVG、AUTO 探测、viewBox 原点归一化、继承 opacity、premultiplied 像素与 texture metadata；`ControlAndIconComponentTest` 检查 SVG path/颜色/安全边界。命令 `./gradlew.bat :fandui-api:test :fandui-core:test --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`BUILD SUCCESSFUL`。
- 当前 SVG 待验证项：真实 Minecraft resource-pack 中的多资产 reload、不同 GUI Scale/resize 后纹理重建、Windows/Linux/macOS headless/runtime 的 Java2D 可用性、RenderDoc 中 SVG 纹理上传的单次生命周期；这些不通过单元测试虚构为已完成。

### 2026-08-24：SVG 最终构建门禁

- `./gradlew.bat test --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`BUILD SUCCESSFUL in 26s`，全仓 XML 汇总 `41 suites / 199 tests / 0 failures / 0 errors / 0 skipped`；Fabric 三版没有 test source。
- `./gradlew.bat check --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`BUILD SUCCESSFUL in 55s`，API Javadoc、published documentation、japicmp source/binary compatibility 均通过；`verifyEmbeddedApiConsistency` 字面 `Verified 246 identical public API classes in all Fabric JARs`。
- `./gradlew.bat buildAll --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`BUILD SUCCESSFUL in 1m 6s`，`59 actionable tasks`。最终 Fabric JAR：1.20.1 `35504392` bytes、1.21.4 `35506430` bytes、26.2 `35436283` bytes；未启动 Minecraft，也未执行 Git commit/branch/push。

### 2026-08-24：SVG 解析边界加固

- `SvgPathParser.numbers(...)` 现在逐段验证 token 之间的分隔符，不再接受数字之间的任意非法字符；图层预算改为允许恰好 `4096` 个 drawable，超过预算才失败。
- SVG user-unit 长度支持无单位和 `px`，相对单位继续明确拒绝；transform 参数数量严格校验，避免宽松解析产生不同平台的几何结果。
- 新增 API 回归覆盖 `px` 尺寸、非法数字分隔符和多余 transform 参数；此前的安全实体、viewBox、opacity、premultiplied 像素和事务式 reload 回归保持通过。

### 2026-08-24：SVG 加固后的全量门禁

- `./gradlew.bat check --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`BUILD SUCCESSFUL in 1m`，Javadoc、published documentation、japicmp 和三版嵌入 API 一致性均通过；报告字面为 `Verified 246 identical public API classes in all Fabric JARs`。
- `./gradlew.bat buildAll --rerun-tasks --no-build-cache --console=plain --max-workers=2`：`BUILD SUCCESSFUL in 1m 18s`，`59 actionable tasks`；全仓 XML 汇总为 `41 suites / 200 tests / 0 failures / 0 errors / 0 skipped`。
- 本次三个发布 JAR：1.20.1 `35504841` bytes / `BB461923092748CE3EB6D2A09661216ED38F270D03D39B4964AB71DC846CE960`；1.21.4 `35506879` bytes / `D5A7A12D1B837EE01FDBBCC7DDFE4093D7D1D2F4B6689E3FE24138A8567ADE11`；26.2 `35436732` bytes / `BD5C7E0ED6886A6EFF43874D858B66C432552737F71648E4EB8678F139B55249`。
- 未启动 Minecraft 客户端，未执行 Git commit、branch、push 或清理操作；真实 resource-pack 多资产、跨平台 Java2D 和 RenderDoc 仍保持“待验证”。

### 2026-08-24：三版本共享综合测试 Screen

- 新增 `cn.fandmc.fandui.internal.demo.FandUiTestScreen`，由 `-Dfandui.test.ui=true` 独立启用；首次 client tick 自动打开，关闭后以 `F9` 上升沿重开。默认关闭时不注册测试 SVG 资源、tick listener 或 Screen，不改变库 Mod 的常规运行行为；
- 三版本复用完全相同的 Core 组件树，Fabric 模块只保留宿主输入差异：1.20.1/1.21.4 调用已核验的 `InputConstants.isKeyDown(long,int)` 与 `Window.getWindow()`，26.2 调用 `InputConstants.isKeyDown(Window,int)`；
- fixture 覆盖中英文、日文、韩文、Emoji fallback，Button/TextInput、Checkbox/ToggleSwitch、Slider/ProgressIndicator、Dropdown、预设 Icon、inline SVG、reload worker SVG 图片、圆角/描边/线性渐变、Backdrop Blur、三层路径裁剪、滚动及紧凑 viewport；
- 新增 `FandUiTestScreenTest`，验证 SVG generation 发布、`960x720` 与 `360x240` 响应式边界、DisplayList blur/image/path/gradient/三层 clip，以及按钮、复选、开关、滑块、下拉、文字输入和滚动的 live state；定向测试字面 `BUILD SUCCESSFUL in 12s`；
- 三版本联合 `compileJava --rerun-tasks --no-build-cache --max-workers=2` 字面 `BUILD SUCCESSFUL in 19s`、`8 actionable tasks: 8 executed`；
- `./gradlew.bat check --rerun-tasks --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 1m 20s`、`41 actionable tasks: 41 executed`；Javadoc、published API documentation、japicmp source/binary compatibility 和 `Verified 246 identical public API classes in all Fabric JARs` 均通过；
- `./gradlew.bat buildAll --rerun-tasks --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 1m 7s`、`59 actionable tasks: 59 executed`；全仓 XML 为 `41 suites / 199 tests / 0 failures / 0 errors / 0 skipped`；
- 本次发布 JAR：1.20.1 `35517626` bytes / `4DCB01D217E7A3CFB65823A174D06B1F44DD8A7BB62E1C395B008F2CE3404A4F`；1.21.4 `35519652` bytes / `A78D43355B512B16F21F05ECA75A38C9C57652680BA07B49159B086B1A554C63`；26.2 `35449504` bytes / `85005A7946352E2DD99BD05C962DEB58F30DF76589D4338B2A86BECABC667CBE`。真实客户端验收随后单独记录。
- 以 `JAVA_TOOL_OPTIONS=-Dfandui.test.ui=true -Dfandui.opengl.assertState=true` 启动 1.21.4；stderr 字面确认 client JVM 拿到两个 property。首次后台命令因子 PowerShell 引号解析没有传入 property，已只结束当次创建的 launcher tree 后重试，没有保留第二个客户端；
- 重试 launcher PID 为 `10708`，Minecraft client PID 为 `26380`。日志确认测试入口安装、首次自动打开、最终 GUI hook、SVG/字体资源 generation `1`；初始正式 pass 为 `854x480 / 39 batches / 53 draw calls / FBO 3`；
- 用户按 F9 后日志于 `16:31:02` 确认第二次 `FandUI opened the comprehensive test Screen`，当前目标重新挂接为 `1920x1121 / 58 batches / 72 draw calls / FBO 11`。针对 stdout/stderr 的 `FandUI failed`、`GL_INVALID`、state mismatch、Exception/ERROR 扫描为 `0`；客户端保持运行供交互、resize、GUI Scale 与 reload 检查。

### 2026-08-24：综合测试 Screen 整页滚动与控件过渡

- 用户实机指出综合页面不能滚动、`ToggleSwitch` 没有视觉过渡且白色 thumb 贴近轨道边缘、`Dropdown` 展开/收起没有视觉过渡。修复前客户端 PID `26380` 与 launcher PID `10708` 已通过窗口正常关闭并确认退出，没有强制终止；
- 新增内部 `ScalarTransition`，统一复用 `ComponentContext.session().animations()`，不增加每控件 timer 或 Fabric/Minecraft 类型。目标变化会取消旧 handle 并从当前标量值继续，因此快速反向不会跳回端点；detach 会取消 session 动画；
- `ToggleSwitch` 使用 140 ms `EASE_OUT` thumb 过渡，动画帧仅触发 paint invalidation；新增主题 token `THUMB_INSET`，默认 `3.0` 逻辑像素，使默认 38x22 track 中的 16px thumb 两端都保持 3px 间距；
- `Dropdown` 使用 160 ms `EASE_OUT` 高度揭示/收起，箭头随同一标量平滑翻转；实现 `ContentClipProvider` 并以当前揭示高度裁剪 option，过渡帧只触发布局失效；
- 综合测试面板的全部 content 外包一层垂直 `ScrollContainer`，底部日志区继续作为嵌套滚动 fixture。滚动范围仍按 `childExtent - viewportExtent` 自动计算，内容完整可见时不制造空偏移；测试改用 `960x520` 逻辑视口验证真实 overflow、滚轮消费、外层偏移和底部嵌套容器偏移；
- 定向命令 `./gradlew.bat :fandui-api:test :fandui-core:test --rerun-tasks --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 23s`，API/Core 共 `82 tests / 0 failures`；
- 完整 `check` 字面 `BUILD SUCCESSFUL in 1m 45s`、`41 actionable tasks: 41 executed`，Javadoc、published API documentation、japicmp source/binary compatibility 均通过，且 `Verified 246 identical public API classes in all Fabric JARs`；
- 完整 `buildAll` 字面 `BUILD SUCCESSFUL in 2m 56s`、`59 actionable tasks: 59 executed`；XML 汇总为 `41 suites / 199 tests / 0 failures / 0 errors / 0 skipped`；
- 发布 JAR：1.20.1 `35521645` bytes / SHA-256 `B1CBA821D69750470DFEBB33B45C1BAC1693A71D5D297C2BC9EE71BF3B7C1518`；1.21.4 `35523671` / `4A53D384EC1A848FAEC75880BEBEAFA21937EFC431608C825CB282BA4D839566`；26.2 `35453523` / `3A31C6BC8B92245DB3A2051EABF67B8C44C24FE0C68D8D87B67E5621876FC271`；未执行 API baseline 更新或 Git 操作。
- 新 1.21.4 客户端以 `-Dfandui.test.ui=true -Dfandui.opengl.assertState=true` 启动，Minecraft PID `16260`；测试 Screen 打开 `2` 次，资源 generation `1`。renderer 初次挂接为 `854x480 / 40 batches / 54 draw calls / FBO 3`，窗口变化后自动重建为 `1920x1121 / 41 batches / 55 draw calls / FBO 11`；`FandUI failed`、state mismatch、`GL_INVALID`、`OpenGlRenderException` 均为 `0`。宿主 OpenAL 设备打开失败仍单独存在，不属于 FandUI renderer；客户端保持运行供滚动与动画目视验收。

### 2026-08-24 19:56：OpenGL 状态交接 stall 根因与三版本实机闭合

- 基线录制为 `artifacts/fandui-1.21.4-dropdown-slider-release.jfr`，时长 `90s`，包含 `5160` 个 `cn.fandmc.fandui.OpenGlFrame`。通过 `jfr print --json` 和 PowerShell `ConvertFrom-Json` 解析得到最大帧 `269.577700ms`，共有 `5` 帧超过 `100ms`，其余四个峰值为 `247.04/209.60/208.60/184.84ms`；同一录制的 GC 最大停顿只有 `7.043100ms`。`jdk.NativeMethodSample` 的真实调用栈反复落在 `GL11C.glGetIntegerv -> GlStateSnapshot.recapture -> OpenGlUiPipeline.render`，因此“类似 GC”的偶发卡顿主因是每帧同步 OpenGL 状态查询，不是 Java GC；
- 打开完整状态断言后，旧恢复路径曾字面报告以下进入值到恢复值差异：program `0 -> 3`、VAO `7 -> 0`、`ARRAY_BUFFER 9 -> 0`、texture unit 1 绑定 `5 -> 0`、clear color `[0.937,0.196,0.239,1] -> [0,0,0,0]`、depth test `false -> true`、stencil mask `-1 -> 255`。这证明 Minecraft 的 Java 侧缓存与 hook 处真实驱动状态并非可由一个固定“猜测恢复值”代表；
- `RenderHost` 现提供窄的 `supportsStateHandoff/prepareStateForFandUi/restoreStateAfterFandUi` 契约。三个正式 Minecraft host 在 Pass 前把驱动和宿主缓存共同规范到已记录状态，Pass 后无查询恢复同一状态；`OpenGlPassScope` 正常发布路径不再执行 `GlStateSnapshot.recapture()`，只有显式 `-Dfandui.opengl.assertState=true` 或不支持交接的测试 host 才保留完整查询与 expected/actual 诊断。准备阶段抛错不会遗留 active singleton 状态；
- 共享 `OpenGlStateHandoff` 统一恢复主 FBO/viewport/scissor、program/VAO/buffer、两个 texture unit/sampler、blend/depth/stencil/cull/sRGB/dither、pixel-store 等 FandUI 会触及的状态。depth 固定关闭，stencil write mask 使用完整 `~0`；不再覆盖宿主 clear color/clear stencil。`NanoVgFramebuffers` 改用 `glClearBufferfv/glClearBufferiv` 清理自有附件，避免借全局 clear 值；
- 1.20.1/1.21.4 host 使用各自真实 Blaze3D `GlStateManager` 和 `BufferUploader.invalidate()` 同步 Java 缓存，保留 Minecraft shader texture 0；26.2 在相同 GL 状态同步外，通过 required Mixin accessor 取得 `CommandEncoder.backend`，并失效 `GlCommandEncoder.lastPipeline/lastProgram/lastVertexArray`。26.2 实机加载 required Mixin 成功，没有 accessor/mixin 应用错误；本机原版仍因 GTX 760 缺少 `VK_KHR_dynamic_rendering` 和 `dynamicRendering` 而选择 OpenGL，FandUI 没有切换后端；
- 三版均以 `-Dfandui.test.ui=true -Dfandui.opengl.assertState=true` 启动最新类，并各自完成首次自动打开、Win32 真实 key-down/up 的 `Esc -> F9` 重开及窗口 resize。1.20.1 为 `854x480 / FBO 2 -> 1582x903 / FBO 11`；1.21.4 为 `854x480 / FBO 3`、重开 `855x481 / FBO 11`、resize `1582x903 / FBO 6`；26.2 为 `854x480 / FBO 4 -> 1582x903 / FBO 29`。三个日志的测试 Screen 打开次数均为 `2`，state mismatch、`GL_INVALID_*`、renderer failed/disabled 和 FandUI OpenGL error 合计均为 `0`；
- 可复查运行日志：1.20.1 为 `10636` bytes / SHA-256 `28D87FAE1B2BB927A4272B003EFDAED5E8C9ABC16F18A5AD8509C8ECE1D45315`；1.21.4 为 `7963` / `ADBDCA9AF9D6F7586C6D123DDA5EEB58B085E55BA5570360C72585183F7A561D`；26.2 为 `11841` / `F5D7860895F0DBF20B966B599A22A0BEDCE027CB3A2DC15DE1F05BEA0370978A`；
- `./gradlew.bat check buildAll --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 52s`，`59 actionable tasks: 23 executed, 36 up-to-date`；测试 XML 为 `42 suites / 203 tests / 0 failures / 0 errors / 0 skipped`，三版发布 JAR 内 `246` 个公共 API class 逐字节一致。Javadoc 仍有既有 missing-comment 警告，但没有 doclint、测试、ABI 或打包失败；
- 当前发布 JAR：1.20.1 `35533190` bytes / SHA-256 `5DAB70677111F8E4F499EE98B289E00BD9B53C6B38DDCD5153A2881A871CF09C`；1.21.4 `35535215` / `2849736200E0947543EB2A0CDBEC8D793E194A76295877C6FDAF0B73255F1990`；26.2 `35466766` / `76FAC96A80DFB0926F91C9871704AE0E59B9B879AE5CE596F71F9BDF5887C2BC`。未更新 API baseline，未执行 Git commit、branch、push 或 reset；
- 这轮已经证明正常路径从“每帧完整 `glGet*`”变为“固定写入 + 缓存交接”，并消除了已定位 stall 的触发代码；开启状态断言本身仍会执行同步查询，只用于诊断，不作为最终性能路径。关闭断言后的同口径 JFR 已完成并记录在下一节；RenderDoc、长期 native/GPU 增长和优化 Mod 矩阵仍保持待验证。

### 2026-08-24 20:20：状态交接无查询路径 JFR 与当前 UI 实测

- 优化后录制为 `artifacts/fandui-1.21.4-state-handoff-zero-threshold-20260824-2011.jfr`，使用关闭 `fandui.opengl.assertState` 的发布路径和零时长阈值 JFC，时长 `30s`，SHA-256 为 `FAB059096FED4F08A345FA686D665247E8A2062F1832F81875DD985D7974BD19`；`jfr summary` 字面包含 `1739` 个 `cn.fandmc.fandui.OpenGlFrame` 和 `1389` 个 `jdk.NativeMethodSample`；
- 使用 `jfr print --json`、PowerShell `ConvertFrom-Json` 和明确的 `PT...S -> ms` 换算重新计算得到：P50 `0.358647ms`、P95 `0.699874ms`、P99 `0.871980ms`、最大 `1.689598ms`，超过 `16.67ms` 和 `100ms` 的 FandUI frame 均为 `0`；唯一一次 GC pause 为 `6.020337ms`；逐个 native sample 检查 `GlStateSnapshot` 调用栈命中数为 `0`；
- 同一解析方法重算旧基线 `artifacts/fandui-1.21.4-dropdown-slider-release.jfr`：`5160` 帧，P50 `2.292775ms`、P95 `3.746931ms`、P99 `4.587881ms`、最大 `269.577733ms`，超过 `16.67ms` 为 `6` 帧、超过 `100ms` 为 `5` 帧，GC 最大 `7.043160ms`，`4059` 个 native sample 中 `108` 个命中 `GlStateSnapshot` 调用栈；
- 由未取整原值计算，P50/P95/P99/最大值分别降低 `84.36%/81.32%/80.99%/99.37%`。两次录制时长不同，因此这里只比较 frame duration 分布和同步查询调用栈，不以总帧数或总 sample 数声称吞吐变化；
- 当前 1.21.4 客户端 PID `37828` 仅以 `-Dfandui.test.ui=true` 运行，未开启状态断言。日志于 `20:03:30` 记录 F9 显式打开综合测试 Screen，用户随后确认当前 UI 可见；因此“当前 UI 消失”不是现存事实。首次自动打开日志早于资源 generation `1` 仍是一个可观察时序，但在没有再次复现用户可见故障前不据此修改自动打开逻辑。

### 2026-08-24 22:36：Slider 按住连续拖动路径闭合

- 本节唯一验收对象是 Slider 主按钮按住后从 `0%` 连续拖到 `100%` 及快速往返；轨道单击、单点跳值和点击后过渡均不能代替该口径。视觉平滑必须保留，逻辑 `value()`/`onValueChange` 仍同步发布最新输入；
- 基线 `artifacts/fandui-1.21.4-slider-continuous-before-clock-fix-25s.jfr` 为 `516654` bytes，SHA-256 `49E985A3DB7E6F0EA24A4174773C0D73E0CD6AF66F290D2445B30A7F712D737E`。真实窗口 RenderTarget 为 `1920x1121`，Slider 绝对物理坐标 `x=622..1298, y=491`；按住后执行 `16` 段往返，每段 `250ms / 676` 个连续位置。拖动区间有 `245` 个 CoreFrame/changed OpenGL frame、`41` 次动画启动，说明旧视觉追赶器约每 `96ms` 结束并重建一段动画；
- `RetargetableScalarTransition` 现在只创建一个 infinite session animation 作为重绘驱动。视觉值在 `PaintScope.frameTimeNanos()` 上按响应半衰期连续采样，单帧时间步上限 `33.333334ms`，因此 hitch 后不会把视觉 thumb 直接跳到最新目标；连续 `setTarget` 只重定向，不排队、不重启动画。本帧刚收到新目标时禁止收敛关闭，避免慢速连续拖动在目标附近反复创建 handle；停止更新并收敛后仍主动关闭 driver；
- 三版 Screen bridge 全部使用同一 `PointerMoveCoalescer`：`mouseMoved/mouseDragged` 只覆盖最新坐标，每个 UI render frame 最多 drain 一个 MOVE；button/scroll 前先 drain 以保持事件顺序。时间戳在 drain 时读取，原始高频 MOVE 不再逐个调用 `MonotonicClock` 的原子 CAS。1.20.1、1.21.4、26.2 已联合 `compileJava`，没有单版特例；
- `InteractiveComponentsTest` 以 `12` 个连续 held-drag frame 从 `0%` 推到 `100%`：逐帧断言逻辑值立即更新、视觉位置单调推进但不 snap，释放后最终精确收敛。`RetargetableScalarTransitionTest` 额外使用 `10ms` 快响应和连续小目标证明同一次拖动只启动一个 driver；`PointerMoveCoalescerTest` 证明只投递每帧最新样本并保留跨帧总 delta；
- profiling 还发现综合测试 UI 的 Slider listener 曾在每个值上 `String.format + status.setText`，令 `240/245` 个基线拖动帧触发整页 Layout。fixture 现只保持 Slider -> Progress 的高频 paint 联动，状态文字留给低频操作。最终有效录制 `artifacts/fandui-1.21.4-slider-continuous-after-paint-only-valid-25s.jfr` 为 `447289` bytes，SHA-256 `A4CED9F177482C919B04CFC4ED438D6DFE4637B759A0597F7063E66B50231762`：动画启动 `1`，CoreFrame `202`，其中 Layout `2`、paint-only `197`，GC `0`；Core duration P50/P95/max 为 `0.4516/1.7385/8.1569ms`，相对基线 `0.8246/2.7217/8.725ms`；
- 最终录制的正常帧间隔 P50/P95 为 `16.7545/18.5701ms`。仍有四个 `190-283ms` 的整机渲染空洞；空洞内 Render-thread 的 `41` 个采样全部位于 Mojang/驱动路径：`28` 个为 `GLFW.glfwSwapBuffers -> Window.updateDisplay`，`13` 个为 `NativeImage._upload -> glTexSubImage2D`，没有 FandUI 栈。FandUI 无法在 Minecraft 没有产出帧时显示中间画面，但 `33.33ms` 时间步上限保证恢复后继续追随而不是一次跳完；
- `artifacts/fandui-1.21.4-slider-continuous-after-persistent-driver-25s.jfr`（SHA-256 `69870B976595454892A21437EE6C381BABB07B52E6300DC24E2F6C5341FF5411`）发生在资源 reload 已替换首次自动 Screen 之后，CoreFrame/动画均为 `0`，明确标记为未命中 Slider 的无效样本，后续不得作为性能对照；中间有效但仍有状态文字联动的样本 SHA-256 为 `9DC4A2409487904064301A88C20F248D9A869B663F23D74D70CC6A3175CEE688`；
- 定向验证命令 `./gradlew.bat :fandui-api:test :fandui-core:test :fandui-fabric-1.20.1:compileJava :fandui-fabric-1.21.4:compileJava :fandui-fabric-26.2:compileJava --console=plain` 字面 `BUILD SUCCESSFUL in 27s`；随后全仓 `./gradlew.bat check --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 1m 21s`，三版发布 JAR 内 `246` 个公共 API class 一致。最新 1.21.4 客户端 PID `34804`，仅启用 `-Dfandui.test.ui=true`、未启用状态断言；资源 generation `1` 后已用 F9 打开 `1920x1121 / 41 batches / 54 draw calls / FBO 11` 测试 Screen。用户随后按住快速拖动并明确确认“不卡了”，因此自动连续重放与真实目视 held-drag 两条验收均已通过。

### 2026-08-24 23:02：Core 事件路由稳态分配收敛

- 对 Slider 最终有效录制 `artifacts/fandui-1.21.4-slider-continuous-after-paint-only-valid-25s.jfr` 重新执行 `jfr summary` 与 `jdk.ObjectAllocationSample` 栈筛选：录制共有 `13` 个 allocation sample，`CoreEventDispatcher`、`ListenerBucket`、`EventListeners`、`CallbackContext` 和 `HandlerKey` 均未命中；可见的 FandUI 分配样本落在 `RecordingCanvas2D.finish -> DisplayList.<init> -> List.copyOf`。这份采样不足以证明事件路径零分配，但把下一项优先级明确为先消除源码中确定存在的 route scratch，再单独处理 DisplayList；
- `CoreEventDispatcher` 原先每次 `dispatchTo` 新建 `ArrayList` path 和 `DispatchState`。现在使用 session-owned、UI 线程限定的 `DispatchFrame` 池，复用 path backing array 与状态；池最多保留 `8` 个 frame，覆盖正常嵌套派发但不因异常递归无限保留。结果以 primitive bit flags 返回，frame 在成功、提前停止传播和异常路径都由 `finally` 清空后归还；
- `ListenerBucket` 的缓存从每次查询创建 `HandlerKey(eventClass, route)` 改为以现成 `Class<?>` 为 key、一次生成 capture/bubble 两个 immutable snapshot。注册或注销 listener 会清空缓存；同一类型与 route 的稳定命中返回相同 snapshot，不再创建复合 key 或扫描 entries；
- `CallbackContext` 刻意没有池化。公共契约要求上下文在 handler 返回后永久失效；若复用同一实例，被错误保留的旧引用可能在后续 handler 执行期间重新变为 active。当前仍每次 callback 创建独立上下文，以正确性换取这一项小对象分配，后续只有在不改变失效语义的设计下才继续优化；
- 新增嵌套路由回归连续执行 `1,000` 次外层 KeyEvent 和 `1,000` 次内层 KeyEvent：最终只保留 `2` 个 route frame，内层 `consume()` 不污染外层 consumed/default 状态，内外层上下文在回调后均抛 `IllegalStateException`。`UiContainerTest` 同时锁定 listener snapshot 命中 identity 与 mutation 后重建；API/Core 定向结果为 `30 suites / 141 tests / 0 failures / 0 errors / 0 skipped`；
- 三版本 `compileJava --rerun-tasks --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 28s`、`8 actionable tasks: 8 executed`。最终 `./gradlew.bat check --rerun-tasks --no-build-cache --console=plain --max-workers=2` 字面 `BUILD SUCCESSFUL in 1m 36s`、`41 actionable tasks: 41 executed`；全仓 XML 为 `45 suites / 215 tests / 0 failures / 0 errors / 0 skipped`，japicmp 没有 source/binary incompatibility，三版发布 JAR 内 `246` 个公共 API class 逐 class 一致；
- 本轮没有启动或重启 Minecraft，没有更新 API baseline，也没有执行 Git commit、branch、push、reset 或清理既有产物。下一项性能证据是 Paint-only 帧中的 immutable DisplayList 重建与 command 分配；在提出 fragment/subtree cache 前必须先建立 `100/1000` component fixture，分别量化静态、单控件动画和多控件动画，避免用破坏 `PaintScope.frameTimeNanos()` 语义的缓存换取表面数字。

### 2026-08-24 23:37：Core Scene/DisplayList 分配基准与热路径优化闭合

- 新增独立 Gradle 任务 `:fandui-core:coreScenePerformanceProbe`，只使用 test source set，不进入发布 JAR，也不挂到默认 `check`。任务固定 Java 17、G1、`512 MiB` heap 与 `-Xbatch`，通过 `com.sun.management.ThreadMXBean` 精确统计当前线程分配；fixture 覆盖 `100/1000` 个组件的静态帧、单组件 Paint 失效和全组件 Paint 失效，每项执行 5 组并取中位数；
- 稳定优化前基线为 `artifacts/fandui-core-scene-before-stable-20260824.csv`，SHA-256 `00B81325ACCEB784B71C535178B2D7F33EE24F69B47AFFD116001E49E2D8323A`；最终结果为 `artifacts/fandui-core-scene-after-list-transfer-20260824.csv`，SHA-256 `A165481D16AA22EFA0F52C36D3EC6FD3EFD053C7210F193DCCE468A879248EF7`。两个文件均记录 Java/VM/OS/CPU/heap、迭代数、命令数、DisplayList identity 变化、每帧时间与分配，可由同一任务重跑，不使用 JMH 外部依赖；
- 1000 组件 single-paint 的命令数由 `7006 -> 6005`（`-14.29%`），中位时间由 `278909.3 -> 196947.4 ns/frame`（`-29.39%`），分配由 `497440 -> 195832 B/frame`（`-60.63%`）；all-paint 中位时间由 `313657 -> 223443 ns/frame`（`-28.76%`），分配由 `497440 -> 195784 B/frame`（`-60.64%`）。静态场景继续复用同一 immutable DisplayList，identity 变化为 `0`，稳态约 `32 B/frame`；
- `RecordingCanvas2D` 用 primitive array 保存 save-state id、clip depth 和 global alpha，替代每层 `StateFrame`/`ArrayDeque`；同一次 recording 的前两种 Paint 使用直接槽位，第三种起才延迟建立 HashMap；相同 global alpha 不再重复写命令，restore 会同步恢复 recorder 侧 alpha。32 层扩容、LIFO 失败、alpha save/restore 和相同 Paint identity 均有回归；
- `LayoutNode` 在布局冻结时缓存不可变 local bounds，Scene paint 不再为每个组件每帧创建相同 `Rect`；`DisplayList` 构造器只接管 `RecordingCanvas2D` 不再使用的独占命令列表并包装 unmodifiable view，同时在一次线性扫描中校验 null 和计算 backdrop-blur 标志，避免 `List.copyOf` 的第二次数组复制；公共 `commands()` 仍不可修改，`DisplayList.combine` 的外部输入仍复制到自有列表；
- 曾尝试把 `SceneCompiler` 的递归 frame state 改为手工 primitive bridge；同口径 probe 显示它阻碍 HotSpot 对原有短命 frame 对象的逃逸分析，没有稳定收益，因此实验代码和测试均已撤回。当前不引入 subtree DisplayList cache：通用组件允许读取 `PaintScope.frameTimeNanos()`，无显式失效的时间动画仍需逐帧 paint，缓存会改变公开语义；
- 最终执行 `./gradlew.bat check --rerun-tasks --no-build-cache --console=plain --max-workers=2`，字面 `BUILD SUCCESSFUL in 1m 38s`、`41 actionable tasks: 41 executed`；XML 汇总为 `45 suites / 218 tests / 0 failures / 0 errors / 0 skipped`，japicmp、API Javadoc 和发布文档门禁通过，三个 Fabric JAR 内 `246` 个公共 API class 一致。未启动或重启 Minecraft，未更新 API baseline，也未执行 Git commit、branch、push、reset 或清理操作；下一项以长期 GPU/native 生命周期和图片、文字、gradient、blur cache 的有界性为对象建立证据。

### 2026-08-25 00:02：GPU cache 有界性审计与失败事务修复

- 当前显式 GPU 上限为：image texture `128 MiB`、text texture `64 MiB`、gradient lookup texture `16 MiB`、blur target `64 MiB`、clip framebuffer `512 MiB`；Skija CPU 侧另有 layout `512` 项、raster `64 MiB`、字体环境最多 `4` 个且只淘汰无 active lease 的环境。texture、NanoVG external image、framebuffer 和 native font environment 均有显式 delete/close；framebuffer/blur 为避免 resize 后重复创建会保留历史峰值，但受硬上限约束，本轮未发现正常路径无界增长；
- 审计发现 text/image cache 替换活动 raster 集时存在失败事务缺口：旧纹理可能已被容量驱逐，而新集合在中途 upload 失败后仍保留旧 `activeRasters` identity；再次激活同一个旧 List 会错误短路，随后 resolve 报告活动纹理缺失。两类 cache 现在在失败时共同清空 active identity/key，回收本轮已创建纹理，并把 cleanup 异常作为 suppressed 保留；捕获范围覆盖 `RuntimeException | Error`，因此后续重试会真实重建旧集合；
- image/text `resolve` 不再执行每次 `glIsTexture`；cache 自身是 texture owner，创建和删除路径已完整记账，逐 draw 同步驱动查询只会引入 stall，不能增加跨上下文正确性。图片采样状态继续由 renderer 的 `NanoVgTextureSampling` 在实际 draw 前集中管理，不再在 cache resolve 中重复改 texture parameter；
- `OpenGlTextTextureCacheTest` 与 `OpenGlImageTextureCacheTest` 新增失败注入：先激活旧集合，再让替代集合的第二次创建失败，确认旧 GPU texture 已被驱逐，随后关闭注入并用相同旧 List 成功重建。两个完整测试类修复后字面 `BUILD SUCCESSFUL in 22s`；本节不以单元测试替代长期显存与 RenderDoc 实机验证。

### 2026-08-25 00:18：OpenGL DisplayList preparation 零分配 direct-paint 路径

- 新增独立 Gradle 任务 `:fandui-render-opengl:renderPreparationPerformanceProbe`，仅位于 test source set，不进入发布 JAR，也不挂默认 `check`。固定 Java 17、G1、`512 MiB` heap 和 `-Xbatch`，以 `ThreadMXBean` 统计当前线程精确分配；无 OpenGL context 的 fixture 覆盖共享纯色、每命令不同纯色、共享 NanoVG 原生两色线性渐变，各自使用 `100/1000` 条 draw command、5 组样本取中位数；
- 优化前基线为 `artifacts/fandui-render-preparation-before-direct-paint-20260825.csv`，`541` bytes / SHA-256 `54FFEF05519CAE5BB1B8FC3ACAF5AF0FCC52F276434F508F56C0CE82105B7945`。最终样本为 `artifacts/fandui-render-preparation-after-direct-paint-final-20260825.csv`，`522` / `D09A71B35EDB346A666FEA9596F0F407FD85548A8AF945F579D90EC98E4E06D3`；独立 JVM 复测为 `artifacts/fandui-render-preparation-after-direct-paint-repeat-20260825.csv`，`522` / `FB74E6FCE2598913EB1E2799C39647A7F95E43344972446D5D01CCBB38D35111`；
- 1000 命令最终对比：共享纯色 `6111.4 -> 1667.733 ns`（`-72.71%`）、不同纯色 `85651.2 -> 1863.0 ns`（`-97.82%`）、原生两色渐变 `6815.733 -> 5901.333 ns`（`-13.42%`）；对应每次准备分配分别从 `792/53120/808 B` 全部降为 `0 B`。独立复测得到 `1599.267/1837.467/5789.0 ns` 且仍全部 `0 B`，方向和量级一致；
- `PreparedFrame` 不再为纯色和 NanoVG 可直接表达的两端渐变创建 `PreparedPaint`、identity-map entry 与 retained array；回放直接读取 immutable `DisplayPaint`。只有必须用 lookup texture 的多段渐变才惰性创建 identity map，以保留同一 paint 的 GPU image 去重；图片/文字改按 immutable DisplayList 的确定命令顺序存入可增长数组，删除 command identity map 和第二份 retained texture array，并在正常回放末尾验证槽位全部消费；
- preparation 与 player 主命令循环改为索引读取 RandomAccess command list，去掉每次 `32 B` iterator。新增 `NanoVgPreparedFrameTest` 锁定 solid/native-linear/native-radial 共用空准备帧，以及 `9` 个透明图片命令跨容量扩展、夹杂非纹理命令时仍保留准确 replay slot；OpenGL 模块全量结果为 `14 suites / 59 tests / 0 failures / 0 errors / 0 skipped`，字面 `BUILD SUCCESSFUL in 23s`；
- 该数字只描述 DisplayList 或资源 identity 变化时的 renderer CPU preparation；稳定 DisplayList 本就复用既有 `PreparedFrame`，而 GPU tessellation、draw call、texture upload 和 swap 不在此 probe 内。本轮没有改变公共 API、Canvas 命令语义、复杂渐变 cache key、premultiplied alpha 或三版本宿主接入，也尚未用这批新字节码重启 Minecraft。

### 2026-08-25 00:47：预设图标目录扩充与 F9 独立 Gallery

- `Icons` 从原有 19 个具名常量扩充到 `58` 个，新增账户、文件、编辑、可见性、状态、窗口、媒体和导航等常用图标；全部定义为 24x24 view-box 下的不可变 FandUI `Path`/stroke/fill，类初始化后不解析 SVG、不创建 texture，也不依赖 Minecraft、Fabric、LWJGL、NanoVG 或 Skija 类型；
- 新增 `Icons.all()`，返回单一、声明顺序稳定且不可修改的 `Map<String, IconDefinition>`，兼容别名也显式保留。目录在常量声明时通过私有 `preset(...)` 注册，避免维护第二份手写清单；反射回归要求每个 public static `IconDefinition` 字段都与目录一一对应，防止以后新增图标却漏入选择器或文档；
- 三版本共享的 `FandUiTestScreen` 主页面新增“浏览全部图标 · Icon gallery”按钮。独立 Gallery 直接消费 `Icons.all()`，逐行显示 30px 矢量预览、常量名和 layer 数，并提供外层滚轮/拖动滚动与显式返回按钮；Screen 替换使用现有 `runtime.screens().open(...)`，没有为开发 fixture 扩张公共导航栈；
- 为保持紧凑视口可靠，当前 Gallery 使用单列目录而未预先引入通用 Grid/Wrap 组件；`360x240` 与 `960x720` 布局、全部 58 项可达、滚动范围、入口/返回各一次回调和只读快照均有 Core 回归。API/Core 定向命令字面 `BUILD SUCCESSFUL in 15s`；
- API/Core 全量测试与三个 Fabric bridge 的 `compileJava --rerun-tasks` 字面 `BUILD SUCCESSFUL in 35s`、`14 actionable tasks: 14 executed`。随后 `./gradlew.bat check --rerun-tasks --no-build-cache --console=plain --max-workers=2 --no-daemon` 字面 `BUILD SUCCESSFUL in 3m 46s`、`41 actionable tasks: 41 executed`；XML 汇总为 `46 suites / 225 tests / 0 failures / 0 errors / 0 skipped`，Javadoc、japicmp 和三版本发布 JAR 内 `246` 个公共 API class 一致性均通过；
- 1.21.4 使用新字节码重新启动，Minecraft PID `19940`；综合测试 Screen 自动打开，日志确认 `854x480 / 46 batches / 59 draw calls / FBO 3`、资源 generation `1`，OpenAL 使用 `No Output` 避免宿主音频设备丢失时重复刷日志。客户端保留运行供人工检查入口、全部图标、滚动与返回；本轮未执行 Git commit、branch、push、reset 或 baseline 更新。
