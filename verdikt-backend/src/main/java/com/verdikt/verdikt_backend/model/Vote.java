package com.verdikt.verdikt_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "votes",
    uniqueConstraints = {
        // same voter cannot pick the same person twice for the same question
        // but CAN vote for multiple different people — WhatsApp style
        @UniqueConstraint(columnNames = {"room_id", "question_id", "voter_id", "voted_for_id"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private Player voter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voted_for_id", nullable = false)
    private Player votedFor;

    @Column(name = "cast_at", nullable = false, updatable = false)
    private LocalDateTime castAt;

    @PrePersist
    public void prePersist() {
        this.castAt = LocalDateTime.now();
    }
}