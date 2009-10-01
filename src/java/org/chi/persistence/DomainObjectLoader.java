package org.chi.persistence;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.chi.util.Log;
import org.chi.util.StringUtil;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.codehaus.groovy.grails.compiler.injection.GrailsASTUtils;
import org.codehaus.groovy.grails.compiler.injection.GrailsDomainClassInjector;

/**
 * Load the hibernate domain classes into the JVM. This is lifted directly from:
 * <br/><br/>
 * <a href="http://svn.codehaus.org/grails/trunk/grails/src/commons/org/codehaus/groovy/grails/compiler/injection/DefaultGrailsDomainClassInjector.java">
 * http://svn.codehaus.org/grails/trunk/grails/src/commons/org/codehaus/groovy/grails/compiler/injection/DefaultGrailsDomainClassInjector.java
 * </a><br/><br/>
 * It typically would inject groovy classes from the "grails-app" directory
 * but is now used to inject dynamic classes from the groovy classloader. 
 * Unfortunately, all the methods are private in the original or it would have
 * just been extended. Associations will now need to be injected after they
 * are dynamically added to the classes.
 * @author rgrey
 */
public class DomainObjectLoader implements GrailsDomainClassInjector {
    
    /**
     * Designates a class as a loadable class
     * 
     */
    public @interface DomainClass {}
    
    private static final String[] MANY_COMPARE = { 
            GrailsDomainClassProperty.RELATES_TO_MANY, 
            GrailsDomainClassProperty.HAS_MANY 
    };
    
    public boolean shouldInject(URL url) {
        return true;
    }
    
    public void performInjection(SourceUnit su, GeneratorContext gc,
            ClassNode classNode) {
        if (! containsDomainAnnotation(classNode)) {
            String n = classNode.getName();
            Log.debug("DomainObjectLoader.performInjection() : skipping " + n);
            return;
        }
        injectIdProperty(classNode);
        injectVersionProperty(classNode);
        injectToStringMethod(classNode);
        injectAssociations(classNode);
    }
    
    /**
     * Inject associated domain objects
     * @param cnode
     */
    private void injectAssociations(ClassNode cnode) {
        Collection<PropertyNode> add = new ArrayList<PropertyNode>();
        for (Object next : cnode.getProperties()) {
            PropertyNode pn = (PropertyNode) next;
            Expression e = pn.getInitialExpression();
            add = createPropsForBelongsToExpression(pn, e, cnode, add);
            add = createPropsForHasManyExpression(pn, e, cnode, add);
        }
        injectAssociationProperties(cnode, add);
    }

    /**
     * Handle belongs-to, if applicable
     * @param pn
     * @param e
     * @param classNode
     * @param properties
     * @return
     */
    private Collection<PropertyNode> createPropsForBelongsToExpression(
            PropertyNode pn, Expression e, ClassNode classNode, 
            Collection<PropertyNode> properties) {
        if (! pn.getName().equals(GrailsDomainClassProperty.BELONGS_TO)) 
            return properties;
        if (! (e instanceof MapExpression)) return properties;
        for (Object next : ((MapExpression) e).getMapEntryExpressions()) {
            MapEntryExpression mme = (MapEntryExpression) next;
            String key = mme.getKeyExpression().getText();
            String type = mme.getValueExpression().getText();
            properties.add(new PropertyNode(key,Modifier.PUBLIC, 
                    ClassHelper.make(type) , classNode, null, null, null));
        }
        return properties;
    }

    /**
     * Handle has-many, if applicable
     * @param pn
     * @param e
     * @param classNode
     * @param properties
     * @return
     */
    private Collection<PropertyNode> createPropsForHasManyExpression(
            PropertyNode pn, Expression e, ClassNode classNode, 
            Collection<PropertyNode> properties) {
        if (! StringUtil.equals(MANY_COMPARE, pn.getName())) return properties;
        if (! (e instanceof MapExpression)) return properties;
        for (Object next : ((MapExpression) e).getMapEntryExpressions()) {
            MapEntryExpression mee = (MapEntryExpression) next;
            String key = mee.getKeyExpression().getText();
            properties.add(new PropertyNode(key, Modifier.PUBLIC, 
                    new ClassNode(Set.class), classNode, null, null, null));
        }
        return properties;
    }
    
    /**
     * Add the actual associations to the class
     * @param classNode
     * @param propertiesToAdd
     */
    private void injectAssociationProperties(ClassNode classNode, 
            Collection<PropertyNode> propertiesToAdd) {
        Log.debug("DomainObjectLoader.injectAssociationProperties() : " + 
                "adding " + propertiesToAdd.size() + " properties");
        for (PropertyNode pn : propertiesToAdd) {
            if (GrailsASTUtils.hasProperty(classNode, pn.getName())) continue;
            Log.debug("DomainObjectLoader.injectAssociationProperties() : " +
            		"adding property [" + pn.getName() + "] to class [" + 
                    classNode.getName() + "]");
            classNode.addProperty(pn);              
        }
    }

    /**
     * Inject the identifier property
     * @param classNode
     */
    private void injectIdProperty(ClassNode classNode) {
        final boolean hasId = GrailsASTUtils.hasProperty(classNode, 
                GrailsDomainClassProperty.IDENTITY);
        if (hasId) return;
        Log.debug("DomainObjectLoader.injectIdProperty() : adding property [" + 
                GrailsDomainClassProperty.IDENTITY + "] to class [" + 
                classNode.getName() + "]");
        classNode.addProperty(GrailsDomainClassProperty.IDENTITY, 
                Modifier.PUBLIC, new ClassNode(Long.class), null, null, null);
    }
    
    /**
     * Inject the version counter
     * @param classNode
     */
    private void injectVersionProperty(ClassNode classNode) {
        final boolean hasVersion = GrailsASTUtils.hasProperty(classNode, 
                GrailsDomainClassProperty.VERSION);
        if (hasVersion) return;
        Log.debug("DomainObjectLoader.injectVersionProperty() :  adding " +
                "property [" + GrailsDomainClassProperty.VERSION + "] " +
                "to class [" + classNode.getName() + "]");
        classNode.addProperty(GrailsDomainClassProperty.VERSION, Modifier.PUBLIC, 
                new ClassNode(Long.class), null, null, null);
    }
    
    /**
     * Add a "toString" method if it doesn't exists already
     * @param classNode
     */
    private void injectToStringMethod(ClassNode classNode) {
        if (GrailsASTUtils.implementsZeroArgMethod(classNode, "toString")) 
            return;
        GStringExpression ge = 
            new GStringExpression(classNode.getName() + " : ${id}");
        ge.addString(new ConstantExpression(classNode.getName()+" : "));
        ge.addValue(new VariableExpression("id"));          
        Statement s = new ReturnStatement(ge);          
        MethodNode mn = new MethodNode("toString", Modifier.PUBLIC, 
                new ClassNode(String.class), new Parameter[0], new ClassNode[0], s);
        Log.debug("DomainObjectLoader.injectToStringMethod() : adding " +
                "toString() method to class [" + classNode.getName() + "]");
        classNode.addMethod(mn);
    }
    
    /**
     * Check to see if the domain annotation has been loaded on this class
     * @param classNode
     * @return
     */
    private boolean containsDomainAnnotation(ClassNode classNode) {
        for (Object next : getAnnotations(classNode)) {
            AnnotationNode a = (AnnotationNode) next;
            if (a.getClassNode().getName().equals("DomainClass")) return true;
        }
        return false;
    }

    /**
     * Convenience
     * @param cn
     * @return
     */
    private Collection<?> getAnnotations(ClassNode cn) {
        try {
            Method _method = cn.getClass().getMethod("getAnnotations");
            if (_method.getReturnType().equals(Map.class)) {
                return ((Map) _method.invoke(cn, new Object[0])).values();
            } else return (Collection) _method.invoke(cn, new Object[0]);
        } catch(Exception e) {
            Log.exception("Unable to load annotation method", e);
            return new ArrayList();
        }
    }

}
