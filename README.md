# ProxyPass

### Introduction

Proxy pass allows developers to MITM a vanilla client and server without modifying them. This allows for easy testing 
of the Bedrock Edition protocol and observing vanilla network behavior. This fork of ProxyPass is designed to work in online mode. The original work for this was largely done by [@valaphee](https://github.com/valaphee).

### Usage

First, download the latest [ProxyPass.jar](https://github.com/Kas-tle/ProxyPass/releases/latest/download/ProxyPass.jar) from the [releases](https://github.com/Kas-tle/ProxyPass/releases) page. Then, run the jar file with `java -jar ProxyPass.jar`.

Run once to create a `config.yml` file, or create one like so in the same directory as the jar file:

```yaml
## Address proxy will bind to.
proxy:
  host: 127.0.0.1
  port: 19122
## Destination server which the client will connect to.
## You are only able to join offline mode servers
destination:
  host: 127.0.0.1
  port: 19132
## Maximum of clients which can connect to ProxyPass. If this should be disabled, set it to 0.
max-clients: 0
## Encode and decode packets to test protocol library for bugs
packet-testing: false
## Log packets for each session
log-packets: true
## Where to log packet data
## Valid options: console, file or both
log-to: file

## Packets to ignore to make your log more refined. These default packet are generally spammed
ignored-packets:
  - "NetworkStackLatencyPacket"
  - "LevelChunkPacket"
  - "MovePlayerPacket"
  - "PlayerAuthInputPacket"
  - "NetworkChunkPublisherUpdatePacket"
  - "ClientCacheBlobStatusPacket"
  - "ClientCacheMissResponsePacket"
```

On attempting to connect, your default web browser will direct you to a Microsoft login page requesting a token. This token has been copied to your clipboard, which you can paste into the prompt to continue. You will then be prompted to login with your Microsoft account, which of course must be associated with a valid Xbox Live account with a Minecraft character. Note that some servers may disconnect you if this process takes to long. If this is the case, simply leave ProxyPass running and login again after completing this initial login process. You will not be prompted to login again as long as ProxyPass is still running.

### Building & Running

To produce a jar file, run `./gradlew build` in the project root directory. This will produce a jar file in the `build/libs` directory.

If you wish to run the project from source, run `./gradlew run` in the project root directory.

### Links

__[Releases](https://ci.opencollab.dev/job/NukkitX/job/ProxyPass/job/master/)__

__[Protocol library](https://github.com/CloudburstMC/Protocol) used in this project__

__[Original ProxyPass](https://github.com/CloudburstMC/ProxyPass)

__[Original Online Fork](https://github.com/valaphee/CloudburstMC-ProxyPass)
