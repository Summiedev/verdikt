package com.verdikt.verdikt_backend.model;

import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import com.verdikt.verdikt_backend.model.enums.VoteMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 6)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_mode", nullable = false)
    private VoteMode voteMode;

    @Column(name = "host_player_id")
    private UUID hostPlayerId;

    @Column(name = "max_players", nullable = false)
    @Builder.Default
    private int maxPlayers = 20;

    @Column(name = "max_questions")
    private Integer maxQuestions;

    @Column(name = "question_duration_seconds")
    private Integer questionDurationSeconds;

    @Column(name = "current_question_started_at")
    private Instant currentQuestionStartedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Player> players = new ArrayList<>();

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RoomQuestion> roomQuestions = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        this.expiresAt = Instant.now().plusSeconds(2 * 60 * 60); // 2 hours
        if (this.status == null) this.status = RoomStatus.WAITING;
        if (this.voteMode == null) this.voteMode = VoteMode.PUBLIC;
    }
}