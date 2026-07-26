package com.doodle.mini.service.user;

import com.doodle.mini.api.user.CreateUserRequest;
import com.doodle.mini.api.user.UpdateUserRequest;
import com.doodle.mini.api.user.UserResponse;
import com.doodle.mini.domain.calendar.Calendar;
import com.doodle.mini.domain.user.User;
import com.doodle.mini.infrastructure.persistence.calendar.CalendarRepository;
import com.doodle.mini.infrastructure.persistence.user.UserRepository;
import com.doodle.mini.service.user.exception.EmailAlreadyExistsException;
import com.doodle.mini.service.user.exception.InvalidTimezoneException;
import com.doodle.mini.service.user.exception.UserNotFoundException;
import com.doodle.mini.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("UTC");

    private final UserRepository userRepository;
    private final CalendarRepository calendarRepository;

    @Transactional
    public UserResponse create(CreateUserRequest request) {

        User user = User.create(request.name(), request.email());
        ensureEmailIsAvailable(user.getEmail(), null);

        ZoneId timezone = parseTimezone(request.timezone());

        try {
            userRepository.saveAndFlush(user);

            Calendar calendar = Calendar.createFor(user, timezone);
            calendarRepository.save(calendar);

            log.debug("User created successfully. email={}", user.getEmail());
            return UserResponse.from(user, calendar);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException(user.getEmail());
        }
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID userId) {
        User user = findUser(userId);
        Calendar calendar = findCalendar(userId);
        return UserResponse.from(user, calendar);
    }

    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        User user = findUserByEmail(email);
        Calendar calendar = findCalendar(user.getId());
        return UserResponse.from(user, calendar);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);

        if (users.isEmpty()) {
            return new PageResponse<>(
                    List.of(),
                    users.getNumber(),
                    users.getSize(),
                    users.getTotalElements(),
                    users.getTotalPages(),
                    users.isFirst(),
                    users.isLast()
            );
        }

        Map<UUID, Calendar> calendarsByUserId = calendarRepository
                .findAllByUserIdIn(users.getContent().stream().map(User::getId).toList())
                .stream()
                .collect(Collectors.toMap(calendar -> calendar.getUser().getId(), Function.identity()));

        Page<UserResponse> responsePage = users
                .map(user -> { Calendar calendar = calendarsByUserId.get(user.getId());
            return UserResponse.from(user, calendar);
        });

        return PageResponse.from(responsePage);
    }

    @Transactional
    public UserResponse update(UUID userId, UpdateUserRequest request) {
        User user = findUser(userId);
        Calendar calendar = findCalendar(userId);

        ensureEmailIsAvailable(request.email(), userId);
        ZoneId timezone = parseTimezone(request.timezone());

        user.update(request.name(), request.email());
        calendar.changeTimezone(timezone);

        try {
            userRepository.flush();
            log.debug("User updated successfully. email={}", user.getEmail());
            return UserResponse.from(user, calendar);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException(request.email());
        }
    }


    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    private void ensureEmailIsAvailable(String email, UUID ignoredUserId) {
        boolean exists = ignoredUserId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, ignoredUserId);

        if (exists) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    private ZoneId parseTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException exception) {
            throw new InvalidTimezoneException(timezone);
        }
    }

    private Calendar findCalendar(UUID userId) {
        return calendarRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Calendar not found for user: " + userId));
    }
}
