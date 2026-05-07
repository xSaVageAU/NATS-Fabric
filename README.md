# NATS-Fabric

A simple Fabric library that provides a shared [NATS](https://nats.io) connection for Minecraft servers.

## Features

- **Shared Connection:** Multiple mods can utilize a single, high-performance NATS connection, reducing overhead.
- **Connection Watchdog:** Hardened background watchdog with infinite retry logic. It ensures the connection stays alive and recovers automatically from network failures.
- **Event API:** Hooks for `CONNECTED`, `RECONNECTED`, and `DISCONNECTED` events, allowing mods to react dynamically to network state changes.

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

`serverName` should be unique for each server in your cluster. This ID is used for session locking and RPC hand-offs.

## Requirements

- Minecraft 26.1+ (Fabric)
- Java 25+
- A NATS server with JetStream enabled

## License

MIT
