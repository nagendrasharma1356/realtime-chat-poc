# Real-Time Chat & Notification POC

Spring Boot + WebSocket (STOMP) + Redis + H2, with a React (Vite) client.

```
React  <-- WebSocket (STOMP) -->  Spring Boot  -->  Redis (presence, unread, typing throttle)
                                       |
                                       +-------->  H2 (messages, users, notifications)
```

## Features
| Feature | How it works |
|---|---|
| One-to-one messaging | `/app/chat.send` -> saved in H2 -> pushed to `/user/{to}/queue/messages` (+ echo to sender) |
| Online / offline | WebSocket session open/close events -> Redis sets (`presence:*`), broadcast on `/topic/presence`. Multi-tab safe. Heartbeats detect dead tabs |
| Read / unread | Redis hash `unread:{user}` counters; message status `SENT -> DELIVERED -> READ`; read receipts pushed to sender (✓ / ✓✓ / blue ✓✓) |
| Typing indicator | `/app/chat.typing`, throttled in Redis (`typing:{from}:{to}`, 2s TTL) -> `/user/{to}/queue/typing` |
| Notifications | Saved in H2 + pushed to `/user/{to}/queue/notifications`; bell + toast in UI; `POST /api/notifications/send` for system notifications |
| Message persistence | H2 file DB (`backend/data/chatdb`) - history survives restarts. Unread counters in Redis are rebuilt from H2 on startup |

## Prerequisites
Java 17+, Maven (or IntelliJ's bundled Maven), Node 18+, Redis (Docker is easiest).

## Run
1. **Redis**
   ```
   docker compose up -d
   ```
   (No Docker? Install Redis/Memurai locally on port 6379.)
2. **Backend** - IntelliJ: *File > Open* -> select the `backend` folder (pom.xml) -> run `ChatApplication`.
   Or: `cd backend && mvn spring-boot:run`  ->  http://localhost:8080
3. **Frontend**
   ```
   cd frontend
   npm install
   npm run dev
   ```
   -> http://localhost:5173

## Test it (2 minutes)
1. Open http://localhost:5173 in a normal window, join as **alice**.
2. Open it in an incognito window (or another browser), join as **bob**.
3. alice sees bob with a green dot. Click bob, type -> bob (with alice's chat open) sees "typing...".
4. Send messages -> ✓✓ appears (bob online = delivered). When bob opens alice's chat the ticks turn blue (read).
5. bob closes the tab -> alice sees bob turn grey (offline). alice sends more messages -> bob later logs in and sees an unread badge + notifications bell.
6. Restart the backend -> chat history is still there.

System notification via Postman / curl:
```
POST http://localhost:8080/api/notifications/send
{ "to": "alice", "title": "Maintenance", "body": "Server restarts at 10 PM" }
```

## REST API
| Method | URL | Purpose |
|---|---|---|
| POST | `/api/users/login` `{username}` | create/get user |
| GET | `/api/users` | all users + online flag |
| GET | `/api/conversations/{user}/{other}` | persisted chat history |
| GET | `/api/unread/{user}` | unread counts per sender (Redis) |
| GET | `/api/notifications/{user}` | latest 50 notifications |
| POST | `/api/notifications/{id}/seen` | mark seen |
| POST | `/api/notifications/send` | push a SYSTEM notification |

## WebSocket protocol (STOMP)
- Endpoint: `ws://localhost:8080/ws`, CONNECT header `username: alice`
- Send: `/app/chat.send {to, content}`, `/app/chat.typing {to, typing}`, `/app/chat.read {with}`
- Subscribe: `/user/queue/messages`, `/user/queue/typing`, `/user/queue/read-receipts`, `/user/queue/notifications`, `/user/queue/errors`, `/topic/presence`

## H2 console
http://localhost:8080/h2-console  - JDBC URL `jdbc:h2:file:./data/chatdb`, user `sa`, empty password
(run backend from the `backend` folder so the path matches).

## POC limitations / next steps
- Auth is username-only (header on CONNECT). Replace with JWT validation in `WebSocketConfig`.
- Uses Spring's in-memory simple broker. For multiple backend instances, switch to a Redis pub/sub relay (or RabbitMQ/ActiveMQ STOMP relay) so messages reach users connected to other nodes.
- No file/image messages, group chat, or message pagination yet.
