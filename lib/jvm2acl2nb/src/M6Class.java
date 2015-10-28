/* M6Class.java:  a Java representation of an ACL2 class object
 *
 * $Id: M6Class.java,v 1.6 2002/12/13 00:18:12 hbl Exp hbl $
 */

import java.util.*;
import java.io.*;

/**
 * M5Class represents a Java class in a format that allows easy conversion to a
 * format that ACL2 (more specifically M5) can handle. When an M5Class object is
 * created, a ClassFile object is passed into the constructor. This object
 * represents the parsed classfile (in IBM's
 * <a href=http://www.alphaworks.ibm.com/tech/cfparse>CFParse library</a>).
 * <p>
 * Once the ClassFile object is loaded into a M5Class object, the
 * <code>processClassFile()</code> method is invoked. This pulls out relevant
 * information from the classfile and stores it in a format that can be exported
 * into a <code>(make-class-decl ...</code> format.
 * <p>
 * State information stored includes:
 * <ul>
 * <li>The ClassFile
 * <li>The class's name
 * <li>The instance fields
 * <li>The static class fields
 * <li>The constant pool
 * <li>The methods (a Vector of M5Method objects)
 * </ul>
 *
 * @author George M. Porter
 * @author J Strother Moore
 * @version 1.0
 */
public class M6Class {

    private com.ibm.toad.cfparse.ClassFile cf;
    private com.sun.tools.classfile.ClassFile cf2;

    private String name;
    private boolean is_interface;
    private String super_name;
    private Vector constant_pool;
    private Vector interfaces;
    private Vector fields;
    private Vector methods;
    private M6AccessFlags accessflags;
    private Vector attributes;
    private boolean debug;

    private static boolean keep_debug_info = false;

    private StringBuffer lntdesc;
    private StringBuffer lvtdesc;

    // don't register other entries, but only the "string" values, we will create
    // those "string" as objects in the heap upon start up of the M6
    static class ClassRef {

        final String className;

        ClassRef(String className) {
            this.className = className;
        }

        @Override
        public String toString() {
            return className.toString();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ClassRef
                    && className.equals(((ClassRef) o).className);
        }

        @Override
        public int hashCode() {
            return className.hashCode() + 11;
        }
    }

    /**
     * Given a CFParse ClassFile object, this constructor creates an M5Class.
     * Note that you must then call <code>processClassFile()</code> before
     * calling methods like <code>toString</code>.
     *
     * @param c the parsed class file (from the CFParse library)
     */
    public M6Class(String className) throws IOException {
        cf = new com.ibm.toad.cfparse.ClassFile(className);
        name = null;
        super_name = null;
        fields = new Vector();
        methods = new Vector();
        constant_pool = new Vector();
        interfaces = new Vector();
        attributes = new Vector();

        lntdesc = new StringBuffer();
        lvtdesc = new StringBuffer();

        debug = true;
    }

    // don't care if there is a main method.
    /**
     * Returns the name of the class this M6Class object represents.
     *
     * @return	A String containing the name of this class.
     */
    public String getName() {
        return name;
    }

    public static void setKeepLntLvt() {
        keep_debug_info = true;
    }

    public StringBuffer getlntdesc() {
        return lntdesc;
    }

    public StringBuffer getlvtdesc() {
        return lvtdesc;
    }

    /**
     * This method processes the ClassFile given in the constructor. It is
     * necessary to call this method before querying this class.
     */
    public void processClassFile(Target target) throws IOException {
        /* First, the name of the class itself */

        name = cf.getName();
        super_name = cf.getSuperName();

        is_interface = com.ibm.toad.cfparse.utils.Access.isInterface(cf.getAccess());
        lntdesc.append("(defconst *" + name + "-lnt*\n"
                + "(list \n");
        lvtdesc.append("(defconst *" + name + "-lvt*\n"
                + "(list \n");

        if (debug) {
            System.out.println("Processing class: " + name);
        }

        resolveInterfaces();
        resolveFields();
        resolveMethods(target);
        resolveAttributes();
        resolveAccessFlags();

        lntdesc.append("))\n");
        lvtdesc.append("))\n");
    }

    private void resolveAccessFlags() {
        accessflags = new M6AccessFlags(cf.getAccess());
    }

    private void resolveInterfaces() {
        com.ibm.toad.cfparse.InterfaceList il = cf.getInterfaces();
        for (int i = 0; i < il.length(); i++) {
            interfaces.addElement(il.getInterfaceName(i));
        }
    }

    private void resolveAttributes() {
        // not used here.
        com.ibm.toad.cfparse.attributes.AttrInfoList al = cf.getAttrs();
        for (int i = 0; i < al.length(); i++) {
            attributes.addElement(al.get(i));
        }
    }

    private void resolveFields() throws IOException {
        com.ibm.toad.cfparse.FieldInfoList fl = cf.getFields();
        for (int i = 0; i < fl.length(); i++) {
            M6Field field = new M6Field(cf, fl.get(i));
            field.processField(constant_pool);
            fields.addElement(field);
        }
    }

    private void resolveMethods(Target target) throws IOException {
        /* This attribute ensures we have relative tags for
         * branches, rather than byte offsets */
        com.ibm.toad.cfparse.attributes.CodeAttrInfo.setViewer(com.ibm.toad.cfparse.instruction.MutableCodeSegment.getViewer());

        com.ibm.toad.cfparse.MethodInfoList ml = cf.getMethods();
        for (int i = 0; i < ml.length(); i++) {
            M6Method meth = new M6Method(cf, ml.get(i));
            meth.processMethod(constant_pool, target);
            // constant_pool here is used for registering constant symbols.
            // when the class is loaded we assume those get into the heap? ;; hanbing
            methods.addElement(meth);

            if (keep_debug_info) {
                lntdesc.append("\n\n'" + meth.getLntStr());
                lvtdesc.append("\n\n'" + meth.getLvtStr());
            }
        }
    }

    private String booleanToACL2String(boolean b) {
        if (b) {
            return "T";
        } else {
            return "NIL";
        }
    }

    private static String padLeft(String s, int w) {
        while (s.length() < w) {
            s = '0' + s;
        }
        return s;
    }

    private static String cpToString(int lmargin, Vector cp, Target target) {
        StringBuffer buf = new StringBuffer();
        StringBuffer padb = new StringBuffer();
        for (int i = 0; i < lmargin; i++) {
            padb.append(" ");
        }

        String pad = padb.toString();
        for (int i = 0; i < cp.size(); i++) {
            buf.append(pad);
            if (cp.get(i) instanceof Integer) {
                buf.append("(INT " + cp.get(i) + ")");
            } else if (cp.get(i) instanceof String) {
                String str = (String) cp.get(i);
                switch (target) {
                    case M5:
                        buf.append("(STRING (REF -1) ; \"" + cp.get(i) + "\"\n");
                        buf.append(pad).append("  ");
                        for (int j = 0; j < str.length(); j++) {
                            buf.append(" ").append((int) str.charAt(j));
                        }
                        buf.append(")");
                        break;
                    case M6:
                        buf.append("(STRING  \"" + str + "\")");
                        break;
                }
            } else if (cp.get(i) instanceof ClassRef) {
                switch (target) {
                    case M5:
                        buf.append("(CLASS (REF -1) \"" + ((ClassRef) cp.get(i)).className + "\")");
                        break;
                    case M6:
                        buf.append("(CLASS  \"" + ((ClassRef) cp.get(i)).className + "\")");
                        break;
                }
            } else if (cp.get(i) instanceof Long) {
                buf.append("(LONG " + cp.get(i) + ")");
            } else if (cp.get(i) instanceof Float) {
                float f = (Float) cp.get(i);
                buf.append("(FLOAT #x" + padLeft(Integer.toHexString(Float.floatToRawIntBits(f)), 8) + ") ; " + Float.toHexString(f) + " " + f);
            } else if (cp.get(i) instanceof Double) {
                double d = (Double) cp.get(i);
                buf.append("(DOUBLE #x" + padLeft(Long.toHexString(Double.doubleToRawLongBits(d)), 16) + ") ; " + Double.toHexString(d) + " " + d);
            } else {
                buf.append("(UNKNOWN)");
            }

            if (i < (cp.size() - 1)) {
                buf.append("\n");
            }
        }
        return buf.toString();
    }

    //at the first stage, I won't dealing with the correct output format
    // like what should be quoted...
    /**
     * Returns a string representing this class (a <code>(make-class-decl ...)
     * </code>).
     *
     * @return	A String containing the <code>(make-class-decl ...)</code> that
     * specifies this class in M5.
     */
    public String toString(int lmargin, Target target) {
        StringBuffer buf = new StringBuffer();
        StringBuffer padb = new StringBuffer();
        for (int i = 0; i < lmargin; i++) {
            padb.append(" ");
        }

        String pad = padb.toString();
        switch (target) {
            case M5:
                buf.append(pad + "(make-class-decl\n");
                buf.append(pad + " \"" + name + "\"\n");
                buf.append(pad + " '(\"" + super_name + "\")\n");

                buf.append(pad + " '(");
                for (int j = 0; j < fields.size(); j++) {
                    M6Field f = (M6Field) fields.get(j);
                    if (com.ibm.toad.cfparse.utils.Access.isStatic(f.getAccessFlags().f)) {
                        continue;
                    }
                    buf.append("\n" + f.toString(lmargin + 20, target));
                }
                buf.append(")\n");

                buf.append(pad + " '(");
                for (int j = 0; j < fields.size(); j++) {
                    M6Field f = (M6Field) fields.get(j);
                    if (!com.ibm.toad.cfparse.utils.Access.isStatic(f.getAccessFlags().f)) {
                        continue;
                    }
                    buf.append("\n" + f.toString(lmargin + 20, target));
                }
                buf.append(")\n");

                buf.append(pad + " '(");
                if (constant_pool.size() > 0) {
                    buf.append("\n" + cpToString(lmargin + 20, constant_pool, target));
                }
                buf.append("\n" + pad + " )\n");

                buf.append(pad + " (list");
                for (int j = 0; j < methods.size(); j++) {
                    M6Method m = (M6Method) methods.get(j);
                    buf.append("\n" + m.toString(lmargin + 2, target));
                }
                buf.append(")\n");
                buf.append(pad + " '(REF -1))");
                break;
            case M6:
                buf.append(pad + "'(class ");
                buf.append("\"" + name + "\"\n");
                //   buf.append(pad + "        ");
                //   buf.append(booleanToACL2String(is_interface)+"\n");
                buf.append(pad + "        \"" + super_name + "\"\n");

                buf.append(pad + "        " + "(constant_pool");
                if (constant_pool.size() > 0) {
                    buf.append("\n" + cpToString(lmargin + 20, constant_pool, target));
                }
                buf.append(")\n");

                buf.append(pad + "        " + "(fields");
                for (int j = 0; j < fields.size(); j++) {
                    M6Field f = (M6Field) fields.get(j);
                    buf.append("\n" + f.toString(lmargin + 20, target));
                }
                buf.append(")\n");

                buf.append(pad + "        " + "(methods");
                for (int j = 0; j < methods.size(); j++) {
                    M6Method m = (M6Method) methods.get(j);
                    buf.append("\n" + m.toString(lmargin + 20, target));
                }
                buf.append(")\n");

                buf.append(pad + "        " + "(interfaces");
                for (int i = 0; i < interfaces.size(); i++) {
                    buf.append(" \"" + interfaces.get(i) + "\"");
                }
                buf.append(")\n");
                buf.append(accessflags.toString(lmargin + 8) + "\n");

                buf.append(pad + "        " + "(attributes");

                for (int i = 0; i < attributes.size(); i++) {
                    buf.append("\n" + pad + "          "
                            + "(attribute \"" + ((com.ibm.toad.cfparse.attributes.AttrInfo) attributes.get(i)).getName() + "\")");
                }
                buf.append("))");
                break;
        }
        return buf.toString();
    }

    private static String vectorToQuotedString(Vector vec) {
        StringBuffer buf = new StringBuffer();

        for (int i = 0; i < vec.size(); i++) {
            buf.append("\"" + vec.get(i) + "\"");
            if (i < (vec.size() - 1)) {
                buf.append(" ");
            }
        }
        return buf.toString();
    }

    /**
     * Sets the amount of debugging information that should be printed as this
     * class is processed.
     *
     * @param b		<code>true</code>, if debugging information should printed,
     * <code>false</code> otherwise.
     */
    public void setDebugMode(boolean b) {
        debug = b;
    }
}
