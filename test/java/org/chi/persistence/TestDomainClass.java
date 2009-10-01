package org.chi.persistence;


/**
 * Represents a new domain class
 * @author rgrey
 */
public class TestDomainClass extends DomainClass {
    
    /**
     * Constructor with class name
     * @param name
     */
    public TestDomainClass(String name) {
        super(name, false);
    }

    /**
     * Add a field
     * @param string
     * @param class1
     */
    public void addField(String string, Class<?> _class) {
        DomainPrimitive field = new DomainPrimitive(string);
        field.setType(_class.getSimpleName());
        fields.add(field);
    }
    
}
