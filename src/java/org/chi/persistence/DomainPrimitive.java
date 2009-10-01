package org.chi.persistence;

import java.util.Collection;

/**
 * A domain object field
 * @author rgrey
 */
public class DomainPrimitive extends DomainBase {
    
    private String type = "String";
    
    // configuration for this class
    private boolean attribute = false;
    private boolean empty = false;
    private Collection<String> values = null;
    
    /**
     * Default
     * @param name
     */
    public DomainPrimitive(String name) {
        super(name);
    }

    /**
     * Is this an attribute?
     * @return
     */
    public boolean isAttribute() {
        return attribute;
    }
    
    /**
     * @param attribute
     */
    public void setAttribute(boolean attribute) { 
        this.attribute = attribute; 
    }
    
    /**
     * Is this an empty element?
     * @return the empty
     */
    public boolean isEmpty() {
        return empty;
    }

    /**
     * @param empty the empty to set
     */
    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
    
    /**
     * Get the possible values
     * @return
     */
    @Override
    public Collection<String> getValues() {
        return values;
    }
    
    /**
     * Set the values
     * @param values
     */
    public void setValues(Collection<String> values) {
        this.values = values;
    }
    
    /**
     * Set the type
     * @param type
     */
    public void setType(String type) {
        // TODO this won't work in all cases, see:
        // http://www.w3.org/2001/XMLSchema-datatypes
        // for examples
        if ("dateTime".equals(type)) type = "Date";
        this.type = PersistUtils.camelCase(type, true); 
    }

    @Override
    public String getMapping() {
        Integer max = getMaxLength();
        if (max == null || max < 99999 || ! "String".equals(type)) return null;
        // create clob if necessary
        return this.getVarName() + " type:'text'";
    }
    
    @Override
    public String getDefString() {
        return (isMultiple() ? type + "[]" : type) + " " + getName();
    }
    
    @Override
    public String toString() {
        return "field [" + getName() + "]";
    }

}
