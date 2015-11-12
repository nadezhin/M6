/* M6Class.java:  a Java representation of an ACL2 class object
 *
 * $Id: M6Class.java,v 1.6 2002/12/13 00:18:12 hbl Exp hbl $
 */

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.DescriptorException;
import com.sun.tools.classfile.Field;
import java.util.*;
import java.io.*;
import java.nio.file.Paths;

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

    private ClassFile cf;

    private String name;
    private boolean is_interface;
    private String super_name;
    private Vector constant_pool;
    private final List<String> interfaces = new ArrayList<>();
    private Vector fields;
    private Vector methods;
    private M6AccessFlags accessflags;
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

    static class StringRef {

        final String value;

        StringRef(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return toString(false);
        }

        public String toString(boolean asCFParse) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\b':
                        sb.append("\\b");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\f':
                        sb.append("\\f");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '"':
                        sb.append("\\\"");
                        break;
                    case '\'':
                        // Compatibility with the bug in CFParse
                        sb.append(asCFParse ? "\\n" : "\\'");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    default:
                        if (c >= ' ' && c <= '~') {
                            sb.append(c);
                        } else {
                            sb.append("\\u");
                            String s = Integer.toHexString(c);
                            for (int j = s.length(); j < 4; j++) {
                                sb.append('0');
                            }
                            sb.append(s);
                        }
                }
            }
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StringRef
                    && value.equals(((StringRef) o).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode() + 17;
        }
    }

    /**
     * Given a CFParse ClassFile object, this constructor creates an M5Class.
     * Note that you must then call <code>processClassFile()</code> before
     * calling methods like <code>toString</code>.
     *
     * @param c the parsed class file (from the CFParse library)
     */
    public M6Class(String className) throws IOException, ConstantPoolException, DescriptorException {
        cf = ClassFile.read(Paths.get(className));
        name = null;
        super_name = null;
        fields = new Vector();
        methods = new Vector();
        constant_pool = new Vector();

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
    public void processClassFile(Target target) throws IOException, ConstantPoolException, DescriptorException {
        /* First, the name of the class itself */

        name = cf.constant_pool.getClassInfo(cf.this_class).getName().replace('/', '.');
        super_name = cf.super_class != 0 ? cf.constant_pool.getClassInfo(cf.super_class).getName().replace('/', '.') : "";

        is_interface = !cf.access_flags.is(AccessFlags.ACC_INTERFACE);
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
        resolveAccessFlags();

        lntdesc.append("))\n");
        lvtdesc.append("))\n");
    }

    private void resolveAccessFlags() {
        accessflags = new M6AccessFlags(cf.access_flags);
    }

    private void resolveInterfaces() throws ConstantPoolException {
        for (int i = 0; i < cf.interfaces.length; i++) {
            interfaces.add(cf.constant_pool.getClassInfo(cf.interfaces[i]).getName().replace('/', '.'));
        }
    }

    private void resolveFields() throws IOException, ConstantPoolException, DescriptorException {
        Field[] fl = cf.fields;
        for (int i = 0; i < fl.length; i++) {
            M6Field field = new M6Field(cf, fl[i]);
            field.processField(constant_pool);
            fields.addElement(field);
        }
    }

    private void resolveMethods(Target target) throws IOException, ConstantPoolException, DescriptorException {
        for (int i = 0; i < cf.methods.length; i++) {
            M6Method meth = new M6Method(cf, cf.methods[i]);
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
            } else if (cp.get(i) instanceof StringRef) {
                StringRef strRef = (StringRef) cp.get(i);
                switch (target) {
                    case M5:
                        buf.append("(STRING (REF -1) ; \"" + strRef + "\"\n");
                        buf.append(pad).append("  ");
                        for (int j = 0; j < strRef.value.length(); j++) {
                            buf.append(" ").append((int) strRef.value.charAt(j));
                        }
                        buf.append(")");
                        break;
                    case M6:
                        buf.append("(STRING  \"" + strRef.toString(true) + "\")");
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
    public String toString(int lmargin, Target target) throws ConstantPoolException {
        StringBuffer buf = new StringBuffer();
        StringBuffer padb = new StringBuffer();
        for (int i = 0; i < lmargin; i++) {
            padb.append(" ");
        }

        String pad = padb.toString();
        switch (target) {
            case M5:
                buf.append(pad + "(make-class-decl\n");
                if (ACL2utils.NAME_AND_TYPE) {
                    buf.append(pad + " \""
                            + cf.constant_pool.getUTF8Value(cf.constant_pool.getClassInfo(cf.this_class).name_index) + "\"\n");
                    if (cf.super_class != 0) {
                        buf.append(pad + " '(\""
                                + cf.constant_pool.getUTF8Value(cf.constant_pool.getClassInfo(cf.super_class).name_index) + "\")\n");
                    } else {
                        buf.append(pad + " ())");
                    }
                } else {
                    buf.append(pad + " \"" + name + "\"\n");
                    buf.append(pad + " '(\"" + super_name + "\")\n");
                }

                buf.append(pad + " '(");
                for (int j = 0; j < fields.size(); j++) {
                    M6Field f = (M6Field) fields.get(j);
                    if (f.field.access_flags.is(AccessFlags.ACC_STATIC)) {
                        continue;
                    }
                    buf.append("\n" + f.toString(lmargin + 2, target));
                }
                buf.append(")\n");

                buf.append(pad + " '(");
                for (int j = 0; j < fields.size(); j++) {
                    M6Field f = (M6Field) fields.get(j);
                    if (!f.field.access_flags.is(AccessFlags.ACC_STATIC)) {
                        continue;
                    }
                    buf.append("\n" + f.toString(lmargin + 2, target));
                }
                buf.append(")\n");

                buf.append(pad + " '(");
                if (constant_pool.size() > 0) {
                    buf.append("\n" + cpToString(lmargin + 2, constant_pool, target));
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

                for (int i = 0; i < cf.attributes.size(); i++) {
                    buf.append("\n" + pad + "          "
                            + "(attribute \"" + cf.attributes.get(i).getName(cf.constant_pool) + "\")");
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
