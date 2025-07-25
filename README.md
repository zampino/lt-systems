# Replisock

A minimal Clojure/ClojureScript demo showcasing Replicant UI with real-time WebSocket communication.

## Overview

Replisock demonstrates:
- **Replicant**: React-like virtual DOM for ClojureScript
- **WebSocket**: Bidirectional real-time communication 
- **Nexus**: Action/effect state management
- **Remote Control**: Server can trigger client actions

## Quick Start

```bash
# Start compiler
npx shadow-cljs watch app

# Start server (in separate terminal)
clj -M -m replisock
```

Open http://localhost:8080

## Development

REPL:

```clojure
(require 'replisock)
(replisock/start! {:port 3000})
(replisock/send-action! :counter/inc)  ; Remote control
```

Hot reload works via shadow-cljs. WebSocket connections survive frontend reloads.

## Project Structure

```
├── src/
│   ├── replisock.clj        # HTTP server + WebSocket
│   └── replisock.cljs       # Replicant UI + Nexus state
├── deps.edn                 # Dependencies
├── shadow-cljs.edn          # Build config
└── resources/public/        # Generated assets
```

## Dependencies

- **http-kit**: WebSocket server
- **Replicant**: Virtual DOM
- **Nexus**: State management  
- **shadow-cljs**: ClojureScript compiler
- **Tailwind CSS**: Styling

## License

MIT
