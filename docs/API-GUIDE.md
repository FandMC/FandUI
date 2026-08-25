# FandUI API 使用指南

## 1. 依赖边界

业务代码只导入 `cn.fandmc.fandui.api` 及其子包。不要引用
`cn.fandmc.fandui.internal`、Minecraft、Fabric、Skija、NanoVG 或 LWJGL 类型来完成 UI
逻辑。每个 Minecraft 版本使用对应的 `fandui-fabric-*` Mod JAR，公共源码保持一份。

## 2. 最小 Screen

下面的代码块由 `ApiGuideCompilationTest` 从本文件读取并以 Java 17 编译，避免文档示例随
API 演进失效。

<!-- api-compile-start -->
```java
package example;

import cn.fandmc.fandui.api.FandUI;
import cn.fandmc.fandui.api.component.Box;
import cn.fandmc.fandui.api.component.Button;
import cn.fandmc.fandui.api.component.Column;
import cn.fandmc.fandui.api.component.Text;
import cn.fandmc.fandui.api.screen.ScreenSession;
import cn.fandmc.fandui.api.screen.UiScreen;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.text.TextStyle;

import java.util.concurrent.CompletableFuture;

public final class ExampleUi {
    private ScreenSession current;

    public CompletableFuture<Void> open() {
        return FandUI.runtime().execute(() -> {
            TextStyle body = TextStyle.builder(16.0f)
                    .locale("zh-CN")
                    .build();
            Text status = Text.of("等待操作 / Idle", body);
            Button action = Button.text("确认 / Confirm", body, () -> status.setText("已完成 / Done"));
            Column content = Column.builder(
                            Text.of("FandUI", TextStyle.of(22.0f)),
                            status,
                            action)
                    .gap(10.0f)
                    .build();
            Box panel = Box.builder(content)
                    .style(Style.builder()
                            .padding(16.0f)
                            .background(Color.rgb(0x20afff).withAlpha(0.38f))
                            .border(1.0f, Color.rgb(0xb9efff))
                            .cornerRadius(10.0f)
                            .backdropBlur(18.0f)
                            .build())
                    .build();
            current = UiScreen.builder("Example", panel)
                    .pausesGame(false)
                    .build()
                    .open();
        });
    }

    public CompletableFuture<Void> close() {
        return FandUI.runtime().execute(() -> {
            if (current != null) {
                current.close();
                current = null;
            }
        });
    }
}
```
<!-- api-compile-end -->

关闭 FandUI Screen 会自动恢复打开第一张 FandUI Screen 前的 Minecraft Screen。
FandUI Screen 替换另一张 FandUI Screen 时仍保留最初 parent。

## 3. 线程与所有权

| API | 线程 | 所有权 |
|---|---|---|
| `UiRuntime.availability/capabilities/diagnostics/isUiThread` | 任意线程 | 返回不可变快照/值 |
| `UiRuntime.execute` | 任意线程 | future 表示 action 完成或失败 |
| Screen、HUD、session、focus、animation | UI 线程 | `ScreenSession`/`HudRegistration` 关闭对应挂载 |
| 已挂载 component、controller 的 mutation | UI 线程 | component 只能属于一个 parent/session |
| `TextService` | 任意线程 | future 可独立取消；`TextLayout` 不含 native handle |
| `ResourceService.generation`、`ImageRef` 读取 | 任意线程 | handle 稳定且无需关闭 |
| 资源注册、reload、reload listener | UI 线程 | registration/listener handle 需由创建者关闭 |
| `PaintScope`、`MeasureScope`、`EventContext` | 仅回调期间 | 禁止保存到回调外 |

`AutoCloseable` handle 均采用幂等关闭语义。session 关闭会解除组件绑定、清理焦点并终止其
动画；关闭 listener handle 只注销 listener，不关闭它观察的对象。

`UiContainer.children()` 返回结构变化时发布的 immutable snapshot；已经取得的旧 snapshot
不会随之后的增删替换改变。挂载后的组件树只能在 UI 线程修改；未挂载树的结构 mutation
也会经过统一串行边界，避免多个线程同时破坏内部列表。`structureChanging` 使用线程局部
重入标记，只拒绝 attach/detach 回调对同一容器的递归修改；全局结构锁负责跨线程的临界区，
它不是渲染同步机制，也不允许把可变组件树交给渲染线程。未来并行渲染仍只读取 immutable
frame/display-list snapshot。

`UiContainer.replace(index, child)` 是原子替换：新 child 的 parent、key 或 attach callback
校验失败时，旧 child、parent 和 `children()` snapshot 都保持有效。`Box`、`Button`、
`ScrollContainer`、`Flexible`、`Positioned`、`ConstrainedBox`、`ThemeScope`、
`DirectionScope` 必须始终恰有一个 child，因此拒绝 `remove()`、`clear()` 和第二次 `add()`；
动态内容使用各自的 `setChild()`，替换在一次 mutation 中完成，不会暴露空的中间树。对这类
容器调用 `clear()` 会给出包含 `setChild(...)` 建议的 `IllegalStateException`；普通 `Row`、
`Column`、`Stack` 允许为空并支持 `clear()`。

首批交互控件为 `Checkbox`、`Slider` 和 `ProgressIndicator`。它们的 setter、change listener、
键盘与 pointer 行为都遵守同一 UI-thread 规则；`Checkbox`/`Slider` 在 pointer down 时获得
focus 和 capture，`Slider` 同时支持方向键、Home/End 与 PageUp/PageDown，`ProgressIndicator`
将输入值有限化并 clamp 到 `[0, 1]`。控件的主题 token 可通过 `StyleResolver`/`Theme` 覆盖，
无需直接接触 Canvas 或 Minecraft 类型。

### 3.1 控件、图标与 SVG

控件使用普通 Java 值和监听器，不需要手写事件状态机：

```java
ToggleSwitch sound = ToggleSwitch.builder()
        .selected(true)
        .onValueChange(enabled -> settings.setSoundEnabled(enabled))
        .build();
Slider volume = Slider.builder()
        .range(0.0, 1.0)
        .value(0.75)
        .onValueChange(settings::setVolume)
        .build();
Dropdown<String> quality = Dropdown.builder(List.of(
        Dropdown.Option.of("fast", "快速 / Fast"),
        Dropdown.Option.of("quality", "质量 / Quality")))
        .onValueChange(settings::setQuality)
        .build();
Icon search = Icon.of(Icons.SEARCH);
Map<String, IconDefinition> presets = Icons.all();
Icon settings = Icon.of(presets.get("SETTINGS"));
```

`Icons` 当前内置 58 个具名预设，覆盖导航、文件操作、编辑、可见性、账户、媒体、窗口和状态提示。
`Icons.all()` 返回同一份按常量声明顺序排列的只读 `Map<String, IconDefinition>`，也包含
`CHECKMARK`、`ARROW_LEFT` 等兼容别名；因此图标选择器和文档生成器无需复制一份容易遗漏的名称清单。
预设定义只包含 FandUI 的不可变 `Path`、fill 和 stroke 数据，类加载后不会解析 SVG、创建纹理或接触
Minecraft/Fabric/native 类型。

`ToggleSwitch.setSelected(...)` 会立即提交布尔值和 change listener，thumb 的视觉位置由 session
拥有的 `AnimationManager` 在 140 ms 内过渡；动画中途反向会从当前视觉值继续，不创建控件私有
计时器。默认 `ToggleSwitch.THUMB_INSET` 为 `3.0` 逻辑像素，可通过 `Theme` 覆盖。

`Dropdown.setExpanded(...)` 同样立即更新交互状态，菜单高度和箭头在 160 ms 内过渡。选项始终
受当前动画高度裁剪，收起期间不会越过组件边界绘制。两种过渡都只在所属 session 活跃时运行，
组件 detach 会取消动画；开关只使 paint 失效，下拉菜单仅在过渡帧使 layout 失效。

`ScrollContainer` 只在 child extent 大于 viewport extent 时产生可滚动偏移；内容完整可见时
`maximumOffset()` 为 `0`。滚轮和 pointer drag 由容器处理，尺寸、GUI Scale 或窗口变化后会在
下一次 layout 自动重算范围。综合测试面板用一个外层垂直容器覆盖整页，并保留底部日志区作为
嵌套滚动验证，不要求业务代码注册窗口回调。

`IconDefinition.fromSvg(String)` 和 `SvgIcon` 将 SVG 解析为不可变 Canvas path，运行时不创建
图片纹理，也不把 Skija、NanoVG 或 Minecraft 类型带入公共签名。解析器支持受限且有界的
`path`、`rect`、`circle`、`ellipse`、`line`、`polyline`、`polygon`、`g`/`transform`，以及
solid `fill`/`stroke`、`rgb`/`rgba`、opacity、stroke cap/join 和标准 path 命令
`M/L/H/V/C/S/Q/T/A/Z`。外部实体、DOCTYPE、`image`/`use`、`text`、filter、外部 CSS 和
渐变引用会被拒绝或忽略；需要渐变时使用 `Canvas2D`/`Style` 的平台中立 API。

资源图片也可直接使用 SVG：

```java
UiKey logoKey = UiKey.of("example", "textures/logo.svg");
ImageRef logo = FandUI.runtime().resources().image(logoKey);
ResourceRegistration registration = FandUI.runtime().resources().registerImage(
        logoKey,
        ResourceSource.svg("<svg viewBox=\"0 0 64 24\">"
                + "<rect width=\"64\" height=\"24\" rx=\"6\" fill=\"#20aee8\"/>"
                + "</svg>"));
```

`ResourceSource.svg(byte[]/String)` 发布 `ResourceFormat.SVG` 提示；旧的
`ResourceSource.bytes(...)` 保持兼容，并按 PNG signature 或 UTF-8 SVG 根元素自动选择解码器。
SVG 在资源 reload worker 上仅栅格化一次。root 的正数 `width`/`height`（可带 `px`）优先作为
intrinsic size；只提供一个可用尺寸时按 `viewBox` 比例补齐，两个尺寸都缺失或使用百分比/相对
单位时使用 `viewBox` 的向上取整像素。随后沿用 PNG 相同的 premultiplied RGBA8、SHA-256
texture key 和 OpenGL texture LRU。reload 仍是
完整 candidate generation 的原子发布：任一 SVG 解析、尺寸预算或像素转换失败，旧 READY
generation 保持不变。资源 SVG 受 2 MiB 编码、4096 边长和 64 MiB 解码像素预算限制；需要
无损缩放的 UI 图标应优先使用 `SvgIcon`。

## 4. Style 消费规则

为避免自定义绘制出现双重 padding 或重复背景，Style 字段按以下边界消费：

| 字段 | 消费方 |
|---|---|
| `margin` | Core layout，作为组件 border box 外部空间 |
| `transform` | Core scene/layout geometry，同时用于绘制、命中、clip 和方向焦点 |
| `opacity` | Core，沿祖先链相乘 |
| `clip` | Core，在组件 paint 与 child traversal 前应用 |
| `backdropBlurRadius` | Core，在组件背景 paint 前记录 |
| `cornerRadii` | Core 的 rounded clip；同时供 box component 绘制背景/边框 |
| `padding/background/border` | `Box`、`Button`、`TextInput`、`ScrollContainer` 等明确的 box component；自定义 component 自行消费 |

`Row`、`Column`、`Text`、`Image`、`CanvasComponent` 不会隐式绘制 background/border，也不会
隐式增加 padding。需要这些行为时，用 `Box` 包裹，或者在自定义 measure/paint 中明确处理。

## 5. 布局与坐标

- 所有公共尺寸和坐标使用逻辑 UI 像素；窗口尺寸、GUI Scale 和 framebuffer 变化由 session
  在下一帧自动重建 viewport、布局和渲染目标。
- `Flexible` 只在直接父级 `Row`/`Column` 中提供 grow、shrink、basis 和 fit 数据。
- `Positioned` 只在直接父级 `Stack` 中解释边缘位置、显式尺寸和 z-index。
- transform 的 Canvas 顺序、hit test、`sceneToLocal`、祖先 clip 和方向焦点共用同一组合矩阵；
  不可逆矩阵不可命中。
- 普通文字纹理在纯平移下自动对齐设备像素网格，开发者不需要手工把 `13px` 等字号改成
  偶数，也不需要在 GUI Scale/resize 后手工重新对齐。

## 6. 输入

事件按 capture -> target -> bubble 路由。`consume()` 同时阻止默认行为和继续传播；
`capturePointer()` 将后续 pointer 序列固定路由到当前目标，直到 release/cancel。

`KeyEvent.scanCode()` 返回宿主物理 scan code；未知时为 `-1`，该整数只适合在当前宿主/键盘布局内做比较。
`KeyEvent.action()` 在三个 Minecraft 版本都提供一致的 `PRESS`、`REPEAT`、`RELEASE`：Screen
首次收到某 key 的 `keyPressed` 是 `PRESS`，同一 Screen 未收到 release 前的后续回调是 `REPEAT`，
`keyReleased` 是 `RELEASE`，Screen 移除时状态清空。宿主 callback 不暴露 GLFW action 字段，
因此这是 FandUI 在 Core 中按生命周期归一化的语义。`UiRuntime.capabilities().distinctKeyRepeat()`
在 1.20.1、1.21.4、26.2 均为 `true`；IME composition 仍只有 26.2 为 `true`。

`TextController.replace(range, replacement)` 可直接替换任意合法 UTF-16 区间并把 caret 放到插入文本末尾；`TextInput` 已处理选择、caret、IME preview/commit、Ctrl/Super+A/C/X/V、placeholder、read-only、
password mask、Unicode code-point max length、replacement filter 和 candidate validator。外部
controller 修改同样必须遵守 UI 线程规则。

HUD 默认 `HudInputMode.PASS_THROUGH`，不会抢占游戏输入。只有显式设为 `INTERACTIVE` 后，
`HudRegistration.dispatch(event)` 才进入组件树；平台桥不会偷偷把所有 HUD 变成交互层。

## 7. 资源与失败

`registerImage/registerFont` 只登记 source。调用 `ResourceService.reload()` 后，FandUI 使用最近
一次 Minecraft resource lookup 构建完整 candidate generation。全部必需资源成功后才原子
发布新 generation；失败时保留旧的 READY snapshot 和 generation，并抛出 reload 异常。

`ImageRef.state()`、`info()`、`failure()` 是同一稳定 handle 的当前 snapshot。首次加载失败或
missing 时可从 `failure()` 读取实际 source/decode 异常；已有 READY 图片在失败 reload 后继续
引用上一 generation。

自定义字体通过 `registerFont` 加入下一次完整 candidate generation。reload 发布前 Skija 会
真实解析所有 TTF/OTF；任一字体无效时整代回滚。每个 `TextLayout` 保存该 generation 的不可变
字体字节 snapshot，不保存 `FontCollection` 等 native handle。每次 Paragraph/raster 操作都会
短暂持有 environment lease，LRU 只会淘汰没有活跃 native 使用者的环境，忙碌环境会延迟到
lease 释放后再关闭。环境查找、lease 获取和 LRU 扫描在同一缓存临界区完成，不会在两步之间
拿到已经退休的环境。Skija 最多缓存四个 native 字体环境；旧环境淘汰后，旧 layout 的 raster、
`hitTest()` 和 `geometry()` 会按其 snapshot 重建环境，因此仍可使用。`FontFamilies.DEFAULT`
保留给内置 CJK/Emoji fallback，不能注册覆盖。

## 8. 文字

`TextService.layout`、`hitTest` 和 `geometry` 都是异步操作。标准 `Text`/`TextInput` 采用
latest-request-wins，并在新结果等待期间保留上一完整 visual，不会把半成品帧提交给 renderer。
索引统一为 UTF-16 offset，并保证 surrogate boundary；长度限制按 Unicode code point 计算。

## 9. 可用性、诊断与失败处理

先读取：

```java
if (!FandUI.runtime().availability().available()) {
    return;
}
```

Renderer 细节从任意线程读取动态 immutable snapshot：

```java
var diagnostics = FandUI.runtime().diagnostics();
if (diagnostics.targetReady()) {
    int width = diagnostics.framebufferWidth();
    int height = diagnostics.framebufferHeight();
    boolean blur = diagnostics.backdropBlur();
}
```

`backend()` 使用稳定的 `UNKNOWN/OPENGL/VULKAN` 枚举，`backendName()` 是宿主诊断名；
`targetReady()` 只会在 FandUI pass 成功验证并使用 Minecraft color target 后变为 `true`。
此时可读取实际 framebuffer 尺寸、`UiColorFormat.RGBA8_UNORM`、最大纹理尺寸和
Stencil/Backdrop Blur 可用性。resize、目标丢失、renderer 失败或 shutdown 会原子清除全部
target-dependent 字段。26.2 在最终 GUI hook 观察实际 Minecraft backend；Vulkan 模式只报告
`UiRendererBackend.VULKAN` 和原因，不创建 OpenGL fallback。

Screen/HUD 创建在 runtime 不可用时抛出 `UiUnavailableException`。`execute`、Text future 和其他
异步结果通过 exceptional completion 传递失败。不要静默切换到另一套 renderer，也不要绕过
公共 API 访问 native/Minecraft handle。

## 10. 内置综合测试界面

FandUI JAR 内含一个默认关闭的开发 fixture，用于对三个 Minecraft bridge 运行相同的组件树。
设置 JVM property `fandui.test.ui=true` 后，测试 Screen 会在首个 client tick 自动打开；关闭后
按 `F9` 可重开。旧的基础 Demo 仍由 `fandui.demo.screen=true` 控制并使用 `F8`，两者互不替代。

综合 fixture 覆盖中英文、日文、韩文、Emoji 与字体 fallback，Button、TextInput、Checkbox、
ToggleSwitch、Slider、ProgressIndicator、Dropdown、预设 Icon、inline `SvgIcon`、reload worker
栅格化的 SVG `Image`、圆角背景、描边、线性渐变、三层路径裁剪、Backdrop Blur 和滚动容器。
布局只读取 runtime 提供的 `UiViewport`；resize、GUI Scale 与 framebuffer generation 变化不需要
业务组件注册窗口回调或手动失效。

面板内容由外层垂直 `ScrollContainer` 承载，紧凑视口下整页可滚动，底部日志仍是独立的嵌套
滚动区域。开关 thumb 与下拉菜单分别使用 140 ms、160 ms 的 session 动画，便于在三个 bridge
上同时检查连续重绘、布局失效、裁剪和中途反向。

```powershell
$env:JAVA_TOOL_OPTIONS="-Dfandui.test.ui=true"
./gradlew.bat :fandui-fabric-1.20.1:runClient --console=plain --max-workers=2
./gradlew.bat :fandui-fabric-1.21.4:runClient --console=plain --max-workers=2
./gradlew.bat :fandui-fabric-26.2:runClient --console=plain --max-workers=2
```

同一时间只运行一个客户端。该 property 是 FandUI 自身的开发验收入口，不属于业务 Mod 必须
依赖的公共 API。
