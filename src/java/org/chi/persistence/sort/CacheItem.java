package org.chi.persistence.sort;


/**
 * Holds extract values from the XML for comparison
 * @author rgrey
 */
class CacheItem {

    String[] ids;
    String[] keys;
    String[] values;
    boolean[] pass;

    /**
     * Default
     */
    public CacheItem() {}

    /**
     * Initialize with a certain size
     * @param size
     */
    public CacheItem(int size) {
        ids = new String[size];
        keys = new String[size];
        values = new String[size];
        pass = new boolean[size];
    }

    /**
     * Set values if not current
     * @param node
     * @param val
     * @param mustpass does this have to be completed with the pass method?
     * @return null if value already set
     */
    SearchNode set(SearchNode node, String val, boolean mustpass) { 
        for (Integer x : node.locations) {
            if (values[x] != null) continue;
            ids[x] = node.id;
            keys[x] = node.name;
            values[x] = val;
            if (! mustpass) pass[x] = true;
        }
        return node;
    }

    /**
     * Pass a predicate
     * @param node
     */
    void pass(SearchNode node) {
        pass[node.predlocation] = true;
    }

    /**
     * Clear values that do not pass (typically for predicate)
     */
    void clearFailed() {
        for (int x = 0 ; x < values.length ; x++) {
            if (pass[x]) continue;
            ids[x] = null;
            keys[x] = null;
            values[x] = null;
        }
    }

    public int compareTo(CacheItem compare) {
        if (values == null) return -1;
        for (int x = 0 ; x < values.length ; x++) {
            if (values[x] == null) return -1;
            if (compare.values[x] == null) return 1;
            int chk = values[x].compareToIgnoreCase(compare.values[x]);
            if (chk != 0) return chk;
        }
        return 0;
    }

    @Override
    public String toString() {
        return java.util.Arrays.toString(values);
    }

}
