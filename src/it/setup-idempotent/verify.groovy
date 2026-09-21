import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.*

def fq = { goal -> "com.sebas3261:ex-maven-plugin:${pluginVersion}:${goal}".toString() }

def settings = new File(basedir, 'settings.xml')
mvn(basedir, localRepositoryPath, ['-s', settings.absolutePath, fq('setup')], null, [:]).assertSuccess()
def afterFirst = settings.bytes
mvn(basedir, localRepositoryPath, ['-s', settings.absolutePath, fq('setup')], null, [:])
        .assertSuccess().assertOutput('is already configured in')
assert settings.bytes == afterFirst
assert basedir.listFiles().count { it.name.endsWith('.bak') } == 1
return true
