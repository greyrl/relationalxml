package org.chi.persistence.web;

import java.io.File;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.chi.persistence.util.UpdateThread;
import org.chi.web.impl.SimpleApplicationContext;

import electric.xml.Element;

/**
 * Load the stores when the application starts
 * @author rgrey
 */
public class PersistenceListener implements ServletContextListener {

    public void contextInitialized(ServletContextEvent sce) {
        ServletContext sc = sce.getServletContext();
        String path = sc.getRealPath("/").replace('\\', '/');
        SimpleApplicationContext sac = new SimpleApplicationContext(sc);
        Element pe = sac.getConfigElement("persistence");
        Loader.loadStores(path + File.separator + ".." + File.separator, pe);
    }
    
    public void contextDestroyed(ServletContextEvent sce) {
        UpdateThread.stopAll();
    }

}
