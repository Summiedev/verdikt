package com.verdikt.verdikt_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
    name = "room_questions",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"room_id", "question_id"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "is_answered", nullable = false)
    @Builder.Default
    private boolean isAnswered = false;

    // tracks if current question voting is still open
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = false;
}