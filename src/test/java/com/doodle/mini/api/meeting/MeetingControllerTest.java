package com.doodle.mini.api.meeting;

import com.doodle.mini.domain.slot.SlotStatus;
import com.doodle.mini.infrastructure.web.MeetingExceptionHandler;
import com.doodle.mini.service.meeting.MeetingService;
import com.doodle.mini.service.meeting.exception.MeetingNotFoundException;
import com.doodle.mini.service.meeting.exception.SlotNotAvailableForMeetingException;
import com.doodle.mini.service.user.exception.UserNotFoundException;
import com.doodle.mini.shared.api.GlobalExceptionHandler;
import com.doodle.mini.shared.api.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class MeetingControllerTest {

    @Mock private MeetingService meetingService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new MeetingController(meetingService))
                .setControllerAdvice(new GlobalExceptionHandler(), new MeetingExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("on create meeting, with valid data, returns 201 (created)")
    void onCreateWithValidDataReturns201Created() throws Exception {
        var slotId = UUID.randomUUID();
        var meetingId = UUID.randomUUID();
        var request = buildDefaultCreateMeetingRequest();
        when(meetingService.create(eq(slotId), any(CreateMeetingRequest.class)))
                .thenReturn(buildingMeetingResponse(meetingId, slotId));

        mockMvc.perform(post("/api/v1/slots/{slotId}/meetings", slotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/meetings/" + meetingId))
                .andExpect(jsonPath("$.id").value(meetingId.toString()))
                .andExpect(jsonPath("$.slotId").value(slotId.toString()));
        verify(meetingService, times(1)).create(eq(slotId), any(CreateMeetingRequest.class));
    }

    @Test
    @DisplayName("on create meeting, with blank title, returns 400 (bad request)")
    void onCreateWithBlankTitleReturns400BadRequest() throws Exception {
        var request = post("/api/v1/slots/{slotId}/meetings", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                { "title": " ", "participantIds": [] }
                """);
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(meetingService, never()).create(any(), any());
    }

    @Test
    @DisplayName("on create meeting, with unavailable slot, returns 409 (conflict)")
    void onCreateWithUnavailableSlotReturns409Conflict() throws Exception {
        var slotId = UUID.randomUUID();
        var request = buildDefaultCreateMeetingRequest();

        when(meetingService.create(eq(slotId), any(CreateMeetingRequest.class)))
                .thenThrow(new SlotNotAvailableForMeetingException(slotId, SlotStatus.BOOKED));

        mockMvc.perform(post("/api/v1/slots/{slotId}/meetings", slotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("on find meeting, with existing meeting, returns 200 (ok)")
    void onFindByIdWithExistingMeetingReturns200Ok() throws Exception {
        var meetingId = UUID.randomUUID();
        var slotId = UUID.randomUUID();

        when(meetingService.findById(meetingId)).thenReturn(buildingMeetingResponse(meetingId, slotId));

        mockMvc.perform(get("/api/v1/meetings/{meetingId}", meetingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(meetingId.toString()));
    }

    @Test
    @DisplayName("on find meeting, with unknown meeting, returns 404 (not found)")
    void onFindByIdWithUnknownMeetingReturns404NotFound() throws Exception {
        var meetingId = UUID.randomUUID();
        when(meetingService.findById(meetingId)).thenThrow(new MeetingNotFoundException(meetingId));
        mockMvc.perform(get("/api/v1/meetings/{meetingId}", meetingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    @DisplayName("on list meetings by user, with existing user, returns 200 (paged meetings)")
    void onFindAllByUserWithExistingUserReturns200PagedMeetings() throws Exception {
        var userId = UUID.randomUUID();
        var meetingId = UUID.randomUUID();
        var slotId = UUID.randomUUID();

        when(meetingService.findAllByUserId(eq(userId), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(buildingMeetingResponse(meetingId, slotId)), 0, 50, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/users/{userId}/meetings", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("on list meetings by user, with unknown user, returns 404 (not found)")
    void onFindAllByUserWithUnknownUserReturns404NotFound() throws Exception {
        var userId = UUID.randomUUID();

        when(meetingService.findAllByUserId(eq(userId), any(Pageable.class)))
                .thenThrow(new UserNotFoundException(userId));

        mockMvc.perform(get("/api/v1/users/{userId}/meetings", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("on update meeting, with valid data, returns 200 (updated meeting)")
    void onUpdateWithValidDataReturns200UpdatedMeeting() throws Exception {
        var meetingId = UUID.randomUUID();
        var slotId = UUID.randomUUID();
        var request = buildDefaultCreateMeetingRequest();

        when(meetingService.update(eq(meetingId), any(UpdateMeetingRequest.class)))
                .thenReturn(buildingMeetingResponse(meetingId, slotId));

        mockMvc.perform(put("/api/v1/meetings/{meetingId}", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(meetingId.toString()));
        verify(meetingService, times(1)).update(eq(meetingId), any(UpdateMeetingRequest.class));
    }

    @Test
    @DisplayName("on cancel meeting, with existing meeting, returns 204 (no content)")
    void onCancelWithExistingMeetingReturns204NoContent() throws Exception {
        var meetingId = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/meetings/{meetingId}", meetingId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(meetingService, times(1)).cancel(meetingId);
    }

    private static CreateMeetingRequest buildDefaultCreateMeetingRequest() {
        return new CreateMeetingRequest("Tech Challenge", "Tech Challenge meeting", Collections.emptySet());
    }

    private static MeetingResponse buildingMeetingResponse(UUID meetingId, UUID slotId) {
        var organizer = new UserSummaryResponse(UUID.randomUUID(), "Denis Silveira", "denis@example.com");
        return new MeetingResponse(
                meetingId,
                slotId,
                organizer,
                "Tech Challenge",
                "Tech Challenge meeting",
                Instant.parse("2026-07-28T09:00:00Z"),
                Instant.parse("2026-07-28T10:00:00Z"),
                List.of(),
                Instant.parse("2026-07-26T10:00:00Z"),
                Instant.parse("2026-07-26T10:00:00Z")
        );
    }
}
