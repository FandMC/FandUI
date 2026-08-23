package cn.fandmc.fandui.api.event;

/** Idempotent ownership handle for a registered event or change listener. */
public interface EventRegistration extends AutoCloseable {
    /** Returns whether the listener may still be invoked. */
    boolean active();

    /** Unregisters the listener; repeated close calls have no additional effect. */
    @Override
    void close();
}
