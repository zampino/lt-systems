# Replisock

A real-time L-system visualization demo showcasing Replicant UI with WebSocket communication. Watch mathematical patterns evolve and grow in your browser as the server computes L-system iterations and streams them to connected clients.

## What is it?

Replisock demonstrates:
- **L-systems**: Mathematical pattern generation (fractals, plant-like structures)
- **Real-time visualization**: Server-computed patterns streamed via WebSocket
- **Replicant**: React-like virtual DOM for ClojureScript
- **Nexus**: Functional state management with actions/effects
- **Remote control**: Server can trigger client UI updates

The app visualizes L-system evolution as SVG graphics, with a "tape head" that reads symbols and generates turtle graphics commands for drawing fractal patterns.

## Quick Start

```bash
# Start ClojureScript compiler
npx shadow-cljs watch app

# Start server (separate terminal)
clj -M -m replisock
```

Open http://localhost:8080

## Development

Interactive REPL control:

```clojure
(require 'replisock)
(replisock/start! {:port 3000})

# Remote control examples
(replisock/send-action! :lt-sys/step)     ; Advance L-system
```

Hot reload works via shadow-cljs. WebSocket connections survive frontend reloads.

## Architecture

**Client (ClojureScript)**
- Replicant virtual DOM rendering
- Nexus action/effect state management
- WebSocket client for real-time updates
- SVG visualization of L-system patterns

**Server (Clojure)**
- http-kit WebSocket server
- L-system computation engine
- Broadcast system for multi-client updates
- Remote action triggering

## Project Structure

```
├── src/
│   ├── replisock.clj        # HTTP/WebSocket server
│   ├── replisock.cljs       # Replicant UI + client logic
│   └── lt_sys.cljc          # L-system engine (shared)
├── deps.edn                 # Dependencies + MCP alias
├── shadow-cljs.edn          # ClojureScript build config
└── resources/public/        # Generated frontend assets
```

## Key Dependencies

- **http-kit**: High-performance WebSocket server
- **Replicant**: Virtual DOM library for ClojureScript
- **Nexus**: Functional state management
- **shadow-cljs**: ClojureScript compilation
- **clojure-mcp**: Model Context Protocol server

## L-system Features

- Configurable rules and axioms
- Turtle graphics interpretation
- Real-time pattern evolution
- SVG path generation
- Interactive stepping control

Example L-system evolution:

```clojure
(->> (-> lt/L-System
         (lt/start-at [400 200])
         (assoc :tape '[C F C F]
                :rules {'F '[F F]
                        'H '[F F H +]
                        'C '[F < - H > + C]}))
     (iterate lt/step)
     (take 100)
     last)
```

## License

MIT
