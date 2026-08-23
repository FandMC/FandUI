package cn.fandmc.fandui.text;

/** Pixel formats produced by the CPU text rasterizer. */
public enum TextPixelFormat {
    ALPHA_8(1),
    RGBA_8888_PREMULTIPLIED(4);

    private final int bytesPerPixel;

    TextPixelFormat(int bytesPerPixel) {
        this.bytesPerPixel = bytesPerPixel;
    }

    public int bytesPerPixel() {
        return bytesPerPixel;
    }
}
