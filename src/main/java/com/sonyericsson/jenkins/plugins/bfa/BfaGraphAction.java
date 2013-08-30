package com.sonyericsson.jenkins.plugins.bfa;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.kohsuke.stapler.StaplerRequest;
import com.sonyericsson.jenkins.plugins.bfa.graphs.GraphCache;
import hudson.model.ModelObject;
import hudson.model.RootAction;
import hudson.util.Graph;


/**
 * Abstract class to handle the detailed graphs pages.
 * @author Christoffer Lauri &lt;christoffer.lauri@sonymobile.com&gt;
 *
 */
public abstract class BfaGraphAction implements RootAction {
    /**
     * Url-parameter for indicating time period to show in a graph.
     */
    protected static final String URL_PARAM_TIME_PERIOD = "time";
    /**
     * Url-parameter for indicating which graph to show.
     */
    protected static final String URL_PARAM_WHICH_GRAPH = "which";
    /**
     * Url-parameter for indicating whether to show or hide aborted builds.
     */
    protected static final String URL_PARAM_SHOW_ABORTED = "showAborted";

    /**
     * Url-parameter for indicating whether to show for all masters or not.
     */
    protected static final String URL_PARAM_ALL_MASTERS = "allMasters";

    /**
     * Url-parameter value for 'today'.
     */
    protected static final String URL_PARAM_VALUE_TODAY = "today";

    /**
     * Url-parameter value for 'month'.
     */
    protected static final String URL_PARAM_VALUE_MONTH = "month";

    /**
     * Url-parameter value for 'max'.
     */
    protected static final String URL_PARAM_VALUE_MAX = "max";

    /**
     * Default width for graphs on detail pages.
     */
    protected static final int DEFAULT_GRAPH_WIDTH = 700;

    /**
     * Default height for graphs on detail pages.
     */
    protected static final int DEFAULT_GRAPH_HEIGHT = 500;

    /**
     * Constant for small bar chart.
     */
    protected static final int BAR_CHART_CAUSES_SMALL = 1;

    /**
     * Constant for bar chart with {@link FailureCause}s.
     */
    protected static final int BAR_CHART_CAUSES = 2;

    /**
     * Constant for bar chart with categories.
     */
    protected static final int BAR_CHART_CATEGORIES = 3;

    /**
     * Constant for bar chart with build numbers.
     */
    protected static final int BAR_CHART_BUILD_NBRS = 4;

    /**
     * Constant for pie chart with {@link FailureCause}s.
     */
    protected static final int PIE_CHART_CAUSES = 5;

    /**
     * Constant for pie chart with categories.
     */
    protected static final int PIE_CHART_CATEGORIES = 6;

    /**
     * Constant for time series chart with {@link FailureCause}s.
     */
    protected static final int TIME_SERIES_CHART_CAUSES = 7;

    /**
     * Constant for time series chart with categories.
     */
    protected static final int TIME_SERIES_CHART_CATEGORIES = 8;

    /**
     * Constant for time series chart displaying unknown failure causes.
     */
    protected static final int TIME_SERIES_UNKNOWN_FAILURES = 9;

    /**
     * Constant for "ABORTED"-cause (used to exclude such {@link FailureCause}s).
     */
    protected static final String EXCLUDE_ABORTED = "ABORTED";

    /**
     * Get the owner.
     * @return The owner
     */
    public abstract ModelObject getOwner();

    /**
     * Returns an array of numbers, where each number represents
     * a graph. These are the numbers used to display the graphs/images
     * on the detailed graphs page, that is, they will be the 'which'-parameter
     * to getGraph(int which, Date ...).
     * The graphs are displayed in the same order as the numbers in the array.
     * @return An array of integers where each integer represents a graph to
     * display
     */
    public abstract int[] getGraphNumbers();

    /**
     * Get the title to display in the top of the detailed graphs page.
     * @return The title as a String
     */
    public abstract String getGraphsPageTitle();

    /**
     * Get the graph corresponding to the specified arguments.
     * @param which Which graph to display
     * @param timePeriod How old statistics should be included in the graph
     * @param hideManAborted Hide manually aborted causes
     * @param allMasters Show for all masters
     * @param rawReqParams The url parameters that came with the request
     * @return A Graph
     */
    protected abstract Graph getGraph(int which, Date timePeriod,
            boolean hideManAborted, boolean allMasters,
            Map<String, String> rawReqParams);

    /**
     * Get the Graph corresponding to the url-parameters.
     * Parameters:
     * - time : how far back should statistics be included
     * - which : which graph to display
     * - showAborted : show manually aborted
     * - allMasters : show for all masters
     * @param req The StaplerRequest
     * @return A graph
     */
    public Graph getGraph(StaplerRequest req) {
        final Map<String, String> rawReqParams = new HashMap<String, String>();

        String reqTimePeriod = req.getParameter(URL_PARAM_TIME_PERIOD);
        if (reqTimePeriod == null || !reqTimePeriod.matches(URL_PARAM_VALUE_MONTH + "|" + URL_PARAM_VALUE_MAX)) {
            reqTimePeriod = URL_PARAM_VALUE_TODAY; // The default value
        }
        rawReqParams.put(URL_PARAM_TIME_PERIOD, reqTimePeriod);

        String reqWhich = req.getParameter(URL_PARAM_WHICH_GRAPH);
        rawReqParams.put(URL_PARAM_WHICH_GRAPH, reqWhich);

        String showAborted = req.getParameter(URL_PARAM_SHOW_ABORTED);
        rawReqParams.put(URL_PARAM_SHOW_ABORTED, showAborted);

        String allMasters = req.getParameter(URL_PARAM_ALL_MASTERS);
        rawReqParams.put(URL_PARAM_ALL_MASTERS, allMasters);

        final Date sinceDate = getDateForUrlStr(reqTimePeriod);
        int tmpWhichGraph =  -1;
        try {
            tmpWhichGraph = Integer.parseInt(reqWhich);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        final int whichGraph = tmpWhichGraph;
        final boolean hideAborted = "0".equals(showAborted);
        final boolean forAllMasters = "1".equals(allMasters);

        String id = getGraphCacheId(whichGraph, reqTimePeriod, hideAborted, forAllMasters);
        Graph graphToReturn = null;
        try {
            graphToReturn = GraphCache.getInstance().get(id, new Callable<Graph>() {
                @Override
                public Graph call() throws Exception {
                    // The requested graph isn't cached, so create a new one.
                    Graph g = getGraph(whichGraph, sinceDate, hideAborted, forAllMasters, rawReqParams);
                    if (g != null) {
                        return g;
                    }
                    // According to documentation, null must not be returned; either
                    // a non-null value must be returned, or an an exception thrown
                    throw new ExecutionException("Graph-parameters not valid", null);
                } });
        } catch (ExecutionException e) {
            // An exception will occur when a graph cannot be generated,
            // e.g. when erroneous url-parameters have been specified
            e.printStackTrace();
        }
        return graphToReturn;
    }

    /**
     * Get a unique id used in the caching of the graph.
     * @param whichGraph Which graph
     * @param reqTimePeriod The selected time period
     * @param hideAborted Hide aborted builds
     * @param forAllMasters For all masters
     * @return An id corresponding to the specified arguments
     */
    protected abstract String getGraphCacheId(int whichGraph,
            String reqTimePeriod, boolean hideAborted, boolean forAllMasters);

    /**
     * Helper for groovy-views; Get the default width of graphs on detailed pages.
     * @return The default height of graphs on the detailed graphs-page
     */
    public int getDefaultGraphWidth() {
        return DEFAULT_GRAPH_WIDTH;
    }

    /**
     * Helper for groovy-views; Get the default height of graphs on detailed pages.
     * @return The default height of graphs on the detailed graphs-page
     */
    public int getDefaultGraphHeight() {
        return DEFAULT_GRAPH_HEIGHT;
    }

    /**
     * Helper for the groovy-views; show/hide Masters-switch.
     * Whether to show links for switching between all masters
     * and the own master.
     * @return True to show the switch, otherwise false
     */
    public boolean showMasterSwitch() {
        return false;
    }

    /**
     * Get a Date object corresponding to the specified string.
     * (today|month).
     * @param str The String
     * @return A Date, or null if not equal to "today" or "month"
     */
    private Date getDateForUrlStr(String str) {
        Calendar cal = Calendar.getInstance();
        Date date = null;
        if (URL_PARAM_VALUE_TODAY.equals(str)) {
           cal.add(Calendar.DAY_OF_YEAR, -1);
           date = cal.getTime();
        } else if (URL_PARAM_VALUE_MONTH.equals(str)) {
            cal.add(Calendar.MONTH, -1);
            date = cal.getTime();
        } // max time => return null
        return date;
     }
}
