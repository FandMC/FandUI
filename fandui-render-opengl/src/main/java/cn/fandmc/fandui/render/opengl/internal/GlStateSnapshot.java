package cn.fandmc.fandui.render.opengl.internal;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBClipControl;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

/** Captures and restores the OpenGL state touched by the FandUI renderer. */
public final class GlStateSnapshot {
    private final int[] integerScratch = new int[4];
    private final ByteBuffer booleanScratch = BufferUtils.createByteBuffer(4);
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private final float[] clearColor = new float[4];
    private final boolean[] colorMask = new boolean[4];
    private final double[] depthRange = new double[2];
    private final BufferBindings bufferBindings = new BufferBindings();
    private final TextureBindings textureBindings = new TextureBindings();
    private final ClipControl clipControl = new ClipControl();
    private final StencilFace frontStencil = new StencilFace();
    private final StencilFace backStencil = new StencilFace();
    private final PixelStore pixelStore = new PixelStore();

    private int drawFramebuffer;
    private int readFramebuffer;
    private int renderbuffer;
    private int program;
    private int vertexArray;
    private int clearStencil;
    private int polygonMode;
    private boolean blend;
    private boolean depthTest;
    private boolean stencilTest;
    private boolean scissorTest;
    private boolean cullFace;
    private boolean colorLogicOperation;
    private int logicOperation;
    private boolean framebufferSrgb;
    private boolean dither;
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private int blendEquationRgb;
    private int blendEquationAlpha;
    private boolean depthMask;
    private int depthFunction;
    private int frontFace;

    private GlStateSnapshot() {
    }

    /** Creates and fills a standalone snapshot for probes and one-shot callers. */
    public static GlStateSnapshot capture() {
        return reusable().recapture();
    }

    /** Creates a snapshot whose storage can be reused by calling {@link #recapture()}. */
    public static GlStateSnapshot reusable() {
        return new GlStateSnapshot();
    }

    /** Replaces this instance's values with the current context state. */
    public GlStateSnapshot recapture() {
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissorBox);
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
        glGetDoublev(GL_DEPTH_RANGE, depthRange);

        booleanScratch.clear();
        glGetBooleanv(GL_COLOR_WRITEMASK, booleanScratch);
        for (int index = 0; index < colorMask.length; index++) {
            colorMask[index] = booleanScratch.get(index) != 0;
        }

        drawFramebuffer = integer(GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = integer(GL_READ_FRAMEBUFFER_BINDING);
        renderbuffer = integer(GL_RENDERBUFFER_BINDING);
        program = integer(GL_CURRENT_PROGRAM);
        vertexArray = integer(GL_VERTEX_ARRAY_BINDING);
        bufferBindings.capture(this);
        textureBindings.capture(this);
        clearStencil = integer(GL_STENCIL_CLEAR_VALUE);
        polygonMode = integer(GL_POLYGON_MODE);
        blend = glIsEnabled(GL_BLEND);
        depthTest = glIsEnabled(GL_DEPTH_TEST);
        stencilTest = glIsEnabled(GL_STENCIL_TEST);
        scissorTest = glIsEnabled(GL_SCISSOR_TEST);
        cullFace = glIsEnabled(GL_CULL_FACE);
        colorLogicOperation = glIsEnabled(GL_COLOR_LOGIC_OP);
        logicOperation = integer(GL_LOGIC_OP_MODE);
        framebufferSrgb = glIsEnabled(GL_FRAMEBUFFER_SRGB);
        dither = glIsEnabled(GL_DITHER);
        blendSrcRgb = integer(GL_BLEND_SRC_RGB);
        blendDstRgb = integer(GL_BLEND_DST_RGB);
        blendSrcAlpha = integer(GL_BLEND_SRC_ALPHA);
        blendDstAlpha = integer(GL_BLEND_DST_ALPHA);
        blendEquationRgb = integer(GL_BLEND_EQUATION_RGB);
        blendEquationAlpha = integer(GL_BLEND_EQUATION_ALPHA);
        booleanScratch.clear();
        glGetBooleanv(GL_DEPTH_WRITEMASK, booleanScratch);
        depthMask = booleanScratch.get(0) != 0;
        depthFunction = integer(GL_DEPTH_FUNC);
        clipControl.capture(this);
        frontFace = integer(GL_FRONT_FACE);
        frontStencil.capture(this, false);
        backStencil.capture(this, true);
        pixelStore.capture(this);
        return this;
    }

    public void restore() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
        glUseProgram(program);
        glBindVertexArray(vertexArray);
        bufferBindings.restore();
        textureBindings.restore();
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        glClearStencil(clearStencil);
        glPolygonMode(GL_FRONT_AND_BACK, polygonMode);
        setEnabled(GL_BLEND, blend);
        setEnabled(GL_DEPTH_TEST, depthTest);
        setEnabled(GL_STENCIL_TEST, stencilTest);
        setEnabled(GL_SCISSOR_TEST, scissorTest);
        setEnabled(GL_CULL_FACE, cullFace);
        setEnabled(GL_COLOR_LOGIC_OP, colorLogicOperation);
        glLogicOp(logicOperation);
        setEnabled(GL_FRAMEBUFFER_SRGB, framebufferSrgb);
        setEnabled(GL_DITHER, dither);
        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        glDepthMask(depthMask);
        glDepthFunc(depthFunction);
        clipControl.restore();
        glDepthRange(depthRange[0], depthRange[1]);
        glFrontFace(frontFace);
        frontStencil.restore(GL_FRONT);
        backStencil.restore(GL_BACK);
        pixelStore.restore();
    }

    /** Captures a temporary comparison snapshot and reports any restoration mismatch. */
    public void assertRestored() {
        assertRestored(capture());
    }

    /** Compares against a caller-owned snapshot, allowing assertion storage to be reused. */
    public void assertRestored(GlStateSnapshot actual) {
        List<String> differences = differences(actual);
        if (!differences.isEmpty()) {
            throw new IllegalStateException("OpenGL state restoration mismatch: " + String.join(", ", differences));
        }
    }

    private int integer(int parameter) {
        glGetIntegerv(parameter, integerScratch);
        return integerScratch[0];
    }

    private List<String> differences(GlStateSnapshot actual) {
        List<String> differences = new ArrayList<>();
        compare(differences, "drawFramebuffer", drawFramebuffer, actual.drawFramebuffer);
        compare(differences, "readFramebuffer", readFramebuffer, actual.readFramebuffer);
        compare(differences, "renderbuffer", renderbuffer, actual.renderbuffer);
        compare(differences, "program", program, actual.program);
        compare(differences, "vertexArray", vertexArray, actual.vertexArray);
        compare(differences, "bufferBindings", bufferBindings, actual.bufferBindings);
        compare(differences, "textureBindings", textureBindings, actual.textureBindings);
        compare(differences, "viewport", viewport, actual.viewport);
        compare(differences, "scissorBox", scissorBox, actual.scissorBox);
        compare(differences, "clearColor", clearColor, actual.clearColor);
        compare(differences, "clearStencil", clearStencil, actual.clearStencil);
        compare(differences, "polygonMode", polygonMode, actual.polygonMode);
        compare(differences, "blend", blend, actual.blend);
        compare(differences, "depthTest", depthTest, actual.depthTest);
        compare(differences, "stencilTest", stencilTest, actual.stencilTest);
        compare(differences, "scissorTest", scissorTest, actual.scissorTest);
        compare(differences, "cullFace", cullFace, actual.cullFace);
        compare(differences, "colorLogicOperation", colorLogicOperation, actual.colorLogicOperation);
        compare(differences, "logicOperation", logicOperation, actual.logicOperation);
        compare(differences, "framebufferSrgb", framebufferSrgb, actual.framebufferSrgb);
        compare(differences, "dither", dither, actual.dither);
        compare(differences, "blendSrcRgb", blendSrcRgb, actual.blendSrcRgb);
        compare(differences, "blendDstRgb", blendDstRgb, actual.blendDstRgb);
        compare(differences, "blendSrcAlpha", blendSrcAlpha, actual.blendSrcAlpha);
        compare(differences, "blendDstAlpha", blendDstAlpha, actual.blendDstAlpha);
        compare(differences, "blendEquationRgb", blendEquationRgb, actual.blendEquationRgb);
        compare(differences, "blendEquationAlpha", blendEquationAlpha, actual.blendEquationAlpha);
        compare(differences, "colorMask", colorMask, actual.colorMask);
        compare(differences, "depthMask", depthMask, actual.depthMask);
        compare(differences, "depthFunction", depthFunction, actual.depthFunction);
        compare(differences, "depthRange", depthRange, actual.depthRange);
        compare(differences, "clipControl", clipControl.same(actual.clipControl));
        compare(differences, "frontFace", frontFace, actual.frontFace);
        compare(differences, "frontStencil", frontStencil, actual.frontStencil);
        compare(differences, "backStencil", backStencil, actual.backStencil);
        compare(differences, "pixelStore", pixelStore, actual.pixelStore);
        return differences;
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            glEnable(capability);
        } else {
            glDisable(capability);
        }
    }

    private static void compare(List<String> differences, String name, int expected, int actual) {
        if (expected != actual) {
            differences.add(name + "[expected=" + expected + ", actual=" + actual + "]");
        }
    }

    private static void compare(List<String> differences, String name, boolean expected, boolean actual) {
        if (expected != actual) {
            differences.add(name + "[expected=" + expected + ", actual=" + actual + "]");
        }
    }

    private static void compare(List<String> differences, String name, int[] expected, int[] actual) {
        if (!Arrays.equals(expected, actual)) {
            differences.add(name + "[expected=" + Arrays.toString(expected)
                    + ", actual=" + Arrays.toString(actual) + "]");
        }
    }

    private static void compare(List<String> differences, String name, float[] expected, float[] actual) {
        if (!Arrays.equals(expected, actual)) {
            differences.add(name + "[expected=" + Arrays.toString(expected)
                    + ", actual=" + Arrays.toString(actual) + "]");
        }
    }

    private static void compare(List<String> differences, String name, double[] expected, double[] actual) {
        if (!Arrays.equals(expected, actual)) {
            differences.add(name + "[expected=" + Arrays.toString(expected)
                    + ", actual=" + Arrays.toString(actual) + "]");
        }
    }

    private static void compare(List<String> differences, String name, boolean[] expected, boolean[] actual) {
        if (!Arrays.equals(expected, actual)) {
            differences.add(name + "[expected=" + Arrays.toString(expected)
                    + ", actual=" + Arrays.toString(actual) + "]");
        }
    }

    private static void compare(
            List<String> differences,
            String name,
            BufferBindings expected,
            BufferBindings actual) {
        if (!expected.same(actual)) {
            differences.add(name + "[expected=" + expected + ", actual=" + actual + "]");
        }
    }

    private static void compare(
            List<String> differences,
            String name,
            TextureBindings expected,
            TextureBindings actual) {
        if (!expected.same(actual)) {
            differences.add(name + "[expected=" + expected + ", actual=" + actual + "]");
        }
    }

    private static void compare(List<String> differences, String name, StencilFace expected, StencilFace actual) {
        if (!expected.same(actual)) {
            differences.add(name + "[expected=" + expected + ", actual=" + actual + "]");
        }
    }

    private static void compare(List<String> differences, String name, PixelStore expected, PixelStore actual) {
        if (!expected.same(actual)) {
            differences.add(name + "[expected=" + expected + ", actual=" + actual + "]");
        }
    }

    private static void compare(List<String> differences, String name, boolean same) {
        if (!same) {
            differences.add(name);
        }
    }

    private static final class ClipControl {
        private boolean supported;
        private int origin;
        private int depthMode;

        private void capture(GlStateSnapshot snapshot) {
            supported = GL.getCapabilities().OpenGL45 || GL.getCapabilities().GL_ARB_clip_control;
            if (supported) {
                origin = snapshot.integer(ARBClipControl.GL_CLIP_ORIGIN);
                depthMode = snapshot.integer(ARBClipControl.GL_CLIP_DEPTH_MODE);
            } else {
                origin = 0;
                depthMode = 0;
            }
        }

        private void restore() {
            if (supported) {
                ARBClipControl.glClipControl(origin, depthMode);
            }
        }

        private boolean same(ClipControl other) {
            return supported == other.supported && origin == other.origin && depthMode == other.depthMode;
        }
    }

    private static final class StencilFace {
        private int func;
        private int ref;
        private int valueMask;
        private int fail;
        private int depthFail;
        private int depthPass;
        private int writeMask;

        private void capture(GlStateSnapshot snapshot, boolean back) {
            func = snapshot.integer(back ? GL_STENCIL_BACK_FUNC : GL_STENCIL_FUNC);
            ref = snapshot.integer(back ? GL_STENCIL_BACK_REF : GL_STENCIL_REF);
            valueMask = snapshot.integer(back ? GL_STENCIL_BACK_VALUE_MASK : GL_STENCIL_VALUE_MASK);
            fail = snapshot.integer(back ? GL_STENCIL_BACK_FAIL : GL_STENCIL_FAIL);
            depthFail = snapshot.integer(back ? GL_STENCIL_BACK_PASS_DEPTH_FAIL : GL_STENCIL_PASS_DEPTH_FAIL);
            depthPass = snapshot.integer(back ? GL_STENCIL_BACK_PASS_DEPTH_PASS : GL_STENCIL_PASS_DEPTH_PASS);
            writeMask = snapshot.integer(back ? GL_STENCIL_BACK_WRITEMASK : GL_STENCIL_WRITEMASK);
        }

        private void restore(int face) {
            glStencilFuncSeparate(face, func, ref, valueMask);
            glStencilOpSeparate(face, fail, depthFail, depthPass);
            glStencilMaskSeparate(face, writeMask);
        }

        private boolean same(StencilFace other) {
            return func == other.func
                    && ref == other.ref
                    && valueMask == other.valueMask
                    && fail == other.fail
                    && depthFail == other.depthFail
                    && depthPass == other.depthPass
                    && writeMask == other.writeMask;
        }

        @Override
        public String toString() {
            return "func=" + func
                    + "/ref=" + ref
                    + "/valueMask=" + valueMask
                    + "/ops=" + fail + ':' + depthFail + ':' + depthPass
                    + "/writeMask=" + writeMask;
        }
    }

    private static final class BufferBindings {
        private int arrayBuffer;
        private int pixelUnpackBuffer;

        private void capture(GlStateSnapshot snapshot) {
            arrayBuffer = snapshot.integer(GL_ARRAY_BUFFER_BINDING);
            pixelUnpackBuffer = snapshot.integer(GL_PIXEL_UNPACK_BUFFER_BINDING);
        }

        private void restore() {
            glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pixelUnpackBuffer);
        }

        private boolean same(BufferBindings other) {
            return arrayBuffer == other.arrayBuffer && pixelUnpackBuffer == other.pixelUnpackBuffer;
        }

        @Override
        public String toString() {
            return "array=" + arrayBuffer + "/pixelUnpack=" + pixelUnpackBuffer;
        }
    }

    private static final class TextureBindings {
        private int activeTexture;
        private int texture2dUnit0;
        private int texture2dUnit1;
        private int textureBufferUnit1;
        private int samplerUnit0;
        private int samplerUnit1;

        private void capture(GlStateSnapshot snapshot) {
            activeTexture = snapshot.integer(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0);
            texture2dUnit0 = snapshot.integer(GL_TEXTURE_BINDING_2D);
            samplerUnit0 = snapshot.integer(GL_SAMPLER_BINDING);
            glActiveTexture(GL_TEXTURE1);
            texture2dUnit1 = snapshot.integer(GL_TEXTURE_BINDING_2D);
            textureBufferUnit1 = snapshot.integer(GL_TEXTURE_BINDING_BUFFER);
            samplerUnit1 = snapshot.integer(GL_SAMPLER_BINDING);
            glActiveTexture(activeTexture);
        }

        private void restore() {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture2dUnit0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, texture2dUnit1);
            glBindTexture(GL_TEXTURE_BUFFER, textureBufferUnit1);
            glBindSampler(0, samplerUnit0);
            glBindSampler(1, samplerUnit1);
            glActiveTexture(activeTexture);
        }

        private boolean same(TextureBindings other) {
            return activeTexture == other.activeTexture
                    && texture2dUnit0 == other.texture2dUnit0
                    && texture2dUnit1 == other.texture2dUnit1
                    && textureBufferUnit1 == other.textureBufferUnit1
                    && samplerUnit0 == other.samplerUnit0
                    && samplerUnit1 == other.samplerUnit1;
        }

        @Override
        public String toString() {
            return "active=" + activeTexture
                    + "/2d0=" + texture2dUnit0
                    + "/2d1=" + texture2dUnit1
                    + "/buffer1=" + textureBufferUnit1
                    + "/sampler0=" + samplerUnit0
                    + "/sampler1=" + samplerUnit1;
        }
    }

    private static final class PixelStore {
        private int unpackSwapBytes;
        private int unpackLeastSignificantBitFirst;
        private int unpackRowLength;
        private int unpackSkipRows;
        private int unpackSkipPixels;
        private int unpackAlignment;
        private int unpackImageHeight;
        private int unpackSkipImages;

        private void capture(GlStateSnapshot snapshot) {
            unpackSwapBytes = snapshot.integer(GL_UNPACK_SWAP_BYTES);
            unpackLeastSignificantBitFirst = snapshot.integer(GL_UNPACK_LSB_FIRST);
            unpackRowLength = snapshot.integer(GL_UNPACK_ROW_LENGTH);
            unpackSkipRows = snapshot.integer(GL_UNPACK_SKIP_ROWS);
            unpackSkipPixels = snapshot.integer(GL_UNPACK_SKIP_PIXELS);
            unpackAlignment = snapshot.integer(GL_UNPACK_ALIGNMENT);
            unpackImageHeight = snapshot.integer(GL_UNPACK_IMAGE_HEIGHT);
            unpackSkipImages = snapshot.integer(GL_UNPACK_SKIP_IMAGES);
        }

        private void restore() {
            glPixelStorei(GL_UNPACK_SWAP_BYTES, unpackSwapBytes);
            glPixelStorei(GL_UNPACK_LSB_FIRST, unpackLeastSignificantBitFirst);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, unpackRowLength);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, unpackSkipRows);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
            glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment);
            glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, unpackImageHeight);
            glPixelStorei(GL_UNPACK_SKIP_IMAGES, unpackSkipImages);
        }

        private boolean same(PixelStore other) {
            return unpackSwapBytes == other.unpackSwapBytes
                    && unpackLeastSignificantBitFirst == other.unpackLeastSignificantBitFirst
                    && unpackRowLength == other.unpackRowLength
                    && unpackSkipRows == other.unpackSkipRows
                    && unpackSkipPixels == other.unpackSkipPixels
                    && unpackAlignment == other.unpackAlignment
                    && unpackImageHeight == other.unpackImageHeight
                    && unpackSkipImages == other.unpackSkipImages;
        }

        @Override
        public String toString() {
            return "swap=" + unpackSwapBytes
                    + "/lsb=" + unpackLeastSignificantBitFirst
                    + "/rowLength=" + unpackRowLength
                    + "/skipRows=" + unpackSkipRows
                    + "/skipPixels=" + unpackSkipPixels
                    + "/alignment=" + unpackAlignment
                    + "/imageHeight=" + unpackImageHeight
                    + "/skipImages=" + unpackSkipImages;
        }
    }
}
