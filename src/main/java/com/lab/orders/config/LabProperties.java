package com.lab.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TEST / DESIGN CONFIGURATION. Not production recommendations.
 */
@ConfigurationProperties(prefix = "lab")
public class LabProperties {

	private final Outbox outbox = new Outbox();
	private final Consumer consumer = new Consumer();

	public Outbox getOutbox() {
		return outbox;
	}

	public Consumer getConsumer() {
		return consumer;
	}

	public static class Outbox {
		private int claimBatchSize = 20;
		private int leaseSeconds = 5;
		private long backoffBaseMs = 10;
		private long backoffCapMs = 200;
		private int maxAttempts = 8;

		public int getClaimBatchSize() {
			return claimBatchSize;
		}

		public void setClaimBatchSize(int claimBatchSize) {
			this.claimBatchSize = claimBatchSize;
		}

		public int getLeaseSeconds() {
			return leaseSeconds;
		}

		public void setLeaseSeconds(int leaseSeconds) {
			this.leaseSeconds = leaseSeconds;
		}

		public long getBackoffBaseMs() {
			return backoffBaseMs;
		}

		public void setBackoffBaseMs(long backoffBaseMs) {
			this.backoffBaseMs = backoffBaseMs;
		}

		public long getBackoffCapMs() {
			return backoffCapMs;
		}

		public void setBackoffCapMs(long backoffCapMs) {
			this.backoffCapMs = backoffCapMs;
		}

		public int getMaxAttempts() {
			return maxAttempts;
		}

		public void setMaxAttempts(int maxAttempts) {
			this.maxAttempts = maxAttempts;
		}
	}

	public static class Consumer {
		private int maxAttempts = 3;
		private long backoffBaseMs = 1;
		private long backoffCapMs = 50;

		public int getMaxAttempts() {
			return maxAttempts;
		}

		public void setMaxAttempts(int maxAttempts) {
			this.maxAttempts = maxAttempts;
		}

		public long getBackoffBaseMs() {
			return backoffBaseMs;
		}

		public void setBackoffBaseMs(long backoffBaseMs) {
			this.backoffBaseMs = backoffBaseMs;
		}

		public long getBackoffCapMs() {
			return backoffCapMs;
		}

		public void setBackoffCapMs(long backoffCapMs) {
			this.backoffCapMs = backoffCapMs;
		}
	}
}
