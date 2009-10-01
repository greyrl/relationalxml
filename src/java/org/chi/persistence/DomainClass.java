package org.chi.persistence;

import groovy.text.SimpleTemplateEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import org.chi.util.GeneralException;
import org.chi.util.Log;

/**
 * Represents a new domain class
 * @author rgrey
 */
public class DomainClass extends DomainBase {
    
    private static final SimpleTemplateEngine ste = new SimpleTemplateEngine();
    
    final ArrayList<DomainBase> fields = new ArrayList<DomainBase>();
    final HashSet<String> parents = new HashSet<String>();
    
    private boolean externalRef;
    private String schema; 
    private String schemaName;
    
    /**
     * Constructor with name
     * @param name
     */
    public DomainClass(String name, boolean externalRef) {
        super(name);
        this.externalRef = externalRef;
    }
    
    @Override
    public String getDefString() {
        return getName() + " " + getVarName();
    }
    
    /**
     * Get the fields
     * @return
     */
    public Collection<DomainBase> getFields() {
        return fields; 
    }

    /**
     * Get the parent names
     * @return
     */
    public Collection<String> getParents() {
        return parents; 
    }

    /**
     * Does this class contain a particular field
     * @param name
     * @return
     */
    public boolean hasField(String name) {
        if (name == null) return false;
        for (DomainBase field : fields) {
            if (name.equals(field.getName())) return true;
        }
        return false;
    }
    
    /**
     * Convenience
     * @return
     */
    public Collection<DomainClass> getChildren() {
        Vector<DomainClass> children = new Vector<DomainClass>();
        for (Object next : fields) {
            if (next instanceof DomainClass) children.add((DomainClass) next);
        }
        return children; 
    }
    
    /**
     * Convenience
     * @return
     */
    public Collection<DomainPrimitive> getPrimitives() {
        Vector<DomainPrimitive> primitive = new Vector<DomainPrimitive>();
        for (Object next : fields) if (next instanceof DomainPrimitive) 
            primitive.add((DomainPrimitive) next);
        return primitive;
    }
    
    /**
     * Get primitives and children that aren't attributes
     * @return
     */
    public String[] getElements() {
        Vector<String> all = new Vector<String>();
        for (DomainBase next : fields) {
            if (next instanceof DomainPrimitive && 
                    ((DomainPrimitive) next).isAttribute()) continue;
            all.add(next.getVarName());
        }
        return all.toArray(new String[0]);
    }
    
    /**
     * @return the schema
     */
    public String getSchema() {
        return schema;
    }

    /**
     * @return the schema name
     */
    public String getSchemaName() {
        return schemaName;
    }

    /**
     * Set the schema that this class was generated from
     * @param name the schema name
     * @param schema the schema to set
     */
    public void setSchema(String name, String schema) {
        this.schemaName = name;
        this.schema = schema;
    }

    /**
     * Does this classes refernce an external entity?
     * @return
     */
    public boolean isExternalRef() {
        return externalRef;
    }

    /**
     * Build a groovy domain class from a template
     * @param template
     * @return
     * @throws GeneralException 
     */
    public String buildTemplate(String template) throws GeneralException {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("current", this);
        try {
            String result = ste.createTemplate(template).make(map).toString();
            Log.debug("DomainClass.buildTemplate() : build\n" + result);
            return result;
        } catch (Exception e) {
            throw new GeneralException(e);
        }
    }

    /**
     * Merge the domain class fields
     * @param original
     */
    public void merge(DomainClass original) {
        super.merge(original);
        this.fields.clear();
        this.fields.addAll(original.fields);
        this.schema = original.schema;
        this.schemaName = original.schemaName;
        original.parents.addAll(this.parents);
    }
    
    @Override
    public String toString() {
        return "class [" + getName() + "]";
    }

}
