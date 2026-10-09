package javax.security.sasl;

import java.io.IOException;

public class SaslException extends IOException {
    private static final long serialVersionUID = 4579593529334992520L;
    private Throwable _exception;

    public SaslException() {
        super();
    }

    public SaslException(String detail) {
        super(detail);
    }

    public SaslException(String detail, Throwable ex) {
        super(detail);
        this._exception = ex;
    }

    @Override
    public Throwable getCause() {
        return _exception;
    }
}
