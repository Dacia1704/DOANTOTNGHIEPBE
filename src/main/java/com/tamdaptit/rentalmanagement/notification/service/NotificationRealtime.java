package com.tamdaptit.rentalmanagement.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRealtime {
    private final SimpMessagingTemplate messaging;

    public void changedAfterCommit(String userId) {
        var synchronization = new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    messaging.convertAndSendToUser(userId,"/queue/notifications",Map.of("kind","CHANGED"));
                } catch (RuntimeException ex) {
                    // REST remains the source of truth when a browser misses a WebSocket update.
                    log.warn("Notification realtime update unavailable ({})",ex.getClass().getSimpleName());
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        else synchronization.afterCommit();
    }
}
