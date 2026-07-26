package com.doodle.mini.domain.calendar;

import com.doodle.mini.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "calendars")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Calendar {

    @Id
    @NotNull
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Valid
    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true
    )
    private User user;

    @NotBlank
    @Column(nullable = false)
    private String timezone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Calendar(User user, ZoneId timezone) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.timezone = timezone.getId();
    }

    public static Calendar createFor(User user) {
        return new Calendar(user, ZoneId.of("UTC"));
    }

    public static Calendar createFor(User user, ZoneId timezone) {
        return new Calendar(user, timezone);
    }

    public void changeTimezone(ZoneId timezone) {
        this.timezone = Objects.requireNonNull(timezone, "Timezone is required").getId();
    }
}
