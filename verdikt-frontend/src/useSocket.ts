import { useEffect, useRef } from 'react';

declare const SockJS: new (url: string) => object;
declare const StompJs: { Client: new (cfg: object) => StompClient };

interface StompClient {
  webSocketFactory: () => object;
  connected?: boolean;
  onConnect: (frame: object) => void;
  onDisconnect: () => void;
  onWebSocketClose?: (event: object) => void;
  onWebSocketError?: (event: object) => void;
  activate: () => void;
  deactivate: () => Promise<void>;
  subscribe: (dest: string, cb: (msg: { body: string }) => void) => { unsubscribe: () => void };
}

type MessageHandler = (payload: Record<string, unknown>) => void;

type SocketStatus = 'connecting' | 'connected' | 'reconnecting';

export function useSocket(
  roomId: string | undefined,
  subscriptions: { topic: string; handler: MessageHandler }[],
  onConnected?: () => void,
  onDisconnected?: (status: SocketStatus) => void
) {
  const clientRef = useRef<StompClient | null>(null);
  const subsRef = useRef(subscriptions);

  // keep subsRef current without triggering reconnect
  useEffect(() => {
    subsRef.current = subscriptions;
  }, [subscriptions]);

  useEffect(() => {
    if (!roomId) return;

    const client = new StompJs.Client({
      webSocketFactory: () => new SockJS(`${import.meta.env.VITE_API_URL}/ws`),
      reconnectDelay: 1500,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      connectionTimeout: 10000,
      debug: () => undefined,
    });

    client.onConnect = () => {
      onConnected?.();
      subsRef.current.forEach(({ topic, handler }) => {
        client.subscribe(topic, (msg) => {
          handler(JSON.parse(msg.body) as Record<string, unknown>);
        });
      });
    };

    client.onDisconnect = () => {
      onDisconnected?.('reconnecting');
    };

    client.onWebSocketClose = () => {
      onDisconnected?.('reconnecting');
    };

    client.onWebSocketError = () => {
      onDisconnected?.('reconnecting');
    };

    client.activate();
    clientRef.current = client;

    return () => {
      clientRef.current = null;
      void client.deactivate();
    };
  }, [roomId, onConnected, onDisconnected]);

  return clientRef;
}