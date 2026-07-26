package com.doodle.mini.service.user;

import com.doodle.mini.api.user.CreateUserRequest;
import com.doodle.mini.api.user.UpdateUserRequest;
import com.doodle.mini.api.user.UserResponse;
import com.doodle.mini.domain.calendar.Calendar;
import com.doodle.mini.domain.user.User;
import com.doodle.mini.infrastructure.persistence.calendar.CalendarRepository;
import com.doodle.mini.infrastructure.persistence.user.UserRepository;
import com.doodle.mini.domain.calendar.exception.CalendarNotFoundException;
import com.doodle.mini.service.user.exception.EmailAlreadyExistsException;
import com.doodle.mini.service.user.exception.InvalidTimezoneException;
import com.doodle.mini.service.user.exception.UserNotFoundException;
import com.doodle.mini.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
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

        var user = User.create(request.name(), request.email());
        ensureEmailIsAvailable(user.getEmail(), null);
        var timezone = parseTimezone(request.timezone());

        try {
            userRepository.saveAndFlush(user);

            Calendar calendar = Calendar.createFor(user, timezone);
            calendarRepository.save(calendar);

            log.info("User created. userId={}", user.getId());
            return UserResponse.from(user, calendar);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailConstraintViolation(exception)) {
                throw new EmailAlreadyExistsException(user.getEmail());
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID userId) {
        var user = findUser(userId);
        var calendar = findCalendar(userId);
        return UserResponse.from(user, calendar);
    }

    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        var user = findUserByEmail(email);
        var calendar = findCalendar(user.getId());
        return UserResponse.from(user, calendar);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(Pageable pageable) {
        var users = userRepository.findAll(pageable);

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

        var calendarsByUserId = calendarRepository
                .findAllByUserIdIn(users.getContent().stream().map(User::getId).toList())
                .stream()
                .collect(Collectors.toMap(calendar -> calendar.getUser().getId(), Function.identity()));

        var responsePage = users
                .map(user -> { Calendar calendar = calendarsByUserId.get(user.getId());
            return UserResponse.from(user, calendar);
        });

        return PageResponse.from(responsePage);
    }

    @Transactional
    public UserResponse update(UUID userId, UpdateUserRequest request) {
        var user = findUser(userId);
        var calendar = findCalendar(userId);

        ensureEmailIsAvailable(request.email(), userId);
        var timezone = parseTimezone(request.timezone());

        user.update(request.name(), request.email());
        calendar.changeTimezone(timezone);

        try {
            userRepository.flush();
            log.info("User updated. userId={}", user.getId());
            return UserResponse.from(user, calendar);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailConstraintViolation(exception)) {
                throw new EmailAlreadyExistsException(request.email());
            }
            throw exception;
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

    private static boolean isEmailConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                return "ux_users_email_lower".equals(cve.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    private Calendar findCalendar(UUID userId) {
        return calendarRepository.findByUserId(userId)
                .orElseThrow(() -> new CalendarNotFoundException(userId));
    }
}
