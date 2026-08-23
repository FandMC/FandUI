package cn.fandmc.fandui.render.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.glBindSampler;

final class NanoVgLayerCompositor implements AutoCloseable {
    private static final String VERTEX_SHADER = """
            #version 150 core
            void main() {
                vec2 position;
                if (gl_VertexID == 0) {
                    position = vec2(-1.0, -1.0);
                } else if (gl_VertexID == 1) {
                    position = vec2(3.0, -1.0);
                } else {
                    position = vec2(-1.0, 3.0);
                }
                gl_Position = vec4(position, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 150 core
            uniform sampler2D childTexture;
            uniform sampler2D maskTexture;
            uniform int mode;
            out vec4 outColor;
            void main() {
                ivec2 pixel = ivec2(gl_FragCoord.xy);
                float mask = texelFetch(maskTexture, pixel, 0).a;
                if (mode == 0) {
                    outColor = vec4(0.0, 0.0, 0.0, mask);
                } else {
                    outColor = texelFetch(childTexture, pixel, 0) * mask;
                }
            }
            """;

    private int program;
    private int vertexArray;
    private int childLocation;
    private int maskLocation;
    private int modeLocation;

    int composite(
            int parentFramebuffer,
            int childTexture,
            int maskTexture,
            int width,
            int height) {
        ensurePipeline();
        glBindFramebuffer(GL_FRAMEBUFFER, parentFramebuffer);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glViewport(0, 0, width, height);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_COLOR_LOGIC_OP);
        glDisable(GL_FRAMEBUFFER_SRGB);
        glDisable(GL_DITHER);
        glColorMask(true, true, true, true);
        glUseProgram(program);
        glBindVertexArray(vertexArray);
        glActiveTexture(GL_TEXTURE0);
        glBindSampler(0, 0);
        glBindTexture(GL_TEXTURE_2D, childTexture);
        glActiveTexture(GL_TEXTURE1);
        glBindSampler(1, 0);
        glBindTexture(GL_TEXTURE_2D, maskTexture);
        glUniform1i(childLocation, 0);
        glUniform1i(maskLocation, 1);
        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);

        glUniform1i(modeLocation, 0);
        glBlendFuncSeparate(
                GL_ZERO,
                GL_ONE_MINUS_SRC_ALPHA,
                GL_ZERO,
                GL_ONE_MINUS_SRC_ALPHA);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        glUniform1i(modeLocation, 1);
        glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ONE, GL_ONE);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        return 2;
    }

    private void ensurePipeline() {
        if (program != 0) {
            return;
        }
        int vertexShader = compile(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compile(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int nextProgram = 0;
        int nextVertexArray = 0;
        boolean complete = false;
        try {
            nextProgram = glCreateProgram();
            if (nextProgram == 0) {
                throw new OpenGlRenderException("OpenGL failed to allocate the clip compositor program");
            }
            glAttachShader(nextProgram, vertexShader);
            glAttachShader(nextProgram, fragmentShader);
            glLinkProgram(nextProgram);
            if (glGetProgrami(nextProgram, GL_LINK_STATUS) == GL_FALSE) {
                throw new OpenGlRenderException(
                        "Failed to link NanoVG clip compositor: " + glGetProgramInfoLog(nextProgram));
            }
            nextVertexArray = glGenVertexArrays();
            if (nextVertexArray == 0) {
                throw new OpenGlRenderException("OpenGL failed to allocate the clip compositor VAO");
            }

            int nextChildLocation = glGetUniformLocation(nextProgram, "childTexture");
            int nextMaskLocation = glGetUniformLocation(nextProgram, "maskTexture");
            int nextModeLocation = glGetUniformLocation(nextProgram, "mode");
            if (nextChildLocation < 0 || nextMaskLocation < 0 || nextModeLocation < 0) {
                throw new OpenGlRenderException("NanoVG clip compositor uniforms are unavailable");
            }

            program = nextProgram;
            vertexArray = nextVertexArray;
            childLocation = nextChildLocation;
            maskLocation = nextMaskLocation;
            modeLocation = nextModeLocation;
            complete = true;
        } finally {
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            if (!complete) {
                if (nextProgram != 0) {
                    glDeleteProgram(nextProgram);
                }
                if (nextVertexArray != 0) {
                    glDeleteVertexArrays(nextVertexArray);
                }
            }
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        if (shader == 0) {
            throw new OpenGlRenderException("OpenGL failed to allocate a clip compositor shader");
        }
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new OpenGlRenderException("Failed to compile NanoVG clip compositor: " + log);
        }
        return shader;
    }

    @Override
    public void close() {
        if (vertexArray != 0) {
            glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        if (program != 0) {
            glDeleteProgram(program);
            program = 0;
        }
        childLocation = 0;
        maskLocation = 0;
        modeLocation = 0;
    }
}
