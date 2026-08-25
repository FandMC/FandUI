package cn.fandmc.fandui.fabric.v262;

import cn.fandmc.fandui.api.UiCapabilities;
import cn.fandmc.fandui.api.UiColorFormat;
import cn.fandmc.fandui.api.UiDiagnostics;
import cn.fandmc.fandui.api.UiRendererBackend;
import cn.fandmc.fandui.api.UiRuntimeState;
import cn.fandmc.fandui.api.session.UiViewport;
import cn.fandmc.fandui.core.resource.CoreResourceService;
import cn.fandmc.fandui.core.runtime.CoreUiRuntime;
import cn.fandmc.fandui.core.runtime.MonotonicClock;
import cn.fandmc.fandui.core.runtime.UiSceneFrame;
import cn.fandmc.fandui.core.runtime.UiThreadDispatcher;
import cn.fandmc.fandui.internal.FandUiRuntimeBinder;
import cn.fandmc.fandui.internal.demo.BlurDemoHud;
import cn.fandmc.fandui.internal.demo.FandUiDemoScreen;
import cn.fandmc.fandui.internal.demo.FandUiTestScreen;
import cn.fandmc.fandui.render.opengl.OpenGlBatchProbe;
import cn.fandmc.fandui.render.opengl.OpenGlRenderReport;
import cn.fandmc.fandui.render.opengl.OpenGlProbe;
import cn.fandmc.fandui.render.opengl.OpenGlProbeReport;
import cn.fandmc.fandui.render.opengl.OpenGlTarget;
import cn.fandmc.fandui.render.opengl.OpenGlUiPipeline;
import cn.fandmc.fandui.render.opengl.GlfwCursorHost;
import cn.fandmc.fandui.text.SkijaTextService;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class FandUiClient262 implements ClientModInitializer {
    public static final String MOD_ID = "fandui";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final MonotonicClock CLOCK = new MonotonicClock();
    private static final Blaze3dOpenGlHost262 HOST = new Blaze3dOpenGlHost262();
    private static final OpenGlProbe PROBE = new OpenGlProbe();
    private static final OpenGlBatchProbe BATCH_PROBE = new OpenGlBatchProbe();
    private static final ArrayList<UiSceneFrame> FRAME_BUFFER = new ArrayList<>(2);
    private static CoreResourceService resources;
    private static SkijaTextService textService;
    private static CoreUiRuntime runtime;
    private static OpenGlUiPipeline pipeline;
    private static UiViewport cachedViewport;
    private static FandUiDemoScreen demoScreen;
    private static FandUiTestScreen testScreen;
    private static boolean demoKeyWasDown;
    private static boolean testKeyWasDown;
    private static boolean hookObserved;
    private static boolean backendReported;
    private static boolean probeFailed;
    private static boolean hudRenderedThisFrame;
    private static boolean stopping;

    @Override
    public void onInitializeClient() {
        MinecraftDispatcher dispatcher = new MinecraftDispatcher();
        resources = new CoreResourceService(dispatcher);
        textService = new SkijaTextService(resources::fontSnapshot);
        resources.setFontValidator(textService::validateFonts);
        runtime = new CoreUiRuntime(
                dispatcher,
                new MinecraftScreenHost262(CLOCK),
                resources,
                textService,
                cn.fandmc.fandui.api.input.ClipboardService.of(
                        () -> Minecraft.getInstance().keyboardHandler.getClipboard(),
                        value -> Minecraft.getInstance().keyboardHandler.setClipboard(value)),
                new GlfwCursorHost(() -> Minecraft.getInstance().getWindow().handle()),
                UiCapabilities.of(true, true),
                CLOCK);
        FandUiRuntimeBinder.bind(runtime);

        registerHudElement();
        registerResourceReload();
        initializeRenderer();
        mountBlurDemo();
        initializeScreenDemo();
        initializeTestScreen();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> closeRuntime());

        LOGGER.info("FandUI 26.2 initialized in state {}; backend detection is deferred; target probe is {}, batch probe is {}",
                runtime.availability().state(),
                PROBE.enabled() ? "enabled" : "disabled",
                BATCH_PROBE.enabled() ? "enabled" : "disabled");
    }

    public static void renderAfterGui(RenderTarget target) {
        HOST.target(target);
        reportHookOnce();
        boolean hudVisible = hudRenderedThisFrame;
        hudRenderedThisFrame = false;
        if (stopping) {
            return;
        }

        if (!HOST.isOpenGlBackend()) {
            CoreUiRuntime currentRuntime = runtime;
            String backendName = HOST.backendName();
            if (currentRuntime != null) {
                String detail = "Minecraft selected backend " + backendName;
                currentRuntime.updateDiagnostics(UiDiagnostics.detached(
                        rendererBackend(backendName),
                        backendName,
                        0,
                        detail));
                if (currentRuntime.availability().state() == UiRuntimeState.AVAILABLE) {
                    currentRuntime.markRendererUnavailable(detail);
                }
            }
            reportUnsupportedBackend();
            return;
        }

        renderProbes();
        CoreUiRuntime currentRuntime = runtime;
        OpenGlUiPipeline currentPipeline = pipeline;
        if (currentRuntime == null
                || currentPipeline == null
                || currentRuntime.availability().state() != UiRuntimeState.AVAILABLE) {
            return;
        }

        try {
            UiViewport viewport = currentViewport();
            if (viewport == null) {
                return;
            }
            currentRuntime.renderFramesInto(
                    viewport,
                    CLOCK.getAsLong(),
                    hudVisible,
                    FRAME_BUFFER);
            currentPipeline.render(viewport, FRAME_BUFFER).ifPresent(FandUiClient262::reportPipelineTarget);
        } catch (RuntimeException exception) {
            try {
                currentRuntime.markFailed(failureDetail(exception));
            } catch (RuntimeException stateFailure) {
                exception.addSuppressed(stateFailure);
            }
            LOGGER.error("FandUI UI renderer failed and has been disabled for this run", exception);
        }
    }

    private static void initializeRenderer() {
        try {
            pipeline = new OpenGlUiPipeline(HOST, resources, textService);
            runtime.markAvailable("Minecraft OpenGL + LWJGL NanoVGGL3");
        } catch (RuntimeException exception) {
            runtime.markRendererUnavailable(failureDetail(exception));
            LOGGER.error("FandUI failed to initialize the OpenGL UI renderer", exception);
        }
    }

    private static void mountBlurDemo() {
        if (BlurDemoHud.mountIfEnabled(runtime)) {
            LOGGER.info("FandUI mounted the 50x50 backdrop-blur development HUD");
        }
    }

    private static void initializeScreenDemo() {
        FandUiDemoScreen.installIfEnabled(runtime).ifPresent(installed -> {
            demoScreen = installed;
            ClientTickEvents.END_CLIENT_TICK.register(FandUiClient262::pollScreenDemo);
            LOGGER.info("FandUI full Screen demo enabled; press F8 to reopen it");
        });
    }

    private static void pollScreenDemo(Minecraft client) {
        FandUiDemoScreen current = demoScreen;
        if (stopping || current == null) {
            return;
        }
        try {
            boolean keyDown = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_F8);
            boolean requested = keyDown && !demoKeyWasDown;
            demoKeyWasDown = keyDown;
            if (current.openIfRequested(requested)) {
                LOGGER.info("FandUI opened the full Screen development demo");
            }
        } catch (RuntimeException exception) {
            demoScreen = null;
            demoKeyWasDown = false;
            LOGGER.error("FandUI Screen development demo failed and has been disabled", exception);
        }
    }

    private static void initializeTestScreen() {
        FandUiTestScreen.installIfEnabled(runtime).ifPresent(installed -> {
            testScreen = installed;
            ClientTickEvents.END_CLIENT_TICK.register(FandUiClient262::pollTestScreen);
            LOGGER.info("FandUI comprehensive test Screen enabled; press F9 to reopen it");
        });
    }

    private static void pollTestScreen(Minecraft client) {
        FandUiTestScreen current = testScreen;
        if (stopping || current == null) {
            return;
        }
        try {
            boolean keyDown = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_F9);
            boolean requested = keyDown && !testKeyWasDown;
            testKeyWasDown = keyDown;
            if (current.openIfRequested(requested)) {
                LOGGER.info("FandUI opened the comprehensive test Screen");
            }
        } catch (RuntimeException exception) {
            testScreen = null;
            testKeyWasDown = false;
            LOGGER.error("FandUI comprehensive test Screen failed and has been disabled", exception);
        }
    }

    private static void registerHudElement() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.SUBTITLES,
                Identifier.fromNamespaceAndPath(MOD_ID, "hud_capture"),
                (graphics, deltaTracker) -> hudRenderedThisFrame = true);
    }

    private static void registerResourceReload() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath(MOD_ID, "resources"),
                new PreparableReloadListener() {
                    @Override
                    public CompletableFuture<Void> reload(
                            SharedState sharedState,
                            Executor preparationExecutor,
                            PreparationBarrier preparationBarrier,
                            Executor applicationExecutor
                    ) {
                        return preparationBarrier.<Void>wait(null)
                                .thenRunAsync(
                                        () -> applyResourceReload(sharedState.resourceManager()),
                                        applicationExecutor);
                    }
                });
    }

    private static void applyResourceReload(ResourceManager resourceManager) {
        if (stopping || resources == null) {
            return;
        }
        try {
            long generation = resources.reload((kind, key) -> resourceManager
                    .getResource(Identifier.fromNamespaceAndPath(key.namespace(), key.value()))
                    .map(resource -> () -> {
                        try (var input = resource.open()) {
                            return input.readAllBytes();
                        }
                    }));
            LOGGER.info("FandUI resources advanced to generation {}", generation);
        } catch (RuntimeException exception) {
            LOGGER.error("FandUI resource reload failed", exception);
        }
    }

    private static UiViewport currentViewport() {
        OpenGlTarget target = HOST.currentTarget().orElse(null);
        if (target == null) {
            detachDiagnostics("Minecraft OpenGL color target is not ready");
            cachedViewport = null;
            return null;
        }
        Window window = Minecraft.getInstance().getWindow();
        float scale = window.getGuiScale();
        if (!Float.isFinite(scale) || scale <= 0.0f) {
            scale = 1.0f;
        }
        float logicalWidth = window.getGuiScaledWidth();
        float logicalHeight = window.getGuiScaledHeight();
        UiViewport current = cachedViewport;
        if (current == null
                || Float.compare(current.logicalWidth(), logicalWidth) != 0
                || Float.compare(current.logicalHeight(), logicalHeight) != 0
                || current.framebufferWidth() != target.width()
                || current.framebufferHeight() != target.height()
                || Float.compare(current.devicePixelRatio(), scale) != 0) {
            if (current != null) {
                detachDiagnostics("Minecraft OpenGL color target changed; waiting for validation");
            }
            current = new UiViewport(logicalWidth, logicalHeight, target.width(), target.height(), scale);
            cachedViewport = current;
        }
        return current;
    }

    private static void renderProbes() {
        if (probeFailed || (!PROBE.enabled() && !BATCH_PROBE.enabled())) {
            return;
        }
        try {
            OpenGlProbeReport report = PROBE.render(HOST);
            if (report.status() == OpenGlProbeReport.Status.TARGET_REBUILT) {
                LOGGER.info(
                        "FandUI OpenGL probe attached {}x{} target, FBO {}, format 0x{}",
                        report.width(),
                        report.height(),
                        report.framebuffer(),
                        Integer.toHexString(report.internalFormat()));
            }
            OpenGlRenderReport batchReport = BATCH_PROBE.render(HOST);
            if (batchReport.status() == OpenGlRenderReport.Status.TARGET_REBUILT) {
                LOGGER.info(
                        "FandUI OpenGL batch probe rendered {} batches in {} draw calls to {}x{} FBO {}",
                        batchReport.batches(),
                        batchReport.drawCalls(),
                        batchReport.width(),
                        batchReport.height(),
                        batchReport.framebuffer());
            }
        } catch (RuntimeException exception) {
            probeFailed = true;
            LOGGER.error("FandUI OpenGL diagnostic probes failed and have been disabled for this run", exception);
        }
    }

    private static void reportPipelineTarget(OpenGlRenderReport report) {
        CoreUiRuntime currentRuntime = runtime;
        OpenGlUiPipeline currentPipeline = pipeline;
        if (currentRuntime != null && currentPipeline != null) {
            currentRuntime.updateDiagnostics(UiDiagnostics.ready(
                    UiRendererBackend.OPENGL,
                    report.hostName(),
                    report.width(),
                    report.height(),
                    UiColorFormat.RGBA8_UNORM,
                    currentPipeline.maxTextureSize(),
                    true,
                    true,
                    "Minecraft OpenGL color target validated by the FandUI NanoVG pass"));
        }
        if (report.status() == OpenGlRenderReport.Status.TARGET_REBUILT) {
            LOGGER.info(
                    "FandUI UI renderer attached {}x{} target and rendered {} batches in {} draw calls to FBO {}",
                    report.width(),
                    report.height(),
                    report.batches(),
                    report.drawCalls(),
                    report.framebuffer());
        }
    }

    private static void detachDiagnostics(String detail) {
        CoreUiRuntime currentRuntime = runtime;
        if (currentRuntime == null || !currentRuntime.diagnostics().targetReady()) {
            return;
        }
        currentRuntime.updateDiagnostics(currentRuntime.diagnostics().withoutTarget(detail));
    }

    private static UiRendererBackend rendererBackend(String backendName) {
        if ("Vulkan".equalsIgnoreCase(backendName)) {
            return UiRendererBackend.VULKAN;
        }
        if ("OpenGL".equalsIgnoreCase(backendName)) {
            return UiRendererBackend.OPENGL;
        }
        return UiRendererBackend.UNKNOWN;
    }

    private static void reportUnsupportedBackend() {
        if (!backendReported) {
            backendReported = true;
            LOGGER.warn("FandUI renderer is inactive because Minecraft selected backend {}", HOST.backendName());
        }
    }

    private static void reportHookOnce() {
        if (!hookObserved) {
            hookObserved = true;
            LOGGER.info("FandUI final GUI render hook observed for Minecraft 26.2");
        }
    }

    private static void closeRuntime() {
        stopping = true;
        cachedViewport = null;
        FRAME_BUFFER.clear();
        CoreUiRuntime currentRuntime = runtime;
        if (currentRuntime != null) {
            try {
                currentRuntime.stop();
            } catch (RuntimeException exception) {
                LOGGER.error("FandUI failed to stop the UI runtime", exception);
            }
        }

        OpenGlUiPipeline currentPipeline = pipeline;
        pipeline = null;
        if (currentPipeline != null) {
            try {
                currentPipeline.close();
            } catch (RuntimeException exception) {
                LOGGER.error("FandUI failed to release UI renderer resources", exception);
            }
        }

        SkijaTextService currentTextService = textService;
        textService = null;
        if (currentTextService != null) {
            try {
                currentTextService.close();
                LOGGER.info("FandUI Skija text service released");
            } catch (RuntimeException exception) {
                LOGGER.error("FandUI failed to release Skija text resources", exception);
            }
        }

        CoreResourceService currentResources = resources;
        resources = null;
        if (currentResources != null) {
            try {
                currentResources.close();
                LOGGER.info("FandUI resource service released");
            } catch (RuntimeException exception) {
                LOGGER.error("FandUI failed to release resource reload worker", exception);
            }
        }

        try {
            HOST.assertRenderThread();
            BATCH_PROBE.close();
            PROBE.close();
            LOGGER.info("FandUI runtime and OpenGL resources released");
        } catch (RuntimeException exception) {
            LOGGER.error("FandUI failed to release OpenGL probe resources", exception);
        }
    }

    private static String failureDetail(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static final class MinecraftDispatcher implements UiThreadDispatcher {
        @Override
        public boolean isUiThread() {
            return RenderSystem.isOnRenderThread();
        }

        @Override
        public void execute(Runnable action) {
            Minecraft.getInstance().execute(action);
        }
    }
}
