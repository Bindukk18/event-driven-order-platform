package com.lab.orders.runtime;

public class RetryableProcessingException extends ProcessingException {

	public RetryableProcessingException(String message) {
		super(FailureClass.RETRYABLE, message);
	}
}
