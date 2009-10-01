package org.chi.persistence.sort;

import java.util.HashMap;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/** 
 * SAX parser for generating sort fields
 * @author rgrey
 */
class ItemHandler extends DefaultHandler {

    CacheItem item;
    SearchNode search;
    int total;

    private SearchNode cur; // the working node
    private HashMap<String, List<SearchNode>> preds = 
        new HashMap<String, List<SearchNode>>(); // predicates

    /**
     * Constructor with root search node and total number of search options
     * @param search
     * @param total
     */
    public ItemHandler(SearchNode search, int total) {
        this.search = search;
        this.total = total;
    }

    @Override
    public void startDocument() {
        cur = search;
        item = new CacheItem(total);
        preds.clear();
    }

    // TODO handle elements in predicate and attribute search field match
    @SuppressWarnings("unchecked")
    @Override
    public void startElement(String ns, String l, String q, Attributes atts) {
        SearchNode next = null;
        for (SearchNode kid : cur.children) if (kid.name.equals(q)) next = kid;
        if (next != null && next.predicate.size() > 0) 
            preds.put(q, (List<SearchNode>) next.predicate.clone());
        if (next != null) 
            next.id = atts.getValue("id") != null ? atts.getValue("id") : cur.id;
        if (next != null) cur = next;
        checkPredicate(q, atts);
    }

    @Override
    public void characters(char[] chars, int off, int len) {
        if (cur.hasLocation()) 
            item.set(cur, new String(chars, off, len), preds.size() > 0);
    }

    @Override
    public void endElement(String ns, String localName, String qn) { 
        if (qn.equals(cur.name)) cur = cur.parent;
        if (preds.remove(qn) != null) item.clearFailed(); 
    }

    /**
     * Evaluate a predicate if it exists
     * @param check
     * @param atts
     */
    private void checkPredicate(String check, Attributes atts) {
        if (preds.size() == 0) return;
        for (List<SearchNode> pred : preds.values()) {
            for (int x = 0 ; x < pred.size() ; x++) {
                SearchNode p = pred.get(x);
                if (p == null) return;
                if (check.equals(p.name) && p.children.size() > 0) {
                    pred.set(x, p.children.get(0));
                    p = pred.get(x);
                }
                if (p.val == null || p.val.equals("")) continue;
                boolean pass = p.val.equals(atts.getValue(p.name)) != p.negate;
                if (pass) item.pass(p);
                else pred.set(x, p.front());
            }
        }
    }

}

