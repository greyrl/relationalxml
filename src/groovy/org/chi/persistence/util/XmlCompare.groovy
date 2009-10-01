package org.chi.persistence.util

import org.chi.persistence.PersistUtils
import org.chi.util.Log
import org.chi.util.FileUtil

import java.util.regex.Pattern
import static java.util.regex.Pattern.MULTILINE

/**
 * Compare XML string objects and mark changes by object id
 * @author rgrey
 */
class XmlCompare {

    private static final close = /.*<\/.*/
    private static final open = / *<\w+.*/
    private static final regex = /<([\w,-]+) .*id="([0-9]*)".*>/
    private static final wsp = Pattern.compile("^\\s*\$\\n", MULTILINE)
    private static final writexml = System.getProperty("pers.xmlcompare")
    private static final tmpKey = "java.io.tmpdir"
    private static final tmpDir = System.getProperty(tmpKey) + "/xmlcompare/"
    { new File(tmpDir).mkdirs() }

    private all
    private chain = []
    private gen = 0
    private idx = -1
    private id
    private last = 0
    private lastgen = 0
    private String[] split
    private Set reord = []
    private Set res = []

    /**
     * Compare two xml strings returning an XmlCompare difference
     * @param xml1
     * @param xml2
     * @return
     */
    public static XmlCompare compare(xml1, xml2) {
        if (! xml1 || ! xml2) { 
            Log.console "XmlCompare.compare() : null left or right" 
            Log.debug "XmlCompare.compare() : left ${xml1}"
            Log.debug "XmlCompare.compare() : right ${xml2}"
            return new XmlCompare()
        }
        if (writexml) new File(tmpDir).listFiles().each { it.delete() }
        def cp1 = new XmlCompare(xml1, "old").catchup(null)
        def cp2 = new XmlCompare(xml2, "new").catchup(null)
        while (cp1.next(cp2)) {}
        Log.debug "XmlCompare.compare() : [${cp1.res}]"
        cp1.all = cp1.reord + cp1.res
        writestr("changed", cp1.res.toString()) 
        writestr("reordered", cp1.reord.toString()) 
        return cp1
    }

    /**
     * Remove an unecessary elements and spaces from the xml
     * @param str
     * @return
     */
    public static prepxml(String str) {
        if (! str) return str
        str = str.replaceAll("<.*created.*>","")
        str = str.replaceAll("<.*last-updated.*>","")
        return wsp.matcher(str).replaceAll("").trim()
    }

    /**
     * Write an xml document to the temporary directory, if enabled
     * @param key
     * @param s
     */
    public static writestr(key, String s) {
        if (! writexml || ! key || ! s) return 
        def ts = System.currentTimeMillis()
        FileUtil.writeFile(new File(tmpDir + key + "." + ts + ".xml"), s)
    }

    /**
     * Convenience
     * @param key
     * @param s
     * @param prep
     */
    public static prepandwritexml(key, String s, boolean prep) {
        def res = writexml || prep ? prepxml(s) : s
        writestr(key, res)
        return prep ? res : s
    }

    /**
     * Default constructor
     * @param xml
     * @param filekey
     */
    def XmlCompare(xml=null, filekey=null) {
        if (xml == null) return
        xml = prepandwritexml(filekey, xml, true)
        split = xml.split("\n")
    }

    /**
     * Move to element with matching id
     * @param id
     * @return
     */
    def catchup(id=null) {
        for (idx = last ; idx < split.length ; idx++) {
            this.id = getid(idx)
            if (! this.id) continue
            if (id && ! (this.id.equals(id))) continue
            if (lastgen != gen) last = idx
            break
        }
        lastgen = gen
        return this
    }

    /**
     * Make the next line comparison
     * @param xmlc
     * @return
     */
    def next(XmlCompare xmlc) {
        if (getid(idx)) this.id = getid(idx)
        if (xmlc.getid(xmlc.idx)) xmlc.id = getid(idx)
        if (getbadid(this.id, xmlc)) reord.add(chain[-2])
        if (! this.id.equals(xmlc.id)) {
            if (chain.size() > 1) res.addAll(chain[0..-2])
            if (chain.size() > 1) reord.add(chain[-2])
            xmlc.catchup(this.id)
        }
        def l1 = line(xmlc)
        def l2 = xmlc.line()
        if (! l1.equals(l2)) {
            def d = Log.isDebugEnabled()
            if (d) Log.debug "XmlCompare.next(): hit ${l1} vs. ${l2}"
            res.addAll(chain)
        } 
        if (l2 ==~ open) xmlc.gen++
        if (l2 ==~ close) xmlc.gen--
        return xmlc.inc() && inc() < split.length
    }

    /**
     * Get the current line id
     * @param cnt
     * @return
     */
    def getid(cnt) {
        if (cnt >= split.length) return null
        def match = split[cnt] =~ regex
        if (match.size() == 0 || match[0].size() < 2) return null
        def id = match[0][2] + ":" + match[0][1].trim()
        if (! chain || ! chain[-1].equals(id)) chain.add(id)
        return id
    }

    /**
     * Check if ids match
     * @param id
     * @param xmlc
     * @return
     */
    def getbadid(id, xmlc) {
        if (xmlc.idx >= xmlc.split.length) return false 
        def id2 = xmlc.getid(xmlc.idx)
        return id2 != null && id2 != id && chain.size() > 1
    }

    /**
     * Return the current line
     * @param xmlc
     * @return
     */
    def line(XmlCompare xmlc=null) {
        def str = sline()
        if (! str) return null
        def lt = lasttype()
        // TODO deal with self closed tags
        if (lt && str.contains("</" + lt + ">")) {
            // this suggests an addition
            if (xmlc && str != xmlc.sline()) res.addAll(chain)
            chain.pop()
        }
        return str
    }

    /**
     * Convenience
     * @return
     */
    def sline() { 
        return idx != null && idx < split.length ? split[idx] : null 
    }

    /**
     * Convenience
     * @return
     */
    def inc() { return idx != null ? ++idx : idx }

    /**
     * Convenience
     * @return
     */
    def lasttype() { return chain ? chain[-1].split(":")[1] : null }

    /**
     * Is this object in the changed list
     * @param classes
     * @param obj
     * @return
     */
    boolean isChanged(classes, obj) {
        if (! obj) return true
        if (obj in Collection) return false
        if (! classes.contains(obj.class) || ! obj.id) return true
        def name = PersistUtils.addDashes(obj.class.name)
        def str = obj.id.toString() + ":" + name
        return all == null || res.contains(str) || reord.contains(str)
    }

    /**
     * Is this object in the reordered list
     * @param obj
     * @return
     */
    boolean isReorder(obj) {
        if (! obj || ! obj.id) return false
        def name = PersistUtils.addDashes(obj.class.name)
        return reord.contains(obj.id.toString() + ":" + name)
    }

    /**
     * Add an object to the changed list
     * @param classes
     * @param obj
     */
    def add(classes, obj) {
        if (! classes.contains(obj.class) || ! obj.id) return xmlc
        def name = PersistUtils.addDashes(obj.class.name)
        res.add(obj.id.toString() + ":" + name)
    }

    /**
     * Find all the objects that have been updated
     * @param obj
     * @param result
     * @return
     */
    def findUpdated(obj, result=[]) {
        if (obj in Collection) return obj.each { findUpdated(it, result) }
        if (! obj) return result
        def name = PersistUtils.addDashes(obj.class.name)
        if (all == null || all.contains(obj.id.toString() + ":" + name)) 
            result.add(obj)
        obj.children.each { findUpdated(obj[it], result) }
        return result
    }

    // TODO remove
    Set getAll() { return all }

}
