
import org.chi.persistence.DomainObjectLoader.DomainClass

/**
 * this represents the template for domain class objects.
 */
@DomainClass
class $current.name { 

    static mapping = { 
        cache include: "non-lazy" 
        columns {
            xmlcache type:'text'
<%  
    current.fields.each { 
        if (it.mapping)
            println "            ${it.mapping}" }
%>
        }
    }

    // the schema used to create the class
    // if this is null, consider it at child
    static final String schema<% 
    if (current.schema) print " = '''${current.schema}'''"%>

    // the original schema name
    // if this is null, consider it at child
    static final String schemaName<%
    if (current.schemaName) print " = \"${current.schemaName}\""%>

    // primitives that should be attributes
    static final Collection<String> attributes = [<%  
        current.primitives.each { if (it.attribute) print "'${it.varName}'," }
%>]

    // primitives that should be empty
    static final Collection<String> empty = [<%  
        current.primitives.each { if (it.empty) print "'${it.varName}'," }
%>]

    static final Collection<String> elements = [<%  
        current.elements.each { print "'${it}'," } %>]

    static final Collection<String> children = [<%  
        current.children.each { print "'${it.varName}'," } %>]

    static final Collection<String> parents = [<%  
        current.parents.each { print "'${it}'," } %>]

    // one-to-many
    static hasMany = [<%
    def multiKids = current.children.findAll() { it.multiple }
    if (multiKids.size() == 0) print ":"
    else multiKids.each { print "${it.varName} : ${it.name}," } 
%>]
    
    // constraints
    static constraints = {
        xmlcache(maxSize: 1000000)
<%  
    current.fields.each { println "        ${it.constraints}" }
%>
    }

    // validation string
    static String validationStr = '''
        <validation>
<%  
    current.fields.each { 
        String xml = "<" + it.constraints.replace("("," ").replace(")","/>")
        xml = xml.replace(": ","=\"").replace(",","\"")
        println "            ${xml}"
    }
%>
        </validation>
    '''

    // interleave ordering convenience
    private o
    def addOrder(type, object) { 
        if (o == null) o = []
        o.add([type, object])
    }
    def order(o) { this.o = o }
    def order() { return o }

    // new object flag
    private _new = false
    def markNew() { _new = true }
    def isNew() { return _new }

    // one-to-one
<% current.children.each { if (! it.multiple) println "    ${it.defString}" } %>

    // fields
<% current.primitives.each {  println "    ${it.defString}" } %>
<% current.children.each { 
    if (it.multiple) println "    List    ${it.varName}" } %>
    // cache
    String xmlcache = " "

    Date getCreated() {
        if (created == null) created = new Date()
        return created
    }

    Date getLastUpdated() {
        if (lastUpdated == null) lastUpdated = new Date()
        return lastUpdated
    }

    void updated() {
        lastUpdated = null
    }

    void finishorder() {
        if (! o) return
        addOrder("lastUpdated", getLastUpdated())
        addOrder("created", getCreated())
    }

    @Override
    boolean equals(Object obj) {
        if (! obj) return false
        return obj.class.equals(this.class) && obj.id.equals(id)
    }

    @Override
    int hashCode() {
        return "<%=current.name%>".hashCode() + id.hashCode()
    }

}
