import static com.sebas3261.ex.it.ItSupport.*

// One project, created at the reactor root, not once per module.
assert new File(basedir, 'tool/pom.xml').isFile()
['a', 'b', 'c'].each { assert !new File(basedir, "$it/tool").exists() }
assert new File(basedir, 'build.log').text.count('Project created successfully') == 1
return true
