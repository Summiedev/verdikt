package com.verdikt.verdikt_backend.repository;

import com.verdikt.verdikt_backend.config.CacheConstants;
import com.verdikt.verdikt_backend.model.Player;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerRepository extends JpaRepository<Player, UUID> {
    @Cacheable(value = CacheConstants.PLAYER_BY_TOKEN, key = "#token")
    @Query("SELECT p FROM Player p JOIN FETCH p.room WHERE p.token = :token AND p.expiresAt > :now")
    Optional<Player> findByTokenAndExpiresAtAfter(@Param("token") UUID token, @Param("now") LocalDateTime now);

    @Query("SELECT p FROM Player p JOIN FETCH p.room WHERE p.token = :token")
    Optional<Player> findByToken(@Param("token") UUID token);

    List<Player> findAllByRoomId(UUID roomId);

    @Query("SELECT p FROM Player p JOIN FETCH p.room WHERE p.id IN :ids")
    List<Player> findAllByIdWithRoom(@Param("ids") List<UUID> ids);

    boolean existsByRoomIdAndName(UUID roomId, String name);
    int countByRoomId(UUID roomId);
}