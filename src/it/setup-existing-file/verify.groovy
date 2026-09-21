import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.*

def fq = { goal -> "com.sebas3261:ex-maven-plugin:${pluginVersion}:${goal}".toString() }

def settings = new File(basedir, 'settings.xml')
mvn(basedir, localRepositoryPath, ['-s', settings.absolutePath, fq('setup')], null, [:]).assertSuccess()
assertBytes(new File(basedir, 'expected-settings.xml').bytes, settings)
mvn(basedir, localRepositoryPath, ['-s', settings.absolutePath, 'ex:help'], null, [:])
        .assertSuccess().assertOutput('This plugin has 5 goals')
// Maven itself rejects a -s file that does not exist, so nothing is created there.
def missing = new File(basedir, 'missing/settings.xml')
mvn(basedir, localRepositoryPath, ['-s', missing.absolutePath, fq('setup')], null, [:])
        .assertFailure().assertOutput('The specified user settings file does not exist')
assert !missing.parentFile.exists()
return true
