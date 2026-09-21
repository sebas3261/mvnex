import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

// Run outside this repository so the upward search cannot find the project's own pom.xml.
def dir = File.createTempDir()
mvn(dir, localRepositoryPath, ['-B', "com.sebas3261:ex-maven-plugin:${pluginVersion}:add".toString(), '-Dex.deps=lombok'], null, [:])
        .assertFailure().assertOutput('pom.xml not found. Run this command inside a Maven project.')
return true
