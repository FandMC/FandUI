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
| `UiRuntime.availability/capabilities/isUiThread` | 任意线程 | 返回不可变快照/值 |
| `UiRuntime.execute` | 任意线程 | future 表示 action 完成或失败 |
| Screen、HUD、session、focus、animation | UI 线程 | `ScreenSession`/`HudRegistration` 关闭对应挂载 |
| 已挂载 component、controller 的 mutation | UI 线程 | component 只能属于一个 parent/session |
| `TextService` | 任意线程 | future 可独立取消；`TextLayout` 不含 native handle |
| `ResourceService.generation`、`ImageRef` 读取 | 任意线程 | handle 稳定且无需关闭 |
| 资源注册、reload、reload listener | UI 线程 | registration/listener handle 需由创建者关闭 |
| `PaintScope`、`MeasureScope`、`EventContext` | 仅回调期间 | 禁止保存到回调外 |

`AutoCloseable` handle 均采用幂等关闭语义。session 关闭会解除组件绑定、清理焦点并终止其
动画；关闭 listener handle 只注销 listener，不关闭它观察的对象。

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

## 8. 文字

`TextService.layout`、`hitTest` 和 `geometry` 都是异步操作。标准 `Text`/`TextInput` 采用
latest-request-wins，并在新结果等待期间保留上一完整 visual，不会把半成品帧提交给 renderer。
索引统一为 UTF-16 offset，并保证 surrogate boundary；长度限制按 Unicode code point 计算。

## 9. 可用性与失败处理

先读取：

```java
if (!FandUI.runtime().availability().available()) {
    return;
}
```

Screen/HUD 创建在 runtime 不可用时抛出 `UiUnavailableException`。`execute`、Text future 和其他
异步结果通过 exceptional completion 传递失败。不要静默切换到另一套 renderer，也不要绕过
公共 API 访问 native/Minecraft handle。
