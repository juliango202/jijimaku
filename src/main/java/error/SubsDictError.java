package error;

@SuppressWarnings("serial")
public class SubsDictError extends RuntimeException {
  public SubsDictError() { super(); }
  public SubsDictError(String message) { super(message); }
  public SubsDictError(String message, Throwable cause) { super(message, cause); }
  public SubsDictError(Throwable cause) { super(cause); }
}