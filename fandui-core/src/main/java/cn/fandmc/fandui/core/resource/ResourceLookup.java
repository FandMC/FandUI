package cn.fandmc.fandui.core.resource;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.resource.ResourceKind;
import cn.fandmc.fandui.api.resource.ResourceSource;

import java.util.Optional;

/**
 * Version-bridge lookup for the resource pack snapshot that is active during a reload.
 */
@FunctionalInterface
public interface ResourceLookup {
    Optional<ResourceSource> find(ResourceKind kind, UiKey key);

    static ResourceLookup empty() {
        return (kind, key) -> Optional.empty();
    }
}
