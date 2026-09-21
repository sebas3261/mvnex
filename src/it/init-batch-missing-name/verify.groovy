import static com.sebas3261.ex.it.ItSupport.*

assertContains(new File(basedir, 'build.log'), 'Missing project name. Provide -Dex.name=<name>.')
// The invoker itself creates .mvn/ to anchor the project root; no project directory may appear.
assert basedir.listFiles().findAll { it.isDirectory() && it.name != '.mvn' }.isEmpty()
return true
