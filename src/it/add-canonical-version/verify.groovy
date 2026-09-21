import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

try {
    // Solr says 1.18.38 (stale index) but the listing + deps.dev choose the current release.
    assertContains(new File(basedir, 'pom.xml'), '<version>1.18.48</version>')
    assertContains(new File(basedir, 'build.log'), 'org.projectlombok:lombok:1.18.48')
    // Confirmation really used deps.dev's version list (not the fallback when it is unavailable).
    def port = new File(basedir, 'wiremock.port').text.trim()
    def depsDev = new groovy.json.JsonSlurper().parse(new URL("http://localhost:${port}/__admin/requests"))
            .requests.findAll { it.request.url.startsWith('/depsdev/') }
    assert !depsDev.isEmpty() && depsDev.every { it.responseDefinition.status == 200 }
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
