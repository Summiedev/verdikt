package com.verdikt.verdikt_backend.model;

import com.verdikt.verdikt_backend.model.enums.SpiceLevel;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false, length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "spice_level", nullable = false)
    private SpiceLevel spiceLevel;

    @Column(name = "is_custom", nullable = false)
    @Builder.Default
    private boolean isCustom = false;
}