package cn.fandmc.fandui.core.runtime;

public interface ScreenHost {
    void open(CoreScreenSession session);

    void close(CoreScreenSession session);
}
