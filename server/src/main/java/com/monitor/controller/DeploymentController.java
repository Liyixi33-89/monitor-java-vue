package com.monitor.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitor.entity.Deployment;
import com.monitor.entity.Project;
import com.monitor.mapper.DeploymentMapper;
import com.monitor.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发布记录 —— 手动登记每次发布，追溯发布与故障的时间线关系
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentMapper deploymentMapper;
    private final ProjectService projectService;

    /** 分页查询，可按项目筛选 */
    @GetMapping("/deployments")
    public ResponseEntity<?> list(@RequestParam(required = false) Long projectId,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        var query = Wrappers.<Deployment>lambdaQuery().orderByDesc(Deployment::getDeployedAt);
        if (projectId != null) query.eq(Deployment::getProjectId, projectId);
        Page<Deployment> result = deploymentMapper.selectPage(new Page<>(Math.max(page, 1), size), query);

        // 附带项目名称
        Map<Long, String> projectNames = new ConcurrentHashMap<>();
        projectService.listAll().forEach(p -> projectNames.put(p.getId(), p.getName()));
        List<Map<String, Object>> enriched = result.getRecords().stream().map(d -> {
            Map<String, Object> m = new ConcurrentHashMap<>();
            m.put("id", d.getId());
            m.put("projectId", d.getProjectId());
            m.put("projectName", projectNames.getOrDefault(d.getProjectId(), "未知项目"));
            // ConcurrentHashMap 不允许 null 值，可空字段统一转空串
            m.put("version", d.getVersion() == null ? "" : d.getVersion());
            m.put("operator", d.getOperator() == null ? "" : d.getOperator());
            m.put("remark", d.getRemark() == null ? "" : d.getRemark());
            m.put("result", d.getResult() == null ? "" : d.getResult());
            m.put("deployedAt", d.getDeployedAt() == null ? "" : d.getDeployedAt());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("ok", true, "data", enriched,
                "total", result.getTotal(), "page", page, "pageSize", size));
    }

    /** 新增发布记录 */
    @PostMapping("/deployments")
    public ResponseEntity<?> create(@RequestBody Deployment d) {
        if (d.getProjectId() == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "projectId 必填"));
        }
        if (d.getDeployedAt() == null) d.setDeployedAt(LocalDateTime.now());
        if (d.getResult() == null || d.getResult().isBlank()) d.setResult("success");
        d.setCreatedAt(LocalDateTime.now());
        deploymentMapper.insert(d);
        return ResponseEntity.ok(Map.of("ok", true, "data", d));
    }

    /** 更新发布记录 */
    @PutMapping("/deployments/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Deployment d) {
        d.setId(id);
        deploymentMapper.updateById(d);
        return ResponseEntity.ok(Map.of("ok", true, "data", d));
    }

    /** 删除发布记录 */
    @DeleteMapping("/deployments/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        deploymentMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
