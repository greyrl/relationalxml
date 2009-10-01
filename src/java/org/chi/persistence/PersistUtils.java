package org.chi.persistence;

import electric.xml.Element;
import electric.xml.XPath;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

import org.chi.util.Log;
import org.chi.util.ElectricUtil;
import org.chi.util.StringUtil;

/**
 * Persistence utilities
 * @author rgrey
 */
public class PersistUtils {
    
    /**
     * Convert a dash notation entry to object notation
     * @param dashed
     * @param upper should the first character be upper or lower case?
     * @return
     */
    public static String camelCase(String dashed, boolean upper) {
        if (StringUtil.isEmpty(dashed)) return dashed;
        StringBuilder result = new StringBuilder();
        for (String next : dashed.split("-")) {
            result.append(Character.toUpperCase(next.charAt(0)));
            result.append(next.substring(1, next.length()));
        }
        if (! upper) result.setCharAt(0, Character.toLowerCase(result.charAt(0)));
        return result.toString();
    }
    
    /**
     * Convert a dash notation entry to camel case
     * @param dashed
     * @return
     */
    public static String lowerCamelCase(String dashed) {
        return camelCase(dashed, false);
    }


    /**
     * Add dashes at upper case letters
     * @param camelCase
     */
    public static String addDashes(String camelCase) {
        return addChar(camelCase, '-');
    }

    /**
     * Add under scores at upper case letters
     * @param camelCase
     */
    public static String addUnderscores(String camelCase) {
        return addChar(camelCase, '_');
    }

    /**
     * Convenience
     * @param _class
     * @return
     */
    public static String addDashes(Class<?> _class) {
        return addDashes(_class.getSimpleName());
    }
    
    /**
     * Check to see if a class contains an annotation
     * @param element
     * @param annotation
     * @return
     */
    public static boolean containsAnnotation(AnnotatedElement element,
            Class<?> annotation) {
        if (element == null || annotation == null) return false;
        return getAnnotation(element, annotation) != null;
    }

    /**
     * Get the annotation for an element
     * @param element
     * @param annotation
     * @return
     */
    public static Annotation getAnnotation(AnnotatedElement element,
            Class<?> annotation) {
        if (element == null || annotation == null) return null;
        for (Annotation anno : element.getAnnotations()) {
            if (anno.annotationType().equals(annotation)) return anno;
        }
        return null;
    }

    /**
    * Get Value from persistence object
    * @param obj
    * @param method
    * @return
    */
   public static Object dynaVal(Object obj, String method) {
       Class<?>[] emptyClass = new Class[0];
       Object[] emptyObject = new Object[0];
       try {
           Method dmethod = obj.getClass().getMethod(method, emptyClass);
           return dmethod.invoke(obj, emptyObject);
       } catch (Exception e) {
           Log.exception("Unable to retrieve value", e);
           return null;
       }
   }
   
   /**
    * Set value on persistence object
    * @param obj
    * @param method
    * @return
    */
   public static void dynaVal(Object obj, String method, Object newVal) {
       Class<?>[] _class = { newVal.getClass() };
       Object[] _object = { newVal };
       try {
           Method dmethod = obj.getClass().getMethod(method, _class);
           dmethod.invoke(obj, _object);
       } catch (Exception e) {
           Log.exception("Unable to set value", e);
       }
   }

   /**
    * Convenience
    * @param result
    * @param xpath
    * @return
    */
   public static String extractQueryVal(String result, String xpath) {
       Element res = ElectricUtil.readElement("wrap", result);
       res = res.getElement(new XPath(xpath));
       return res != null ? res.getString() : null;
   }

    /**
     * Add certain characters at upper case letters
     * @param camelCase
     */
    private static String addChar(String camelCase, char insert) {
        if (StringUtil.isEmpty(camelCase)) return camelCase;
        StringBuilder result = new StringBuilder(camelCase);
        for (int x = 0 ; x < result.length() ; x++) {
            char _char = result.charAt(x);
            if (! Character.isUpperCase(_char)) continue;
            result.deleteCharAt(x);
            result.insert(x, Character.toLowerCase(_char));
            if (x > 0) result.insert(x, insert);
        }
        return result.toString();
    }

}
