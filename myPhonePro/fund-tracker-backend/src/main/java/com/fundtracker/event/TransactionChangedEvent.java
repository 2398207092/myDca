package com.fundtracker.event;

public record TransactionChangedEvent(String holdingId, String userId) {
}
