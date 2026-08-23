package cn.fandmc.fandui.api.text;

import cn.fandmc.fandui.api.layout.Point;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Thread-safe asynchronous Unicode shaping and editor-geometry service.
 *
 * <p>Returned futures are independent views of internally deduplicated work: cancelling
 * one caller's future does not cancel another caller. Futures fail when input is foreign
 * to this service, the service is closed, native text work fails, or an optional geometry
 * capability is not implemented.</p>
 */
public interface TextService {
    /** Shapes and lays out an immutable text request. */
    CompletableFuture<TextLayout> layout(TextRequest request);

    /** Resolves a logical text position for a point in layout-local coordinates. */
    default CompletableFuture<TextPosition> hitTest(TextLayout layout, Point position) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(position, "position");
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("This text service does not provide hit testing"));
    }

    /** Resolves caret and requested range rectangles without exposing a native paragraph. */
    default CompletableFuture<TextGeometry> geometry(
            TextLayout layout,
            TextPosition caret,
            List<TextRange> ranges) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(caret, "caret");
        List.copyOf(Objects.requireNonNull(ranges, "ranges"));
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("This text service does not provide editor geometry"));
    }
}
