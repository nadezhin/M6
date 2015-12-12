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
import com.sun.tools.classfile.ConstantValue_attribute;
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
import java.util.Calendar;
import java.util.List;

public class jvm2m5 implements
        Instruction.KindVisitor<StringBuilder, Void>,
        ConstantPool.Visitor<Void, Opcode> {

    private static boolean abstract_mode = false;

    private static String defaultpath = "/var/local/";

    private static String usage
            = "Usage: jvm2m5 [-a] [-g] [-d default-path-prefix] <output directory> <file names>* [<directory names>*]\n";

    final ClassFile cf;
    final ConstantPool cp;
    final List<ConstantPool.CPInfo> constant_pool0 = new ArrayList<>();
    final StringBuilder sb = new StringBuilder();
    final StringBuilder sb2 = new StringBuilder();

    jvm2m5(ClassFile cf) throws ConstantPoolException {
        this.cf = cf;
        this.cp = cf.constant_pool;
        for (Field field : cf.fields) {
            ConstantValue_attribute va
                    = (ConstantValue_attribute) field.attributes.get(Attribute.ConstantValue);
            if (va != null) {
                ConstantPool.CPInfo info = cf.constant_pool.get(va.constantvalue_index);
                if (!constant_pool0.contains(info)) {
                    constant_pool0.add(info);
                }
            }
        }
        for (Method meth : cf.methods) {
            Code_attribute cai = (Code_attribute) meth.attributes.get(Attribute.Code);
            if (cai != null && !abstract_mode) {
                AbstractInstructionVisitor ldcVisitor = new AbstractInstructionVisitor<Void, Void>(null) {
                    @Override
                    public Void visitConstantPoolRef(Instruction instr, int index, Void p) {
                        switch (instr.getOpcode()) {
                            case LDC:
                            case LDC_W:
                            case LDC2_W:
                                try {
                                    ConstantPool.CPInfo info = cf.constant_pool.get(index);
                                    if (!constant_pool0.contains(info)) {
                                        constant_pool0.add(info);
                                    }
                                } catch (ConstantPoolException e) {
                                }
                        }
                        return null;
                    }

                };
                for (Instruction inst : cai.getInstructions()) {
                    inst.accept(ldcVisitor, null);
                }
            }
        }
    }

    int curIndent;

    void indent(int delta) {
        curIndent += delta;
    }

    Void nl(String s) {
        sb.append('\n');
        for (int i = 0; i < curIndent; i++) {
            sb.append(' ');
        }
        sb.append(s);
        return null;
    }

    private static String[] collectFileNames(String[] args) {
        List files = new ArrayList<>();
        String[] reslt = null;
        try {
            for (int i = 0; i < args.length; i++) {
                File cur = new File(args[i]);
                System.out.println(args[i]);
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

            reslt = new String[files.size()];
            for (int i = 0; i < files.size(); i++) {
                reslt[i] = (String) files.get(i);
                System.out.println(reslt[i]);
            }
        } catch (Exception e) {
            System.out.println("File reading error!");
        }
        return reslt;
    }

    public static void main(String[] args) {

        int start = 0;

        System.err.println("JVM --> M5  An automatic M5 state generator.");

        if (args.length <= 1) {
            System.err.println(usage);
            System.exit(0);
        }

        start = 0;

        if (args[start].equals("-a")) {
            abstract_mode = true;
            start++;
            if (args.length - start < 2) {
                System.err.println(usage);
                System.exit(0);
            }
        }

        if (args[start].equals("-d")) {
            start++;
            if (args.length - start < 3) {
                System.err.println(usage);
                System.exit(0);
            }
            defaultpath = args[start];
            start++;

            if (defaultpath.charAt(defaultpath.length() - 1) != '/') {
                defaultpath = defaultpath + "/";
            }
        }

        String tablepath = args[start];
        if (tablepath.lastIndexOf('/') != -1) {
            tablepath = tablepath.substring(0, tablepath.lastIndexOf('/'));
        }
        String[] collectedFiles = collectFileNames(Arrays.copyOfRange(args, start + 1, args.length));
        for (String fn : collectedFiles) {
            try {
                processOneFile(fn, tablepath);
            } catch (java.lang.Exception e) {
                System.err.println("Could not open file " + fn + " " + e);
                System.exit(0);
            }
        }
    }

    private static void processOneFile(String classfn, String pathn) {
        try {
            ClassFile cf = ClassFile.read(Paths.get(classfn));
            String classname = cf.getName().replace('/', '.');
            jvm2m5 printVisitor = new jvm2m5(cf);
            printVisitor.printClass();
            assert printVisitor.curIndent == 0;
            String fn = pathn + "/" + classname + ".lisp";
            System.out.println("Writing state to file " + defaultpath + fn);
            FileWriter outfile = new FileWriter(defaultpath + fn);
            outfile.write("; " + fn + "\n");
            outfile.write("; Automatically generated by jvm2acl2x ");

            Calendar rightNow = Calendar.getInstance();

            outfile.write(/*"on " + rightNow.getTime().toString() +*/".\n;\n\n");

            outfile.write(printVisitor.sb.toString() + "\n");
            outfile.write("\n");
            outfile.close();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
            System.exit(-1);
        }
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
        sb.append("(in-package \"M5\")");
        nl("(include-book \"models/jvm/m5/m5\" :dir :system)\n");
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
                String fname = f.getName(cf.constant_pool);
                String fdesc = f.descriptor.getValue(cf.constant_pool);
                nl("\"" + fname + ":" + fdesc + "\"");
            }
        }
        sb.append(")");
        indent(-2);

        nl(" '(");
        indent(2);
        for (Field f : cf.fields) {
            if (f.access_flags.is(AccessFlags.ACC_STATIC)) {
                String fname = f.getName(cf.constant_pool);
                String fdesc = f.descriptor.getValue(cf.constant_pool);
                nl("\"" + fname + ":" + fdesc + "\"");
            }
        }
        sb.append(")");
        indent(-2);

        nl(" '(");
        indent(2);
        CPPrintM5 cpp = new CPPrintM5();
        for (ConstantPool.CPInfo info : constant_pool0) {
            info.accept(cpp, null);
        }
        indent(-2);
        nl(" )");

        nl(" (list");
        indent(2);
        for (Method m : cf.methods) {
            printMethod(m);
        }
        sb.append(")");
        indent(-2);
        nl(" '(REF -1)))");
        indent(-4);
        nl("");
        nl("");
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

        if (cai != null && !abstract_mode) {
            LineNumberTable_attribute lnt
                    = (LineNumberTable_attribute) cai.attributes.get(Attribute.LineNumberTable);
            int li = 0; // index in linenumber table
            // get the parsed code part
            for (Instruction instr : cai.getInstructions()) {
                if (lnt != null && li < lnt.line_number_table.length && lnt.line_number_table[li].start_pc == instr.getPC()) {
                    nl("; " + "line_number #" + lnt.line_number_table[li].line_number);
                    li++;
                }
                sb2.setLength(0);
                sb2.append("; " + instr.getPC());

                nl("");
                int mark = sb.length();
                sb.append("(");
                instr.accept(this, null);
                sb.append(")");

                // round the line length to multiple of 8.
                int pos = 56;
                while (pos <= sb.length() - mark) {
                    pos += 8;
                }
                int bc = mark + pos - sb.length();
                for (int j = 0; j < bc; j++) {
                    sb.append(" ");
                }
                sb.append(sb2);
            }
        } else {
            if (method.access_flags.is(AccessFlags.ACC_NATIVE)) {
                nl("NIL ; native method");
            }
        }
        indent(-2);
        nl(" )");
    }

    int cpref(ConstantPool.CPInfo c) {
        int ind = constant_pool0.indexOf(c);
        assert ind >= 0;
        return ind;
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
            cp.get(index).accept(this, instr.getOpcode());
            return sb;
        } catch (ConstantPoolException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public StringBuilder visitConstantPoolRefAndValue(Instruction instr, int index, int value, Void p) {
        sb.append(instr.getMnemonic());
        try {
            cp.get(index).accept(this, instr.getOpcode());
            return sb.append(' ').append(value);
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
    public Void visitClass(ConstantPool.CONSTANT_Class_info c, Opcode op) {
        try {
            String name = c.getName();

            switch (op) {
                case LDC:
                case LDC_W:
                case LDC2_W:
                    sb.append(' ').append(cpref(c));
                    sb2.append(";;CLASS:: \"" + name + "\"");
                    break;
                default:
                    sb.append(' ').append("\"" + name + "\"");
                    break;
            }
        } catch (ConstantPoolException e) {
            sb.append(e.getMessage());
        }
        return null;
    }

    @Override
    public Void visitDouble(ConstantPool.CONSTANT_Double_info c, Opcode op) {
        sb.append(' ').append(cpref(c));
        sb2.append(op == Opcode.LDC2_W ? ";; DOUBLE:: \"" : ";;Doulbe:: \"")
                .append(c.value)
                .append("\"");
        return null;
    }

    @Override
    public Void visitFieldref(ConstantPool.CONSTANT_Fieldref_info c, Opcode op) {
        try {
            String className = c.getClassName();
            ConstantPool.CONSTANT_NameAndType_info infoNameAndType = c.getNameAndTypeInfo();
            String name = infoNameAndType.getName();
            String desc = infoNameAndType.getType();

            sb.append(" \"")
                    .append(className)
                    .append("\" \"")
                    .append(name)
                    .append(':')
                    .append(desc)
                    .append("\"");
        } catch (ConstantPoolException e) {
            sb.append(e.getMessage());
        }
        return null;
    }

    @Override
    public Void visitFloat(ConstantPool.CONSTANT_Float_info c, Opcode op) {
        sb.append(' ').append(cpref(c));
        sb2.append(";;FLOAT:: \"")
                .append(c.value)
                .append("\"");
        return null;
    }

    @Override
    public Void visitInteger(ConstantPool.CONSTANT_Integer_info c, Opcode op) {
        sb.append(' ').append(cpref(c));
        sb2.append(";;INT:: \"")
                .append(c.value)
                .append("\"");
        return null;
    }

    @Override
    public Void visitInterfaceMethodref(ConstantPool.CONSTANT_InterfaceMethodref_info c, Opcode op) {
        try {
            String className = c.getClassName();
            ConstantPool.CONSTANT_NameAndType_info infoNameAndType = c.getNameAndTypeInfo();
            String name = infoNameAndType.getName();
            String desc = infoNameAndType.getType();

            sb.append(' ');
            String[] ss = splitMethodDesc(desc);
            int paramCount = ss.length - 1;
            for (int i = 1; i < ss.length; i++) {
                if (ss[i].equals("D") || ss[i].equals("J")) {
                    paramCount++;
                }
            }
            sb.append("\"")
                    .append(className)
                    .append("\" \"")
                    .append(name)
                    .append(':')
                    .append(desc)
                    .append("\" ")
                    .append(paramCount);
        } catch (ConstantPoolException e) {
            sb.append(e.getMessage());
        }
        return null;
    }

    @Override
    public Void visitInvokeDynamic(ConstantPool.CONSTANT_InvokeDynamic_info c, Opcode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Void visitLong(ConstantPool.CONSTANT_Long_info c, Opcode op) {
        sb.append(' ').append(cpref(c));
        sb2.append(op == Opcode.LDC2_W ? ";; LONG:: \"" : ";;Long:: \"")
                .append(c.value)
                .append("\"");
        return null;
    }

    @Override
    public Void visitNameAndType(ConstantPool.CONSTANT_NameAndType_info c, Opcode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Void visitMethodref(ConstantPool.CONSTANT_Methodref_info c, Opcode op) {
        try {
            String className = c.getClassName();
            ConstantPool.CONSTANT_NameAndType_info infoNameAndType = c.getNameAndTypeInfo();
            String name = infoNameAndType.getName();
            String desc = infoNameAndType.getType();

            sb.append(' ');
            String[] ss = splitMethodDesc(desc);
            int paramCount = ss.length - 1;
            for (int i = 1; i < ss.length; i++) {
                if (ss[i].equals("D") || ss[i].equals("J")) {
                    paramCount++;
                }
            }
            sb.append("\"")
                    .append(className)
                    .append("\" \"")
                    .append(name)
                    .append(':')
                    .append(desc)
                    .append("\"")
                    .append(' ')
                    .append(paramCount);
        } catch (ConstantPoolException e) {
            sb.append(e.getMessage());
        }
        return null;
    }

    @Override
    public Void visitMethodHandle(ConstantPool.CONSTANT_MethodHandle_info c, Opcode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Void visitMethodType(ConstantPool.CONSTANT_MethodType_info c, Opcode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Void visitString(ConstantPool.CONSTANT_String_info c, Opcode op) {
        try {
            String value = c.getString();

            sb.append(' ').append(cpref(c));
            sb2.append(";;STRING:: \"");
            appendString(sb2, value);
            sb2.append("\"");
        } catch (ConstantPoolException e) {
            sb.append(e.getMessage());
        }
        return null;
    }

    @Override
    public Void visitUtf8(ConstantPool.CONSTANT_Utf8_info cnstnt, Opcode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    class CPPrintM5 implements ConstantPool.Visitor<Void, Void> {

        @Override
        public Void visitClass(ConstantPool.CONSTANT_Class_info c, Void p) {
            try {
                return nl("(CLASS (REF -1) \"" + c.getName() + "\")");
            } catch (ConstantPoolException e) {
                return nl(e.getMessage());
            }
        }

        @Override
        public Void visitDouble(ConstantPool.CONSTANT_Double_info c, Void p) {
            return nl("(DOUBLE #x"
                    + padLeft(Long.toHexString(Double.doubleToRawLongBits(c.value)), 16)
                    + ") ; " + Double.toHexString(c.value) + " " + c.value);
        }

        @Override
        public Void visitFieldref(ConstantPool.CONSTANT_Fieldref_info c, Void p) {
            return nl("(fieldref)");
        }

        @Override
        public Void visitFloat(ConstantPool.CONSTANT_Float_info c, Void p) {
            return nl("(FLOAT #x"
                    + padLeft(Integer.toHexString(Float.floatToRawIntBits(c.value)), 8)
                    + ") ; " + Float.toHexString(c.value) + " " + c.value);
        }

        @Override
        public Void visitInteger(ConstantPool.CONSTANT_Integer_info c, Void p) {
            return nl("(INT " + c.value + ")");
        }

        @Override
        public Void visitInterfaceMethodref(ConstantPool.CONSTANT_InterfaceMethodref_info c, Void p) {
            return nl("(interface-methodref)");
        }

        @Override
        public Void visitInvokeDynamic(ConstantPool.CONSTANT_InvokeDynamic_info c, Void p) {
            return nl("(invoke-dynamic)");
        }

        @Override
        public Void visitLong(ConstantPool.CONSTANT_Long_info c, Void p) {
            return nl("(LONG " + c.value + ")");
        }

        @Override
        public Void visitNameAndType(ConstantPool.CONSTANT_NameAndType_info c, Void p) {
            return nl("(name-and-type)");
        }

        @Override
        public Void visitMethodref(ConstantPool.CONSTANT_Methodref_info c, Void p) {
            return nl("(methodref)");
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
                nl("(STRING (REF -1) ; \"");
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
