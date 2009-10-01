package org.chi.persistence 

import org.chi.persistence.util.XmlCompare

class XmlCompareTest extends GroovyTestCase {

    def simplemod1='''
        <wrap id="123">
            <name>one</name>
        </wrap>
    '''

    def simplemod2='''
        <wrap id="123">
            <name>two</name>
        </wrap>
    '''

    def modifyxml1='''
        <wrap id="123">
            <display-element id="6684672">
                <data-type>default</data-type>
                <block id="456">
                    <value>Topper</value>
                </block>
            </display-element>
            <display-element id="6684673" blaa="same">
            </display-element>
            <display-element id="6684674" blaa="diff1">
            </display-element>
        </wrap>
    '''

    def modifyxml2='''
        <wrap id="123">
            <display-element id="6684672" blaa="yo">
                <data-type>default</data-type>
                <block id="456">
                    <value>Top</value>
                </block>
            </display-element>
            <display-element id="6684673" blaa="same">
            </display-element>
            <display-element id="6684674" blaa="diff2">
            </display-element>
        </fap>
    '''

    def addxml1='''
        <wrap id="123">
            <display-element id="6684673" blaa="same">
            </display-element>
        </wrap>
    '''

    def addxml2='''
        <wrap id="123">
            <display-element id="6684673" blaa="same">
            </display-element>
            <display-element blaa="diff">
            </display-element>
        </wrap>
    '''

    def addmiddle1='''
        <wrap id="123">
            <display-element id="6684673" blaa="same">
            </display-element>
            <display-element id="6684674" blaa="other">
            </display-element>
        </wrap>
    '''

    def addmiddle2='''
        <wrap id="123">
            <display-element id="6684673" blaa="same">
            </display-element>
            <display-element blaa="diff">
            </display-element>
            <display-element id="6684674" blaa="other">
            </display-element>
        </wrap>
    '''

    def deletexml1='''
        <wrap id="123">
            <display-element id="6684673" blaa="same">
            </display-element>
            <display-element id="6684674" blaa="diff">
            </display-element>
        </wrap>
    '''

    def deletexml2='''
        <wrap id="123">
            <display-element id="6684673" blaa="same">
            </display-element>
        </wrap>
    '''

    def addanddel1='''
        <wrap id="123">
            <display-element id="6684673" blaa="old">
            </display-element>
            <display-element id="6684674" blaa="same">
            </display-element>
        </wrap>
    '''

    def addanddel2='''
        <wrap id="123">
            <display-element id="6684674" blaa="same">
            </display-element>
            <display-element blaa="new">
            </display-element>
        </wrap>
    '''

    def switch1='''
     <mixed-kids id="13926400">
      <title>test</title>
      <kid-one id="13795328">
       <title>kid1-1</title>
      </kid-one>
      <kid-two id="13828096">
       <title>kid2-1</title>
      </kid-two>
      <kid-one id="13795329">
       <title>kid1-2</title>
      </kid-one>
      <kid-one id="13795330">
       <title>kid1-3</title>
      </kid-one>
      <kid-three id="13860864">
       <title>kid1-3</title>
      </kid-three>
     </mixed-kids>
    '''

    def switch2='''
     <mixed-kids id="13926400">
      <title>test</title>
      <kid-one id="13795328">
       <title>kid1-1</title>
      </kid-one>
      <kid-one id="13795329">
       <title>kid1-2</title>
      </kid-one>
      <kid-one id="13795330">
       <title>kid1-3</title>
      </kid-one>
      <kid-two id="13828096">
       <title>kid2-1</title>
      </kid-two>
      <kid-three id="13860864">
       <title>kid1-3</title>
      </kid-three>
     </mixed-kids>
    '''

    List modifyres = [
        "6684672:display-element","123:wrap","456:block",
        "6684674:display-element"
    ]

    List deleteres = [
        "123:wrap", "6684674:display-element"
    ]

    List addanddelres = [
        "123:wrap", "6684673:display-element"
    ]

    List reordres = [ "123:wrap" ]

    void testSimpleModify() {
        def result = XmlCompare.compare(simplemod1, simplemod2)
        rescompare(result, ["123:wrap"])
    }

    void testModify() {
        def result = XmlCompare.compare(modifyxml1, modifyxml2)
        rescompare(result, modifyres)
    }

    void testAdd() {
        def res = XmlCompare.compare(addxml1, addxml2)
        rescompare(res, reordres)
        res = XmlCompare.compare(addmiddle1, addmiddle2)
        rescompare(res, reordres)
        res = XmlCompare.compare(addmiddle1, addmiddle2)
        reordcompare(res, reordres)
    }

    void testDelete() {
        def result = XmlCompare.compare(deletexml1, deletexml2)
        rescompare(result, deleteres)
        reordcompare(result, reordres)
    }

    void testAddAndDelete() {
        def result = XmlCompare.compare(addanddel1, addanddel2)
        rescompare(result, addanddelres)
        reordcompare(result, reordres)
    }

    void testSwitch() {
        def result = XmlCompare.compare(switch1, switch2)
        reordcompare(result, ["13926400:mixed-kids"])
    }

    void testNull() {
        def xml = "</xml>"
        assert XmlCompare.compare(null, null).all == null
        assert XmlCompare.compare(xml, null).all == null
        assert XmlCompare.compare(null, xml).all == null
    }

    /**
     * Convenience
     * @param one
     * @param two
     */
    private rescompare(one, two) {
        compare(one.res.asList(), two)
    }

    /**
     * Convenience
     * @param one
     * @param two
     */
    private reordcompare(one, two) {
        compare(one.reord.asList(), two)
    }

    /**
     * Convenience
     * @param one
     * @param two
     */
    private compare(one, two) {
        one.sort() ; two.sort()
        assert one == two
    }

}
