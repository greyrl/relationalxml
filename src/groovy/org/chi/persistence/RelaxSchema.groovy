package org.chi.persistence

import groovy.util.slurpersupport.GPathResult
import groovy.xml.QName

import org.chi.util.Log
import org.iso_relax.jaxp.ValidatingSAXParserFactory
import org.iso_relax.verifier.VerifierFactory
import org.xml.sax.EntityResolver
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

/**
 * Relax NG implementatio of the schema parser
 * @author rgrey
 */
class RelaxSchema implements SchemaParser {

    public static final cName = "cache"
    public static final ilName = "interleave"
    protected static final ilSchema = ilName + ".rng"

    private static final mclass = RelaxSchema.class.metaClass
    private static final nsStr = "http://relaxng.org/ns/structure/1.0"
    private static final ns = new groovy.xml.Namespace(nsStr, 'ns')
    private static final cache = '''
        <grammar xmlns="http://relaxng.org/ns/structure/1.0" 
            datatypeLibrary="http://www.w3.org/2001/XMLSchema-datatypes">
            <start>
                <element name="cache">
                    <element name="oid"><text/></element>
                    <element name="otype"><text/></element>
                </element>
            </start>
        </grammar>
    '''
    private static final interleave = '''
        <grammar xmlns="http://relaxng.org/ns/structure/1.0" 
            datatypeLibrary="http://www.w3.org/2001/XMLSchema-datatypes">
            <start>
                <element name="interleave">
                    <element name="ordering">
                        <data type="string">
                            <param name="maxLength">100000</param>
                        </data>
                    </element>        
                </element>
            </start>
        </grammar>
    '''

    // fields that might need to be appended
    private static final addFields = [ "id", "lastUpdated", "created" ]
    // check for complex elements
    private static final complexChecks = [ "externalRef", "oneOrMore", 
        "zeroOrMore", "element", "attribute" ]

    private List classes = []

    private current
    private root

    private attribute
    private values

    @Override
    DomainClass[] getClasses(File directory) {
        assert directory.exists()
        directory.listFiles().each { f ->
            current = null
            if (! f.isDirectory() && f.name.endsWith(".rng")) parse(f)
            if (current) classes = current.loadClasses(classes)
        }
        // load cache
        custom(cache, "cache.rng")
        // load reference interleave, if necessary
        if (classes.contains(new DomainClass(ilSchema, false))) 
            custom(interleave, ilSchema)
        // load external references
        classes.each() { it.resolveExternalRefs(classes) }
        // rerun the load to filter out duplicates
        List result = []
        classes.each() { result = it.loadClasses(result) }
        return result
    }

    @Override
    GPathResult validate(Reader reader, String string, Map externals) {
        Log.debug "RelaxSchema.validate() : enter"
        RelaxResolver rr = new RelaxResolver(externals)
        def schema = schemaParse(new StringReader(string), rr)
        def saxFactory = new ValidatingSAXParserFactory(schema)
        def slurper = new XmlSlurper(saxFactory.newSAXParser())
        def handler = new RelaxErrorHandler() 
        slurper.setErrorHandler(handler)
        try {
            return slurper.parse(reader)
        } finally {
            if (handler.message) throw new ParseException(handler.message)
        }
    }

    /**
     * Parse a schema from a file
     * @param schema
     */
    private parse(File schema) {
        Log.console "RelaxSchema.parse() : parse [${schema}]"
        FileResolver fr = new FileResolver(schema.getParentFile())
        schemaParse(new FileReader(schema), fr)
        root = new XmlParser().parse(schema)
        parse(schema.name)
    }

    /**
     * After generating root, parse elements
     * @parma sname schema nam
     */
    private parse(String sname) {
        assert root
        if ("grammar".equals(name(root))) 
            recurse(root.find { "start".equals(name(it)) })
        else recurse(root)
        if (current) current.setSchema(sname, generateSchema())
    }

    /**
     * Generate the schema from the existing parsed node
     * @return
     */
    private generateSchema() {
        def result = new StringWriter()
        new XmlNodePrinter(new PrintWriter(result), "").print(root)
        return result.toString().replaceAll("\n","")
    }

    /**
     * Recurse through a single node and it's children
     * @param node
     */
    private recurse(Node node) {
        def name = name(node)
        def end = "end" + PersistUtils.camelCase(name, true)
        // this requires each relax element name to have a method
        if (mclass.hasMetaMethod(name)) this.invokeMethod(name, node)
        else Log.error "No method defined to handle relax [${name}]"
        node.children().each { recurse(it) }
        // invoke possible end methods
        if (mclass.hasMetaMethod(end)) this.invokeMethod(end, node) 
    }

    /**
     * Handle a text based child
     * @param node
     */
    private recurse(String string) {
        if (values != null) values.add(string)
    }

    /**
     * Handle start tag. Not used
     * @param node
     */
    private start(node) {}

    /**
     * Follow a reference
     * @param node
     */
    private ref(node) { 
        def cname = node.@name
        if (! cname) return
        def refelement = root.find {
            return "define".equals(name(it)) && cname.equals(it.@name)
        }
        if (! refelement || refelement.children().size() != 1) return
        Log.debug "RelaxSchema.ref() : follow [${cname}]"
        def repeat = current.firstAncestor
        repeat = childClass(PersistUtils.camelCase(cname, true), repeat)
        if (repeat) current.addField(repeat, false)
        else recurse(refelement.children()[0])
    }

    //--------------- CORE TYPES --------------

    /**
     * Process an element
     * @param node
     */
    private element(node) {
        def name = PersistUtils.camelCase(node.@name, true)
        addBaseFields(node)
        current = new RelaxClass(name, current)
        Log.debug "RelaxSchema.recurse() : element ${name}"
    }

    /**
     * Finish an element
     * @param node
     */
    private endElement(node) {
        if (current.parent) current = current.parent
    }

    /**
     * Handle an attribute
     * @param node
     */
    private attribute(node) {
        attribute = node.@name
        // if the node is empty, define the value
        if (node.children().size() == 0) 
            node.appendNode(new QName(nsStr, "text"))
    }

    /**
     * Finish an attribute
     * @param node
     */
    private endAttribute(node) {
        attribute = null
    }

    /**
     * Process a text node
     * @param node
     */
    private text(node) {
        if (attribute) createFieldFromAttribute()
        else createFieldFromClass()
    }

    /**
     * Handle a specific data type
     * @param node
     */
    private data(node) {
        def field
        if (attribute) field = createFieldFromAttribute()
        else field = createFieldFromClass()
        if (node.@type) field.type = node.@type
    }

    /** 
     * Handle choice. 
     * @param node
     */
    private choice(node) {
        values = []
    }

    /**
     * End choice.
     * @param node
     */
    private endChoice(node) {
        def field
        if (attribute) field = createFieldFromAttribute()
        else field = createFieldFromClass()
        field.values = values
        values = null
    }

    /**
     * Assign a field configuration parameter
     * @param node
     */
    private param(node) {
        def n = node.@name
        if (! n) return
        // get the last assigned field
        def field = current.parent.fields
        field = field[field.size() - 1]
        // if the property exists, assign it
        if (field.metaClass.hasProperty(field, n)) field[n] = node.text()
        else Log.error "Unable to handle parameter [${n}]"
    }

    /** 
     * Process an empty element. This basically becomes a boolean
     * @param node
     */
    private empty(node) {
        def field = createFieldFromClass()
        field.optional = false
        field.empty = true
        field.setType("boolean")
    }

    private value(node) {}

    /**
     * Process an interleave, all children must be ordered
     * @param node
     */
    private interleave(node) {
        new InterleaveClass(current)
    }

    //--------------- MUTLIPLICITY ------------

    /**
     * Process an optional tag
     * @param node
     */
    private optional(node) {
        current.coptional = true
    }

    /**
     * End an optional tag
     * @param node
     */
    private endOptional(node) {
        current.coptional = false
    }

    /**
     * Process zero or more items
     * @param node
     */
    private zeroOrMore(node) {
        current.cmultiple = true
        current.coptional = true
    }

    /**
     * End processing zero or more items
     * @param node
     */
    private endZeroOrMore(node) {
        current.cmultiple = false
        current.coptional = false
    }

    /**
     * Process one or more items
     * @param node
     */
    private oneOrMore(node) {
        current.cmultiple = true
    }

    /**
     * End processing one or more items
     * @param node
     */
    private endOneOrMore(node) {
        current.cmultiple = false
    }

    /**
     * Handle external references
     * @param node
     */
    private externalRef(node) {
        new RelaxClass(node.@href, current, true)
    }

    //--------------- APPEND BASE FIELDS ------

    /**
     * Add additonal base fields if necessary
     * @param node
     */
    private addBaseFields(node) {
        if (! node) return
        // check to make sure this is a child and not a primitive
        if (! node.depthFirst().any() { 
            if (node.equals(it)) return false
            def n = name(it)
            return complexChecks.any() { check -> 
                return check.equals(n);
            }
        }) return
        Log.debug "RelaxSchema.addBaseFields() : evaluating ${node.@name}"
        addFields.each() { name ->
            Log.debug "RelaxSchema.addBaseFields() : check field ${name}"
            // if it already has the field, ignore it
            if (node.depthFirst().any() { return name.equals(it.@name) }) return
            Log.debug "RelaxSchema.addBaseFields() : add field ${name}"
            name = "append" + PersistUtils.camelCase(name, true)
            if (mclass.hasMetaMethod(name)) this.invokeMethod(name, node)
            else Log.error "No method defined to handle adding field [${name}]"
        }
    }

    /**
     * Append an id attribute onto a node
     * @param node
     */
    private appendId(node) {
        def optional = addOptional(node)
        optional.appendNode(new QName(nsStr, "attribute"))
        def attribute = optional[ns.attribute][0]
        attribute.@name = "id"
        attribute.appendNode(new QName(nsStr, "data"))
        def data = attribute[ns.data][0]
        data.@type = "long"
    }

    /**
     * Append the date of creation
     * @param node
     */
    private appendCreated(node) {
        def optional = addOptional(node)
        optional.appendNode(new QName(nsStr, "element"))
        def element = optional[ns.element][0]
        element.@name = "created"
        element.appendNode(new QName(nsStr, "data"))
        def data = element[ns.data][0]
        data.@type = "dateTime"
    }

    /**
     * Append the date of the last update
     * @param node
     */
    private appendLastUpdated(node) {
        def optional = addOptional(node)
        optional.appendNode(new QName(nsStr, "element"))
        def element = optional[ns.element][0]
        element.@name = "last-updated"
        element.appendNode(new QName(nsStr, "data"))
        def data = element[ns.data][0]
        data.@type = "dateTime"
    }

    /**
     * Convenience
     * @param node
     * @return
     */
    private addOptional(node) {
        node.appendNode(new QName(nsStr, "optional"))
        def optional = node[ns.optional]
        return optional[optional.size() - 1]
    }

    //--------------- CONVENIENCE -------------

    /**
     * Create a field and remove the current class
     */
    private createFieldFromClass() {
        def field = new RelaxPrimitive(current.name, current.parent)
        current.parent.addField(field, true)
        return field
    }

    /**
     * Create an attribute on a class
     */
    private createFieldFromAttribute() {
        def field = new RelaxPrimitive(attribute, current)
        field.attribute = true
        current.addField(field, false)
        return field
    }

    /**
     * Convenience
     * @param node
     */
    private String name(node) {
        (node.name() in QName) ? node.name().localPart : node.name()
    }
            
    /**
     * Convenience
     * @param source
     * @param er
     * @return
     */
    private schemaParse(Reader source, EntityResolver er) {
        def factory = VerifierFactory.newInstance(nsStr)
        factory.setEntityResolver(er)
        return factory.compileSchema(new InputSource(source))
    }

    /**
     * Grab a child if it matches a name
     * @param name
     * @param _next
     * @return null or class if it exists
     */
    private RelaxClass childClass(String name, RelaxClass _next) {
        if (_next.name.equals(name)) return _next
        return _next.children.find() { child ->
            return childClass(name, child)
        }
    }

    /**
     * Load a custom, built-in schema
     * @param schema
     * @param file
     */
    private void custom(schema, file) {
        current = null
        root = new XmlParser().parseText(schema)
        parse(file)
        current.loadClasses(classes)
    }

}

/**
 * Relax class metadata
 * @author rgrey
 */
class RelaxClass extends DomainClass {

    def parent

    // these flags represent options for new children
    def cmultiple = false
    def coptional = false

    // ancestor count
    protected ac = 0
    // first ancestor
    private firstAncestor = this

    def RelaxClass(String name, RelaxClass parent, boolean externalRef) { 
        super(name, externalRef)
        this.parent = parent 
        if (! parent) return
        multiple = parent.cmultiple
        optional = parent.coptional
        parent.addField(this, false)
        for ( ac = 0 ; firstAncestor.parent ; ac++) 
            firstAncestor = firstAncestor.parent
        Log.debug "RelaxClass() : ancestor count for [${name}] = ${ac}"
    }

    def RelaxClass(String name, RelaxClass parent) { 
        this(name, parent, false)
    }

    /**
     * Add a new field. If it already exists, make the other one a "multiple"
     * TODO this will not retain order for elements that aren't direct siblings
     * @param field 
     * @param removeLast remove the last added field?
     */
    def addField(DomainBase field, boolean removeLast) {
        if (removeLast) fields.remove(fields.size() - 1)
        def dup = fields.find() { return (it.name.equals(field.name)) }
        if (dup) dup.multiple = true
        else {
            if (field in DomainClass) field.parents.add(name)
            fields.add(field)
        }
    }

    /**
     * Load the domain classes with the non-referenced children coming first
     * @return
     */
    DomainClass[] loadClasses(classes) {
        children.each { it.loadClasses(classes) }
        if (! classes.contains(this)) { classes.add(this) }
        return classes
    }

    /**
     * Resolve any external referenced classes
     * @param classes all loaded classes for this store
     */
    def resolveExternalRefs(classes) {
        fields.each() { field ->
            if (! (field in DomainClass) || ! field.externalRef) return
            Log.debug "DomainClass.resolveExternalRefs() : ext [${field.name}]"
            def ref = classes.find() { cl -> field.name.equals(cl.schemaName) }
            if (ref) field.merge(ref)
            else Log.error "External reference [${field.name}] not found"
        }
    }

    @Override
    String toString() {
        def result = "class [${name}]"
        def fspace = " " * (ac + 2)
        fields.each() { result += "\n${fspace}${it}" }
        children.each() { result += "\n" + (" " * it.ac) + it }
        return result 
    }

}

/**
 * Interlave external reference
 * @author rgrey
 */
class InterleaveClass extends RelaxClass {

    def InterleaveClass(RelaxClass parent) {
        super(RelaxSchema.ilSchema, parent, true)
        this.optional = true
    }

}

/**
 * Relax field metadata
 * @author rgrey
 */
class RelaxPrimitive extends DomainPrimitive {
    
    def RelaxPrimitive(String name, RelaxClass parent) {
        super(PersistUtils.lowerCamelCase(name))
        multiple = parent.cmultiple
        optional = parent.coptional
    }

}

/** 
 * Entity resolver from the file system
 * @author rgrey
 */
private class FileResolver implements EntityResolver {

    static File path

    public FileResolver(path) {
        this.path = path;
    }

    InputSource resolveEntity(String publicId, String systemId) {
        Log.debug "FileResolver.resolveEntity() : looking for [${systemId}]"
        def fullPath = new File(path.absolutePath + File.separator + systemId)
        if (fullPath.exists()) return new InputSource(new FileReader(fullPath))
        else Log.error "File reference ${systemId} not found"
        return null
    }

}

/** 
 * Entity resolver for the string schemas
 * @author rgrey
 */
private class RelaxResolver implements EntityResolver {

    static Map schemas

    public RelaxResolver(schemas) {
        this.schemas = schemas;
    }

    InputSource resolveEntity(String publicId, String systemId) {
        Log.debug "RelaxResolver.resolveEntity() : looking for [${systemId}]"
        def entry = schemas.find() { key, val -> systemId.equals(key) }
        if (! entry) Log.error "External reference ${systemId} not found"
        return entry ? new InputSource(new StringReader(entry.value)) : null
    }

}

/**
 * Handle parsing errors
 * @author rgrey
 */
private class RelaxErrorHandler implements ErrorHandler {

    def message

    void error(SAXParseException exception) { assign(exception) }
    void fatalError(SAXParseException exception) { assign(exception) }
    void warning(SAXParseException exception) { assign(exception) }

    /**
     * Assign the error message that occurred during validation
     * @param e
     */
    private assign(SAXParseException e) {
        message = "Parse error occurred \"${e.message}\" on " + 
            "line ${e.lineNumber}, column ${e.columnNumber}"
    }

}

