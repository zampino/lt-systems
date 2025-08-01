# Replisock Project Summary

## Overview
Replisock is a minimal Clojure/ClojureScript web application demonstrating real-time WebSocket communication with Replicant UI components. It features a counter application that can be controlled both locally via UI and remotely via server-sent WebSocket messages.

## Architecture
- **Backend**: Clojure with http-kit server
- **Frontend**: ClojureScript with Replicant (React-like virtual DOM)
- **State Management**: Nexus (action/effect system)
- **Build Tool**: shadow-cljs for ClojureScript compilation
- **Communication**: WebSocket with EDN message format
- **Styling**: Tailwind CSS (via CDN)

## Key Files

### `/src/replisock.clj` - Backend Server
- **Purpose**: HTTP server with WebSocket support and client connection management
- **Key Functions**:
  - `start!` - Starts server on specified port (default 8080)
  - `stop!` - Gracefully stops server
  - `handler` - Routes requests, serves HTML page and JS assets, handles WebSocket connections
  - `html-page` - Generates HTML with Replicant server-side rendering
  - `add-client!`/`remove-client!` - Manage WebSocket client connections
  - `broadcast!` - Send messages to all connected clients
  - `send-action!` - Broadcast Nexus actions to clients via WebSocket
- **Features**: WebSocket client management, static JS file serving, system state with client tracking

### `/src/replisock.cljs` - Frontend Client
- **Purpose**: Interactive UI with Replicant components and WebSocket connectivity
- **Key Components**:
  - `app-view` - Main UI component with counter and styled buttons (flex layout with equal spacing)
  - `init` - Application initialization with DOM mounting, event handling, and WebSocket connection
  - `connect-ws!` - Establishes WebSocket connection and message handling
- **System Architecture**: 
  - `system` map contains `:store` atom and `:ws` atom for full system access
  - `nxr/register-system->state!` uses `(comp deref :store)` to extract UI state
  - Effects receive full system map, enabling WebSocket access
- **State Management**: 
  - Atom-based store with counter state
  - Nexus actions: `:counter/inc`, `:counter/dec`, `:counter/reset`
  - Effects: `:store/assoc-in`, `:store/update-in` for state updates, `:ws/send` for WebSocket messaging
- **WebSocket**: Receives EDN messages and dispatches actions to Nexus registry, properly managed in system

### `/deps.edn` - Project Dependencies
- **Core Dependencies**:
  - `http-kit/http-kit "2.8.0"` - Async HTTP server
  - `no.cjohansen/replicant "2025.06.21"` - Virtual DOM library
  - `no.cjohansen/nexus "2025.07.1"` - State management system
  - `thheller/shadow-cljs "2.28.14"` - ClojureScript compiler
- **Aliases**: `:mcp` for clojure-mcp server integration

### `/shadow-cljs.edn` - Build Configuration
- **Build Target**: Browser application
- **Output**: `resources/public/js/` directory
- **Module**: Single main module with `replisock/init` entry point
- **Dev Features**: Hot reload with after-load hooks
- **Dependencies**: Includes nexus for client-side state management

## Development Workflow

### Starting Development
1. **Start shadow-cljs compiler**: `npx shadow-cljs watch app`
2. **Start Clojure server**: `clj -M -m replisock` or `(start! {})` in REPL
3. **Access application**: `http://localhost:8080`

### Hot Reload
- ClojureScript changes automatically reload via shadow-cljs
- Server changes require REPL evaluation or restart
- Frontend state persists during hot reloads
- WebSocket reconnects automatically on client reload

### REPL Development

```clojure
;; Server operations
(require 'replisock)
(replisock/start! {:port 3000})
(replisock/stop!)

;; WebSocket messaging
(replisock/broadcast-action! :counter/inc)
(replisock/broadcast-action! :counter/reset)

;; Check connected clients
@replisock/system
```

## State Management Pattern

### Action/Effect Architecture
```clojure
;; Actions return effects and send WebSocket notifications
:counter/inc → [[:store/update-in [:counter] inc]
               [:ws/send {:type :counter :action :inc}]]
:counter/dec → [[:store/update-in [:counter] dec]
               [:ws/send {:type :counter :action :dec}]]
:counter/reset → [[:store/assoc-in [:counter] 0]
                 [:ws/send {:type :counter :action :reset}]]

;; Effects modify system state and can access WebSocket
:store/assoc-in → (swap! (:store system) assoc-in path value)
:store/update-in → (swap! (:store system) update-in path f & args)
:ws/send → (.send @(:ws system) (pr-str message))
```

### UI Event Handling
```clojure
{:on {:click [[:counter/inc]]}}  ; Clean action dispatch
```

### WebSocket Communication
- **Server to Client**: EDN format via `pr-str`
- **Message Format**: `{:type :action :action :counter/inc :args []}`
- **Client Processing**: `clojure.edn/read-string` → Nexus dispatch
- **Client to Server**: Notifications about actions taken

## Extension Points

### Adding New Features
1. **New Actions**: Register with `nxr/register-action!`
2. **New Effects**: Register with `nxr/register-effect!`
3. **UI Components**: Add to `app-view` function
4. **Server Routes**: Extend `handler` function conditions
5. **WebSocket Messages**: Use `send-action!` for server-initiated updates

### Common Patterns
- **State Updates**: Use `:store/update-in` for functional updates, `:store/assoc-in` for direct assignment
- **Component Styling**: Tailwind utility classes with responsive design
- **Server Endpoints**: Add conditions to handler cond form
- **Remote Actions**: Server can trigger any registered Nexus action via WebSocket

## Key Implementation Details

### WebSocket Architecture
- Server maintains client set in system atom
- Clients auto-connect on initialization
- EDN messaging for type safety and Clojure compatibility
- Server can broadcast actions to all connected clients simultaneously
- Client actions automatically notify server of state changes

### Server-Side Rendering
The server generates initial HTML with embedded JavaScript, allowing for immediate interactivity.

### Component Communication
Uses Nexus registry system for decoupled action/effect handling, enabling testable and composable state management.

### Asset Pipeline
Static JS assets served directly by server with proper content-type headers, no separate asset server required.

### Development Experience
Hot reload works seamlessly with shadow-cljs watch mode, preserving application state during development iterations. WebSocket connections survive frontend reloads.

## WebSocket API Examples

### Server-side Broadcasting
```clojure
;; Send counter increment to all connected clients
(send-action! :counter/inc)

;; Send counter reset to all connected clients  
(send-action! :counter/reset)
```

### Client-side Message Handling
Messages automatically dispatched to Nexus registry based on `:action` field in received EDN data.
