package com.monitor.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.monitor.entity.AlertLog;
import com.monitor.entity.AlertRule;
import com.monitor.mapper.AlertLogMapper;
import com.monitor.mapper.AlertRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertLogMapper alertLogMapper;

    // ---------- 告警规则 CRUD ----------

    @GetMapping("/alert-rules")
    public ResponseEntity<?> listRules() {
        return ResponseEntity.ok(Map.of("ok", true, "data", alertRuleMapper.selectList(null)));
    }

    @PostMapping("/alert-rules")
    public ResponseEntity<?> createRule(@RequestBody AlertRule rule) {
        alertRuleMapper.insert(rule);
        return ResponseEntity.ok(Map.of("ok", true, "data", rule));
    }

    @PutMapping("/alert-rules/{id}")
    public ResponseEntity<?> updateRule(@PathVariable Long id, @RequestBody AlertRule rule) {
        rule.setId(id);
        alertRuleMapper.updateById(rule);
        return ResponseEntity.ok(Map.of("ok", true, "data", rule));
    }

    @DeleteMapping("/alert-rules/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable Long id) {
        alertRuleMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ---------- 告警记录 ----------

    @GetMapping("/alert-logs")
    public ResponseEntity<?> listLogs(@RequestParam(required = false) Long projectId,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int offset = (Math.max(page, 1) - 1) * size;
        var query = Wrappers.<AlertLog>lambdaQuery()
                .orderByDesc(AlertLog::getTriggeredAt)
                .last("LIMIT " + size + " OFFSET " + offset);
        if (projectId != null) query.eq(AlertLog::getProjectId, projectId);
        List<AlertLog> list = alertLogMapper.selectList(query);

        var countQuery = Wrappers.<AlertLog>lambdaQuery();
        if (projectId != null) countQuery.eq(AlertLog::getProjectId, projectId);
        long total = alertLogMapper.selectCount(countQuery);
        return ResponseEntity.ok(Map.of("ok", true, "data", list, "total", total, "page", page, "pageSize", size));
    }
}
