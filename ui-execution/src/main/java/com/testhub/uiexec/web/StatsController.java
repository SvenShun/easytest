package com.testhub.uiexec.web;

import com.testhub.uiexec.service.StatsService;
import com.testhub.uiexec.web.dto.StatsDtos.StatsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard statistics. */
@RestController
@RequestMapping("/api/v1/ui-execution/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public StatsView stats() {
        return statsService.stats();
    }
}
