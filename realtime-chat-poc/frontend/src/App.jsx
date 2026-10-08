import { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';

const API = 'http://localhost:8080/api';
const WS_URL = 'ws://localhost:8080/ws';

const tick = (status) => (status === 'SENT' ? '✓' : '✓✓');
const fmtTime = (iso) =>
  new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

export default function App() {
  const [me, setMe] = useState(null);

  if (!me) return <Login onLogin={setMe} />;
  return <Chat me={me} onLogout={() => setMe(null)} />;
}

/* ------------------------------ Login ------------------------------ */
function Login({ onLogin }) {
  const [name, setName] = useState('');
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const res = await fetch(`${API}/users/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: name }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.message || 'Login failed');
      }
      const user = await res.json();
      onLogin(user.username);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <h2>Realtime Chat POC</h2>
        <p className="muted">Pick a username (new users are created automatically).</p>
        <input
          autoFocus
          placeholder="e.g. alice"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button type="submit">Join</button>
        {error && <div className="error">{error}</div>}
      </form>
    </div>
  );
}

/* ------------------------------ Chat ------------------------------ */
function Chat({ me, onLogout }) {
  const [users, setUsers] = useState([]);
  const [activeChat, setActiveChat] = useState(null);
  const [messages, setMessages] = useState([]);
  const [unread, setUnread] = useState({});
  const [typingFrom, setTypingFrom] = useState({});
  const [notifications, setNotifications] = useState([]);
  const [toast, setToast] = useState(null);
  const [showNotifs, setShowNotifs] = useState(false);
  const [connected, setConnected] = useState(false);
  const [text, setText] = useState('');

  const clientRef = useRef(null);
  const activeChatRef = useRef(null);
  const typingTimers = useRef({});
  const stopTypingTimer = useRef(null);
  const bottomRef = useRef(null);

  const publish = useCallback((destination, payload) => {
    const client = clientRef.current;
    if (client && client.connected) {
      client.publish({ destination, body: JSON.stringify(payload) });
    }
  }, []);

  const loadUsers = useCallback(async () => {
    const res = await fetch(`${API}/users`);
    const all = await res.json();
    setUsers(all.filter((u) => u.username !== me));
  }, [me]);

  const markNotificationsSeenLocally = (partner) =>
    setNotifications((prev) =>
      prev.map((n) =>
        n.type === 'NEW_MESSAGE' && n.refUser === partner ? { ...n, seen: true } : n
      )
    );

  /* ---- WebSocket lifecycle ---- */
  useEffect(() => {
    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { username: me },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnected(true);

        // incoming + echoed messages
        client.subscribe('/user/queue/messages', (frame) => {
          const m = JSON.parse(frame.body);
          const partner = m.sender === me ? m.recipient : m.sender;

          if (partner === activeChatRef.current) {
            setMessages((prev) => (prev.some((x) => x.id === m.id) ? prev : [...prev, m]));
            if (m.sender !== me) {
              publish('/app/chat.read', { with: partner });
              markNotificationsSeenLocally(partner);
            }
          } else if (m.sender !== me) {
            setUnread((u) => ({ ...u, [m.sender]: (u[m.sender] || 0) + 1 }));
          }
          // unknown user (registered after we loaded the list)?
          setUsers((prev) => {
            if (!prev.some((u) => u.username === partner) && partner !== me) loadUsers();
            return prev;
          });
        });

        // typing indicator
        client.subscribe('/user/queue/typing', (frame) => {
          const { from, typing } = JSON.parse(frame.body);
          clearTimeout(typingTimers.current[from]);
          setTypingFrom((t) => ({ ...t, [from]: typing }));
          if (typing) {
            // server throttles to one event / 2s, so expire after 3s without refresh
            typingTimers.current[from] = setTimeout(
              () => setTypingFrom((t) => ({ ...t, [from]: false })),
              3000
            );
          }
        });

        // read receipts
        client.subscribe('/user/queue/read-receipts', (frame) => {
          const { messageIds } = JSON.parse(frame.body);
          setMessages((prev) =>
            prev.map((m) => (messageIds.includes(m.id) ? { ...m, status: 'READ' } : m))
          );
        });

        // notifications
        client.subscribe('/user/queue/notifications', (frame) => {
          const n = JSON.parse(frame.body);
          const isActive = n.type === 'NEW_MESSAGE' && n.refUser === activeChatRef.current;
          setNotifications((prev) => [{ ...n, seen: isActive ? true : n.seen }, ...prev]);
          if (!isActive) {
            setToast(n);
            setTimeout(() => setToast(null), 4000);
          }
        });

        // server-side errors for this user
        client.subscribe('/user/queue/errors', (frame) => alert(frame.body));

        // presence broadcast
        client.subscribe('/topic/presence', (frame) => {
          const { username, online } = JSON.parse(frame.body);
          if (username === me) return;
          setUsers((prev) => {
            if (!prev.some((u) => u.username === username)) {
              loadUsers();
              return prev;
            }
            return prev.map((u) => (u.username === username ? { ...u, online } : u));
          });
        });

        // initial data
        loadUsers();
        fetch(`${API}/unread/${me}`).then((r) => r.json()).then(setUnread);
        fetch(`${API}/notifications/${me}`).then((r) => r.json()).then(setNotifications);
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: (frame) => console.error('STOMP error', frame.headers['message']),
    });

    client.activate();
    clientRef.current = client;
    return () => {
      client.deactivate();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, activeChat, typingFrom]);

  /* ---- actions ---- */
  const openChat = async (username) => {
    setActiveChat(username);
    activeChatRef.current = username;
    const res = await fetch(`${API}/conversations/${me}/${username}`);
    setMessages(await res.json());
    setUnread((u) => {
      const copy = { ...u };
      delete copy[username];
      return copy;
    });
    markNotificationsSeenLocally(username);
    publish('/app/chat.read', { with: username });
  };

  const send = (e) => {
    e.preventDefault();
    const content = text.trim();
    if (!content || !activeChat) return;
    publish('/app/chat.send', { to: activeChat, content });
    publish('/app/chat.typing', { to: activeChat, typing: false });
    clearTimeout(stopTypingTimer.current);
    setText('');
  };

  const onType = (e) => {
    setText(e.target.value);
    if (!activeChat) return;
    publish('/app/chat.typing', { to: activeChat, typing: true });
    clearTimeout(stopTypingTimer.current);
    stopTypingTimer.current = setTimeout(
      () => publish('/app/chat.typing', { to: activeChat, typing: false }),
      1500
    );
  };

  const markSeen = async (n) => {
    if (n.seen) return;
    await fetch(`${API}/notifications/${n.id}/seen`, { method: 'POST' });
    setNotifications((prev) => prev.map((x) => (x.id === n.id ? { ...x, seen: true } : x)));
  };

  const unseenCount = notifications.filter((n) => !n.seen).length;
  const activeUser = users.find((u) => u.username === activeChat);

  return (
    <div className="app">
      {/* ---------- sidebar ---------- */}
      <aside className="sidebar">
        <div className="me">
          <div>
            <strong>{me}</strong>
            <div className={`conn ${connected ? 'ok' : 'bad'}`}>
              {connected ? 'connected' : 'reconnecting...'}
            </div>
          </div>
          <div className="me-actions">
            <button className="bell" onClick={() => setShowNotifs((s) => !s)}>
              🔔{unseenCount > 0 && <span className="badge">{unseenCount}</span>}
            </button>
            <button className="link" onClick={onLogout}>logout</button>
          </div>
        </div>

        {showNotifs && (
          <div className="notif-panel">
            {notifications.length === 0 && <div className="muted pad">No notifications</div>}
            {notifications.map((n) => (
              <div
                key={n.id}
                className={`notif ${n.seen ? '' : 'unseen'}`}
                onClick={() => markSeen(n)}
              >
                <div className="notif-title">{n.title}</div>
                <div className="muted">{n.body}</div>
              </div>
            ))}
          </div>
        )}

        <div className="section-title">Users</div>
        <div className="user-list">
          {users.length === 0 && (
            <div className="muted pad">Open another tab and join with a different username.</div>
          )}
          {users.map((u) => (
            <div
              key={u.username}
              className={`user ${activeChat === u.username ? 'active' : ''}`}
              onClick={() => openChat(u.username)}
            >
              <span className={`dot ${u.online ? 'on' : 'off'}`} />
              <span className="uname">{u.username}</span>
              {unread[u.username] > 0 && <span className="badge">{unread[u.username]}</span>}
            </div>
          ))}
        </div>
      </aside>

      {/* ---------- chat window ---------- */}
      <main className="chat">
        {!activeChat ? (
          <div className="empty muted">Select a user to start chatting</div>
        ) : (
          <>
            <header className="chat-header">
              <strong>{activeChat}</strong>
              <span className="muted">
                {typingFrom[activeChat]
                  ? 'typing...'
                  : activeUser?.online
                  ? 'online'
                  : activeUser?.lastSeen
                  ? `last seen ${new Date(activeUser.lastSeen).toLocaleString()}`
                  : 'offline'}
              </span>
            </header>

            <div className="messages">
              {messages.map((m) => (
                <div key={m.id} className={`bubble-row ${m.sender === me ? 'mine' : 'theirs'}`}>
                  <div className="bubble">
                    <div>{m.content}</div>
                    <div className="meta">
                      {fmtTime(m.sentAt)}
                      {m.sender === me && (
                        <span className={`tick ${m.status === 'READ' ? 'read' : ''}`}>
                          {' '}
                          {tick(m.status)}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
              {typingFrom[activeChat] && (
                <div className="bubble-row theirs">
                  <div className="bubble typing">
                    <span>•</span><span>•</span><span>•</span>
                  </div>
                </div>
              )}
              <div ref={bottomRef} />
            </div>

            <form className="composer" onSubmit={send}>
              <input
                placeholder={`Message ${activeChat}`}
                value={text}
                onChange={onType}
                disabled={!connected}
              />
              <button type="submit" disabled={!connected}>Send</button>
            </form>
          </>
        )}
      </main>

      {toast && (
        <div className="toast">
          <strong>{toast.title}</strong>
          <div>{toast.body}</div>
        </div>
      )}
    </div>
  );
}
