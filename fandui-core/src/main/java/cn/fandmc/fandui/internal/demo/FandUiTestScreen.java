package cn.fandmc.fandui.internal.demo;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.UiDiagnostics;
import cn.fandmc.fandui.api.component.Box;
import cn.fandmc.fandui.api.component.Button;
import cn.fandmc.fandui.api.component.Checkbox;
import cn.fandmc.fandui.api.component.Column;
import cn.fandmc.fandui.api.component.ConstrainedBox;
import cn.fandmc.fandui.api.component.Dropdown;
import cn.fandmc.fandui.api.component.Icon;
import cn.fandmc.fandui.api.component.Image;
import cn.fandmc.fandui.api.component.ImageFit;
import cn.fandmc.fandui.api.component.ProgressIndicator;
import cn.fandmc.fandui.api.component.Row;
import cn.fandmc.fandui.api.component.ScrollContainer;
import cn.fandmc.fandui.api.component.Slider;
import cn.fandmc.fandui.api.component.SvgIcon;
import cn.fandmc.fandui.api.component.Text;
import cn.fandmc.fandui.api.component.TextInput;
import cn.fandmc.fandui.api.component.ToggleSwitch;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.control.ScrollController;
import cn.fandmc.fandui.api.component.control.TextController;
import cn.fandmc.fandui.api.icon.Icons;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.CrossAxisAlignment;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceSource;
import cn.fandmc.fandui.api.screen.ScreenBackground;
import cn.fandmc.fandui.api.screen.ScreenSession;
import cn.fandmc.fandui.api.screen.UiScreen;
import cn.fandmc.fandui.api.style.ClipMode;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.GradientStop;
import cn.fandmc.fandui.api.style.LinearGradient;
import cn.fandmc.fandui.api.style.SolidPaint;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.style.VisualState;
import cn.fandmc.fandui.api.text.FontWeight;
import cn.fandmc.fandui.api.text.TextStyle;
import cn.fandmc.fandui.core.runtime.CoreUiRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Comprehensive property-gated UI fixture used by all three Minecraft bridges. */
public final class FandUiTestScreen {
    public static final String ENABLE_PROPERTY = "fandui.test.ui";

    static final UiKey ROOT_KEY = key("test-ui/root");
    static final UiKey PANEL_KEY = key("test-ui/panel");
    static final UiKey STATUS_KEY = key("test-ui/status");
    static final UiKey RUN_KEY = key("test-ui/run");
    static final UiKey ICON_GALLERY_KEY = key("test-ui/icon-gallery");
    static final UiKey CHECKBOX_KEY = key("test-ui/checkbox");
    static final UiKey SWITCH_KEY = key("test-ui/switch");
    static final UiKey SLIDER_KEY = key("test-ui/slider");
    static final UiKey DROPDOWN_KEY = key("test-ui/dropdown");
    static final UiKey PROGRESS_KEY = key("test-ui/progress");
    static final UiKey INPUT_KEY = key("test-ui/input");
    static final UiKey PAGE_SCROLL_KEY = key("test-ui/page-scroll");
    static final UiKey SCROLL_KEY = key("test-ui/scroll");
    static final UiKey SVG_KEY = key("test-ui/svg-icon");
    static final UiKey RESOURCE_IMAGE_KEY = key("test-ui/svg-resource");
    static final UiKey ICON_GALLERY_ROOT_KEY = key("test-ui/icon-gallery/root");
    static final UiKey ICON_GALLERY_PANEL_KEY = key("test-ui/icon-gallery/panel");
    static final UiKey ICON_GALLERY_SCROLL_KEY = key("test-ui/icon-gallery/scroll");
    static final UiKey ICON_GALLERY_BACK_KEY = key("test-ui/icon-gallery/back");

    private static final float PANEL_MAX_WIDTH = 760.0f;
    private static final float PANEL_MAX_HEIGHT = 590.0f;

    private static final TextStyle TITLE = TextStyle.builder(22.0f)
            .weight(FontWeight.BOLD)
            .color(Color.rgb(0xf4fbff))
            .locale("zh-CN")
            .build();
    private static final TextStyle BODY = TextStyle.builder(14.0f)
            .color(Color.rgb(0xdcecf4))
            .locale("zh-CN")
            .build();
    private static final TextStyle MUTED = TextStyle.builder(12.0f)
            .color(Color.rgb(0xa5bcc7))
            .locale("zh-CN")
            .build();
    private static final Style PANEL_STYLE = Style.builder()
            .padding(16.0f)
            .background(Color.rgb(0x0d2433).withAlpha(0.78f))
            .border(1.0f, Color.rgb(0x79ddff).withAlpha(0.78f))
            .cornerRadius(12.0f)
            .backdropBlur(16.0f)
            .clip(ClipMode.ROUNDED_BOUNDS)
            .build();
    private static final Style SECTION_STYLE = Style.builder()
            .padding(10.0f)
            .background(new LinearGradient(
                    new Point(0.0f, 0.0f),
                    new Point(420.0f, 80.0f),
                    List.of(
                            GradientStop.at(0.0f, Color.rgb(0x173b50).withAlpha(0.88f)),
                            GradientStop.at(0.52f, Color.rgb(0x185064).withAlpha(0.82f)),
                            GradientStop.at(1.0f, Color.rgb(0x17372f).withAlpha(0.72f)))))
            .border(1.0f, Color.rgb(0x6ea8b9).withAlpha(0.48f))
            .cornerRadius(7.0f)
            .clip(ClipMode.ROUNDED_BOUNDS)
            .build();
    private static final Style SCROLL_STYLE = Style.builder()
            .padding(8.0f)
            .background(Color.rgb(0x07151d).withAlpha(0.52f))
            .border(1.0f, Color.rgb(0x7d9aa6).withAlpha(0.46f))
            .cornerRadius(7.0f)
            .clip(ClipMode.ROUNDED_BOUNDS)
            .build();
    private static final Style IMAGE_CLIP_STYLE = Style.builder()
            .padding(3.0f)
            .background(Color.rgb(0x061117).withAlpha(0.56f))
            .border(1.0f, Color.rgb(0x72e2ff).withAlpha(0.52f))
            .cornerRadius(6.0f)
            .clip(ClipMode.ROUNDED_BOUNDS)
            .build();
    private static final Style ICON_ROW_STYLE = Style.builder()
            .padding(10.0f, 7.0f)
            .background(Color.rgb(0x102c3b).withAlpha(0.68f))
            .border(1.0f, Color.rgb(0x56899a).withAlpha(0.42f))
            .cornerRadius(5.0f)
            .build();
    private static final String INLINE_SVG = """
            <svg viewBox="0 0 24 24">
              <path d="M12 2L22 12L12 22L2 12Z" fill="#28b9e9" opacity="0.92"/>
              <circle cx="12" cy="12" r="4.2" fill="#eafcff"/>
            </svg>
            """;
    private static final String SVG_RESOURCE = """
            <svg width="128px" height="72px" viewBox="0 0 64 36">
              <rect width="64" height="36" rx="7" fill="#153c55"/>
              <path d="M4 27 C14 12 22 30 32 16 S50 22 60 8" fill="none"
                    stroke="#72e2ff" stroke-width="2" stroke-linecap="round"/>
              <circle cx="48" cy="12" r="5" fill="#b8f5ff" opacity="0.82"/>
            </svg>
            """;

    private final CoreUiRuntime runtime;
    private boolean initialOpenPending = true;

    private FandUiTestScreen(CoreUiRuntime runtime) {
        this.runtime = runtime;
    }

    public static Optional<FandUiTestScreen> installIfEnabled(CoreUiRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || !runtime.availability().available()) {
            return Optional.empty();
        }
        runtime.resources().registerImage(RESOURCE_IMAGE_KEY, ResourceSource.svg(SVG_RESOURCE));
        return Optional.of(new FandUiTestScreen(runtime));
    }

    /** Opens once on the first client tick, then on an explicit development-key edge. */
    public boolean openIfRequested(boolean requested) {
        boolean shouldOpen = initialOpenPending || requested;
        initialOpenPending = false;
        if (!shouldOpen) {
            return false;
        }
        open();
        return true;
    }

    public ScreenSession open() {
        return openMain();
    }

    private ScreenSession openMain() {
        return runtime.screens().open(createDefinition(
                runtime,
                runtime.resources().image(RESOURCE_IMAGE_KEY),
                this::openIconGallery).screen());
    }

    private ScreenSession openIconGallery() {
        return runtime.screens().open(createIconGalleryDefinition(this::openMain).screen());
    }

    static Definition createDefinition(CoreUiRuntime runtime, ImageRef resourceImage) {
        return createDefinition(runtime, resourceImage, () -> { });
    }

    static Definition createDefinition(CoreUiRuntime runtime, ImageRef resourceImage, Runnable openIconGallery) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(resourceImage, "resourceImage");
        Objects.requireNonNull(openIconGallery, "openIconGallery");

        UiDiagnostics diagnostics = runtime.diagnostics();
        String backend = diagnostics.backendName().isBlank() ? "unknown" : diagnostics.backendName();
        Text status = Text.builder(
                        "状态：就绪 · Ready · backend=" + backend,
                        MUTED)
                .key(STATUS_KEY)
                .build();

        ProgressIndicator progress = ProgressIndicator.builder()
                .key(PROGRESS_KEY)
                .progress(0.35)
                .build();
        Slider slider = Slider.builder()
                .key(SLIDER_KEY)
                .range(0.0, 100.0)
                .value(35.0)
                .step(1.0)
                .onValueChange(value -> progress.setProgress(value / 100.0))
                .build();
        Checkbox checkbox = Checkbox.builder(Text.of("路径/裁剪测试 · Path + clip", BODY))
                .key(CHECKBOX_KEY)
                .checked(true)
                .build();
        checkbox.onChange(() -> status.setText(
                checkboxLabel(checkbox.checked(), "路径/裁剪测试")));
        ToggleSwitch toggle = ToggleSwitch.builder()
                .key(SWITCH_KEY)
                .selected(true)
                .onValueChange(value -> status.setText(
                        (value ? "高对比度已启用" : "高对比度已关闭") + " · Toggle"))
                .build();
        Dropdown<String> dropdown = Dropdown.builder(List.of(
                        Dropdown.Option.of("fast", "快速 · Fast"),
                        Dropdown.Option.of("balanced", "平衡 · Balanced"),
                        Dropdown.Option.disabled("disabled", "禁用项 · Disabled"),
                        Dropdown.Option.of("quality", "质量 · Quality")))
                .key(DROPDOWN_KEY)
                .selectedIndex(1)
                .onChange(value -> status.setText("下拉选择：" + value + " · Dropdown"))
                .build();

        TextController textController = TextController.create("FandUI SVG 测试");
        TextInput input = TextInput.builder(textController, BODY)
                .key(INPUT_KEY)
                .onSubmit(value -> status.setText("输入提交：" + value + " · TextInput"))
                .build();

        int[] runs = {0};
        Button run = Button.builder(Text.of("运行一次 · Run", BODY))
                .key(RUN_KEY)
                .style(FandUiTestScreen::buttonStyle)
                .onClick(() -> {
                    runs[0]++;
                    double next = (runs[0] % 11) / 10.0;
                    progress.setProgress(next);
                    slider.setValue(next * 100.0);
                    status.setText("测试通过 " + runs[0] + " 次 · checks="
                            + "layout,input,svg,clip,gradient,blur");
                })
                .build();
        Button iconGallery = Button.builder(Row.builder(
                                Icon.builder(Icons.GRID)
                                        .size(18.0f, 18.0f)
                                        .tint(new SolidPaint(Color.rgb(0xe7faff)))
                                        .build(),
                                Text.of("浏览全部图标 · Icon gallery", BODY))
                        .gap(7.0f)
                        .crossAxisAlignment(CrossAxisAlignment.CENTER)
                        .build())
                .key(ICON_GALLERY_KEY)
                .style(FandUiTestScreen::buttonStyle)
                .onClick(openIconGallery)
                .build();

        Icon preset = Icon.builder(Icons.CHECK)
                .size(30.0f, 30.0f)
                .tint(new SolidPaint(Color.rgb(0x8ee7cf)))
                .build();
        SvgIcon inline = SvgIcon.builder(INLINE_SVG)
                .key(SVG_KEY)
                .size(54.0f, 54.0f)
                .build();
        Image resource = Image.builder(resourceImage)
                .key(RESOURCE_IMAGE_KEY)
                .size(128.0f, 72.0f)
                .fit(ImageFit.CONTAIN)
                .build();
        Box clippedResource = Box.builder(resource)
                .style(IMAGE_CLIP_STYLE)
                .build();

        Column controls = Column.builder(
                        rowLabel("Checkbox", checkbox),
                        rowLabel("ToggleSwitch", toggle),
                        Text.of("Slider", MUTED),
                        slider,
                        progress,
                        Text.of("Dropdown", MUTED),
                        dropdown)
                .gap(5.0f)
                .crossAxisAlignment(CrossAxisAlignment.STRETCH)
                .build();
        Box controlsSection = Box.builder(controls)
                .style(SECTION_STYLE)
                .build();

        Column visuals = Column.builder(
                        Text.of("矢量与纹理 · Vector + resource texture", MUTED),
                        Row.builder(preset, inline, clippedResource)
                                .gap(18.0f)
                                .crossAxisAlignment(CrossAxisAlignment.CENTER)
                                .build())
                .gap(7.0f)
                .crossAxisAlignment(CrossAxisAlignment.STRETCH)
                .build();
        Box visualsSection = Box.builder(visuals)
                .style(SECTION_STYLE)
                .build();

        Column logContent = Column.builder(
                        logLine("滚动容器：嵌套裁剪、滚轮和拖拽", MUTED),
                        logLine("中文 fallback · English shaping · 日本語 · 한국어 · 🎮", BODY),
                        logLine("圆角背景 / border / linear gradient / backdrop blur", BODY),
                        logLine("资源 reload 后 SVG 会重新栅格化并保留 premultiplied RGBA8。", BODY),
                        logLine("Resize、GUI Scale 和非整数缩放由 viewport 自动重建。", BODY),
                        logLine("最后一行用于检查内容裁剪和滚动边界。", BODY))
                .gap(7.0f)
                .crossAxisAlignment(CrossAxisAlignment.STRETCH)
                .build();
        ScrollController scrollController = ScrollController.create();
        ScrollContainer scroll = ScrollContainer.builder(logContent)
                .key(SCROLL_KEY)
                .controller(scrollController)
                .style(SCROLL_STYLE)
                .build();

        Column content = Column.builder(
                        Row.builder(
                                        Icon.builder(Icons.INFO).size(28.0f, 28.0f)
                                                .tint(new SolidPaint(Color.rgb(0x8edcff))).build(),
                                        Text.of("FandUI 综合测试面板 · Test UI", TITLE))
                                .gap(9.0f)
                                .crossAxisAlignment(CrossAxisAlignment.CENTER)
                                .build(),
                        status,
                        controlsSection,
                        visualsSection,
                        input,
                        run,
                        iconGallery,
                        Text.of("F9 重开 · Esc 关闭 · Resize/GUI Scale 可直接测试", MUTED),
                        fixedHeight(scroll, 142.0f))
                .gap(9.0f)
                .crossAxisAlignment(CrossAxisAlignment.STRETCH)
                .build();
        ScrollController pageScrollController = ScrollController.create();
        ScrollContainer pageScroll = ScrollContainer.builder(content)
                .key(PAGE_SCROLL_KEY)
                .controller(pageScrollController)
                .build();
        Box panel = Box.builder(pageScroll)
                .key(PANEL_KEY)
                .alignment(Alignment.TOP_LEFT)
                .style(PANEL_STYLE)
                .build();
        UiComponent responsivePanel = ConstrainedBox.builder(
                        panel,
                        Constraints.loose(PANEL_MAX_WIDTH, PANEL_MAX_HEIGHT))
                .alignment(Alignment.CENTER)
                .build();
        Box root = Box.builder(responsivePanel)
                .key(ROOT_KEY)
                .alignment(Alignment.CENTER)
                .style(Style.builder().padding(10.0f).build())
                .build();
        UiScreen screen = UiScreen.builder("FandUI Test UI", root)
                .pausesGame(false)
                .background(ScreenBackground.DEFAULT)
                .build();
        return new Definition(screen, status, run, iconGallery, checkbox, toggle, slider, dropdown,
                progress, input, pageScroll, pageScrollController, scroll, scrollController,
                inline, resource, textController);
    }

    static IconGalleryDefinition createIconGalleryDefinition(Runnable openMain) {
        Objects.requireNonNull(openMain, "openMain");

        List<Icon> previews = new ArrayList<>(Icons.all().size());
        Column.Builder rows = Column.builder()
                .gap(6.0f)
                .crossAxisAlignment(CrossAxisAlignment.STRETCH);
        Icons.all().forEach((name, definition) -> {
            Icon preview = Icon.builder(definition)
                    .key(key("test-ui/icon-gallery/icon/" + name.toLowerCase(Locale.ROOT)))
                    .size(30.0f, 30.0f)
                    .tint(new SolidPaint(Color.rgb(0x8ee7ff)))
                    .build();
            previews.add(preview);
            Column label = Column.builder(
                            Text.of(name, BODY),
                            Text.of(definition.layers().size() + " vector layer(s)", MUTED))
                    .gap(2.0f)
                    .build();
            rows.child(Box.builder(Row.builder(preview, label)
                            .gap(12.0f)
                            .crossAxisAlignment(CrossAxisAlignment.CENTER)
                            .build())
                    .alignment(Alignment.CENTER_LEFT)
                    .style(ICON_ROW_STYLE)
                    .build());
        });

        Button back = Button.builder(Row.builder(
                                Icon.builder(Icons.ARROW_LEFT)
                                        .size(17.0f, 17.0f)
                                        .tint(new SolidPaint(Color.rgb(0xe7faff)))
                                        .build(),
                                Text.of("返回 · Back", BODY))
                        .gap(6.0f)
                        .crossAxisAlignment(CrossAxisAlignment.CENTER)
                        .build())
                .key(ICON_GALLERY_BACK_KEY)
                .style(FandUiTestScreen::buttonStyle)
                .onClick(openMain)
                .build();
        Column content = Column.builder(
                        back,
                        Row.builder(
                                        Icon.builder(Icons.GRID)
                                                .size(26.0f, 26.0f)
                                                .tint(new SolidPaint(Color.rgb(0x8edcff)))
                                                .build(),
                                        Text.of("预设图标 · Preset Icons", TITLE))
                                .gap(9.0f)
                                .crossAxisAlignment(CrossAxisAlignment.CENTER)
                                .build(),
                        Text.of(Icons.all().size() + " 个具名预设，按声明顺序显示", MUTED),
                        rows.build())
                .gap(10.0f)
                .crossAxisAlignment(CrossAxisAlignment.STRETCH)
                .build();
        ScrollController scrollController = ScrollController.create();
        ScrollContainer scroll = ScrollContainer.builder(content)
                .key(ICON_GALLERY_SCROLL_KEY)
                .controller(scrollController)
                .build();
        Box panel = Box.builder(scroll)
                .key(ICON_GALLERY_PANEL_KEY)
                .alignment(Alignment.TOP_LEFT)
                .style(PANEL_STYLE)
                .build();
        UiComponent responsivePanel = ConstrainedBox.builder(
                        panel,
                        new Constraints(360.0f, PANEL_MAX_WIDTH, 0.0f, PANEL_MAX_HEIGHT))
                .alignment(Alignment.CENTER)
                .build();
        Box root = Box.builder(responsivePanel)
                .key(ICON_GALLERY_ROOT_KEY)
                .alignment(Alignment.CENTER)
                .style(Style.builder().padding(10.0f).build())
                .build();
        UiScreen screen = UiScreen.builder("FandUI Icon Gallery", root)
                .pausesGame(false)
                .background(ScreenBackground.DEFAULT)
                .build();
        return new IconGalleryDefinition(screen, back, scroll, scrollController, previews);
    }

    private static UiComponent rowLabel(String label, UiComponent control) {
        return Row.builder(Text.of(label, MUTED), control)
                .gap(8.0f)
                .crossAxisAlignment(CrossAxisAlignment.CENTER)
                .build();
    }

    private static String checkboxLabel(boolean checked, String label) {
        return label + (checked ? "：ON" : "：OFF") + " · Checkbox";
    }

    private static UiComponent fixedHeight(UiComponent child, float height) {
        return ConstrainedBox.builder(
                        child,
                        new Constraints(0.0f, Float.POSITIVE_INFINITY, height, height))
                .alignment(Alignment.TOP_LEFT)
                .build();
    }

    private static UiComponent logLine(String value, TextStyle style) {
        return fixedHeight(Text.of(value, style), 28.0f);
    }

    private static Style buttonStyle(Theme theme, VisualState state) {
        Color background = state.disabled()
                ? Color.rgb(0x34434a).withAlpha(0.64f)
                : state.pressed()
                        ? Color.rgb(0x147b9e)
                        : state.hovered() ? Color.rgb(0x35bce9) : Color.rgb(0x209fd0);
        return Style.builder()
                .padding(10.0f, 6.0f)
                .background(background)
                .border(1.0f, Color.rgb(0xb9efff).withAlpha(0.72f))
                .cornerRadius(5.0f)
                .build();
    }

    private static UiKey key(String value) {
        return UiKey.of("fandui", value);
    }

    record Definition(
            UiScreen screen,
            Text status,
            Button run,
            Button iconGallery,
            Checkbox checkbox,
            ToggleSwitch toggle,
            Slider slider,
            Dropdown<String> dropdown,
            ProgressIndicator progress,
            TextInput input,
            ScrollContainer pageScroll,
            ScrollController pageScrollController,
            ScrollContainer scroll,
            ScrollController scrollController,
            SvgIcon inlineSvg,
            Image resourceImage,
            TextController textController) {
        Definition {
            Objects.requireNonNull(screen, "screen");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(iconGallery, "iconGallery");
            Objects.requireNonNull(checkbox, "checkbox");
            Objects.requireNonNull(toggle, "toggle");
            Objects.requireNonNull(slider, "slider");
            Objects.requireNonNull(dropdown, "dropdown");
            Objects.requireNonNull(progress, "progress");
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(pageScroll, "pageScroll");
            Objects.requireNonNull(pageScrollController, "pageScrollController");
            Objects.requireNonNull(scroll, "scroll");
            Objects.requireNonNull(scrollController, "scrollController");
            Objects.requireNonNull(inlineSvg, "inlineSvg");
            Objects.requireNonNull(resourceImage, "resourceImage");
            Objects.requireNonNull(textController, "textController");
        }
    }

    record IconGalleryDefinition(
            UiScreen screen,
            Button back,
            ScrollContainer scroll,
            ScrollController scrollController,
            List<Icon> icons) {
        IconGalleryDefinition {
            Objects.requireNonNull(screen, "screen");
            Objects.requireNonNull(back, "back");
            Objects.requireNonNull(scroll, "scroll");
            Objects.requireNonNull(scrollController, "scrollController");
            icons = List.copyOf(icons);
        }
    }
}
