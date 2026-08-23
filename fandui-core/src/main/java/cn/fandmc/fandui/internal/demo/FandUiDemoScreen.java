package cn.fandmc.fandui.internal.demo;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.component.Box;
import cn.fandmc.fandui.api.component.Button;
import cn.fandmc.fandui.api.component.Column;
import cn.fandmc.fandui.api.component.ConstrainedBox;
import cn.fandmc.fandui.api.component.Image;
import cn.fandmc.fandui.api.component.ImageFit;
import cn.fandmc.fandui.api.component.Row;
import cn.fandmc.fandui.api.component.ScrollContainer;
import cn.fandmc.fandui.api.component.Text;
import cn.fandmc.fandui.api.component.TextInput;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.control.ScrollController;
import cn.fandmc.fandui.api.component.control.TextController;
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
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.style.VisualState;
import cn.fandmc.fandui.api.text.FontWeight;
import cn.fandmc.fandui.api.text.TextStyle;
import cn.fandmc.fandui.core.runtime.CoreUiRuntime;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Property-gated integration fixture shared by all Minecraft version bridges. */
public final class FandUiDemoScreen {
    public static final String ENABLE_PROPERTY = "fandui.demo.screen";

    static final UiKey ROOT_KEY = key("demo/screen/root");
    static final UiKey PANEL_KEY = key("demo/screen/panel");
    static final UiKey STATUS_KEY = key("demo/screen/status");
    static final UiKey ACTION_KEY = key("demo/screen/action");
    static final UiKey INPUT_KEY = key("demo/screen/input");
    static final UiKey SCROLL_KEY = key("demo/screen/scroll");
    static final UiKey ARTWORK_COMPONENT_KEY = key("demo/screen/artwork_component");
    static final UiKey ARTWORK_KEY = key("demo/artwork.png");

    private static final float PANEL_WIDTH = 620.0f;
    private static final float PANEL_HEIGHT = 430.0f;
    private static final float ROOT_INSET = 12.0f;

    private static final TextStyle TITLE_TEXT = TextStyle.builder(22.0f)
            .weight(FontWeight.BOLD)
            .color(Color.rgb(0xf4fbff))
            .locale("zh-CN")
            .build();
    private static final TextStyle BODY_TEXT = TextStyle.builder(15.0f)
            .color(Color.rgb(0xddebf2))
            .locale("zh-CN")
            .build();
    private static final TextStyle MUTED_TEXT = TextStyle.builder(13.0f)
            .color(Color.rgb(0x9fb7c3))
            .locale("zh-CN")
            .build();
    private static final Style PANEL_STYLE = Style.builder()
            .padding(18.0f)
            .background(Color.rgb(0x102737).withAlpha(0.62f))
            .border(1.0f, Color.rgb(0x72d9ff).withAlpha(0.72f))
            .cornerRadius(12.0f)
            .backdropBlur(18.0f)
            .clip(ClipMode.ROUNDED_BOUNDS)
            .build();
    private static final Style SCROLL_STYLE = Style.builder()
            .padding(8.0f)
            .background(Color.rgb(0x07141d).withAlpha(0.48f))
            .border(1.0f, Color.rgb(0x6b8794).withAlpha(0.45f))
            .cornerRadius(8.0f)
            .clip(ClipMode.ROUNDED_BOUNDS)
            .build();
    private static final Style CARD_STYLE = Style.builder()
            .padding(10.0f, 7.0f)
            .background(new LinearGradient(
                    new Point(0.0f, 0.0f),
                    new Point(360.0f, 42.0f),
                    List.of(
                            GradientStop.at(0.0f, Color.rgb(0x16364a).withAlpha(0.86f)),
                            GradientStop.at(1.0f, Color.rgb(0x183d38).withAlpha(0.76f)))))
            .border(1.0f, Color.rgb(0x5b91a6).withAlpha(0.36f))
            .cornerRadius(6.0f)
            .clip(ClipMode.ROUNDED_BOUNDS)
            .build();
    private static final Style ARTWORK_STYLE = Style.builder()
            .background(Color.rgb(0x102737))
            .border(1.0f, Color.rgb(0x8ee7cf).withAlpha(0.62f))
            .cornerRadius(8.0f)
            .clip(ClipMode.ROUNDED_BOUNDS)
            .build();

    private static final String ARTWORK_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAABgAAAAQCAYAAAAMJL+VAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUA"
                    + "AAAJcEhZcwAADsMAAA7DAcdvqGQAAAB+SURBVDhP7Y3BDYAgEARpwB6swCYowyJ9WYUdWIf/M5CcWczKhXD8fExC"
                    + "2N2bMG8iI/kFBXLtGfzrFkxLfN7ugnRcYXmiKqgN8bjCep8Ca4g58u5RgTVkuYK9RFjPQxA2UqxcwXtNghb0XiFg"
                    + "xR4KASt4kAUs8CKwT08GC6LcNxtRkDYoetUAAAAASUVORK5CYII=";

    private final CoreUiRuntime runtime;
    private boolean initialOpenPending = true;

    private FandUiDemoScreen(CoreUiRuntime runtime) {
        this.runtime = runtime;
    }

    public static Optional<FandUiDemoScreen> installIfEnabled(CoreUiRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || !runtime.availability().available()) {
            return Optional.empty();
        }
        byte[] artwork = Base64.getDecoder().decode(ARTWORK_PNG_BASE64);
        runtime.resources().registerImage(
                ARTWORK_KEY,
                ResourceSource.bytes(artwork));
        return Optional.of(new FandUiDemoScreen(runtime));
    }

    /** Opens once on the first tick, then only after an explicit development-key request. */
    public boolean openIfRequested(boolean requested) {
        boolean shouldOpen = initialOpenPending || requested;
        initialOpenPending = false;
        if (!shouldOpen) {
            return false;
        }
        open();
        return true;
    }

    /** Creates a fresh component tree so the development key can reopen the fixture. */
    public ScreenSession open() {
        ImageRef artwork = runtime.resources().image(ARTWORK_KEY);
        return runtime.screens().open(createDefinition(artwork).screen());
    }

    static Definition createDefinition(ImageRef artwork) {
        Objects.requireNonNull(artwork, "artwork");
        Text status = Text.builder("等待操作 · Idle", MUTED_TEXT)
                .key(STATUS_KEY)
                .build();
        TextController textController = TextController.create("蔚蓝群岛");
        TextInput input = TextInput.builder(textController, BODY_TEXT)
                .key(INPUT_KEY)
                .onSubmit(value -> status.setText("世界名称：" + value))
                .build();

        int[] clickCount = {0};
        Button action = Button.builder(Text.of("加入世界 / Join", BODY_TEXT))
                .key(ACTION_KEY)
                .style(FandUiDemoScreen::buttonStyle)
                .onClick(() -> {
                    clickCount[0]++;
                    status.setText("已加入 " + clickCount[0] + " 次 · Joined " + clickCount[0]);
                })
                .build();

        Row actions = Row.builder(action, status)
                .gap(12.0f)
                .crossAxisAlignment(CrossAxisAlignment.CENTER)
                .build();

        Image artworkImage = Image.builder(artwork)
                .key(ARTWORK_COMPONENT_KEY)
                .fit(ImageFit.COVER)
                .build();
        Box artworkCard = Box.builder(artworkImage)
                .alignment(Alignment.CENTER)
                .style(ARTWORK_STYLE)
                .build();

        Column scrollContent = Column.builder()
                .gap(8.0f)
                .crossAxisAlignment(CrossAxisAlignment.STRETCH)
                .child(fixedHeight(artworkCard, 96.0f))
                .child(messageCard("晨光穿过白桦林，河面泛起微光。"))
                .child(messageCard("Build worlds that feel immediate and alive."))
                .child(messageCard("静かな夜、星がゆっくり流れる。"))
                .child(messageCard("Bonsoir · Le ciel est clair ce soir."))
                .child(messageCard("今日的队伍：Alex · Steve · 🎮"))
                .child(messageCard("在线成员 24 · Friends online 24"))
                .child(messageCard("远山与海风 · Mountains and sea breeze"))
                .child(messageCard("下一站：青空群系 ✨"))
                .build();
        ScrollController scrollController = ScrollController.create();
        ScrollContainer scroll = ScrollContainer.builder(scrollContent)
                .key(SCROLL_KEY)
                .controller(scrollController)
                .style(SCROLL_STYLE)
                .build();

        Column content = Column.builder(
                        Text.of("FandUI", TITLE_TEXT),
                        Text.of("你好，世界 · Hello, Minecraft · こんにちは ✨", BODY_TEXT),
                        input,
                        actions,
                        scroll)
                .gap(10.0f)
                .crossAxisAlignment(CrossAxisAlignment.STRETCH)
                .build();
        Box panel = Box.builder(content)
                .key(PANEL_KEY)
                .alignment(Alignment.TOP_LEFT)
                .style(PANEL_STYLE)
                .build();
        UiComponent responsivePanel = ConstrainedBox.builder(
                        panel,
                        Constraints.loose(PANEL_WIDTH, PANEL_HEIGHT))
                .alignment(Alignment.CENTER)
                .build();
        Box root = Box.builder(responsivePanel)
                .key(ROOT_KEY)
                .alignment(Alignment.CENTER)
                .style(Style.builder().padding(ROOT_INSET).build())
                .build();
        UiScreen screen = UiScreen.builder("FandUI", root)
                .pausesGame(false)
                .background(ScreenBackground.DEFAULT)
                .build();
        return new Definition(
                screen,
                status,
                action,
                input,
                textController,
                scroll,
                scrollController,
                artworkImage);
    }

    private static UiComponent messageCard(String message) {
        return fixedHeight(
                Box.builder(Text.of(message, MUTED_TEXT))
                        .alignment(Alignment.CENTER_LEFT)
                        .style(CARD_STYLE)
                        .build(),
                42.0f);
    }

    private static UiComponent fixedHeight(UiComponent child, float height) {
        return ConstrainedBox.builder(
                        child,
                        new Constraints(0.0f, Float.POSITIVE_INFINITY, height, height))
                .alignment(Alignment.CENTER_LEFT)
                .build();
    }

    private static Style buttonStyle(Theme theme, VisualState state) {
        Color background = state.disabled()
                ? Color.rgb(0x31414a).withAlpha(0.62f)
                : state.pressed()
                        ? Color.rgb(0x1589bd)
                        : state.hovered() ? Color.rgb(0x35bdf2) : Color.rgb(0x20afff);
        return Style.builder()
                .padding(13.0f, 7.0f)
                .background(background)
                .border(1.0f, Color.rgb(0xb9efff).withAlpha(0.72f))
                .cornerRadius(6.0f)
                .build();
    }

    private static UiKey key(String value) {
        return UiKey.of("fandui", value);
    }

    record Definition(
            UiScreen screen,
            Text status,
            Button action,
            TextInput input,
            TextController textController,
            ScrollContainer scroll,
            ScrollController scrollController,
            Image artwork) {
        Definition {
            Objects.requireNonNull(screen, "screen");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(textController, "textController");
            Objects.requireNonNull(scroll, "scroll");
            Objects.requireNonNull(scrollController, "scrollController");
            Objects.requireNonNull(artwork, "artwork");
        }
    }
}
