package com.verdikt.verdikt_backend.repository;

import com.verdikt.verdikt_backend.model.Room;
import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByCode(String code);
    boolean existsByCode(String code);
    List<Room> findAllByStatusAndExpiresAtBefore(RoomStatus status, Instant time);
}