// WireMock started in setup.groovy, loaded this project's stubs, and is stopped here.
try {
    def port = new File(basedir, 'wiremock.port').text.trim()
    assert new URL("http://localhost:${port}/ping").text == 'pong'
} finally {
    com.sebas3261.ex.it.WireMockSupport.stop(basedir)
}
