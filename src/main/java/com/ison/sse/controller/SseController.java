package com.ison.sse.controller;

import com.ison.sse.com.ison.sse.repository.NotificationRepository;
import org.h2.api.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.jpa.Jpa;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.integration.dsl.channel.MessageChannels;

import com.ison.sse.model.Notification;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class SseController {

    private final Map<String, SseEmitter> sses = new ConcurrentHashMap<>();

    @Autowired
    NotificationRepository notificationRepository;

    @Bean
    SubscribableChannel databaseChannel() {
        return MessageChannels.publishSubscribe().get();
    }


    @Bean
    IntegrationFlow integrationFlow(EntityManager entityManager) {
        return IntegrationFlows.from(Jpa.inboundAdapter(entityManager)
                        .jpaQuery("select n from Notification n order by n.id desc")
                        .maxResults(1),
                poller->poller.poller(spec->spec.fixedRate(10000L)))
                .transform(Notification.class,Notification::getMessage)
                .handle(String.class, (message,map) -> {
                    sses.forEach((k, sse) -> {
                        try {
                            sse.send(message);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    return null;
                })
                .get();
    }

    @RequestMapping(path = "/stream/{client}", method = RequestMethod.GET)
    public SseEmitter notifications(@PathVariable String client) {

        SseEmitter emitter = new SseEmitter(60*10000L);
        sses.put(client,emitter);

        return emitter;
    }

    @RequestMapping(path = "/chatsse", method = RequestMethod.POST)
    public void sendMessage(@RequestBody Notification notification) {

        sses.forEach((k, sse) -> {
            try {
                sse.send(notification, MediaType.APPLICATION_JSON);
            } catch (Exception e) {
                sses.remove(k);
                System.out.println("Client " + k + " dropped from chat");
            }
        });

    }

    @RequestMapping(path = "/chat", method = RequestMethod.POST)
    public ResponseEntity<?> sendMessage2(@RequestBody Notification notification) {

        return new ResponseEntity<>(notificationRepository.save(notification), HttpStatus.OK);

    }


}
