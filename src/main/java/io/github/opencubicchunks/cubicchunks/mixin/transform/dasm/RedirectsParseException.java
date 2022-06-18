package io.github.opencubicchunks.cubicchunks.mixin.transform.dasm;

import java.io.IOException;

public class RedirectsParseException extends IOException {
    public RedirectsParseException(String message) {
        super(message);
    }
    public RedirectsParseException(String message, Throwable cause) {
        super(message, cause);
    }
    public RedirectsParseException(Throwable cause) {
        super(cause);
    }
}

