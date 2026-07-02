package com.verdikt.verdikt_backend.repository;

import com.verdikt.verdikt_backend.model.RoomQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomQuestionRepository extends JpaRepository<RoomQuestion, UUID> {
    List<RoomQuestion> findAllByRoomIdOrderByOrderIndex(UUID roomId);
    Optional<RoomQuestion> findByRoomIdAndIsActiveTrue(UUID roomId);
    int countByRoomId(UUID roomId);
    int countByRoomIdAndIsAnsweredTrue(UUID roomId);
}