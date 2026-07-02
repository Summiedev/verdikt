package com.verdikt.verdikt_backend.repository;

import com.verdikt.verdikt_backend.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerRepository extends JpaRepository<Player, UUID> {
    Optional<Player> findByToken(UUID token);
    List<Player> findAllByRoomId(UUID roomId);
    boolean existsByRoomIdAndName(UUID roomId, String name);
    int countByRoomId(UUID roomId);
}