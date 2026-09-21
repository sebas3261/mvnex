import static com.sebas3261.ex.it.ItSupport.*

def project = new File(basedir, 'svc')
assertPlatformText(goldenText('init/svc/pom.xml'), new File(project, 'pom.xml'))
assert !new File(project, 'mvnw').exists() && !new File(project, 'mvnw.cmd').exists() && !new File(project, '.mvn').exists()
def log = new File(basedir, 'build.log')
assertContains(log, 'Wrapper   None')
assertContains(log, '  mvn package')
return true
