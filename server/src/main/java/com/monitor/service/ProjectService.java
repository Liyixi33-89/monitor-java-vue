package com.monitor.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.monitor.entity.Project;
import com.monitor.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;

    public List<Project> listAll() {
        return projectMapper.selectList(null);
    }

    public List<Project> listByServer(Long serverId) {
        return projectMapper.selectList(Wrappers.<Project>lambdaQuery().eq(Project::getServerId, serverId));
    }

    public Project getById(Long id) {
        return projectMapper.selectById(id);
    }

    public Project create(Project project) {
        projectMapper.insert(project);
        return project;
    }

    public Project update(Long id, Project project) {
        project.setId(id);
        projectMapper.updateById(project);
        return project;
    }

    public void delete(Long id) {
        projectMapper.deleteById(id);
    }
}
