const { Client } = require("@stomp/stompjs");

const wsUrl = process.env.WS_URL;
const token = process.env.ACCESS_TOKEN;
const channelId = process.env.CHANNEL_ID;

if (!wsUrl || !token || !channelId) {
  console.error("WS_URL, ACCESS_TOKEN and CHANNEL_ID are required.");
  process.exit(2);
}

const events = [];

const done = new Promise((resolve, reject) => {
  const timeout = setTimeout(() => {
    reject(new Error(`Timed out waiting for typing events. Received: ${events.join(",")}`));
  }, 10000);

  const client = new Client({
    brokerURL: wsUrl,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 0,
    debug: () => undefined,
    onConnect: () => {
      client.subscribe(`/topic/channels/${channelId}/typing`, (frame) => {
        const event = JSON.parse(frame.body);
        events.push(event.eventType);
        if (events.includes("TYPING_STARTED") && events.includes("TYPING_STOPPED")) {
          clearTimeout(timeout);
          client.deactivate().finally(() => resolve(events));
        }
      });

      setTimeout(() => {
        client.publish({
          destination: `/app/channels/${channelId}/typing`,
          body: JSON.stringify({ typing: true })
        });
      }, 150);

      setTimeout(() => {
        client.publish({
          destination: `/app/channels/${channelId}/typing`,
          body: JSON.stringify({ typing: false })
        });
      }, 350);
    },
    onStompError: (frame) => {
      clearTimeout(timeout);
      reject(new Error(frame.headers.message || frame.body || "STOMP error"));
    },
    onWebSocketError: (error) => {
      clearTimeout(timeout);
      reject(error);
    }
  });

  client.activate();
});

done
  .then((received) => {
    console.log(JSON.stringify({ ok: true, events: received }));
  })
  .catch((error) => {
    console.error(JSON.stringify({ ok: false, error: error.message }));
    process.exit(1);
  });
