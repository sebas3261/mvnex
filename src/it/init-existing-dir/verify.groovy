import static com.sebas3261.ex.it.ItSupport.*

assertContains(new File(basedir, 'build.log'), 'Project directory already exists: my-app')
assert new File(basedir, 'my-app').list() as List == ['keep.txt']
return true
