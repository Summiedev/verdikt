package com.verdikt.verdikt_backend.dto.response.reportcard;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReportCardResponse {
    private ReportCardOverviewResponse overview;       // slide 1
    private List<PollCardResponse> pollCards;           // slides 2..N, one per question
    private List<PlayerTitleCardResponse> playerCards;  // final slides, one per player
}