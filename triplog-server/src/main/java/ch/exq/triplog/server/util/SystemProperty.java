package ch.exq.triplog.server.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.Alternative;

/**
 * User: Nicolas Oeschger <noe@exq.ch>
 * Date: 04.04.14
 * Time: 09:03
 */
@Alternative
public class SystemProperty {

    private final static Logger logger = LoggerFactory.getLogger(SystemProperty.class);

    private String key;
    private String fallback;

    //Only used for tests!
    SystemProperty() {}

    public SystemProperty(Config config) {
        this.key = config.key();
        this.fallback = config.fallback();
    }

    public String getString() {
        String value = System.getProperty(key);

        if (value == null) {
            logger.debug("System property '{}' not set. Fallback to '{}'", key, fallback);
            value = fallback;
        }

        return value;
    }

    public Integer getInteger() {
        return isNullOrEmpty() ? null : Integer.valueOf(getString());
    }

    public Long getLong() {
        return isNullOrEmpty() ? null : Long.valueOf(getString());
    }

    public boolean getBoolean() {
        return isNullOrEmpty() ? false : "true".equalsIgnoreCase(getString());
    }

    public boolean isNullOrEmpty() {
        return getString() == null || "".equals(getString());
    }
}
