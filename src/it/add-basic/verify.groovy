import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

try {
    assertBytes(golden('add/fresh/expected.pom.xml'), new File(basedir, 'pom.xml'))
    assertContains(new File(basedir, 'build.log'), 'Maven validate passed')
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
