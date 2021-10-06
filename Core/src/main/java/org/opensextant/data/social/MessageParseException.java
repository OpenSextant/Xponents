package org.opensextant.data.social;

public class MessageParseException extends Exception {

    private static final long serialVersionUID = 1L;

    public MessageParseException(String msg, Exception e) {
        super(msg, e);
    }

    public MessageParseException(String msg) {
        super(msg);
    }
}
