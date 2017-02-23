package net.fortytwo.smsn.server.actions;

import net.fortytwo.smsn.SemanticSynchrony;
import net.fortytwo.smsn.brain.model.entities.Atom;
import net.fortytwo.smsn.server.errors.BadRequestException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SetPropertiesTest extends ActionTestBase {
    private Atom atom;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        topicGraph.begin();
        atom = topicGraph.createAtom(null);
        atom.setTitle("before");
        atom.setText("the page");
        topicGraph.commit();

        atom = topicGraph.getAtomById(atom.getId());
        assertEquals("before", atom.getTitle());
        assertEquals("the page", atom.getText());
    }

    @Test
    public void titleIsSetCorrectly() throws Exception {

        SetProperties action = new SetProperties();
        action.setId(atom.getId());
        action.setName(SemanticSynchrony.PropertyKeys.TITLE);
        action.setValue("after");

        perform(action);

        atom = topicGraph.getAtomById(atom.getId());
        assertEquals("after", atom.getTitle());
    }

    @Test(expected = BadRequestException.class)
    public void emptyTitleIsError() throws Exception {

        SetProperties action = new SetProperties();
        action.setId(atom.getId());
        action.setName(SemanticSynchrony.PropertyKeys.TITLE);
        action.setValue("  \n");

        perform(action);
    }

    @Test
    public void pageIsSetCorrectly() throws Exception {

        SetProperties action = new SetProperties();
        action.setId(atom.getId());
        action.setName(SemanticSynchrony.PropertyKeys.PAGE);
        action.setValue("after");

        perform(action);

        atom = topicGraph.getAtomById(atom.getId());
        assertEquals("after", atom.getText());
    }

    @Test
    public void emptyPageBecomesNullPage() throws Exception {

        SetProperties action = new SetProperties();
        action.setId(atom.getId());
        action.setName(SemanticSynchrony.PropertyKeys.PAGE);
        action.setValue("  \n ");

        perform(action);

        atom = topicGraph.getAtomById(atom.getId());
        assertNull(atom.getText());
    }

    @Test
    public void weightIsSetCorrectly() throws Exception {
        topicGraph.begin();
        Atom atom = topicGraph.createAtom(null);
        atom.setTitle("test");
        atom.setWeight(0.25f);
        topicGraph.commit();

        atom = topicGraph.getAtomById(atom.getId());
        assertEquals(0.25f, atom.getWeight(), 0.0f);

        SetProperties action = new SetProperties();
        action.setId(atom.getId());
        action.setName(SemanticSynchrony.PropertyKeys.WEIGHT);
        action.setValue(0.5);

        perform(action);

        atom = topicGraph.getAtomById(atom.getId());
        assertEquals(0.5f, atom.getWeight(), 0.0f);
    }

    @Test
    public void sharabilityIsSetCorrectly() throws Exception {
        topicGraph.begin();
        Atom atom = topicGraph.createAtom(null);
        atom.setTitle("test");
        atom.setSharability(0.25f);
        topicGraph.commit();

        atom = topicGraph.getAtomById(atom.getId());
        assertEquals(0.25f, atom.getSharability(), 0.0f);

        SetProperties action = new SetProperties();
        action.setId(atom.getId());
        action.setName(SemanticSynchrony.PropertyKeys.SHARABILITY);
        action.setValue(0.5);

        perform(action);

        atom = topicGraph.getAtomById(atom.getId());
        assertEquals(0.5f, atom.getSharability(), 0.0f);
    }

    @Test
    public void priorityIsSetCorrectly() throws Exception {
        topicGraph.begin();
        Atom atom = topicGraph.createAtom(null);
        atom.setTitle("test");
        atom.setPriority(0.25f);
        topicGraph.commit();

        atom = topicGraph.getAtomById(atom.getId());
        assertEquals(0.25f, atom.getPriority(), 0.0f);

        SetProperties action = new SetProperties();
        action.setId(atom.getId());
        action.setName(SemanticSynchrony.PropertyKeys.PRIORITY);
        action.setValue(0.5);

        perform(action);

        atom = topicGraph.getAtomById(atom.getId());
        assertEquals(0.5f, atom.getPriority(), 0.0f);
    }

    @Test
    public void shortcutIsSetCorrectly() throws Exception {
        topicGraph.begin();
        Atom atom = topicGraph.createAtom(null);
        atom.setTitle("test");
        topicGraph.commit();

        atom = topicGraph.getAtomById(atom.getId());
        assertNull(atom.getShortcut());

        SetProperties action = new SetProperties();
        action.setId(atom.getId());
        action.setName(SemanticSynchrony.PropertyKeys.SHORTCUT);
        action.setValue("after");

        perform(action);

        atom = topicGraph.getAtomById(atom.getId());
        assertEquals("after", atom.getShortcut());
    }
}