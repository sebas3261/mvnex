// Stored under another name because the invoker parses every IT's pom.xml while collecting projects.
new File(basedir, 'pom.xml').bytes = new File(basedir, 'broken-pom.xml').bytes
com.sebas3261.ex.it.WireMockSupport.startLookups(basedir)
return true
