package com.ssafy.fint.domain.account.event;

public record AccountCreatedEvent(Long tenantId, Long accountId) {
}
