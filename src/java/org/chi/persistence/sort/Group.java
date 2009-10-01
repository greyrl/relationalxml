package org.chi.persistence.sort;

/**
 * Marks segments in grouped results
 * @author rgrey
 */
class Group {

    String id;
    String name;
    String by;
    
    /**
     * Default constructor
     * @param id
     * @param name
     * @param by
     */
    Group(String id, String name, String by) {
        this.id = id;
        this.name = name;
        this.by = by;
    }

}
