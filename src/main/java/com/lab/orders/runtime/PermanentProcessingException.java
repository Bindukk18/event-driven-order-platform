package com.lab.orders.runtime;

public class PermanentProcessingException extends ProcessingException {

	public PermanentProcessingException(String message) {
		super(FailureClass.PERMANENT, message);
	}
}
