import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

try {
    assertContains(new File(basedir, 'build.log'), 'Dependency lookup requires network access; it is not available in offline mode (-o).')
    assert requests(basedir, '/') == 0
    assertBytes(golden('add/fresh/input.pom.xml'), new File(basedir, 'pom.xml'))
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
