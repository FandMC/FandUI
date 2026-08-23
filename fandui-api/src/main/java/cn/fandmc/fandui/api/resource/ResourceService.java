package cn.fandmc.fandui.api.resource;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.text.FontFamily;

/**
 * Registry and stable-handle service for images and fonts.
 *
 * <p>{@link #generation()}, image handle state, information, and failures are safe to
 * read from any thread. Registration, listener registration, and reload require the UI
 * thread. A reload decodes on a worker but blocks the caller until the complete candidate
 * is atomically published or rejected; a rejected candidate preserves the previous ready
 * generation and throws a runtime resource-reload failure.</p>
 */
public interface ResourceService {
    long generation();

    ImageRef image(UiKey key);

    FontFamily font(UiKey key);

    /** Registers an image source for subsequent reloads and returns its ownership handle. */
    ResourceRegistration registerImage(UiKey key, ResourceSource source);

    /** Registers a font source for subsequent reloads and returns its ownership handle. */
    ResourceRegistration registerFont(UiKey key, ResourceSource source);

    /**
     * Reloads all current registrations using the most recent platform resource lookup.
     * Runtime registrations therefore become resolvable without a manual lookup adapter.
     *
     * @return the newly published generation
     * @throws RuntimeException if any required source or decode step rejects the candidate
     */
    long reload();

    /** Registers an atomic-generation publication listener. */
    EventRegistration onReload(ResourceReloadListener listener);
}
