import static com.sebas3261.ex.it.ItSupport.*

def settings = new File(basedir, 'settings.xml')
def run = mvn(basedir, localRepositoryPath, ['-s', settings.absolutePath,
        "com.sebas3261:ex-maven-plugin:${pluginVersion}:setup".toString()], null, [:]).assertSuccess()
assert run.output().count('Added plugin group com.sebas3261') == 1, 'processed once, not per module'
assert basedir.listFiles().count { it.name.endsWith('.bak') } == 1
return true
