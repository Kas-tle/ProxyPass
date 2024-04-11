# ProxyPass

### Introduction

ProxyPass allows developers to MITM a vanilla client and server without modifying them. This allows for easy testing of the Bedrock Edition protocol and observing vanilla network behavior. This fork of ProxyPass is designed to work in online mode. It also includes submodules of the Network and Protocol libraries, which have been modified to work with more servers than the original libraries. This setup is also more convenient for developers trying to debug these libraries.

### Usage

If trying to use ProxyPass on the same machine as the client (Windows), you will need to disable loopback restrictions. This can be done by running the following command(s) in an *administrator* PowerShell prompt:

```ps1
# Release Version
CheckNetIsolation LoopbackExempt -a -n="Microsoft.MinecraftUWP_8wekyb3d8bbwe"
# Preview Version
CheckNetIsolation LoopbackExempt -a -n="Microsoft.MinecraftWindowsBeta_8wekyb3d8bbwe"
```

Next, download the latest [ProxyPass.jar](https://github.com/Kas-tle/ProxyPass/releases/latest/download/ProxyPass.jar) from the [releases](https://github.com/Kas-tle/ProxyPass/releases) page. Then, run the jar file with `java -jar ProxyPass.jar`.

Run once to create a `config.yml` file, or create one like so in the same directory as the jar file:

```yaml
## Address proxy will bind to.
proxy:
  host: 127.0.0.1
  port: 19122
## Destination server which the client will connect to.
destination:
  host: 127.0.0.1
  port: 19132
## Run the proxy in online mode. This will require login with a Microsoft account on start.
online-mode: true
## Save credentials when in online mode for future logins.
save-auth-details: true
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

On attempting to connect in online mode, your default web browser will direct you to a Microsoft login page requesting a token. This token has been copied to your clipboard, which you can paste into the prompt to continue. You will then be prompted to login with your Microsoft account, which of course must be associated with a valid Xbox Live account with a Minecraft character. Note that some servers may disconnect you if this process takes to long. If this is the case, simply leave ProxyPass running and login again after completing this initial login process. You will not be prompted to login again as long as ProxyPass is still running. By default, your credentials for future logins are saved. This can be disabled by setting `save-auth-details` to `false` in the `config.yml` file.

### Building & Running

This project uses git submodules. To clone:

```sh
git clone https://github.com/Kas-tle/ProxyPass.git --recursive
```

If you have already cloned the repository, you can initialize the submodules with:

```sh
git submodule update --init --recursive
```

To produce a jar file, run `./gradlew build` in the project root directory. This will produce a jar file in the `build/libs` directory.

If you wish to run the project from source, run `./gradlew run` in the project root directory.

### Links
- [Releases](https://github.com/Kas-tle/ProxyPass/releases)
- [Auth Library](https://github.com/RaphiMC/MinecraftAuth) used in this project by [RaphiMC](https://github.com/RaphiMC)
- [Original Protocol Library](https://github.com/CloudburstMC/Protocol) used in this project by [CloudburstMC](https://github.com/CloudburstMC)
- [Original Network Library](https://github.com/CloudburstMC/Network) used in this project by [CloudburstMC](https://github.com/CloudburstMC)
- [Original ProxyPass](https://github.com/CloudburstMC/ProxyPass) by [CloudburstMC](https://github.com/CloudburstMC)
