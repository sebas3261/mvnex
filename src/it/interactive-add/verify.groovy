import static com.sebas3261.ex.it.ItSupport.*

def fq = { goal -> "com.sebas3261:ex-maven-plugin:${pluginVersion}:${goal}".toString() }
// Force interactive mode as in interactive-init (CI=true and CI settings files turn it off).
def interactive = [CI: null, MAVEN_ARGS: null]
def settings = new File(basedir, 'interactive-settings.xml')
settings.text = '<settings><interactiveMode>true</interactiveMode></settings>'
def pom = new File(basedir, 'pom.xml')

try {
    // Dependencies, Version (Enter = latest), Scope (Enter = none)
    mvn(basedir, localRepositoryPath, ['-s', settings.absolutePath, fq('add')], 'org.projectlombok:lombok\n\n\n', interactive)
            .assertSuccess().assertOutput('Dependencies: ').assertOutput('Version (latest): ').assertOutput('1) none (default)')
    def text = pom.getText('UTF-8')
    assert text.contains('<artifactId>lombok</artifactId>') && text.contains('<version>1.18.48</version>')
    assert !text.contains('<scope>')

    // A search term with an answered version and scope.
    mvn(basedir, localRepositoryPath, ['-s', settings.absolutePath, fq('add')], 'junit-jupiter\n5.10.0\ntest\n', interactive)
            .assertSuccess().assertOutput('org.junit.jupiter:junit-jupiter:5.10.0 [test]')
    text = pom.getText('UTF-8')
    assert text.contains('<version>5.10.0</version>') && text.contains('<scope>test</scope>')
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
