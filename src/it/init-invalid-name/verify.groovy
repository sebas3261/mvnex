import static com.sebas3261.ex.it.ItSupport.*

assertContains(new File(basedir, 'build.log'), 'Invalid project name. Use lowercase letters, numbers and hyphens.')
assert !new File(basedir, 'Bad').exists()
return true
