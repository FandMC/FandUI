package cn.fandmc.fandui.api;

import java.util.Objects;

/** Thrown when an operation requires an available renderer-backed runtime. */
public final class UiUnavailableException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    private final UiAvailability availability;

    public UiUnavailableException(UiAvailability availability) {
        super(message(availability));
        this.availability = availability;
    }

    public UiAvailability availability() {
        return availability;
    }

    private static String message(UiAvailability availability) {
        Objects.requireNonNull(availability, "availability");
        return "FandUI is unavailable: " + availability.state() + " (" + availability.detail() + ')';
    }
}
