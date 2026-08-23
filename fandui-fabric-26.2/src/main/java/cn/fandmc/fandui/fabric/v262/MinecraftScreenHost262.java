package cn.fandmc.fandui.fabric.v262;

import cn.fandmc.fandui.core.runtime.CoreScreenSession;
import cn.fandmc.fandui.core.runtime.ScreenHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Objects;
import java.util.function.LongSupplier;

final class MinecraftScreenHost262 implements ScreenHost {
    private final LongSupplier clock;
    private Screen parent;

    MinecraftScreenHost262(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void open(CoreScreenSession session) {
        Objects.requireNonNull(session, "session");
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof FandUiScreen262)) {
            parent = minecraft.gui.screen();
        }
        minecraft.gui.setScreen(new FandUiScreen262(session, clock));
    }

    @Override
    public void close(CoreScreenSession session) {
        Objects.requireNonNull(session, "session");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof FandUiScreen262 screen && screen.session() == session) {
            Screen target = parent;
            parent = null;
            minecraft.gui.setScreen(target);
        }
    }
}
