import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

try {
    assertContains(new File(basedir, 'build.log'), 'The build could not read 1 project')
    assert requests(basedir, '/') == 0
    assert new File(basedir, 'pom.xml').text == new File(basedir, 'broken-pom.xml').text
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
