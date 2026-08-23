# FandUI

FandUI 是面向 Fabric 客户端 Mod 的 UI 基础库。它提供稳定的纯 Java 公共 API，并将
Minecraft、Fabric、LWJGL、NanoVG 和 Skija 类型限制在实现模块中。

当前版本为 `0.1.0-SNAPSHOT`，目标版本分别独立构建：

| Minecraft | Java | 发布模块 |
|---|---:|---|
| 1.20.1 | 17 | `fandui-fabric-1.20.1` |
| 1.21.4 | 21 | `fandui-fabric-1.21.4` |
| 26.2 | 25 | `fandui-fabric-26.2` |

三个版本共享以 Java 17 编译的 `fandui-api`，业务 Mod 不需要在公共代码中引用 Minecraft
渲染类型。

## 能力

- 可变组件树与不可变布局/DisplayList 帧快照
- Box、Row、Column、Flexible、Stack、Positioned、Text、Image、Button、TextInput、ScrollContainer
- Theme、Style、局部 Theme/方向作用域、transform-aware hit test
- capture/target/bubble 事件、pointer capture、enter/leave、焦点、光标、剪贴板与 IME 事件
- Skija Unicode shaping、中文/Emoji fallback、换行、基线、命中与编辑几何
- NanoVG 路径、渐变、图片、Scissor、路径裁剪、描边和 backdrop blur
- Minecraft Screen 与默认穿透、可显式交互的 HUD layer
- 原子资源 generation、PNG decode、文字/图片有界 GPU cache

## 当前渲染边界

当前 `0.1` 实现使用 Minecraft 已有 OpenGL 上下文与当前颜色目标，通过 NanoVGGL3 提交
独立 UI pass。它不创建第二个窗口、Swapchain 或图形设备。26.2 只有在 Minecraft 实际选择
OpenGL backend 时该实现才可用；当前没有 Vulkan renderer。运行代码应读取
`FandUI.runtime().availability()`，不要自行探测 LWJGL 或 Minecraft backend 类。

当前 native 打包和实机验证范围为 Windows x86-64。其他 OS/CPU classifier 尚未作为可发布
支持声明。

## 使用

公共 API、线程规则、Style 消费语义、Screen/HUD 和资源示例见
[API 使用指南](docs/API-GUIDE.md)。API Javadoc 由以下命令生成：

```powershell
./gradlew.bat :fandui-api:javadoc
```

生成入口为 `fandui-api/build/docs/javadoc/index.html`，其中不会发布
`cn.fandmc.fandui.internal` 包。

## 构建

```powershell
./gradlew.bat buildAll --no-build-cache --console=plain --max-workers=2
```

该命令构建共享模块和三个独立 Fabric JAR，不启动 Minecraft。各版本产物位于对应模块的
`build/libs/`。

## API 兼容门禁

```powershell
./gradlew.bat :fandui-api:check --no-build-cache --console=plain --max-workers=2
./gradlew.bat verifyEmbeddedApiConsistency --no-build-cache --console=plain --max-workers=2
```

第一条命令用 japicmp 同时检查 source/binary 兼容性，并验证 Java 17 consumer、公开签名隔离、
Javadoc 和 sources JAR；报告位于 `fandui-api/build/reports/api-compatibility.html`。第二条命令
逐 class 比较三个 Fabric JAR 内嵌公共 API 与规范 API JAR 的 SHA-256，报告位于
`build/reports/api-class-consistency.txt`。

`fandui-api:updateApiBaseline` 会替换兼容基线，不属于常规构建。只有明确接受对应版本的公共
API 变更时才执行；修复兼容检查时不得用忽略缺失 class 的选项绕过依赖解析。

## 工程边界

```text
fandui-api
  <- fandui-canvas
  <- fandui-core
  <- fandui-text-skija
  <- fandui-render-opengl
  <- fandui-fabric-1.20.1 / 1.21.4 / 26.2
```

`RESEARCH.md` 是实现追踪与源码/依赖事实记录，不是面向使用者的稳定 API 文档。
