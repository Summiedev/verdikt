package com.verdikt.verdikt_backend.service;

import com.verdikt.verdikt_backend.dto.request.CastVoteRequest;
import com.verdikt.verdikt_backend.model.Player;
import com.verdikt.verdikt_backend.model.Question;
import com.verdikt.verdikt_backend.model.Room;
import com.verdikt.verdikt_backend.model.RoomQuestion;
import com.verdikt.verdikt_backend.model.Vote;
import com.verdikt.verdikt_backend.model.enums.RoomStatus;
import com.verdikt.verdikt_backend.model.enums.VoteMode;
import com.verdikt.verdikt_backend.repository.PlayerRepository;
import com.verdikt.verdikt_backend.repository.QuestionRepository;
import com.verdikt.verdikt_backend.repository.RoomQuestionRepository;
import com.verdikt.verdikt_backend.repository.RoomRepository;
import com.verdikt.verdikt_backend.repository.VoteRepository;
import com.verdikt.verdikt_backend.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    @Mock
    private VoteRepository voteRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private RoomQuestionRepository roomQuestionRepository;
    @Mock
    private WebSocketEventPublisher eventPublisher;

    @InjectMocks
    private VoteService voteService;

    @Test
    void castVotesPublishesAuthoritativeVoteState() {
        UUID roomId = UUID.randomUUID();
        UUID voterToken = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID voterId = UUID.randomUUID();
        UUID targetPlayerId = UUID.randomUUID();

        Room room = Room.builder()
                .id(roomId)
                .code("ABC123")
                .name("Test room")
                .status(RoomStatus.IN_PROGRESS)
                .voteMode(VoteMode.PUBLIC)
                .build();

        Player voter = Player.builder().id(voterId).room(room).name("Alice").token(voterToken).build();
        Player targetPlayer = Player.builder().id(targetPlayerId).room(room).name("Bob").build();
        Question question = Question.builder().id(questionId).text("Who should win?").category("Fun").spiceLevel(com.verdikt.verdikt_backend.model.enums.SpiceLevel.MILD).build();
        RoomQuestion activeQuestion = RoomQuestion.builder().room(room).question(question).isActive(true).build();

        CastVoteRequest request = new CastVoteRequest();
        request.setQuestionId(questionId);
        request.setVotedForPlayerIds(List.of(targetPlayerId));

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(playerRepository.findByToken(voterToken)).thenReturn(Optional.of(voter));
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(roomQuestionRepository.findByRoomIdAndIsActiveTrue(roomId)).thenReturn(Optional.of(activeQuestion));
        when(playerRepository.findAllById(any())).thenReturn(List.of(targetPlayer));
        when(voteRepository.findVotedForIdsByRoomAndQuestionAndVoter(roomId, questionId, voterId)).thenReturn(List.of());
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vote emittedVote = Vote.builder()
                .room(room)
                .question(question)
                .voter(voter)
                .votedFor(targetPlayer)
                .build();
        when(voteRepository.findAllByRoomIdAndQuestionId(roomId, questionId)).thenReturn(List.of(emittedVote));

        List<Map<String, Object>> authoritativeState = voteService.castVotes(roomId, voterToken, request);

        assertNotNull(authoritativeState);
        verify(eventPublisher).publishVoteState(eq(roomId), eq(questionId), any(List.class), eq(VoteMode.PUBLIC));
    }

    @Test
    void castVotesSupportsMultipleSelectionsInOneRequest() {
        UUID roomId = UUID.randomUUID();
        UUID voterToken = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID voterId = UUID.randomUUID();
        UUID firstTargetId = UUID.randomUUID();
        UUID secondTargetId = UUID.randomUUID();

        Room room = Room.builder()
                .id(roomId)
                .code("ABC123")
                .name("Test room")
                .status(RoomStatus.IN_PROGRESS)
                .voteMode(VoteMode.PUBLIC)
                .build();

        Player voter = Player.builder().id(voterId).room(room).name("Alice").token(voterToken).build();
        Player firstTarget = Player.builder().id(firstTargetId).room(room).name("Bob").build();
        Player secondTarget = Player.builder().id(secondTargetId).room(room).name("Cara").build();
        Question question = Question.builder().id(questionId).text("Who should win?").category("Fun").spiceLevel(com.verdikt.verdikt_backend.model.enums.SpiceLevel.MILD).build();
        RoomQuestion activeQuestion = RoomQuestion.builder().room(room).question(question).isActive(true).build();

        CastVoteRequest request = new CastVoteRequest();
        request.setQuestionId(questionId);
        request.setVotedForPlayerIds(List.of(firstTargetId, secondTargetId));

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(playerRepository.findByToken(voterToken)).thenReturn(Optional.of(voter));
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(roomQuestionRepository.findByRoomIdAndIsActiveTrue(roomId)).thenReturn(Optional.of(activeQuestion));
        when(playerRepository.findAllById(any())).thenReturn(List.of(firstTarget, secondTarget));
        when(voteRepository.findVotedForIdsByRoomAndQuestionAndVoter(roomId, questionId, voterId)).thenReturn(List.of());
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(voteRepository.findAllByRoomIdAndQuestionId(roomId, questionId)).thenReturn(List.of(
                Vote.builder().room(room).question(question).voter(voter).votedFor(firstTarget).build(),
                Vote.builder().room(room).question(question).voter(voter).votedFor(secondTarget).build()
        ));

        List<Map<String, Object>> state = voteService.castVotes(roomId, voterToken, request);

        assertNotNull(state);
        verify(eventPublisher).publishVoteState(eq(roomId), eq(questionId), any(List.class), eq(VoteMode.PUBLIC));
    }
}
