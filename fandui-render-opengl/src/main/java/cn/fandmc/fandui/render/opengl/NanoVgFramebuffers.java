package cn.fandmc.fandui.render.opengl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.*;

final class NanoVgFramebuffers implements AutoCloseable {
    static final long DEFAULT_BYTE_LIMIT = 512L * 1024L * 1024L;

    private final long byteLimit;
    private TargetKey targetKey;
    private Framebuffer root;
    private List<Framebuffer> layers = List.of();
    private Framebuffer mask;
    private int width;
    private int height;
    private long allocatedBytes;
    private boolean closed;

    NanoVgFramebuffers() {
        this(DEFAULT_BYTE_LIMIT);
    }

    NanoVgFramebuffers(long byteLimit) {
        if (byteLimit < 1L) {
            throw new IllegalArgumentException("Framebuffer byte limit must be positive");
        }
        this.byteLimit = byteLimit;
    }

    boolean ensure(OpenGlTarget target, int maximumClipDepth) {
        requireOpen();
        if (maximumClipDepth < 0) {
            throw new IllegalArgumentException("maximumClipDepth must not be negative");
        }
        TargetKey nextKey = TargetKey.from(target);
        if (nextKey.equals(targetKey)
                && maximumClipDepth <= layers.size()) {
            // Minecraft can recreate texture storage under the same RenderTarget and GL name during resize.
            reattachBorrowedColor(root, target);
            return false;
        }

        TextureInfo texture = inspectTexture(target);
        if (texture.internalFormat != GL_RGBA8) {
            throw new OpenGlRenderException(
                    "Unsupported Minecraft color format 0x"
                            + Integer.toHexString(texture.internalFormat));
        }
        if (texture.width != target.width() || texture.height != target.height()) {
            throw new OpenGlRenderException(
                    "Minecraft color texture dimensions " + texture.width + "x" + texture.height
                            + " do not match target " + target.width() + "x" + target.height());
        }

        int layerCount = nextKey.equals(targetKey)
                ? Math.max(maximumClipDepth, layers.size())
                : maximumClipDepth;
        boolean allocateMask = layerCount != 0;
        long requiredBytes = requireBudget(target.width(), target.height(), layerCount, allocateMask);

        // Keep rebuilds transactional when the configured budget can hold both pools.
        // Otherwise release the stale pool first so resize/clip growth cannot double VRAM.
        if (allocatedBytes > byteLimit - requiredBytes) {
            deleteResources();
        }

        Framebuffer nextRoot = null;
        List<Framebuffer> nextLayers = new ArrayList<>(layerCount);
        Framebuffer nextMask = null;
        boolean complete = false;
        try {
            nextRoot = createBorrowed(target);
            for (int index = 0; index < layerCount; index++) {
                nextLayers.add(createOwned(target.width(), target.height()));
            }
            if (allocateMask) {
                nextMask = createOwned(target.width(), target.height());
            }
            complete = true;
        } finally {
            if (!complete) {
                delete(nextRoot);
                nextLayers.forEach(NanoVgFramebuffers::delete);
                delete(nextMask);
            }
        }

        deleteResources();
        root = nextRoot;
        layers = List.copyOf(nextLayers);
        mask = nextMask;
        targetKey = nextKey;
        width = target.width();
        height = target.height();
        allocatedBytes = requiredBytes;
        return true;
    }

    int rootFramebuffer() {
        requireReady();
        return root.framebuffer;
    }

    int layerFramebuffer(int depthIndex) {
        return layer(depthIndex).framebuffer;
    }

    int layerTexture(int depthIndex) {
        return layer(depthIndex).colorTexture;
    }

    int maskFramebuffer() {
        requireClipTargets();
        return mask.framebuffer;
    }

    int maskTexture() {
        requireClipTargets();
        return mask.colorTexture;
    }

    void ensureMask() {
        requireReady();
        if (mask != null) {
            return;
        }
        long additionalBytes = ownedFramebufferBytes(width, height);
        long requiredBytes;
        try {
            requiredBytes = Math.addExact(allocatedBytes, additionalBytes);
        } catch (ArithmeticException exception) {
            throw new OpenGlRenderException("NanoVG shared mask size overflow", exception);
        }
        if (requiredBytes > byteLimit) {
            throw new OpenGlRenderException(
                    "NanoVG shared mask requires " + requiredBytes
                            + " bytes, exceeding the " + byteLimit + " byte limit");
        }

        Framebuffer nextMask = createOwned(width, height);
        mask = nextMask;
        allocatedBytes = requiredBytes;
    }

    void bind(int framebuffer) {
        requireReady();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glViewport(0, 0, width, height);
    }

    void clearRootStencil() {
        clearStencil(rootFramebuffer());
    }

    void copyToLayer(int sourceFramebuffer, int depthIndex) {
        Framebuffer destination = layer(depthIndex);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFramebuffer);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destination.framebuffer);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glBlitFramebuffer(
                0,
                0,
                width,
                height,
                0,
                0,
                width,
                height,
                GL_COLOR_BUFFER_BIT,
                GL_NEAREST);
        clearStencil(destination.framebuffer);
    }

    void clearMask() {
        requireClipTargets();
        bind(mask.framebuffer);
        glDisable(GL_SCISSOR_TEST);
        glColorMask(true, true, true, true);
        glStencilMask(0xff);
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClearStencil(0);
        glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    }

    void clearMask(int x, int y, int clearWidth, int clearHeight) {
        requireClipTargets();
        if (x < 0
                || y < 0
                || clearWidth < 1
                || clearHeight < 1
                || x + clearWidth > width
                || y + clearHeight > height) {
            throw new IllegalArgumentException("Mask clear bounds are outside the framebuffer");
        }
        bind(mask.framebuffer);
        glEnable(GL_SCISSOR_TEST);
        glScissor(x, y, clearWidth, clearHeight);
        glColorMask(true, true, true, true);
        glStencilMask(0xff);
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClearStencil(0);
        glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    }

    private void clearStencil(int framebuffer) {
        bind(framebuffer);
        glDisable(GL_SCISSOR_TEST);
        glStencilMask(0xff);
        glClearStencil(0);
        glClear(GL_STENCIL_BUFFER_BIT);
    }

    private Framebuffer layer(int depthIndex) {
        requireReady();
        if (depthIndex < 0 || depthIndex >= layers.size()) {
            throw new OpenGlRenderException("Path clip depth exceeds allocated NanoVG layers");
        }
        return layers.get(depthIndex);
    }

    private void requireClipTargets() {
        requireReady();
        if (mask == null) {
            throw new OpenGlRenderException("Path clipping was not allocated for this display list");
        }
    }

    private long requireBudget(int targetWidth, int targetHeight, int layerCount, boolean allocateMask) {
        long required = requiredBytes(targetWidth, targetHeight, layerCount, allocateMask);
        if (required > byteLimit) {
            throw new OpenGlRenderException(
                    "NanoVG path clip targets require " + required
                            + " bytes, exceeding the " + byteLimit + " byte limit");
        }
        return required;
    }

    static long requiredBytes(int targetWidth, int targetHeight, int layerCount) {
        return requiredBytes(targetWidth, targetHeight, layerCount, layerCount != 0);
    }

    static long requiredBytes(
            int targetWidth,
            int targetHeight,
            int layerCount,
            boolean allocateMask) {
        if (targetWidth < 1 || targetHeight < 1) {
            throw new IllegalArgumentException("Framebuffer dimensions must be positive");
        }
        if (layerCount < 0) {
            throw new IllegalArgumentException("layerCount must not be negative");
        }
        try {
            long pixels = Math.multiplyExact((long) targetWidth, targetHeight);
            long bytesPerPixel = Math.addExact(
                    4L,
                    Math.addExact(Math.multiplyExact((long) layerCount, 8L), allocateMask ? 8L : 0L));
            return Math.multiplyExact(pixels, bytesPerPixel);
        } catch (ArithmeticException exception) {
            throw new OpenGlRenderException("NanoVG path clip target size overflow", exception);
        }
    }

    private static long ownedFramebufferBytes(int targetWidth, int targetHeight) {
        try {
            return Math.multiplyExact(Math.multiplyExact((long) targetWidth, targetHeight), 8L);
        } catch (ArithmeticException exception) {
            throw new OpenGlRenderException("NanoVG owned framebuffer size overflow", exception);
        }
    }

    private static Framebuffer createBorrowed(OpenGlTarget target) {
        int framebuffer = glGenFramebuffers();
        int depthStencil = glGenRenderbuffers();
        if (framebuffer == 0 || depthStencil == 0) {
            if (framebuffer != 0) {
                glDeleteFramebuffers(framebuffer);
            }
            if (depthStencil != 0) {
                glDeleteRenderbuffers(depthStencil);
            }
            throw new OpenGlRenderException("OpenGL failed to allocate the NanoVG root framebuffer");
        }
        boolean complete = false;
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0,
                    GL_TEXTURE_2D,
                    target.colorTextureId(),
                    target.mipLevel());
            attachDepthStencil(depthStencil, target.width(), target.height());
            requireComplete("root");
            complete = true;
            return new Framebuffer(framebuffer, 0, depthStencil);
        } finally {
            if (!complete) {
                glDeleteFramebuffers(framebuffer);
                glDeleteRenderbuffers(depthStencil);
            }
        }
    }

    private static void reattachBorrowedColor(Framebuffer framebuffer, OpenGlTarget target) {
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer.framebuffer);
        glFramebufferTexture2D(
                GL_FRAMEBUFFER,
                GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D,
                target.colorTextureId(),
                target.mipLevel());
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
    }

    private static Framebuffer createOwned(int width, int height) {
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int colorTexture = 0;
        int framebuffer = 0;
        int depthStencil = 0;
        boolean complete = false;
        try {
            colorTexture = glGenTextures();
            framebuffer = glGenFramebuffers();
            depthStencil = glGenRenderbuffers();
            if (colorTexture == 0 || framebuffer == 0 || depthStencil == 0) {
                throw new OpenGlRenderException("OpenGL failed to allocate a NanoVG clip target");
            }
            glBindTexture(GL_TEXTURE_2D, colorTexture);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGBA8,
                    width,
                    height,
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0,
                    GL_TEXTURE_2D,
                    colorTexture,
                    0);
            attachDepthStencil(depthStencil, width, height);
            requireComplete("clip");
            complete = true;
            return new Framebuffer(framebuffer, colorTexture, depthStencil);
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glActiveTexture(activeTexture);
            if (!complete) {
                if (framebuffer != 0) {
                    glDeleteFramebuffers(framebuffer);
                }
                if (colorTexture != 0) {
                    glDeleteTextures(colorTexture);
                }
                if (depthStencil != 0) {
                    glDeleteRenderbuffers(depthStencil);
                }
            }
        }
    }

    private static void attachDepthStencil(int renderbuffer, int width, int height) {
        glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        glFramebufferRenderbuffer(
                GL_FRAMEBUFFER,
                GL_DEPTH_STENCIL_ATTACHMENT,
                GL_RENDERBUFFER,
                renderbuffer);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
    }

    private static void requireComplete(String description) {
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new OpenGlRenderException(
                    "NanoVG " + description + " framebuffer is incomplete: 0x"
                            + Integer.toHexString(status));
        }
    }

    private static TextureInfo inspectTexture(OpenGlTarget target) {
        if (!glIsTexture(target.colorTextureId())) {
            throw new OpenGlRenderException("Minecraft color handle is not a live OpenGL texture");
        }
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glBindTexture(GL_TEXTURE_2D, target.colorTextureId());
            int level = target.mipLevel();
            return new TextureInfo(
                    glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_INTERNAL_FORMAT),
                    glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_WIDTH),
                    glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_HEIGHT));
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glActiveTexture(activeTexture);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("NanoVG framebuffers are closed");
        }
    }

    private void requireReady() {
        requireOpen();
        if (root == null) {
            throw new IllegalStateException("NanoVG framebuffers have no target");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        deleteResources();
    }

    private void deleteResources() {
        delete(root);
        layers.forEach(NanoVgFramebuffers::delete);
        delete(mask);
        root = null;
        layers = List.of();
        mask = null;
        targetKey = null;
        width = 0;
        height = 0;
        allocatedBytes = 0L;
    }

    private static void delete(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return;
        }
        glDeleteFramebuffers(framebuffer.framebuffer);
        if (framebuffer.colorTexture != 0) {
            glDeleteTextures(framebuffer.colorTexture);
        }
        glDeleteRenderbuffers(framebuffer.depthStencil);
    }

    private record Framebuffer(int framebuffer, int colorTexture, int depthStencil) {
    }

    private record TextureInfo(int internalFormat, int width, int height) {
    }

    private record TargetKey(int texture, int mipLevel, int width, int height, long generation) {
        private static TargetKey from(OpenGlTarget target) {
            return new TargetKey(
                    target.colorTextureId(),
                    target.mipLevel(),
                    target.width(),
                    target.height(),
                    target.generationToken());
        }
    }
}
