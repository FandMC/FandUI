package cn.fandmc.fandui.api.text;

import cn.fandmc.fandui.api.layout.Size;

import java.util.List;

/**
 * Immutable shaped text result safe to retain and read from any thread.
 * It contains no native paragraph handle and is valid only with the {@link TextService}
 * that produced it for hit testing and editor geometry.
 */
public interface TextLayout {
    TextRequest request();

    long resourceGeneration();

    Size size();

    float alphabeticBaseline();

    float ideographicBaseline();

    List<TextLine> lines();

    int unresolvedGlyphs();
}
