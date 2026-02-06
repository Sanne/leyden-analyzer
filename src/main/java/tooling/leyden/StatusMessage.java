package tooling.leyden;

import org.jline.utils.AttributedString;

import java.util.Objects;

public record StatusMessage(Long timestamp, AttributedString message) {
    public StatusMessage {
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(message);
    }
}