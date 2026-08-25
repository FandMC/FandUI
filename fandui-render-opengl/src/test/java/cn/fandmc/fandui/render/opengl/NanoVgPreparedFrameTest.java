package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.canvas.ImageSampling;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.resource.ImageInfo;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceState;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.GradientStop;
import cn.fandmc.fandui.api.style.LinearGradient;
import cn.fandmc.fandui.api.style.RadialGradient;
import cn.fandmc.fandui.api.style.SolidPaint;
import cn.fandmc.fandui.canvas.DisplayList;
import cn.fandmc.fandui.canvas.RecordingCanvas2D;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class NanoVgPreparedFrameTest {
    private static final NanoVgRenderResources NO_RESOURCES = new NanoVgRenderResources() {
        @Override
        public Image resolveImage(ImageRef image, ImageSampling sampling) {
            throw new AssertionError("Direct and transparent commands must not resolve images");
        }

        @Override
        public Text resolveText(cn.fandmc.fandui.api.text.TextLayout layout) {
            throw new AssertionError("The fixture does not contain text");
        }
    };
    private static final ImageRef IMAGE = new ImageRef() {
        @Override
        public UiKey key() {
            return UiKey.of("fandui", "prepared-frame-test");
        }

        @Override
        public ResourceState state() {
            return ResourceState.READY;
        }

        @Override
        public Optional<ImageInfo> info() {
            return Optional.of(new ImageInfo(1, 1));
        }
    };

    @Test
    void directPaintsReuseTheEmptyPreparedFrame() {
        NanoVgExternalImages images = new NanoVgExternalImages(1L);
        NanoVgGradientCache gradients = new NanoVgGradientCache(1L);

        NanoVgGl3Renderer.PreparedFrame first = NanoVgGl3Renderer.PreparedFrame.prepare(
                directPaints(), NO_RESOURCES, images, gradients);
        NanoVgGl3Renderer.PreparedFrame second = NanoVgGl3Renderer.PreparedFrame.prepare(
                directPaints(), NO_RESOURCES, images, gradients);

        assertSame(first, second);
        assertEquals(0, first.preparedPaintCount());
        assertEquals(0, first.textureCommandCount());
    }

    @Test
    void transparentImagesRetainTheirReplaySlotWithoutResolvingResources() {
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        for (int index = 0; index < 9; index++) {
            canvas.drawImage(
                    IMAGE,
                    new Rect(index * 16.0f, 0.0f, 16.0f, 16.0f),
                    ImageSampling.LINEAR,
                    0.0f);
            canvas.fillRect(
                    new Rect(index, 16.0f, 1.0f, 1.0f),
                    new SolidPaint(Color.rgb(0x20AFFF)));
        }

        NanoVgGl3Renderer.PreparedFrame prepared = NanoVgGl3Renderer.PreparedFrame.prepare(
                canvas.finish(),
                NO_RESOURCES,
                new NanoVgExternalImages(1L),
                new NanoVgGradientCache(1L));

        assertEquals(0, prepared.preparedPaintCount());
        assertEquals(9, prepared.textureCommandCount());
    }

    private static DisplayList directPaints() {
        List<GradientStop> stops = List.of(
                GradientStop.at(0.0f, Color.rgb(0x20AFFF)),
                GradientStop.at(1.0f, Color.rgb(0xEAF8FF)));
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        canvas.fillRect(
                new Rect(0.0f, 0.0f, 10.0f, 10.0f),
                new SolidPaint(Color.rgb(0x20AFFF)));
        canvas.fillRect(
                new Rect(10.0f, 0.0f, 10.0f, 10.0f),
                new LinearGradient(new Point(0.0f, 0.0f), new Point(20.0f, 0.0f), stops));
        canvas.fillRect(
                new Rect(20.0f, 0.0f, 10.0f, 10.0f),
                new RadialGradient(new Point(25.0f, 5.0f), 0.0f, 5.0f, stops));
        return canvas.finish();
    }
}
