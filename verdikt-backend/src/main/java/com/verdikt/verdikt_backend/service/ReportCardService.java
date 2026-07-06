package com.verdikt.verdikt_backend.service;

import com.verdikt.verdikt_backend.dto.response.reportcard.*;
import com.verdikt.verdikt_backend.exception.*;
import com.verdikt.verdikt_backend.model.*;
import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import com.verdikt.verdikt_backend.model.enums.VoteMode;
import com.verdikt.verdikt_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportCardService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final RoomQuestionRepository roomQuestionRepository;
    private final VoteRepository voteRepository;

    @Transactional(readOnly = true)
    public ReportCardResponse generateReportCard(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found."));

        if (room.getStatus() != RoomStatus.FINISHED) {
            throw new InvalidRoomStateException("Report card isn't ready yet — game hasn't finished.");
        }

        List<Player> players = playerRepository.findAllByRoomId(roomId);
        List<RoomQuestion> roomQuestions = roomQuestionRepository.findAllByRoomIdOrderByOrderIndex(roomId);
        List<Vote> allVotes = voteRepository.findAllByRoomId(roomId);

        Map<UUID, Player> playerById = players.stream()
                .collect(Collectors.toMap(Player::getId, p -> p));

        List<PollCardResponse> pollCards = buildPollCards(roomQuestions, allVotes, playerById, room.getVoteMode());
        Map<UUID, List<String>> titlesByPlayer = extractTitles(pollCards);

        ReportCardOverviewResponse overview = buildOverview(room, players, allVotes, titlesByPlayer);
        List<PlayerTitleCardResponse> playerCards = buildPlayerCards(players, allVotes, titlesByPlayer);

        log.info("Report card generated: room={} players={} questions={}",
                room.getCode(), players.size(), roomQuestions.size());

        return ReportCardResponse.builder()
                .overview(overview)
                .pollCards(pollCards)
                .playerCards(playerCards)
                .build();
    }

    private List<PollCardResponse> buildPollCards(
            List<RoomQuestion> roomQuestions,
            List<Vote> allVotes,
            Map<UUID, Player> playerById,
            VoteMode voteMode
    ) {
        List<PollCardResponse> cards = new ArrayList<>();

        for (RoomQuestion rq : roomQuestions) {
            UUID questionId = rq.getQuestion().getId();

            List<Vote> votesForQuestion = allVotes.stream()
                    .filter(v -> v.getQuestion().getId().equals(questionId))
                    .collect(Collectors.toList());

            // group by votedFor -> list of voter names
            Map<UUID, List<String>> votedByMap = new HashMap<>();
            Map<UUID, Long> voteCounts = new HashMap<>();

            for (Vote v : votesForQuestion) {
                UUID votedForId = v.getVotedFor().getId();
                voteCounts.merge(votedForId, 1L, Long::sum);
                votedByMap.computeIfAbsent(votedForId, k -> new ArrayList<>())
                        .add(v.getVoter().getName());
            }

            long maxVotes = voteCounts.values().stream().max(Long::compareTo).orElse(0L);

            List<PollCardResponse.PollResultEntry> results = voteCounts.entrySet().stream()
                    .map(entry -> {
                        Player votedFor = playerById.get(entry.getKey());
                        return PollCardResponse.PollResultEntry.builder()
                                .playerId(entry.getKey())
                                .playerName(votedFor != null ? votedFor.getName() : "Unknown")
                                .voteCount(entry.getValue())
                                .isWinner(entry.getValue() == maxVotes)
                                .votedByNames(voteMode == VoteMode.PUBLIC ? votedByMap.get(entry.getKey()) : null)
                                .build();
                    })
                    .sorted((a, b) -> Long.compare(b.getVoteCount(), a.getVoteCount()))
                    .collect(Collectors.toList());

            cards.add(PollCardResponse.builder()
                    .questionId(questionId)
                    .questionText(rq.getQuestion().getText())
                    .orderIndex(rq.getOrderIndex())
                    .results(results)
                    .build());
        }

        return cards;
    }

    // maps playerId -> list of question titles they won (had the most votes for)
    private Map<UUID, List<String>> extractTitles(List<PollCardResponse> pollCards) {
        Map<UUID, List<String>> titles = new HashMap<>();

        for (PollCardResponse card : pollCards) {
            card.getResults().stream()
                    .filter(PollCardResponse.PollResultEntry::isWinner)
                    .forEach(entry -> titles.computeIfAbsent(entry.getPlayerId(), k -> new ArrayList<>())
                            .add(card.getQuestionText()));
        }

        return titles;
    }

    private ReportCardOverviewResponse buildOverview(
            Room room,
            List<Player> players,
            List<Vote> allVotes,
            Map<UUID, List<String>> titlesByPlayer
    ) {
        Map<UUID, Long> totalVotesByPlayer = allVotes.stream()
                .collect(Collectors.groupingBy(v -> v.getVotedFor().getId(), Collectors.counting()));

        List<ReportCardOverviewResponse.LeaderboardEntry> leaderboard = players.stream()
                .map(p -> ReportCardOverviewResponse.LeaderboardEntry.builder()
                        .playerName(p.getName())
                        .totalVotesReceived(totalVotesByPlayer.getOrDefault(p.getId(), 0L))
                        .topTitle(getTopTitle(titlesByPlayer.get(p.getId())))
                        .build())
                .sorted((a, b) -> Long.compare(b.getTotalVotesReceived(), a.getTotalVotesReceived()))
                .collect(Collectors.toList());

        return ReportCardOverviewResponse.builder()
                .roomName(room.getName())
                .totalPlayers(players.size())
                .totalQuestions(roomQuestions.size())
                .leaderboard(leaderboard)
                .verdict("THE GC HAS SPOKEN 🔥")
                .build();
    }

    private List<PlayerTitleCardResponse> buildPlayerCards(
            List<Player> players,
            List<Vote> allVotes,
            Map<UUID, List<String>> titlesByPlayer
    ) {
        Map<UUID, Long> totalVotesByPlayer = allVotes.stream()
                .collect(Collectors.groupingBy(v -> v.getVotedFor().getId(), Collectors.counting()));

        return players.stream()
                .map(p -> PlayerTitleCardResponse.builder()
                        .playerId(p.getId())
                        .playerName(p.getName())
                        .titlesWon(titlesByPlayer.getOrDefault(p.getId(), List.of()))
                        .totalVotesReceived(totalVotesByPlayer.getOrDefault(p.getId(), 0L))
                        .build())
                .collect(Collectors.toList());
    }

    private String getTopTitle(List<String> titles) {
        if (titles == null || titles.isEmpty()) return null;
        return titles.get(0); // first title won, could weight by vote count instead
    }
}
