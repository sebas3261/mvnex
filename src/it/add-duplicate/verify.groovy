import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

try {
    assertBytes(golden('add/already-present/input.pom.xml'), new File(basedir, 'pom.xml'))
    assertContains(new File(basedir, 'build.log'), 'org.projectlombok:lombok:1.18.32 already exists or was duplicated in this command')
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
