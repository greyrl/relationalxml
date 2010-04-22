package org.chi.persistence 

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import org.chi.persistence.util.DeadLockException
import org.chi.persistence.util.LockItem
import org.chi.util.Log
import static org.junit.Assert.assertFalse
import static org.junit.Assert.fail
import org.junit.Before

class LockTest extends GroovyTestCase {

    private static String objxml = '''<abc id="123">
    <def id="324"></def>
</abc>
    '''

    private static String otherxml = '''<abc id="324">
    <def id="123"></def>
</abc>
    '''

    // override XmlSerializer methods for mocking
    static {
        XmlSerializer.metaClass.beginTransaction = {}
        XmlSerializer.metaClass.endTransaction = {}
        XmlSerializer.metaClass.closeSession = {}
    }

    private XmlSerializer serial = new XmlSerializer(null, null, null)
    private Object obj = new Object()
    private Object other = new Object()

    @Before void setUp() throws Exception {
        serial.class.metaClass.session = new MockSession()
        obj.class.metaClass.id = 0L
        obj.id = 123L
        other.id = 324L
    }

    void testBasicLock() {
        def threads = exec(12, 100, { it % 2 == 0 ? 500 : 100 })
        while(SyncLock.current.get() > 0) { Thread.sleep(500) } 
        threads.each { assertNull(it.fail, it.fail) }
    }

    void testDeadLock() {
        def threads = exec(4, 1, { 1000 })
        while(SyncLock.current.get() > 0) { Thread.sleep(500) } 
        def fnd = threads.find { 
            return it.fail && it.fail instanceof DeadLockException
        }
        if (! fnd) fail("no deadlock thrown")
    }

    /**
     * Convenience
     * @param items threads
     * @param dc deadlock count
     * @param priority priority in milliseconds
     */
    private exec(items, dc, priority) {
        def barrier = new CyclicBarrier(items + 2)
        def threads = []
        (0..items).each { 
            def args = [b:barrier, ser:serial, dc:dc, o:obj, xml:objxml]
            args.priority = priority(it)
            create(args, threads)
        }
        def args = [b:barrier, ser:serial, dc:dc, o:other, xml:otherxml]
        args.priority = priority(items + 1)
        create(args, threads)
        return threads
    }

    /**
     * Convenience
     * @param args 
     * @param threads 
     */
    private create(args, threads) {
        def sl = new SyncLock(args)
        threads.add(sl)
        new Thread(sl).start()
    }

}

class MockSession {

    def evict(Object object) {}

    def get(Class _class, Object id) { return null }
    
}

class SyncLock implements Runnable {

    static current = new AtomicInteger()
    static msg = "SyncLock.run(): "
    static running = []

    private args, id, fail, priority

    def SyncLock(args) { 
        this.args = args 
        id = current.getAndIncrement()
    }

    void run() { 
        def keys = LockItem.keys(args.xml)
        def p = args.priority
        Log.console msg + "id ${id}, running ${current}, priorirty ${p}"
        try {
            try {
                args.b.await()
                LockItem.lock(args.o, keys, args.ser, p, args.dc)
                if (running.contains(args.o.id)) fail = "concurrent runs"
                running.add(args.o.id)
                Thread.sleep(200)
            } finally {
                running.remove(args.o.id)
                LockItem.unlock(keys, args.ser)
            } 
        } catch(Exception e) {
            fail = e
        } finally { 
            current.decrementAndGet() 
            Log.console msg + "finish id ${id}, count ${current.get()}"
        }
    }

}

