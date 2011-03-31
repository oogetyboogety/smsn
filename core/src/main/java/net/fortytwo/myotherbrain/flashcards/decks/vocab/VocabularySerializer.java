package net.fortytwo.myotherbrain.flashcards.decks.vocab;

import net.fortytwo.myotherbrain.flashcards.Card;
import net.fortytwo.myotherbrain.flashcards.Deck;
import net.fortytwo.myotherbrain.flashcards.db.CardSerializer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * User: josh
 * Date: 3/29/11
 * Time: 11:22 PM
 */
public class VocabularySerializer implements CardSerializer<String, String> {
    private final VocabularyDeck.Format format;

    public VocabularySerializer(VocabularyDeck.Format format) {
        this.format = format;
    }

    public String serialize(final Card<String, String> card) throws IOException {
        try {
            List<Term> defs = ((VocabularyCard) card).getDefinitions();

            JSONArray a = new JSONArray();
            for (Term t : defs) {
                a.put(t.toJson());
            }

            return a.toString(4);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public Card<String, String> deserialize(final String name,
                                            final Deck<String, String> deck,
                                            final String data) throws IOException {
        try {
            List<Term> defs = new LinkedList<Term>();
            JSONArray a = new JSONArray(data);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                defs.add(new Term(o));
            }

            return new VocabularyCard(name, deck, defs, format);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }
}
