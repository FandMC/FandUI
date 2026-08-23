package cn.fandmc.fandui.core.scene;

import cn.fandmc.fandui.api.component.Box;
import cn.fandmc.fandui.api.component.CanvasComponent;
import cn.fandmc.fandui.api.component.Image;
import cn.fandmc.fandui.api.component.ImageFit;
import cn.fandmc.fandui.api.component.PaintScope;
import cn.fandmc.fandui.api.component.Spacer;
import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.resource.ImageInfo;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceState;
import cn.fandmc.fandui.api.style.ClipMode;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.SolidPaint;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.canvas.DisplayCommand;
import cn.fandmc.fandui.canvas.DisplayList;
import cn.fandmc.fandui.canvas.DisplayListException;
import cn.fandmc.fandui.core.layout.LayoutEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneCompilerTest {
    @Test
    void compilesPaintCallbacksWithCumulativeOpacityAndScopedLifetime() {
        AtomicReference<PaintScope> capturedScope = new AtomicReference<>();
        CanvasComponent child = CanvasComponent.builder(
                        (constraints, style, theme) -> new Size(10.0f, 8.0f),
                        scope -> {
                            capturedScope.set(scope);
                            scope.canvas().fillRect(
                                    scope.bounds(),
                                    new SolidPaint(new Color(1.0f, 0.0f, 0.0f, 0.5f)));
                        })
                .style(StyleResolver.fixed(Style.builder().opacity(0.5f).build()))
                .build();
        Box root = Box.builder(child)
                .style(StyleResolver.fixed(Style.builder()
                        .opacity(0.5f)
                        .clip(ClipMode.ROUNDED_BOUNDS)
                        .build()))
                .build();
        var layout = new LayoutEngine().layout(
                root,
                new Constraints(0.0f, 100.0f, 0.0f, 100.0f),
                LayoutDirection.LEFT_TO_RIGHT);

        DisplayList displayList = new SceneCompiler().compile(layout, 42L);
        List<Float> alphas = displayList.commands().stream()
                .filter(DisplayCommand.SetGlobalAlpha.class::isInstance)
                .map(DisplayCommand.SetGlobalAlpha.class::cast)
                .map(DisplayCommand.SetGlobalAlpha::alpha)
                .toList();

        assertEquals(List.of(0.5f, 0.25f), alphas);
        assertEquals(1, displayList.maximumClipDepth());
        assertThrows(IllegalStateException.class, () -> capturedScope.get().bounds());
        assertThrows(IllegalStateException.class, () -> capturedScope.get().canvas());
    }

    @Test
    void rejectsCanvasStateLeaksFromAComponentCallback() {
        CanvasComponent component = CanvasComponent.builder(
                (constraints, style, theme) -> new Size(10.0f, 10.0f),
                scope -> scope.canvas().save())
                .build();
        var layout = new LayoutEngine().layout(
                component,
                Constraints.tight(new Size(10.0f, 10.0f)),
                LayoutDirection.LEFT_TO_RIGHT);

        assertThrows(DisplayListException.class, () -> new SceneCompiler().compile(layout, 1L));
    }

    @Test
    void wrapsComponentPaintFailuresWithComponentIdentity() {
        CanvasComponent component = CanvasComponent.builder(
                (constraints, style, theme) -> new Size(1.0f, 1.0f),
                scope -> {
                    throw new IllegalArgumentException("paint failed");
                })
                .build();
        var layout = new LayoutEngine().layout(
                component,
                Constraints.tight(new Size(1.0f, 1.0f)),
                LayoutDirection.LEFT_TO_RIGHT);

        assertThrows(SceneCompileException.class, () -> new SceneCompiler().compile(layout, 1L));
    }

    @Test
    void compilesAStandardImageComponentIntoAnAtlasDraw() {
        ImageRef imageRef = new ImageRef() {
            @Override
            public UiKey key() {
                return UiKey.of("test", "atlas");
            }

            @Override
            public ResourceState state() {
                return ResourceState.READY;
            }

            @Override
            public Optional<ImageInfo> info() {
                return Optional.of(new ImageInfo(256, 128));
            }
        };
        Image image = Image.builder(imageRef)
                .source(new Rect(64.0f, 32.0f, 128.0f, 64.0f))
                .size(100.0f, 100.0f)
                .fit(ImageFit.CONTAIN)
                .build();
        var layout = new LayoutEngine().layout(
                image,
                Constraints.tight(100.0f, 100.0f),
                LayoutDirection.LEFT_TO_RIGHT);

        DisplayCommand.DrawImageRegion draw = new SceneCompiler().compile(layout, 1L).commands().stream()
                .filter(DisplayCommand.DrawImageRegion.class::isInstance)
                .map(DisplayCommand.DrawImageRegion.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals(new Rect(64.0f, 32.0f, 128.0f, 64.0f), draw.source());
        assertEquals(new Rect(0.0f, 25.0f, 100.0f, 50.0f), draw.destination());
    }

    @Test
    void recordsStyleBackdropBlurBeforeTheComponentBackground() {
        Box box = Box.builder(Spacer.of(80.0f, 40.0f))
                .style(Style.builder()
                        .background(Color.rgb(0x20afff).withAlpha(0.42f))
                        .cornerRadius(12.0f)
                        .backdropBlur(18.0f)
                        .build())
                .build();
        var layout = new LayoutEngine().layout(
                box,
                Constraints.tight(80.0f, 40.0f),
                LayoutDirection.LEFT_TO_RIGHT);

        DisplayList displayList = new SceneCompiler().compile(layout, 1L);
        int blurIndex = indexOf(displayList, DisplayCommand.BackdropBlur.class);
        int backgroundIndex = indexOf(displayList, DisplayCommand.FillRoundedRect.class);
        DisplayCommand.BackdropBlur blur = (DisplayCommand.BackdropBlur) displayList.commands().get(blurIndex);

        assertEquals(true, displayList.hasBackdropBlur());
        assertEquals(new Rect(0.0f, 0.0f, 80.0f, 40.0f), blur.rect());
        assertEquals(18.0f, blur.radius());
        assertEquals(true, blurIndex < backgroundIndex);
    }

    private static int indexOf(DisplayList displayList, Class<? extends DisplayCommand> type) {
        for (int index = 0; index < displayList.commands().size(); index++) {
            if (type.isInstance(displayList.commands().get(index))) {
                return index;
            }
        }
        throw new AssertionError("Missing display command " + type.getName());
    }
}
