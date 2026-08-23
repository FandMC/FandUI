package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.resource.ImageInfo;
import cn.fandmc.fandui.api.resource.ImageRef;
import cn.fandmc.fandui.api.resource.ResourceState;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.style.VisualState;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ImageComponentTest {
    @Test
    void computesAllStandardFitModesWithoutChangingAspectRatio() {
        Rect bounds = new Rect(0.0f, 0.0f, 100.0f, 100.0f);

        assertEquals(new Rect(0.0f, 25.0f, 100.0f, 50.0f),
                Image.destination(200.0f, 100.0f, bounds, ImageFit.CONTAIN, Alignment.CENTER));
        assertEquals(new Rect(-50.0f, 0.0f, 200.0f, 100.0f),
                Image.destination(200.0f, 100.0f, bounds, ImageFit.COVER, Alignment.CENTER));
        assertEquals(new Rect(-100.0f, 0.0f, 200.0f, 100.0f),
                Image.destination(200.0f, 100.0f, bounds, ImageFit.NONE, Alignment.BOTTOM_RIGHT));
        assertEquals(bounds,
                Image.destination(200.0f, 100.0f, bounds, ImageFit.FILL, Alignment.TOP_LEFT));
        assertEquals(new Rect(100.0f, 150.0f, 200.0f, 100.0f), Image.destination(
                200.0f,
                100.0f,
                new Rect(0.0f, 0.0f, 400.0f, 400.0f),
                ImageFit.SCALE_DOWN,
                Alignment.CENTER));
    }

    @Test
    void supportsShortFactoriesAndPassingAStyleDirectly() {
        Style style = Style.builder()
                .padding(8.0f, 4.0f)
                .background(Color.rgb(0x202124))
                .border(1.0f, Color.rgb(0xffffff))
                .cornerRadius(4.0f)
                .build();
        Box box = Box.builder(Spacer.of(12.0f, 6.0f)).style(style).build();
        Column column = Column.of(
                Text.of("FandUI", 16.0f),
                Button.text("OK", 14.0f, () -> { }),
                Image.of(new StubImageRef()));

        assertSame(style, box.style());
        assertSame(style, style.resolve(Theme.defaults(), VisualState.defaults()));
        assertEquals(3, column.children().size());
        assertEquals(12.0f, ((Spacer) box.child()).preferredSize().width());
    }

    private static final class StubImageRef implements ImageRef {
        @Override
        public UiKey key() {
            return UiKey.of("test", "image");
        }

        @Override
        public ResourceState state() {
            return ResourceState.READY;
        }

        @Override
        public Optional<ImageInfo> info() {
            return Optional.of(new ImageInfo(200, 100));
        }
    }
}
