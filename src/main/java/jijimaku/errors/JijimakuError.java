package jijimaku.errors;

@SuppressWarnings("serial")
public class JijimakuError extends RuntimeException {
  public JijimakuError() {
    super();
  }

  public JijimakuError(String message) {
    super(message);
  }
}