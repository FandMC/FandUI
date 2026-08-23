package cn.fandmc.fandui.render.opengl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NanoVgTextureSamplingTest {
    @Test
    void batchesDistinctTexturesAndRepeatedSamplingWithoutAFlush() {
        NanoVgTextureSampling sampling = new NanoVgTextureSampling();

        assertEquals(NanoVgTextureSampling.Decision.CONFIGURE,
                sampling.register(11, OpenGlSampling.LINEAR));
        assertEquals(NanoVgTextureSampling.Decision.REUSE,
                sampling.register(11, OpenGlSampling.LINEAR));
        assertEquals(NanoVgTextureSampling.Decision.CONFIGURE,
                sampling.register(12, OpenGlSampling.NEAREST));
    }

    @Test
    void flushesOnlyWhenOneTextureChangesSamplingWithinTheQueuedFrame() {
        NanoVgTextureSampling sampling = new NanoVgTextureSampling();
        sampling.register(11, OpenGlSampling.LINEAR);

        assertEquals(NanoVgTextureSampling.Decision.FLUSH,
                sampling.register(11, OpenGlSampling.NEAREST));

        sampling.clear();
        assertEquals(NanoVgTextureSampling.Decision.CONFIGURE,
                sampling.register(11, OpenGlSampling.NEAREST));
    }

    @Test
    void reusesConfiguredSamplingAcrossFramesUntilItIsInvalidated() {
        NanoVgTextureSampling sampling = new NanoVgTextureSampling();

        assertEquals(NanoVgTextureSampling.Decision.CONFIGURE,
                sampling.register(11, OpenGlSampling.LINEAR));
        sampling.markConfigured(11, OpenGlSampling.LINEAR);
        sampling.clear();

        assertEquals(NanoVgTextureSampling.Decision.REUSE,
                sampling.register(11, OpenGlSampling.LINEAR));

        sampling.invalidateConfigured();
        sampling.clear();
        assertEquals(NanoVgTextureSampling.Decision.CONFIGURE,
                sampling.register(11, OpenGlSampling.LINEAR));
    }

    @Test
    void changedSamplingFlushesAndConfiguresTheNewNanoVgFrame() {
        NanoVgTextureSampling sampling = new NanoVgTextureSampling();
        assertEquals(NanoVgTextureSampling.Decision.CONFIGURE,
                sampling.register(11, OpenGlSampling.LINEAR));
        sampling.markConfigured(11, OpenGlSampling.LINEAR);

        assertEquals(NanoVgTextureSampling.Decision.FLUSH,
                sampling.register(11, OpenGlSampling.NEAREST));
        sampling.clear();
        assertEquals(NanoVgTextureSampling.Decision.CONFIGURE,
                sampling.register(11, OpenGlSampling.NEAREST));
        sampling.markConfigured(11, OpenGlSampling.NEAREST);
        sampling.clear();
        assertEquals(NanoVgTextureSampling.Decision.REUSE,
                sampling.register(11, OpenGlSampling.NEAREST));
    }
}
