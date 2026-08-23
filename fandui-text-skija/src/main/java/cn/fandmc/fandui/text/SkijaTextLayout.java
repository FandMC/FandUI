package cn.fandmc.fandui.text;

import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextLine;
import cn.fandmc.fandui.api.text.TextRequest;

import java.util.List;
import java.util.Objects;

final class SkijaTextLayout implements TextLayout {
    private final SkijaTextService owner;
    private final TextRequest request;
    private final TextCacheKey cacheKey;
    private final LayoutMetrics metrics;

    SkijaTextLayout(
            SkijaTextService owner,
            TextRequest request,
            TextCacheKey cacheKey,
            LayoutMetrics metrics) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.request = Objects.requireNonNull(request, "request");
        this.cacheKey = Objects.requireNonNull(cacheKey, "cacheKey");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public TextRequest request() {
        return request;
    }

    @Override
    public long resourceGeneration() {
        return cacheKey.resourceGeneration();
    }

    @Override
    public Size size() {
        return metrics.size();
    }

    @Override
    public float alphabeticBaseline() {
        return metrics.alphabeticBaseline();
    }

    @Override
    public float ideographicBaseline() {
        return metrics.ideographicBaseline();
    }

    @Override
    public List<TextLine> lines() {
        return metrics.lines();
    }

    @Override
    public int unresolvedGlyphs() {
        return metrics.unresolvedGlyphs();
    }

    boolean belongsTo(SkijaTextService service) {
        return owner == service;
    }

    TextCacheKey cacheKey() {
        return cacheKey;
    }

    LayoutMetrics metrics() {
        return metrics;
    }
}
