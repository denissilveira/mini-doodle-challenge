package com.doodle.mini.integration;

import com.doodle.mini.api.meeting.CreateMeetingRequest;
import com.doodle.mini.api.slot.CreateSlotRequest;
import com.doodle.mini.api.slot.TimeInterval;
import com.doodle.mini.api.user.CreateUserRequest;
import com.doodle.mini.domain.slot.SlotStatus;
import com.doodle.mini.service.meeting.MeetingService;
import com.doodle.mini.service.slot.SlotService;
import com.doodle.mini.service.slot.exception.SlotOverlapException;
import com.doodle.mini.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PersistenceIntegrationTest {

    private static final Instant NINE = Instant.parse("2026-08-15T09:00:00Z");
    private static final Instant TEN = Instant.parse("2026-08-15T10:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-08-15T11:00:00Z");
    private static final Instant NOON = Instant.parse("2026-08-15T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MeetingService meetingService;
    @Autowired private SlotService slotService;
    @Autowired private UserService userService;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE meeting_participants, meetings, slots, calendars, users CASCADE");
    }

    @Test
    @DisplayName("invited users see the meeting and its interval in their effective availability")
    void invitedUserSeesMeetingAndBusyInterval() {
        var organizer = userService.create(
                new CreateUserRequest("Organizer", "organizer@example.com", "Europe/Madrid"));
        var participant = userService.create(
                new CreateUserRequest("Participant", "participant@example.com", "Europe/Madrid"));

        var participantSlot = slotService.create(
                participant.id(),
                new CreateSlotRequest(NINE, NOON));
        var organizerSlot = slotService.create(
                organizer.id(),
                new CreateSlotRequest(TEN, ELEVEN));

        var meeting = meetingService.create(
                organizerSlot.id(),
                new CreateMeetingRequest(
                        "Architecture review",
                        "Review the scheduling model",
                        Set.of(participant.id())));

        var participantMeetings = meetingService.findAllByUserId(
                participant.id(),
                PageRequest.of(0, 10));
        var availability = slotService.getAvailability(
                participant.id(),
                NINE.minusSeconds(3600),
                NOON.plusSeconds(3600));

        assertThat(participantMeetings.content())
                .extracting(response -> response.id())
                .containsExactly(meeting.id());
        assertThat(availability.free()).containsExactly(
                new TimeInterval(NINE, TEN),
                new TimeInterval(ELEVEN, NOON));
        assertThat(availability.busy()).containsExactly(
                new TimeInterval(TEN, ELEVEN));
        assertThat(slotService.findById(participantSlot.id()).status())
                .isEqualTo(SlotStatus.FREE);
    }

    @Test
    @DisplayName("adjacent slots are accepted and overlapping slots are rejected")
    void adjacentSlotsAreAcceptedAndOverlappingSlotsAreRejected() {
        var user = userService.create(
                new CreateUserRequest("Slot owner", "owner@example.com", "UTC"));

        slotService.create(user.id(), new CreateSlotRequest(NINE, TEN));
        slotService.create(user.id(), new CreateSlotRequest(TEN, ELEVEN));

        assertThatThrownBy(() -> slotService.create(
                user.id(),
                new CreateSlotRequest(NINE.plusSeconds(1800), TEN.plusSeconds(1800))))
                .isInstanceOf(SlotOverlapException.class);
    }

    @Test
    @DisplayName("participant meeting pagination executes an accurate count query")
    void participantMeetingPaginationExecutesAccurateCountQuery() {
        var organizer = userService.create(
                new CreateUserRequest("Page organizer", "page-organizer@example.com", "UTC"));
        var participant = userService.create(
                new CreateUserRequest("Page participant", "page-participant@example.com", "UTC"));

        var firstSlot = slotService.create(
                organizer.id(),
                new CreateSlotRequest(NINE, TEN));
        var secondSlot = slotService.create(
                organizer.id(),
                new CreateSlotRequest(TEN, ELEVEN));

        meetingService.create(
                firstSlot.id(),
                new CreateMeetingRequest("First meeting", null, Set.of(participant.id())));
        meetingService.create(
                secondSlot.id(),
                new CreateMeetingRequest("Second meeting", null, Set.of(participant.id())));

        var page = meetingService.findAllByUserId(
                participant.id(),
                PageRequest.of(0, 1));

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("concurrent overlapping slot requests persist only one interval")
    void concurrentOverlappingSlotRequestsPersistOnlyOneInterval() throws Exception {
        var user = userService.create(
                new CreateUserRequest("Concurrent owner", "concurrent@example.com", "UTC"));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createAfterSignal(
                    user.id(),
                    new CreateSlotRequest(NINE, TEN.plusSeconds(1800)),
                    ready,
                    start));
            var second = executor.submit(() -> createAfterSignal(
                    user.id(),
                    new CreateSlotRequest(TEN, ELEVEN),
                    ready,
                    start));

            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                assertThat(List.of(
                        first.get(10, TimeUnit.SECONDS),
                        second.get(10, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(true, false);
            } finally {
                start.countDown();
                first.cancel(true);
                second.cancel(true);
            }
        }

        var slots = slotService.findAll(
                user.id(),
                NINE.minusSeconds(1),
                ELEVEN.plusSeconds(1),
                null,
                PageRequest.of(0, 10));
        assertThat(slots.totalElements()).isEqualTo(1);
    }

    private boolean createAfterSignal(
            java.util.UUID userId,
            CreateSlotRequest request,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {

        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent slot test did not start in time");
        }
        try {
            slotService.create(userId, request);
            return true;
        } catch (SlotOverlapException exception) {
            return false;
        }
    }
}
