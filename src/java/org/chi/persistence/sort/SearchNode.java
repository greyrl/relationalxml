package org.chi.persistence.sort;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree representation of search fields. 
 * @author rgrey
 */
class SearchNode {

    // standard search node fields
    String id;
    String name = "";
    ArrayList<Integer> locations;

    // predicate fields
    int predlocation;
    boolean att;
    boolean negate;
    String val = "";
    boolean variable = false;
    boolean inKey = true;

    // indent for toString
    int ind = 0;

    SearchNode parent;
    List<SearchNode> children = new ArrayList<SearchNode>();
    ArrayList<SearchNode> predicate = new ArrayList<SearchNode>();

    /**
     * Default
     */
    public SearchNode() {}

    /**
     * Constructor with an associated parent
     * @param parent
     */
    public SearchNode(SearchNode parent) {
        setParent(parent);
    }

    /**
     * Constructor with an associated parent an indentation
     * @param parent
     * @param ind
     */
    public SearchNode(SearchNode parent, int ind) {
        setParent(parent);
        this.ind = ind;
    }

    /**
     * Set the parent for this node and attach as a child
     * @param parent
     */
    public void setParent(SearchNode parent) {
        this.parent = parent;
        parent.children.add(this);
    }

    /**
     * Process a standard character
     * @param _char
     */
    public void addChar(char _char) {
        if (inKey) name += _char;
        else val += _char;
    }

    /**
     * Does this have a location set
     * @return
     */
    boolean hasLocation() { return locations != null; }

    /**
     * Add a location terminator to this node
     * @param loc
     */
    void addLocation(int loc) {
        if (locations == null) locations = new ArrayList<Integer>();
        locations.add(loc);
    }

    /**
     * Extract the first part of a predicate if there are any predicate
     * parents
     * @return null if no parents
     */
    SearchNode front() {
        return parent.predicate.size() > 0 ? null : recursePred(this);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(
                "val [" + name + "], " + children.size() + " kid(s)");
        if (hasLocation()) result.append(", locations " + locations);
        if (predicate.size() > 0) { 
            result.append(", predicates [");
            for (SearchNode pred : predicate) result.append(pred.getPredStr());
            result.append(']');
        }
        for (SearchNode child : children) {
            result.append("\n");
            for (int x = 0 ; x < ind ; x++) result.append(' ');
            result.append(child);
        }
        return result.toString();
    }

    /**
     * Print a predicate as a string
     * @return
     */
    private String getPredStr() {
        if (children.size() > 0) return name + "/" + children.get(0).getPredStr();
        else return (att ? "@" + name : name) + (negate ? "!=" : "=") + val + 
            " on " + predlocation + ", ";
    }

    /**
     * Complete predicate
     * @param params
     * @param location
     */
    SearchNode finalize(String[] params, int location) {
        name = name.trim();
        val = val.trim();
        predlocation = location;
        if (variable) val = params[Integer.parseInt(val) - 1];
        return this;
    }

    /**
     * Recursively extract the front of a predicate
     * @param pred
     * @return
     */
    private SearchNode recursePred(SearchNode pred) {
        SearchNode parent = pred.parent;
        return parent.predicate.size() > 0 ? pred : recursePred(parent);
    }

}

