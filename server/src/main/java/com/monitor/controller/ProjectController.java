package com.monitor.controller;

import com.monitor.entity.Project;
import com.monitor.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/projects")
    public ResponseEntity<?> list(@RequestParam(required = false) Long serverId,
                                  @RequestParam(required = false) String type) {
        List<Project> list = serverId != null
                ? projectService.listByServer(serverId)
                : projectService.listAll();
        if (type != null && !type.isBlank()) {
            list = list.stream().filter(p -> type.equals(p.getType())).toList();
        }
        return ResponseEntity.ok(Map.of("ok", true, "data", list));
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Project project = projectService.getById(id);
        if (project == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", "项目不存在"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "data", project));
    }

    @PostMapping("/projects")
    public ResponseEntity<?> create(@RequestBody Project project) {
        return ResponseEntity.ok(Map.of("ok", true, "data", projectService.create(project)));
    }

    @PutMapping("/projects/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Project project) {
        return ResponseEntity.ok(Map.of("ok", true, "data", projectService.update(id, project)));
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
