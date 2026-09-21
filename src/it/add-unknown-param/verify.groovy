import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

try {
    assertContains(new File(basedir, 'build.log'), 'Unknown parameter: ex.scop (did you mean ex.scope?)')
    assert requests(basedir, '/') == 0
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
