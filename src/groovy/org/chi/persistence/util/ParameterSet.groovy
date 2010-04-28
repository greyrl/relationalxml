package org.chi.persistence.util

import org.chi.persistence.XmlSerializer

/**
 * Query parameter helper
 * @author rgrey
 */
class ParameterSet {

    String[] values // parameter values
    def query // the hibernate query
    XmlSerializer serial

    int current = 0

    /**
     * Set the next parameter
     * @param param
     */
    void setParam(Node param) {
        assert param.'@type'
        Class type = Class.forName(param.'@type')
        String named = param.'@name'
        if (named) query.setParameterList(named, extractMulti(type))
        else query.setParameter(current, extractValue(type))
    }

    /**
     * Set the rest of the values as a named parameter. This assumes
     * There are no more parameters on the query
     * @param type
     * @return
     */
    private extractMulti(Class type) {
        def result = []
        while (current < values.length) result.add(extractValue(type))
        return result
    }

    /**
     * Set an individual parameter
     * @param type
     * @return
     */
    private extractValue(Class type) {
        def xml = new XmlSlurper().parseText("<val>${values[current++]}</val>")
        return serial.executeCreate(xml, type, null)
    }

}

