package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.layout.Rect;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.glBindSampler;

final class NanoVgBackdropBlur implements AutoCloseable {
    static final long DEFAULT_BYTE_LIMIT = 64L * 1024L * 1024L;
    private static final int BASE_DOWNSAMPLE = 2;
    private static final float MAX_DOWNSAMPLED_RADIUS = 12.0f;
    private static final float MIN_SIGMA = 0.5f;
    private static final int PLAN_CACHE_SIZE = 8;

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

    private static final String BLUR_FRAGMENT_SHADER = """
            #version 150 core
            uniform sampler2D sourceTexture;
            uniform vec2 allocatedSize;
            uniform vec2 validSize;
            uniform vec2 direction;
            uniform float centerWeight;
            uniform vec2 pairWeights;
            uniform vec2 pairOffsets;
            out vec4 outColor;

            vec4 sampleAt(float offset) {
                vec2 pixel = clamp(
                    gl_FragCoord.xy + direction * offset,
                    vec2(0.5),
                    validSize - vec2(0.5));
                return texture(sourceTexture, pixel / allocatedSize);
            }

            void main() {
                vec4 color = sampleAt(0.0) * centerWeight;
                color += (sampleAt(pairOffsets.x) + sampleAt(-pairOffsets.x)) * pairWeights.x;
                color += (sampleAt(pairOffsets.y) + sampleAt(-pairOffsets.y)) * pairWeights.y;
                outColor = color;
            }
            """;

    private static final String DOWNSAMPLE_FRAGMENT_SHADER = """
            #version 150 core
            uniform sampler2D sourceTexture;
            uniform vec2 allocatedSize;
            uniform vec2 validSize;
            uniform vec2 destinationSize;
            out vec4 outColor;

            vec4 sampleAt(vec2 offset) {
                vec2 sourcePixel = clamp(
                    (gl_FragCoord.xy / destinationSize) * validSize + offset,
                    vec2(0.5),
                    validSize - vec2(0.5));
                return texture(sourceTexture, sourcePixel / allocatedSize);
            }

            void main() {
                vec4 color = sampleAt(vec2(0.0)) * 0.25;
                color += (sampleAt(vec2(-1.0, 0.0))
                    + sampleAt(vec2(1.0, 0.0))
                    + sampleAt(vec2(0.0, -1.0))
                    + sampleAt(vec2(0.0, 1.0))) * 0.125;
                color += (sampleAt(vec2(-1.0, -1.0))
                    + sampleAt(vec2(1.0, -1.0))
                    + sampleAt(vec2(-1.0, 1.0))
                    + sampleAt(vec2(1.0, 1.0))) * 0.0625;
                outColor = color;
            }
            """;

    private static final String COMPOSITE_FRAGMENT_SHADER = """
            #version 150 core
            uniform sampler2D blurredTexture;
            uniform sampler2D maskTexture;
            uniform int analyticMask;
            uniform vec4 maskRect;
            uniform float maskAlpha;
            uniform vec2 targetSize;
            uniform vec2 allocatedSize;
            uniform vec2 validSize;
            out vec4 outColor;

            float resolveMask() {
                if (analyticMask != 0) {
                    vec2 edgeDistance = min(
                        gl_FragCoord.xy - maskRect.xy,
                        maskRect.zw - gl_FragCoord.xy);
                    vec2 coverage = clamp(edgeDistance + vec2(0.5), vec2(0.0), vec2(1.0));
                    return coverage.x * coverage.y * maskAlpha;
                }
                return texelFetch(maskTexture, ivec2(gl_FragCoord.xy), 0).a;
            }

            void main() {
                vec2 sourcePixel = clamp(
                    (gl_FragCoord.xy / targetSize) * validSize,
                    vec2(0.5),
                    validSize - vec2(0.5));
                vec4 blurred = texture(blurredTexture, sourcePixel / allocatedSize);
                outColor = blurred * resolveMask();
            }
            """;

    private final long byteLimit;
    private final int[] planWidths = new int[PLAN_CACHE_SIZE];
    private final int[] planHeights = new int[PLAN_CACHE_SIZE];
    private final int[] planDprBits = new int[PLAN_CACHE_SIZE];
    private final int[] planRadiusBits = new int[PLAN_CACHE_SIZE];
    private final Plan[] planCache = new Plan[PLAN_CACHE_SIZE];
    private int nextPlanSlot;
    private int targetWidth;
    private int targetHeight;
    private int allocatedWidth;
    private int allocatedHeight;
    private Target first;
    private Target second;
    private long allocatedBytes;

    private int blurProgram;
    private int downsampleProgram;
    private int compositeProgram;
    private int vertexArray;
    private int blurSourceLocation;
    private int blurAllocatedSizeLocation;
    private int blurValidSizeLocation;
    private int blurDirectionLocation;
    private int blurCenterWeightLocation;
    private int blurPairWeightsLocation;
    private int blurPairOffsetsLocation;
    private int downsampleSourceLocation;
    private int downsampleAllocatedSizeLocation;
    private int downsampleValidSizeLocation;
    private int downsampleDestinationSizeLocation;
    private int compositeBlurredLocation;
    private int compositeMaskLocation;
    private int compositeAnalyticMaskLocation;
    private int compositeMaskRectLocation;
    private int compositeMaskAlphaLocation;
    private int compositeTargetSizeLocation;
    private int compositeAllocatedSizeLocation;
    private int compositeValidSizeLocation;
    private boolean closed;

    NanoVgBackdropBlur() {
        this(DEFAULT_BYTE_LIMIT);
    }

    NanoVgBackdropBlur(long byteLimit) {
        if (byteLimit < 1L) {
            throw new IllegalArgumentException("Backdrop blur byte limit must be positive");
        }
        this.byteLimit = byteLimit;
    }

    Result blur(
            int sourceFramebuffer,
            int width,
            int height,
            float devicePixelRatio,
            float logicalRadius,
            PixelBounds affectedBounds) {
        requireOpen();
        if (affectedBounds.empty()) {
            throw new IllegalArgumentException("Backdrop blur bounds must not be empty");
        }
        Plan plan = planFor(width, height, devicePixelRatio, logicalRadius);
        ensureTargets(width, height);
        ensurePipeline();

        Target filtered = first;
        Target scratch = second;
        int sourceWidth = width;
        int sourceHeight = height;
        int stageDownsample = BASE_DOWNSAMPLE;
        int stageWidth = divideCeil(width, stageDownsample);
        int stageHeight = divideCeil(height, stageDownsample);
        int downsampleDrawCalls = 0;
        PixelBounds stageBounds = prefilterBounds(
                affectedBounds,
                stageDownsample,
                stageWidth,
                stageHeight,
                plan);
        blitDownsample(
                sourceFramebuffer,
                sourceWidth,
                sourceHeight,
                filtered.framebuffer,
                stageWidth,
                stageHeight,
                stageBounds);

        // A single 4x+ linear blit skips high-frequency world texels. Later
        // stages use a tent prefilter to suppress temporal moire on foliage.
        for (int stage = 1; stage < plan.downsampleStages; stage++) {
            int nextDownsample = stageDownsample << 1;
            int nextWidth = divideCeil(width, nextDownsample);
            int nextHeight = divideCeil(height, nextDownsample);
            PixelBounds nextBounds = prefilterBounds(
                    affectedBounds,
                    nextDownsample,
                    nextWidth,
                    nextHeight,
                    plan);
            drawDownsample(
                    filtered.texture,
                    stageWidth,
                    stageHeight,
                    scratch.framebuffer,
                    nextWidth,
                    nextHeight,
                    nextBounds);
            downsampleDrawCalls++;
            Target previous = filtered;
            filtered = scratch;
            scratch = previous;
            stageDownsample = nextDownsample;
            stageWidth = nextWidth;
            stageHeight = nextHeight;
        }

        PixelBounds outputBounds = affectedBounds.downsample(plan.downsample, plan.width, plan.height);
        PixelBounds horizontalBounds = outputBounds.expandVertical(4, plan.height);
        drawBlur(filtered.texture, scratch.framebuffer, plan, true, horizontalBounds);
        drawBlur(scratch.texture, filtered.framebuffer, plan, false, outputBounds);
        return new Result(filtered.texture, plan.width, plan.height, 2 + downsampleDrawCalls);
    }

    int composite(
            int destinationFramebuffer,
            int width,
            int height,
            Result result,
            CompositeMask mask) {
        requireOpen();
        if (mask == null || mask.bounds.empty()) {
            throw new IllegalArgumentException("Backdrop blur composite mask must not be empty");
        }
        if ((result.texture != first.texture && result.texture != second.texture)
                || result.validWidth < 1
                || result.validHeight < 1
                || width != targetWidth
                || height != targetHeight) {
            throw new IllegalArgumentException("Backdrop blur result does not match the active target");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, destinationFramebuffer);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glViewport(0, 0, width, height);
        prepareDrawState();
        glUseProgram(compositeProgram);
        glBindVertexArray(vertexArray);
        glActiveTexture(GL_TEXTURE0);
        glBindSampler(0, 0);
        glBindTexture(GL_TEXTURE_2D, result.texture);
        glActiveTexture(GL_TEXTURE1);
        glBindSampler(1, 0);
        glBindTexture(GL_TEXTURE_2D, mask.analytic ? result.texture : mask.texture);
        glUniform1i(compositeBlurredLocation, 0);
        glUniform1i(compositeMaskLocation, 1);
        glUniform1i(compositeAnalyticMaskLocation, mask.analytic ? 1 : 0);
        glUniform4f(
                compositeMaskRectLocation,
                mask.left,
                mask.bottom,
                mask.right,
                mask.top);
        glUniform1f(compositeMaskAlphaLocation, mask.alpha);
        glUniform2f(compositeTargetSizeLocation, width, height);
        glUniform2f(compositeAllocatedSizeLocation, allocatedWidth, allocatedHeight);
        glUniform2f(compositeValidSizeLocation, result.validWidth, result.validHeight);
        glEnable(GL_SCISSOR_TEST);
        glScissor(
                mask.bounds.x,
                mask.bounds.y,
                mask.bounds.width,
                mask.bounds.height);
        glEnable(GL_BLEND);
        glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        return 1;
    }

    private void drawBlur(
            int sourceTexture,
            int destinationFramebuffer,
            Plan plan,
            boolean horizontal,
            PixelBounds affectedBounds) {
        glBindFramebuffer(GL_FRAMEBUFFER, destinationFramebuffer);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glViewport(0, 0, plan.width, plan.height);
        prepareDrawState();
        glEnable(GL_SCISSOR_TEST);
        glScissor(
                affectedBounds.x,
                affectedBounds.y,
                affectedBounds.width,
                affectedBounds.height);
        glDisable(GL_BLEND);
        glUseProgram(blurProgram);
        glBindVertexArray(vertexArray);
        glActiveTexture(GL_TEXTURE0);
        glBindSampler(0, 0);
        glBindTexture(GL_TEXTURE_2D, sourceTexture);
        glUniform1i(blurSourceLocation, 0);
        glUniform2f(blurAllocatedSizeLocation, allocatedWidth, allocatedHeight);
        glUniform2f(blurValidSizeLocation, plan.width, plan.height);
        glUniform2f(blurDirectionLocation, horizontal ? 1.0f : 0.0f, horizontal ? 0.0f : 1.0f);
        glUniform1f(blurCenterWeightLocation, plan.kernel.centerWeight);
        glUniform2f(
                blurPairWeightsLocation,
                plan.kernel.firstPairWeight,
                plan.kernel.secondPairWeight);
        glUniform2f(
                blurPairOffsetsLocation,
                plan.kernel.firstPairOffset,
                plan.kernel.secondPairOffset);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    private void drawDownsample(
            int sourceTexture,
            int sourceWidth,
            int sourceHeight,
            int destinationFramebuffer,
            int destinationWidth,
            int destinationHeight,
            PixelBounds affectedBounds) {
        glBindFramebuffer(GL_FRAMEBUFFER, destinationFramebuffer);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glViewport(0, 0, destinationWidth, destinationHeight);
        prepareDrawState();
        glEnable(GL_SCISSOR_TEST);
        glScissor(
                affectedBounds.x,
                affectedBounds.y,
                affectedBounds.width,
                affectedBounds.height);
        glDisable(GL_BLEND);
        glUseProgram(downsampleProgram);
        glBindVertexArray(vertexArray);
        glActiveTexture(GL_TEXTURE0);
        glBindSampler(0, 0);
        glBindTexture(GL_TEXTURE_2D, sourceTexture);
        glUniform1i(downsampleSourceLocation, 0);
        glUniform2f(downsampleAllocatedSizeLocation, allocatedWidth, allocatedHeight);
        glUniform2f(downsampleValidSizeLocation, sourceWidth, sourceHeight);
        glUniform2f(downsampleDestinationSizeLocation, destinationWidth, destinationHeight);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    private static void blitDownsample(
            int sourceFramebuffer,
            int sourceWidth,
            int sourceHeight,
            int destinationFramebuffer,
            int destinationWidth,
            int destinationHeight,
            PixelBounds affectedBounds) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFramebuffer);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destinationFramebuffer);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glEnable(GL_SCISSOR_TEST);
        glScissor(
                affectedBounds.x,
                affectedBounds.y,
                affectedBounds.width,
                affectedBounds.height);
        glBlitFramebuffer(
                0,
                0,
                sourceWidth,
                sourceHeight,
                0,
                0,
                destinationWidth,
                destinationHeight,
                GL_COLOR_BUFFER_BIT,
                GL_LINEAR);
    }

    private static void prepareDrawState() {
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_COLOR_LOGIC_OP);
        glDisable(GL_FRAMEBUFFER_SRGB);
        glDisable(GL_DITHER);
        glColorMask(true, true, true, true);
    }

    private void ensureTargets(int width, int height) {
        if (width == targetWidth && height == targetHeight) {
            return;
        }
        int nextWidth = divideCeil(width, BASE_DOWNSAMPLE);
        int nextHeight = divideCeil(height, BASE_DOWNSAMPLE);
        long required = requiredBytes(width, height);
        if (required > byteLimit) {
            throw new OpenGlRenderException(
                    "Backdrop blur targets require " + required
                            + " bytes, exceeding the " + byteLimit + " byte limit");
        }

        if (allocatedBytes > byteLimit - required) {
            deleteTargets();
        }

        Target nextFirst = null;
        Target nextSecond = null;
        boolean complete = false;
        try {
            nextFirst = createTarget(nextWidth, nextHeight);
            nextSecond = createTarget(nextWidth, nextHeight);
            complete = true;
        } finally {
            if (!complete) {
                delete(nextFirst);
                delete(nextSecond);
            }
        }

        deleteTargets();
        first = nextFirst;
        second = nextSecond;
        targetWidth = width;
        targetHeight = height;
        allocatedWidth = nextWidth;
        allocatedHeight = nextHeight;
        allocatedBytes = required;
    }

    private void ensurePipeline() {
        if (blurProgram != 0) {
            return;
        }
        int vertexShader = 0;
        int blurFragment = 0;
        int downsampleFragment = 0;
        int compositeFragment = 0;
        int nextBlurProgram = 0;
        int nextDownsampleProgram = 0;
        int nextCompositeProgram = 0;
        int nextVertexArray = 0;
        boolean complete = false;
        try {
            vertexShader = compile(GL_VERTEX_SHADER, VERTEX_SHADER);
            blurFragment = compile(GL_FRAGMENT_SHADER, BLUR_FRAGMENT_SHADER);
            downsampleFragment = compile(GL_FRAGMENT_SHADER, DOWNSAMPLE_FRAGMENT_SHADER);
            compositeFragment = compile(GL_FRAGMENT_SHADER, COMPOSITE_FRAGMENT_SHADER);
            nextBlurProgram = link(vertexShader, blurFragment, "backdrop blur");
            nextDownsampleProgram = link(vertexShader, downsampleFragment, "backdrop downsample");
            nextCompositeProgram = link(vertexShader, compositeFragment, "backdrop composite");
            nextVertexArray = glGenVertexArrays();
            if (nextVertexArray == 0) {
                throw new OpenGlRenderException("OpenGL failed to allocate the backdrop blur VAO");
            }

            int nextBlurSource = location(nextBlurProgram, "sourceTexture");
            int nextBlurAllocatedSize = location(nextBlurProgram, "allocatedSize");
            int nextBlurValidSize = location(nextBlurProgram, "validSize");
            int nextBlurDirection = location(nextBlurProgram, "direction");
            int nextBlurCenterWeight = location(nextBlurProgram, "centerWeight");
            int nextBlurPairWeights = location(nextBlurProgram, "pairWeights");
            int nextBlurPairOffsets = location(nextBlurProgram, "pairOffsets");
            int nextDownsampleSource = location(nextDownsampleProgram, "sourceTexture");
            int nextDownsampleAllocatedSize = location(nextDownsampleProgram, "allocatedSize");
            int nextDownsampleValidSize = location(nextDownsampleProgram, "validSize");
            int nextDownsampleDestinationSize = location(nextDownsampleProgram, "destinationSize");
            int nextCompositeBlurred = location(nextCompositeProgram, "blurredTexture");
            int nextCompositeMask = location(nextCompositeProgram, "maskTexture");
            int nextCompositeAnalyticMask = location(nextCompositeProgram, "analyticMask");
            int nextCompositeMaskRect = location(nextCompositeProgram, "maskRect");
            int nextCompositeMaskAlpha = location(nextCompositeProgram, "maskAlpha");
            int nextCompositeTargetSize = location(nextCompositeProgram, "targetSize");
            int nextCompositeAllocatedSize = location(nextCompositeProgram, "allocatedSize");
            int nextCompositeValidSize = location(nextCompositeProgram, "validSize");

            blurProgram = nextBlurProgram;
            downsampleProgram = nextDownsampleProgram;
            compositeProgram = nextCompositeProgram;
            vertexArray = nextVertexArray;
            blurSourceLocation = nextBlurSource;
            blurAllocatedSizeLocation = nextBlurAllocatedSize;
            blurValidSizeLocation = nextBlurValidSize;
            blurDirectionLocation = nextBlurDirection;
            blurCenterWeightLocation = nextBlurCenterWeight;
            blurPairWeightsLocation = nextBlurPairWeights;
            blurPairOffsetsLocation = nextBlurPairOffsets;
            downsampleSourceLocation = nextDownsampleSource;
            downsampleAllocatedSizeLocation = nextDownsampleAllocatedSize;
            downsampleValidSizeLocation = nextDownsampleValidSize;
            downsampleDestinationSizeLocation = nextDownsampleDestinationSize;
            compositeBlurredLocation = nextCompositeBlurred;
            compositeMaskLocation = nextCompositeMask;
            compositeAnalyticMaskLocation = nextCompositeAnalyticMask;
            compositeMaskRectLocation = nextCompositeMaskRect;
            compositeMaskAlphaLocation = nextCompositeMaskAlpha;
            compositeTargetSizeLocation = nextCompositeTargetSize;
            compositeAllocatedSizeLocation = nextCompositeAllocatedSize;
            compositeValidSizeLocation = nextCompositeValidSize;
            complete = true;
        } finally {
            if (vertexShader != 0) {
                glDeleteShader(vertexShader);
            }
            if (blurFragment != 0) {
                glDeleteShader(blurFragment);
            }
            if (downsampleFragment != 0) {
                glDeleteShader(downsampleFragment);
            }
            if (compositeFragment != 0) {
                glDeleteShader(compositeFragment);
            }
            if (!complete) {
                if (nextBlurProgram != 0) {
                    glDeleteProgram(nextBlurProgram);
                }
                if (nextDownsampleProgram != 0) {
                    glDeleteProgram(nextDownsampleProgram);
                }
                if (nextCompositeProgram != 0) {
                    glDeleteProgram(nextCompositeProgram);
                }
                if (nextVertexArray != 0) {
                    glDeleteVertexArrays(nextVertexArray);
                }
            }
        }
    }

    static Plan plan(int width, int height, float devicePixelRatio, float logicalRadius) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }
        if (!Float.isFinite(devicePixelRatio) || devicePixelRatio <= 0.0f) {
            throw new IllegalArgumentException("devicePixelRatio must be finite and positive");
        }
        if (!Float.isFinite(logicalRadius) || logicalRadius <= 0.0f) {
            throw new IllegalArgumentException("logicalRadius must be finite and positive");
        }

        double requestedPhysical = (double) logicalRadius * devicePixelRatio;
        double maximumUseful = Math.max(width, height) * 2.0;
        float physicalRadius = (float) Math.max(0.5, Math.min(requestedPhysical, maximumUseful));
        physicalRadius = Math.max(0.5f, Math.round(physicalRadius * 2.0f) * 0.5f);

        int downsample = BASE_DOWNSAMPLE;
        while (physicalRadius / downsample > MAX_DOWNSAMPLED_RADIUS
                && downsample <= (1 << 29)) {
            downsample <<= 1;
        }
        int planWidth = divideCeil(width, downsample);
        int planHeight = divideCeil(height, downsample);
        float sigma = Math.max(MIN_SIGMA, physicalRadius / (downsample * 3.0f));
        int downsampleStages = Integer.numberOfTrailingZeros(downsample);
        return new Plan(
                planWidth,
                planHeight,
                downsample,
                downsampleStages,
                physicalRadius,
                Kernel.gaussian(sigma));
    }

    Plan planFor(int width, int height, float devicePixelRatio, float logicalRadius) {
        int dprBits = Float.floatToIntBits(devicePixelRatio);
        int radiusBits = Float.floatToIntBits(logicalRadius);
        for (int index = 0; index < planCache.length; index++) {
            Plan cached = planCache[index];
            if (cached != null
                    && planWidths[index] == width
                    && planHeights[index] == height
                    && planDprBits[index] == dprBits
                    && planRadiusBits[index] == radiusBits) {
                return cached;
            }
        }

        Plan created = plan(width, height, devicePixelRatio, logicalRadius);
        int slot = nextPlanSlot;
        planWidths[slot] = width;
        planHeights[slot] = height;
        planDprBits[slot] = dprBits;
        planRadiusBits[slot] = radiusBits;
        planCache[slot] = created;
        nextPlanSlot = (slot + 1) % planCache.length;
        return created;
    }

    static long requiredBytes(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }
        try {
            long halfWidth = divideCeil(width, BASE_DOWNSAMPLE);
            long halfHeight = divideCeil(height, BASE_DOWNSAMPLE);
            return Math.multiplyExact(Math.multiplyExact(halfWidth, halfHeight), 8L);
        } catch (ArithmeticException exception) {
            throw new OpenGlRenderException("Backdrop blur target size overflow", exception);
        }
    }

    static PixelBounds prefilterBounds(
            PixelBounds affectedBounds,
            int stageDownsample,
            int stageWidth,
            int stageHeight,
            Plan plan) {
        if (affectedBounds == null || affectedBounds.empty()) {
            throw new IllegalArgumentException("Backdrop blur bounds must not be empty");
        }
        if (stageDownsample < BASE_DOWNSAMPLE
                || stageDownsample > plan.downsample
                || plan.downsample % stageDownsample != 0) {
            throw new IllegalArgumentException("Invalid backdrop blur downsample stage");
        }
        int remainingScale = plan.downsample / stageDownsample;
        int support = Math.subtractExact(Math.multiplyExact(6, remainingScale), 2);
        return affectedBounds
                .downsample(stageDownsample, stageWidth, stageHeight)
                .expand(support, support, stageWidth, stageHeight);
    }

    static CompositeMask axisAlignedMask(
            Rect rect,
            float[] transform,
            float devicePixelRatio,
            int targetWidth,
            int targetHeight,
            float alpha) {
        if (rect == null || transform == null || transform.length != 6) {
            throw new IllegalArgumentException("Backdrop blur mask inputs are invalid");
        }
        if (!Float.isFinite(devicePixelRatio) || devicePixelRatio <= 0.0f) {
            throw new IllegalArgumentException("devicePixelRatio must be finite and positive");
        }
        if (targetWidth < 1 || targetHeight < 1) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }
        if (!Float.isFinite(alpha) || alpha < 0.0f || alpha > 1.0f) {
            throw new IllegalArgumentException("alpha must be between zero and one");
        }
        if (transform[1] != 0.0f
                || transform[2] != 0.0f
                || transform[0] == 0.0f
                || transform[3] == 0.0f) {
            return null;
        }

        double x0 = (transform[0] * rect.x() + transform[4]) * devicePixelRatio;
        double x1 = (transform[0] * (rect.x() + rect.width()) + transform[4]) * devicePixelRatio;
        double logicalY0 = transform[3] * rect.y() + transform[5];
        double logicalY1 = transform[3] * (rect.y() + rect.height()) + transform[5];
        double left = Math.max(0.0, Math.min(targetWidth, Math.min(x0, x1)));
        double right = Math.max(0.0, Math.min(targetWidth, Math.max(x0, x1)));
        double bottom = Math.max(
                0.0,
                Math.min(targetHeight, targetHeight - Math.max(logicalY0, logicalY1) * devicePixelRatio));
        double top = Math.max(
                0.0,
                Math.min(targetHeight, targetHeight - Math.min(logicalY0, logicalY1) * devicePixelRatio));
        PixelBounds bounds = new PixelBounds(
                clampFloor(left, targetWidth),
                clampFloor(bottom, targetHeight),
                Math.max(0, clampCeil(right, targetWidth) - clampFloor(left, targetWidth)),
                Math.max(0, clampCeil(top, targetHeight) - clampFloor(bottom, targetHeight)));
        if (bounds.empty()) {
            return null;
        }
        return CompositeMask.analytic(
                bounds,
                (float) left,
                (float) bottom,
                (float) right,
                (float) top,
                alpha);
    }

    static PixelBounds transformedBounds(
            Rect rect,
            float[] transform,
            float devicePixelRatio,
            int targetWidth,
            int targetHeight) {
        if (transform == null || transform.length != 6) {
            throw new IllegalArgumentException("NanoVG transform must contain six values");
        }
        if (!Float.isFinite(devicePixelRatio) || devicePixelRatio <= 0.0f) {
            throw new IllegalArgumentException("devicePixelRatio must be finite and positive");
        }
        if (targetWidth < 1 || targetHeight < 1) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }

        double left = rect.x();
        double top = rect.y();
        double right = left + rect.width();
        double bottom = top + rect.height();
        double firstX = transform[0] * left + transform[2] * top + transform[4];
        double firstY = transform[1] * left + transform[3] * top + transform[5];
        double secondX = transform[0] * right + transform[2] * top + transform[4];
        double secondY = transform[1] * right + transform[3] * top + transform[5];
        double thirdX = transform[0] * right + transform[2] * bottom + transform[4];
        double thirdY = transform[1] * right + transform[3] * bottom + transform[5];
        double fourthX = transform[0] * left + transform[2] * bottom + transform[4];
        double fourthY = transform[1] * left + transform[3] * bottom + transform[5];
        double minimumX = Math.min(Math.min(firstX, secondX), Math.min(thirdX, fourthX));
        double maximumX = Math.max(Math.max(firstX, secondX), Math.max(thirdX, fourthX));
        double minimumY = Math.min(Math.min(firstY, secondY), Math.min(thirdY, fourthY));
        double maximumY = Math.max(Math.max(firstY, secondY), Math.max(thirdY, fourthY));

        int physicalLeft = clampFloor(minimumX * devicePixelRatio - 1.0, targetWidth);
        int physicalRight = clampCeil(maximumX * devicePixelRatio + 1.0, targetWidth);
        int physicalTop = clampFloor(minimumY * devicePixelRatio - 1.0, targetHeight);
        int physicalBottom = clampCeil(maximumY * devicePixelRatio + 1.0, targetHeight);
        if (physicalLeft == physicalRight || physicalTop == physicalBottom) {
            return new PixelBounds(0, 0, 0, 0);
        }
        return new PixelBounds(
                physicalLeft,
                targetHeight - physicalBottom,
                Math.max(0, physicalRight - physicalLeft),
                Math.max(0, physicalBottom - physicalTop));
    }

    private static Target createTarget(int width, int height) {
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int texture = 0;
        int framebuffer = 0;
        boolean complete = false;
        try {
            texture = glGenTextures();
            framebuffer = glGenFramebuffers();
            if (texture == 0 || framebuffer == 0) {
                throw new OpenGlRenderException("OpenGL failed to allocate a backdrop blur target");
            }
            glBindTexture(GL_TEXTURE_2D, texture);
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
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0,
                    GL_TEXTURE_2D,
                    texture,
                    0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            glReadBuffer(GL_COLOR_ATTACHMENT0);
            int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new OpenGlRenderException(
                        "Backdrop blur framebuffer is incomplete: 0x" + Integer.toHexString(status));
            }
            complete = true;
            return new Target(framebuffer, texture);
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glActiveTexture(activeTexture);
            if (!complete) {
                if (framebuffer != 0) {
                    glDeleteFramebuffers(framebuffer);
                }
                if (texture != 0) {
                    glDeleteTextures(texture);
                }
            }
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        if (shader == 0) {
            throw new OpenGlRenderException("OpenGL failed to allocate a backdrop blur shader");
        }
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new OpenGlRenderException("Failed to compile backdrop blur shader: " + log);
        }
        return shader;
    }

    private static int link(int vertexShader, int fragmentShader, String description) {
        int program = glCreateProgram();
        if (program == 0) {
            throw new OpenGlRenderException("OpenGL failed to allocate the " + description + " program");
        }
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new OpenGlRenderException("Failed to link " + description + " program: " + log);
        }
        return program;
    }

    private static int location(int program, String name) {
        int location = glGetUniformLocation(program, name);
        if (location < 0) {
            throw new OpenGlRenderException("Backdrop blur uniform is unavailable: " + name);
        }
        return location;
    }

    private static int divideCeil(int value, int divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    private static int clampFloor(double value, int maximum) {
        if (value <= 0.0) {
            return 0;
        }
        if (value >= maximum) {
            return maximum;
        }
        return (int) Math.floor(value);
    }

    private static int clampCeil(double value, int maximum) {
        if (value <= 0.0) {
            return 0;
        }
        if (value >= maximum) {
            return maximum;
        }
        return (int) Math.ceil(value);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Backdrop blur renderer is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        deleteTargets();
        if (vertexArray != 0) {
            glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        if (blurProgram != 0) {
            glDeleteProgram(blurProgram);
            blurProgram = 0;
        }
        if (downsampleProgram != 0) {
            glDeleteProgram(downsampleProgram);
            downsampleProgram = 0;
        }
        if (compositeProgram != 0) {
            glDeleteProgram(compositeProgram);
            compositeProgram = 0;
        }
    }

    private void deleteTargets() {
        delete(first);
        delete(second);
        first = null;
        second = null;
        targetWidth = 0;
        targetHeight = 0;
        allocatedWidth = 0;
        allocatedHeight = 0;
        allocatedBytes = 0L;
    }

    private static void delete(Target target) {
        if (target == null) {
            return;
        }
        glDeleteFramebuffers(target.framebuffer);
        glDeleteTextures(target.texture);
    }

    record Plan(
            int width,
            int height,
            int downsample,
            int downsampleStages,
            float physicalRadius,
            Kernel kernel) {
    }

    record Kernel(
            float centerWeight,
            float firstPairWeight,
            float firstPairOffset,
            float secondPairWeight,
            float secondPairOffset) {
        private static Kernel gaussian(float sigma) {
            float exponentScale = -1.0f / (2.0f * sigma * sigma);
            float weight0 = 1.0f;
            float weight1 = (float) Math.exp(exponentScale);
            float weight2 = (float) Math.exp(4.0f * exponentScale);
            float weight3 = (float) Math.exp(9.0f * exponentScale);
            float weight4 = (float) Math.exp(16.0f * exponentScale);
            float inverseSum = 1.0f / (weight0 + (weight1 + weight2 + weight3 + weight4) * 2.0f);
            weight0 *= inverseSum;
            weight1 *= inverseSum;
            weight2 *= inverseSum;
            weight3 *= inverseSum;
            weight4 *= inverseSum;
            float firstWeight = weight1 + weight2;
            float secondWeight = weight3 + weight4;
            return new Kernel(
                    weight0,
                    firstWeight,
                    weightedOffset(weight1, weight2, 1.0f, 2.0f),
                    secondWeight,
                    weightedOffset(weight3, weight4, 3.0f, 4.0f));
        }

        float normalizedWeight() {
            return centerWeight + (firstPairWeight + secondPairWeight) * 2.0f;
        }

        private static float weightedOffset(float first, float second, float firstOffset, float secondOffset) {
            float total = first + second;
            return total == 0.0f
                    ? firstOffset
                    : (first * firstOffset + second * secondOffset) / total;
        }
    }

    record Result(int texture, int validWidth, int validHeight, int drawCalls) {
    }

    record CompositeMask(
            int texture,
            PixelBounds bounds,
            boolean analytic,
            float left,
            float bottom,
            float right,
            float top,
            float alpha) {
        CompositeMask {
            if (bounds == null || bounds.empty()) {
                throw new IllegalArgumentException("Composite mask bounds must not be empty");
            }
            if (!analytic && texture <= 0) {
                throw new IllegalArgumentException("Sampled composite mask texture must be positive");
            }
            if (!Float.isFinite(left)
                    || !Float.isFinite(bottom)
                    || !Float.isFinite(right)
                    || !Float.isFinite(top)
                    || !Float.isFinite(alpha)
                    || alpha < 0.0f
                    || alpha > 1.0f) {
                throw new IllegalArgumentException("Composite mask values are invalid");
            }
        }

        static CompositeMask sampled(int texture, PixelBounds bounds) {
            return new CompositeMask(texture, bounds, false, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        }

        static CompositeMask analytic(
                PixelBounds bounds,
                float left,
                float bottom,
                float right,
                float top,
                float alpha) {
            return new CompositeMask(0, bounds, true, left, bottom, right, top, alpha);
        }
    }

    record PixelBounds(int x, int y, int width, int height) {
        PixelBounds {
            if (x < 0 || y < 0 || width < 0 || height < 0) {
                throw new IllegalArgumentException("Pixel bounds must not be negative");
            }
        }

        boolean empty() {
            return width == 0 || height == 0;
        }

        private PixelBounds downsample(int factor, int targetWidth, int targetHeight) {
            int left = x / factor;
            int bottom = y / factor;
            int right = Math.min(targetWidth, divideCeil(Math.addExact(x, width), factor));
            int top = Math.min(targetHeight, divideCeil(Math.addExact(y, height), factor));
            return new PixelBounds(left, bottom, right - left, top - bottom);
        }

        private PixelBounds expandVertical(int amount, int targetHeight) {
            int bottom = Math.max(0, y - amount);
            int top = Math.min(targetHeight, Math.addExact(y, height) + amount);
            return new PixelBounds(x, bottom, width, top - bottom);
        }

        PixelBounds expand(int horizontal, int vertical, int targetWidth, int targetHeight) {
            if (horizontal < 0 || vertical < 0 || targetWidth < 1 || targetHeight < 1) {
                throw new IllegalArgumentException("Pixel bounds expansion is invalid");
            }
            int left = Math.max(0, x - horizontal);
            int bottom = Math.max(0, y - vertical);
            int right = Math.min(targetWidth, Math.addExact(x, width) + horizontal);
            int top = Math.min(targetHeight, Math.addExact(y, height) + vertical);
            return new PixelBounds(left, bottom, right - left, top - bottom);
        }

        long area() {
            return Math.multiplyExact((long) width, height);
        }
    }

    private record Target(int framebuffer, int texture) {
    }
}
