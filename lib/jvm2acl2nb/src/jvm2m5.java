// this program write to /var/local/hbl/....
// java jvm2acl2 some/path/tablename <.class files> <directories>
// it generate separate files for each .class file.
// and a file call table-name-class-table with lisp forms that load all class.
//
// it also take an -a argument that tells it to ignore method-body
//

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Field;
import com.sun.tools.classfile.Instruction;
import com.sun.tools.classfile.LineNumberTable_attribute;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.Opcode;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class jvm2m5 implements
        Instruction.KindVisitor<StringBuilder, Void>,
        ConstantPool.Visitor<String, Opcode> {

    private static final String usage
            = "Usage: jvm2m5 <output directory> <file names>* [<directory names>*]\n";

    public static void main(String[] args) {

        System.err.println("JVM --> M5  An automatic M5 state generator.");
        if (args.length <= 1) {
            System.err.println(usage);
            System.exit(0);
        }

        String outputPath = args[0];
        String[] collectedFiles = collectFileNames(Arrays.copyOfRange(args, 1, args.length));
        for (String fn : collectedFiles) {
            try {
                processOneFile(fn, outputPath);
            } catch (java.lang.Exception e) {
                System.err.println("Could not open file " + fn + " " + e);
                System.exit(0);
            }
        }
    }

    private static String[] collectFileNames(String[] args) {
        List<String> files = new ArrayList<>();
        try {
            for (int i = 0; i < args.length; i++) {
                File cur = new File(args[i]);
                if (cur.isDirectory()) {
                    String[] nextlevel = cur.list();
                    Arrays.sort(nextlevel);
                    for (int j = 0; j < nextlevel.length; j++) {
                        nextlevel[j] = (cur.getPath() + "/" + nextlevel[j]);
                    }
                    String[] names = collectFileNames(nextlevel);
                    for (int j = 0; j < names.length; j++) {
                        files.add(names[j]);
                    }
                } else {
                    String curn = cur.getPath();
                    int e = curn.lastIndexOf('.');
                    if (e != -1) {
                        String suffix = curn.substring(e + 1);
                        if (suffix.equals("class")) {
                            files.add(curn);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("File reading error!");
        }
        return files.toArray(new String[files.size()]);
    }

    private static void processOneFile(String classfn, String pathn) {
        try {
            ClassFile cf = ClassFile.read(Paths.get(classfn));
            String classname = cf.getName().replace('/', '.');
            jvm2m5 printVisitor = new jvm2m5(cf);
            String fn = pathn + "/" + classname + ".lisp";
            System.out.println("Writing " + fn);
            FileWriter outfile = new FileWriter(fn);
            printVisitor.printClass();
            assert printVisitor.indent == 0;
            outfile.write(printVisitor.sb.toString());
            outfile.close();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
            System.exit(-1);
        }
    }

    final ClassFile cf;
    final ConstantPool cp;
    final StringBuilder sb = new StringBuilder();
    final StringBuilder sb2 = new StringBuilder();

    jvm2m5(ClassFile cf) throws ConstantPoolException {
        this.cf = cf;
        cp = cf.constant_pool;
    }

    int indent;
    int mark;

    void indent(int delta) {
        indent += delta;
    }

    Void nl(String s) {
        if (sb2.length() > 0) {
            // round the line length to multiple of 8.
            int pos = 56;
            int sbl = sb.length();
            while (pos <= sb.length() - mark) {
                pos += 8;
                if (pos > 1000) {
                    pos = pos;
                }
            }
            int bc = mark + pos - sb.length();
            for (int j = 0; j < bc; j++) {
                sb.append(" ");
            }
            sb.append(sb2);
            sb2.setLength(0);
        }
        sb.append('\n');
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
        mark = sb.length();
        sb.append(s);
        return null;
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
    public String printClass() throws ConstantPoolException {
        sb.append("; Automatically generated by jvm2m5");
        nl("");
        nl("(in-package \"M5\")");
        nl("(include-book \"models/jvm/m5/m5\" :dir :system)");
        nl("");
        nl("(defconst *" + cf.getName().replace('/', '.') + "*");
        indent(4);
        String name = cf.getName();
        String supername = cf.super_class != 0 ? cf.getSuperclassName() : null;
        nl("(make-class-decl");
        nl(" \"" + name + "\"");
        if (supername != null) {
            nl(" '(\"" + supername + "\")");
        } else {
            nl(" ()");
        }

        nl(" '(");
        indent(2);
        for (Field f : cf.fields) {
            if (!f.access_flags.is(AccessFlags.ACC_STATIC)) {
                String fname = f.getName(cp);
                String fdesc = f.descriptor.getValue(cp);
                nl("\"" + fname + ":" + fdesc + "\"");
            }
        }
        sb.append(")");
        indent(-2);

        nl(" '(");
        indent(2);
        for (Field f : cf.fields) {
            if (f.access_flags.is(AccessFlags.ACC_STATIC)) {
                String fname = f.getName(cp);
                String fdesc = f.descriptor.getValue(cp);
                nl("\"" + fname + ":" + fdesc + "\"");
            }
        }
        sb.append(")");
        indent(-2);

        nl(" '(nil");
        indent(3);
        CPPrintM5 cpp = new CPPrintM5();
        int ind = 1;
        for (ConstantPool.CPInfo info : cp.entries()) {
            info.accept(cpp, null);
            sb2.append("; " + ind);
            ind += info.size();
            for (int i = 1; i < info.size(); i++) {
                nl("nil");
            }
        }
        indent(-3);
        nl("  )");

        nl(" (list");
        indent(2);
        for (Method m : cf.methods) {
            printMethod(m);
        }
        sb.append(")");
        indent(-2);
        nl(" '(ref -1)))");
        indent(-4);
        return sb.toString();
    }

    public void printMethod(Method method) throws ConstantPoolException {
        String name = method.getName(cp);
        String desc = method.descriptor.getValue(cp);
        Code_attribute cai = (Code_attribute) method.attributes.get(Attribute.Code);

        nl("");
        sb.append("'(\"")
                .append(name)
                .append(":")
                .append(desc)
                .append("\"")
                .append(' ')
                .append((method.access_flags.is(AccessFlags.ACC_SYNCHRONIZED) ? "t" : "nil"));
        indent(2);

        if (cai != null) {
            LineNumberTable_attribute lnt
                    = (LineNumberTable_attribute) cai.attributes.get(Attribute.LineNumberTable);
            int li = 0; // index in linenumber table
            // get the parsed code part
            for (Instruction instr : cai.getInstructions()) {
                if (lnt != null && li < lnt.line_number_table.length && lnt.line_number_table[li].start_pc == instr.getPC()) {
                    nl("; " + "line_number #" + lnt.line_number_table[li].line_number);
                    li++;
                }
                nl("(");
                instr.accept(this, null);
                sb.append(")");
                sb2.insert(0, "; " + instr.getPC());
            }
        } else {
            if (method.access_flags.is(AccessFlags.ACC_NATIVE)) {
                nl("; native method");
            }
        }
        indent(-2);
        nl(" )");
    }

    // Instruction.KindVisitor<String, Void> methods
    @Override
    public StringBuilder visitNoOperands(Instruction instr, Void p) {
        return sb.append(instr.getMnemonic());
    }

    @Override
    public StringBuilder visitArrayType(Instruction instr, Instruction.TypeKind tk, Void p) {
        return sb.append(instr.getMnemonic())
                .append(" T_")
                .append(tk.name.toUpperCase());
    }

    @Override
    public StringBuilder visitBranch(Instruction instr, int offset, Void p) {
        return sb.append(instr.getMnemonic())
                .append(' ')
                .append(offset);
    }

    @Override
    public StringBuilder visitConstantPoolRef(Instruction instr, int index, Void p) {
        sb.append(instr.getMnemonic());
        try {
            ConstantPool.CPInfo info = cp.get(index);
            sb2.append(" ");
            String cpref = info.accept(this, instr.getOpcode());
            return sb.append(cpref != null ? cpref : " " + index);
        } catch (ConstantPoolException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public StringBuilder visitConstantPoolRefAndValue(Instruction instr, int index, int value, Void p) {
        sb.append(instr.getMnemonic());
        try {
            ConstantPool.CPInfo info = cp.get(index);
            sb2.append(" ");
            String cpref = info.accept(this, instr.getOpcode());
            return sb.append(cpref != null ? cpref : " " + index).append(' ').append(value);
        } catch (ConstantPoolException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public StringBuilder visitLocal(Instruction instr, int index, Void p) {
        return sb.append(instr.getMnemonic()/*.replace("_w", "")*/)
                .append(' ')
                .append(index);
    }

    @Override
    public StringBuilder visitLocalAndValue(Instruction instr, int index, int value, Void p) {
        return sb.append(instr.getMnemonic().replace("_w", ""))
                .append(' ')
                .append(index)
                .append(' ')
                .append(value);
    }

    @Override
    public StringBuilder visitLookupSwitch(Instruction instr, int default_, int npairs, int[] matches, int[] offsets, Void p) {
        sb.append(instr.getMnemonic()) // the opcode
                .append(" (lookupswitchinfo ")
                .append(default_) // the default target
                .append(' ')
                .append(npairs) // the pair count
                .append(" (");
        for (int i = 0; i < npairs; i++) {
            sb.append('(')
                    .append(matches[i])
                    .append(" . ")
                    .append(offsets[i])
                    .append(") ");
        }
        sb.setCharAt(sb.length() - 1, ')');
        return sb.append(")");
    }

    @Override
    public StringBuilder visitTableSwitch(Instruction instr, int default_, int low, int high, int[] offsets, Void p) {
        sb.append(instr.getMnemonic()) // the opcode
                .append(" (tableswitchinfo ")
                .append(default_) // the default target
                .append(" (")
                .append(low)
                .append(" . ")
                .append(high)
                .append(") (");
        for (int i = 0; i < high - low + 1; i++) {
            sb.append(offsets[i])
                    .append(' ');
        }
        sb.setCharAt(sb.length() - 1, ')');
        return sb.append(")");
    }

    @Override
    public StringBuilder visitValue(Instruction instr, int value, Void p) {
        return sb.append(instr.getMnemonic())
                .append(' ')
                .append(value);
    }

    @Override
    public StringBuilder visitUnknown(Instruction instr, Void p) {
        return sb.append("bytecode ")
                .append(instr.getOpcode());
    }

    // ConstantPool.Visitor<Void, Opcode> methods
    @Override
    public String visitClass(ConstantPool.CONSTANT_Class_info c, Opcode op) {
        try {
            String name = c.getName();
            sb2.append("class " + name.replace('/', '.'));
            return null;
        } catch (ConstantPoolException e) {
            return e.getMessage();
        }
    }

    @Override
    public String visitDouble(ConstantPool.CONSTANT_Double_info c, Opcode op) {
        sb2.append(c.value).append("d");
        return null;
    }

    @Override
    public String visitFieldref(ConstantPool.CONSTANT_Fieldref_info c, Opcode op) {
        try {
            String className = c.getClassName();
            ConstantPool.CONSTANT_NameAndType_info infoNameAndType = c.getNameAndTypeInfo();
            String name = infoNameAndType.getName();
            String desc = infoNameAndType.getType();
            sb2.append(className.replace('/', '.') + "." + name + ":" + desc);
            return null;
//            return " \"" + className + "\" \"" + name + ":" + desc + "\"";
        } catch (ConstantPoolException e) {
            return e.getMessage();
        }
    }

    @Override
    public String visitFloat(ConstantPool.CONSTANT_Float_info c, Opcode op) {
        sb2.append(c.value).append("f");
        return null;
    }

    @Override
    public String visitInteger(ConstantPool.CONSTANT_Integer_info c, Opcode op) {
        sb2.append(c.value);
        return null;
    }

    @Override
    public String visitInterfaceMethodref(ConstantPool.CONSTANT_InterfaceMethodref_info c, Opcode op) {
        try {
            String className = c.getClassName();
            ConstantPool.CONSTANT_NameAndType_info infoNameAndType = c.getNameAndTypeInfo();
            String name = infoNameAndType.getName();
            String desc = infoNameAndType.getType();
            sb2.append(className.replace('/', '.') + "." + name + ":" + desc);
            return null;
        } catch (ConstantPoolException e) {
            return e.getMessage();
        }
    }

    @Override
    public String visitInvokeDynamic(ConstantPool.CONSTANT_InvokeDynamic_info c, Opcode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String visitLong(ConstantPool.CONSTANT_Long_info c, Opcode op) {
        sb2.append(c.value).append("f");
        return null;
    }

    @Override
    public String visitNameAndType(ConstantPool.CONSTANT_NameAndType_info c, Opcode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String visitMethodref(ConstantPool.CONSTANT_Methodref_info c, Opcode op) {
        try {
            String className = c.getClassName();
            ConstantPool.CONSTANT_NameAndType_info infoNameAndType = c.getNameAndTypeInfo();
            String name = infoNameAndType.getName();
            String desc = infoNameAndType.getType();
            sb2.append(className.replace('/', '.') + "." + name + ":" + desc);
            return null;
        } catch (ConstantPoolException e) {
            return e.getMessage();
        }
    }

    @Override
    public String visitMethodHandle(ConstantPool.CONSTANT_MethodHandle_info c, Opcode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String visitMethodType(ConstantPool.CONSTANT_MethodType_info c, Opcode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String visitString(ConstantPool.CONSTANT_String_info c, Opcode op) {
        try {
            String value = c.getString();
            sb2.append("\"");
            appendString(sb2, value);
            sb2.append("\"");
            return null;
        } catch (ConstantPoolException e) {
            return e.getMessage();
        }
    }

    @Override
    public String visitUtf8(ConstantPool.CONSTANT_Utf8_info cnstnt, Opcode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    class CPPrintM5 implements ConstantPool.Visitor<Void, Void> {

        @Override
        public Void visitClass(ConstantPool.CONSTANT_Class_info c, Void p) {
            try {
                return nl("(class (ref -1) \"" + c.getName() + "\")");
            } catch (ConstantPoolException e) {
                return nl(e.getMessage());
            }
        }

        @Override
        public Void visitDouble(ConstantPool.CONSTANT_Double_info c, Void p) {
            return nl("(double #x"
                    + padLeft(Long.toHexString(Double.doubleToRawLongBits(c.value)), 16)
                    + ") ; " + Double.toHexString(c.value) + " " + c.value);
        }

        @Override
        public Void visitFieldref(ConstantPool.CONSTANT_Fieldref_info c, Void p) {
            try {
                String className = c.getClassName();
                ConstantPool.CONSTANT_NameAndType_info infoNameAndType = c.getNameAndTypeInfo();
                String name = infoNameAndType.getName();
                String desc = infoNameAndType.getType();

                int size = desc.equals("D") || desc.equals("J") ? 2 : 1;
                return nl("(fieldref \"" + className + "\" \"" + name + ":" + desc + "\" " + size + ")");
            } catch (ConstantPoolException e) {
                return nl("\"" + e.getMessage() + "\"");
            }
        }

        @Override
        public Void visitFloat(ConstantPool.CONSTANT_Float_info c, Void p) {
            return nl("(float #x"
                    + padLeft(Integer.toHexString(Float.floatToRawIntBits(c.value)), 8)
                    + ") ; " + Float.toHexString(c.value) + " " + c.value);
        }

        @Override
        public Void visitInteger(ConstantPool.CONSTANT_Integer_info c, Void p) {
            return nl("(integer " + c.value + ")");
        }

        @Override
        public Void visitInterfaceMethodref(ConstantPool.CONSTANT_InterfaceMethodref_info c, Void p) {
            try {
                String className = c.getClassName();
                ConstantPool.CONSTANT_NameAndType_info infoNameAndType = c.getNameAndTypeInfo();
                String name = infoNameAndType.getName();
                String desc = infoNameAndType.getType();

                String[] ss = splitMethodDesc(desc);
                int paramCount = ss.length - 1;
                for (int i = 1; i < ss.length; i++) {
                    if (ss[i].equals("D") || ss[i].equals("J")) {
                        paramCount++;
                    }
                }
                return nl("(interface-methodref \"" + className + "\" \"" + name + ":" + desc + "\" " + paramCount + ")");
            } catch (ConstantPoolException e) {
                return nl("\"" + e.getMessage() + "\"");
            }
        }

        @Override
        public Void visitInvokeDynamic(ConstantPool.CONSTANT_InvokeDynamic_info c, Void p) {
            return nl("(invoke-dynamic)");
        }

        @Override
        public Void visitLong(ConstantPool.CONSTANT_Long_info c, Void p) {
            return nl("(long " + c.value + ")");
        }

        @Override
        public Void visitNameAndType(ConstantPool.CONSTANT_NameAndType_info c, Void p) {
            try {
                String name = c.getName();
                String desc = c.getType();
                return nl("(name-and-type \"" + name + ":" + desc + "\")");
            } catch (ConstantPoolException e) {
                return nl("\"" + e.getMessage() + "\"");
            }
        }

        @Override
        public Void visitMethodref(ConstantPool.CONSTANT_Methodref_info c, Void p) {
            try {
                String className = c.getClassName();
                ConstantPool.CONSTANT_NameAndType_info infoNameAndType = c.getNameAndTypeInfo();
                String name = infoNameAndType.getName();
                String desc = infoNameAndType.getType();

                String[] ss = splitMethodDesc(desc);
                int paramCount = ss.length - 1;
                for (int i = 1; i < ss.length; i++) {
                    if (ss[i].equals("D") || ss[i].equals("J")) {
                        paramCount++;
                    }
                }
                return nl("(methodref \"" + className + "\" \"" + name + ":" + desc + "\" " + paramCount + ")");
            } catch (ConstantPoolException e) {
                return nl("\"" + e.getMessage() + "\"");
            }
        }

        @Override
        public Void visitMethodHandle(ConstantPool.CONSTANT_MethodHandle_info c, Void p) {
            return nl("(method-handle)");
        }

        @Override
        public Void visitMethodType(ConstantPool.CONSTANT_MethodType_info c, Void p) {
            return nl("(method-type)");
        }

        @Override
        public Void visitString(ConstantPool.CONSTANT_String_info c, Void p) {
            try {
                String value = c.getString();
                nl("(string (ref -1) ; \"");
                appendString(sb, value);
                sb.append("\"");
                nl("  ");
                for (int j = 0; j < value.length(); j++) {
                    sb.append(" ").append((int) value.charAt(j));
                }
                sb.append(")");
                return null;
            } catch (ConstantPoolException e) {
                return nl(e.getMessage());
            }
        }

        @Override
        public Void visitUtf8(ConstantPool.CONSTANT_Utf8_info cnstnt, Void p) {
            return nl("(utf8)");
        }
    }

    private static String[] splitMethodDesc(String desc) {
        List<String> ls = new ArrayList<>();
        int ind = desc.indexOf(')');
        assert desc.charAt(0) == '(' && ind > 0;
        ls.add(desc.substring(ind + 1));
        desc = desc.substring(1, ind);
        while (!desc.isEmpty()) {
            int i = 0;
            while (desc.charAt(i) == '[') {
                i++;
            }
            if (desc.charAt(i) == 'L') {
                i = desc.indexOf(';') + 1;
            } else {
                i++;
            }
            ls.add(desc.substring(0, i));
            desc = desc.substring(i);
        }
        return ls.toArray(new String[ls.size()]);
    }

    private static void appendString(StringBuilder sb, String value) {
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
                    sb.append("\\'");
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

    private static String padLeft(String s, int w) {
        while (s.length() < w) {
            s = '0' + s;
        }
        return s;
    }
}
