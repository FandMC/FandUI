package cn.fandmc.fandui.api.resource;

/** UI-thread callback invoked after an atomic resource generation is published. */
@FunctionalInterface
public interface ResourceReloadListener {
    void reloaded(long oldGeneration, long newGeneration);
}
