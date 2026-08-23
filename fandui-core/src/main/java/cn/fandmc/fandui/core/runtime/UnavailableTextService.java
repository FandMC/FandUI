package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Explicit placeholder used until the Skija text worker has initialized. */
public final class UnavailableTextService implements TextService {
    private final String detail;

    public UnavailableTextService(String detail) {
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    @Override
    public CompletableFuture<TextLayout> layout(TextRequest request) {
        Objects.requireNonNull(request, "request");
        return CompletableFuture.failedFuture(new IllegalStateException(detail));
    }
}
