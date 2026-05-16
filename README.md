# NATS-Fabric

A Fabric library that provides a shared [NATS](https://nats.io) connection for Minecraft servers.

> **Server-side only.** This library has no client-side functionality.

## Features

- **Shared Connection:** Multiple mods share a single NATS connection, reducing overhead and simplifying setup.
- **Automatic Reconnection:** The NATS client handles reconnection internally once the initial connection is established. The initial connection retries indefinitely until it succeeds.
- **Event API:** Hooks for `CONNECTED`, `RECONNECTED`, and `DISCONNECTED` events, allowing mods to react to network state changes.

## Setup

1. Start the server once to generate `config/nats-fabric.json`.
2. Fill in your NATS server details:

```json
{
  "serverName": "server-1",
  "natsUrl": "nats://127.0.0.1:4222",
  "natsAuthToken": "",
  "natsUsername": "",
  "natsPassword": ""
}
```

`serverName` should be unique for each server in your network. It is used as the connection identifier and can be read by dependent mods via `NatsManager.getInstance().getServerName()`.

## API

```java
NatsManager nats = NatsManager.getInstance();

// Check connection state
nats.isConnected();
nats.getConnection();   // returns null if not connected
nats.getJetStream();    // returns null if unavailable

// Reload config from disk and reconnect
nats.reload();

// --- Coordinated shutdown ---

// Register at mod startup so the library waits for you before closing
nats.registerClient("your-mod-id");
nats.registerClient("your-mod-id", 30); // require at least 30s

// Optionally update timeout at runtime (e.g. based on player count)
nats.requestTimeout("your-mod-id", 60); // must be registered first

// Signal cleanup is complete (call in SERVER_STOPPING or whenever done)
nats.deregisterClient("your-mod-id");
```

The effective shutdown timeout is `max(config.shutdownTimeoutSeconds, highest client request)`.
Config default: `"shutdownTimeoutSeconds": 10`.

## Requirements

- Minecraft 26.1+ (Fabric)
- Java 25+
- A running NATS server (JetStream optional)

## License

MIT
