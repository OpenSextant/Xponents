package org.opensextant.extraction;

/**
 *
 * @author ubaldino
 */
public class ConfigException extends Exception {

    protected static final long serialVersionUID = 20031981L;

    public ConfigException(String msg) {
        super(msg);
    }

    public ConfigException(String msg, Exception cause) {
        super(msg, cause);
    }
}
