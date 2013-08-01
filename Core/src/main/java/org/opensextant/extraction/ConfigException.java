package org.opensextant.extraction;

/**
 *
 * @author ubaldino
 */
public class ConfigException extends Exception {

    public ConfigException(String msg) {
        super(msg);
    }

    public ConfigException(String msg, Exception cause) {
        super(msg, cause);
    }
}
