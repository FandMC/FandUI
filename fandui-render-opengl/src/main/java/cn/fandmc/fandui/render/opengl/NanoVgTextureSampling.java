package cn.fandmc.fandui.render.opengl;

import java.util.Arrays;
import java.util.Objects;

final class NanoVgTextureSampling {
    private int[] frameTextureIds = new int[8];
    private OpenGlSampling[] frameSamplings = new OpenGlSampling[8];
    private int frameSize;
    private int[] configuredTextureIds = new int[8];
    private OpenGlSampling[] configuredSamplings = new OpenGlSampling[8];
    private int configuredSize;

    Decision register(int textureId, OpenGlSampling sampling) {
        if (textureId <= 0) {
            throw new IllegalArgumentException("textureId must be positive");
        }
        Objects.requireNonNull(sampling, "sampling");
        for (int index = 0; index < frameSize; index++) {
            if (frameTextureIds[index] == textureId) {
                return frameSamplings[index] == sampling ? Decision.REUSE : Decision.FLUSH;
            }
        }
        ensureFrameCapacity(frameSize + 1);
        frameTextureIds[frameSize] = textureId;
        frameSamplings[frameSize] = sampling;
        frameSize++;
        return configured(textureId) == sampling ? Decision.REUSE : Decision.CONFIGURE;
    }

    void markConfigured(int textureId, OpenGlSampling sampling) {
        if (textureId <= 0) {
            throw new IllegalArgumentException("textureId must be positive");
        }
        Objects.requireNonNull(sampling, "sampling");
        for (int index = 0; index < configuredSize; index++) {
            if (configuredTextureIds[index] == textureId) {
                configuredSamplings[index] = sampling;
                return;
            }
        }
        ensureConfiguredCapacity(configuredSize + 1);
        configuredTextureIds[configuredSize] = textureId;
        configuredSamplings[configuredSize] = sampling;
        configuredSize++;
    }

    void invalidateConfigured() {
        Arrays.fill(configuredSamplings, 0, configuredSize, null);
        configuredSize = 0;
    }

    void clear() {
        Arrays.fill(frameSamplings, 0, frameSize, null);
        frameSize = 0;
    }

    private OpenGlSampling configured(int textureId) {
        for (int index = 0; index < configuredSize; index++) {
            if (configuredTextureIds[index] == textureId) {
                return configuredSamplings[index];
            }
        }
        return null;
    }

    private void ensureFrameCapacity(int required) {
        if (required <= frameTextureIds.length) {
            return;
        }
        int capacity = Math.max(required, frameTextureIds.length * 2);
        frameTextureIds = Arrays.copyOf(frameTextureIds, capacity);
        frameSamplings = Arrays.copyOf(frameSamplings, capacity);
    }

    private void ensureConfiguredCapacity(int required) {
        if (required <= configuredTextureIds.length) {
            return;
        }
        int capacity = Math.max(required, configuredTextureIds.length * 2);
        configuredTextureIds = Arrays.copyOf(configuredTextureIds, capacity);
        configuredSamplings = Arrays.copyOf(configuredSamplings, capacity);
    }

    enum Decision {
        CONFIGURE,
        REUSE,
        FLUSH
    }
}
