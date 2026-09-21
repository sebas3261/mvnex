import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.*

def fq = { goal -> "com.sebas3261:ex-maven-plugin:${pluginVersion}:${goal}".toString() }

def work = new File(basedir, 'work')
work.mkdirs()
mvn(work, localRepositoryPath, ['-B', '-s', new File(basedir, 'mirror-settings.xml').absolutePath, fq('init'), '-Dex.name=app'], null, [:]).assertSuccess()
assertPlatformText(goldenText('wrapper/maven-wrapper.properties.template')
        .replace('${repoUrl}', 'https://repo.maven.apache.org/maven2')
        .replace('${mavenVersion}', mavenVersion()), new File(work, 'app/.mvn/wrapper/maven-wrapper.properties'))
return true
