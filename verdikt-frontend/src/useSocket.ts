import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useEffect, useRef } from 'react';
import { apiUrl } from './api/client';

interface SocketClient {
  webSocketFactory: () => object;
  connected: boolean;
  onConnect: () => void;
  onDisconnect: () => void;
  onWebSocketClose: () => void;
  onWebSocketError: () => void;
  activate: () => void;
  deactivate: () => Promise<void>;
  subscribe: (destination: string, callback: (message: IMessage) => void) => StompSubscription;
}

type MessageHandler = (payload: Record<string, unknown>) => void;
type SocketStatus = 'connecting' | 'connected' | 'reconnecting';

export function useSocket(
  roomId: string | undefined,
  subscriptions: { topic: string; handler: MessageHandler }[],
  onConnected?: () => void,
  onDisconnected?: (status: SocketStatus) => void,
  playerToken?: string,
) {
  const clientRef = useRef<SocketClient | null>(null);
  const subsRef = useRef(subscriptions);
  const activeSubscriptionsRef = useRef<StompSubscription[]>([]);

  useEffect(() => { subsRef.current = subscriptions; }, [subscriptions]);

  useEffect(() => {
    if (!roomId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(apiUrl('/ws')),
      connectHeaders: playerToken ? { 'X-Player-Token': playerToken } : {},
      reconnectDelay: 1500,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      connectionTimeout: 10000,
      debug: () => undefined,
    }) as unknown as SocketClient;

    const reconnect = () => onDisconnected?.('reconnecting');
    client.onConnect = () => {
      onConnected?.();
      activeSubscriptionsRef.current.forEach((subscription) => subscription.unsubscribe());
      activeSubscriptionsRef.current = [];
      subsRef.current.forEach(({ topic, handler }) => {
        activeSubscriptionsRef.current.push(client.subscribe(topic, (message) => {
          try { handler(JSON.parse(message.body) as Record<string, unknown>); } catch { /* Ignore malformed room events. */ }
        }));
      });
    };
    client.onDisconnect = reconnect;
    client.onWebSocketClose = reconnect;
    client.onWebSocketError = reconnect;
    client.activate();
    clientRef.current = client;

    return () => {
      activeSubscriptionsRef.current.forEach((subscription) => subscription.unsubscribe());
      activeSubscriptionsRef.current = [];
      clientRef.current = null;
      void client.deactivate();
    };
  }, [roomId, onConnected, onDisconnected, playerToken]);

  return clientRef;
}
