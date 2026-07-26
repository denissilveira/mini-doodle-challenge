package com.doodle.mini.api.user;

import com.doodle.mini.service.user.UserService;
import com.doodle.mini.service.user.exception.EmailAlreadyExistsException;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-26T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-26T11:00:00Z");

    @Mock
    private UserService userService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        var controller = new UserController(userService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("on create user, with valid data, returns 201 (created)")
    void onCreateWithValidDataReturns201Created() throws Exception {

        UUID userId = UUID.randomUUID();
        CreateUserRequest request = buildDefaultCreateUserRequest();
        when(userService.create(any(CreateUserRequest.class))).thenReturn(buildUserResponse(userId));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/" + userId))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("denis@example.com"));

        verify(userService).create(any(CreateUserRequest.class));
    }

    @Test
    @DisplayName("on create user, with invalid data, returns 400 (bad request)")
    void onCreateWithInvalidDataReturns400BadRequest() throws Exception {

        CreateUserRequest request = buildInvalidCreateUserRequest();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations.name").exists())
                .andExpect(jsonPath("$.violations.email").exists());
        verify(userService, never()).create(any());
    }


    @Test
    @DisplayName("on create user, with duplicated email, returns 409 (conflict)")
    void onCreateWithDuplicatedEmailReturns409Conflict() throws Exception {

        CreateUserRequest request = buildDefaultCreateUserRequest();

        when(userService.create(any(CreateUserRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("denis@example.com"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

        verify(userService, times(1)).create(any(CreateUserRequest.class));
    }


    @Test
    @DisplayName("on find user by id, with existing user, returns 200 (ok)")
    void onFindByIdWithExistingUserReturns200Ok()
            throws Exception {

        var userId = UUID.randomUUID();
        var request = get("/api/v1/users/{userId}", userId);

        when(userService.findById(userId)).thenReturn(buildUserResponse(userId));

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("denis@example.com"));

        verify(userService, times(1)).findById(userId);
    }


    @Test
    @DisplayName("on find user by id, with unknown user, returns 404 (not found)")
    void onFindByIdWithUnknownUserReturns404NotFound() throws Exception {

        var userId = UUID.randomUUID();
        var request = get("/api/v1/users/{userId}", userId);

        when(userService.findById(userId)).thenThrow(new UserNotFoundException(userId));

        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/" + userId));

        verify(userService, times(1)).findById(userId);
    }


    @Test
    @DisplayName("on find user by email, with existing user, returns 200 (ok)")
    void onFindByEmailWithExistingUserReturns200Ok()
            throws Exception {

        var userId = UUID.randomUUID();

        when(userService.findByEmail("denis@example.com")).thenReturn(buildUserResponse(userId));

        mockMvc.perform(get("/api/v1/users/email")
                        .queryParam("email", "denis@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email")
                        .value("denis@example.com"));

        verify(userService, times(1)).findByEmail("denis@example.com");
    }

    @Test
    @DisplayName("on list users, with pagination, returns 200 (paged users)")
    void onFindAllWithPaginationReturns200PagedUsers() throws Exception {

        var userId = UUID.randomUUID();

        var page = new PageResponse<>(
                List.of(buildUserResponse(userId)),
                0,
                10,
                1,
                1,
                true,
                true
        );

        when(userService.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/users")
                        .queryParam("page", "0")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(userId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));

        verify(userService, times(1))
                .findAll(argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 10));
    }

    @Test
    @DisplayName("on update user, with valid data, returns 200 (updated user)")
    void onUpdateWithValidDataReturns200UpdatedUser()
            throws Exception {

        var userId = UUID.randomUUID();
        var request = buildDefaultUpdateUserRequest();

        var response = new UserResponse(
                userId,
                "Denis Updated",
                "updated@example.com",
                "America/Sao_Paulo",
                CREATED_AT,
                UPDATED_AT
        );

        when(userService.update(eq(userId), any(UpdateUserRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/users/{userId}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Denis Updated"))
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"));

        verify(userService, times(1))
                .update(eq(userId), any(UpdateUserRequest.class));
    }

    private static CreateUserRequest  buildDefaultCreateUserRequest() {
        return new CreateUserRequest(
                "Denis Silveira",
                "denis@example.com",
                "Europe/Madrid"
        );
    }

    private static UpdateUserRequest  buildDefaultUpdateUserRequest() {
        return new UpdateUserRequest(
                "Denis Updated",
                "updated@example.com",
                "America/Sao_Paulo"
        );
    }

    private static CreateUserRequest  buildInvalidCreateUserRequest() {
        return new CreateUserRequest(
                null,
                "invalid-email",
                "Europe/Madrid"
        );
    }

    private static UserResponse buildUserResponse(UUID userId) {
        return new UserResponse(
                userId,
                "Denis Silveira",
                "denis@example.com",
                "Europe/Madrid",
                CREATED_AT,
                UPDATED_AT
        );
    }
}