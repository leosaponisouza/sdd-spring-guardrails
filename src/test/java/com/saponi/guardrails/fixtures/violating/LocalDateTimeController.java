package com.saponi.guardrails.fixtures.violating;

import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocalDateTimeController {

    @GetMapping("/events")
    public EventPayload getEvent() {
        return new EventPayload(LocalDateTime.now());
    }

    public record EventPayload(LocalDateTime scheduledAt) {}
}
