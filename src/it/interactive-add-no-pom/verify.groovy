import static com.sebas3261.ex.it.ItSupport.*

// Run outside this repository so the upward search cannot find the project's own pom.xml.
def dir = File.createTempDir()
def settings = new File(dir, 'interactive-settings.xml')
settings.text = '<settings><interactiveMode>true</interactiveMode></settings>'
def run = mvn(dir, localRepositoryPath, ['-s', settings.absolutePath, "com.sebas3261:ex-maven-plugin:${pluginVersion}:add".toString()],
        'lombok\n', [CI: null, MAVEN_ARGS: null])
        .assertFailure().assertOutput('pom.xml not found. Run this command inside a Maven project.')
assert !run.output().contains('Dependencies'), 'no prompt before the POM search'
return true
