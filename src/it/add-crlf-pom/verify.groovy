import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

try {
    assertBytes(golden('add/crlf/expected.pom.xml'), new File(basedir, 'pom.xml'))
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
