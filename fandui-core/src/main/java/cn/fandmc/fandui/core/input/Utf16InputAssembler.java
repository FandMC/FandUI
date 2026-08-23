package cn.fandmc.fandui.core.input;

import java.util.ArrayList;
import java.util.List;

/** Converts legacy char callbacks into well-formed UTF-16 text event payloads. */
public final class Utf16InputAssembler {
    private static final String REPLACEMENT = "\ufffd";

    private char pendingHighSurrogate;

    public List<String> accept(char value) {
        List<String> output = new ArrayList<>(2);
        if (Character.isHighSurrogate(value)) {
            if (pendingHighSurrogate != 0) {
                output.add(REPLACEMENT);
            }
            pendingHighSurrogate = value;
        } else if (Character.isLowSurrogate(value)) {
            if (pendingHighSurrogate == 0) {
                output.add(REPLACEMENT);
            } else {
                output.add(new String(new char[]{pendingHighSurrogate, value}));
                pendingHighSurrogate = 0;
            }
        } else {
            if (pendingHighSurrogate != 0) {
                output.add(REPLACEMENT);
                pendingHighSurrogate = 0;
            }
            output.add(Character.toString(value));
        }
        return List.copyOf(output);
    }

    public List<String> flush() {
        if (pendingHighSurrogate == 0) {
            return List.of();
        }
        pendingHighSurrogate = 0;
        return List.of(REPLACEMENT);
    }
}
