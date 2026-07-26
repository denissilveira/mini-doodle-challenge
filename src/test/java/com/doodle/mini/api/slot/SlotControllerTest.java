package com.doodle.mini.api.slot;

import com.doodle.mini.domain.slot.SlotStatus;
import com.doodle.mini.infrastructure.web.SlotExceptionHandler;
import com.doodle.mini.service.slot.SlotService;
import com.doodle.mini.service.slot.exception.SlotNotFoundException;
import com.doodle.mini.service.slot.exception.SlotOverlapException;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
class SlotControllerTest {

    private static final Instant START_AT = Instant.parse("2026-07-27T09:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-07-27T10:00:00Z");

    @Mock
    private SlotService slotService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new SlotController(slotService))
                .setControllerAdvice(new SlotExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("on create slot, with valid data, returns 201 (created)")
    void onCreateWithValidDataReturns201Created() throws Exception {
        var userId = UUID.randomUUID();
        var slotId = UUID.randomUUID();
        var request = buildDefaultCreateSlotRequest();

        when(slotService.create(eq(userId), any(CreateSlotRequest.class)))
                .thenReturn(buildSlotResponse(slotId));

        mockMvc.perform(post("/api/v1/users/{userId}/slots", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/slots/" + slotId))
                .andExpect(jsonPath("$.id").value(slotId.toString()))
                .andExpect(jsonPath("$.status").value("FREE"));

        verify(slotService).create(eq(userId), any(CreateSlotRequest.class));
    }

    @Test
    @DisplayName("on create slot, with invalid data, returns 400 (bad request)")
    void onCreateWithInvalidDataReturns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/slots", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(slotService, never()).create(any(), any());
    }

    @Test
    @DisplayName("on create slot, with overlapping range, returns 409 (conflict)")
    void onCreateWithOverlappingRangeReturns409Conflict() throws Exception {
        var userId = UUID.randomUUID();
        var request = buildDefaultCreateSlotRequest();

        when(slotService.create(eq(userId), any(CreateSlotRequest.class)))
                .thenThrow(new SlotOverlapException());

        mockMvc.perform(post("/api/v1/users/{userId}/slots", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_OPERATION_CONFLICT"));
    }

    @Test
    @DisplayName("on find slot by id, with existing slot, returns 200 (ok)")
    void onFindByIdWithExistingSlotReturns200Ok() throws Exception {
        var slotId = UUID.randomUUID();
        when(slotService.findById(slotId)).thenReturn(buildSlotResponse(slotId));

        mockMvc.perform(get("/api/v1/slots/{slotId}", slotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(slotId.toString()));
    }

    @Test
    @DisplayName("on find slot by id, with unknown slot, returns 404 (not found)")
    void onFindByIdWithUnknownSlotReturns404NotFound() throws Exception {

        var slotId = UUID.randomUUID();
        when(slotService.findById(slotId)).thenThrow(new SlotNotFoundException(slotId));

        mockMvc.perform(get("/api/v1/slots/{slotId}", slotId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULING_RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("on list slots, with selected range, returns 200 (paged slots)")
    void onFindAllWithSelectedRangeReturns200PagedSlots() throws Exception {

        var userId = UUID.randomUUID();
        var slotId = UUID.randomUUID();

        when(slotService.findAll(eq(userId), eq(START_AT), eq(END_AT), isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(buildSlotResponse(slotId)), 0, 50, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/users/{userId}/slots", userId)
                        .queryParam("from", START_AT.toString())
                        .queryParam("to", END_AT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("on update slot, with valid data, returns 200 (updated slot)")
    void onUpdateWithValidDataReturns200UpdatedSlot() throws Exception {
        var slotId = UUID.randomUUID();
        var request = buildDefaultCreateSlotRequest();

        when(slotService.update(eq(slotId), any(UpdateSlotRequest.class)))
                .thenReturn(new SlotResponse(slotId, END_AT, END_AT.plusSeconds(3600), SlotStatus.FREE, null, null));

        mockMvc.perform(put("/api/v1/slots/{slotId}", slotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").value("2026-07-27T10:00:00Z"));
    }

    @Test
    @DisplayName("on delete slot, with existing slot, returns 204 (no content)")
    void onDeleteWithExistingSlotReturns204NoContent() throws Exception {
        var slotId = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/slots/{slotId}", slotId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(slotService, times(1)).delete(slotId);
    }

    private static SlotResponse buildSlotResponse(UUID slotId) {
        return new SlotResponse(slotId, START_AT, END_AT, SlotStatus.FREE, null, null);
    }

    private static CreateSlotRequest buildDefaultCreateSlotRequest() {
        return new CreateSlotRequest(START_AT, END_AT);
    }

}
