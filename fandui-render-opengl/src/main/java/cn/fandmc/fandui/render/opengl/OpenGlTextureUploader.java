package cn.fandmc.fandui.render.opengl;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_MAX_TEXTURE_SIZE;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_UNPACK_LSB_FIRST;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ROW_LENGTH;
import static org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_ROWS;
import static org.lwjgl.opengl.GL11.GL_UNPACK_SWAP_BYTES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12.GL_UNPACK_IMAGE_HEIGHT;
import static org.lwjgl.opengl.GL12.GL_UNPACK_SKIP_IMAGES;
import static org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_R8;
import static org.lwjgl.opengl.GL30.GL_RED;
import static org.lwjgl.opengl.GL33.GL_TEXTURE_SWIZZLE_A;
import static org.lwjgl.opengl.GL33.GL_TEXTURE_SWIZZLE_B;
import static org.lwjgl.opengl.GL33.GL_TEXTURE_SWIZZLE_G;
import static org.lwjgl.opengl.GL33.GL_TEXTURE_SWIZZLE_R;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

final class OpenGlTextureUploader {
    private static GLCapabilities limitContext;
    private static int maximumTextureSize;

    private OpenGlTextureUploader() {
    }

    static int maximumTextureSize() {
        requireContext("texture size query");
        GLCapabilities current = GL.getCapabilities();
        if (limitContext != current) {
            int queried = glGetInteger(GL_MAX_TEXTURE_SIZE);
            if (queried < 1) {
                throw new OpenGlRenderException("OpenGL reported an invalid maximum texture size");
            }
            limitContext = current;
            maximumTextureSize = queried;
        }
        return maximumTextureSize;
    }

    static int create(
            int width,
            int height,
            int byteSize,
            ByteBuffer source,
            boolean alphaOnly,
            OpenGlSampling sampling,
            String description) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(description, "description");
        requireContext(description);
        if (width < 1 || height < 1 || byteSize < 1 || source.remaining() != byteSize) {
            throw new OpenGlRenderException(description + " has invalid pixel dimensions");
        }
        int maximumSize = maximumTextureSize();
        if (width > maximumSize || height > maximumSize) {
            throw new OpenGlRenderException(
                    description + " " + width + "x" + height
                            + " exceeds GL_MAX_TEXTURE_SIZE " + maximumSize);
        }

        ByteBuffer pixels = memAlloc(byteSize);
        try {
            pixels.put(source.duplicate()).flip();
            UploadState state = OpenGlPassScope.isActive() ? null : UploadState.capture();
            int texture = 0;
            try {
                UploadState.prepareUpload();
                texture = glGenTextures();
                if (texture == 0) {
                    throw new OpenGlRenderException("OpenGL did not allocate " + description);
                }
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        alphaOnly ? GL_R8 : GL_RGBA8,
                        width,
                        height,
                        0,
                        alphaOnly ? GL_RED : GL_RGBA,
                        GL_UNSIGNED_BYTE,
                        pixels);
                if (alphaOnly) {
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_R, GL_RED);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_G, GL_RED);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_B, GL_RED);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_A, GL_RED);
                }
                setSamplingParameters(sampling);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                return texture;
            } catch (RuntimeException | Error exception) {
                if (texture != 0) {
                    glDeleteTextures(texture);
                }
                throw exception;
            } finally {
                if (state != null) {
                    state.restore();
                }
            }
        } finally {
            memFree(pixels);
        }
    }

    static void configureSampling(int textureId, OpenGlSampling sampling) {
        Objects.requireNonNull(sampling, "sampling");
        requireContext("texture sampling update");
        if (textureId <= 0) {
            throw new IllegalArgumentException("textureId must be positive");
        }
        if (OpenGlPassScope.isActive()) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, textureId);
            setSamplingParameters(sampling);
            return;
        }
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glBindTexture(GL_TEXTURE_2D, textureId);
            setSamplingParameters(sampling);
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glActiveTexture(activeTexture);
        }
    }

    static void delete(int textureId) {
        glDeleteTextures(textureId);
    }

    private static void setSamplingParameters(OpenGlSampling sampling) {
        int filter = sampling == OpenGlSampling.NEAREST ? GL_NEAREST : GL_LINEAR;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    }

    private static void requireContext(String operation) {
        if (GL.getCapabilities() == null) {
            throw new OpenGlRenderException("No current OpenGL context for " + operation);
        }
    }

    private record UploadState(
            int activeTexture,
            int texture2d,
            int unpackBuffer,
            int swapBytes,
            int leastSignificantBitFirst,
            int rowLength,
            int skipRows,
            int skipPixels,
            int alignment,
            int imageHeight,
            int skipImages) {
        private static UploadState capture() {
            int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0);
            int texture2d = glGetInteger(GL_TEXTURE_BINDING_2D);
            return new UploadState(
                    activeTexture,
                    texture2d,
                    glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING),
                    glGetInteger(GL_UNPACK_SWAP_BYTES),
                    glGetInteger(GL_UNPACK_LSB_FIRST),
                    glGetInteger(GL_UNPACK_ROW_LENGTH),
                    glGetInteger(GL_UNPACK_SKIP_ROWS),
                    glGetInteger(GL_UNPACK_SKIP_PIXELS),
                    glGetInteger(GL_UNPACK_ALIGNMENT),
                    glGetInteger(GL_UNPACK_IMAGE_HEIGHT),
                    glGetInteger(GL_UNPACK_SKIP_IMAGES));
        }

        private static void prepareUpload() {
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

        private void restore() {
            glBindTexture(GL_TEXTURE_2D, texture2d);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            glPixelStorei(GL_UNPACK_SWAP_BYTES, swapBytes);
            glPixelStorei(GL_UNPACK_LSB_FIRST, leastSignificantBitFirst);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, rowLength);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, skipRows);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, skipPixels);
            glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
            glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, imageHeight);
            glPixelStorei(GL_UNPACK_SKIP_IMAGES, skipImages);
            glActiveTexture(activeTexture);
        }
    }
}
