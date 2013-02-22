package net.fortytwo.myotherbrain.notes.server;

import com.tinkerpop.blueprints.KeyIndexableGraph;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.rexster.RexsterResourceContext;
import com.tinkerpop.rexster.extension.AbstractRexsterExtension;
import com.tinkerpop.rexster.extension.ExtensionResponse;
import net.fortytwo.myotherbrain.Atom;
import net.fortytwo.myotherbrain.MOBGraph;
import net.fortytwo.myotherbrain.notes.Filter;
import net.fortytwo.myotherbrain.notes.Note;
import net.fortytwo.myotherbrain.notes.NoteHistory;
import net.fortytwo.myotherbrain.notes.NoteParser;
import net.fortytwo.myotherbrain.notes.NoteQueries;
import net.fortytwo.myotherbrain.notes.NoteWriter;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpSession;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public abstract class TinkerNotesExtension extends AbstractRexsterExtension {
    protected static final Logger LOGGER = Logger.getLogger(TinkerNotesExtension.class.getName());

    private static final String HISTORY_ATTR = "history";

    protected abstract ExtensionResponse performTransaction(Params p) throws Exception;

    protected abstract boolean doesRead();

    protected abstract boolean doesWrite();

    protected Params createParams(final RexsterResourceContext context,
                                  final KeyIndexableGraph graph) {
        Params p = new Params();
        p.baseGraph = graph;
        p.context = context;
        SecurityContext security = p.context.getSecurityContext();
        p.user = null == security ? null : security.getUserPrincipal();

        if (null == p.user) {
            logWarning("no security");
        }

        return p;
    }

    protected ExtensionResponse handleRequestInternal(final Params p) {


        if (doesWrite() && !canWrite(p.user)) {
            return ExtensionResponse.error("user does not have permission to for write operations");
        }

        if (doesRead() && null == p.filter) {
            return ExtensionResponse.error("weight and sharability filter is not set");
        }

        String rootKey = p.rootId;
        String styleName = p.styleName;

        try {
            p.map = new HashMap<String, Object>();

            if (!(p.baseGraph instanceof KeyIndexableGraph)) {
                return ExtensionResponse.error("graph must be an instance of IndexableGraph");
            }

            if (null != p.view) {
                // Force the use of the UTF-8 charset, which is apparently not chosen by Jersey
                // even when it is specified by the client in the Content-Type header, e.g.
                //    Content-Type: application/x-www-form-urlencoded;charset=UTF-8
                p.view = new String(p.view.getBytes("UTF-8"));
            }

            p.manager = new FramedGraph<KeyIndexableGraph>(p.baseGraph);
            p.graph = MOBGraph.getInstance(p.baseGraph);
            p.queries = new NoteQueries(p.graph);
            p.parser = new NoteParser();
            p.writer = new NoteWriter();

            if (null != p.depth) {
                if (p.depth < 1) {
                    return ExtensionResponse.error("depth must be at least 1");
                }

                if (p.depth > 5) {
                    return ExtensionResponse.error("depth may not be more than 5");
                }

                p.map.put("depth", "" + p.depth);
            }

            if (null != p.filter) {
                p.map.put("minSharability", "" + p.filter.getMinSharability());
                p.map.put("maxSharability", "" + p.filter.getMaxSharability());
                p.map.put("defaultSharability", "" + p.filter.getDefaultSharability());
                p.map.put("minWeight", "" + p.filter.getMinWeight());
                p.map.put("maxWeight", "" + p.filter.getMaxWeight());
                p.map.put("defaultWeight", "" + p.filter.getDefaultWeight());
            }

            if (null != rootKey) {
                p.root = p.graph.getAtom(rootKey);

                if (null == p.root) {
                    return ExtensionResponse.error("root of view does not exist: " + rootKey);
                }

                if (null != p.filter && !p.filter.isVisible(p.root)) {
                    return ExtensionResponse.error("root of view is not visible: " + rootKey);
                }

                p.map.put("root", rootKey);
            }

            p.map.put("title", null == p.root || null == p.root.getValue() || 0 == p.root.getValue().length() ? "[no title]" : p.root.getValue());

            if (null != styleName) {
                p.style = NoteQueries.lookupStyle(styleName);
                p.map.put("style", p.style.getName());
            }

            boolean manual;
            // Force manual transaction mode (provided that the graph is transactional)
            if (doesWrite() && p.baseGraph instanceof TransactionalGraph) {
                manual = true;
            } else {
                manual = false;
            }

            boolean normal = false;

            try {
                ExtensionResponse r = performTransaction(p);
                normal = true;

                // Note: currently, all activities are logged, but the log is not immediately flushed
                //       unless the transaction succeeds.
                if (null != p.graph.getActivityLog()) {
                    p.graph.getActivityLog().flush();
                }

                return r;
            } finally {
                if (doesWrite()) {
                    if (manual) {
                        if (!normal) {
                            logWarning("rolling back transaction");
                        }

                        ((TransactionalGraph) p.baseGraph).stopTransaction(normal
                                ? TransactionalGraph.Conclusion.SUCCESS
                                : TransactionalGraph.Conclusion.FAILURE);
                    } else if (!normal) {
                        logWarning("failed update of non-transactional graph. Data integrity is not guaranteed");
                    }
                }
            }
        } catch (Exception e) {
            logWarning("operation failed: " + e.getMessage());
            // TODO
            e.printStackTrace(System.err);
            return ExtensionResponse.error(e);
        }
    }

    protected Filter createFilter(final Principal user,
                                  final float minWeight,
                                  final float maxWeight,
                                  final float defaultWeight,
                                  final float minSharability,
                                  final float maxSharability,
                                  final float defaultSharability) {

        float m = findMinAuthorizedSharability(user, minSharability);
        return new Filter(minWeight, maxWeight, defaultWeight,
                m, maxSharability, defaultSharability);
    }

    protected org.codehaus.jettison.json.JSONObject toJettison(JSONObject j) throws IOException {
        try {
            return new org.codehaus.jettison.json.JSONObject(j.toString());
        } catch (org.codehaus.jettison.json.JSONException e) {
            throw new IOException(e);
        }
    }

    protected void addView(final Note n,
                           final Params p) throws IOException {
        JSONObject json;

        try {
            json = p.writer.toJSON(n);
        } catch (JSONException e) {
            throw new IOException(e);
        }

        p.map.put("view", toJettison(json));
    }

    protected float findMinAuthorizedSharability(final Principal user,
                                                 final float minSharability) {
        // TODO
        float minAuth = (null == user)
                ? 0.0f
                : !user.getName().equals("josh")
                ? 0.75f : 0;

        return Math.max(minSharability, minAuth);
    }

    protected boolean canWrite(final Principal user) {
        // TODO
        return null == user || user.getName().equals("josh");
    }

    private NoteHistory getNotesHistory(final RexsterResourceContext context) {
        HttpSession session = context.getRequest().getSession();
        NoteHistory h = (NoteHistory) session.getAttribute(HISTORY_ATTR);
        if (null == h) {
            h = new NoteHistory();
            session.setAttribute(HISTORY_ATTR, h);
        }

        return h;
    }

    protected void addToHistory(final String rootId,
                                final RexsterResourceContext context) {
        NoteHistory h = getNotesHistory(context);
        h.visit(rootId);
    }

    protected List<String> getHistory(final RexsterResourceContext context,
                                      final MOBGraph graph,
                                      final Filter filter) {
        NoteHistory h = getNotesHistory(context);
        return h.getHistory(100, true, graph, filter);
    }

    protected void logInfo(final String message) {
        LOGGER.info(message);
        //System.err.println(message);
    }

    protected void logWarning(final String message) {
        LOGGER.warning(message);
        //System.err.println(message);
    }

    protected class Params {
        public RexsterResourceContext context;
        public Principal user;
        public Map<String, Object> map;
        public KeyIndexableGraph baseGraph;
        public MOBGraph graph;
        public FramedGraph<KeyIndexableGraph> manager;
        public NoteQueries queries;
        public NoteParser parser;
        public NoteWriter writer;
        public Atom root;
        public Integer depth;
        public String view;
        public NoteQueries.AdjacencyStyle style;
        public Filter filter;
        public String query;
        public Float newWeight;
        public Float newSharability;
        public String rootId;
        public String styleName;
    }
}