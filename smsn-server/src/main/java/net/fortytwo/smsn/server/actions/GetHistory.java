package net.fortytwo.smsn.server.actions;

import net.fortytwo.smsn.brain.model.entities.Atom;
import net.fortytwo.smsn.server.ActionContext;
import net.fortytwo.smsn.server.errors.BadRequestException;
import net.fortytwo.smsn.server.errors.RequestProcessingException;

import java.io.IOException;

/**
 * A service for finding recently visited atoms
 */
public class GetHistory extends FilteredAction {

    @Override
    protected void performTransaction(final ActionContext context) throws RequestProcessingException, BadRequestException {
        Iterable<Atom> atoms = getHistory(context.getBrain().getTopicGraph(), getFilter());

        try {
            addView(context.getQueries().customView(atoms, getFilter()), context);
        } catch (IOException e) {
            throw new RequestProcessingException(e);
        }
    }

    @Override
    protected boolean doesRead() {
        return true;
    }

    @Override
    protected boolean doesWrite() {
        return false;
    }
}
