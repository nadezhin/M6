/* M6Method.java:  a Java representation of an ACL2 method
 *
 * $Id: M6Method.java,v 1.12 2003/06/17 22:11:16 hbl Exp hbl $
 */

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Attributes;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Descriptor;
import com.sun.tools.classfile.DescriptorException;
import com.sun.tools.classfile.Instruction;
import com.sun.tools.classfile.LineNumberTable_attribute;
import com.sun.tools.classfile.LocalVariableTable_attribute;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.Opcode;
import com.sun.tools.classfile.StackMap_attribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * M6Method represents a Java method in a way that allows for easy conversion to
 * a format that ACL2 (and more specifically M6) can understand. At the heart of
 * an M6Method is the following state information:
 * <ul>
 * <li>The name of the Method.
 * <li>A list of the parameters to the method
 * <li>A flag indicating if the method is <code>synchronized</code>
 * <li>A <code>Vector</code> of M6-formatted instructions
 * </ul>
 * <p>
 * Methods are also provided to print out the method as a String, in an M6
 * format.
 *
 * @author George M. Porter
 * @author J Strother Moore
 * @version 1.0
 */
public class M6Method {

    private final Method m;
    private final ClassFile cf;

    private final String name;
    private final String desc;
    private final List<String> params = new ArrayList<>();
    private String returntype;
    private final M6AccessFlags accessflags;

    private final Code_attribute cai;
    private final int max_stack; // assuming there is only one code attribute for each method.
    private final int max_locals;
    private final int code_length;
    private M6ExceptionHandler[] handlers;
    private M6StackMapAttrInfo stackmaps;
    private final LineNumberTable_attribute lnt;
    private final LocalVariableTable_attribute lvt;

    private List<String> m6instructions = new ArrayList<>();

    private boolean debug;
    private boolean processed;

    private Hashtable tagTable;

    private static boolean no_method_body = false;
    private static boolean keep_debug_info = false;

    private String lntStr;
    private String lvtStr;

    /**
     * This constructor takes a ClassFile and the MethodInfo structure
     * representing the method and produces an M6Method object.
     *
     * @param c	The ClassFile representing the class
     * @param m	The MethodInfo structure representing the method to analyze.
     */
    public M6Method(ClassFile c, Method m)
            throws ConstantPoolException {
        cf = c;
        this.m = m;
        name = m.getName(c.constant_pool);
        desc = m.descriptor.getValue(c.constant_pool);
        accessflags = new M6AccessFlags(m.access_flags);
        cai = (Code_attribute) m.attributes.get(Attribute.Code);
        if (cai != null) {
            max_stack = cai.max_stack;
            max_locals = cai.max_locals;
            code_length = cai.code_length;
            lnt = (LineNumberTable_attribute) cai.attributes.get(Attribute.LineNumberTable);
            lvt = (LocalVariableTable_attribute) cai.attributes.get(Attribute.LocalVariableTable);
        } else {
            max_stack = max_locals = code_length = 0;
            lnt = null;
            lvt = null;
        }

        handlers = null;
        stackmaps = null;

        tagTable = new Hashtable();
        debug = true;
        processed = false;
        lntStr = null;
        lvtStr = null;
    }

    public String getLntStr() {
        return lntStr;
    }

    public String getLvtStr() {
        return lvtStr;
    }

    private static String replaceAllandApp(String src, String o, String n) {
        StringBuffer newstr = new StringBuffer();

        int offset = 0;
        int max = 0;
        int start;

        max = src.indexOf(";;");
        if (max == -1) {
            max = src.length();
        }

        boolean found = false;
        StringTokenizer tok = new StringTokenizer(src.substring(0, max));
        while (tok.hasMoreTokens()) {
            String cur = tok.nextToken().trim();

            int y = cur.lastIndexOf("(");
            String cur1 = cur;
            if (y != -1) {
                cur1 = cur.substring(y + 1);
            }

            int x = cur1.indexOf(")");
            String cur2 = cur1;
            if (x != -1) {
                cur2 = cur1.substring(0, x);
            }

            if (cur2.equals(o)) {
                found = true;
                if (y != -1) {
                    newstr.append(cur.substring(0, y + 1));
                }
                newstr.append(n);
                if (x != -1) {
                    newstr.append(cur1.substring(x));
                }
            } else {
                newstr.append(cur);
            }
            newstr.append(" ");
        }
        // System.out.println(newstr);
        if (found) {
            newstr.append(" ;;to " + o);
        }
        newstr.append(src.substring(max));
        return newstr.toString();
    }

    private void resolveTagInCode() {
        // only call this after m6instructions is collected.
        List<String> newinstr = new ArrayList<>();
        List<String> instrs = m6instructions;

        for (int i = 0; i < instrs.size(); i++) {
            String inst = (String) instrs.get(i);
            Enumeration tags = tagTable.keys();
            for (; tags.hasMoreElements();) {
                Object key = tags.nextElement();
                inst = replaceAllandApp(inst, key.toString(), tagTable.get(key.toString()).toString());
            }
            newinstr.add(inst);
        }
        m6instructions = newinstr;
    }

    public String printLineNumberTable() throws ConstantPoolException {
        StringBuffer buf = new StringBuffer();
        buf.append("((\"" + cf.getName() + "\" \"" + name + "\"");
        buf.append("\n  (");
        for (int i = 0; i < params.size(); i++) {
            buf.append(params.get(i));
            if (i < (params.size() - 1)) {
                buf.append(" ");
            }
        }
        buf.append(")\n");
        buf.append("      (returntype . " + returntype + "))\n");
        buf.append("(");
        if (lnt != null) {
            int len = lnt.line_number_table_length;
            for (int i = 0; i < len; i++) {
                buf.append("(" + lnt.line_number_table[i].start_pc + " . " + lnt.line_number_table[i].line_number + ")\n");
            }
        }
        buf.append(")");
        buf.append(")");
        return buf.toString();
    }

    public String printLocalVariableTable() throws ConstantPoolException {
        StringBuffer buf = new StringBuffer();
        buf.append("((\"" + cf.getName() + "\" \"" + name + "\"");
        buf.append("\n  (");

        for (int i = 0; i < params.size(); i++) {
            buf.append(params.get(i));
            if (i < (params.size() - 1)) {
                buf.append(" ");
            }
        }
        buf.append(")\n");
        buf.append("      (returntype . " + returntype + "))\n");
        buf.append("(");
        if (lvt != null) {
            int len = lvt.local_variable_table_length;
            for (int i = 0; i < len; i++) {
                printLocalVariableEntry(buf, i);
            }
        }
        buf.append(")");
        buf.append(")");
        return buf.toString();
    }

    private StringBuffer printLocalVariableEntry(StringBuffer buf, int i) throws ConstantPoolException {
        if (lvt != null) {
            int len = lvt.local_variable_table_length;
            if (i >= 0 && i < len) {
                LocalVariableTable_attribute.Entry entry = lvt.local_variable_table[i];
                int start_pc = entry.start_pc;
                int length = entry.length;
                int name_index = entry.name_index;
                int descriptor_index = entry.descriptor_index;
                String name = cf.constant_pool.getUTF8Value(name_index);
                String desc = cf.constant_pool.getUTF8Value(descriptor_index);
                buf.append("(" + start_pc
                        + " " + (start_pc + length)
                        + " " + name
                        + " " + ACL2utils.ClassInfoToACL2TypeStr(desc) + ")\n");
            }
        }
        return buf;
    }

    /**
     * returns a pretty-printed String representing this method.
     *
     * @param lmargin	The number of spaces of indentation to place before the
     * output
     * @param target Target JVM model
     * @return	A pretty-printed String representing this method.
     */
    public String toString(int lmargin, Target target) {
        if (!processed) {
            return "You must call processMethod first!";
        }

        StringBuffer buf = new StringBuffer();
        StringBuffer padb = new StringBuffer();
        for (int i = 0; i < lmargin; i++) {
            padb.append(" ");
        }

        String pad = padb.toString();
        switch (target) {
            case M5:
                if (ACL2utils.NAME_AND_TYPE) {
                    buf
                            .append(pad)
                            .append("'(\"")
                            .append(name)
                            .append(":")
                            .append(desc)
                            .append("\"");
                } else {
                    buf.append(pad + "'(\"" + name + "\" (");
                    for (int i = 0; i < params.size(); i++) {
                        buf.append(params.get(i));

                        if (i < (params.size() - 1)) {
                            buf.append(" ");
                        }
                    }
                    buf.append(')');
                }
                buf.append(' ');
                buf.append((m.access_flags.is(AccessFlags.ACC_SYNCHRONIZED) ? "t" : "nil") + "\n");
                break;
            case M6:
                buf.append(pad + "(method \"" + name + "\"\n");
                buf.append(pad + "      (parameters ");
                for (int i = 0; i < params.size(); i++) {
                    buf.append(params.get(i));

                    if (i < (params.size() - 1)) {
                        buf.append(" ");
                    }
                }
                buf.append(")\n");
                buf.append(pad + "      (returntype . " + returntype + ")\n");

                buf.append(accessflags.toString(lmargin + 6) + "\n");
                buf.append(pad + "      (code");
                if (cai != null && !no_method_body) {
                    buf.append("\n" + pad + "           (max_stack . " + max_stack + ")"
                            + " (max_locals . " + max_locals + ")"
                            + " (code_length . " + code_length + ")\n");
                    buf.append(pad + "           (parsedcode\n");
                }
                break;
        }

        for (int i = 0; i < m6instructions.size(); i++) {
            StringBuffer line = new StringBuffer(pad);
            switch (target) {
                case M5:
                    line.append("  ");
                    break;
                case M6:
                    line.append("              ");
                    break;
            }
            line.append(m6instructions.get(i));

            // round the line length to multiple of 20.
            /*
             if (i < m6instructions.size() - 1) {
             int bc = 20 - (line.length()) % 20;
             for (int j = 0; j < bc; j++) {
             line.append(" ");
             }
             };

             if (i == (m6instructions.size() - 1)) {
             line.append(")");
             int bc = 20 - (line.length()) % 20;
             for (int j = 0; j < bc; j++) {
             line.append(" ");
             }
             line.append(";; " + i);
             } else {
             line.append(";; " + i);
             line.append("\n");
             }
             */
            buf.append(line);
            switch (target) {
                case M5:
                    buf.append("\n");
                    break;
                case M6:
                    if (i < m6instructions.size() - 1) {
                        buf.append("\n");
                    }
                    break;
            }
        }
        switch (target) {
            case M5:
                buf.append(pad + " )");
                break;
            case M6:
                buf.append(")");
                if (cai != null && !no_method_body) {
                    buf.append("\n" + pad + "           (Exceptions ");
                    if (handlers != null) {
                        for (int i = 0; i < handlers.length; i++) {
                            buf.append("\n" + pad + "             " + handlers[i].toString());
                        };
                    };
                    buf.append(")\n");

                    if (stackmaps != null) {
                        buf.append(stackmaps.toString(lmargin + 12));
                    } else {
                        buf.append(pad + "           (StackMap )");
                    }
                    buf.append(")");
                }
                buf.append(")");
                break;
        }
        return buf.toString();
    }

    public static void setAbstractMode() {
        no_method_body = true;
    }

    public static void setKeepLntLvt() {
        keep_debug_info = true;
    }

    static class LabelsVisitor implements Instruction.KindVisitor<List<Integer>, Void> {

        @Override
        public List<Integer> visitNoOperands(Instruction instr, Void p) {
            return Collections.emptyList();
        }

        @Override
        public List<Integer> visitArrayType(Instruction instr, Instruction.TypeKind kind, Void p) {
            return Collections.emptyList();
        }

        @Override
        public List<Integer> visitBranch(Instruction instr, int offset, Void p) {
            return Arrays.asList(offset);
        }

        @Override
        public List<Integer> visitConstantPoolRef(Instruction instr, int index, Void p) {
            return Collections.emptyList();
        }

        @Override
        public List<Integer> visitConstantPoolRefAndValue(Instruction instr, int index, int value, Void p) {
            return Collections.emptyList();
        }

        @Override
        public List<Integer> visitLocal(Instruction instr, int index, Void p) {
            return Collections.emptyList();
        }

        @Override
        public List<Integer> visitLocalAndValue(Instruction instr, int index, int value, Void p) {
            return Collections.emptyList();
        }

        @Override
        public List<Integer> visitLookupSwitch(Instruction instr, int default_, int npairs, int[] matches, int[] offsets, Void p) {
            ArrayList<Integer> result = new ArrayList<>();
            result.add(default_);
            for (int j : offsets) {
                result.add(j);
            }
            return result;
        }

        @Override
        public List<Integer> visitTableSwitch(Instruction instr, int default_, int low, int high, int[] offsets, Void p) {
            ArrayList<Integer> result = new ArrayList<>();
            result.add(default_);
            for (int j : offsets) {
                result.add(j);
            }
            return result;
        }

        @Override
        public List<Integer> visitValue(Instruction instr, int value, Void p) {
            return Collections.emptyList();
        }

        @Override
        public List<Integer> visitUnknown(Instruction instr, Void p) {
            return Collections.emptyList();
        }
    }

    /**
     * This method processes the MethodInfo given in the constructor. It is
     * necessary to call this method before querying this method.
     *
     * @param constant_pool	The constant pool element from the M6Class object
     * representing the outer class from which this method is taken from.
     * @param target Target JVM model
     */
    public void processMethod(List constant_pool, Target target) throws ConstantPoolException, DescriptorException {
        /* The name of the method */
        if (debug) {
            System.out.println("\nProcessing method: " + name);
        }

        /* We parse in the parameters into a Vector insert "top" if necessary. if necessary. */
        int paramCount = m.descriptor.getParameterCount(cf.constant_pool);
        String paramTypes = m.descriptor.getParameterTypes(cf.constant_pool);
        if (paramCount > 0) {
            assert paramTypes.charAt(0) == '(' && paramTypes.charAt(paramTypes.length() - 1) == ')';
            String[] paramStrs = paramTypes.substring(1, paramTypes.length() - 1).split(",");
            assert paramStrs.length == paramCount;
            for (int i = 0; i < paramCount; i++) {
                params.add(ACL2utils.JavaTypeStrToACL2TypeStr(paramStrs[i].trim()));
            }
        } else {
            assert paramTypes.equals("()");
        }
//        String[] ps = m0.getParams();
//        for (int i = 0; i < ps.length; i++) {
//            String curtype = ps[i];
//            params.add(ACL2utils.JavaTypeStrToACL2TypeStr(curtype));
//        }
        if (debug) {
            System.out.println("Parameters: " + params);
        }
        /* store the return type */
        returntype = ACL2utils.JavaTypeStrToACL2TypeStr(m.descriptor.getReturnType(cf.constant_pool)); // hanbing

        /* Process the code attribute */
        if (cai == null || no_method_body) {
            switch (target) {
                case M5:
                    if (m.access_flags.is(AccessFlags.ACC_NATIVE)) {
                        m6instructions.add("NIL ; native method");
                    }
                    break;
                case M6:
                    break;
            }
        } else {
            LinkedHashMap<Integer, Integer> labels = new LinkedHashMap<>();
            LabelsVisitor labelsVisitor = new LabelsVisitor();
            for (Instruction inst : cai.getInstructions()) {
                for (int offset : inst.accept(labelsVisitor, null)) {
                    int label = inst.getPC() + offset;
                    if (!labels.containsKey(label)) {
                        labels.put(label, labels.size());
                    }
                }
            }
            for (Code_attribute.Exception_data e : cai.exception_table) {
                if (!labels.containsKey(e.start_pc)) {
                    labels.put(e.start_pc, labels.size());
                }
                if (!labels.containsKey(e.end_pc)) {
                    labels.put(e.end_pc, labels.size());
                }
                if (!labels.containsKey(e.handler_pc)) {
                    labels.put(e.handler_pc, labels.size());
                }
            }

            int offset = 0;
            int li = 0; // index in linenumber table
            // get the parsed code part
            for (Instruction instr : cai.getInstructions()) {
                if (lnt != null && li < lnt.line_number_table.length && lnt.line_number_table[li].start_pc == instr.getPC()) {
                    m6instructions.add("; " + "line_number #" + lnt.line_number_table[li].line_number);
                    li++;
                }
                String resultStr = parseInst(instr, constant_pool, offset, labels, target);
                if (resultStr != null) {
                    m6instructions.add(resultStr);
                    offset = offset + instr.length();
                }
            }

            switch (target) {
                case M5:
                    break;
                case M6:
                    // resolve any tag to absolute offsets.
                    resolveTagInCode();
                    break;
            }

            // add an end marker to the code.
            switch (target) {
                case M5:
                    break;
                case M6:
                    m6instructions.add("(endofcode " + code_length + ")");
                    break;
            }

            // get handlers correctly.
            int ecount = cai.exception_table_length;

            if (ecount > 0) {
                handlers = new M6ExceptionHandler[ecount];
                for (int i = 0; i < ecount; i++) {
                    M6ExceptionHandler h = new M6ExceptionHandler(cai.exception_table[i], cf.constant_pool);
                    handlers[i] = h;
                }
            }

            // get StackMap Attributes
            Attributes aal = cai.attributes;
            StackMap_attribute sm = (StackMap_attribute) aal.get(Attribute.StackMap);

            if (sm != null) {
                M6StackMapAttrInfo attr = new M6StackMapAttrInfo(sm, cf.constant_pool, max_locals);
                stackmaps = attr;
            } else {
                stackmaps = null;
            }

            // get LineNumberAttrInfo 
        }
        processed = true;

        if (M6Method.keep_debug_info) {
            lntStr = printLineNumberTable();
            lvtStr = printLocalVariableTable();
        }
    }

    abstract class ParseVisitor implements Instruction.KindVisitor<String, Void> {

        private final Target target;
        private final List constant_pool;
        final Map<Integer, Integer> labels;
        private final StringBuilder tmp2;

        ParseVisitor(Target target, List constant_pool, Map<Integer, Integer> labels, StringBuilder tmp2) {
            this.target = target;
            this.constant_pool = constant_pool;
            this.labels = labels;
            this.tmp2 = tmp2;
        }

        abstract String label(int pc, int offset);

        abstract String makeClassCP(ConstantPool.CONSTANT_Class_info infoClass) throws ConstantPoolException;

        abstract String makeFieldCP(ConstantPool.CONSTANT_Fieldref_info infoFieldref) throws ConstantPoolException;

        abstract String makeMethodCP(ConstantPool.CONSTANT_Class_info infoClass,
                ConstantPool.CONSTANT_NameAndType_info infoNameAndType)
                throws ConstantPoolException, DescriptorException;

        @Override
        public String visitNoOperands(Instruction instr, Void p) {
            StringBuilder sb = new StringBuilder();
            return sb.append(instr.getMnemonic())
                    .toString();
        }

        @Override
        public String visitArrayType(Instruction instr, Instruction.TypeKind tk, Void p) {
            StringBuilder sb = new StringBuilder();
            return sb.append(instr.getMnemonic())
                    .append(' ')
                    .append(tk.name.toUpperCase())
                    .toString();
        }

        @Override
        public String visitBranch(Instruction instr, int offset, Void p) {
            StringBuilder sb = new StringBuilder();
            return sb.append(instr.getMnemonic())
                    .append(' ')
                    .append(label(instr.getPC(), offset))
                    .toString();
        }

        @Override
        public String visitConstantPoolRef(Instruction instr, int index, Void p) {
            StringBuilder sb = new StringBuilder();
            sb.append(instr.getMnemonic()).append(' ');
            try {
                ConstantPool.CONSTANT_Class_info infoClass;
                ConstantPool.CONSTANT_NameAndType_info infoNameAndType;
                ConstantPool.CPInfo info = cf.constant_pool.get(index);
                switch (info.getTag()) {
                    case ConstantPool.CONSTANT_Integer:
                        Integer valInteger = new Integer(((ConstantPool.CONSTANT_Integer_info) info).value);
                        if (constant_pool.contains(valInteger)) {
                            sb.append(constant_pool.indexOf(valInteger));
                        } else {
                            constant_pool.add(valInteger);
                            sb.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(";;INT:: \"" + valInteger + "\"");
                        break;
                    case ConstantPool.CONSTANT_Float:
                        Float valFloat = new Float(((ConstantPool.CONSTANT_Float_info) info).value);
                        if (constant_pool.contains(valFloat)) {
                            sb.append(constant_pool.indexOf(valFloat));
                        } else {
                            constant_pool.add(valFloat);
                            sb.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(";;FLOAT:: \"" + valFloat + "\"");
                        break;
                    case ConstantPool.CONSTANT_Long:
                        Long valLong = new Long(((ConstantPool.CONSTANT_Long_info) info).value);
                        if (constant_pool.contains(valLong)) {
                            sb.append(constant_pool.indexOf(valLong));
                        } else {
                            constant_pool.add(valLong);
                            sb.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(instr.getOpcode() == Opcode.LDC2_W ? ";; LONG:: \"" : ";;Long:: \"")
                                .append(valLong)
                                .append("\"");
                        break;
                    case ConstantPool.CONSTANT_Double:
                        Double valDouble = new Double(((ConstantPool.CONSTANT_Double_info) info).value);
                        if (constant_pool.contains(valDouble)) {
                            sb.append(constant_pool.indexOf(valDouble));
                        } else {
                            constant_pool.add(valDouble);
                            sb.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(instr.getOpcode() == Opcode.LDC2_W ? ";; DOUBLE:: \"" : ";;Doulbe:: \"")
                                .append(valDouble)
                                .append("\"");
                        break;
                    case ConstantPool.CONSTANT_Class:
                        infoClass = (ConstantPool.CONSTANT_Class_info) info;
                        switch (instr.getOpcode()) {
                            case LDC:
                            case LDC_W:
                            case LDC2_W:
                                M6Class.ClassRef valClass = new M6Class.ClassRef(infoClass.getName());
                                if (constant_pool.contains(valClass)) {
                                    sb.append(constant_pool.indexOf(valClass));
                                } else {
                                    constant_pool.add(valClass);
                                    sb.append(constant_pool.size() - 1);  // zero-based indices
                                }
                                switch (target) {
                                    case M5:
                                        tmp2.append(";;CLASS:: \"" + valClass + "\"");
                                        break;
                                    case M6:
                                        tmp2.append(";;CLASS:: \"" + valClass.className.replace('/', '.') + "\"");
                                        break;
                                }
                                break;
                            default:
                                sb.append(makeClassCP(infoClass));
                        }
                        break;
                    case ConstantPool.CONSTANT_String:
                        M6Class.StringRef valString = new M6Class.StringRef(((ConstantPool.CONSTANT_String_info) info).getString());
                        if (constant_pool.contains(valString)) {
                            sb.append(constant_pool.indexOf(valString));
                        } else {
                            constant_pool.add(valString);
                            sb.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(";;STRING:: \"" + valString.toString(target == Target.M6) + "\"");
                        break;
                    case ConstantPool.CONSTANT_Fieldref:
                        ConstantPool.CONSTANT_Fieldref_info infoFieldref
                                = (ConstantPool.CONSTANT_Fieldref_info) info;
                        sb.append(makeFieldCP(infoFieldref));
                        break;
                    case ConstantPool.CONSTANT_Methodref:
                    case ConstantPool.CONSTANT_InterfaceMethodref:
                        if (info.getTag() == ConstantPool.CONSTANT_InterfaceMethodref) {
                            ConstantPool.CONSTANT_InterfaceMethodref_info infoInterfaceMethodref
                                    = (ConstantPool.CONSTANT_InterfaceMethodref_info) info;
                            infoClass = cf.constant_pool.getClassInfo(infoInterfaceMethodref.class_index);
                            infoNameAndType = cf.constant_pool.getNameAndTypeInfo(infoInterfaceMethodref.name_and_type_index);

                        } else {
                            ConstantPool.CONSTANT_Methodref_info infoMethodref
                                    = (ConstantPool.CONSTANT_Methodref_info) info;
                            infoClass = cf.constant_pool.getClassInfo(infoMethodref.class_index);
                            infoNameAndType = cf.constant_pool.getNameAndTypeInfo(infoMethodref.name_and_type_index);
                        }
                        switch (target) {
                            case M5:
                                sb.append(makeMethodCP(infoClass, infoNameAndType));
                                break;
                            case M6:
                                sb.setLength(sb.length() - 1);
                                sb.append("\n\t\t\t\t\t")
                                        .append(makeMethodCP(infoClass, infoNameAndType));
                                break;
                        }
                        break;
                    default:
                        throw new AssertionError();
                }
            } catch (ConstantPoolException | DescriptorException e) {
                sb.append(e.getMessage());
            }
            return sb.toString();
        }

        @Override
        public String visitConstantPoolRefAndValue(Instruction instr, int index, int value, Void p) {
            StringBuilder sb = new StringBuilder();
            sb.append(instr.getMnemonic())
                    .append(' ');
            try {
                ConstantPool.CONSTANT_Class_info infoClass;
                ConstantPool.CPInfo info = cf.constant_pool.get(index);
                switch (info.getTag()) {
                    case ConstantPool.CONSTANT_Class:
                        infoClass = cf.constant_pool.getClassInfo(index);
                        sb.append(makeClassCP((ConstantPool.CONSTANT_Class_info) info))
                                .append(' ')
                                .append(value);
                        break;
                    case ConstantPool.CONSTANT_InterfaceMethodref:
                        ConstantPool.CONSTANT_InterfaceMethodref_info infoInterfaceMethodref
                                = (ConstantPool.CONSTANT_InterfaceMethodref_info) info;
                        infoClass = cf.constant_pool.getClassInfo(infoInterfaceMethodref.class_index);
                        ConstantPool.CONSTANT_NameAndType_info infoNameAndType
                                = cf.constant_pool.getNameAndTypeInfo(infoInterfaceMethodref.name_and_type_index);
                        switch (target) {
                            case M5:
                                sb.append(makeMethodCP(infoClass, infoNameAndType))
                                        .append(' ')
                                        .append(value);
                                break;
                            case M6:
                                sb.setLength(sb.length() - 1);
                                sb.append("\n\t\t\t\t\t")
                                        .append(makeMethodCP(infoClass, infoNameAndType))
                                        .append(' ')
                                        .append(value);
                                break;
                        }
                        break;
                }
            } catch (ConstantPoolException | DescriptorException e) {
                sb.append(e.getMessage());
            }
            return sb.toString();
        }

        @Override
        public String visitLocal(Instruction instr, int index, Void p) {
            StringBuilder sb = new StringBuilder();
            return sb.append(instr.getMnemonic()/*.replace("_w", "")*/)
                    .append(' ')
                    .append(index)
                    .toString();
        }

        @Override
        public String visitLocalAndValue(Instruction instr, int index, int value, Void p) {
            StringBuilder sb = new StringBuilder();
            return sb.append(instr.getMnemonic().replace("_w", ""))
                    .append(' ')
                    .append(index)
                    .append(' ')
                    .append(value)
                    .toString();
        }

        @Override
        public String visitLookupSwitch(Instruction instr, int default_, int npairs, int[] matches, int[] offsets, Void p) {
            StringBuilder sb = new StringBuilder();
            sb.append(instr.getMnemonic()) // the opcode
                    .append(" (lookupswitchinfo ")
                    .append(label(instr.getPC(), default_)) // the default target
                    .append(' ')
                    .append(npairs) // the pair count
                    .append(" (");
            for (int i = 0; i < npairs; i++) {
                sb.append('(')
                        .append(matches[i])
                        .append(" . ")
                        .append(label(instr.getPC(), offsets[i]))
                        .append(") ");
            }
            sb.setCharAt(sb.length() - 1, ')');
            return sb.append(")")
                    .toString();
        }

        @Override
        public String visitTableSwitch(Instruction instr, int default_, int low, int high, int[] offsets, Void p) {
            StringBuilder sb = new StringBuilder();
            sb.append(instr.getMnemonic()) // the opcode
                    .append(" (tableswitchinfo ")
                    .append(label(instr.getPC(), default_)) // the default target
                    .append(" (")
                    .append(low)
                    .append(" . ")
                    .append(high)
                    .append(") (");
            for (int i = 0; i < high - low + 1; i++) {
                sb.append(label(instr.getPC(), offsets[i]))
                        .append(' ');
            }
            sb.setCharAt(sb.length() - 1, ')');
            return sb.append(")")
                    .toString();
        }

        @Override
        public String visitValue(Instruction instr, int value, Void p) {
            StringBuilder sb = new StringBuilder();
            return sb.append(instr.getMnemonic())
                    .append(' ')
                    .append(value)
                    .toString();
        }

        @Override
        public String visitUnknown(Instruction instr, Void p) {
            return "bytecode " + instr.getOpcode();
        }
    }

    class ParseVisitorM5 extends ParseVisitor {

        ParseVisitorM5(List constant_pool, Map<Integer, Integer> labels, StringBuilder tmp2) {
            super(Target.M5, constant_pool, labels, tmp2);
        }

        @Override
        String label(int pc, int offset) {
            return Integer.toString(offset);
        }

        @Override
        String makeClassCP(ConstantPool.CONSTANT_Class_info infoClass)
                throws ConstantPoolException {
            return "\"" + infoClass.getName().replace('/', '.') + "\"";
        }

        @Override
        String makeFieldCP(ConstantPool.CONSTANT_Fieldref_info infoFieldref)
                throws ConstantPoolException {
            StringBuilder sb = new StringBuilder();
            ConstantPool.CONSTANT_Class_info infoClass
                    = cf.constant_pool.getClassInfo(infoFieldref.class_index);
            ConstantPool.CONSTANT_NameAndType_info infoNameAndType
                    = cf.constant_pool.getNameAndTypeInfo(infoFieldref.name_and_type_index);
            if (ACL2utils.NAME_AND_TYPE) {
                sb.append("\"")
                        .append(infoClass.getName())
                        .append("\" \"")
                        .append(infoNameAndType.getName())
                        .append(':')
                        .append(infoNameAndType.getType())
                        .append("\"");
            } else {
                sb.append("\"")
                        .append(infoClass.getName().replace('/', '.'))
                        .append("\" \"")
                        .append(infoNameAndType.getName())
                        .append("\"");
                switch (infoNameAndType.getType()) {
                    case "J":
                    case "D":
                        sb.append(" t");
                        break;
                }
            }
            return sb.toString();
        }

        @Override
        String makeMethodCP(ConstantPool.CONSTANT_Class_info infoClass,
                ConstantPool.CONSTANT_NameAndType_info infoNameAndType)
                throws ConstantPoolException, DescriptorException {
            StringBuilder sb = new StringBuilder();

            if (ACL2utils.NAME_AND_TYPE) {
                sb.append("\"")
                        .append(infoClass.getName())
                        .append("\" \"")
                        .append(infoNameAndType.getName())
                        .append(':')
                        .append(infoNameAndType.getType());
            } else {
                sb.append("\"")
                        .append(infoClass.getName().replace('/', '.'))
                        .append("\" \"")
                        .append(infoNameAndType.getName());

            }
            int paramCount = new Descriptor(infoNameAndType.type_index)
                    .getParameterCount(cf.constant_pool);
            return sb.append("\" ")
                    .append(paramCount)
                    .toString();
        }
    }

    class ParseVisitorM6 extends ParseVisitor {

        ParseVisitorM6(List constant_pool, Map<Integer, Integer> labels, StringBuilder tmp2) {
            super(Target.M6, constant_pool, labels, tmp2);
        }

        @Override
        String label(int pc, int offset) {
            return "TAG_" + labels.get(pc + offset);
        }

        @Override
        String makeClassCP(ConstantPool.CONSTANT_Class_info infoClass)
                throws ConstantPoolException {
            String className = infoClass.getName();
            if (className.startsWith("[")) {
                return "(array " + makeTypeCP(className.substring(1)) + ")";
            } else {
                return "(class \"" + className.replace('/', '.') + "\")";
            }
        }

        private String makeTypeCP(String type)
                throws ConstantPoolException {
            type = type.replace('/', '.');
            int c = 0;
            if (type.startsWith("[")) {
                c = type.lastIndexOf("[") + 1;
                assert c > 0;
                type = type.substring(c);
            }
            switch (type) {
                case "V":
                    type = "void";
                    break;
                case "Z":
                    type = "boolean";
                    break;
                case "B":
                    type = "byte";
                    break;
                case "C":
                    type = "char";
                    break;
                case "S":
                    type = "short";
                    break;
                case "I":
                    type = "int";
                    break;
                case "J":
                    type = "long";
                    break;
                case "F":
                    type = "float";
                    break;
                case "D":
                    type = "double";
                    break;
                default:
                    assert type.startsWith("L") && type.endsWith(";");
                    type = type.substring(1, type.length() - 1);
                    type = "(class \"" + type + "\")";
            }
            while (c > 0) {
                type = "(array " + type + ")";
                c--;
            }
            return type;
        }

        private String classInfo2(ConstantPool.CONSTANT_Class_info infoClass)
                throws ConstantPoolException {
            String name = infoClass.getName().replace('/', '.');
            if (name.startsWith("[")) {
                int c = name.lastIndexOf("[") + 1;
                assert c > 0;
                name = name.substring(c);
                switch (name) {
                    case "Z":
                        name = "boolean";
                        break;
                    case "B":
                        name = "byte";
                        break;
                    case "C":
                        name = "char";
                        break;
                    case "S":
                        name = "short";
                        break;
                    case "I":
                        name = "int";
                        break;
                    case "J":
                        name = "long";
                        break;
                    case "F":
                        name = "float";
                        break;
                    case "D":
                        name = "double";
                        break;
                    default:
                        assert name.startsWith("L") && name.endsWith(";");
                        name = name.substring(1, name.length() - 1);
                }
                while (c > 0) {
                    name += "[]";
                    c--;
                }
            }
            return "\"" + name + "\"";
        }

        @Override
        String makeFieldCP(ConstantPool.CONSTANT_Fieldref_info infoFieldref)
                throws ConstantPoolException {
            StringBuilder sb = new StringBuilder();
            ConstantPool.CONSTANT_Class_info infoClass
                    = cf.constant_pool.getClassInfo(infoFieldref.class_index);
            ConstantPool.CONSTANT_NameAndType_info infoNameAndType
                    = cf.constant_pool.getNameAndTypeInfo(infoFieldref.name_and_type_index);
            return sb.append("(fieldCP \"")
                    .append(infoNameAndType.getName())
                    .append("\" \"")
                    .append(infoClass.getName().replace('/', '.'))
                    .append("\" ")
                    .append(makeTypeCP(cf.constant_pool.getUTF8Value(infoNameAndType.type_index)))
                    .append(')')
                    .toString();
        }

        @Override
        String makeMethodCP(ConstantPool.CONSTANT_Class_info infoClass,
                ConstantPool.CONSTANT_NameAndType_info infoNameAndType)
                throws ConstantPoolException {
            StringBuilder sb = new StringBuilder();
            sb.append("(methodCP \"")
                    .append(infoNameAndType.getName())
                    .append("\" ")
                    .append(classInfo2(infoClass))
                    .append(" (");
            String type = cf.constant_pool.getUTF8Value(infoNameAndType.type_index);
            int ind = type.indexOf(')');
            assert type.startsWith("(") && ind > 0;
            String params = type.substring(1, ind);
            while (!params.isEmpty()) {
                int i = 0;
                while (params.charAt(i) == '[') {
                    i++;
                }
                if (params.charAt(i) == 'L') {
                    i = params.indexOf(';') + 1;
                } else {
                    i++;
                }
                sb.append(makeTypeCP(params.substring(0, i)));
                params = params.substring(i);
                if (!params.isEmpty()) {
                    sb.append(' ');
                }
            }
            return sb.append(") ")
                    .append(makeTypeCP(type.substring(ind + 1)))
                    .append(')')
                    .toString();
        }
    }

    private String parseInst(
            Instruction inst,
            List constant_pool,
            int offset,
            Map<Integer, Integer> labels,
            Target targetModel) throws ConstantPoolException {
        StringBuffer tmp = new StringBuffer();
        StringBuilder tmp2 = new StringBuilder();

        ParseVisitor parseVisitor;
        switch (targetModel) {
            case M5:
                parseVisitor = new ParseVisitorM5(constant_pool, labels, tmp2);
                break;
            case M6:
                parseVisitor = new ParseVisitorM6(constant_pool, labels, tmp2);
                break;
            default:
                throw new AssertionError();
        }
//        System.out.println(inst.toString());

        switch (targetModel) {
            case M5:
                tmp.append("(");
//                if (labels.containsKey(inst.getPC())) {
                tmp2.append("; " + inst.getPC());
//                }
                break;
            case M6:
                tmp.append("(" + offset + " (");
                if (labels.containsKey(inst.getPC())) {
                    String label = "TAG_" + labels.get(inst.getPC());
                    tagTable.put(label, new Integer(offset));
                    tmp2.append(";;at " + label);
                }
                break;
        }

        tmp.append(inst.accept(parseVisitor, null));

        switch (targetModel) {
            case M5:
                tmp.append(")");
                break;
            case M6:
                tmp.append("))");
                break;
        }
        // round the line length to multiple of 8.
        if (!tmp2.equals("")) {
            int pos;
            switch (targetModel) {
                case M5:

                    pos = 56;
                    while (pos <= tmp.length()) {
                        pos += 8;
                    }
                    break;
                case M6:
                    pos = 20;
                    while (pos <= tmp.length()) {
                        pos += 20;
                    }
                    break;
                default:
                    throw new AssertionError();
            }
            int bc = pos - tmp.length();
            for (int j = 0; j < bc; j++) {
                tmp.append(" ");
            }
            tmp.append(tmp2);
        }
        return tmp.toString().trim();
    }
}
