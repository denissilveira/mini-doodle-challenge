package com.doodle.mini.domain.meeting;

import com.doodle.mini.domain.meeting.exception.OrganizerCannotBeParticipantException;
import com.doodle.mini.domain.slot.Slot;
import com.doodle.mini.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.*;

@Getter
@Entity
@Table(name = "meetings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Meeting {

    @Id
    @NotNull
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Valid
    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false, unique = true)
    private Slot slot;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Valid
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MeetingParticipant> participants = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Meeting(
            Slot slot,
            String title,
            String description) {
        this.id = UUID.randomUUID();
        this.slot = slot;
        this.title = normalizeTitle(title);
        this.description = normalizeDescription(description);
    }

    public Set<MeetingParticipant> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    public void updateDetails(String title, String description) {
        this.title = normalizeTitle(title);
        this.description = normalizeDescription(description);
    }

    private static String normalizeTitle(String title) {
        return title == null ? null : title.trim();
    }

    private static String normalizeDescription(String description) {
        if (description == null) return null;
        var trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static Meeting schedule(Slot slot, String title, String description) {
        slot.book();
        return new Meeting(slot, title, description);
    }

    public void addParticipant(User user) {
        if (slot.isOwnedBy(user)) {
            throw new OrganizerCannotBeParticipantException(user.getId());
        }

        participants.add(MeetingParticipant.create(this, user));
    }

    public void replaceParticipants(Collection<User> users) {
        participants.clear();
        users.forEach(this::addParticipant);
    }

}
