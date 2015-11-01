/* M6Class.java:  a Java representation of an ACL2 class object
 *
 * $Id: M6Class.java,v 1.6 2002/12/13 00:18:12 hbl Exp hbl $
 */

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.DescriptorException;
import com.sun.tools.classfile.Field;
import com.sun.tools.classfile.Method;
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

    private com.ibm.toad.cfparse.ClassFile cf0;
    private ClassFile cf;

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
        cf0 = new com.ibm.toad.cfparse.ClassFile(className);
        cf = ClassFile.read(Paths.get(className));
        checkClassFiles(cf0, cf);
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

    private static void checkClassFiles(com.ibm.toad.cfparse.ClassFile cf0, ClassFile cf) throws ConstantPoolException, DescriptorException {
        assert cf0.getMagic() == cf.magic;
        assert cf0.getMinor() == cf.minor_version;
        assert cf0.getMajor() == cf.major_version;
        assert cf0.getCP().length() == cf.constant_pool.size();
        int next = 1;
        BitSet validEntries = new BitSet();
        for (ConstantPool.CPInfo current : cf.constant_pool.entries()) {
            validEntries.set(next);
            switch (current.getTag()) {
                case ConstantPool.CONSTANT_Double:
                case ConstantPool.CONSTANT_Long:
                    next += 2;
                    break;
                default:
                    next += 1;
            }
        }
        for (int i = 0; i < cf.constant_pool.size(); i++) {
            com.ibm.toad.cfparse.ConstantPoolEntry entry = cf0.getCP().get(i);
            int entryType = cf0.getCP().getType(i);
            if (!validEntries.get(i)) {
                if (i == 0) {
                    assert entryType == com.ibm.toad.cfparse.ConstantPool.CONSTANT_Utf8;
                    assert entry instanceof com.ibm.toad.cfparse.ConstantPool.Utf8Entry;
                    assert entry.getAsString().equals("<dummy Entry>");
                } else {
                    assert entryType == -1;
                    assert entry == null;
                }
                continue;
            }
            ConstantPool.CPInfo info = cf.constant_pool.get(i);
            assert entryType == info.getTag();
            switch (info.getTag()) {
                case ConstantPool.CONSTANT_Utf8:
                    com.ibm.toad.cfparse.ConstantPool.Utf8Entry entryUtf8
                            = (com.ibm.toad.cfparse.ConstantPool.Utf8Entry) entry;
                    ConstantPool.CONSTANT_Utf8_info infoUt8
                            = (ConstantPool.CONSTANT_Utf8_info) info;
                    assert entryUtf8.getValue().equals(infoUt8.value);
                    break;
                case ConstantPool.CONSTANT_Integer:
                    com.ibm.toad.cfparse.ConstantPool.IntegerEntry entryInteger
                            = (com.ibm.toad.cfparse.ConstantPool.IntegerEntry) entry;
                    ConstantPool.CONSTANT_Integer_info infoInteger
                            = (ConstantPool.CONSTANT_Integer_info) info;
                    assert entryInteger.getValue() == infoInteger.value;
                    break;
                case ConstantPool.CONSTANT_Float:
                    com.ibm.toad.cfparse.ConstantPool.FloatEntry entryFloat
                            = (com.ibm.toad.cfparse.ConstantPool.FloatEntry) entry;
                    ConstantPool.CONSTANT_Float_info infoFloat
                            = (ConstantPool.CONSTANT_Float_info) info;
                    assert Float.floatToRawIntBits(entryFloat.getValue()) == Float.floatToRawIntBits(infoFloat.value);
                    break;
                case ConstantPool.CONSTANT_Long:
                    com.ibm.toad.cfparse.ConstantPool.LongEntry entryLong
                            = (com.ibm.toad.cfparse.ConstantPool.LongEntry) entry;
                    ConstantPool.CONSTANT_Long_info infoLong
                            = (ConstantPool.CONSTANT_Long_info) info;
                    assert entryLong.getValue() == infoLong.value;
                    break;
                case ConstantPool.CONSTANT_Double:
                    com.ibm.toad.cfparse.ConstantPool.DoubleEntry entryDouble
                            = (com.ibm.toad.cfparse.ConstantPool.DoubleEntry) entry;
                    ConstantPool.CONSTANT_Double_info infoDouble
                            = (ConstantPool.CONSTANT_Double_info) info;
                    assert Double.doubleToRawLongBits(entryDouble.getValue()) == Double.doubleToRawLongBits(infoDouble.value);
                    break;
                case ConstantPool.CONSTANT_Class:
                    com.ibm.toad.cfparse.ConstantPool.ClassEntry entryClass
                            = (com.ibm.toad.cfparse.ConstantPool.ClassEntry) entry;
                    ConstantPool.CONSTANT_Class_info infoClass
                            = (ConstantPool.CONSTANT_Class_info) info;
                    entryClass.getAsString().equals(infoClass.getName());
                    break;
                case ConstantPool.CONSTANT_String:
                    com.ibm.toad.cfparse.ConstantPool.StringEntry entryString
                            = (com.ibm.toad.cfparse.ConstantPool.StringEntry) entry;
                    ConstantPool.CONSTANT_String_info infoString
                            = (ConstantPool.CONSTANT_String_info) info;
                    entryString.getAsString().equals(infoString.getString());
                    break;
                case ConstantPool.CONSTANT_Fieldref:
                    com.ibm.toad.cfparse.ConstantPool.FieldrefEntry entryFieldref
                            = (com.ibm.toad.cfparse.ConstantPool.FieldrefEntry) entry;
                    ConstantPool.CONSTANT_Fieldref_info infoFieldref
                            = (ConstantPool.CONSTANT_Fieldref_info) info;
                    entryFieldref.getAsString().equals(infoFieldref.toString());
                    break;
                case ConstantPool.CONSTANT_Methodref:
                    com.ibm.toad.cfparse.ConstantPool.MethodrefEntry entryMethodref
                            = (com.ibm.toad.cfparse.ConstantPool.MethodrefEntry) entry;
                    ConstantPool.CONSTANT_Methodref_info infoMethodref
                            = (ConstantPool.CONSTANT_Methodref_info) info;
                    entryMethodref.getAsString().equals(infoMethodref.toString());
                    break;
                case ConstantPool.CONSTANT_InterfaceMethodref:
                    com.ibm.toad.cfparse.ConstantPool.InterfaceMethodrefEntry entryInterfaceMethodref
                            = (com.ibm.toad.cfparse.ConstantPool.InterfaceMethodrefEntry) entry;
                    ConstantPool.CONSTANT_InterfaceMethodref_info infoInterfaceMethodref
                            = (ConstantPool.CONSTANT_InterfaceMethodref_info) info;
                    entryInterfaceMethodref.getAsString().equals(infoInterfaceMethodref.toString());
                    break;
                case ConstantPool.CONSTANT_NameAndType:
                    com.ibm.toad.cfparse.ConstantPool.NameAndTypeEntry entryNameAndType
                            = (com.ibm.toad.cfparse.ConstantPool.NameAndTypeEntry) entry;
                    ConstantPool.CONSTANT_NameAndType_info infoNameAndType
                            = (ConstantPool.CONSTANT_NameAndType_info) info;
                    entryNameAndType.getAsString().equals(infoNameAndType.toString());
                    break;
                case ConstantPool.CONSTANT_MethodHandle:
                    ConstantPool.CONSTANT_MethodHandle_info infoMethodHandle
                            = (ConstantPool.CONSTANT_MethodHandle_info) info;
                    assert false;
                    break;
                case ConstantPool.CONSTANT_MethodType:
                    ConstantPool.CONSTANT_MethodType_info infoMethodType
                            = (ConstantPool.CONSTANT_MethodType_info) info;
                    assert false;
                    break;
                case ConstantPool.CONSTANT_InvokeDynamic:
                    ConstantPool.CONSTANT_InvokeDynamic_info infoInvokeDynamic
                            = (ConstantPool.CONSTANT_InvokeDynamic_info) info;
                    assert false;
                    break;
                default:
                    throw new AssertionError();
            }
        }
        assert cf0.getAccess() == cf.access_flags.flags;
        ConstantPool.CONSTANT_Class_info thisClassCP = (ConstantPool.CONSTANT_Class_info) cf.constant_pool.get(cf.this_class);
        ConstantPool.CONSTANT_Utf8_info thisName = (ConstantPool.CONSTANT_Utf8_info) cf.constant_pool.get(thisClassCP.name_index);
        assert cf0.getName().replace('.', '/').equals(thisName.value);
        if (cf.super_class != 0) {
            ConstantPool.CONSTANT_Class_info superClassCP = (ConstantPool.CONSTANT_Class_info) cf.constant_pool.get(cf.super_class);
            ConstantPool.CONSTANT_Utf8_info superName = (ConstantPool.CONSTANT_Utf8_info) cf.constant_pool.get(superClassCP.name_index);
            assert cf0.getSuperName().replace('.', '/').equals(superName.value);
        } else {
            assert cf0.getSuperName().isEmpty();
        }
        assert cf0.getInterfaces().length() == cf.interfaces.length;
        for (int i = 0; i < cf.interfaces.length; i++) {
            ConstantPool.CONSTANT_Class_info interfaceCP = (ConstantPool.CONSTANT_Class_info) cf.constant_pool.get(cf.interfaces[i]);
            ConstantPool.CONSTANT_Utf8_info interfaceName = (ConstantPool.CONSTANT_Utf8_info) cf.constant_pool.get(interfaceCP.name_index);
            assert cf0.getInterfaces().get(i).replace('.', '/').equals(interfaceName.value);
        }
        assert cf0.getFields().length() == cf.fields.length;
        for (int i = 0; i < cf.fields.length; i++) {
            Field field = cf.fields[i];
            com.ibm.toad.cfparse.FieldInfo fieldInfo = cf0.getFields().get(i);
            assert fieldInfo.getAccess() == field.access_flags.flags;
            ConstantPool.CONSTANT_Utf8_info fieldName = (ConstantPool.CONSTANT_Utf8_info) cf.constant_pool.get(field.name_index);
            assert fieldInfo.getName().equals(fieldName.value);
            assert fieldInfo.getDesc().equals(field.descriptor.getValue(cf.constant_pool));
            assert fieldInfo.getType().equals(field.descriptor.getFieldType(cf.constant_pool));
            assert fieldInfo.getAttrs().length() == field.attributes.size();
            for (int j = 0; j < field.attributes.size(); j++) {
                Attribute attr = field.attributes.get(j);
                com.ibm.toad.cfparse.attributes.AttrInfo attrInfo = fieldInfo.getAttrs().get(j);
                assert attrInfo.getName().equals(attr.getName(cf.constant_pool));
            }
        }
        assert cf0.getMethods().length() == cf.methods.length;
        for (int i = 0; i < cf.methods.length; i++) {
            Method method = cf.methods[i];
            com.ibm.toad.cfparse.MethodInfo methodInfo = cf0.getMethods().get(i);
            assert methodInfo.getAccess() == method.access_flags.flags;
            ConstantPool.CONSTANT_Utf8_info methodName = (ConstantPool.CONSTANT_Utf8_info) cf.constant_pool.get(method.name_index);
            assert methodInfo.getName().equals(methodName.value);
            assert methodInfo.getDesc().equals(method.descriptor.getValue(cf.constant_pool));
            String[] params = methodInfo.getParams();
            assert params.length == method.descriptor.getParameterCount(cf.constant_pool);
            String paramTypes = method.descriptor.getParameterTypes(cf.constant_pool);
            assert paramTypes.charAt(0) == '(' && paramTypes.charAt(paramTypes.length() - 1) == ')';
            paramTypes = paramTypes.substring(1, paramTypes.length() - 1);
            if (params.length > 0) {
                String[] params2 = paramTypes.split(",");
                assert params.length == params2.length;
                for (int j = 0; j < params.length; j++) {
                    assert params[j].equals(params2[j].trim());
                }
            } else {
                assert paramTypes.isEmpty();
            }
            assert methodInfo.getReturnType().equals(method.descriptor.getReturnType(cf.constant_pool));
            assert methodInfo.getAttrs().length() == method.attributes.size();
            for (int j = 0; j < method.attributes.size(); j++) {
                Attribute attr = method.attributes.get(j);
                com.ibm.toad.cfparse.attributes.AttrInfo attrInfo = methodInfo.getAttrs().get(j);
                assert attrInfo.getName().equals(attr.getName(cf.constant_pool));
            }
        }
        assert cf0.getAttrs().length() == cf.attributes.size();
        for (int j = 0; j < cf.attributes.size(); j++) {
            Attribute attr = cf.attributes.get(j);
            com.ibm.toad.cfparse.attributes.AttrInfo attrInfo = cf0.getAttrs().get(j);
            assert attrInfo.getName().equals(attr.getName(cf.constant_pool));
        }
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

        name = cf0.getName();
        super_name = cf0.getSuperName();

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
        resolveAttributes();
        resolveAccessFlags();

        lntdesc.append("))\n");
        lvtdesc.append("))\n");
    }

    private void resolveAccessFlags() {
        accessflags = new M6AccessFlags(cf.access_flags);
    }

    private void resolveInterfaces() {
        com.ibm.toad.cfparse.InterfaceList il = cf0.getInterfaces();
        for (int i = 0; i < il.length(); i++) {
            interfaces.addElement(il.getInterfaceName(i));
        }
    }

    private void resolveAttributes() {
        // not used here.
        com.ibm.toad.cfparse.attributes.AttrInfoList al = cf0.getAttrs();
        for (int i = 0; i < al.length(); i++) {
            attributes.addElement(al.get(i));
        }
    }

    private void resolveFields() throws IOException, ConstantPoolException, DescriptorException {
        com.ibm.toad.cfparse.FieldInfoList fl0 = cf0.getFields();
        Field[] fl = cf.fields;
        for (int i = 0; i < fl0.length(); i++) {
            M6Field field = new M6Field(cf, fl[i]);
            field.processField(constant_pool);
            fields.addElement(field);
        }
    }

    private void resolveMethods(Target target) throws IOException, ConstantPoolException {
        /* This attribute ensures we have relative tags for
         * branches, rather than byte offsets */
        com.ibm.toad.cfparse.attributes.CodeAttrInfo.setViewer(com.ibm.toad.cfparse.instruction.MutableCodeSegment.getViewer());

        com.ibm.toad.cfparse.MethodInfoList ml = cf0.getMethods();
        for (int i = 0; i < ml.length(); i++) {
            M6Method meth = new M6Method(cf0, cf, ml.get(i), cf.methods[i]);
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
                    if (f.field.access_flags.is(AccessFlags.ACC_STATIC)) {
                        continue;
                    }
                    buf.append("\n" + f.toString(lmargin + 20, target));
                }
                buf.append(")\n");

                buf.append(pad + " '(");
                for (int j = 0; j < fields.size(); j++) {
                    M6Field f = (M6Field) fields.get(j);
                    if (!f.field.access_flags.is(AccessFlags.ACC_STATIC)) {
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
