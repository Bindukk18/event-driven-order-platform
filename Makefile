.PHONY: test demo

JAVA_HOME ?= /opt/homebrew/opt/openjdk@21
export JAVA_HOME
export PATH := $(JAVA_HOME)/bin:$(PATH)

test:
	./mvnw -q test

demo:
	./mvnw -q -Dtest=LabWalkthroughTest test
