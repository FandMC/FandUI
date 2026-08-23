package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.hud.HudLayer;
import cn.fandmc.fandui.api.hud.HudRegistration;
import cn.fandmc.fandui.api.hud.HudInputMode;
import cn.fandmc.fandui.api.event.UiEvent;

import java.util.Objects;
import java.util.function.Consumer;

final class CoreHudRegistration extends AbstractCoreSession implements HudRegistration {
    private final HudLayer layer;
    private final Consumer<CoreHudRegistration> closeCallback;

    CoreHudRegistration(
            CoreUiRuntime runtime,
            HudLayer layer,
            Consumer<CoreHudRegistration> closeCallback) {
        super(runtime, layer.root(), layer.theme(), layer.layoutDirection());
        this.layer = Objects.requireNonNull(layer, "layer");
        this.closeCallback = Objects.requireNonNull(closeCallback, "closeCallback");
        initialize();
    }

    @Override
    public UiKey key() {
        return layer.key();
    }

    @Override
    public HudLayer layer() {
        return layer;
    }

    @Override
    protected boolean notifyHostOnApiClose() {
        return false;
    }

    @Override
    protected boolean acceptsInput(UiEvent event) {
        return layer.inputMode() == HudInputMode.INTERACTIVE;
    }

    @Override
    protected void hostCloseRequested() {
    }

    @Override
    protected void detachedFromRuntime() {
        closeCallback.accept(this);
    }
}
