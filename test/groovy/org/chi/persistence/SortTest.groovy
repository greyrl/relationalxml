package org.chi.persistence 

import org.chi.persistence.sort.Group
import org.chi.persistence.sort.MultiSort

class SortTest extends GroovyTestCase {

    final String SORT_SAMPLE = '''<?xml version="1.0"?>
        <elements>
            <sort>
                <sort1>addressone</sort1>
                <sort2>rgrey@chi.ucsf.edu</sort2>
            </sort>
            <sort>
                <sort1>addresstwo</sort1>
                <sort2>rgrey@chi.ucsf.edu</sort2>
                <sort-child><name>def</name></sort-child>
            </sort>
            <sort>
                <sort1>addresstwo</sort1>
                <sort2>rgrey@chi.ucsf.edu</sort2>
                <sort-child><name>abc</name></sort-child>
            </sort>
            <sort>
                <sort1>addresstwo</sort1>
                <sort2>rgrey@chi.ucsf.edu</sort2>
            </sort>
            <sort>
                <sort1>addressthree</sort1>
                <sort2>joe@chi.ucsf.edu</sort2>
            </sort>
        </elements>
    '''

    final String PREDICATE_SORT_SAMPLE = '''<?xml version="1.0"?>
        <elements>
            <sort>
                <group type="nogroup">x</group>
                <group type="group">a</group>
            </sort>
            <sort>
                <group type="group">c</group>
                <group type="nogroup">f</group>
            </sort>
            <sort>
                <group type="group">d</group>
                <group type="nogroup">e</group>
            </sort>
        </elements>
    '''

    final String COMPLEX_SORT_SAMPLE = '''<?xml version="1.0"?>
        <elements>
            <sort>
                <sort-child> 
                    <name>orange</name>
                    <type id="fruit"/>
                </sort-child>
                <test>d sort</test>
            </sort>
            <sort>
                <sort-child>
                    <name>lettuce</name>
                    <type id="vegetable"/>
                </sort-child>
                <sort-child>
                    <name>pear</name>
                    <type id="fruit"/>
                </sort-child>
                <test>b sort</test>
            </sort>
            <sort>
                <sort-child>
                    <name>apple</name>
                    <type id="fruit"/>
                </sort-child>
                <sort-child>
                    <name>onion</name>
                    <type id="vegetable"/>
                </sort-child>
                <test>c sort</test>
            </sort>
            <sort>
                <sort-child>
                    <name>apple</name>
                    <type id="fruit"/>
                </sort-child>
                <test>a sort</test>
            </sort>
        </elements>
    '''

    final String ORDER_QUERY = '''<?xml version="1.0"?>
        <query>
            <sort>
                <item>sort/sort1</item>
            </sort>
            <sort>
                <item>sort/sort2</item>
            </sort>
            <sort><item>sort/sort-child/name</item></sort>
        </query>
    '''

    final String PREDICATE_QUERY = '''<?xml version="1.0"?>
        <query>
            <sort>
                <item>sort/group[@type='group']</item>
            </sort>
        </query>
    '''

    final String PREDICATE_PARAM_QUERY = '''<?xml version="1.0"?>
        <query>
            <sort>
                <item>sort/group[$1=@type]</item>
            </sort>
        </query>
    '''

    final String COMPLEX_PREDICATE_PARAM_QUERY = '''<?xml version="1.0"?>
        <query>
            <sort>
                <item>sort/sort-child[type/@id='fruit']/name</item>
            </sort>
            <sort>
                <item>sort/sort-child[type/@id='vegetable']/name</item>
            </sort>
            <sort>
                <item>sort[sort-child/type/@id='vegetable']/test</item>
            </sort>
        </query>
    '''

    final String GROUP_QUERY = '''<?xml version="1.0"?>
        <query group="group">
            <sort>
                <item>sort/sort1</item>
            </sort>
        </query>
    '''

    private static parser = new XmlParser()

    void testSort() {
        def query = new XmlParser().parseText(ORDER_QUERY)
        def last = ""
        MultiSort.sort(query, genSortItems(SORT_SAMPLE), null).each {
            def xml = parser.parseText(it.xmlcache)
            String next = xml.sort1[0].text() + xml.sort2[0].text()
            if (xml["sort-child"]) next += xml["sort-child"].name.text()
            assert next.compareTo(last) >= 0
            last = next
        }
    }

    void testPredicateSort() {
        predicateTest(new XmlParser().parseText(PREDICATE_QUERY), null)
    }

    void testPredicateParamSort() {
        String[] params = ["group"]
        predicateTest(new XmlParser().parseText(PREDICATE_PARAM_QUERY), params)
    }

    void testComplexPredicateSort() {
        def query = new XmlParser().parseText(COMPLEX_PREDICATE_PARAM_QUERY)
        def last = ""
        MultiSort.sort(query, genSortItems(COMPLEX_SORT_SAMPLE), null).each {
            def xml = parser.parseText(it.xmlcache)
            def next = xml.depthFirst().find { "fruit".equals(it.'@id') }
            next = next.parent().name.text()
            assert next.compareTo(last) >= 0
            last = next
        }
    }

    void testGroup() {
        def query = new XmlParser().parseText(GROUP_QUERY)
        def sortItems = genSortItems(SORT_SAMPLE)
        String[] params = ["2"]
        assert MultiSort.sort(query, genSortItems(SORT_SAMPLE), params).findAll {
            return (it in Group)
        }.size == 3
    }

    private genSortItems(String xml) {
        def elements = parser.parseText(xml)
        return elements.sort.collect { node ->
            StringWriter writer = new StringWriter()
            new XmlNodePrinter(new PrintWriter(writer), "").print(node)
            return new SortItem(xmlcache:writer.toString().replaceAll("\n",""))
        }
    }

    private predicateTest(query, params) {
        def last = ""
        MultiSort.sort(query, genSortItems(PREDICATE_SORT_SAMPLE), params).each {
            def xml = parser.parseText(it.xmlcache)
            def next = xml.depthFirst().find { "group".equals(it.'@type') }.text()
            assert next.compareTo(last) >= 0
            last = next
        }
    }

}

class SortItem {
    
    static int idgen = 0

    def id = ++idgen
    def xmlcache

}
