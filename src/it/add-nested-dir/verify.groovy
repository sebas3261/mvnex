import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

try {
    assertContains(new File(basedir, 'pom.xml'), '<artifactId>lombok</artifactId>')
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
