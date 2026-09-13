package com.verdikt.verdikt_backend.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.verdikt.verdikt_backend.model.Room;
import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import com.verdikt.verdikt_backend.repository.PlayerRepository;
import com.verdikt.verdikt_backend.repository.RoomRepository;

import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class BusinessMetricsService {

    private final MeterRegistry meterRegistry;
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;

    private Counter roomsCreatedCounter;
    private Counter votesCastCounter;
    private Counter playersJoinedCounter;
    private Counter gamesStartedCounter;

    @PostConstruct
    public void init() {
        roomsCreatedCounter = Counter.builder("verdikt.rooms.created")
                .description("Total rooms created")
                .register(meterRegistry);

        votesCastCounter = Counter.builder("verdikt.votes.cast")
                .description("Total votes cast")
                .register(meterRegistry);

        playersJoinedCounter = Counter.builder("verdikt.players.joined")
                .description("Total players joined")
                .register(meterRegistry);

        gamesStartedCounter = Counter.builder("verdikt.games.started")
                .description("Total games started")
                .register(meterRegistry);

        Gauge.builder("verdikt.rooms.active")
                .description("Active rooms not expired")
                .register(meterRegistry, roomRepository, repo -> repo.countByStatusNot(RoomStatus.EXPIRED));

        Gauge.builder("verdikt.players.active")
                .description("Active players")
                .register(meterRegistry, playerRepository, repo -> repo.countByIsActiveTrue());
    }

    public void incrementRoomsCreated() {
        roomsCreatedCounter.increment();
    }

    public void incrementVotesCast() {
        votesCastCounter.increment();
    }

    public void incrementPlayersJoined() {
        playersJoinedCounter.increment();
    }

    public void incrementGamesStarted() {
        gamesStartedCounter.increment();
    }
}
