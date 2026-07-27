package com.verdikt.verdikt_backend.config;

public final class CacheConstants {

    private CacheConstants() {}

    public static final String ROOM_BY_CODE = "roomByCode";
    public static final String PLAYER_BY_TOKEN = "playerByToken";
    public static final String VOTE_STATE = "voteState";
    public static final String ACTIVE_QUESTION = "activeQuestion";
    public static final String REPORT_CARD = "reportCard";
    public static final String NON_CUSTOM_QUESTIONS = "nonCustomQuestions";

    public static final long TTL_ROOM_BY_CODE_MS = 10 * 60 * 1000;
    public static final long TTL_PLAYER_BY_TOKEN_MS = 30 * 60 * 1000;
    public static final long TTL_VOTE_STATE_MS = 1 * 1000;
    public static final long TTL_ACTIVE_QUESTION_MS = 30 * 1000;
    public static final long TTL_REPORT_CARD_MS = 24 * 60 * 60 * 1000;
    public static final long TTL_NON_CUSTOM_QUESTIONS_MS = 60 * 60 * 1000;
}
