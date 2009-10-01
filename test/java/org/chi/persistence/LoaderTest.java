package org.chi.persistence;

import java.util.Date;
import java.io.Serializable;
import java.lang.reflect.Method;

import junit.framework.TestCase;

import org.chi.util.GeneralException;
import org.chi.util.Log;

/**
 * Test the dynamic persistence loading system
 * @author rgrey
 */
public class LoaderTest extends TestCase {
    
    private PersistenceLoader load;
    private Class<?> _class;
    
    @Override
    protected void setUp() throws Exception {
        load = PersistenceLoader.getInstance("hibernate.cfg.xml");
        if (! load.isLoaded()) {
            TestDomainClass test = new TestDomainClass("Test");
            test.addField("foo", String.class);
            test.addField("bar", String.class);
            test.addField("created", Date.class);
            test.addField("lastUpdated", Date.class);
            load.addBaseClass(test);
            load.load(true);
        }
        _class = load.getClasses().iterator().next();
        assertEquals("didn't load correct amount of classes", 1,
                load.getSF().getAllClassMetadata().size());
    }

    public void testSave() throws GeneralException {
        try {
            Object test = _class.newInstance();
            Class<?>[] set = new Class[]{String.class};
            Method method = test.getClass().getMethod("setFoo", set);
            method.invoke(test, new Object[] {"blaa"});
            method = test.getClass().getMethod("setBar", set);
            method.invoke(test, new Object[] {"blaa"});
            method = test.getClass().getMethod("setXmlcache", set);
            method.invoke(test, new Object[] {""});
            load.beginTransaction();
            Serializable id = load.getSession().save(test);
            Log.console("Save ID [" + id + "]");
            load.succeed();
            load.endTransaction();
            Object result = load.getSession().get(_class, id);
            Log.console("Loaded Object [" + result + "]");
            method = result.getClass().getMethod("getFoo", new Class[0]);
            String value = method.invoke(result, new Object[0]).toString();
            Log.console("Loaded Value [" + value + "]"); 
        } catch (Exception e) {
            throw new GeneralException(e);
        } finally {
            load.closeSession();
        }
    }
    
//    public void testMetaClass() throws GeneralException {
//        List<?> result = 
//            load.getSession().createQuery("FROM Test").list();
//        try {
//            Method mt = 
//                result.get(0).getClass().getMethod("getMetaClass", new Class[0]);
//            Object object = mt.invoke(result.get(0), new Object[0]);
//            MetaClassImpl mclass = (MetaClassImpl) object;
//            for (Object next : mclass.getMetaMethods()) {
//                NewMetaMethod mm = (NewMetaMethod) next;
//                System.out.println(mm.getName());
//            }
//        } catch (Exception e) {
//            throw new GeneralException(e);
//        }
//    }

}
