package com.example.codingagent.domain;

import java.time.Duration;

public record CommandTimeouts(Duration defaultTimeout, Duration maxTimeout) {

    public Duration resolve(Integer requestedSeconds) {
        if (requestedSeconds == null || requestedSeconds <= 0) {
            return this.defaultTimeout;
        }
        Duration requested = Duration.ofSeconds(requestedSeconds);
        return requested.compareTo(this.maxTimeout) > 0 ? this.maxTimeout : requested;
    }
}
