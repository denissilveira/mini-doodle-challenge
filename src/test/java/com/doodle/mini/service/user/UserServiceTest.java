package com.doodle.mini.service.user;

import com.doodle.mini.api.user.CreateUserRequest;
import com.doodle.mini.api.user.UpdateUserRequest;
import com.doodle.mini.domain.calendar.Calendar;
import com.doodle.mini.domain.user.User;
import com.doodle.mini.infrastructure.persistence.calendar.CalendarRepository;
import com.doodle.mini.infrastructure.persistence.user.UserRepository;
import com.doodle.mini.service.user.exception.EmailAlreadyExistsException;
import com.doodle.mini.service.user.exception.InvalidTimezoneException;
import com.doodle.mini.service.user.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CalendarRepository calendarRepository;
    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("on create user, with valid data, returns user and creates calendar")
    void onCreateWithValidDataReturnsUserAndCreatesCalendar() {
        var request = buildDefaultCreateUserRequest();

        when(userRepository.existsByEmailIgnoreCase("denis@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(calendarRepository.save(any(Calendar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.create(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Denis Silveira");
        assertThat(response.email()).isEqualTo("denis@example.com");
        assertThat(response.timezone()).isEqualTo("Europe/Madrid");

        verify(userRepository, times(1)).existsByEmailIgnoreCase("denis@example.com");
        verify(userRepository, times(1)).saveAndFlush(any(User.class));
        verify(calendarRepository, times(1)).save(any(Calendar.class));
    }

    @Test
    @DisplayName("on create user, without timezone, returns user with UTC timezone")
    void onCreateWithoutTimezoneReturnsUserWithUtcTimezone() {
        var request = new CreateUserRequest("Denis Silveira", "denis@example.com", null);

        when(userRepository.existsByEmailIgnoreCase("denis@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(calendarRepository.save(any(Calendar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.create(request);
        assertThat(response.timezone()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("on create user, with duplicated email, throws EmailAlreadyExistsException")
    void onCreateWithDuplicatedEmailThrowsEmailAlreadyExistsException() {
        var request = buildDefaultCreateUserRequest();

        when(userRepository.existsByEmailIgnoreCase("denis@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request)).isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(calendarRepository, never()).save(any(Calendar.class));
    }

    @Test
    @DisplayName(
            "on create user, with invalid timezone, throws InvalidTimezoneException"
    )
    void onCreateWithInvalidTimezoneThrowsInvalidTimezoneException() {
        var request = new CreateUserRequest("Denis Silveira", "denis@example.com", "Invalid/Timezone");

        when(userRepository.existsByEmailIgnoreCase("denis@example.com")).thenReturn(false);

        assertThatThrownBy(() -> userService.create(request)).isInstanceOf(InvalidTimezoneException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("on find user by id, with existing user, returns user")
    void onFindByIdWithExistingUserReturnsUser() {
        var user = user();
        var calendar = calendar(user);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(calendarRepository.findByUserId(user.getId())).thenReturn(Optional.of(calendar));

        var response = userService.findById(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("denis@example.com");
        assertThat(response.timezone()).isEqualTo("Europe/Madrid");

        verify(userRepository, times(1)).findById(user.getId());
        verify(calendarRepository, times(1)).findByUserId(user.getId());
    }

    @Test
    @DisplayName("on find user by id, with unknown user, throws UserNotFoundException")
    void onFindByIdWithUnknownUserThrowsUserNotFoundException() {
        var userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(userId)).isInstanceOf(UserNotFoundException.class);

        verify(calendarRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("on list users, with existing users, returns paged users")
    void onFindAllWithExistingUsersReturnsPagedUsers() {
        var firstUser = user();
        var secondUser = User.create("Fabiana", "fabiana@example.com");
        var firstCalendar = calendar(firstUser);
        var secondCalendar = Calendar.createFor(secondUser, ZoneId.of("America/Sao_Paulo"));

        var pageable = PageRequest.of(0, 10);

        when(userRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(firstUser, secondUser), pageable, 2));
        when(calendarRepository.findAllByUserIdIn(List.of(firstUser.getId(), secondUser.getId())))
                .thenReturn(List.of(firstCalendar, secondCalendar));

        var response = userService.findAll(pageable);

        assertThat(response.content()).hasSize(2);
        assertThat(response.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("on update user, with valid data, returns updated user")
    void onUpdateWithValidDataReturnsUpdatedUser() {
        var user = user();
        var calendar = calendar(user);
        var request = buildDefaultUpdateUserRequest();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(calendarRepository.findByUserId(user.getId())).thenReturn(Optional.of(calendar));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("updated@example.com", user.getId())).thenReturn(false);

        var response = userService.update(user.getId(), request);

        assertThat(response.name()).isEqualTo("Denis Updated");
        assertThat(response.email()).isEqualTo("updated@example.com");
        assertThat(response.timezone()).isEqualTo("America/Sao_Paulo");
        verify(userRepository, times(1)).flush();
    }

    private User user() {
        return User.create("Denis Silveira", "denis@example.com");
    }

    private Calendar calendar(User user) {
        return Calendar.createFor(user, ZoneId.of("Europe/Madrid"));
    }

    private static CreateUserRequest buildDefaultCreateUserRequest() {
        return new CreateUserRequest(
                "Denis Silveira",
                "denis@example.com",
                "Europe/Madrid"
        );
    }

    private static UpdateUserRequest buildDefaultUpdateUserRequest() {
        return new UpdateUserRequest(
                "Denis Updated",
                "updated@example.com",
                "America/Sao_Paulo"
        );
    }
}