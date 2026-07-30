package com.ecosphere.notification.domain;

public class InvalidDeliveryStateException extends IllegalStateException {

    public InvalidDeliveryStateException(String message) {
        super(message);
    }
}
