import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.*

def fq = { goal -> "com.sebas3261:ex-maven-plugin:${pluginVersion}:${goal}".toString() }

def settings = new File(basedir, 'settings.xml')
def original = settings.bytes
mvn(basedir, localRepositoryPath, ['-s', settings.absolutePath, fq('setup')], null, [:]).assertSuccess()
mvn(basedir, localRepositoryPath, ['-s', settings.absolutePath, 'ex:uninstall'], null, [:])
        .assertSuccess().assertOutput('Removed plugin group com.sebas3261')
assert settings.bytes == original, 'setup followed by uninstall restores the file byte for byte'
mvn(basedir, localRepositoryPath, ['-s', settings.absolutePath, fq('help')], null, [:]).assertSuccess()
mvn(basedir, localRepositoryPath, ['-s', settings.absolutePath, 'ex:help'], null, [:])
        .assertFailure().assertOutput("No plugin found for prefix 'ex'")
return true
