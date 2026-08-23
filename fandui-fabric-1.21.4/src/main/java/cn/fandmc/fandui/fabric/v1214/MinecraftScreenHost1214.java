package cn.fandmc.fandui.fabric.v1214;

import cn.fandmc.fandui.core.runtime.CoreScreenSession;
import cn.fandmc.fandui.core.runtime.ScreenHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Objects;
import java.util.function.LongSupplier;

final class MinecraftScreenHost1214 implements ScreenHost {
    private final LongSupplier clock;
    private Screen parent;

    MinecraftScreenHost1214(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void open(CoreScreenSession session) {
        Objects.requireNonNull(session, "session");
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof FandUiScreen1214)) {
            parent = minecraft.screen;
        }
        minecraft.setScreen(new FandUiScreen1214(session, clock));
    }

    @Override
    public void close(CoreScreenSession session) {
        Objects.requireNonNull(session, "session");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FandUiScreen1214 screen && screen.session() == session) {
            Screen target = parent;
            parent = null;
            minecraft.setScreen(target);
        }
    }
}
