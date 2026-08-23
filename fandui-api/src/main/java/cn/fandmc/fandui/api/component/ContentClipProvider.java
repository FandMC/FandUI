package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.style.ClipMode;

/**
 * Declares the minimum clip a component requires to preserve its own semantics.
 *
 * <p>The resolved style may request a stronger clip. Returning {@link ClipMode#NONE}
 * leaves clipping entirely to the style.</p>
 */
public interface ContentClipProvider {
    ClipMode contentClip();
}
