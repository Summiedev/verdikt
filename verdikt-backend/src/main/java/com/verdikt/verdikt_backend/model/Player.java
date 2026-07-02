package com.verdikt.verdikt_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "players",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"room_id", "name"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Room room;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, unique = true)
    private UUID token;

    @Column(name = "is_host", nullable = false)
    @Builder.Default
    private boolean isHost = false;

    @Column(name = "is_original_host", nullable = false)
    @Builder.Default
    private boolean isOriginalHost = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    public void prePersist() {
        this.joinedAt = LocalDateTime.now();
        if (this.token == null) this.token = UUID.randomUUID();
    }
}