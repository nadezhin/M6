/* M6Class.java:  a Java representation of an ACL2 class object
 *
 * $Id: M6Class.java,v 1.6 2002/12/13 00:18:12 hbl Exp hbl $
 */

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.DescriptorException;
import com.sun.tools.classfile.Opcode;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * M5Class represents a Java class in a format that allows easy conversion to a
 * format that ACL2 (more specifically M5) can handle. When an M5Class object is
 * created, a ClassFile object is passed into the constructor. This object
 * represents the parsedresolve classfile (in IBM's
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
 * <li>The methods (a List of M5Method objects)
 * </ul>
 *
 * @author George M. Porter
 * @author J Strother Moore
 * @version 1.0
 */
public class M6Class implements Comparable<M6Class> {

    final ClassFile cf;
    private final String name;
    private final String superclassName;
    final CPEntry[] constantPool;
    private final List<CPEntry> constant_pool = new ArrayList<>();
    final String[] interfaceNames;
    M6Class[] allSuperclasses;
    private final M6Field[] fields;
    private final M6Method[] methods;
    private boolean debug;
    int level;

    @Override
    public int compareTo(M6Class o) {
        if (this == o) return 0;
        if (this.getJvmName().equals("java/lang/Object")) return -1;
        if (o.getJvmName().equals("java/lang/Object")) return 1;
        if (this.isInterface() && o.isClass()) return -1;
        if (this.isClass() && o.isInterface()) return 1;
        if (this.level < o.level) return -1;
        if (this.level > o.level) return 1;
        return this.getJvmName().compareTo(o.getJvmName());
    }

    abstract class CPEntry {

        private final int cpix;

        CPEntry(int cpix) {
            this.cpix = cpix;
        }

        ConstantPool getCP() {
            return cf.constant_pool;
        }

        abstract ConstantPool.CPInfo getInfo();

        void appendM5(StringBuilder sb, int indent) {
            nl(sb, indent, "(UNKNOWN " + getInfo().getTag() + ")");
        }

        void appendM6(StringBuilder sb, int indent) {
            appendM5(sb, indent);
        }

        void appendCPRefM5(StringBuilder sb, StringBuilder sb2, Opcode op) {
            sb.append(' ');
            if (constant_pool.contains(this)) {
                sb.append(constant_pool.indexOf(this));
            } else {
                constant_pool.add(this);
                sb.append(constant_pool.size() - 1);  // zero-based indices
            }
        }

        void appendCPRefM6(StringBuilder sb, StringBuilder sb2, Opcode op) {
            appendCPRefM5(sb, sb2, op);
        }
    }

    abstract class CPRefEntry extends CPEntry {

        final String className;
        final String name;
        final String desc;

        CPRefEntry(int cpix) throws ConstantPoolException {
            super(cpix);
            ConstantPool.CPRefInfo info
                    = (ConstantPool.CPRefInfo) cf.constant_pool.get(cpix);
            className = info.getClassName();
            ConstantPool.CONSTANT_NameAndType_info infoNameAndType = info.getNameAndTypeInfo();
            name = infoNameAndType.getName();
            desc = infoNameAndType.getType();

        }

        @Override
        abstract ConstantPool.CPRefInfo getInfo();

        @Override
        void appendCPRefM5(StringBuilder sb, StringBuilder sb2, Opcode op) {
            sb.append(' ');
            String[] ss = M6Method.splitMethodDesc(desc);
            int paramCount = ss.length - 1;
            for (int i = 1; i < ss.length; i++) {
                if (ss[i].equals("D") || ss[i].equals("J")) {
                    paramCount++;
                }
            }
            if (ACL2utils.NAME_AND_TYPE) {
                sb
                        .append("\"")
                        .append(className)
                        .append("\" \"")
                        .append(name)
                        .append(':')
                        .append(desc)
                        .append("\"");
            } else {
                sb
                        .append("\"")
                        .append(className.replace('/', '.'))
                        .append("\" \"")
                        .append(name)
                        .append("\"");
            }
            sb.append(' ').append(paramCount);
        }

        @Override
        void appendCPRefM6(StringBuilder sb, StringBuilder sb2, Opcode op) {
            String[] ss = M6Method.splitMethodDesc(desc);
            sb
                    .append("\n\t\t\t\t\t")
                    .append("(methodCP \"")
                    .append(name)
                    .append("\" \"")
                    .append(className.replace('/', '.'))
                    .append("\" ")
                    .append('(');
            for (int i = 1; i < ss.length; i++) {
                if (i != 1) {
                    sb.append(' ');
                }
                sb.append(ACL2utils.descToACL2TypeStr(ss[i]));
            }
            sb.append(") ")
                    .append(ACL2utils.descToACL2TypeStr(ss[0]))
                    .append(')');
        }
    }

    class CPUtf8 extends CPEntry {

        private final ConstantPool.CONSTANT_Utf8_info info;

        CPUtf8(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_Utf8_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_Utf8_info getInfo() {
            return info;
        }
    }

    class CPInteger extends CPEntry {

        private final ConstantPool.CONSTANT_Integer_info info;

        CPInteger(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_Integer_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_Integer_info getInfo() {
            return info;
        }

        @Override
        void appendM5(StringBuilder sb, int indent) {
            nl(sb, indent, "(INT " + info.value + ")");
        }

        @Override
        void appendCPRefM5(StringBuilder sb, StringBuilder sb2, Opcode op) {
            super.appendCPRefM5(sb, sb2, op);
            sb2.append(";;INT:: \"" + info.value + "\"");
        }
    }

    class CPFloat extends CPEntry {

        private final ConstantPool.CONSTANT_Float_info info;

        CPFloat(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_Float_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_Float_info getInfo() {
            return info;
        }

        @Override
        void appendM5(StringBuilder sb, int indent) {
            String s = "(FLOAT #x"
                    + padLeft(Integer.toHexString(Float.floatToRawIntBits(info.value)), 8)
                    + ") ; " + Float.toHexString(info.value) + " " + info.value;
            nl(sb, indent, s);
        }

        @Override
        void appendCPRefM5(StringBuilder sb, StringBuilder sb2, Opcode op) {
            super.appendCPRefM5(sb, sb2, op);
            sb2.append(";;FLOAT:: \"" + info.value + "\"");
        }
    }

    class CPLong extends CPEntry {

        private final ConstantPool.CONSTANT_Long_info info;

        CPLong(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_Long_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_Long_info getInfo() {
            return info;
        }

        @Override
        void appendM5(StringBuilder sb, int indent) {
            nl(sb, indent, "(LONG " + info.value + ")");
        }

        @Override
        void appendCPRefM5(StringBuilder sb, StringBuilder sb2, Opcode op) {
            super.appendCPRefM5(sb, sb2, op);
            sb2.append(op == Opcode.LDC2_W ? ";; LONG:: \"" : ";;Long:: \"")
                    .append(info.value)
                    .append("\"");
        }
    }

    class CPDouble extends CPEntry {

        private final ConstantPool.CONSTANT_Double_info info;

        CPDouble(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_Double_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_Double_info getInfo() {
            return info;
        }

        @Override
        void appendM5(StringBuilder sb, int indent) {
            String s = "(DOUBLE #x"
                    + padLeft(Long.toHexString(Double.doubleToRawLongBits(info.value)), 16)
                    + ") ; " + Double.toHexString(info.value) + " " + info.value;
            nl(sb, indent, s);
        }

        @Override
        void appendCPRefM5(StringBuilder sb, StringBuilder sb2, Opcode op) {
            super.appendCPRefM5(sb, sb2, op);
            sb2.append(op == Opcode.LDC2_W ? ";; DOUBLE:: \"" : ";;Doulbe:: \"")
                    .append(info.value)
                    .append("\"");
        }
    }

    class CPClass extends CPEntry {

        private final ConstantPool.CONSTANT_Class_info info;
        private String name;

        CPClass(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_Class_info) getCP().get(cpix);
            name = info.getName();
        }

        @Override
        ConstantPool.CONSTANT_Class_info getInfo() {
            return info;
        }

        @Override
        void appendM5(StringBuilder sb, int indent) {
            nl(sb, indent, "(CLASS (REF -1) \"" + name + "\")");
        }

        @Override
        void appendM6(StringBuilder sb, int indent) {
            nl(sb, indent, "(CLASS  \"" + name.replace('/', '.') + "\")");
        }

        @Override
        void appendCPRefM5(StringBuilder sb, StringBuilder sb2, Opcode op) {
            switch (op) {
                case LDC:
                case LDC_W:
                case LDC2_W:
                    super.appendCPRefM5(sb, sb2, op);
                    sb2.append(";;CLASS:: \"" + name + "\"");
                    break;
                default:
                    sb.append(' ').append("\"" + name + "\"");
                    break;
            }
        }

        @Override
        void appendCPRefM6(StringBuilder sb, StringBuilder sb2, Opcode op) {
            switch (op) {
                case LDC:
                case LDC_W:
                case LDC2_W:
                    super.appendCPRefM5(sb, sb2, op);
                    sb2.append(";;CLASS:: \"" + name.replace('/', '.') + "\"");
                    break;
                default:
                    sb.append(' ').append(ACL2utils.classNameToACL2TypeStr(name));
                    break;
            }
        }
    }

    class CPString extends CPEntry {

        private final ConstantPool.CONSTANT_String_info info;
        private String value;

        CPString(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_String_info) getCP().get(cpix);
            value = info.getString();
        }

        @Override
        ConstantPool.CONSTANT_String_info getInfo() {
            return info;
        }

        @Override
        void appendM5(StringBuilder sb, int indent) {
            nl(sb, indent, "(STRING (REF -1) ; \"");
            appendString(sb, false);
            sb.append("\"");
            nl(sb, indent + 2);
            for (int j = 0; j < value.length(); j++) {
                sb.append(" ").append((int) value.charAt(j));
            }
            sb.append(")");
        }

        @Override
        void appendM6(StringBuilder sb, int indent) {
            nl(sb, indent, "(STRING  \"");
            appendString(sb, true);
            sb.append("\")");
        }

        private void appendString(StringBuilder sb, boolean cfBug) {
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
                        sb.append(cfBug ? "\\n" : "\\'");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    default:
                        if (c >= ' ' && c <= '~') {
                            sb.append(c);
                        } else {
                            sb.append("\\u").append(padLeft(Integer.toHexString(c), 4));
                        }
                }
            }
        }

        @Override
        void appendCPRefM5(StringBuilder sb, StringBuilder sb2, Opcode op) {
            super.appendCPRefM5(sb, sb2, op);
            sb2.append(";;STRING:: \"");
            appendString(sb2, false);
            sb2.append("\"");
        }

        @Override
        void appendCPRefM6(StringBuilder sb, StringBuilder sb2, Opcode op) {
            super.appendCPRefM5(sb, sb2, op);
            sb2.append(";;STRING:: \"");
            appendString(sb2, true);
            sb2.append("\"");
        }
    }

    class CPFieldref extends CPRefEntry {

        private final ConstantPool.CONSTANT_Fieldref_info info;

        CPFieldref(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_Fieldref_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_Fieldref_info getInfo() {
            return info;
        }

        @Override
        void appendCPRefM5(StringBuilder sb, StringBuilder sb2, Opcode op) {
            sb.append(' ');
            if (ACL2utils.NAME_AND_TYPE) {
                sb
                        .append("\"")
                        .append(className)
                        .append("\" \"")
                        .append(name)
                        .append(':')
                        .append(desc)
                        .append("\"");
            } else {
                sb
                        .append("\"")
                        .append(className.replace('/', '.'))
                        .append("\" \"")
                        .append(name)
                        .append("\"");
                switch (desc) {
                    case "J":
                    case "D":
                        sb.append(" t");
                        break;
                }
            }
        }

        @Override
        void appendCPRefM6(StringBuilder sb, StringBuilder sb2, Opcode op) {
            sb
                    .append(" (fieldCP \"")
                    .append(name)
                    .append("\" \"")
                    .append(className.replace('/', '.'))
                    .append("\" ")
                    .append(ACL2utils.descToACL2TypeStr(desc))
                    .append(')');
        }
    }

    class CPMethodref extends CPRefEntry {

        private final ConstantPool.CONSTANT_Methodref_info info;

        CPMethodref(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_Methodref_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_Methodref_info getInfo() {
            return info;
        }
    }

    class CPInterfaceMethodref extends CPRefEntry {

        private final ConstantPool.CONSTANT_InterfaceMethodref_info info;

        CPInterfaceMethodref(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_InterfaceMethodref_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_InterfaceMethodref_info getInfo() {
            return info;
        }
    }

    class CPNameAndType extends CPEntry {

        private final ConstantPool.CONSTANT_NameAndType_info info;

        CPNameAndType(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_NameAndType_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_NameAndType_info getInfo() {
            return info;
        }
    }

    class CPMethodHandle extends CPEntry {

        private final ConstantPool.CONSTANT_MethodHandle_info info;

        CPMethodHandle(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_MethodHandle_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_MethodHandle_info getInfo() {
            return info;
        }
    }

    class CPMethodType extends CPEntry {

        private final ConstantPool.CONSTANT_MethodType_info info;

        CPMethodType(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_MethodType_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_MethodType_info getInfo() {
            return info;
        }
    }

    class CPInvokeDynamic extends CPEntry {

        private final ConstantPool.CONSTANT_InvokeDynamic_info info;

        CPInvokeDynamic(int cpix) throws ConstantPoolException {
            super(cpix);
            info = (ConstantPool.CONSTANT_InvokeDynamic_info) getCP().get(cpix);
        }

        @Override
        ConstantPool.CONSTANT_InvokeDynamic_info getInfo() {
            return info;
        }
    }

    /**
     * Given a class name, this constructor creates an M6Class.
     *
     * @param className the parsed class file
     */
    public M6Class(String className) throws IOException, ConstantPoolException, DescriptorException {
        cf = ClassFile.read(Paths.get(className));
        name = cf.getName();
        superclassName = cf.super_class != 0 ? cf.constant_pool.getClassInfo(cf.super_class).getName() : null;

        interfaceNames = new String[cf.interfaces.length];
        for (int i = 0; i < interfaceNames.length; i++) {
            interfaceNames[i] = cf.constant_pool.getClassInfo(cf.interfaces[i]).getName();
        }

        constantPool = new CPEntry[cf.constant_pool.size()];
        int cpix = 1;
        for (ConstantPool.CPInfo info : cf.constant_pool.entries()) {
            CPEntry cpe;
            switch (info.getTag()) {
                case ConstantPool.CONSTANT_Utf8:
                    cpe = new CPUtf8(cpix);
                    break;
                case ConstantPool.CONSTANT_Integer:
                    cpe = new CPInteger(cpix);
                    break;
                case ConstantPool.CONSTANT_Float:
                    cpe = new CPFloat(cpix);
                    break;
                case ConstantPool.CONSTANT_Long:
                    cpe = new CPLong(cpix);
                    break;
                case ConstantPool.CONSTANT_Double:
                    cpe = new CPDouble(cpix);
                    break;
                case ConstantPool.CONSTANT_Class:
                    cpe = new CPClass(cpix);
                    break;
                case ConstantPool.CONSTANT_String:
                    cpe = new CPString(cpix);
                    break;
                case ConstantPool.CONSTANT_Fieldref:
                    cpe = new CPFieldref(cpix);
                    break;
                case ConstantPool.CONSTANT_Methodref:
                    cpe = new CPMethodref(cpix);
                    break;
                case ConstantPool.CONSTANT_InterfaceMethodref:
                    cpe = new CPInterfaceMethodref(cpix);
                    break;
                case ConstantPool.CONSTANT_NameAndType:
                    cpe = new CPNameAndType(cpix);
                    break;
                case ConstantPool.CONSTANT_MethodHandle:
                    cpe = new CPMethodHandle(cpix);
                    break;
                case ConstantPool.CONSTANT_MethodType:
                    cpe = new CPMethodType(cpix);
                    break;
                case ConstantPool.CONSTANT_InvokeDynamic:
                    cpe = new CPInvokeDynamic(cpix);
                    break;
                default:
                    throw new AssertionError();
            }
            constantPool[cpix] = cpe;
            cpix += info.size();
        }

        fields = new M6Field[cf.fields.length];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = new M6Field(this, cf.fields[i]);
        }

        methods = new M6Method[cf.methods.length];
        for (int i = 0; i < methods.length; i++) {
            methods[i] = new M6Method(this, cf.methods[i]);
        }

        debug = true;
    }

    /**
     * Returns the name of the class this M6Class object represents.
     *
     * @return	A String containing the name of this class.
     */
    public String getJvmName() {
        return name;
    }

    /**
     * Returns the name of the class this M6Class object represents.
     *
     * @return	A String containing the name of this class.
     */
    public String getJavaName() {
        return name.replace('/', '.');
    }

    public boolean isClass() {
        return cf.isClass();
    }

    public boolean isInterface() {
        return cf.isInterface();
    }

    /**
     * Returns the name of the superclass.
     *
     * @return	A String containing the name of the superclass.
     */
    public String getJvmSuperclassName() {
        return superclassName;
    }

    /**
     * Returns the name of the superclass.
     *
     * @return	A String containing the name of the superclass.
     */
    public String getJavaSuperclassName() {
        return superclassName != null ? superclassName.replace('/', '.') : "";
    }

    public String getlntdesc() throws ConstantPoolException {
        StringBuilder lntdesc = new StringBuilder();
        lntdesc.append("(defconst *" + getJavaName() + "-lnt*\n"
                + "(list \n");
        for (M6Method meth : methods) {
            lntdesc.append("\n\n'" + meth.getLntStr());
        }
        lntdesc.append("))\n");
        return lntdesc.toString();
    }

    public String getlvtdesc() throws ConstantPoolException {
        StringBuilder lvtdesc = new StringBuilder();
        lvtdesc.append("(defconst *" + getJavaName() + "-lvt*\n"
                + "(list \n");
        for (M6Method meth : methods) {
            lvtdesc.append("\n\n'" + meth.getLvtStr());
        }
        lvtdesc.append("))\n");
        return lvtdesc.toString();
    }

    /**
     * This method processes the ClassFile given in the constructor. It is
     * necessary to call this method before querying this class.
     */
    public void processClassFile(Target target) throws IOException, ConstantPoolException, DescriptorException {
        /* First, the name of the class itself */

        if (debug) {
            System.out.println("Processing class: " + getJavaName());
        }

        for (M6Field field : fields) {
            field.processField(constant_pool);
        }
        for (M6Method meth : methods) {
            meth.processMethod(target);
        }
    }

    private static String padLeft(String s, int w) {
        while (s.length() < w) {
            s = '0' + s;
        }
        return s;
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
        StringBuilder buf = new StringBuilder();
        StringBuilder padb = new StringBuilder();
        for (int i = 0; i < lmargin; i++) {
            padb.append(" ");
        }

        String pad = padb.toString();
        switch (target) {
            case M5:
                buf.append(pad + "(make-class-decl\n");
                if (ACL2utils.NAME_AND_TYPE) {
                    buf.append(pad + " \"" + getJvmName() + "\"\n");
                    if (getJvmSuperclassName() != null) {
                        buf.append(pad + " '(\"" + getJvmSuperclassName() + "\")\n");
                    } else {
                        buf.append(pad + " ()\n");
                    }
                } else {
                    buf.append(pad + " \"" + getJavaName() + "\"\n");
                    buf.append(pad + " '(\"" + getJavaSuperclassName() + "\")\n");
                }

                buf.append(pad + " '(");
                for (M6Field f : fields) {
                    if (!f.isStatic()) {
                        nl(buf, lmargin + 2, "\"" + f.getName() + ":" + f.getDesc() + "\"");
                    }
                }
                buf.append(")\n");

                buf.append(pad + " '(");
                for (M6Field f : fields) {
                    if (f.isStatic()) {
                        nl(buf, lmargin + 2, "\"" + f.getName() + ":" + f.getDesc() + "\"");
                    }
                }
                buf.append(")\n");

                buf.append(pad + " '(");
                for (CPEntry cpe : constant_pool) {
                    cpe.appendM5(buf, lmargin + 2);
                }
                buf.append("\n" + pad + " )\n");

                buf.append(pad + " (list");
                for (M6Method m : methods) {
                    buf.append("\n" + m.toString(lmargin + 2, target));
                }
                buf.append(")\n");
                buf.append(pad + " '(REF -1))");
                break;
            case M6:
                indent(buf, lmargin);
                buf.append("'(class ").append("\"" + getJavaName() + "\"");
                nl(buf, lmargin + 8, "\"" + getJavaSuperclassName() + "\"");

                nl(buf, lmargin + 8, "(constant_pool");
                for (CPEntry cpe : constant_pool) {
                    cpe.appendM6(buf, lmargin + 20);
                }
                buf.append(")");

                nl(buf, lmargin + 8, "(fields");
                for (M6Field f : fields) {
                    nl(buf, lmargin + 20, "(field \"" + f.getName() + "\" "
                            + ACL2utils.descToACL2TypeStr(f.getDesc()) + " "
                            + ACL2utils.accessFlagsToString(f.getAccessFlags()) + " " + f.index + ")");
                }
                buf.append(")");

                nl(buf, lmargin + 8, "(methods");
                for (M6Method m : methods) {
                    buf.append("\n" + m.toString(lmargin + 20, target));
                }
                buf.append(")\n");

                buf.append(pad + "        " + "(interfaces");
                for (String interfaceName : interfaceNames) {
                    buf.append(" \"" + interfaceName.replace('/', '.') + "\"");
                }
                buf.append(")\n");
                buf.append(pad + "        " + ACL2utils.accessFlagsToString(cf.access_flags) + "\n");

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

    private static void indent(StringBuilder sb, int indent) {
        while (indent-- > 0) {
            sb.append(' ');
        }
    }

    private static void nl(StringBuilder sb, int indent) {
        sb.append('\n');
        indent(sb, indent);
    }

    private static void nl(StringBuilder sb, int indent, String s) {
        nl(sb, indent);
        sb.append(s);
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
