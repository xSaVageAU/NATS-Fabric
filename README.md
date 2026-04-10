# NATS-Fabric

A lightweight Fabric library mod that manages a single shared [NATS](https://nats.io) connection for a Minecraft server.

## What it does

If multiple mods on the same server need to communicate over NATS, each opening their own connection wastes resources and complicates configuration. NATS-Fabric provides a single managed connection instance that any mod can access through a simple API.

The connection is established when the server starts and cleanly closed after the server finishes saving and shutting down.

## Requirements

- Minecraft 26.1+ (Fabric)
- Java 25+
- A NATS server with JetStream enabled

## Setup

Start the server once to generate `config/nats-fabric.json`, then fill in your details:

```json
{
  "serverName": "server-1",
  "natsUrl": "nats://localhost:4222"
}
```

`serverName` should be a unique identifier for each server in your cluster.

## For mod developers

Add NATS-Fabric as a dependency and use `NatsManager.getInstance().getConnection()` to access the shared NATS connection.

The connection is available from `SERVER_STARTED` onwards.

## License

MIT
