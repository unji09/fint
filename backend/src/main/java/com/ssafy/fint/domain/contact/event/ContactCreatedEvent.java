package com.ssafy.fint.domain.contact.event;

public record ContactCreatedEvent(Long tenantId, Long accountId, Long contactId) {
}
