package org.chi.persistence.util

import org.chi.util.IOUtils
import org.chi.util.Log

/**
 * Dynamically load queries from a file or stream
 * @author rgrey
 */
class Queries {

    private static final String key = "Queries.load() : "

    private String name
    private File file
    private long last = 0
    private NodeList queries 

    Queries(name) {
        this.name = name
        if (name) this.file = new File(name)
    }

    /**
     * Get a query
     * @param queryId
     * @return query
     */
    def get(String queryId) {
        if (! file) return null
        if (modified()) load()
        return queries.find() { 
            return it.'@id' && it.'@id'.equals(queryId) 
        }
    }

    /**
     * Load the queries from the file
     * @return queries
     */
    private synchronized NodeList load() {
        if (! modified()) return queries
        queries = []
        if (file.exists()) {
            Log.debug "${key} load from file [${name}]"
            queries = new XmlParser().parse(file).query
            last = file.lastModified()
        } else {
            Log.debug "${key} load from stream [${name}]"
            def stream = IOUtils.classLoadFile(name)
            if (stream) queries = new XmlParser().parse(stream).query
        }
        Log.debug "${key} loaded ${queries.size()}"
        return queries
    }

    /**
     * Has the query file been modified?
     * @return
     */
    private boolean modified() {
        if (queries == null) return true
        return file.exists() ? file.lastModified() != last : false
    }
    
}

