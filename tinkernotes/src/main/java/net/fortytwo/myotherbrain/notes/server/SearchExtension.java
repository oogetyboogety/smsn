package net.fortytwo.myotherbrain.notes.server;

import com.tinkerpop.blueprints.pgm.Graph;
import com.tinkerpop.rexster.RexsterResourceContext;
import com.tinkerpop.rexster.extension.ExtensionDefinition;
import com.tinkerpop.rexster.extension.ExtensionDescriptor;
import com.tinkerpop.rexster.extension.ExtensionNaming;
import com.tinkerpop.rexster.extension.ExtensionPoint;
import com.tinkerpop.rexster.extension.ExtensionRequestParameter;
import com.tinkerpop.rexster.extension.ExtensionResponse;
import com.tinkerpop.rexster.extension.RexsterContext;
import net.fortytwo.myotherbrain.notes.Filter;
import net.fortytwo.myotherbrain.notes.Note;
import org.json.JSONException;
import org.json.JSONObject;

import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
@ExtensionNaming(namespace = "tinkernotes", name = "search")
public class SearchExtension extends TinkerNotesExtension {

    @ExtensionDefinition(extensionPoint = ExtensionPoint.GRAPH)
    @ExtensionDescriptor(description = "an extension for performing full text search over MyOtherBrain using TinkerNotes")
    public ExtensionResponse handleRequest(@RexsterContext RexsterResourceContext context,
                                           @RexsterContext Graph graph,
                                           @ExtensionRequestParameter(name = "query", description = "full-text query") String query,
                                           @ExtensionRequestParameter(name = "depth", description = "depth of the view") Integer depth,
                                           @ExtensionRequestParameter(name = "minWeight", description = "minimum-weight criterion for atoms in the view") Float minWeight,
                                           @ExtensionRequestParameter(name = "maxWeight", description = "maximum-weight criterion for atoms in the view") Float maxWeight,
                                           @ExtensionRequestParameter(name = "minSharability", description = "minimum-sharability criterion for atoms in the view") Float minSharability,
                                           @ExtensionRequestParameter(name = "maxSharability", description = "maximum-sharability criterion for atoms in the view") Float maxSharability,
                                           @ExtensionRequestParameter(name = "style", description = "the style of view to generate") String styleName) {
        try {
            // TODO: this doesn't solve the problem (that you can't search on queries with extended characters)
            query = new String(query.getBytes(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }

        LOGGER.info("tinkernotes search \"" + query + "\"");
        System.err.println("tinkernotes search \"" + query + "\"");

        SecurityContext security = context.getSecurityContext();
        Principal user = null == security ? null : security.getUserPrincipal();

        Filter filter;

        try {
            float m = findMinAuthorizedSharability(user, minSharability);
            filter = new Filter(m, maxSharability, -1, minWeight, maxWeight, -1);
        } catch (IllegalArgumentException e) {
            return ExtensionResponse.error(e.getMessage());
        }

        Params p = new Params();
        p.baseGraph = graph;
        p.filter = filter;
        p.query = query;
        p.depth = depth;
        p.styleName = styleName;
        return this.handleRequestInternal(p);
    }

    @Override
    protected ExtensionResponse performTransaction(final Params p) throws Exception {
        addSearchResults(p);

        p.map.put("title", p.query);
        return ExtensionResponse.ok(p.map);
    }

    @Override
    protected boolean isReadOnly() {
        return true;
    }

    protected void addSearchResults(final Params p) throws IOException {
        Note n = p.semantics.search(p.query, p.depth, p.filter, p.inverse);
        JSONObject json;

        try {
            json = p.syntax.toJSON(n);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        p.map.put("view", json.toString());
    }
}
