package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.canvas.ImageSampling;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.StrokeStyle;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.resource.ImageInfo;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.api.style.Transform2D;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.canvas.DisplayCommand;
import cn.fandmc.fandui.canvas.DisplayGradientStop;
import cn.fandmc.fandui.canvas.DisplayList;
import cn.fandmc.fandui.canvas.DisplayPaint;
import cn.fandmc.fandui.canvas.PremultipliedColor;
import cn.fandmc.fandui.render.opengl.internal.GlStateSnapshot;
import cn.fandmc.fandui.text.TextPixelFormat;
import cn.fandmc.fandui.text.TextRaster;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_DEBUG;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_UNPACK_IMAGE_HEIGHT;
import static org.lwjgl.opengl.GL12.GL_UNPACK_SKIP_IMAGES;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL33.glBindSampler;

/** Directly replays immutable FandUI display lists through LWJGL's stock NanoVG GL3 backend. */
public final class NanoVgGl3Renderer implements AutoCloseable {
    public static final String ASSERT_STATE_PROPERTY = "fandui.opengl.assertState";
    public static final String NANOVG_DEBUG_PROPERTY = "fandui.nanovg.debug";

    private final boolean assertState;
    private final NanoVgFramebuffers framebuffers;
    private final NanoVgLayerCompositor compositor;
    private final NanoVgBackdropBlur backdropBlur;
    private final GlStateSnapshot stateBefore = GlStateSnapshot.reusable();
    private final GlStateSnapshot stateCheck = GlStateSnapshot.reusable();

    private Thread renderThread;
    private long context;
    private NanoVgExternalImages externalImages;
    private NanoVgGradientCache gradients;
    private DisplayList preparedDisplayList;
    private NanoVgRenderResources preparedResources;
    private PreparedFrame preparedFrame;
    private Player player;
    private boolean closed;

    public NanoVgGl3Renderer() {
        this(
                Boolean.parseBoolean(System.getProperty(ASSERT_STATE_PROPERTY, "false")),
                new NanoVgFramebuffers(),
                new NanoVgLayerCompositor(),
                new NanoVgBackdropBlur());
    }

    NanoVgGl3Renderer(
            boolean assertState,
            NanoVgFramebuffers framebuffers,
            NanoVgLayerCompositor compositor,
            NanoVgBackdropBlur backdropBlur) {
        this.assertState = assertState;
        this.framebuffers = Objects.requireNonNull(framebuffers, "framebuffers");
        this.compositor = Objects.requireNonNull(compositor, "compositor");
        this.backdropBlur = Objects.requireNonNull(backdropBlur, "backdropBlur");
    }

    OpenGlRenderReport render(
            RenderHost host,
            OpenGlFrameInfo frameInfo,
            DisplayList displayList,
            NanoVgRenderResources resources) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(frameInfo, "frameInfo");
        Objects.requireNonNull(displayList, "displayList");
        Objects.requireNonNull(resources, "resources");
        requireOpenRenderThread(host);

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
        frameInfo.validateTarget(target);
        requireContext();
        OpenGlPassScope.requireActive();
        boolean externalFrame = false;
        boolean gradientFrame = false;
        try {
            ensureNanoVg();
            prepareUploadState();
            boolean rebuilt = framebuffers.ensure(
                    target,
                    displayList.maximumClipDepth());
            externalImages.beginFrame();
            externalFrame = true;
            gradients.beginFrame();
            gradientFrame = true;
            PreparedFrame prepared = preparedFrame;
            if (prepared == null
                    || preparedDisplayList != displayList
                    || preparedResources != resources) {
                prepared = PreparedFrame.prepare(
                        displayList,
                        resources,
                        externalImages,
                        gradients);
                preparedDisplayList = displayList;
                preparedResources = resources;
                preparedFrame = prepared;
            } else {
                prepared.retain(externalImages, gradients);
            }

            framebuffers.clearRootStencil();
            if (player == null) {
                player = new Player(context, framebuffers, compositor, backdropBlur);
            }
            RenderCounts counts = player.play(frameInfo, target, displayList, prepared);

            gradients.endFrame();
            gradientFrame = false;
            externalImages.endFrame();
            externalFrame = false;
            return new OpenGlRenderReport(
                    rebuilt
                            ? OpenGlRenderReport.Status.TARGET_REBUILT
                            : OpenGlRenderReport.Status.RENDERED,
                    host.name(),
                    framebuffers.rootFramebuffer(),
                    target.width(),
                    target.height(),
                    counts.operations,
                    counts.drawCalls);
        } finally {
            if (gradientFrame && gradients != null) {
                gradients.abortFrame();
            }
            if (externalFrame && externalImages != null) {
                externalImages.abortFrame();
            }
        }
    }

    private void ensureNanoVg() {
        if (context != 0L) {
            return;
        }
        int flags = NVG_ANTIALIAS | NVG_STENCIL_STROKES;
        if (Boolean.getBoolean(NANOVG_DEBUG_PROPERTY)) {
            flags |= NVG_DEBUG;
        }
        long nextContext = nvgCreate(flags);
        if (nextContext == 0L) {
            throw new OpenGlRenderException("LWJGL NanoVGGL3 failed to create a context");
        }
        context = nextContext;
        externalImages = new NanoVgExternalImages(context);
        gradients = new NanoVgGradientCache(context);
    }

    private static void prepareUploadState() {
        glActiveTexture(GL_TEXTURE0);
        glBindSampler(0, 0);
        glActiveTexture(GL_TEXTURE1);
        glBindSampler(1, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glPixelStorei(GL_UNPACK_SWAP_BYTES, GL_FALSE);
        glPixelStorei(GL_UNPACK_LSB_FIRST, GL_FALSE);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);
    }

    private static void requireContext() {
        if (GL.getCapabilities() == null) {
            throw new OpenGlRenderException("No current OpenGL context for NanoVG rendering");
        }
    }

    private void requireOpenRenderThread(RenderHost host) {
        if (closed) {
            throw new IllegalStateException("NanoVG GL3 renderer is closed");
        }
        host.assertRenderThread();
        Thread current = Thread.currentThread();
        if (renderThread == null) {
            renderThread = current;
        } else if (renderThread != current) {
            throw new IllegalStateException("NanoVG GL3 renderer is confined to its first Render Thread");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (renderThread != null && renderThread != Thread.currentThread()) {
            throw new IllegalStateException("NanoVG GL3 renderer must close on its Render Thread");
        }
        if (context == 0L) {
            closed = true;
            return;
        }
        requireContext();
        GlStateSnapshot snapshot = stateBefore.recapture();
        closed = true;
        RuntimeException primary = null;
        try {
            externalImages.close();
        } catch (RuntimeException exception) {
            primary = exception;
        }
        try {
            gradients.close();
        } catch (RuntimeException exception) {
            primary = append(primary, exception);
        }
        try {
            backdropBlur.close();
        } catch (RuntimeException exception) {
            primary = append(primary, exception);
        }
        try {
            compositor.close();
        } catch (RuntimeException exception) {
            primary = append(primary, exception);
        }
        try {
            framebuffers.close();
        } catch (RuntimeException exception) {
            primary = append(primary, exception);
        }
        try {
            nvgDelete(context);
        } catch (RuntimeException exception) {
            primary = append(primary, exception);
        } finally {
            context = 0L;
            externalImages = null;
            gradients = null;
            preparedDisplayList = null;
            preparedResources = null;
            preparedFrame = null;
            player = null;
            try {
                snapshot.restore();
                if (assertState) {
                    snapshot.assertRestored(stateCheck.recapture());
                }
            } catch (RuntimeException restoreFailure) {
                primary = append(primary, restoreFailure);
            }
        }
        if (primary != null) {
            throw primary;
        }
    }

    private static RuntimeException append(RuntimeException primary, RuntimeException additional) {
        if (primary == null) {
            return additional;
        }
        primary.addSuppressed(additional);
        return primary;
    }

    private static final class Player {
        private static final int INITIAL_STATE_DEPTH = 16;

        private final long context;
        private final NanoVgFramebuffers framebuffers;
        private final NanoVgLayerCompositor compositor;
        private final NanoVgBackdropBlur backdropBlur;
        private final List<DisplayCommand> stateHistory = new ArrayList<>();
        private final List<ClipLayer> clipLayers = new ArrayList<>();
        private final NanoVgTextureSampling textureSampling = new NanoVgTextureSampling();
        private final float[] transformScratch = new float[6];
        private final float[] pixelOffsetScratch = new float[2];
        private int[] stateClipCounts = new int[INITIAL_STATE_DEPTH];
        private int[] stateHistorySizes = new int[INITIAL_STATE_DEPTH];

        private OpenGlFrameInfo frameInfo;
        private OpenGlTarget target;
        private DisplayList displayList;
        private PreparedFrame prepared;
        private int currentFramebuffer;
        private boolean nanoVgFrame;
        private int operations;
        private int drawCalls;
        private int stateDepth;
        private int clipDepth;
        private int textureIndex;

        private Player(
                long context,
                NanoVgFramebuffers framebuffers,
                NanoVgLayerCompositor compositor,
                NanoVgBackdropBlur backdropBlur) {
            this.context = context;
            this.framebuffers = framebuffers;
            this.compositor = compositor;
            this.backdropBlur = backdropBlur;
        }

        private RenderCounts play(
                OpenGlFrameInfo frameInfo,
                OpenGlTarget target,
                DisplayList displayList,
                PreparedFrame prepared) {
            if (this.prepared != prepared) {
                textureSampling.invalidateConfigured();
            }
            this.frameInfo = frameInfo;
            this.target = target;
            this.displayList = displayList;
            this.prepared = prepared;
            stateHistory.clear();
            stateDepth = 0;
            clearActiveClips();
            textureSampling.clear();
            operations = 0;
            drawCalls = 0;
            textureIndex = 0;
            currentFramebuffer = framebuffers.rootFramebuffer();
            beginNanoVgFrame(false);
            try {
                List<DisplayCommand> commands = displayList.commands();
                for (int index = 0, size = commands.size(); index < size; index++) {
                    execute(commands.get(index));
                }
                prepared.requireTexturesConsumed(textureIndex);
                while (clipDepth != 0) {
                    closeClip();
                }
                endNanoVgFrame();
                return new RenderCounts(operations, drawCalls);
            } catch (RuntimeException | Error failure) {
                cancelNanoVgFrame();
                clearActiveClips();
                stateDepth = 0;
                stateHistory.clear();
                throw failure;
            }
        }

        private void execute(DisplayCommand command) {
            if (isStateCommand(command)) {
                executeState(command, true);
            } else if (command instanceof DisplayCommand.Clip clip) {
                openClip(clip.path());
            } else if (command instanceof DisplayCommand.BackdropBlur blur) {
                renderBackdropBlur(blur);
            } else if (command instanceof DisplayCommand.FillRect fill) {
                fillRect(fill.rect(), fill.paint());
            } else if (command instanceof DisplayCommand.FillRoundedRect fill) {
                fillRoundedRect(fill.rect(), fill.radii(), fill.paint());
            } else if (command instanceof DisplayCommand.FillPath fill) {
                NanoVgPathReplay.replay(context, fill.path());
                applyFillPaint(fill.paint());
                nvgFill(context);
                recordNanoVgDraw();
            } else if (command instanceof DisplayCommand.StrokePath stroke) {
                NanoVgPathReplay.replay(context, stroke.path());
                applyStrokePaint(stroke.paint());
                applyStrokeStyle(stroke.style());
                nvgStroke(context);
                recordNanoVgDraw();
            } else if (command instanceof DisplayCommand.DrawImage
                    || command instanceof DisplayCommand.DrawImageRegion
                    || command instanceof DisplayCommand.DrawText) {
                drawTexture();
            } else {
                throw new OpenGlRenderException(
                        "Unsupported display command: " + command.getClass().getName());
            }
        }

        private void executeState(DisplayCommand command, boolean remember) {
            if (!remember) {
                applyState(command);
                return;
            }
            if (command == DisplayCommand.Save.INSTANCE) {
                pushStateMarker(clipDepth, stateHistory.size());
                applyState(command);
                stateHistory.add(command);
            } else if (command == DisplayCommand.Restore.INSTANCE) {
                if (stateDepth == 0) {
                    throw new OpenGlRenderException("Display list restored an empty NanoVG state stack");
                }
                int markerIndex = --stateDepth;
                int markerClipCount = stateClipCounts[markerIndex];
                int markerHistorySize = stateHistorySizes[markerIndex];
                while (clipDepth > markerClipCount) {
                    closeClip();
                }
                applyState(command);
                truncateStateHistory(markerHistorySize);
            } else {
                applyState(command);
                stateHistory.add(command);
            }
        }

        private void pushStateMarker(int clipCount, int historySize) {
            if (stateDepth == stateClipCounts.length) {
                int nextLength = Math.multiplyExact(stateDepth, 2);
                stateClipCounts = Arrays.copyOf(stateClipCounts, nextLength);
                stateHistorySizes = Arrays.copyOf(stateHistorySizes, nextLength);
            }
            stateClipCounts[stateDepth] = clipCount;
            stateHistorySizes[stateDepth] = historySize;
            stateDepth++;
        }

        private void truncateStateHistory(int size) {
            while (stateHistory.size() > size) {
                stateHistory.remove(stateHistory.size() - 1);
            }
        }

        private void applyState(DisplayCommand command) {
            if (command == DisplayCommand.Save.INSTANCE) {
                nvgSave(context);
            } else if (command == DisplayCommand.Restore.INSTANCE) {
                nvgRestore(context);
            } else if (command instanceof DisplayCommand.Translate translate) {
                nvgTranslate(context, translate.x(), translate.y());
            } else if (command instanceof DisplayCommand.Scale scale) {
                nvgScale(context, scale.x(), scale.y());
            } else if (command instanceof DisplayCommand.Rotate rotate) {
                nvgRotate(context, rotate.radians());
            } else if (command instanceof DisplayCommand.Transform transform) {
                Transform2D value = transform.value();
                nvgTransform(
                        context,
                        value.m00(),
                        value.m01(),
                        value.m10(),
                        value.m11(),
                        value.tx(),
                        value.ty());
            } else if (command instanceof DisplayCommand.SetCompositeOperation composite) {
                nvgGlobalCompositeOperation(context, NanoVgMappings.composite(composite.operation()));
            } else if (command instanceof DisplayCommand.SetGlobalAlpha alpha) {
                nvgGlobalAlpha(context, alpha.alpha());
            } else if (command instanceof DisplayCommand.Scissor scissor) {
                Rect rect = scissor.rect();
                nvgScissor(context, rect.x(), rect.y(), rect.width(), rect.height());
            } else if (command instanceof DisplayCommand.IntersectScissor scissor) {
                Rect rect = scissor.rect();
                nvgIntersectScissor(context, rect.x(), rect.y(), rect.width(), rect.height());
            } else if (command == DisplayCommand.ResetScissor.INSTANCE) {
                nvgResetScissor(context);
            } else {
                throw new IllegalArgumentException("Not a NanoVG state command: " + command);
            }
        }

        private void fillRect(Rect rect, DisplayPaint paint) {
            nvgBeginPath(context);
            nvgRect(context, rect.x(), rect.y(), rect.width(), rect.height());
            applyFillPaint(paint);
            nvgFill(context);
            recordNanoVgDraw();
        }

        private void fillRoundedRect(Rect rect, CornerRadii radii, DisplayPaint paint) {
            nvgBeginPath(context);
            nvgRoundedRectVarying(
                    context,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    radii.topLeft(),
                    radii.topRight(),
                    radii.bottomRight(),
                    radii.bottomLeft());
            applyFillPaint(paint);
            nvgFill(context);
            recordNanoVgDraw();
        }

        private void applyFillPaint(DisplayPaint paint) {
            applyPaint(paint, false);
        }

        private void applyStrokePaint(DisplayPaint paint) {
            applyPaint(paint, true);
        }

        private void applyPaint(DisplayPaint paint, boolean stroke) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                if (paint instanceof DisplayPaint.Solid solid) {
                    NVGColor color = unpremultiplied(solid.color(), NVGColor.calloc(stack));
                    if (stroke) {
                        nvgStrokeColor(context, color);
                    } else {
                        nvgFillColor(context, color);
                    }
                    return;
                }

                NVGPaint result = NVGPaint.calloc(stack);
                if (paint instanceof DisplayPaint.Linear linear
                        && PreparedFrame.nativeStops(linear.stops())) {
                    nvgLinearGradient(
                            context,
                            linear.start().x(),
                            linear.start().y(),
                            linear.end().x(),
                            linear.end().y(),
                            unpremultiplied(PreparedFrame.first(linear.stops()), NVGColor.calloc(stack)),
                            unpremultiplied(PreparedFrame.last(linear.stops()), NVGColor.calloc(stack)),
                            result);
                } else if (paint instanceof DisplayPaint.Radial radial
                        && PreparedFrame.nativeStops(radial.stops())) {
                    nvgRadialGradient(
                            context,
                            radial.center().x(),
                            radial.center().y(),
                            radial.innerRadius(),
                            radial.outerRadius(),
                            unpremultiplied(PreparedFrame.first(radial.stops()), NVGColor.calloc(stack)),
                            unpremultiplied(PreparedFrame.last(radial.stops()), NVGColor.calloc(stack)),
                            result);
                } else {
                    PreparedPaint value = prepared.paint(paint);
                    if (value instanceof ImageLinearPrepared linear) {
                        float dx = linear.end.x() - linear.start.x();
                        float dy = linear.end.y() - linear.start.y();
                        float length = Math.max((float) Math.sqrt(dx * dx + dy * dy), 1.0e-6f);
                        nvgImagePattern(
                                context,
                                linear.start.x(),
                                linear.start.y(),
                                length,
                                1.0f,
                                (float) Math.atan2(dy, dx),
                                linear.image.imageId(),
                                1.0f,
                                result);
                    } else if (value instanceof ImageRadialPrepared radial) {
                        float outer = Math.max(radial.outerRadius, 1.0e-6f);
                        nvgImagePattern(
                                context,
                                radial.center.x() - outer,
                                radial.center.y() - outer,
                                outer * 2.0f,
                                outer * 2.0f,
                                0.0f,
                                radial.image.imageId(),
                                1.0f,
                                result);
                    } else {
                        throw new OpenGlRenderException("Display paint was not prepared");
                    }
                }
                if (stroke) {
                    nvgStrokePaint(context, result);
                } else {
                    nvgFillPaint(context, result);
                }
            }
        }

        private void applyStrokeStyle(StrokeStyle style) {
            nvgStrokeWidth(context, style.width());
            nvgMiterLimit(context, style.miterLimit());
            nvgLineCap(context, NanoVgMappings.lineCap(style.cap()));
            nvgLineJoin(context, NanoVgMappings.lineJoin(style.join()));
        }

        private void drawTexture() {
            PreparedTexture texture = prepared.texture(textureIndex++);
            if (texture == null) {
                return;
            }

            prepareTextureSampling(texture.textureId, texture.sampling);
            boolean pixelOffsetApplied = applyPixelOffset(texture);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Rect source = texture.source;
                Rect destination = texture.destination;
                NanoVgImagePattern geometry = NanoVgImagePattern.from(
                        texture.imageWidth,
                        texture.imageHeight,
                        source,
                        destination);
                NVGPaint pattern = nvgImagePattern(
                        context,
                        geometry.x(),
                        geometry.y(),
                        geometry.width(),
                        geometry.height(),
                        0.0f,
                        texture.image.imageId(),
                        texture.opacity,
                        NVGPaint.calloc(stack));
                multiplyColor(pattern.innerColor(), texture.modulation);
                pattern.outerColor().set(pattern.innerColor());
                nvgBeginPath(context);
                nvgRect(
                        context,
                        destination.x(),
                        destination.y(),
                        destination.width(),
                        destination.height());
                nvgFillPaint(context, pattern);
                nvgFill(context);
                recordNanoVgDraw();
            } finally {
                if (pixelOffsetApplied) {
                    nvgRestore(context);
                }
            }
        }

        private boolean applyPixelOffset(PreparedTexture texture) {
            if (!texture.snapToDevicePixels) {
                return false;
            }
            nvgCurrentTransform(context, transformScratch);
            if (!NanoVgPixelAlignment.textOffset(
                    transformScratch,
                    texture.destination,
                    frameInfo.devicePixelRatio(),
                    pixelOffsetScratch)) {
                return false;
            }
            nvgSave(context);
            nvgTranslate(context, pixelOffsetScratch[0], pixelOffsetScratch[1]);
            return true;
        }

        private void prepareTextureSampling(int textureId, OpenGlSampling sampling) {
            NanoVgTextureSampling.Decision decision = textureSampling.register(textureId, sampling);
            if (decision == NanoVgTextureSampling.Decision.FLUSH) {
                endNanoVgFrame();
                beginNanoVgFrame(true);
                decision = textureSampling.register(textureId, sampling);
            }
            if (decision == NanoVgTextureSampling.Decision.CONFIGURE) {
                OpenGlTextureUploader.configureSampling(textureId, sampling);
                textureSampling.markConfigured(textureId, sampling);
            }
        }

        private void renderBackdropBlur(DisplayCommand.BackdropBlur blur) {
            operations++;
            if (blur.radius() == 0.0f
                    || blur.rect().width() == 0.0f
                    || blur.rect().height() == 0.0f) {
                return;
            }

            float alpha = currentGlobalAlpha();
            if (alpha == 0.0f) {
                return;
            }
            nvgCurrentTransform(context, transformScratch);
            NanoVgBackdropBlur.PixelBounds affectedBounds = NanoVgBackdropBlur.transformedBounds(
                    blur.rect(),
                    transformScratch,
                    frameInfo.devicePixelRatio(),
                    target.width(),
                    target.height());
            if (affectedBounds.empty()) {
                return;
            }
            endNanoVgFrame();
            NanoVgBackdropBlur.Result result = backdropBlur.blur(
                    currentFramebuffer,
                    target.width(),
                    target.height(),
                    frameInfo.devicePixelRatio(),
                    blur.radius(),
                    affectedBounds);
            NanoVgBackdropBlur.CompositeMask mask = null;
            if (zeroRadii(blur.radii()) && !hasActiveScissor()) {
                mask = NanoVgBackdropBlur.axisAlignedMask(
                        blur.rect(),
                        transformScratch,
                        frameInfo.devicePixelRatio(),
                        target.width(),
                        target.height(),
                        alpha);
            }
            if (mask == null) {
                framebuffers.ensureMask();
                renderBackdropMask(blur, affectedBounds);
                mask = NanoVgBackdropBlur.CompositeMask.sampled(
                        framebuffers.maskTexture(),
                        affectedBounds);
            }
            drawCalls += result.drawCalls();
            drawCalls += backdropBlur.composite(
                    currentFramebuffer,
                    target.width(),
                    target.height(),
                    result,
                    mask);
            beginNanoVgFrame(true);
        }

        private float currentGlobalAlpha() {
            for (int index = stateHistory.size() - 1; index >= 0; index--) {
                DisplayCommand command = stateHistory.get(index);
                if (command instanceof DisplayCommand.SetGlobalAlpha alpha) {
                    return alpha.alpha();
                }
            }
            return 1.0f;
        }

        private boolean hasActiveScissor() {
            for (int index = stateHistory.size() - 1; index >= 0; index--) {
                DisplayCommand command = stateHistory.get(index);
                if (command == DisplayCommand.ResetScissor.INSTANCE) {
                    return false;
                }
                if (command instanceof DisplayCommand.Scissor
                        || command instanceof DisplayCommand.IntersectScissor) {
                    return true;
                }
            }
            return false;
        }

        private static boolean zeroRadii(CornerRadii radii) {
            return radii.topLeft() == 0.0f
                    && radii.topRight() == 0.0f
                    && radii.bottomRight() == 0.0f
                    && radii.bottomLeft() == 0.0f;
        }

        private void renderBackdropMask(
                DisplayCommand.BackdropBlur blur,
                NanoVgBackdropBlur.PixelBounds affectedBounds) {
            framebuffers.clearMask(
                    affectedBounds.x(),
                    affectedBounds.y(),
                    affectedBounds.width(),
                    affectedBounds.height());
            prepareNanoVgTarget(framebuffers.maskFramebuffer());
            nvgBeginFrame(
                    context,
                    frameInfo.logicalWidth(),
                    frameInfo.logicalHeight(),
                    frameInfo.devicePixelRatio());
            nanoVgFrame = true;
            textureSampling.clear();
            for (DisplayCommand command : stateHistory) {
                executeState(command, false);
            }
            nvgGlobalCompositeOperation(context, NVG_SOURCE_OVER);
            Rect rect = blur.rect();
            CornerRadii radii = blur.radii();
            nvgBeginPath(context);
            nvgRoundedRectVarying(
                    context,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    radii.topLeft(),
                    radii.topRight(),
                    radii.bottomRight(),
                    radii.bottomLeft());
            try (MemoryStack stack = MemoryStack.stackPush()) {
                nvgFillColor(context, nvgRGBAf(1.0f, 1.0f, 1.0f, 1.0f, NVGColor.calloc(stack)));
                nvgFill(context);
            }
            drawCalls++;
            endNanoVgFrame();
        }

        private void openClip(Path path) {
            int depthIndex = clipDepth;
            ClipLayer layer = acquireClipLayer(depthIndex);
            layer.path = path;
            clipDepth++;
            try {
                nvgCurrentTransform(context, layer.transform);
                endNanoVgFrame();

                int parentFramebuffer = currentFramebuffer;
                framebuffers.copyToLayer(parentFramebuffer, depthIndex);
                int childFramebuffer = framebuffers.layerFramebuffer(depthIndex);
                layer.parentFramebuffer = parentFramebuffer;
                layer.depthIndex = depthIndex;
                currentFramebuffer = childFramebuffer;
                beginNanoVgFrame(true);
            } catch (RuntimeException | Error failure) {
                clipDepth--;
                layer.clear();
                throw failure;
            }
        }

        private void closeClip() {
            if (clipDepth == 0) {
                throw new OpenGlRenderException("NanoVG path clip stack underflow");
            }
            ClipLayer layer = clipLayers.get(--clipDepth);
            try {
                endNanoVgFrame();
                renderMask(layer);
                drawCalls += compositor.composite(
                        layer.parentFramebuffer,
                        framebuffers.layerTexture(layer.depthIndex),
                        framebuffers.maskTexture(),
                        target.width(),
                        target.height());
                currentFramebuffer = layer.parentFramebuffer;
                beginNanoVgFrame(true);
            } finally {
                layer.clear();
            }
        }

        private ClipLayer acquireClipLayer(int index) {
            if (index == clipLayers.size()) {
                clipLayers.add(new ClipLayer());
            }
            return clipLayers.get(index);
        }

        private void clearActiveClips() {
            for (int index = 0; index < clipDepth; index++) {
                clipLayers.get(index).clear();
            }
            clipDepth = 0;
        }

        private void renderMask(ClipLayer layer) {
            framebuffers.clearMask();
            prepareNanoVgTarget(framebuffers.maskFramebuffer());
            nvgBeginFrame(
                    context,
                    frameInfo.logicalWidth(),
                    frameInfo.logicalHeight(),
                    frameInfo.devicePixelRatio());
            nanoVgFrame = true;
            textureSampling.clear();
            nvgResetTransform(context);
            nvgTransform(
                    context,
                    layer.transform[0],
                    layer.transform[1],
                    layer.transform[2],
                    layer.transform[3],
                    layer.transform[4],
                    layer.transform[5]);
            nvgResetScissor(context);
            nvgGlobalAlpha(context, 1.0f);
            nvgGlobalCompositeOperation(context, NVG_SOURCE_OVER);
            NanoVgPathReplay.replay(context, Objects.requireNonNull(layer.path, "clip path"));
            try (MemoryStack stack = MemoryStack.stackPush()) {
                nvgFillColor(context, nvgRGBAf(1.0f, 1.0f, 1.0f, 1.0f, NVGColor.calloc(stack)));
                nvgFill(context);
            }
            operations++;
            drawCalls++;
            endNanoVgFrame();
        }

        private void beginNanoVgFrame(boolean replayState) {
            if (nanoVgFrame) {
                throw new IllegalStateException("NanoVG frame is already active");
            }
            textureSampling.clear();
            prepareNanoVgTarget(currentFramebuffer);
            nvgBeginFrame(
                    context,
                    frameInfo.logicalWidth(),
                    frameInfo.logicalHeight(),
                    frameInfo.devicePixelRatio());
            nanoVgFrame = true;
            if (replayState) {
                for (DisplayCommand command : stateHistory) {
                    executeState(command, false);
                }
            }
        }

        private void prepareNanoVgTarget(int framebuffer) {
            framebuffers.bind(framebuffer);
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glViewport(0, 0, target.width(), target.height());
            glDisable(GL_COLOR_LOGIC_OP);
            glDisable(GL_FRAMEBUFFER_SRGB);
            glDisable(GL_DITHER);
            glColorMask(true, true, true, true);
            glActiveTexture(GL_TEXTURE0);
            glBindSampler(0, 0);
            glActiveTexture(GL_TEXTURE1);
            glBindSampler(1, 0);
            glActiveTexture(GL_TEXTURE0);
        }

        private void endNanoVgFrame() {
            if (!nanoVgFrame) {
                throw new IllegalStateException("NanoVG frame is not active");
            }
            nvgEndFrame(context);
            nanoVgFrame = false;
        }

        private void cancelNanoVgFrame() {
            if (nanoVgFrame) {
                nvgCancelFrame(context);
                nanoVgFrame = false;
            }
        }

        private void recordNanoVgDraw() {
            operations++;
            drawCalls++;
        }

        private static boolean isStateCommand(DisplayCommand command) {
            return command == DisplayCommand.Save.INSTANCE
                    || command == DisplayCommand.Restore.INSTANCE
                    || command instanceof DisplayCommand.Translate
                    || command instanceof DisplayCommand.Scale
                    || command instanceof DisplayCommand.Rotate
                    || command instanceof DisplayCommand.Transform
                    || command instanceof DisplayCommand.SetCompositeOperation
                    || command instanceof DisplayCommand.SetGlobalAlpha
                    || command instanceof DisplayCommand.Scissor
                    || command instanceof DisplayCommand.IntersectScissor
                    || command == DisplayCommand.ResetScissor.INSTANCE;
        }
    }

    static final class PreparedFrame {
        private static final PreparedPaint[] NO_PAINTS = new PreparedPaint[0];
        private static final PreparedTexture[] NO_TEXTURES = new PreparedTexture[0];
        private static final PreparedFrame EMPTY = new PreparedFrame(Map.of(), NO_TEXTURES, 0);

        private final Map<DisplayPaint, PreparedPaint> paints;
        private final PreparedTexture[] textures;
        private final int textureCount;
        private final PreparedPaint[] retainedPaints;

        private PreparedFrame(
                Map<DisplayPaint, PreparedPaint> paints,
                PreparedTexture[] textures,
                int textureCount) {
            this.paints = paints;
            this.textures = textures;
            this.textureCount = textureCount;
            retainedPaints = paints.isEmpty()
                    ? NO_PAINTS
                    : paints.values().toArray(PreparedPaint[]::new);
        }

        static PreparedFrame prepare(
                DisplayList displayList,
                NanoVgRenderResources resources,
                NanoVgExternalImages externalImages,
                NanoVgGradientCache gradients) {
            IdentityHashMap<DisplayPaint, PreparedPaint> paints = null;
            PreparedTexture[] textures = null;
            int textureCount = 0;
            List<DisplayCommand> commands = displayList.commands();
            for (int commandIndex = 0, size = commands.size(); commandIndex < size; commandIndex++) {
                DisplayCommand command = commands.get(commandIndex);
                DisplayPaint paint = paint(command);
                if (paint != null && requiresPreparation(paint)) {
                    if (paints == null) {
                        paints = new IdentityHashMap<>();
                    }
                    if (!paints.containsKey(paint)) {
                        paints.put(paint, preparePaint(paint, gradients));
                    }
                }
                PreparedTexture texture;
                if (command instanceof DisplayCommand.DrawImage image) {
                    texture = prepareImage(
                            image.image(),
                            null,
                            image.destination(),
                            image.sampling(),
                            image.opacity(),
                            resources,
                            externalImages);
                } else if (command instanceof DisplayCommand.DrawImageRegion image) {
                    texture = prepareImage(
                            image.image(),
                            image.source(),
                            image.destination(),
                            image.sampling(),
                            image.opacity(),
                            resources,
                            externalImages);
                } else if (command instanceof DisplayCommand.DrawText text) {
                    texture = prepareText(
                            text.text(),
                            text.origin(),
                            resources,
                            externalImages);
                } else {
                    continue;
                }
                if (textures == null) {
                    textures = new PreparedTexture[4];
                } else if (textureCount == textures.length) {
                    textures = Arrays.copyOf(textures, Math.multiplyExact(textureCount, 2));
                }
                textures[textureCount++] = texture;
            }
            if (paints == null && textureCount == 0) {
                return EMPTY;
            }
            return new PreparedFrame(
                    paints == null ? Map.of() : paints,
                    textures == null ? NO_TEXTURES : textures,
                    textureCount);
        }

        private void retain(
                NanoVgExternalImages externalImages,
                NanoVgGradientCache gradients) {
            for (PreparedPaint paint : retainedPaints) {
                if (paint instanceof ImageLinearPrepared linear) {
                    gradients.retain(linear.image);
                } else if (paint instanceof ImageRadialPrepared radial) {
                    gradients.retain(radial.image);
                }
            }
            for (int index = 0; index < textureCount; index++) {
                PreparedTexture texture = textures[index];
                if (texture != null) {
                    externalImages.retain(texture.image);
                }
            }
        }

        private PreparedPaint paint(DisplayPaint paint) {
            return paints.get(paint);
        }

        private PreparedTexture texture(int index) {
            if (index < 0 || index >= textureCount) {
                throw new OpenGlRenderException("Prepared texture sequence underflow");
            }
            return textures[index];
        }

        private void requireTexturesConsumed(int consumed) {
            if (consumed != textureCount) {
                throw new OpenGlRenderException("Prepared texture sequence was not fully consumed");
            }
        }

        int preparedPaintCount() {
            return paints.size();
        }

        int textureCommandCount() {
            return textureCount;
        }

        private static DisplayPaint paint(DisplayCommand command) {
            if (command instanceof DisplayCommand.FillRect fill) {
                return fill.paint();
            }
            if (command instanceof DisplayCommand.FillRoundedRect fill) {
                return fill.paint();
            }
            if (command instanceof DisplayCommand.FillPath fill) {
                return fill.paint();
            }
            if (command instanceof DisplayCommand.StrokePath stroke) {
                return stroke.paint();
            }
            return null;
        }

        private static boolean requiresPreparation(DisplayPaint paint) {
            if (paint instanceof DisplayPaint.Solid) {
                return false;
            }
            if (paint instanceof DisplayPaint.Linear linear) {
                return !nativeStops(linear.stops());
            }
            if (paint instanceof DisplayPaint.Radial radial) {
                return !nativeStops(radial.stops());
            }
            throw new OpenGlRenderException("Unsupported display paint: " + paint.getClass().getName());
        }

        private static PreparedPaint preparePaint(
                DisplayPaint paint,
                NanoVgGradientCache gradients) {
            if (paint instanceof DisplayPaint.Linear linear) {
                return new ImageLinearPrepared(
                        linear.start(),
                        linear.end(),
                        gradients.linearImage(linear.stops()));
            }
            if (paint instanceof DisplayPaint.Radial radial) {
                float ratio = radial.outerRadius() == 0.0f
                        ? 0.0f
                        : radial.innerRadius() / radial.outerRadius();
                return new ImageRadialPrepared(
                        radial.center(),
                        radial.outerRadius(),
                        gradients.radialImage(radial.stops(), ratio));
            }
            throw new OpenGlRenderException("Unsupported display paint: " + paint.getClass().getName());
        }

        private static PreparedTexture prepareImage(
                cn.fandmc.fandui.api.resource.ImageRef image,
                Rect requestedSource,
                Rect destination,
                ImageSampling sampling,
                float opacity,
                NanoVgRenderResources resources,
                NanoVgExternalImages externalImages) {
            if (destination.width() == 0.0f || destination.height() == 0.0f || opacity == 0.0f) {
                return null;
            }
            NanoVgRenderResources.Image resolved = Objects.requireNonNull(
                    resources.resolveImage(image, sampling),
                    "resources.resolveImage()");
            ImageInfo declared = image.info().orElse(null);
            if (declared == null) {
                throw new OpenGlRenderException("Image dimensions are unavailable for " + image.key());
            }
            if (declared.width() != resolved.width() || declared.height() != resolved.height()) {
                throw new OpenGlRenderException("Resolved image dimensions do not match " + image.key());
            }
            Rect source = requestedSource == null
                    ? new Rect(0.0f, 0.0f, resolved.width(), resolved.height())
                    : requestedSource;
            requireSource(source, resolved.width(), resolved.height(), image.key().toString());
            OpenGlSampling openGlSampling = sampling == ImageSampling.NEAREST
                    ? OpenGlSampling.NEAREST
                    : OpenGlSampling.LINEAR;
            NanoVgExternalImages.Image externalImage = externalImages.resolve(
                    resolved.textureKey(),
                    resolved.texture(),
                    resolved.width(),
                    resolved.height(),
                    openGlSampling,
                    false);
            return new PreparedTexture(
                    externalImage,
                    resolved.texture().textureId(),
                    resolved.width(),
                    resolved.height(),
                    source,
                    destination,
                    openGlSampling,
                    opacity,
                    Color.rgb(0xffffff),
                    false);
        }

        private static PreparedTexture prepareText(
                TextLayout layout,
                Point origin,
                NanoVgRenderResources resources,
                NanoVgExternalImages externalImages) {
            NanoVgRenderResources.Text resolved = Objects.requireNonNull(
                    resources.resolveText(layout),
                    "resources.resolveText()");
            TextRaster raster = resolved.raster();
            if (raster.resourceGeneration() != layout.resourceGeneration()) {
                throw new OpenGlRenderException("Text raster generation does not match its layout");
            }
            if (raster.byteSize() == 0) {
                return null;
            }
            OpenGlTexture texture = resolved.texture().orElseThrow();
            NanoVgExternalImages.Image externalImage = externalImages.resolve(
                    raster.textureKey(),
                    texture,
                    raster.width(),
                    raster.height(),
                    OpenGlSampling.LINEAR,
                    raster.format() == TextPixelFormat.ALPHA_8);
            float logicalWidth = raster.width() / raster.deviceScale();
            float logicalHeight = raster.height() / raster.deviceScale();
            Rect destination = new Rect(
                    origin.x() + raster.originOffsetX(),
                    origin.y() + raster.originOffsetY(),
                    logicalWidth,
                    logicalHeight);
            return new PreparedTexture(
                    externalImage,
                    texture.textureId(),
                    raster.width(),
                    raster.height(),
                    new Rect(0.0f, 0.0f, raster.width(), raster.height()),
                    destination,
                    OpenGlSampling.LINEAR,
                    1.0f,
                    raster.modulationColor(),
                    true);
        }

        private static void requireSource(Rect source, int width, int height, String description) {
            if (source.width() <= 0.0f
                    || source.height() <= 0.0f
                    || source.x() < 0.0f
                    || source.y() < 0.0f
                    || source.x() + source.width() > width
                    || source.y() + source.height() > height) {
                throw new OpenGlRenderException("Image source rectangle is outside " + description);
            }
        }

        private static boolean nativeStops(List<DisplayGradientStop> stops) {
            return stops.size() == 2
                    && stops.get(0).offset() == 0.0f
                    && stops.get(1).offset() == 1.0f;
        }

        private static PremultipliedColor first(List<DisplayGradientStop> stops) {
            return stops.get(0).color();
        }

        private static PremultipliedColor last(List<DisplayGradientStop> stops) {
            return stops.get(stops.size() - 1).color();
        }
    }

    private static NVGColor unpremultiplied(PremultipliedColor color, NVGColor result) {
        float alpha = color.alpha();
        if (alpha == 0.0f) {
            return nvgRGBAf(0.0f, 0.0f, 0.0f, 0.0f, result);
        }
        return nvgRGBAf(
                color.red() / alpha,
                color.green() / alpha,
                color.blue() / alpha,
                alpha,
                result);
    }

    private static void multiplyColor(NVGColor target, Color modulation) {
        target.r(target.r() * modulation.red());
        target.g(target.g() * modulation.green());
        target.b(target.b() * modulation.blue());
        target.a(target.a() * modulation.alpha());
    }

    private sealed interface PreparedPaint permits
            ImageLinearPrepared,
            ImageRadialPrepared {
    }

    private record ImageLinearPrepared(
            Point start,
            Point end,
            NanoVgGradientCache.Image image) implements PreparedPaint {
    }

    private record ImageRadialPrepared(
            Point center,
            float outerRadius,
            NanoVgGradientCache.Image image) implements PreparedPaint {
    }

    private record PreparedTexture(
            NanoVgExternalImages.Image image,
            int textureId,
            int imageWidth,
            int imageHeight,
            Rect source,
            Rect destination,
            OpenGlSampling sampling,
            float opacity,
            Color modulation,
            boolean snapToDevicePixels) {
    }

    private static final class ClipLayer {
        private final float[] transform = new float[6];
        private Path path;
        private int parentFramebuffer;
        private int depthIndex;

        private void clear() {
            path = null;
        }
    }

    private record RenderCounts(int operations, int drawCalls) {
    }
}
