import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

assertContains(new File(basedir, 'build.log'), 'The build could not read 1 project')
assert !new File(basedir, 'demo').exists()
return true
