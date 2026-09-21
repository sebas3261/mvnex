import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

try {
    def log = new File(basedir, 'build.log')
    assertContains(log, 'Multiple dependency matches found: lombok')
    assertContains(log, '  org.projectlombok:lombok:1.18.48')
    assertContains(log, 'Specify an exact groupId:artifactId, for example -Dex.deps=org.projectlombok:lombok')
    assertBytes(golden('add/fresh/input.pom.xml'), new File(basedir, 'pom.xml'))
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
