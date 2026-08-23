package cn.fandmc.fandui.fabric.v1201;

import cn.fandmc.fandui.core.runtime.CoreScreenSession;
import cn.fandmc.fandui.core.runtime.ScreenHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Objects;
import java.util.function.LongSupplier;

final class MinecraftScreenHost1201 implements ScreenHost {
    private final LongSupplier clock;
    private Screen parent;

    MinecraftScreenHost1201(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void open(CoreScreenSession session) {
        Objects.requireNonNull(session, "session");
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof FandUiScreen1201)) {
            parent = minecraft.screen;
        }
        minecraft.setScreen(new FandUiScreen1201(session, clock));
    }

    @Override
    public void close(CoreScreenSession session) {
        Objects.requireNonNull(session, "session");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FandUiScreen1201 screen && screen.session() == session) {
            Screen target = parent;
            parent = null;
            minecraft.setScreen(target);
        }
    }
}
