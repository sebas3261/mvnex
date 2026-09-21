import static com.sebas3261.ex.it.ItSupport.*

def fq = { goal -> "com.sebas3261:ex-maven-plugin:${pluginVersion}:${goal}".toString() }
// A mirrorOf="*" mirror intercepts every repository, so it points at a repository that can actually
// serve the plugin's dependencies: the outer build's local repository.
def mirrorUrl = outerRepositoryUrl()
def settings = new File(basedir, 'mirror-settings.xml')
settings.text = """<settings><mirrors><mirror><id>central</id><mirrorOf>*</mirrorOf>
    <url>${mirrorUrl}</url></mirror></mirrors></settings>"""
def work = new File(basedir, 'work')
work.mkdirs()
mvn(work, localRepositoryPath, ['-B', '-s', settings.absolutePath, fq('init'), '-Dex.name=app'], null, [:]).assertSuccess()
assertPlatformText(goldenText('wrapper/maven-wrapper.properties.template')
        .replace('${repoUrl}', mirrorUrl)
        .replace('${mavenVersion}', mavenVersion()), new File(work, 'app/.mvn/wrapper/maven-wrapper.properties'))
return true
