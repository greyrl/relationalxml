package org.chi.persistence 

import org.chi.persistence.util.UpdateCache
import org.chi.util.Log
import org.junit.Before

import groovy.util.GroovyTestCase

import java.util.concurrent.CyclicBarrier

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class RelaxSchemaGroovyTest extends GroovyTestCase {

    final String SAMPLE = '''<?xml version="1.0"?>
        <address-book>
            <name>robs address book</name>
            <email>rgrey@chi.ucsf.edu</email>
            <enabled/>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <status>new</status>
            <statistics size="100" accessed="100"/>
            <favorite user-id="25"><name>gary</name></favorite>
            <favorite user-id="30"><name>joe</name></favorite>
            <remover><title>remove</title></remover>
        </address-book>
    '''

    final String INTERLEAVE_SAMPLE = '''<?xml version="1.0"?>
        <mixed-kids>
            <title>test</title>
            <kid-one><title>kid1-1</title></kid-one>
            <kid-two><title>kid2-1</title></kid-two>
            <kid-one><title>kid1-2</title></kid-one>
            <kid-one><title>kid1-3</title></kid-one>
            <kid-three><title>kid1-3</title></kid-three>
        </mixed-kids>
    '''
    final INTERLEAVE_TITLES = ["kid1-1", "kid2-1", "kid1-2", "kid1-3", "kid1-3"]
    final REORDER_TITLES = ["kid2-1", "kid1-1", "kid1-2", "kid1-3", "kid1-3"]

    final String COMPLEX_SAMPLE = '''<?xml version="1.0"?>
        <levelone>
            <leveltwo>
                <levelthree>
                    <name>child</name>
                </levelthree>
                <levelthree>
                    <name>kid</name>
                </levelthree>
            </leveltwo>
            <leveltwo>
                <levelthree>
                    <name>another</name>
                </levelthree>
            </leveltwo>
        </levelone>
    '''

    final String COMPLEX_WRAP = '''<?xml version="1.0"?>
        <levelone>
            <leveltwo>
                replace
            </leveltwo>
        </levelone>
    '''

    final String BAD_SAMPLE = '''<?xml version="1.0"?>
        <address-book>
            <name>
                blaablaablaablaablaablaablaablaablaablaablaab
                blaablaablaablaablaablaablaablaablaablaablaab
                blaablaablaablaablaablaablaablaablaablaablaab
                blaablaablaablaablaablaablaablaablaablaablaab
            </name>
        </address-book>
    '''

    final String LARGE_SAMPLE = '''<?xml version="1.0"?>
        <address-book>
            <name>robs address book</name>
            <email>rgrey@chi.ucsf.edu</email>
            <enabled/>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <address type="home">
                <street>1382 5th Ave</street>
                <street>Apt. 2</street>
                <street>Bldg. 14</street>
                <street>Station. 12</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip-code>94122</zip-code>
                <region>USA</region>
                <region>North America</region>
            </address>
            <status>new</status>
            <statistics size="100" accessed="100"/>
            <favorite user-id="25"><name>gary</name></favorite>
            <favorite user-id="30"><name>joe</name></favorite>
            <remover><title>remove</title></remover>
        </address-book>
    '''

    final String BOOL_SAMPLE = '''<?xml version="1.0"?>
        <bool-test>
            <name>boolean test</name>
        </bool-test>
    '''

    private static builder = 
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private static NS = XPathConstants.NODESET
    private static xpath = XPathFactory.newInstance().newXPath()
    private static XmlSerializer serial

    @Before void setUp() throws Exception {
        if (serial) return
        def sl = new SchemaLoader("hibernate.cfg.xml", "test/schema/relax", 
            RelaxSchema.class)
        serial = sl.load("queries.xml", true)
        assert serial
    }

    void testSave() {
        String result = serial.save(SAMPLE)
        def obj = serial.extract(new StringReader(result), "AddressBook")
        assert obj.id > 0
    }

    void testSaveAndAddChild() {
        String result = serial.save(SAMPLE)
        def obj = serial.extract(new StringReader(result), "AddressBook")
        assert obj && obj.address && obj.address.size() == 1
        println "Initial address size ${obj.address.size()}"
        def _new = serial.extract(new StringReader(SAMPLE), "AddressBook")
        _new = _new.address[0]
        _new.street[0] = "Clement and 43rd"
        obj.address.add(_new)
        result = serial.save(serial.serialize(obj))
        obj = serial.extract(new StringReader(result), "AddressBook")
        assert obj && obj.address && obj.address.size() == 2
        assert obj.address[1].street[0] == "Clement and 43rd"
        println "Added address size ${obj.address.size()}"
    }

    void testUpdate() {
        String result = serial.save(SAMPLE)
        def obj = serial.extract(new StringReader(result), "AddressBook")
        assert obj
        obj.name = "set another name"
        serial.save(serial.serialize(obj))
        def root = serial.sqlLoad("query.by.id", obj.id.toString())
        root = new XmlParser().parseText(root)
        assert root['address-book'] && root['address-book'].size() == 1
        assert "set another name".equals(root['address-book'][0].name.text())
    }

    void testUpdateTiming() {
        String result = serial.save(LARGE_SAMPLE)
        def obj = serial.extract(new StringReader(result), "AddressBook")
        assert obj
        long timing = System.currentTimeMillis()
        obj.name += "1"
        obj.address[0].street = "1"
        serial.save(serial.serialize(obj))
        timing = Log.timing("testUpdateTiming", timing, "save1")
        obj.name += "1"
        obj.address[0].street = "12"
        serial.save(serial.serialize(obj))
        timing = Log.timing("testUpdateTiming", timing, "save2")
        obj.name += "1"
        obj.address[0].street = "123"
        serial.save(serial.serialize(obj))
        timing = Log.timing("testUpdateTiming", timing, "save3")
    }


    void testUpdateAttribute() {
        String result = serial.save(SAMPLE)
        def obj = serial.extract(new StringReader(result), "AddressBook")
        assert obj
        obj.statistics.accessed = 200
        serial.save(serial.serialize(obj))
        def root = serial.sqlLoad("query.by.id", obj.id.toString())
        root = new XmlParser().parseText(root)
        def accessed = root['address-book'].statistics.@accessed[0]
        assert accessed && accessed.equals("200")
    }

    void testUpdateChild() {
        String result = serial.save(SAMPLE)
        def obj = serial.extract(new StringReader(result), "AddressBook")
        assert obj && obj.address && obj.address.size() == 1
        def fav = obj.favorite[0]
        // rorce a load of the cached value
        serial.sqlLoad("query.fav.by.id", fav.id.toString())
        fav.name = "ice cream!"
        serial.save(serial.serialize(fav))
        def newfav = serial.sqlLoad("query.by.id", obj.id.toString())
        newfav = new XmlParser().parseText(newfav)["address-book"].favorite[0]
        assert newfav.name && newfav.name.text().equals(fav.name)
    }

    void testUpdateChildThroughParent() {
        String result = serial.save(SAMPLE)
        def obj = serial.extract(new StringReader(result), "AddressBook")
        obj = obj.statistics
        result = result.replace(
            "statistics accessed=\"100\"","statistics accessed=\"1000\"");
        result = serial.save(result)
        result = serial.sqlLoad("query.stats.by.id", obj.id.toString())
        println "result ${result}"
        def reg = result =~ /accessed="([0-9]*)"/
        assert "1000" == reg[0][1]
    }

    void testUpdateComplexChildThroughParent() {
        def result = serial.save(COMPLEX_SAMPLE)
        def obj = serial.extract(new StringReader(result), "Levelone")
        def id = obj.leveltwo[0].levelthree[0].id.toString()
        def name = obj.leveltwo[0].levelthree[0].name + "NEW"
        result = serial.sqlLoad("query.lthree.by.id", id)
        result = result.replaceAll("<name>.*</name>","<name>${name}</name>")
        result = result.replaceAll("<.*query.*>","")
        serial.save(result)
        result = serial.sqlLoad("query.lone.by.id", obj.id.toString())
        assert result.contains(name)
    }

    void testReuse() {
        def result = new StringReader(serial.save(SAMPLE))
        def obj = serial.extract(result, "AddressBook").statistics
        def id = obj.id
        obj = SAMPLE.replaceAll("<statistics.*>", serial.serialize(obj))
        result = new StringReader(serial.save(obj))
        assert id == serial.extract(result, "AddressBook").statistics.id
    }

    void testCopy() {
        // test standard
        String result = serial.save(SAMPLE)
        def obj = serial.extract(new StringReader(result), "AddressBook")
        def ids = getIds(obj, null)
        result = serial.copy("AddressBook", ids[0])
        obj = serial.extract(new StringReader(result), "AddressBook")
        def ids2 = getIds(obj, null)
        ids.eachWithIndex { id, index -> assert id != ids2[index] }
        // test interleave
        result = serial.save(INTERLEAVE_SAMPLE)
        obj = serial.extract(new StringReader(result), "MixedKids")
        result = serial.copy("MixedKids", obj.id)
        obj = serial.extract(new StringReader(result), "MixedKids")
        def str = serial.sqlLoad("query.multi.by.id", obj.id.toString())
        assert extract(str)[1].equals(INTERLEAVE_TITLES)
    }

    void testQuery() {
        serial.save(SAMPLE)
        def root = new XmlParser().parseText(serial.sqlLoad("query.all.entries"))
        assert root["address-book"].size() > 0
        println "Found ${root["address-book"].size()} address book(s)"
    }

    void testNativeQuery() {
        String result = serial.save(SAMPLE)
        def id = serial.extract(new StringReader(result), "AddressBook").id
        serial.sqlLoad("query.native.by.id", id.toString())
    }

    void testQueryMultiple() {
        String result = serial.save(SAMPLE)
        def id = serial.extract(new StringReader(result), "AddressBook").id
        assert id > 0
        def root = new XmlParser().parseText(
            serial.sqlLoad("query.by.id", id.toString()))
        assert root["address-book"].size() == 1
        def addressBook = root['address-book'][0]
        assert addressBook.@id.toString().equals(id.toString())
        println "Load ID : ${addressBook.@id} Query ID : ${id}"
    }

    void testQueryList() {
        String result = serial.save(SAMPLE)
        def id = serial.extract(new StringReader(result), "AddressBook").id
        String[] params = [id.toString(), "12", "14", "25", "32"]
        serial.sqlLoad("query.multiple", params)
    }

    void testSwitchOrder() {
        String result = serial.save(SAMPLE)
        def obj = serial.extract(new StringReader(result), "AddressBook")
        assert obj && obj.favorite && obj.favorite.size() == 2
        def created = obj.address[0].created
        def dateobj = obj.lastUpdated
        def dateadd = obj.address[0].lastUpdated
        def datefav1 = obj.favorite[0].lastUpdated
        def datefav2 = obj.favorite[1].lastUpdated
        obj.favorite = [ obj.favorite[1], obj.favorite[0] ] 
        obj.favorite[0].name = "jerryg"
        Thread.sleep(1000)
        serial.save(serial.serialize(obj))
        result = serial.sqlLoad("query.by.id", obj.id.toString())
        result = result.replaceAll("<.*query.*>","")
        obj = serial.extract(new StringReader(result), "AddressBook")
        assert ! obj.lastUpdated.equals(dateobj)
        assert ! obj.favorite[0].lastUpdated.equals(datefav1)
        assert obj.favorite[1].lastUpdated.equals(datefav2)
        assert obj.address[0].lastUpdated.equals(dateadd)
        assert obj.address[0].created.equals(created)
    }

    void testInterleave() {
        def result = serial.save(INTERLEAVE_SAMPLE)
        def obj = serial.extract(new StringReader(result), "MixedKids")
        def str = serial.sqlLoad("query.multi.by.id", obj.id.toString())
        result = extract(str)
        assert result[1].equals(INTERLEAVE_TITLES)
        def mixed = str.split("\n").findAll { it.contains("mixed-kids") }
        assert mixed.size() == 2
        def reorder = mixed[0] + "\n<title>test</title>\n"
        [2,1,3,4,5].each { reorder += result[0].item(it) }
        reorder += "\n" + mixed[1]
        reorder = reorder.replace('<?xml version="1.0" encoding="UTF-8"?>',"")
        result = serial.save(reorder)
        assert extract(result)[1].equals(REORDER_TITLES)
    }

    void testRemove() {
        // remove child and verify against parent
        String result = serial.save(SAMPLE)
        def obj = serial.extract(new StringReader(result), "AddressBook")
        def id = obj.id.toString()
        obj = obj.address[0]
        serial.remove(obj.class.name, obj.id)
        result = serial.sqlLoad("query.by.id", id).replaceAll("<.*query.*>","")
        obj = serial.extract(new StringReader(result), "AddressBook")
        assert obj.address == null || obj.address.size() == 0
        // now try a single value
        obj = obj.remover
        serial.remove(obj.class.name, obj.id)
        result = serial.sqlLoad("query.by.id", id).replaceAll("<.*query.*>","")
        obj = serial.extract(new StringReader(result), "AddressBook")
        assert obj.remover == null 
    }

    void testError() {
        try {
            serial.save(BAD_SAMPLE)
            fail "it saved..."
        } catch (ParseException pe) {
            println pe.message
        }
    }

    void testLock() {
        String result = serial.save(SAMPLE)
        CyclicBarrier cb = new CyclicBarrier(3) 
        def o1 = SaveQueue.create(serial, result, cb)
        def o2 = SaveQueue.create(serial, result, cb)
        def o3 = SaveQueue.create(serial, result, cb)
        while (! o1.done || ! o2.done || ! o3.done) Thread.sleep(100)
        assert o1.pass && o2.pass && o3.pass
    }

    void testDeadlock() {
        // attempt to produce a deadlock
        def result = serial.save(COMPLEX_SAMPLE)
        def other = result.replaceAll("<.*created.*>","")
        other = other.replaceAll("<.*last-updated.*>","")
        other = other.replaceAll("<.*levelone.*>","")
        other = other.replaceAll("<.*leveltwo.*>","")
        other = COMPLEX_WRAP.replace("replace", other)
        CyclicBarrier cb = new CyclicBarrier(3) 
        def o1 = SaveQueue.create(serial, result, cb)
        def o2 = SaveQueue.create(serial, other, cb)
        def o3 = SaveQueue.create(serial, other, cb)
        while (! o1.done || ! o2.done || ! o3.done) Thread.sleep(100)
        assert o1.pass && o2.pass && o3.pass
    }

    void testBoolean() {
        def result = serial.save(BOOL_SAMPLE)
        def obj = serial.extract(new StringReader(result), "BoolTest")
        obj.available = false
        serial.save(serial.serialize(obj))
        obj = serial.session.get(obj.class, obj.id)
        assert obj && obj.available != null
    }
    
    /**
     * Extract titles from results
     * @param xml
     * @return list
     */
    def extract(String xml) {
        def inputStream = new ByteArrayInputStream(xml.bytes)
        def root = builder.parse(inputStream).documentElement
        def nodes = xpath.evaluate("//*[@id]", root, NS)
        def titles = nodes.collect { return xpath.evaluate("title/text()", it) }
        // remove the empty parent title
        titles.remove(0)
        return [nodes, titles]
    }

    /**
     * Get all the ids in sequential order
     * @param object
     * @param result
     * @return collection of ids
     */
    def getIds(obj, result) {
        if (! result) result = []
        result.add(obj.id)
        obj.children.each { kid ->
            kid = obj[kid]
            if (kid instanceof Collection) kid.each { getIds(it, result) }
            else getIds(kid, result)
        }
        return result
    }

}

/**
 * Enter save method concurrently
 * @author rgrey
 */
class SaveQueue extends Thread {

        boolean done = false
        boolean pass = false

        private barrier
        private result
        private serial

        /**
         * Convenience
         * @param serial
         * @param result
         * @param barrier
         * @return
         */
        static create(serial, result, barrier) {
            SaveQueue sq = new SaveQueue(serial, result, barrier)
            new Thread(sq).start()
            return sq
        }

        /**
         * Constructor
         * @param serial
         * @param result
         * @param barrier
         * @return
         */
        def SaveQueue(serial, result, barrier) {
            this.barrier = barrier
            this.result = result
            this.serial = serial
        }

        void run() {
            try { 
                barrier.await()
                serial.save(result) 
                pass = true
            } finally {
                done = true
            }
        }

}

