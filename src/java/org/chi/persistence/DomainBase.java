package org.chi.persistence;

import java.util.Collection;

/**
 * Shared items between {@link DomainClass} and {@link DomainPrimitive}
 * @author rgrey
 */
public abstract class DomainBase {
    
    private String name;
    private Integer maxLength;
    private boolean multiple = false;
    private boolean optional = false;
    
    /**
     * Default
     * @param name
     */
    public DomainBase(String name) {
        this.name = name;
    }
    
    /**
     * Get the variable definition string
     * @return
     */
    public abstract String getDefString();
    
    /**
     * Get the possible values of a field
     * Override if necessary
     * @return
     */
    public Collection<String> getValues() { 
        return null;
    }

    /**
     * Get the name of this object
     * @return the name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the variable name for this object
     * @return
     */
    public String getVarName() {
        return PersistUtils.lowerCamelCase(name);
    }
    
    /**
     * Get multiplicity
     * @return
     */
    public boolean isMultiple() { 
        return multiple; 
    }
    
    /**
     * Set multiplicity
     * @param multiple
     */
    public void setMultiple(boolean multiple) { 
        this.multiple = multiple; 
    }
    
    /**
     * Get optional
     * @return
     */
    public boolean isOptional() { return optional; }
    
    /**
     * Set as an optional field
     * @param multiple
     */
    public void setOptional(boolean optional) { 
        this.optional = optional; 
    }
    
    /**
     * Set the maximum length of a field value
     * @param maxLength the maxLength to set
     */
    public void setMaxLength(String maxLength) {
        this.maxLength = Integer.valueOf(maxLength);
    }

    /**
     * Protected access to max length
     * @return
     */
    protected Integer getMaxLength() {
        return maxLength;
    }
    
    /**
     * Get the GORM domain object constraints
     * @return
     */
    public String getConstraints() {
        StringBuilder results = new StringBuilder();
        results.append(this.getVarName() + "(");
        if (maxLength != null) results.append("maxSize : " + maxLength + ","); 
        if (optional) results.append("nullable : true,");
        else results.append("nullable : false,");
        if (getValues() != null) {
            results.append("inList : [");
            for (String value : getValues()) results.append('"' + value + "\",");
            results.append("], ");
        }
        results.append(")");
        return results.toString();
    }

    /**
     * Override to modify column mapping
     * @return
     */
    public String getMapping() { return null; }

    /**
     * Merge the domain base fields
     * @param original
     */
    public void merge(DomainBase original) {
        this.name = original.getName();
        if (original.maxLength != null) this.maxLength = original.maxLength;
    }
    
    @Override
    public int hashCode() { 
        return name.hashCode(); 
    }
    
    @Override
    public boolean equals(Object db) {
        return name.equals(((DomainBase) db).getName());
    }


}
