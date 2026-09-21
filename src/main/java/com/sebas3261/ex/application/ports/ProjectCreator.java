package com.sebas3261.ex.application.ports;

import com.sebas3261.ex.domain.project.ProjectConfig;

/** Writes a new project to disk. */
public interface ProjectCreator {

    void create(ProjectConfig config, boolean skipWrapper);
}
