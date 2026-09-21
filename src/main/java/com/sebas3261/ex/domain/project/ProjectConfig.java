package com.sebas3261.ex.domain.project;

/**
 * The project {@code ex:init} creates.
 *
 * @param name        project name, also used as the artifactId and directory name
 * @param groupId     Maven groupId
 * @param packageName Java package of the generated {@code Main} class
 * @param javaVersion value for {@code maven.compiler.release}
 */
public record ProjectConfig(String name, String groupId, String packageName, String javaVersion) {
}
