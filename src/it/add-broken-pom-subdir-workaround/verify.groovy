import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

try {
    // From a subdirectory without a pom.xml Maven runs standalone; the plugin still finds and edits the parent POM.
    assertContains(new File(basedir, 'pom.xml'), '<artifactId>lombok</artifactId>')
    assertContains(new File(basedir, 'build.log'), 'Maven validate failed. Check the project output with mvn validate.')
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
