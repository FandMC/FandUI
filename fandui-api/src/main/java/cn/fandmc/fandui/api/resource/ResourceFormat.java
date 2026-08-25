package cn.fandmc.fandui.api.resource;

/** Explicit encoding hint for byte-backed image resources. */
public enum ResourceFormat {
    /** Detect the encoding from the resource signature. */
    AUTO,
    /** Portable Network Graphics. */
    PNG,
    /** UTF-8 SVG document using FandUI's bounded vector subset. */
    SVG
}
