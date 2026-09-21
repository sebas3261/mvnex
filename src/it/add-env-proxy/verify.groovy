import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.*

def fq = { goal -> "com.sebas3261:ex-maven-plugin:${pluginVersion}:${goal}".toString() }

def proxy = new com.sebas3261.ex.it.ForwardProxy('user', 'p@ss')
try {
    def env = [ALL_PROXY: "http://user:p%40ss@127.0.0.1:${proxy.port()}".toString()]
    mvn(basedir, localRepositoryPath, ['-B', fq('add'), '-Dex.deps=org.projectlombok:lombok'], null, env).assertSuccess()
    assertContains(new File(basedir, 'pom.xml'), '<version>1.18.48</version>')
    assert proxy.forwarded() > 0, 'lookups went through the proxy'
    assert requests(basedir, '/') == proxy.forwarded(), 'every lookup reached WireMock through the proxy'
} finally {
    proxy.close()
    stop(basedir)
}
return true
