package jijimaku.errors;

@SuppressWarnings("serial")
public class SubsDictError extends RuntimeException {
  SubsDictError() {
    super();
  }

  SubsDictError(String message) {
    super(message);
  }
}