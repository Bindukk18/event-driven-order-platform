package com.lab.orders.runtime;

public class ProcessingException extends RuntimeException {

	private final FailureClass failureClass;

	public ProcessingException(FailureClass failureClass, String message) {
		super(message);
		this.failureClass = failureClass;
	}

	public FailureClass getFailureClass() {
		return failureClass;
	}
}
