package com.verdikt.verdikt_backend.scheduler;

import com.verdikt.verdikt_backend.model.Room;
import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import com.verdikt.verdikt_backend.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomCleanupScheduler {

    private final RoomRepository roomRepository;

    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void expireStaleRooms() {
        Instant now = Instant.now();
        List<RoomStatus> targetStatuses = List.of(RoomStatus.WAITING, RoomStatus.IN_PROGRESS);
        int expired = roomRepository.bulkExpireStaleRooms(targetStatuses, now);

        if (expired > 0) {
            log.info("Room cleanup: expired {} stale rooms", expired);
        }
    }

    @Scheduled(fixedRate = 24 * 60 * 60 * 1000)
    @Transactional
    public void purgeOldExpiredRooms() {
        Instant cutoff = Instant.now().minusSeconds(24 * 60 * 60);
        List<Room> oldRooms = roomRepository.findAllByStatusAndExpiresAtBefore(RoomStatus.EXPIRED, cutoff);

        if (!oldRooms.isEmpty()) {
            roomRepository.deleteAll(oldRooms);
            log.info("Purged {} old expired rooms from database", oldRooms.size());
        }
    }
}