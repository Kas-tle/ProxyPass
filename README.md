# ProxyPass

### Introduction

Proxy pass allows developers to MITM a vanilla client and server without modifying them. This allows for easy testing 
of the Bedrock Edition protocol and observing vanilla network behavior.

__ProxyPass requires  Java 8 u162 or later to function correctly due to the encryption used during login__<br>
`online-mode` __needs to be set to__ `false` __in__ `server.properties` __so that ProxyPass can communicate with your Bedrock Dedicated Server.__

### Building & Running
To produce a jar file, run `./gradlew shadowJar` in the project root directory. This will produce a jar file in the `build/libs` directory.

If you wish to run the project from source, run `./gradlew run` in the project root directory.

### Links

__[Jenkins](https://ci.opencollab.dev/job/NukkitX/job/ProxyPass/job/master/)__

__[Protocol library](https://github.com/CloudburstMC/Protocol) used in this project__
