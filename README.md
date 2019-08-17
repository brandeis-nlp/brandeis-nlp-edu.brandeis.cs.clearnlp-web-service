# Brandeis LAPPS Grid wrapper for ReVerb

This project is to build ReVerb wrappers based on LAPPS Grid I/O specification, namely using LAPPS Interchange Format and LAPPS Vocabulary.
Two wrappers are available: a simple commandline interface and as a lapps webservice.
By default (`mvn package`), the [webservice module](reverb-webservice) is built into a war artifact that can be deployed as a tomcat servelet and later callable through [service-manager](https://github.com/openlangrid/langrid) SOAP API.
To build a CLI application, use "cli" profile (`mvn pacakge -Pcli`). See [CLI module](reverb-cli) for instructions how to use the CLI application. 
This version uses ReVerb ${reverb.version} internally.

## LICENSE

This project is developed under the [apache 2 license](LICENSE) and source code for the wrapper is hereby available. However the original ReVerb was release under [ReVerb LICENSE](http://reverb.cs.washington.edu/LICENSE.txt) that forbids commercial use. Check out the original license for more details. 

## parent POM and base artifact

The parent POM used in the project ([`edu.brandeis.lapps.parent-pom`](https://github.com/brandeis-llc/lapps-parent-pom)) and the base java artifact ([`edu.brandeis.lapps.app-scaffolding`](https://github.com/brandeis-llc/lapps-app-scaffolding)) are not available on the maven central. Their source code are available at [`brandeis-llc`](https://github.com/brandeis-llc) github organization, and the maven artifacts are available via [Brandeis LLC nexus repository](http://morbius.cs-i.brandeis.edu:8081/).

## releasing with maven-release-plugin

Don't forget to use `release` profile to include all submodules during the release preparation as well as performing release.

```
mvn release:preprare release:perform -Prelease
```

