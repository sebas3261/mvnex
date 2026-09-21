import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

try {
    def log = new File(basedir, 'build.log')
    assertContains(log, 'Parameter ex.groupId is not used by ex:add and will be ignored.')
    assertContains(log, 'Dependencies added')
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
