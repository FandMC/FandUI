package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.input.CursorShape;

/** Platform bridge that owns native cursor handles and applies stable cursor shapes. */
public interface CursorHost extends AutoCloseable {
    void setCursor(CursorShape shape);

    @Override
    void close();

    static CursorHost noOp() {
        return new CursorHost() {
            @Override
            public void setCursor(CursorShape shape) {
            }

            @Override
            public void close() {
            }
        };
    }
}
