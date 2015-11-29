/* M6Method.java:  a Java representation of an ACL2 method
 *
 * $Id: M6Method.java,v 1.12 2003/06/17 22:11:16 hbl Exp hbl $
 */

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.DescriptorException;
import com.sun.tools.classfile.Instruction;
import com.sun.tools.classfile.LineNumberTable_attribute;
import com.sun.tools.classfile.LocalVariableTable_attribute;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.Opcode;
import com.sun.tools.classfile.StackMapTable_attribute;
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

    private final M6Class cls;
    private final Method m;
    private final ClassFile cf;

    private final String name;
    private final String desc;
    private final List<String> params = new ArrayList<>();
    private final String returntype;

    private final Code_attribute cai;
    private final int max_stack; // assuming there is only one code attribute for each method.
    private final int max_locals;
    private final int code_length;
    private final StackMap_attribute sm;
    private final LineNumberTable_attribute lnt;
    private final LocalVariableTable_attribute lvt;

    private List<String> m6instructions = new ArrayList<>();

    private boolean debug;
    private boolean processed;

    private Hashtable tagTable;

    private static boolean no_method_body = false;

    /**
     * This constructor takes a ClassFile and the MethodInfo structure
     * representing the method and produces an M6Method object.
     *
     * @param c	The ClassFile representing the class
     * @param m	The MethodInfo structure representing the method to analyze.
     */
    public M6Method(M6Class cls, Method m)
            throws ConstantPoolException {
        this.cls = cls;
        cf = cls.cf;
        this.m = m;
        name = m.getName(cf.constant_pool);
        desc = m.descriptor.getValue(cf.constant_pool);
        String[] ss = splitMethodDesc(desc);
        for (int i = 1; i < ss.length; i++) {
            params.add(ss[i]);
        }
        returntype = ss[0];
        cai = (Code_attribute) m.attributes.get(Attribute.Code);
        if (cai != null) {
            max_stack = cai.max_stack;
            max_locals = cai.max_locals;
            code_length = cai.code_length;
            sm = (StackMap_attribute) cai.attributes.get(Attribute.StackMap);
            lnt = (LineNumberTable_attribute) cai.attributes.get(Attribute.LineNumberTable);
            lvt = (LocalVariableTable_attribute) cai.attributes.get(Attribute.LocalVariableTable);
        } else {
            max_stack = max_locals = code_length = 0;
            sm = null;
            lnt = null;
            lvt = null;
        }

        tagTable = new Hashtable();
//        debug = true;
        processed = false;
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

    public String getLntStr() throws ConstantPoolException {
        StringBuffer buf = new StringBuffer();
        buf.append("((\"" + cf.getName() + "\" \"" + name + "\"");
        buf.append("\n  (");
        for (int i = 0; i < params.size(); i++) {
            buf.append(ACL2utils.descToACL2TypeStr(params.get(i)));
            if (i < (params.size() - 1)) {
                buf.append(" ");
            }
        }
        buf.append(")\n");
        buf.append("      (returntype . " + ACL2utils.descToACL2TypeStr(returntype) + "))\n");
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

    public String getLvtStr() throws ConstantPoolException {
        StringBuffer buf = new StringBuffer();
        buf.append("((\"" + cf.getName() + "\" \"" + name + "\"");
        buf.append("\n  (");

        for (int i = 0; i < params.size(); i++) {
            buf.append(ACL2utils.descToACL2TypeStr(params.get(i)));
            if (i < (params.size() - 1)) {
                buf.append(" ");
            }
        }
        buf.append(")\n");
        buf.append("      (returntype . " + ACL2utils.descToACL2TypeStr(returntype) + "))\n");
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
                        + " " + ACL2utils.classNameToACL2TypeStr(desc) + ")\n");
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
    public String toString(int lmargin, Target target) throws ConstantPoolException {
        if (!processed) {
            return "You must call processMethod first!";
        }

        StringBuilder sb = new StringBuilder();
        switch (target) {
            case M5:
                if (ACL2utils.NAME_AND_TYPE) {
                    indent(sb, lmargin);
                    sb
                            .append("'(\"")
                            .append(name)
                            .append(":")
                            .append(desc)
                            .append("\"");
                } else {
                    indent(sb, lmargin);
                    sb.append("'(\"" + name + "\" (");
                    for (int i = 0; i < params.size(); i++) {
                        sb.append(params.get(i));

                        if (i < (params.size() - 1)) {
                            sb.append(" ");
                        }
                    }
                    sb.append(')');
                }
                sb.append(' ');
                sb.append((m.access_flags.is(AccessFlags.ACC_SYNCHRONIZED) ? "t" : "nil") + "\n");

                for (int i = 0; i < m6instructions.size(); i++) {
                    indent(sb, lmargin + 2);
                    sb.append(m6instructions.get(i)).append('\n');
                }
                indent(sb, lmargin + 1);
                sb.append(")");
                break;
            case M6:
                indent(sb, lmargin);
                sb.append("(method \"" + name + "\"");
                nl(sb, lmargin + 6, "(parameters ");
                for (int i = 0; i < params.size(); i++) {
                    sb.append(ACL2utils.descToACL2TypeStr(params.get(i)));

                    if (i < (params.size() - 1)) {
                        sb.append(" ");
                    }
                }
                sb.append(")");
                nl(sb, lmargin + 6, "(returntype . " + ACL2utils.descToACL2TypeStr(returntype) + ")");
                nl(sb, lmargin + 6, ACL2utils.accessFlagsToString(m.access_flags));

                nl(sb, lmargin + 6, "(code");
                if (cai != null && !no_method_body) {
                    nl(sb, lmargin + 11, "(max_stack . " + max_stack + ")"
                            + " (max_locals . " + max_locals + ")"
                            + " (code_length . " + code_length + ")");
                    nl(sb, lmargin + 11, "(parsedcode");

                    for (int i = 0; i < m6instructions.size(); i++) {
                        nl(sb, lmargin + 14, m6instructions.get(i));
                    }
                    sb.append(")");

                    nl(sb, lmargin + 11, "(Exceptions ");
                    for (int i = 0; i < cai.exception_table.length; i++) {
                        Code_attribute.Exception_data ed = cai.exception_table[i];
                        String catchType = ed.catch_type != 0
                                ? cf.constant_pool.getClassInfo(ed.catch_type).getName().replace('/', '.')
                                : "java/lang.Throwable";
                        String acl2Type = ACL2utils.classNameToACL2TypeStr(catchType);
                        nl(sb, lmargin + 13,
                                "(handler " + ed.start_pc + " " + ed.end_pc + "  " + ed.handler_pc + " " + acl2Type + ")");
                    }
                    sb.append(')');

                    nl(sb, lmargin + (sm != null ? 12 : 11), "(StackMap ");
                    if (sm != null) {
                        for (int i = 0; i < sm.entries.length; i++) {
                            sb.append('\n');
                            StackMap_attribute.stack_map_frame smf
                                    = (StackMap_attribute.stack_map_frame) sm.entries[i];
                            appendStackMapFrame(sb, lmargin + 17, smf, cf.constant_pool, max_locals);
                        }
                    }
                    sb.append(")");
                }
                sb.append("))");
                break;
        }
        return sb.toString();
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

    private static void appendStackMapFrame(StringBuilder sb, int lmargin,
            StackMap_attribute.stack_map_frame smf,
            ConstantPool cp,
            int FrameSize) throws ConstantPoolException {
        indent(sb, lmargin);
        sb.append('(').append(smf.offset_delta).append(" (frame");
        nl(sb, lmargin + 4);
        sb.append("(locals ");

        String rtflags = "nil";
        int k = 0;
        for (int i = 0; i < smf.number_of_locals; i++) {
            StackMapTable_attribute.verification_type_info typeInfo
                    = (StackMapTable_attribute.verification_type_info) smf.locals[i];
            if (k > 0) {
                nl(sb, lmargin + 9);
            }
            appendStackMapType(sb, typeInfo, cp);
            k++;
            switch (typeInfo.tag) {
                case StackMapTable_attribute.verification_type_info.ITEM_Long:
                case StackMapTable_attribute.verification_type_info.ITEM_Double:
                    nl(sb, lmargin + 9);
                    sb.append("topx");
                    k++;
                    break;
                case StackMapTable_attribute.verification_type_info.ITEM_UninitializedThis:
                    rtflags = "(flagThisUninit)";
                    break;
            }
        }
        while (k < FrameSize) {
            if (k > 0) {
                nl(sb, lmargin + 9);
            }
            sb.append("topx");
            k++;
        }
        sb.append(')');
        nl(sb, lmargin + 4);
        sb.append("(stack ");
        k = 0;
        for (int i = smf.number_of_stack_items - 1; i >= 0; i--) {
            StackMapTable_attribute.verification_type_info typeInfo
                    = (StackMapTable_attribute.verification_type_info) smf.stack[i];
            switch (typeInfo.tag) {
                case StackMapTable_attribute.verification_type_info.ITEM_Long:
                case StackMapTable_attribute.verification_type_info.ITEM_Double:
                    if (k > 0) {
                        nl(sb, lmargin + 9);
                    }
                    sb.append("topx");
                    k++;
                    break;
            }
            if (k > 0) {
                nl(sb, lmargin + 9);
            }
            appendStackMapType(sb, typeInfo, cp);
            k++;
        }
        sb.append(')');
        nl(sb, lmargin + 4);
        sb.append(rtflags).append("))");
    }

    private static String[] type_names
            = {"topx", "int", "float", "double",
                "long", "null", "uninitializedThis", "cpindex",
                "uninitilizedoffset", "returnaddress"};

    private static void appendStackMapType(StringBuilder sb,
            StackMapTable_attribute.verification_type_info typeInfo, ConstantPool cp)
            throws ConstantPoolException {
        if (typeInfo == null) {
            sb.append(type_names[0]);
            return;
        }
        int type = typeInfo.tag;
        switch (type) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
                sb.append(type_names[type]);
                break;
            case 7: {
                StackMapTable_attribute.Object_variable_info typeInfoObject
                        = (StackMapTable_attribute.Object_variable_info) typeInfo;
                int index = typeInfoObject.cpool_index;
                ConstantPool.CONSTANT_Class_info info = cp.getClassInfo(index);
                sb.append(ACL2utils.classNameToACL2TypeStr(info.getName()));
            }
            break;
            case 8: {
                StackMapTable_attribute.Uninitialized_variable_info typeInfoUninitialized
                        = (StackMapTable_attribute.Uninitialized_variable_info) typeInfo;
                int offset = typeInfoUninitialized.offset;
                sb.append("(uninitialized " + offset + ")");
            }
            break;
            case 9: {
                assert false;
//                int offset;
//                offset = file.readUnsignedShort();
//                type_name = "(returnAddress " + offset + ")";
            }
            break;
            default:
                throw new RuntimeException("Illeagal Type in StackMap" + type);

        }
    }

    public static void setAbstractMode() {
        no_method_body = true;
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
    public void processMethod(Target target) throws ConstantPoolException, DescriptorException {
        /* The name of the method */
        if (debug) {
            System.out.println("\nProcessing method: " + name + "Parameters: " + params);
        }
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
                String resultStr = parseInst(instr, offset, labels, target);
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
        }
        processed = true;
    }

    abstract class ParseVisitor implements Instruction.KindVisitor<String, Void> {

        final Map<Integer, Integer> labels;
        final StringBuilder tmp2;

        ParseVisitor(Map<Integer, Integer> labels, StringBuilder tmp2) {
            this.labels = labels;
            this.tmp2 = tmp2;
        }

        abstract String label(int pc, int offset);

        abstract String typeKind(Instruction.TypeKind tk);

        abstract void cpRef(StringBuilder sb, Opcode op, M6Class.CPEntry cpe);

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
                    .append(typeKind(tk))
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
            sb.append(instr.getMnemonic());
            cpRef(sb, instr.getOpcode(), cls.constantPool[index]);
            return sb.toString();
        }

        @Override
        public String visitConstantPoolRefAndValue(Instruction instr, int index, int value, Void p) {
            StringBuilder sb = new StringBuilder();
            sb.append(instr.getMnemonic());
            cpRef(sb, instr.getOpcode(), cls.constantPool[index]);
            sb.append(' ').append(value);
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

        ParseVisitorM5(Map<Integer, Integer> labels, StringBuilder tmp2) {
            super(labels, tmp2);
        }

        @Override
        String label(int pc, int offset) {
            return Integer.toString(offset);
        }

        @Override
        String typeKind(Instruction.TypeKind tk) {
            return "T_" + tk.name.toUpperCase();
        }

        @Override
        void cpRef(StringBuilder sb, Opcode op, M6Class.CPEntry cpe) {
            cpe.appendCPRefM5(sb, tmp2, op);
        }
    }

    class ParseVisitorM6 extends ParseVisitor {

        ParseVisitorM6(Map<Integer, Integer> labels, StringBuilder tmp2) {
            super(labels, tmp2);
        }

        @Override
        String label(int pc, int offset) {
            return "TAG_" + labels.get(pc + offset);
        }

        @Override
        String typeKind(Instruction.TypeKind tk) {
            return tk.name.toUpperCase();
        }

        @Override
        void cpRef(StringBuilder sb, Opcode op, M6Class.CPEntry cpe) {
            cpe.appendCPRefM6(sb, tmp2, op);
        }
    }

    private String parseInst(
            Instruction inst,
            int offset,
            Map<Integer, Integer> labels,
            Target targetModel) throws ConstantPoolException {
        StringBuffer tmp = new StringBuffer();
        StringBuilder tmp2 = new StringBuilder();

        ParseVisitor parseVisitor;
        switch (targetModel) {
            case M5:
                parseVisitor = new ParseVisitorM5(labels, tmp2);
                break;
            case M6:
                parseVisitor = new ParseVisitorM6(labels, tmp2);
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

    static String[] splitMethodDesc(String desc) {
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
}
