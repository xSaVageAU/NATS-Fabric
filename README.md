# NATS-Fabric

A simple Fabric library that provides a shared [NATS](https://nats.io) connection for Minecraft servers.

## What it does

Instead of every mod opening its own NATS connection, they can share one through this library. This keeps resource usage low and simplifies your configuration files.

## Setup

1. Start the server once to generate `config/nats-fabric.yml`.
2. Fill in your NATS server details:

```yaml
serverName: server-1
natsUrl: nats://localhost:4222
natsAuthToken: ""
natsUsername: ""
natsPassword: ""
```

`serverName` should be unique for each server in your cluster.

## Requirements

- Minecraft 26.1+ (Fabric)
- Java 25+
- A NATS server with JetStream enabled

## License

MIT
