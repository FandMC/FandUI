package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.render.opengl.internal.GlStateSnapshot;
import java.util.Objects;
import java.util.Optional;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Temporary GPU go/no-go probe. It is inert unless {@code fandui.openglProbe=true}.
 */
public final class OpenGlProbe implements AutoCloseable {
    public static final String ENABLE_PROPERTY = "fandui.openglProbe";
    public static final String ASSERT_STATE_PROPERTY = "fandui.openglProbe.assertState";

    private static final String VERTEX_SHADER = """
            #version 150
            const vec2 POSITIONS[3] = vec2[](
                vec2(-0.96, 0.96),
                vec2(-0.78, 0.96),
                vec2(-0.96, 0.74)
            );
            void main() {
                gl_Position = vec4(POSITIONS[gl_VertexID], 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 150
            out vec4 fragColor;
            void main() {
                fragColor = vec4(0.12, 0.52, 0.76, 0.80);
            }
            """;

    private final boolean enabled;
    private final boolean assertState;
    private TargetKey targetKey;
    private int framebuffer;
    private int depthStencilRenderbuffer;
    private int vertexArray;
    private int program;
    private int internalFormat;

    public OpenGlProbe() {
        this(Boolean.getBoolean(ENABLE_PROPERTY),
                Boolean.parseBoolean(System.getProperty(ASSERT_STATE_PROPERTY, "true")));
    }

    OpenGlProbe(boolean enabled, boolean assertState) {
        this.enabled = enabled;
        this.assertState = assertState;
    }

    public boolean enabled() {
        return enabled;
    }

    public OpenGlProbeReport render(RenderHost host) {
        Objects.requireNonNull(host, "host");
        if (!enabled) {
            return report(OpenGlProbeReport.Status.DISABLED, host.name(), 0, 0);
        }

        host.assertRenderThread();
        Optional<OpenGlTarget> target = host.currentTarget();
        if (target.isEmpty()) {
            return report(OpenGlProbeReport.Status.NO_TARGET, host.name(), 0, 0);
        }

        return renderTarget(host.name(), target.orElseThrow());
    }

    private OpenGlProbeReport renderTarget(String hostName, OpenGlTarget target) {
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        RuntimeException primaryFailure = null;
        boolean rebuilt = false;
        try {
            ensureProgram();
            TargetKey nextKey = TargetKey.from(target);
            if (!nextKey.equals(targetKey)) {
                rebuildTarget(target, nextKey);
                rebuilt = true;
            }
            drawProbe(target);
            return report(
                    rebuilt ? OpenGlProbeReport.Status.TARGET_REBUILT : OpenGlProbeReport.Status.RENDERED,
                    hostName,
                    target.width(),
                    target.height());
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            try {
                snapshot.restore();
                if (assertState) {
                    snapshot.assertRestored();
                }
            } catch (RuntimeException restoreFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(restoreFailure);
                } else {
                    throw restoreFailure;
                }
            }
        }
    }

    private void rebuildTarget(OpenGlTarget target, TargetKey nextKey) {
        TextureInfo textureInfo = inspectTexture(target);
        if (textureInfo.internalFormat() != GL_RGBA8) {
            throw new OpenGlProbeException(
                    "Unsupported main color format 0x" + Integer.toHexString(textureInfo.internalFormat()));
        }
        if (textureInfo.width() != target.width() || textureInfo.height() != target.height()) {
            throw new OpenGlProbeException(
                    "Main color texture dimensions " + textureInfo.width() + "x" + textureInfo.height()
                            + " do not match target " + target.width() + "x" + target.height());
        }

        int newFramebuffer = glGenFramebuffers();
        int newRenderbuffer = glGenRenderbuffers();
        boolean complete = false;
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, newFramebuffer);
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0,
                    GL_TEXTURE_2D,
                    target.colorTextureId(),
                    target.mipLevel());

            glBindRenderbuffer(GL_RENDERBUFFER, newRenderbuffer);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, target.width(), target.height());
            glFramebufferRenderbuffer(
                    GL_FRAMEBUFFER,
                    GL_DEPTH_STENCIL_ATTACHMENT,
                    GL_RENDERBUFFER,
                    newRenderbuffer);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            glReadBuffer(GL_COLOR_ATTACHMENT0);

            int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new OpenGlProbeException(
                        "FandUI probe framebuffer is incomplete: 0x" + Integer.toHexString(status));
            }
            complete = true;
        } finally {
            if (!complete) {
                glDeleteFramebuffers(newFramebuffer);
                glDeleteRenderbuffers(newRenderbuffer);
            }
        }

        deleteTargetResources();
        framebuffer = newFramebuffer;
        depthStencilRenderbuffer = newRenderbuffer;
        internalFormat = textureInfo.internalFormat();
        targetKey = nextKey;
    }

    private TextureInfo inspectTexture(OpenGlTarget target) {
        if (!glIsTexture(target.colorTextureId())) {
            throw new OpenGlProbeException("Minecraft color handle is not a live OpenGL texture");
        }

        int previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
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
            glActiveTexture(previousActiveTexture);
        }
    }

    private void ensureProgram() {
        if (program != 0) {
            return;
        }

        int vertexShader = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int newProgram = glCreateProgram();
        try {
            glAttachShader(newProgram, vertexShader);
            glAttachShader(newProgram, fragmentShader);
            glLinkProgram(newProgram);
            if (glGetProgrami(newProgram, GL_LINK_STATUS) == GL_FALSE) {
                throw new OpenGlProbeException("Probe program link failed: " + glGetProgramInfoLog(newProgram));
            }
            vertexArray = glGenVertexArrays();
            program = newProgram;
            newProgram = 0;
        } finally {
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            if (newProgram != 0) {
                glDeleteProgram(newProgram);
            }
        }
    }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new OpenGlProbeException("Probe shader compile failed: " + log);
        }
        return shader;
    }

    private void drawProbe(OpenGlTarget target) {
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glViewport(0, 0, target.width(), target.height());
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glDisable(GL_CULL_FACE);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_STENCIL_TEST);
        glEnable(GL_BLEND);
        glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(true);
        glStencilMaskSeparate(GL_FRONT, 0xFF);
        glStencilMaskSeparate(GL_BACK, 0xFF);
        glClearBufferfi(GL_DEPTH_STENCIL, 0, 1.0f, 0);

        glUseProgram(program);
        glBindVertexArray(vertexArray);
        glColorMask(false, false, false, false);
        glStencilFuncSeparate(GL_FRONT, GL_ALWAYS, 1, 0xFF);
        glStencilFuncSeparate(GL_BACK, GL_ALWAYS, 1, 0xFF);
        glStencilOpSeparate(GL_FRONT, GL_KEEP, GL_KEEP, GL_REPLACE);
        glStencilOpSeparate(GL_BACK, GL_KEEP, GL_KEEP, GL_REPLACE);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        glColorMask(true, true, true, true);
        glStencilMaskSeparate(GL_FRONT, 0);
        glStencilMaskSeparate(GL_BACK, 0);
        glStencilFuncSeparate(GL_FRONT, GL_EQUAL, 1, 0xFF);
        glStencilFuncSeparate(GL_BACK, GL_EQUAL, 1, 0xFF);
        glStencilOpSeparate(GL_FRONT, GL_KEEP, GL_KEEP, GL_KEEP);
        glStencilOpSeparate(GL_BACK, GL_KEEP, GL_KEEP, GL_KEEP);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    private OpenGlProbeReport report(OpenGlProbeReport.Status status, String hostName, int width, int height) {
        return new OpenGlProbeReport(status, hostName, framebuffer, internalFormat, width, height);
    }

    @Override
    public void close() {
        deleteTargetResources();
        if (vertexArray != 0) {
            glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        if (program != 0) {
            glDeleteProgram(program);
            program = 0;
        }
    }

    private void deleteTargetResources() {
        if (framebuffer != 0) {
            glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (depthStencilRenderbuffer != 0) {
            glDeleteRenderbuffers(depthStencilRenderbuffer);
            depthStencilRenderbuffer = 0;
        }
        targetKey = null;
        internalFormat = 0;
    }

    private record TargetKey(int texture, int mipLevel, int width, int height, long generationToken) {
        private static TargetKey from(OpenGlTarget target) {
            return new TargetKey(
                    target.colorTextureId(),
                    target.mipLevel(),
                    target.width(),
                    target.height(),
                    target.generationToken());
        }
    }

    private record TextureInfo(int internalFormat, int width, int height) {
    }
}

