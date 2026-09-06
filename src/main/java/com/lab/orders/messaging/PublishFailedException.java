package com.lab.orders.messaging;

public class PublishFailedException extends RuntimeException {

	public PublishFailedException(String message) {
		super(message);
	}
}
