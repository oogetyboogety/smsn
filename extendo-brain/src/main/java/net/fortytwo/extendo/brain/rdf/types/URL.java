package net.fortytwo.extendo.brain.rdf.types;

import java.util.regex.Pattern;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class URL extends SimpleSecondClassType {
    public static final URL INSTANCE = new URL();

    private URL() {
        super("url");
    }

    public Pattern getValueRegex() {
        return Pattern.compile("http(s)?://.+");
    }

    public boolean additionalConstraintsSatisfied(final String value) {
        return true;
    }
}
