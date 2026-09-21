import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.*

def fq = { goal -> "com.sebas3261:ex-maven-plugin:${pluginVersion}:${goal}".toString() }

try {
    def port = new File(basedir, 'wiremock.port').text.trim()
    // Mirror id 'central' keeps artifacts cached from central valid in the local repository.
    def settings = new File(basedir, 'mirror-settings.xml')
    settings.text = """<settings><mirrors><mirror><id>central</id><mirrorOf>central</mirrorOf>
        <url>http://localhost:${port}/maven2</url></mirror></mirrors></settings>"""
    mvn(basedir, localRepositoryPath, ['-B', '-s', settings.absolutePath, fq('add'), '-Dex.deps=org.projectlombok:lombok'], null, [:])
            .assertSuccess()
    assertContains(new File(basedir, 'pom.xml'), '<version>1.18.48</version>')
    // No ex.internal.centralUrl here: the version listing can only reach WireMock through the mirror.
    assert requests(basedir, '/maven2/org/projectlombok/lombok/maven-metadata.xml') > 0
    assert requests(basedir, '/smo/') + requests(basedir, '/sonatype/') > 0
} finally {
    stop(basedir)
}
return true
