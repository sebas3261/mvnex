import static com.sebas3261.ex.it.ItSupport.*

def project = new File(basedir, 'my-app')
// Batch defaults (groupId com.example, Java 21) reproduce the recorded my-app golden.
assertPlatformText(goldenText('init/my-app/pom.xml'), new File(project, 'pom.xml'))
assertPlatformText(goldenText('init/my-app/Main.java'), new File(project, 'src/main/java/com/example/myapp/Main.java'))
assert new File(project, 'src/test/java/com/example/myapp').isDirectory()
assertPlatformText(goldenText('wrapper/maven-wrapper.properties.template')
        .replace('${repoUrl}', 'https://repo.maven.apache.org/maven2')
        .replace('${mavenVersion}', mavenVersion()), new File(project, '.mvn/wrapper/maven-wrapper.properties'))
assertBytes(golden('wrapper/mvnw'), new File(project, 'mvnw'))
assertBytes(golden('wrapper/mvnw.cmd'), new File(project, 'mvnw.cmd'))
assertContains(new File(basedir, 'build.log'), 'Project created successfully')

if (!System.getProperty('os.name').toLowerCase().contains('win')) {
    assert new File(project, 'mvnw').canExecute()
    // The generated wrapper downloads the pinned Maven into a throwaway MAVEN_USER_HOME and runs it.
    def run = run(project, ['./mvnw', '-v'], null, [MAVEN_USER_HOME: new File(basedir, 'wrapper-home').absolutePath])
    run.assertSuccess().assertOutput('Apache Maven ' + mavenVersion())
}
return true
