package error;

@SuppressWarnings("serial")
public class UnexpectedError extends SubsDictError {
    public UnexpectedError() { super(); }
    public UnexpectedError(String message) { super(message); }
}