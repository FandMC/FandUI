package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.UiCapabilities;
import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.component.PaintScope;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.UiContainer;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.input.ClipboardService;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Placeable;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceRegistration;
import cn.fandmc.fandui.api.resource.ResourceReloadListener;
import cn.fandmc.fandui.api.resource.ResourceService;
import cn.fandmc.fandui.api.resource.ResourceSource;
import cn.fandmc.fandui.api.screen.UiScreen;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.SolidPaint;
import cn.fandmc.fandui.api.text.FontFamily;
import cn.fandmc.fandui.canvas.DisplayList;
import com.sun.management.ThreadMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Repeatable allocation/time probe for the immutable Core scene pipeline. */
public final class CoreScenePerformanceProbe {
    private static final UiViewport VIEWPORT = new UiViewport(800.0f, 600.0f, 800, 600, 1.0f);
    private static final Paint PAINT_A = new SolidPaint(Color.rgb(0x20AFFF));
    private static final Paint PAINT_B = new SolidPaint(Color.rgb(0x8EDCFF));
    private static volatile long sink;

    private CoreScenePerformanceProbe() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 0) {
            throw new IllegalArgumentException("This probe does not accept arguments");
        }
        ThreadMXBean allocationBean = allocationBean();
        StringBuilder report = new StringBuilder(1_024);
        report.append("# java=").append(System.getProperty("java.version")).append('\n');
        report.append("# vm=").append(System.getProperty("java.vm.name")).append('\n');
        report.append("# os=").append(System.getProperty("os.name"))
                .append('/').append(System.getProperty("os.arch")).append('\n');
        report.append("# processors=").append(Runtime.getRuntime().availableProcessors()).append('\n');
        report.append("# max_heap_bytes=").append(Runtime.getRuntime().maxMemory()).append('\n');
        report.append("components,scenario,warmup_iterations,measured_iterations,samples,commands,")
                .append("display_list_changes,median_nanos_per_frame,median_bytes_per_frame\n");

        for (int components : new int[] {100, 1_000}) {
            int warmupIterations = components == 100 ? 2_000 : 1_000;
            int measuredIterations = components == 100 ? 3_000 : 1_000;
            for (Scenario scenario : Scenario.values()) {
                Result result = runScenario(
                        allocationBean,
                        components,
                        scenario,
                        warmupIterations,
                        measuredIterations,
                        5);
                report.append(String.format(
                        Locale.ROOT,
                        "%d,%s,%d,%d,%d,%d,%d,%.3f,%.1f%n",
                        components,
                        scenario.label,
                        warmupIterations,
                        measuredIterations,
                        result.samples,
                        result.commands,
                        result.displayListChanges,
                        result.medianNanosPerFrame,
                        result.medianBytesPerFrame));
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

    private static Result runScenario(
            ThreadMXBean allocationBean,
            int componentCount,
            Scenario scenario,
            int warmupIterations,
            int measuredIterations,
            int samples) {
        try (Fixture fixture = new Fixture(componentCount)) {
            for (int index = 0; index < warmupIterations; index++) {
                scenario.beforeFrame(fixture.leaves, index);
                consume(fixture.frame());
            }

            long[] sampleNanos = new long[samples];
            long[] sampleBytes = new long[samples];
            long displayListChanges = 0L;
            int commands = fixture.frame().commands().size();
            for (int sample = 0; sample < samples; sample++) {
                DisplayList previous = fixture.frame();
                long allocatedBefore = allocationBean.getCurrentThreadAllocatedBytes();
                long started = System.nanoTime();
                for (int index = 0; index < measuredIterations; index++) {
                    scenario.beforeFrame(fixture.leaves, index);
                    DisplayList current = fixture.frame();
                    if (current != previous) {
                        displayListChanges++;
                    }
                    previous = current;
                    consume(current);
                }
                sampleNanos[sample] = System.nanoTime() - started;
                sampleBytes[sample] = allocationBean.getCurrentThreadAllocatedBytes() - allocatedBefore;
            }
            Arrays.sort(sampleNanos);
            Arrays.sort(sampleBytes);
            return new Result(
                    samples,
                    commands,
                    displayListChanges,
                    (double) sampleNanos[samples / 2] / measuredIterations,
                    (double) sampleBytes[samples / 2] / measuredIterations);
        }
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

    private static void consume(DisplayList displayList) {
        sink += System.identityHashCode(displayList) + displayList.commands().size();
    }

    private enum Scenario {
        STATIC("static") {
            @Override
            void beforeFrame(List<ProbeLeaf> leaves, int index) {
            }
        },
        SINGLE_PAINT("single-paint") {
            @Override
            void beforeFrame(List<ProbeLeaf> leaves, int index) {
                leaves.get(index % leaves.size()).togglePaint();
            }
        },
        ALL_PAINT("all-paint") {
            @Override
            void beforeFrame(List<ProbeLeaf> leaves, int index) {
                for (ProbeLeaf leaf : leaves) {
                    leaf.togglePaint();
                }
            }
        };

        private final String label;

        Scenario(String label) {
            this.label = label;
        }

        abstract void beforeFrame(List<ProbeLeaf> leaves, int index);
    }

    private record Result(
            int samples,
            int commands,
            long displayListChanges,
            double medianNanosPerFrame,
            double medianBytesPerFrame) {
    }

    private static final class Fixture implements AutoCloseable {
        private final List<UiSceneFrame> frames = new ArrayList<>(1);
        private final List<ProbeLeaf> leaves;
        private final CoreUiRuntime runtime;
        private long frameTimeNanos = 1L;

        private Fixture(int componentCount) {
            ProbeContainer root = new ProbeContainer();
            List<ProbeLeaf> mutableLeaves = new ArrayList<>(componentCount);
            for (int index = 0; index < componentCount; index++) {
                ProbeLeaf leaf = new ProbeLeaf();
                mutableLeaves.add(leaf);
                root.add(leaf);
            }
            leaves = List.copyOf(mutableLeaves);
            Thread owner = Thread.currentThread();
            runtime = new CoreUiRuntime(
                    new UiThreadDispatcher() {
                        @Override
                        public boolean isUiThread() {
                            return Thread.currentThread() == owner;
                        }

                        @Override
                        public void execute(Runnable action) {
                            action.run();
                        }
                    },
                    new ScreenHost() {
                        @Override
                        public void open(CoreScreenSession session) {
                        }

                        @Override
                        public void close(CoreScreenSession session) {
                        }
                    },
                    new EmptyResourceService(),
                    new UnavailableTextService("performance probe"),
                    ClipboardService.inMemory(),
                    CursorHost.noOp(),
                    UiCapabilities.of(false, true),
                    () -> frameTimeNanos);
            runtime.markAvailable("performance probe");
            runtime.screens().open(UiScreen.of("Core scene performance probe", root));
            consume(frame());
        }

        private DisplayList frame() {
            runtime.renderFramesInto(VIEWPORT, frameTimeNanos, true, frames);
            frameTimeNanos += 16_666_667L;
            if (frames.size() != 1) {
                throw new IllegalStateException("Expected one probe frame, got " + frames.size());
            }
            return frames.get(0).displayList();
        }

        @Override
        public void close() {
            runtime.stop();
        }
    }

    private static final class ProbeContainer extends UiContainer {
        @Override
        public MeasureResult measure(MeasureScope scope, Constraints constraints) {
            List<Placeable> measured = new ArrayList<>(children().size());
            Constraints childConstraints = Constraints.tight(1.0f, 1.0f);
            for (UiComponent child : children()) {
                measured.add(scope.measure(child, childConstraints));
            }
            Size size = constraints.constrain(new Size(800.0f, 600.0f));
            return scope.layout(size.width(), size.height(), placements -> {
                for (int index = 0; index < measured.size(); index++) {
                    placements.place(measured.get(index), (index % 50) * 2.0f, (index / 50) * 2.0f);
                }
            });
        }
    }

    private static final class ProbeLeaf extends UiComponent {
        private Paint paint = PAINT_A;

        @Override
        public MeasureResult measure(MeasureScope scope, Constraints constraints) {
            Size size = constraints.constrain(new Size(1.0f, 1.0f));
            return scope.layout(size.width(), size.height(), placements -> { });
        }

        @Override
        public void paint(PaintScope scope) {
            scope.canvas().fillRect(scope.bounds(), paint);
        }

        private void togglePaint() {
            paint = paint == PAINT_A ? PAINT_B : PAINT_A;
            invalidatePaint();
        }
    }

    private static final class EmptyResourceService implements ResourceService {
        @Override
        public long generation() {
            return 0L;
        }

        @Override
        public ImageRef image(UiKey key) {
            throw new UnsupportedOperationException("image");
        }

        @Override
        public FontFamily font(UiKey key) {
            throw new UnsupportedOperationException("font");
        }

        @Override
        public ResourceRegistration registerImage(UiKey key, ResourceSource source) {
            throw new UnsupportedOperationException("registerImage");
        }

        @Override
        public ResourceRegistration registerFont(UiKey key, ResourceSource source) {
            throw new UnsupportedOperationException("registerFont");
        }

        @Override
        public long reload() {
            return 0L;
        }

        @Override
        public EventRegistration onReload(ResourceReloadListener listener) {
            throw new UnsupportedOperationException("onReload");
        }
    }
}
