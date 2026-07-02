package com.verdikt.verdikt_backend.controller;

import com.verdikt.verdikt_backend.dto.response.reportcard.ReportCardResponse;
import com.verdikt.verdikt_backend.service.ReportCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/report-card")
@RequiredArgsConstructor
public class ReportCardController {

    private final ReportCardService reportCardService;

    @GetMapping
    public ResponseEntity<ReportCardResponse> getReportCard(@PathVariable UUID roomId) {
        return ResponseEntity.ok(reportCardService.generateReportCard(roomId));
    }
}