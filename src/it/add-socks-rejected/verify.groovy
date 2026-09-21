import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.*

def fq = { goal -> "com.sebas3261:ex-maven-plugin:${pluginVersion}:${goal}".toString() }

try {
    mvn(basedir, localRepositoryPath, ['-B', fq('add'), '-Dex.deps=lombok'], null, [ALL_PROXY: 'socks5://127.0.0.1:1080'])
            .assertFailure()
            .assertOutput('SOCKS proxy socks5://127.0.0.1:1080 (from ALL_PROXY) is not supported for dependency lookups.')
    assert requests(basedir, '/') == 0, 'no direct connection'
    assertBytes(golden('add/fresh/input.pom.xml'), new File(basedir, 'pom.xml'))
} finally {
    stop(basedir)
}
return true
