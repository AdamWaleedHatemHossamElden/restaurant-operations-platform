package com.adam.restaurantoperations.reports;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportRangeTest {
    private static final Instant START = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void acceptsHalfOpenRangeAndBoundedTop() {
        assertThat(ReportRange.validated(START, START.plusSeconds(60)).from()).isEqualTo(START);
        assertThat(ReportRange.validatedTop(null)).isEqualTo(10);
        assertThat(ReportRange.validatedTop(100)).isEqualTo(100);
    }

    @Test
    void rejectsMissingReversedEqualAndOversizedRanges() {
        assertBadRange(null, START);
        assertBadRange(START, null);
        assertBadRange(START, START);
        assertBadRange(START.plusSeconds(1), START);
        assertBadRange(START, START.plusSeconds(367L * 24 * 60 * 60));
    }

    @Test
    void rejectsUnboundedTopValues() {
        assertThatThrownBy(() -> ReportRange.validatedTop(0))
                .isInstanceOf(ReportManagementException.class);
        assertThatThrownBy(() -> ReportRange.validatedTop(101))
                .isInstanceOf(ReportManagementException.class);
    }

    private void assertBadRange(Instant from, Instant to) {
        assertThatThrownBy(() -> ReportRange.validated(from, to))
                .isInstanceOf(ReportManagementException.class);
    }
}
