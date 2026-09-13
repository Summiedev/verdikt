package com.verdikt.verdikt_backend.repository;

import com.verdikt.verdikt_backend.config.CacheConstants;
import com.verdikt.verdikt_backend.model.Room;
import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    @Cacheable(value = CacheConstants.ROOM_BY_CODE, key = "#code")
    Optional<Room> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.code = :code")
    Optional<Room> findByCodeForUpdate(@org.springframework.data.repository.query.Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    boolean existsByCode(String code);
    List<Room> findAllByStatusAndExpiresAtBefore(RoomStatus status, Instant time);

    @Modifying
    @Query("UPDATE Room r SET r.status = com.verdikt.verdikt_backend.model.enums.RoomStatus.EXPIRED WHERE r.status IN (:statuses) AND r.expiresAt < :now")
    int bulkExpireStaleRooms(@org.springframework.data.repository.query.Param("statuses") List<RoomStatus> statuses, @org.springframework.data.repository.query.Param("now") Instant now);

    long countByStatusNot(RoomStatus status);
}
