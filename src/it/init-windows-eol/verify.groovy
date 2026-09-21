import static com.sebas3261.ex.it.ItSupport.*
import static com.sebas3261.ex.it.WireMockSupport.requests

def project = new File(basedir, 'my-app')
['pom.xml', 'src/main/java/com/example/myapp/Main.java', '.mvn/wrapper/maven-wrapper.properties'].each {
    def text = new File(project, it).getText('UTF-8')
    assert text.contains('\r\n') && !text.replace('\r\n', '').contains('\n'), "$it must use CRLF"
}
assertBytes(golden('wrapper/mvnw'), new File(project, 'mvnw'))
assertBytes(golden('wrapper/mvnw.cmd'), new File(project, 'mvnw.cmd'))
return true
