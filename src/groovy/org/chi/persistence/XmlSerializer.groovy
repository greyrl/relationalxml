package org.chi.persistence

import groovy.util.slurpersupport.GPathResult

import java.text.SimpleDateFormat
import java.util.regex.Matcher

import org.chi.persistence.sort.Group
import org.chi.persistence.sort.MultiSort
import org.chi.persistence.util.LockItem
import org.chi.persistence.util.Queries
import org.chi.persistence.util.ParameterSet
import org.chi.persistence.util.UpdateCache
import org.chi.persistence.util.XmlCompare
import static org.chi.persistence.util.XmlCompare.prepandwritexml
import org.chi.util.ArrayUtils
import org.chi.util.IOUtils
import org.chi.util.Log
import org.chi.util.StringUtil
import org.dom4j.Document
import org.dom4j.DocumentHelper
import org.dom4j.Element
import org.dom4j.io.OutputFormat
import org.dom4j.io.XMLWriter
import org.hibernate.EntityMode
import org.hibernate.SessionFactory
import org.hibernate.impl.SQLQueryImpl

/**
 * Save and retrieve XML from the database
 * @author rgrey
 */
class XmlSerializer extends HibernateTools implements GroovyInterceptable {

    private static final attstr = "attributes"
    private static final mclass = XmlSerializer.class.metaClass
    private static final format = new OutputFormat(" ", true)
    static { format.suppressDeclaration = true }
    private static final String sdateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    private static final String ILN = RelaxSchema.ilName
    private static final String dateRep = 
        "lastUpdated:[-]?[0-9]*,created:[-]?[0-9]*"

    private SessionFactory sf
    private SchemaParser validator
    private List classes = []
    private Queries queries
    private Map schemas = [:]

    /**
     * Default
     * @param sf the session factory used to load these objects
     * @param schemaClass the class used to validate 
     */
    def XmlSerializer(SessionFactory sf, String queryFile, Class schemaClass) {
        this.sf = sf
        if (! sf || ! schemaClass) return
        sf.allClassMetadata.each { key, md ->
            classes.add(md.getMappedClass(EntityMode.POJO))
        }
        // load the schema map
        classes.each {
            def inst = it.newInstance()
            if (! it.schema || ! it.schemaName) return
            schemas.put(it.schemaName, it.schema)
        }
        Log.debug "XmlSerializer() : loaded ${classes.size()} classes"
        assert schemaClass in SchemaParser
        validator = schemaClass.newInstance()
        queries = new Queries(queryFile)
    }

    @Override
    public SessionFactory getSF() { return sf }

    /**
     * Update object from XML
     * @param reader
     * @param _class the name of the class to generate 
     * @return the serialized XML of the saved object
     */
    String save(Reader reader, String _class) {
        long timing = System.currentTimeMillis()
        Object obj = extract(reader, _class)
        timing = Log.timing("XmlSerializer.save", timing, "extract")
        LockItem.lock(obj, this, 100)
        timing = Log.timing("XmlSerializer.save", timing, "lock")
        try {
            def xmlc = changed(obj)
            timing = Log.timing("XmlSerializer.save", timing, "changed")
            if (xmlc.all == null) obj.id = null
            storeAll(obj, xmlc)
            timing = Log.timing("XmlSerializer.save", timing, "storeAll")
            def updated = findNew(obj, xmlc.findUpdated(obj))
            timing = Log.timing("XmlSerializer.save", timing, "findupdate")
            if (updated.size() > 0) updated.addAll(loadParents(obj))
            updated.each { UpdateCache.update(it, this, obj) }
            timing = Log.timing("XmlSerializer.save", timing, "cache")
            succeed()
            return prepandwritexml("result", UpdateCache.get(obj), false)
        } finally {
            LockItem.unlock(this)
            timing = Log.timing("XmlSerializer.save", timing, "save")
            Log.debug("XmlSerializer.save() : timing ================")
        }
    }

    /**
     * Update an element from a string base. This method attempts
     * to determine the schema by extracting the class name from
     * the first element
     * @param string
     */
    String save(String string) {
        return save(new StringReader(string), extractClass(string))
    }

    /**
     * Copy an object by removing ids
     * @param type the object class
     * @param id
     * @return the new object
     */
    String copy(String type, Serializable id) {
        def obj = null
        Log.debug "XmlSerializer.copy() : ${type}, id ${id}"
        try {
            beginTransaction()
            obj = session.get(type, id)
            if (! obj) {
                Log.console "XmlSerializer.copy() : unable to find object"
                return
            }
            XmlCompare xmlc = new XmlCompare()
            obj = storeAll(replicate(obj), xmlc)
            xmlc.findUpdated(obj).each { UpdateCache.update(it, this) }
            succeed()
        } finally {
            endTransaction()
            closeSession()
        }
        return serialize(obj)
    }

    /**
     * Load some XML from a query
     * @param queryId the id of the query to load from the file
     * @param params the parameters for the query
     * @return objects serialized to XML
     */
    String sqlLoad(String queryId, String[] params) {
        Log.debug "XmlSerializer.sqlLoad() : query [${queryId}]"
        def query = queries.get(queryId)  
        if (! query) {
            Log.error "Unable to find query [${queryId}]"
            return null
        }
        def sql = query.@sql ? query.@sql : query.sql[0].text()
        assert sql
        def _native = query["@native"]
        try {
            StringBuilder result = new StringBuilder("<query")
            Log.debug "XmlSerializer.sqlLoad() : sql [${sql}]"
            if (Log.isDebugEnabled()) result.append(" sql=\"${sql}\"")
            if (params.length >= query.param.size()) {
                def hquery 
                if (! _native) hquery = session.createQuery(sql)
                else {
                    hquery = session.createSQLQuery(sql)
                    def name = PersistUtils.addUnderscores(_native)
                    hquery.addEntity(name, getDomainClass(_native))
                }
                ParameterSet pset = new ParameterSet(
                    serial:this, values:params, query:hquery)
                query.param.each() { pset.setParam(it) }
                if (Boolean.valueOf(query.@cache)) hquery.setCacheable(true)
                long timing = System.currentTimeMillis()
                def alias = hquery in SQLQueryImpl ? null : hquery.returnAliases
                def alen = badAlias(alias) ? 0 : alias.size() 
                def group = query.'@group'
                def amark = badAlias(alias) || group ? null : query.'@alias-mark'
                def list = hquery.list()
                if (Boolean.valueOf(query.'@remove-dupes')) list = list.unique()
                list = reduce(list)
                def total = alen ? list.size() / alen : list.size();
                Log.timing("XmlSerializer.sqlLoad", timing, "query")
                result.append(" result-count=\"${total}\">\n")
                if (amark) result.append("<${amark}>")
                MultiSort.sort(query, list, params).eachWithIndex() { it, i ->
                    if (handleGroup(group, it, result, i == 0)) return
                    def nextm = amark && i != 0 && i % alen == 0
                    if (nextm) result.append("</${amark}><${amark}>")
                    String _cache = UpdateCache.get(it)
                    if (! _cache) {
                        def msg = " no cached db value ${it}"
                        Log.console "XmlSerializer.sqlLoad() :"  + msg
                        if (alen) { 
                            def name = alias[i % alen]
                            result.append(serialize(it, name, null))
                        } else result.append(serialize(it))
                    } else result.append(_cache)
                }
                if (group && list.size() > 0) result.append("\n</${group}>")
                if (amark) result.append("</${amark}>")
            } else {
                Log.error "XmlSerializer.sqlLoad() : wrong params count"
                result.append(" result-count=\"0\">\n")
            }
            return result.append("\n</query>").toString()
        } finally {
            closeSession()
        }
    }

    /**
     * Convenience
     * @param queryId
     * @return 
     */
    String sqlLoad(String queryId) {
        return sqlLoad(queryId, new String[0])
    }

    /**
     * Remove data from a store
     * @param type the object type
     * @param ids the id(s) of object(s) to be removed
     * @return boolean success or failure
     */
    def remove(String type, Serializable... ids) {
        def parents = []
        String name = PersistUtils.lowerCamelCase(type)
        try {
            beginTransaction()
            ids.each { id ->
                Log.debug "XmlSerializer.remove() : ${type}, id ${id}"
                def obj = session.get(type, id)
                if (! obj) return
                loadParents(obj).each { p ->
                    parents.add(p)
                    if (! p.metaClass.hasProperty(p, name)) return
                    if (p[name] instanceof Collection) p[name].remove(obj)
                    else p[name] = null
                }
                session.delete(obj)
            }
            succeed()
        } finally {
            if (! endTransaction()) closeSession()
        }
        parents.each() { parent -> UpdateCache.update(parent, this) }
        closeSession()
    }

    /**
     * Create or update an object from an XML stream
     * @param reader
     * @param domainClass 
     * @return
     */
    Object extract(Reader reader, String domainClass) {
        Class _class = getDomainClass(domainClass) 
        assert _class && _class.schema
        def root = validator.validate(reader, _class.schema, schemas)
        assert root
        return genobj(root, null)
    }

    /**
     * Convert an object back into XML
     * @param object
     * @param name optional name override
     * @param or override an object
     */
    String serialize(Object object, String name=null, Object over=null) {
        if (! object) return null
        String val
        Document document = DocumentHelper.createDocument()
        if (object.class.array) {
            Log.debug "XmlSerializer.serialize() : array len ${object.length}"
            Element wrap = document.addElement("array")
            StringBuilder result = new StringBuilder()
            object.each() { result.append(_serialize(it, wrap, name, over)) }
            return result.toString()
        } else return _serialize(object, document, name, over)
    }

    /**
     * Load a domain class by name
     * @param name
     */
    Class getDomainClass(String name) {
        name = PersistUtils.camelCase(name, true)
        return classes.find {
            return it.canonicalName.equals(name) || it.simpleName.equals(name)
        }
    }

    /**
     * Convenience
     * @param domainClass
     * @param field
     * @return
     */
    Object getDomainClassField(String domainClass, String field) {
        def res = getDomainClass(domainClass)
        return res ? res[field] : null
    }

    /**
     * Generate an object from a {@link GPathResult}
     * @param path
     * @param result the object we are working on
     * @return 
     */
    Object genobj(GPathResult path, Object result) {
        if (! result) result = createObj(path)
        assert result
        def order = result.children.contains(ILN)
        path.children().each { result = process(it, result, order) }
        path.attributes().each { key, value ->
            result = process(path["@${key}"], result, order)
        }
        result.finishorder()
        interleave(result)
        return result
    }

    /**
     * What has changed between two xml documents
     * @param obj
     * @return
     */
    private XmlCompare changed(obj) {
        if (! obj.id) return new XmlCompare()
        if (! session.get(obj.class, obj.id)) return new XmlCompare()
        def xml1 = UpdateCache.get(session.get(obj.class, obj.id))
        return XmlCompare.compare(xml1, serialize(obj))
    }

    /**
     * Find all the objects that are new
     * @param obj
     * @param result
     * @return
     */
    private findNew(obj, result=[]) {
        if (! obj) return result
        if (obj in Collection) return obj.each { findNew(it, result) }
        if (obj.isNew()) result.add(obj)
        obj.children.each { findNew(obj[it], result) }
        return result
    }

    /**
     * Remove any arrays from the results
     * @param list
     * @return
     */
    private reduce(list) {
        def result = []
        list.each() { item ->
            if (item && item.class.array) item.each() { result.add(it) }
            else result.add(item)
        }
        return result
    }

    /**
     * Append group data if necessary
     * @param node
     * @param item
     * @param result
     * @param first
     */
    private boolean handleGroup(node, item, result, boolean first) {
        if (! (item in Group)) return false
        if (! first) result.append("</${node}>\n")
        return result.append(
            "<${node} by=\"${item.by}\" id=\"${item.id}\">\n" +
            " <name>${item.name}</name>")
    }

    /**
     * Extract the class name from the XML
     * @param xml
     * @return
     */
    private String extractClass(String xml) {
        // extract the schema name from the first element name
        Matcher fe = xml =~ /<([A-Za-z0-9\-]*)[ | >| \/>]/
        assert fe.size() > 0 && fe[0].size() > 0
        return PersistUtils.camelCase(fe[0][1], true)
    }

    /**
     * Recursively generate XML from domain objects
     * @param obj
     * @param e the current element
     * @param over an optional override object
     */
    private Element genxml(Object obj, Element e, over=null) {
        if (! obj) {
            Log.error("Null object returned from query")
            return null
        }
        if (obj.equals(over)) obj = over
        // this must be a non-object result
        if (! obj.metaClass.hasProperty(obj, attstr)) {
            e.setText(obj.toString())
            return e;
        }
        // handle attributes
        obj.attributes.each { 
            Object val = obj[it]
            if (val == null) return
            e.addAttribute(PersistUtils.addDashes(it), val.toString())
        }
        // handle elements
        def code = { n, val ->
            if (val == null) return
            def coll = (val in Collection)
            if (val.class.array) 
                val.reverseEach() { p -> extractField(e, n, obj, p, over) }
            else if (! coll) extractField(e, n, obj, val, over)
            else val.each() { genxml(it, e.addElement(n), over) }
        }
        // prepare ordering if necessary
        if (obj.metaClass.hasProperty(obj, ILN) && obj.interleave) {
            String order = obj.interleave.ordering
            order.split(",").each { key ->
                def keys = key.split(":")
                def val = obj[keys[0]]
                String n = PersistUtils.addDashes(keys[0])
                if (! (val in Collection)) code.call(n, val)
                else {
                    def c = { it.hashCode().toString() }
                    val = val.find { it && c.call(it).equals(keys[1]) }
                    if (val) code.call(n, val)
                    else {
                        def m = "no ${keys[0]}, hash ${keys[1]}"
                        Log.console "XmlSerializer.genxml() : " + m
                    }
                }
            }
        // handle elements as already ordered
        } else obj.elements.each {
            if (ILN.equals(it)) return
            String n = PersistUtils.addDashes(it)
            code.call(PersistUtils.addDashes(it), obj[it])
        }
        return e
    }

    /**
     * Create a clone of an object
     * @param obj
     * @return the clone
     */
    private replicate(obj) {
        if (! obj || ! getDomainClass(obj.class.name)) return obj
        def res = obj.class.newInstance()
        obj.attributes.each { key ->
            if (! "id".equals(key)) res[key] = obj[key]
        }
        def code = { key, val, hash ->
            if ("lastUpdated".equals(key) || "created".equals(key)) return
            if (! (val in Collection)) {
                res[key] = replicate(val)
                if (hash) res.addOrder(key, res[key])
            } else {
                if (hash) {
                    val = val.find() { it.hashCode().toString().equals(hash) }
                    if (! val) return
                    if (! res[key]) res[key] = []
                    val = replicate(val)
                    res[key].add(val)
                    res.addOrder(key, val)
                } else {
                    res[key] = []
                    val.each { res[key].add(replicate(it)) }
                }
            }
        }
        if (obj.metaClass.hasProperty(obj, ILN) && obj.interleave) {
            String order = obj.interleave.ordering
            order.split(",").each { key ->
                def keys = key.split(":")
                code.call(keys[0], obj[keys[0]], keys[1])
            }
        } else obj.elements.each { k -> code.call(k, obj[k], false) }
        res.finishorder()
        interleave(res)
        return res
    }

    /**
     * Generate an element value for a field on an existing document
     * @param prnt the parent XML element
     * @param name the new element name
     * @param obj the parent object
     * @param val the field value
     * @param over an optional override object
     * @return
     */
    private extractField(Element prnt, String name, Object obj, Object val, 
            over=null) {
        Element _new = prnt.addElement(name)
        // one-to-one
        if (classes.contains(val.class)) genxml(val, _new, over)
        // boolean might have no body
        else if (! obj.empty.contains(name)) _new.addText(getFieldValue(val))
    }

    /**
     * Get the string value of a field
     * @param val
     * @return
     */
    private String getFieldValue(Object val) {
        String create = "string${val.class.simpleName}"
        Class[] classes = [val.class]
        if (! mclass.hasMetaMethod(create, classes)) {
            def msg = "XmlSerializer.getFieldValue() : "
            Log.console msg + "unable to find create method [${create}]"
            return val.toString()
        } else return this.invokeMethod(create, (Object[]) [val])
    }

    /**
     * Serialize a single element
     * @param object
     * @param entity the dom4j entity where the child should be added
     * @param name optional name override
     * @param over an optional override object
     * @return the string value
     */
    private String _serialize(Object object, entity, name=null, over=null) {
        if (! name) name = PersistUtils.addDashes(object.class)
        Log.debug "XmlSerializer._serialize() : add [${name}]"
        Element _new = genxml(object, entity.addElement(name), over)
        if (_new == null) return null
        StringWriter writer = new StringWriter()
        new XMLWriter(writer, format).writeNode(_new)
        return writer.toString()
    }

    /**
     * Extract an appropriate data type from a path
     * @param path
     * @param result
     * @param order should order be retained
     * @return the object update with the new value
     */
    private Object process(GPathResult path, Object result, boolean order) {
        String name = PersistUtils.lowerCamelCase(path.name())
        MetaBeanProperty mbp = result.metaClass.getMetaProperty(name)
        if (! mbp) {
            def m = result.class.name
            throw new Exception("Unable to find property '${name}' on '${m}'")
        }
        def res = executeCreate(path, mbp.type, result[name])
        result[name] = res
        if (! order || ! res || ! result.elements.contains(name)) return result
        if (res in Collection) result.addOrder(name, res[res.size() - 1])
        else result.addOrder(name, res)
        return result
    }

    /**
     * Create a new object or load the existing from a database
     * @param path current element
     * @return
     */
    private Object createObj(GPathResult path) {
        Class _class = getDomainClass(path.name())
        assert _class
        return _class.newInstance()
    }

    /**
     * Create or update all the children objects before finally 
     * creating or updating the parent
     * @param obj the object to be saved
     * @param xmlc the xml comparison
     * @return
     */ 
    private Object storeAll(obj, XmlCompare xmlc) {
        if (! obj.metaClass.hasProperty(obj, attstr)) return
        if (! obj.isNew() && ! xmlc.isChanged(classes, obj)) return obj
        def msg = "XmlSerializer().storeAll() : "
        def s = session
        def orig = obj.id ? s.get(obj.class, obj.id) : null
        def code = {
            def collup = false
            def val = obj[it]
            def date = "lastUpdated".equals(it) || "created".equals(it)
            if (date || ILN.equals(it)) return
            if (val && (val in Collection)) {
                def nids = val.collect { o -> o.id ? o.id.toString() : null }
                def oids = idorder(it, orig)
                def up = orig ? orig[it] : val
                val.eachWithIndex { c, i -> 
                    if (c.isNew() || xmlc.isChanged(classes, c)) {
                        up[i] = storeAll(c, xmlc)
                        collup = true
                    } else if (moved(oids, nids, i)) {
                        up[i] = s.get(c.class, Long.parseLong(nids[i])) 
                        collup = true
                    }
                }
                collup = collup || val.size() != up.size()
                // shrink update, in case items have been removed...
                while (up.size() > val.size()) up.remove(up.size() - 1)
                val = up
                Log.debug msg + "old ${oids}"
                Log.debug msg + "new ${nids}"
                //def gids = val.collect { o -> o.id ? o.id.toString() : null }
                //Log.debug msg + "gen ${gids}"
            }
            if (val && classes.contains(val.class)) {
                val = storeAll(val, xmlc)
                if (! orig) obj[it] = val
            }
            if (! orig) return
            def current = orig[it]
            if (current == null && val == null) return
            if (simpleAndMatch(current, val, classes)) return
            if (! collup && ! xmlc.isChanged(classes, val)) return
            orig[it] = val
            if (Log.isDebugEnabled()) Log.debug msg + "update timestamp " +
                "on ${obj.class} for field ${it}. New Val [${val}], " + 
                "old [${current}]"
            if (! obj.isNew()) obj.updated()
            if (! orig.isNew()) orig.updated()
        }
        obj.attributes.each(code) 
        obj.elements.each(code) 
        def name = obj.class.name
        Log.debug msg + " save [${name}], id [${obj.id}]"
        def doiln = orderchange(orig, obj)
        if (doiln && orig) orig.order(obj.order())
        if (orig) obj = orig
        else obj.markNew() 
        if (doiln) interleave(obj)
        if (doiln && obj.interleave) s.save(obj.interleave)
        s.save(obj)
        return obj
    }

    /**
     * Do simple fields match?
     * @param orig
     * @param _new
     * @param classes
     * @return
     */
    private boolean simpleAndMatch(orig, _new, classes) {
        if (orig == null || (orig in Collection)) return false
        if (classes.contains(orig.class)) return false
        return orig == _new
    }

    /**
     * Extract ids from existing obj cache
     * @param type
     * @param obj
     * @return
     */
    private List idorder(String type, Object obj) {
        if (! obj || ! obj.id) return []
        def regex = /<${type} .*id="([0-9]*)".*>/
        def r = UpdateCache.get(obj) =~ regex
        if (r.size() == 0) return []
        return (0..(r.size() - 1)).collect { r[it][1] }
    }

    /**
     * Has an integer moved between two lists
     * @param one
     * @param two
     * @param spot
     * @return
     */
    private boolean moved(List one, List two, int spot) {
        if (spot >= one.size() || spot >= two.size()) return true
        return one[spot] != two[spot]
    }

    /**
     * Generate the interleave string
     * @param object
     */
    private void interleave(object) {
        if (! object.order()) return
        if (! object.interleave) {
            object.interleave = getDomainClass(ILN).newInstance()
        }
        object.interleave.ordering = object.order().collect { 
            def val = it[1]
            if (classes.contains(val.class) && ! val.id) {
                // have to save for the hashcode
                session.save(val)
                val.markNew()
            }
            return it[0] + ":" + val.hashCode()
        }.toString().replace("[","").replace("]","").replace(" ","")
        //session.save(object.interleave)
    }

    /**
     * Has interleave order changed?
     * @param orig
     * @param obj
     * @return
     */
    private boolean orderchange(orig, obj) {
        if (! obj.metaClass.hasProperty(obj, ILN)) return false
        def exist = orig && orig.interleave && orig.interleave.id
        if (! exist) return true
        def str1 = orig.interleave.ordering.replaceAll(dateRep, "")
        def str2 = obj.interleave.ordering.replaceAll(dateRep, "")
        if (str1 == str2) return false
        Log.debug "XmlSerializer.orderchange() : go [${str1}] vs [${str2}]"
        return true
    }

    /**
     * For some reason, sybase returns column numbers as aliases... 
     * sometimes
     * @param aliases
     * @return
     */
    private boolean badAlias(aliases) {
        if (! aliases) return true
        boolean bad = true
        aliases.eachWithIndex { kid, i -> if (kid != i + "") bad = false }
        return bad
    }

    /**
     * Convenience
     * @param obj
     * @param res result list
     * @return list of parents
     */
    private List loadParents(obj, List res=[]) {
        if (! obj.parents) return res
        obj.parents.each() { parent -> 
            String query = PersistUtils.lowerCamelCase(obj.class.name)
            query = "from ${parent} where ${query}.id = ${obj.id}"
            Log.debug "UpdateCache.updateCache() : process [${query}]"
            session.createQuery(query).list().each { load ->
                if (! res.contains(load)) loadParents(load, res)
                res.add(load)
            }
        }
        return res
    }

    /**
     * Wrapper around object generation methods
     * @param path
     * @param type
     * @param current current value
     * @return
     */
    private Object executeCreate(GPathResult path, Class type, Object current) {
        boolean array = type.array
        if (array) type = type.componentType
        // one-to-one link to an existing class
        if (classes.contains(type)) return genobj(path, null)
        String create = "create${type.simpleName}"
        Class[] classes = [GPathResult.class, type]
        if (! mclass.hasMetaMethod(create, classes))
            throw new Exception("No method to create type ${type.simpleName}")
        Object[] args = [path, current]
        if (! array) return this.invokeMethod(create, args)
        // handle an array
        Object[] _new = [this.invokeMethod(create, [path, null])]
        return ArrayUtils.append(_new, current, type)
    }

    /**
     * Create a string from a node
     * @param p
     * @param e existing
     */
    private String createString(GPathResult p, String e) { return p.text() }

    /**
     * Create a date from a node
     * @param path
     * @param e existing
     */
    private Date createDate(GPathResult path, Date e) { 
        // necessary because dateformat is not thread safe
        def dateFormat = new SimpleDateFormat(sdateFormat)
        return path.text() ? dateFormat.parse(path.text()) : null
    }

    /**
     * Create a boolean value from a node
     * @param path
     * @param e existing
     */
    private Boolean createBoolean(GPathResult path, Boolean e) { 
        //assume the existence of the element means true
        if (! path.text()) return true
        return Boolean.valueOf(path.text()) 
    }

    /**
     * Create an integer value from a string
     * @param path
     * @param e existing
     */
    private Integer createInteger(GPathResult path, Integer e) { 
        return Integer.valueOf(path.text()) 
    }

    /**
     * Create a long value from a string
     * @param path
     * @param e existing
     */
    private Long createLong(GPathResult path, Long e) { 
        return Long.valueOf(path.text()) 
    }

    /**
     * Create or update a set
     * @param path
     * @param e existing
     */
    private Set createSet(GPathResult path, Set exist) {
        if (! exist) exist = new HashSet()
        exist.add(genobj(path, null))
        return exist
    }
    
    /**
     * Create or update a list
     * @param path
     * @param e existing
     */
    private List createList(GPathResult path, List exist) {
        if (! exist) exist = new Vector()
        exist.add(genobj(path, null))
        return exist
    }

    /**
     * Create a date string
     */
    private String stringDate(Date date) {
        // necessary because dateformat is not thread safe
        def dateFormat = new SimpleDateFormat(sdateFormat)
        return date ? dateFormat.format(date) : null
    }

    /**
     * Create a timestamp string
     */
    private String stringTimestamp(Date date) {
        // necessary because dateformat is not thread safe
        def dateFormat = new SimpleDateFormat(sdateFormat)
        return date ? dateFormat.format(date) : null
    }

    /**
     * Hack for worthless sybase
     */
    private String stringSybTimestamp(Date date) {
        // necessary because dateformat is not thread safe
        def dateFormat = new SimpleDateFormat(sdateFormat)
        return date ? dateFormat.format(date) : null
    }

    /**
     * Necessary to avoid error messages
     */
    private String stringString(String _string) { return _string }

    /**
     * Necessary to avoid error messages
     */
    private String stringBoolean(Boolean bool) { return bool.toString() }

    /**
     * Necessary to avoid error messages
     */
    private String stringLong(Long _long) { return _long.toString() }

    /**
     * Necessary to avoid error messages
     */
    private String stringInteger(Long _long) { return _long.toString() }

}
