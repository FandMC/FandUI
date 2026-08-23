package cn.fandmc.fandui.core.runtime;

public interface UiThreadDispatcher {
    boolean isUiThread();

    void execute(Runnable action);
}
