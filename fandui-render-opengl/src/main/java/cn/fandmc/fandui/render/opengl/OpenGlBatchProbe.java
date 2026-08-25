package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.canvas.CanvasState;
import cn.fandmc.fandui.api.canvas.ImageSampling;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.PathWinding;
import cn.fandmc.fandui.api.canvas.StrokeStyle;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.resource.ImageInfo;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceState;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.api.style.GradientStop;
import cn.fandmc.fandui.api.style.LinearGradient;
import cn.fandmc.fandui.api.style.SolidPaint;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextLine;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextStyle;
import cn.fandmc.fandui.canvas.DisplayList;
import cn.fandmc.fandui.canvas.RecordingCanvas2D;
import cn.fandmc.fandui.core.resource.ImageRaster;
import cn.fandmc.fandui.render.opengl.internal.GlStateSnapshot;
import cn.fandmc.fandui.text.TextPixelFormat;
import cn.fandmc.fandui.text.TextRaster;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;

/** Explicit end-to-end DisplayList/LWJGL NanoVGGL3/OpenGL diagnostic. */
public final class OpenGlBatchProbe implements AutoCloseable {
    public static final String ENABLE_PROPERTY = "fandui.openglBatchProbe";

    private static final SolidPaint BACKGROUND = solid(0.04f, 0.10f, 0.16f, 1.0f);
    private static final SolidPaint RED = solid(0.92f, 0.08f, 0.06f, 1.0f);
    private static final SolidPaint YELLOW = solid(0.98f, 0.78f, 0.08f, 1.0f);
    private static final SolidPaint WHITE = solid(1.0f, 1.0f, 1.0f, 1.0f);
    private static final SolidPaint BLACK = solid(0.0f, 0.0f, 0.0f, 1.0f);
    private static final SolidPaint BLUR_RED = solid(1.0f, 0.0f, 0.0f, 1.0f);
    private static final SolidPaint BLUR_BLUE = solid(0.0f, 0.0f, 1.0f, 1.0f);
    private static final long ALPHA_TEXTURE_KEY = 0x0A11_FA00_0000_0001L;
    private static final long RGBA_TEXTURE_KEY = 0x0A11_FA00_0000_0002L;
    private static final long IMAGE_TEXTURE_KEY = 0x0A11_FA00_0000_0003L;
    private static final Color ALPHA_TINT = Color.rgb(0x28D7F4);
    private static final TextLayout ALPHA_LAYOUT = probeLayout("alpha");
    private static final TextLayout RGBA_LAYOUT = probeLayout("rgba");
    private static final List<TextRaster> TEXT_RASTERS = List.of(
            alphaRaster(),
            rgbaRaster());
    private static final ImageRef IMAGE_REF = new ProbeImageRef(
            UiKey.of("fandui", "diagnostic/image.png"),
            new ImageInfo(16, 16));
    private static final ImageRaster IMAGE_RASTER = imageRaster();
    private static final DisplayList DISPLAY_LIST = displayList();

    private final boolean enabled;
    private final NanoVgGl3Renderer renderer;
    private final OpenGlTextTextureCache textTextures;
    private final OpenGlImageTextureCache imageTextures;
    private TargetKey renderedTarget;
    private OpenGlFrameInfo frameInfo;

    public OpenGlBatchProbe() {
        this(Boolean.getBoolean(ENABLE_PROPERTY));
    }

    OpenGlBatchProbe(boolean enabled) {
        this.enabled = enabled;
        this.renderer = new NanoVgGl3Renderer();
        this.textTextures = new OpenGlTextTextureCache();
        this.imageTextures = new OpenGlImageTextureCache();
    }

    public boolean enabled() {
        return enabled;
    }

    public OpenGlRenderReport render(RenderHost host) {
        Objects.requireNonNull(host, "host");
        if (!enabled) {
            return new OpenGlRenderReport(
                    OpenGlRenderReport.Status.NO_TARGET,
                    host.name(),
                    0,
                    0,
                    0,
                    0,
                    0);
        }
        host.assertRenderThread();
        Optional<OpenGlTarget> optionalTarget = host.currentTarget();
        if (optionalTarget.isEmpty()) {
            return new OpenGlRenderReport(
                    OpenGlRenderReport.Status.NO_TARGET,
                    host.name(),
                    0,
                    0,
                    0,
                    0,
                    0);
        }

        OpenGlTarget target = optionalTarget.orElseThrow();
        TargetKey nextTarget = TargetKey.from(target);
        boolean firstRenderForTarget = !nextTarget.equals(renderedTarget);
        if (firstRenderForTarget) {
            frameInfo = new OpenGlFrameInfo(target.width(), target.height(), 1.0f);
        }
        OpenGlPassScope pass = OpenGlPassScope.open(host);
        try {
            textTextures.activate(TEXT_RASTERS);
            imageTextures.activate(List.of(IMAGE_RASTER));
            OpenGlRenderReport report = renderer.render(
                    host,
                    frameInfo,
                    DISPLAY_LIST,
                    renderResources());
            renderedTarget = nextTarget;
            if (firstRenderForTarget) {
                verifyPixels(report, target);
            }
            return report;
        } finally {
            pass.close();
        }
    }

    private NanoVgRenderResources renderResources() {
        return new NanoVgRenderResources() {
            @Override
            public Image resolveImage(ImageRef image, ImageSampling sampling) {
                if (image != IMAGE_REF) {
                    throw new OpenGlRenderException("Unknown NanoVG probe image");
                }
                OpenGlSampling openGlSampling = sampling == ImageSampling.NEAREST
                        ? OpenGlSampling.NEAREST
                        : OpenGlSampling.LINEAR;
                OpenGlTexture texture = imageTextures.resolve(IMAGE_TEXTURE_KEY, openGlSampling)
                        .orElseThrow(() -> new OpenGlRenderException("Probe image texture is unavailable"));
                return new Image(IMAGE_TEXTURE_KEY, texture, 16, 16);
            }

            @Override
            public Text resolveText(TextLayout layout) {
                TextRaster raster;
                if (layout == ALPHA_LAYOUT) {
                    raster = TEXT_RASTERS.get(0);
                } else if (layout == RGBA_LAYOUT) {
                    raster = TEXT_RASTERS.get(1);
                } else {
                    throw new OpenGlRenderException("Unknown NanoVG probe text layout");
                }
                Optional<OpenGlTexture> texture = textTextures.resolve(
                        raster.textureKey(),
                        OpenGlSampling.LINEAR);
                return new Text(raster, texture);
            }
        };
    }

    private static DisplayList displayList() {
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        canvas.fillRoundedRect(
                new Rect(8.0f, 8.0f, 248.0f, 168.0f),
                uniformRadii(8.0f),
                BACKGROUND);
        canvas.fillRoundedRect(
                new Rect(16.0f, 16.0f, 232.0f, 40.0f),
                uniformRadii(6.0f),
                new LinearGradient(
                        new Point(16.0f, 16.0f),
                        new Point(248.0f, 16.0f),
                        List.of(
                                new GradientStop(0.0f, new Color(0.95f, 0.16f, 0.12f, 1.0f)),
                                new GradientStop(0.5f, new Color(0.08f, 0.78f, 0.38f, 1.0f)),
                                new GradientStop(1.0f, new Color(0.10f, 0.38f, 0.95f, 1.0f)))));

        CanvasState outer = canvas.save();
        canvas.clip(Path.builder().rect(new Rect(16.0f, 64.0f, 150.0f, 96.0f)).build());
        CanvasState nested = canvas.save();
        canvas.clip(Path.builder()
                .rect(new Rect(24.0f, 72.0f, 130.0f, 80.0f))
                .rect(new Rect(72.0f, 96.0f, 38.0f, 28.0f))
                .winding(PathWinding.HOLE)
                .build());
        canvas.fillRect(new Rect(16.0f, 64.0f, 150.0f, 96.0f), RED);
        nested.close();
        canvas.stroke(
                Path.builder().rect(new Rect(20.0f, 68.0f, 142.0f, 88.0f)).build(),
                WHITE,
                StrokeStyle.width(3.0f).build());
        outer.close();

        canvas.fillRoundedRect(
                new Rect(184.0f, 72.0f, 56.0f, 32.0f),
                uniformRadii(5.0f),
                YELLOW);
        canvas.fill(
                Path.builder()
                        .rect(new Rect(184.0f, 116.0f, 56.0f, 44.0f))
                        .rect(new Rect(200.0f, 128.0f, 24.0f, 20.0f))
                        .winding(PathWinding.HOLE)
                         .build(),
                WHITE);
        canvas.fillRect(new Rect(180.0f, 68.0f, 56.0f, 24.0f), BLACK);
        canvas.drawText(ALPHA_LAYOUT, new Point(184.0f, 72.0f));
        canvas.drawText(RGBA_LAYOUT, new Point(216.0f, 72.0f));
        canvas.fillRect(new Rect(264.0f, 72.0f, 24.0f, 24.0f), BLACK);
        canvas.drawImage(
                IMAGE_REF,
                new Rect(268.0f, 76.0f, 16.0f, 16.0f),
                ImageSampling.NEAREST,
                1.0f);
        canvas.fillRect(new Rect(304.0f, 72.0f, 16.0f, 48.0f), BLUR_RED);
        canvas.fillRect(new Rect(320.0f, 72.0f, 80.0f, 48.0f), BLUR_BLUE);
        canvas.backdropBlur(
                new Rect(312.0f, 80.0f, 80.0f, 32.0f),
                uniformRadii(12.0f),
                12.0f);
        return canvas.finish();
    }

    private static void verifyPixels(OpenGlRenderReport report, OpenGlTarget target) {
        if (report.framebuffer() == 0) {
            throw new OpenGlRenderException("Batch probe did not produce a framebuffer");
        }
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        RuntimeException primaryFailure = null;
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, report.framebuffer());
            glReadBuffer(GL_COLOR_ATTACHMENT0);
            requirePixel(target, 132, 32, 20, 199, 97, "three-stop gradient center");
            requirePixel(target, 32, 80, 235, 20, 15, "nested clip content");
            requirePixel(target, 84, 108, 10, 26, 41, "non-convex clip hole");
            requirePixel(target, 200, 100, 250, 199, 20, "clip pop sibling");
            requirePixel(target, 188, 76, 40, 215, 244, "A8 text tint and top row");
            requirePixel(target, 188, 84, 0, 0, 0, "A8 transparent bottom row");
            requirePixel(target, 220, 76, 100, 15, 5, "RGBA premultiplied top row");
            requirePixel(target, 220, 84, 10, 95, 20, "RGBA premultiplied bottom row");
            requirePixel(target, 272, 80, 30, 120, 200, "PNG-style image top row");
            requirePixel(target, 272, 88, 100, 15, 45, "PNG-style image premultiplied bottom row");
            requireBlurredMix(target, 320, 96, "backdrop blur red-blue boundary");
            requirePixel(target, 313, 81, 255, 0, 0, "backdrop blur rounded-mask exterior");
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            try {
                snapshot.restore();
                snapshot.assertRestored();
            } catch (RuntimeException restoreFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(restoreFailure);
                } else {
                    throw restoreFailure;
                }
            }
        }
    }

    private static void requirePixel(
            OpenGlTarget target,
            int x,
            int topY,
            int expectedRed,
            int expectedGreen,
            int expectedBlue,
            String name
    ) {
        if (x >= target.width() || topY >= target.height()) {
            throw new OpenGlRenderException("Batch probe target is too small for " + name);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            glReadPixels(x, target.height() - topY - 1, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            int red = Byte.toUnsignedInt(pixel.get(0));
            int green = Byte.toUnsignedInt(pixel.get(1));
            int blue = Byte.toUnsignedInt(pixel.get(2));
            if (Math.abs(red - expectedRed) > 8
                    || Math.abs(green - expectedGreen) > 8
                    || Math.abs(blue - expectedBlue) > 8) {
                throw new OpenGlRenderException(
                        name + " pixel mismatch: expected "
                                + expectedRed + "," + expectedGreen + "," + expectedBlue
                                + " but was " + red + "," + green + "," + blue);
            }
        }
    }

    private static void requireBlurredMix(OpenGlTarget target, int x, int topY, String name) {
        if (x >= target.width() || topY >= target.height()) {
            throw new OpenGlRenderException("Batch probe target is too small for " + name);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            glReadPixels(x, target.height() - topY - 1, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            int red = Byte.toUnsignedInt(pixel.get(0));
            int green = Byte.toUnsignedInt(pixel.get(1));
            int blue = Byte.toUnsignedInt(pixel.get(2));
            if (red < 24 || blue < 24 || red > 231 || blue > 231 || green > 8) {
                throw new OpenGlRenderException(
                        name + " pixel did not contain the expected red-blue mix: "
                                + red + "," + green + "," + blue);
            }
        }
    }

    private static SolidPaint solid(float red, float green, float blue, float alpha) {
        return new SolidPaint(new Color(red, green, blue, alpha));
    }

    private static CornerRadii uniformRadii(float value) {
        return new CornerRadii(value, value, value, value);
    }

    private static TextLayout probeLayout(String text) {
        TextRequest request = TextRequest.builder(text, TextStyle.builder(16.0f).build()).build();
        return new ProbeTextLayout(
                request,
                0L,
                new Size(16.0f, 16.0f),
                12.0f,
                12.0f,
                List.of(new TextLine(0, text.length(), 16.0f, 16.0f, 12.0f)),
                0);
    }

    private static TextRaster alphaRaster() {
        byte[] pixels = new byte[16 * 16];
        Arrays.fill(pixels, 0, 16 * 8, (byte) 0xFF);
        return raster(
                ALPHA_TEXTURE_KEY,
                0x41,
                TextPixelFormat.ALPHA_8,
                16,
                ALPHA_TINT,
                pixels);
    }

    private static TextRaster rgbaRaster() {
        byte[] pixels = new byte[16 * 16 * 4];
        for (int y = 0; y < 16; y++) {
            int red = y < 8 ? 100 : 10;
            int green = y < 8 ? 15 : 95;
            int blue = y < 8 ? 5 : 20;
            for (int x = 0; x < 16; x++) {
                int offset = (y * 16 + x) * 4;
                pixels[offset] = (byte) red;
                pixels[offset + 1] = (byte) green;
                pixels[offset + 2] = (byte) blue;
                pixels[offset + 3] = (byte) 128;
            }
        }
        return raster(
                RGBA_TEXTURE_KEY,
                0x52,
                TextPixelFormat.RGBA_8888_PREMULTIPLIED,
                16 * 4,
                Color.rgb(0xFFFFFF),
                pixels);
    }

    private static ImageRaster imageRaster() {
        byte[] pixels = new byte[16 * 16 * 4];
        for (int y = 0; y < 16; y++) {
            int red = y < 8 ? 30 : 100;
            int green = y < 8 ? 120 : 15;
            int blue = y < 8 ? 200 : 45;
            int alpha = y < 8 ? 255 : 128;
            for (int x = 0; x < 16; x++) {
                int offset = (y * 16 + x) * 4;
                pixels[offset] = (byte) red;
                pixels[offset + 1] = (byte) green;
                pixels[offset + 2] = (byte) blue;
                pixels[offset + 3] = (byte) alpha;
            }
        }
        byte[] digest = new byte[32];
        Arrays.fill(digest, (byte) 0x63);
        return ImageRaster.copyOf(
                IMAGE_REF.key(),
                0L,
                IMAGE_TEXTURE_KEY,
                digest,
                16,
                16,
                pixels);
    }

    private static TextRaster raster(
            long textureKey,
            int digestByte,
            TextPixelFormat format,
            int rowBytes,
            Color modulation,
            byte[] pixels) {
        byte[] digest = new byte[32];
        Arrays.fill(digest, (byte) digestByte);
        return TextRaster.copyOf(
                0L,
                textureKey,
                digest,
                format,
                16,
                16,
                rowBytes,
                1.0f,
                0.0f,
                0.0f,
                modulation,
                pixels);
    }

    @Override
    public void close() {
        RuntimeException primaryFailure = null;
        try {
            renderer.close();
        } catch (RuntimeException exception) {
            primaryFailure = exception;
        }
        try {
            textTextures.close();
        } catch (RuntimeException exception) {
            primaryFailure = appendFailure(primaryFailure, exception);
        }
        try {
            imageTextures.close();
        } catch (RuntimeException exception) {
            primaryFailure = appendFailure(primaryFailure, exception);
        }
        renderedTarget = null;
        frameInfo = null;
        if (primaryFailure != null) {
            throw primaryFailure;
        }
    }

    private static RuntimeException appendFailure(
            RuntimeException primary,
            RuntimeException additional) {
        if (primary == null) {
            return additional;
        }
        primary.addSuppressed(additional);
        return primary;
    }

    private record TargetKey(int texture, int width, int height, long generation) {
        private static TargetKey from(OpenGlTarget target) {
            return new TargetKey(
                    target.colorTextureId(),
                    target.width(),
                    target.height(),
                    target.generationToken());
        }
    }

    private record ProbeTextLayout(
            TextRequest request,
            long resourceGeneration,
            Size size,
            float alphabeticBaseline,
            float ideographicBaseline,
            List<TextLine> lines,
            int unresolvedGlyphs) implements TextLayout {
        private ProbeTextLayout {
            lines = List.copyOf(lines);
        }
    }

    private record ProbeImageRef(UiKey key, ImageInfo imageInfo) implements ImageRef {
        @Override
        public ResourceState state() {
            return ResourceState.READY;
        }

        @Override
        public Optional<ImageInfo> info() {
            return Optional.of(imageInfo);
        }
    }
}
