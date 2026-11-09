package com.astral.exchange;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseHub {
    private final Set<SseEmitter> clients = ConcurrentHashMap.newKeySet();
    public SseEmitter connect(Object snapshot) {
        SseEmitter stream = new SseEmitter(55000L);
        clients.add(stream);
        stream.onCompletion(() -> clients.remove(stream));
        stream.onTimeout(() -> { clients.remove(stream); stream.complete(); });
        stream.onError(e -> clients.remove(stream));
        send(stream, snapshot);
        return stream;
    }
    public void publish(Object snapshot) { clients.forEach(client -> send(client, snapshot)); }
    private void send(SseEmitter client, Object data) {
        try { client.send(SseEmitter.event().name("market").data(data)); }
        catch (Exception e) { clients.remove(client); client.complete(); }
    }
}
