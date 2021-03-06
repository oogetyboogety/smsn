package net.fortytwo.smsn.brain.rdf.classes;

import net.fortytwo.smsn.brain.rdf.SimpleAtomClass;

import java.util.regex.Pattern;

public class RFIDReference extends SimpleAtomClass {

    public RFIDReference() {
        super(
                "rfid",
                Pattern.compile("RFID: [0-9A-F]{4} [0-9A-F]{4} [0-9A-F]{4} [0-9A-F]{4} [0-9A-F]{4} [0-9A-F]{4}"),
                null,
                null
                );
    }
}
