package com.doodle.mini.domain.slot;

import com.doodle.mini.domain.calendar.Calendar;
import com.doodle.mini.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "slots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Slot {

    @Id
    @NotNull
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Valid
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Valid
    @NotNull
    @Embedded
    private TimeRange timeRange;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SlotStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Slot(Calendar calendar, TimeRange timeRange) {
        this.id = UUID.randomUUID();
        this.calendar = calendar;
        this.timeRange = timeRange;
        this.status = SlotStatus.FREE;
    }

    public boolean isFree() {
        return status == SlotStatus.FREE;
    }

    public boolean isBlocked() {
        return status == SlotStatus.BLOCKED;
    }

    public boolean isBooked() {
        return status == SlotStatus.BOOKED;
    }

    public static Slot createFree(
            Calendar calendar,
            TimeRange timeRange) {
        return new Slot(calendar, timeRange);
    }

    public void moveTo(TimeRange newTimeRange) {
        if (isBooked()) {
            throw new IllegalStateException("A booked slot cannot be moved");
        }
        this.timeRange = newTimeRange;
    }

    public void block() {
        if (isBooked()) {
            throw new IllegalStateException("A booked slot cannot be blocked");
        }
        status = SlotStatus.BLOCKED;
    }

    public void markAsFree() {
        if (isBooked()) {
            throw new IllegalStateException("A booked slot cannot be released directly");
        }

        status = SlotStatus.FREE;
    }

    public void book() {
        if (!isFree()) {
            throw new IllegalStateException("Only free slots can be booked");
        }
        status = SlotStatus.BOOKED;
    }

    public void cancelBooking() {
        if (!isBooked()) {
            throw new IllegalStateException("Only booked slots can be released");
        }
        status = SlotStatus.FREE;
    }

    public boolean isOwnedBy(User user) {
        if (user == null) {
            return false;
        }
        return calendar
                .getUser()
                .sameIdentityAs(user);
    }
}