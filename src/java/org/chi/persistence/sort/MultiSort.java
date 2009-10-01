package org.chi.persistence.sort;

import groovy.util.Node;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

import org.chi.util.Log;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * Sort by multiple fields
 * @auther rgrey
 */
class MultiSort implements Comparator<Object> {

    private XMLReader reader;
    private HashMap<String, CacheItem> cache = new HashMap<String, CacheItem>();

    /**
     * Constructor with XML reader
     * @param reader
     */
    public MultiSort(XMLReader reader) {
        this.reader = reader;
    }

    /**
     * Sort the elements from a query
     * @param query
     * @param list
     * @param params
     * @return
     */
    public static List<?> sort(Node query, List<?> list, 
            String[] params) throws Exception {
        SearchNode search = new SearchNode();
        int count = 0;
        long timing = System.currentTimeMillis();
        for (Object next : query.children()) {
            if (! (next instanceof Node)) continue;
            if (! "sort".equals(((Node) next).name())) continue;
            for (Object child : ((Node) next).children()) {
                parse(search, ((Node) child).text(), count, params);
            }
            count++;
        }
        if (count == 0) return list;
        //Log.debug("MultiSort.sort() : search\n" + search);
        XMLReader reader = 
            SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        reader.setContentHandler(new ItemHandler(search, count));
        timing = Log.timing("MultiSort.sort", timing, "prep");
        MultiSort ms = new MultiSort(reader);
        Collections.sort(list, ms);
        timing = Log.timing("MultiSort.sort", timing, "sort");
        if (query.attribute("group") == null) return list;
        String last = "";
        List<Object> grouped = new ArrayList<Object>();
        if (list.size() == 1) ms.gen(list.get(0));
        for (Object val : list) {
            CacheItem item = ms.cache.get(getField(val, "getId"));
            String id = item.ids[0];
            String first = item.values[0];
            if (! last.equals(first)) {  
                grouped.add(new Group(id, first, item.keys[0]));
                last = first != null ? first : "";
            }
            grouped.add(val);
        }
        return grouped;
    }

    /**
     * Parse a search string
     * @param root root search node
     * @param next
     * @param location the save location in the result array
     * @param p substitution params
     */
    static void parse(SearchNode root, String next, int location, String[] p) {
        SearchNode cur = new SearchNode(root, 1);
        for (char _char : next.toCharArray()) {
            switch(_char) {
                case '/': 
                    cur = new SearchNode(findDupe(cur), cur.ind + 1);
                    break;
                case '[': cur = createPredicate(cur) ; break;
                case '@': cur.att = true ; break;
                case '!': cur.negate = true ; break;
                case '=': cur.inKey = ! cur.inKey ; break;
                case '$': cur.variable = true;
                case '"': 
                case '\'': cur.inKey = false ; break;
                case ']': 
                    cur = predicateParent(cur.finalize(p, location)) ; break;
                default: cur.addChar(_char);
            }
        }
        findDupe(cur).addLocation(location);
    }

    public int compare(Object a, Object b) {
        //Log.debug("MultiSort.compare() : [" + gen(a) + "] with [" + gen(b) + "]");
        return gen(a).compareTo(gen(b));
    }

    /**
     * Find a duplicate of a SeachNode and remove it from parents
     * @param child
     * @return the duplicate value or the original child
     */
    private static SearchNode findDupe(SearchNode child) {
        SearchNode dupe = null;
        for (SearchNode it : child.parent.children) {
            if (it == child) continue;
            if (it != null && child.name.equals(it.name)) dupe = it;
        }
        return dupe != null ? appendPredicates(child, dupe) : child;
    }

    /**
     * Create and attach a predicate
     * @param parent
     * @return
     */
    private static SearchNode createPredicate(SearchNode parent) {
        SearchNode pred = new SearchNode(parent);
        parent.predicate.add(pred);
        parent.children.remove(pred);
        return pred;
    }

    /**
     * Append predicates from a duplicate
     * @param from
     * @param to
     * @return the original
     */
    private static SearchNode appendPredicates(SearchNode from, SearchNode to) {
        from.parent.children.remove(from);
        for (SearchNode pred : from.predicate) to.predicate.add(pred);
        return to;
    }

    /**
     * Traverse the parents until the predicate parent is found
     * @param p
     * @return
     */
    private static SearchNode predicateParent(SearchNode p) {
        while (p != null && ! p.parent.predicate.contains(p)) p = p.parent;
        return p.parent;
    }

    /**
     * Generate the sort string
     * @param obj
     * @throws Exception
     */
    private CacheItem gen(Object obj) {
        if (obj == null) return new CacheItem();
        try {
            String id = getField(obj, "getId");
            CacheItem result = cache.get(id);
            if (result != null) return result;
            // TODO generate XML if database cache is not present...
            byte[] cxml = getField(obj, "getXmlcache").getBytes();
            reader.parse(new InputSource(new ByteArrayInputStream(cxml)));
            cache.put(id, ((ItemHandler) reader.getContentHandler()).item);
            return cache.get(id);
        } catch (Exception e) {
            Log.exception("Unable to generate cache item", e);
            return new CacheItem();
        }
    }

    /**
     * Dynamically retrieve a field from an object by getter
     * @param obj
     * @param getter method name
     * @return
     */
    private static String getField(Object obj, String getter) throws Exception {
        Method method = obj.getClass().getMethod(getter);
        return method.invoke(obj).toString();
    }

}
