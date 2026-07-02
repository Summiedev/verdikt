package com.verdikt.verdikt_backend.repository;

import com.verdikt.verdikt_backend.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

@Repository
public interface VoteRepository extends JpaRepository<Vote, UUID> {

    List<Vote> findAllByRoomIdAndQuestionId(UUID roomId, UUID questionId);

    List<Vote> findAllByRoomId(UUID roomId);

    boolean existsByRoomIdAndQuestionIdAndVoterIdAndVotedForId(
        UUID roomId, UUID questionId, UUID voterId, UUID votedForId
    );

    // replaces N individual existsBy calls inside the vote loop — one query instead
    @Query("""
        SELECT v.votedFor.id
        FROM Vote v
        WHERE v.room.id = :roomId
          AND v.question.id = :questionId
          AND v.voter.id = :voterId
    """)
    List<UUID> findVotedForIdsByRoomAndQuestionAndVoter(
        @Param("roomId") UUID roomId,
        @Param("questionId") UUID questionId,
        @Param("voterId") UUID voterId
    );

    @Query("""
        SELECT v.votedFor.id, COUNT(v)
        FROM Vote v
        WHERE v.room.id = :roomId
        GROUP BY v.votedFor.id
        ORDER BY COUNT(v) DESC
    """)
    List<Object[]> countVotesByPlayerInRoom(@Param("roomId") UUID roomId);

    @Query("""
        SELECT v.question.id, v.votedFor.id, COUNT(v)
        FROM Vote v
        WHERE v.room.id = :roomId
        GROUP BY v.question.id, v.votedFor.id
        ORDER BY v.question.id, COUNT(v) DESC
    """)
    List<Object[]> countVotesByQuestionAndPlayer(@Param("roomId") UUID roomId);

    @Modifying
    @Query("DELETE FROM Vote v WHERE v.room.id = :roomId AND v.voter.id = :voterId AND v.question.id = :questionId")
    void deleteByRoomIdAndVoterIdAndQuestionId(
        @Param("roomId") UUID roomId,
        @Param("voterId") UUID voterId,
        @Param("questionId") UUID questionId
    );

    @Modifying
    @Query("DELETE FROM Vote v WHERE v.room.id = :roomId AND v.voter.id = :voterId AND v.question.id = :questionId AND v.votedFor.id IN :votedForIds")
    void deleteByRoomIdAndVoterIdAndQuestionIdAndVotedForIdIn(
        @Param("roomId") UUID roomId,
        @Param("voterId") UUID voterId,
        @Param("questionId") UUID questionId,
        @Param("votedForIds") List<UUID> votedForIds
    );
}