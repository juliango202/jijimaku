package jijimaku.errors;

@SuppressWarnings("serial")
public class JijimakuError extends RuntimeException {
  JijimakuError() {
    super();
  }

  JijimakuError(String message) {
    super(message);
  }
}