package org.chi.persistence.web;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.chi.persistence.XmlSerializer;
import org.chi.persistence.web.Loader;
import org.chi.util.ElectricUtil;
import org.chi.util.Log;
import org.chi.util.PooledThread;
import org.chi.web.impl.SimpleApplicationContext;

import electric.xml.Element;

/**
 * General purpose threaded application
 * @author rgrey
 */
public abstract class Threaded extends PooledThread implements ServletContextListener {
    
    private static final int pr = Thread.MIN_PRIORITY;
    private static final 
        Map<Integer, Threaded> r = new HashMap<Integer, Threaded>();

    public XmlSerializer serial;
    public SimpleApplicationContext sac;
    private boolean run = true;
    
    /**
     * Amount of time in seconds between runs
     * @return
     */
    public abstract int timer();

    /**
     * Run process
     * @throws InterruptedException
     */
    public abstract void process() throws InterruptedException;
    
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext sc = sce.getServletContext();
        SimpleApplicationContext sac = new SimpleApplicationContext(sc);
        Element pe = sac.getConfigElement("persistence");
        for (Element scfg : ElectricUtil.getAllElementsNamed(pe, "store")) {
            String id = scfg.getAttribute("id");
            if (id == null) continue;
            start(Loader.getStore(id), getClass(), sac);
        }
    }
    
    public void contextDestroyed(ServletContextEvent sce) {
        Log.debug("Threaded.contextDestroyed(): stop " + r.size());
        for (Threaded s : r.values()) s.run = false;
        r.clear();
    }

    /**
     * Start the thread
     * @param s 
     * @param cls implementing class
     * @param sac 
     */
    public static void start(XmlSerializer serializer, Class cls, 
            SimpleApplicationContext sac) {
        int code = serializer.hashCode() + cls.hashCode();
        if (r.get(code) != null) return;
        try {
            Threaded instance = (Threaded) cls.newInstance();
            instance.sac = sac;
            instance.serial = serializer;
            instance.queue(pr);
            r.put(code, instance);
        } catch(Exception e) {
            Log.exception("Unable to start threaded application", e);
        }
    }
    
    @Override
    public void run() {
        while (run) {
            try {
                this.process();
                Thread.sleep(timer() * 1000);
            } catch (InterruptedException e) {
                run = false;
            }
        }
    }
    
}

