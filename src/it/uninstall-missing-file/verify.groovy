import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.*

def fq = { goal -> "com.sebas3261:ex-maven-plugin:${pluginVersion}:${goal}".toString() }

// MAVEN_OPTS is word-split by the mvn script, so the home must not contain spaces.
def home = File.createTempDir()
mvn(basedir, localRepositoryPath, [fq('uninstall')], null, [MAVEN_OPTS: "-Duser.home=${home.absolutePath}".toString()])
        .assertSuccess().assertOutput('Nothing to uninstall.')
assert !new File(home, '.m2').exists()
return true
