package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.GradientStop;
import cn.fandmc.fandui.api.style.LinearGradient;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.SolidPaint;
import cn.fandmc.fandui.canvas.DisplayList;
import cn.fandmc.fandui.canvas.RecordingCanvas2D;
import com.sun.management.ThreadMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Repeatable allocation/time probe for renderer-side display-list preparation. */
public final class OpenGlPreparationPerformanceProbe {
    private static final SolidPaint PAINT = new SolidPaint(Color.rgb(0x20AFFF));
    private static final NanoVgRenderResources NO_RESOURCES = new NanoVgRenderResources() {
        @Override
        public Image resolveImage(
                cn.fandmc.fandui.api.resource.ImageRef image,
                cn.fandmc.fandui.api.canvas.ImageSampling sampling) {
            throw new AssertionError("The solid preparation probe must not resolve images");
        }

        @Override
        public Text resolveText(cn.fandmc.fandui.api.text.TextLayout layout) {
            throw new AssertionError("The solid preparation probe must not resolve text");
        }
    };
    private static volatile long sink;

    private OpenGlPreparationPerformanceProbe() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 0) {
            throw new IllegalArgumentException("This probe does not accept arguments");
        }
        ThreadMXBean allocationBean = allocationBean();
        NanoVgExternalImages externalImages = new NanoVgExternalImages(1L);
        NanoVgGradientCache gradients = new NanoVgGradientCache(1L);
        StringBuilder report = new StringBuilder(512);
        report.append("# java=").append(System.getProperty("java.version")).append('\n');
        report.append("# vm=").append(System.getProperty("java.vm.name")).append('\n');
        report.append("# os=").append(System.getProperty("os.name"))
                .append('/').append(System.getProperty("os.arch")).append('\n');
        report.append("# processors=").append(Runtime.getRuntime().availableProcessors()).append('\n');
        report.append("# max_heap_bytes=").append(Runtime.getRuntime().maxMemory()).append('\n');
        report.append("scene,draw_commands,warmup_iterations,measured_iterations,samples,")
                .append("median_nanos_per_prepare,median_bytes_per_prepare\n");

        for (int drawCommands : new int[] {100, 1_000}) {
            int warmupIterations = drawCommands == 100 ? 3_000 : 1_000;
            int measuredIterations = drawCommands == 100 ? 5_000 : 1_500;
            for (Scene scene : scenes(drawCommands)) {
                Result result = measure(
                        allocationBean,
                        scene.displayList,
                        externalImages,
                        gradients,
                        warmupIterations,
                        measuredIterations,
                        5);
                report.append(String.format(
                        Locale.ROOT,
                        "%s,%d,%d,%d,%d,%.3f,%.1f%n",
                        scene.name,
                        drawCommands,
                        warmupIterations,
                        measuredIterations,
                        result.samples,
                        result.medianNanos,
                        result.medianBytes));
            }
        }
        report.append("# sink=").append(sink).append('\n');

        String value = report.toString();
        System.out.print(value);
        String output = System.getProperty("fandui.probe.output", "").strip();
        if (!output.isEmpty()) {
            Path path = Path.of(output).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            Files.writeString(path, value, StandardCharsets.UTF_8);
        }
    }

    private static Result measure(
            ThreadMXBean allocationBean,
            DisplayList displayList,
            NanoVgExternalImages externalImages,
            NanoVgGradientCache gradients,
            int warmupIterations,
            int measuredIterations,
            int samples) {
        for (int iteration = 0; iteration < warmupIterations; iteration++) {
            consume(NanoVgGl3Renderer.PreparedFrame.prepare(
                    displayList, NO_RESOURCES, externalImages, gradients));
        }

        long[] sampleNanos = new long[samples];
        long[] sampleBytes = new long[samples];
        for (int sample = 0; sample < samples; sample++) {
            long allocatedBefore = allocationBean.getCurrentThreadAllocatedBytes();
            long started = System.nanoTime();
            for (int iteration = 0; iteration < measuredIterations; iteration++) {
                consume(NanoVgGl3Renderer.PreparedFrame.prepare(
                        displayList, NO_RESOURCES, externalImages, gradients));
            }
            sampleNanos[sample] = System.nanoTime() - started;
            sampleBytes[sample] = allocationBean.getCurrentThreadAllocatedBytes() - allocatedBefore;
        }
        Arrays.sort(sampleNanos);
        Arrays.sort(sampleBytes);
        return new Result(
                samples,
                (double) sampleNanos[samples / 2] / measuredIterations,
                (double) sampleBytes[samples / 2] / measuredIterations);
    }

    private static List<Scene> scenes(int drawCommands) {
        return List.of(
                new Scene("shared_solid", displayList(drawCommands, index -> PAINT)),
                new Scene("varied_solid", displayList(drawCommands, index -> new SolidPaint(
                        Color.rgb((int) ((index * 0x9E3779B9L) & 0x00FF_FFFFL))))),
                new Scene("shared_native_linear", displayList(drawCommands, index -> NATIVE_LINEAR)));
    }

    private static final LinearGradient NATIVE_LINEAR = new LinearGradient(
            new Point(0.0f, 0.0f),
            new Point(100.0f, 0.0f),
            List.of(
                    GradientStop.at(0.0f, Color.rgb(0x20AFFF)),
                    GradientStop.at(1.0f, Color.rgb(0xEAF8FF))));

    private static DisplayList displayList(int drawCommands, PaintFactory paints) {
        RecordingCanvas2D canvas = RecordingCanvas2D.begin();
        for (int index = 0; index < drawCommands; index++) {
            canvas.fillRect(
                    new Rect(index % 50, index / 50, 1.0f, 1.0f),
                    paints.paint(index));
        }
        return canvas.finish();
    }

    private static ThreadMXBean allocationBean() {
        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        if (!(platformBean instanceof ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) {
            throw new IllegalStateException("Thread allocation accounting is unavailable");
        }
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        return bean;
    }

    private static void consume(NanoVgGl3Renderer.PreparedFrame frame) {
        sink += System.identityHashCode(frame);
    }

    private record Result(int samples, double medianNanos, double medianBytes) {
    }

    private record Scene(String name, DisplayList displayList) {
    }

    @FunctionalInterface
    private interface PaintFactory {
        Paint paint(int index);
    }
}
