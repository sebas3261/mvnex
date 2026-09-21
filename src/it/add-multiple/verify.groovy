import static com.sebas3261.ex.it.ItSupport.*

try {
    // Maven splits the comma-separated property into the List parameter, in order.
    def pom = new File(basedir, 'pom.xml').text
    assert pom.indexOf('<artifactId>lombok</artifactId>') < pom.indexOf('<artifactId>postgresql</artifactId>')
    assert pom.count('<artifactId>lombok</artifactId>') == 1
    def log = new File(basedir, 'build.log')
    assertContains(log, 'org.projectlombok:lombok:1.18.32 already exists or was duplicated in this command')
    // The goal suggests setup when invoked by its full name without the plugin group.
    assertContains(log, 'Tip: run mvn com.sebas3261:ex-maven-plugin:')
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
return true
