package org.chi.persistence.ant

import org.apache.commons.codec.digest.DigestUtils
import org.apache.tools.ant.BuildException
import org.chi.util.Log

/**
 * Bulk data import tool
 * @author rgrey
 */
class BulkImport extends AntBase {

    def File bulkImportXml
    def history = [:]
    def allowDupes = []
    def count = [:]

    private XmlParser parse = new XmlParser()

    @Override
    public void execute() throws BuildException {
        assert bulkImportXml && bulkImportXml.exists()
        Log.console "BulkImport() : import [${bulkImportXml}]"
        def root = parse.parse(bulkImportXml)
        def hibCfg = checkParam(root, "@hibernate-cfg")
        def schemaDir = checkFile(root, "@schema-dir")
        def queryFile = checkParam(root, "@query-file")
        if (root["@allow-dupes"]) {
            root["@allow-dupes"].split(",").each() { allowDupes.add(it.trim()) }
        }
        loadData(root, schemaLoader(hibCfg, schemaDir).load(queryFile, true))
        printCounts()
    }

    /**
     * Recursively process all the children of the root element
     * @param data
     * @param serial
     * @return string value of the child
     */
    private loadData(Node data, serial) {
        def replacements = [:]
        data.children().eachWithIndex() { it, i -> 
            def updated = loadData(it, serial)
            if (updated) {
                def parsed = parse.parse(new StringReader(updated))
                replacements.put(i, parsed)
            }
        }
        replacements.each() { index, val ->
            data.children().remove(index)
            data.children().add(index, val)
        }
        return processChild(data, serial)
    }

    /**
     * Process an individual child, only save the children that are new...
     * @param child
     * @param serial
     * @return the string value of the element
     */
    private String processChild(child, serial) {
        String name = child.name()
        def _class = serial.getDomainClass(name)
        if (! _class || ! _class.schema) return null
        String str = serialize(child)
        String hash = DigestUtils.md5Hex(str)
        if (! history.get(hash)) {
            try {
                str = serial.save(str.replaceAll("\n",""))
            } catch (Exception e) {
                Log.console "Unable to save ${str}"
                throw e
            }
            if (! allowDupes.contains(name)) history.put(hash, str)
            Integer upd = count.get(name) ? count.get(name) : 0
            count.put(name, ++upd)
            checkCounts()
        }
        return history.get(hash)
    }

    // skip element bodies
    private void loadData(String data, serial) {}

    /**
     * Does a config parameter exist?
     * @param config
     * @param name
     * @return the value of the parameter
     */
    private String checkParam(config, name) {
        def val = config[name]
        Log.console "BulkImport.checkParam() : check [${name}]"
        assert val
        return val
    }

    /**
     * Does a file defined in a config parameter exist?
     * @param config
     * @param name
     * @return the file
     */
    private File checkFile(config, name) {
        def file = new File(checkParam(config, name))
        assert file.exists()
        return file
    }

    /**
     * Convert a node into a string
     * @param node
     * @return
     * TODO this is ugly, figure out why XmlParser unescapes...
     */
    private String serialize(node) {
        // remove unescaped < and >, yuck
        node.depthFirst().each() {
            if (! textOnly(it)) return
            def text = it.text()
            if (text.contains(">") || text.contains("<")) {
                it.setValue(text.replaceAll(">", "&gt;").replaceAll("<","&lt;"))
            }
        }
        StringWriter writer = new StringWriter()
        new XmlNodePrinter(new PrintWriter(writer), "").print(node)
        def result = writer.toString().replaceAll("&","&amp;")
        result = result.replaceAll("&amp;([\\w,#]{2,6};)", "&\$1")
        return result
    }

    /**
     * Check to see if it's time to print the status
     */
    private void checkCounts() {
        def total = 0
        count.each { k, v -> total += v }
        if (total % 100 == 0) printCounts()
    }

    /**
     * Print the status
     */
    private void printCounts() {
        Log.console "BulkImport() : ------------------------"
        count.each() { k, v -> 
            Log.console "BulkImport() : processed ${v} [${k}]" 
        }
    }

    /**
     * Is this node text only?
     * @param node
     * @return 
     */
    private boolean textOnly(node) {
        def kid = node.children()
        def vals = history.values()
        return kid.size() == 1 && (kid[0] in String) && ! vals.contains(kid[0])
    }

}
