package cn.fandmc.fandui.api.resource;

import cn.fandmc.fandui.api.UiKey;

/** Idempotent ownership handle for one registered image or font source. */
public interface ResourceRegistration extends AutoCloseable {
    UiKey key();

    ResourceKind kind();

    boolean active();

    /** Removes the source from future reload candidates without invalidating existing handles. */
    @Override
    void close();
}
