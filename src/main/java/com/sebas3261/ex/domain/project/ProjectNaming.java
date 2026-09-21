package com.sebas3261.ex.domain.project;

/** Derives Java-safe names from project names. */
public final class ProjectNaming {

    private ProjectNaming() {
    }

    /** Returns the package segment for a project name: the name with every hyphen removed. */
    public static String toPackageName(String projectName) {
        return projectName.replace("-", "");
    }
}
