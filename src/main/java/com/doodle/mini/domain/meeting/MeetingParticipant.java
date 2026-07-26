package com.doodle.mini.domain.meeting;

import com.doodle.mini.domain.user.User;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "meeting_participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MeetingParticipant {

    @Valid
    @NotNull
    @EmbeddedId
    @EqualsAndHashCode.Include
    private MeetingParticipantId id;

    @Valid
    @NotNull
    @MapsId("meetingId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Valid
    @NotNull
    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private MeetingParticipant(
            Meeting meeting,
            User user) {
        this.meeting = meeting;
        this.user = user;
        this.id = new MeetingParticipantId(meeting.getId(), user.getId());
    }

    static MeetingParticipant create(
            Meeting meeting,
            User user) {
        return new MeetingParticipant(meeting, user);
    }
}