/* M6Method.java:  a Java representation of an ACL2 method
 *
 * $Id: M6Method.java,v 1.12 2003/06/17 22:11:16 hbl Exp hbl $
 */

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Attributes;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Instruction;
import com.sun.tools.classfile.LineNumberTable_attribute;
import com.sun.tools.classfile.LocalVariableTable_attribute;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.StackMap_attribute;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;

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

    private com.ibm.toad.cfparse.MethodInfo m0;
    private Method m;
    private com.ibm.toad.cfparse.ClassFile cf0;
    private ClassFile cf;

    private String name;
    private String desc;
    private Vector params;
    private String returntype;
    private boolean synced;
    private M6AccessFlags accessflags;

    private int max_stack; // assuming there is only one code attribute for each method.
    private int max_locals;
    private int code_length;
    private M6ExceptionHandler[] handlers;
    private M6StackMapAttrInfo stackmaps;
    private LineNumberTable_attribute lnt;
    private LocalVariableTable_attribute lvt;

    private Vector instructions;
    private Vector m6instructions;

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
     * @param mi	The MethodInfo structure representing the method to analyze.
     */
    public M6Method(com.ibm.toad.cfparse.ClassFile c0, ClassFile c, com.ibm.toad.cfparse.MethodInfo mi, Method m) {
        cf0 = c0;
        cf = c;
        m0 = mi;
        this.m = m;
        params = new Vector();
        instructions = new Vector();
        m6instructions = new Vector();
        accessflags = null;
        handlers = null;
        stackmaps = null;

        lnt = null;

        tagTable = new Hashtable();
        // synced= false; // as a accessflag now feb 13.
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
        Vector newinstr = new Vector();
        Vector instrs = m6instructions;

        for (int i = 0; i < instrs.size(); i++) {
            String inst = (String) instrs.get(i);
            Enumeration tags = tagTable.keys();
            for (; tags.hasMoreElements();) {
                Object key = tags.nextElement();
                inst = replaceAllandApp(inst, key.toString(), tagTable.get(key.toString()).toString());
            }
            newinstr.addElement(inst);
        }
        m6instructions = newinstr;
    }

    public String printLineNumberTable() {
        StringBuffer buf = new StringBuffer();
        buf.append("((\"" + cf0.getName() + "\" \"" + name + "\"");
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
                            .append("\" (");
                } else {
                    buf.append(pad + "'(\"" + name + "\" (");
                }
                break;
            case M6:
                buf.append(pad + "(method \"" + name + "\"\n");
                buf.append(pad + "      (parameters ");
                break;
        }
        for (int i = 0; i < params.size(); i++) {
            buf.append(params.get(i));

            if (i < (params.size() - 1)) {
                buf.append(" ");
            }
        }
        switch (target) {
            case M5:
                buf.append(") ");
                buf.append((m.access_flags.is(AccessFlags.ACC_SYNCHRONIZED) ? "t" : "nil") + "\n");
                break;
            case M6:
                buf.append(")\n");
                buf.append(pad + "      (returntype . " + returntype + ")\n");

                buf.append(accessflags.toString(lmargin + 6) + "\n");
                break;
        }

        switch (target) {
            case M5:
                break;
            case M6:
                buf.append(pad + "      (code");
                if (instructions != null) {
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
                if (instructions != null) {
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

    /**
     * This method processes the MethodInfo given in the constructor. It is
     * necessary to call this method before querying this method.
     *
     * @param constant_pool	The constant pool element from the M6Class object
     * representing the outer class from which this method is taken from.
     * @param target Target JVM model
     */
    public void processMethod(List constant_pool, Target target) throws ConstantPoolException {
        /* The name of the method */
        name = m0.getName();
        if (debug) {
            System.out.println("\nProcessing method: " + name);
        }

        /* We parse in the parameters into a Vector insert "top" if necessary. if necessary. */
        desc = m0.getDesc();
        String[] ps = m0.getParams();
        for (int i = 0; i < ps.length; i++) {
            String curtype = ps[i];
            params.addElement(ACL2utils.JavaTypeStrToACL2TypeStr(curtype));
        }
        if (debug) {
            System.out.println("Parameters: " + params);
        }
        /* store the return type */
        returntype = ACL2utils.JavaTypeStrToACL2TypeStr(m0.getReturnType()); // hanbing

      // we need to parse the Descriptor to get types, instead of using Java
        // String representation
        /* Set the sync flag */

        /* Set native, abstract */
      // only interested in these two flags now.  
       /*
         if (Access.isNative(m.getAccess())) {
         accessflags.addElement("*native*");
         };

         if (Access.isStatic(m.getAccess())) {
         accessflags.addElement("*static*");
         };

         if (Access.isAbstract(m.getAccess())) {
         accessflags.addElement("*abstract*");
         };

         if (Access.isSynchronized(m.getAccess())) {
         accessflags.addElement("*synced*");
         };

         */
        // Changed to make use of the M6AccessFlags 
        accessflags = new M6AccessFlags(m.access_flags);

        /* Process the code attribute */
        com.ibm.toad.cfparse.attributes.AttrInfoList al0 = m0.getAttrs();
        Attributes al = m.attributes;
        assert al0.length() == al.size();
        if (al0 == null) {
            instructions = null;
            return;  /* abstract method--no instructions */

        }

        com.ibm.toad.cfparse.attributes.CodeAttrInfo cai0 = (com.ibm.toad.cfparse.attributes.CodeAttrInfo) al0.get("Code");
        Code_attribute cai = (Code_attribute) al.get("Code");
        assert (cai == null) == (cai0 == null);
        if (cai == null || no_method_body) {
            instructions = null;
            switch (target) {
                case M5:
                    if (m.access_flags.is(AccessFlags.ACC_NATIVE)) {
                        m6instructions.addElement("NIL ; native method");
                    }
                    break;
                case M6:
                    break;
            }
        } else {
            assert cai0.getMaxStack() == cai.max_stack;
            assert cai0.getMaxLocals() == cai.max_locals;
            assert Arrays.equals(cai0.getCode(), cai.code);
            assert cai0.getExceptions().length == cai.exception_table_length;
            assert cai0.getAttrs().length() == cai.attributes.size();

            com.ibm.toad.cfparse.instruction.MutableCodeSegment mc = new com.ibm.toad.cfparse.instruction.MutableCodeSegment(cf0.getCP(), cai0, true);
            mc.setInstructionFactory(new com.ibm.toad.cfparse.instruction.StringInstructionFactory());
            com.ibm.toad.cfparse.instruction.MutableCodeIterator mci = new com.ibm.toad.cfparse.instruction.MutableCodeIterator(mc);
            /* mci is an iterator that gives access to the instructions */

            int offset = 0;
            com.ibm.toad.cfparse.instruction.ImmutableCodeSegment imc = new com.ibm.toad.cfparse.instruction.ImmutableCodeSegment(cai0.getCode());
            // ImmutableCodeIterator imci = new ImmutableCodeIterator(imc);
            // get the parsed code part

            // System.out.println("number of instructions" + mci.length() + " " + imc.getNumInstructions());
            // System.out.println("CodeSize" + cai.getCode().length + " " + " " + imc.getOffset(imc.getNumInstructions()-1));
            while (mci.hasNext()) {
                com.ibm.toad.cfparse.instruction.BaseInstruction inst0 = (com.ibm.toad.cfparse.instruction.BaseInstruction) mci.next();
                //     System.out.println("\n"+idx+inst.toString());
                if (inst0.toString().startsWith("line_number")) {
                    m6instructions.addElement("; " + inst0.toString().trim());
                    continue;
                }

                Instruction inst = new Instruction(cai.code, offset);
                assert inst0.getOpCode()
                        == (inst.getOpcode().opcode >= 0x100 ? (inst.getOpcode().opcode) >> 8 : inst.getOpcode().opcode);
                assert inst0.getLength(offset) == inst.length();
                String resultStr = parseInst(inst0, inst, cf, constant_pool, offset, target);
                if (resultStr != null) {
                    m6instructions.addElement(resultStr);
                    offset = offset + inst0.getLength(offset);

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

            // get the max_stack, in # slot
            max_stack = cai0.getMaxStack();

            // get the max_locals
            max_locals = cai0.getMaxLocals();

            // get the code_length in number of instructions. -- changed feb 17 hanbing
            // now in number of bytes -- the length of byte array. 
            // instructions like tableswitch need padding.
            code_length = cai0.getCode().length; // in bytes -- feb 17 

            // add an end marker to the code.
            switch (target) {
                case M5:
                    break;
                case M6:
                    m6instructions.addElement("(endofcode " + code_length + ")");
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
            StackMap_attribute sm = (StackMap_attribute) aal.get("StackMap");

            if (sm != null) {
                M6StackMapAttrInfo attr = new M6StackMapAttrInfo(sm, cf.constant_pool, max_locals);
                stackmaps = attr;
            } else {
                stackmaps = null;
            }

            // get LineNumberAttrInfo 
            lnt = (LineNumberTable_attribute) aal.get("LineNumberTable");
            lvt = (LocalVariableTable_attribute) aal.get("LocalVariableTable");
        }
        processed = true;

        if (M6Method.keep_debug_info) {
            lntStr = printLineNumberTable();
            lvtStr = printLocalVariableTable();
        }
    }

    private static String makeClassCP(String jtype, Target target) {
        switch (target) {
            case M5:
                return "\"" + jtype + "\"";
            case M6:
                return ACL2utils.JavaTypeStrToACL2TypeStr(jtype);
        }
        throw new AssertionError();
    }

    private static String makeFieldCP(String n, String cn, String rt, Target target) {
        switch (target) {
            case M5:
                if (ACL2utils.NAME_AND_TYPE) {
                    return "\"" + cn + "\" \"" + n + "\"";
                } else {
                    return "\"" + cn + "\" \"" + n + "\" " + (rt.equals("long") || rt.equals("double") ? "t" : "nil");
                }
            case M6:
                return "(fieldCP \"" + n + "\" \"" + cn + "\" " + makeClassCP(rt, target) + ")";
        }
        throw new AssertionError();
    }

    private static String makeMethodCP(String n, String cn, Vector params, String rt, Target target) {
        StringBuffer buf = new StringBuffer();
        switch (target) {
            case M5:
                buf.append("\"" + cn + "\" \"" + n + "\" " + params.size());
                break;
            case M6:
                buf.append("(methodCP \"" + n + "\" \"" + cn + "\" (");

                for (int i = 0; i < params.size(); i++) {
                    buf.append(makeClassCP((String) params.get(i), target));
                    if (i < params.size() - 1) {
                        buf.append(" ");
                    }
                }
                buf.append(") ");
                buf.append(makeClassCP(rt, target));
                break;
        }
        return buf.toString();
    }

    private String parseInst(com.ibm.toad.cfparse.instruction.BaseInstruction inst0,
            Instruction inst,
            ClassFile cf,
            List constant_pool,
            int offset,
            Target targetModel) throws ConstantPoolException {
        StringBuffer tmp = new StringBuffer();
        StringBuffer tmp2 = new StringBuffer();
        StringBuffer buf = new StringBuffer();
        StringTokenizer tok;
        ConstantPool.CPInfo info;
        int tag;

        System.out.println(inst0.toString());

        switch (targetModel) {
            case M5:
                tmp.append("(");
                if (inst0.getTag() != null) {
                    tmp.append("LABEL::").append(inst0.getTag()).append(" ");
                    tagTable.put(inst0.getTag(), new Integer(offset));
                    tmp2.append(";;at " + inst0.getTag());
                }
                break;
            case M6:
                tmp.append("(" + offset + " (");
                if (inst0.getTag() != null) {
                    tagTable.put(inst0.getTag(), new Integer(offset));
                    tmp2.append(";;at " + inst0.getTag());
                }
                break;
        }


        /*	 
         tmp.append("JVM::");
         */
        // here George chose not to explictly list all OPCODE, 
        // because if the inst is one byte, inst.toString() 
        // suffice
        if (inst0.getLength(offset) == 1) {
            tmp.append(inst0.toString().trim());
            switch (targetModel) {
                case M5:
                    tmp.append(")");
                    break;
                case M6:
                    tmp.append("))");  // here we handled aload_x etc.  
                    break;
            }
        } else {
            int opCode = inst0.getOpCode();
            boolean isWide = opCode == com.ibm.toad.cfparse.instruction.JVMConstants.WIDE;
            if (isWide) {
                opCode = inst0.getCode(null, 0)[1] & 0xFF;
                if (opCode != com.ibm.toad.cfparse.instruction.JVMConstants.IINC
                        && opCode != com.ibm.toad.cfparse.instruction.JVMConstants.ALOAD
                        && opCode != com.ibm.toad.cfparse.instruction.JVMConstants.ASTORE
                        && opCode != com.ibm.toad.cfparse.instruction.JVMConstants.DLOAD
                        && opCode != com.ibm.toad.cfparse.instruction.JVMConstants.DSTORE
                        && opCode != com.ibm.toad.cfparse.instruction.JVMConstants.FLOAD
                        && opCode != com.ibm.toad.cfparse.instruction.JVMConstants.FSTORE
                        && opCode != com.ibm.toad.cfparse.instruction.JVMConstants.ILOAD
                        && opCode != com.ibm.toad.cfparse.instruction.JVMConstants.ISTORE
                        && opCode != com.ibm.toad.cfparse.instruction.JVMConstants.LLOAD
                        && opCode != com.ibm.toad.cfparse.instruction.JVMConstants.LSTORE
                        && opCode != com.ibm.toad.cfparse.instruction.JVMConstants.RET) {
                    opCode = com.ibm.toad.cfparse.instruction.JVMConstants.WIDE;
                }
            }
            switch (opCode) {
                /* aload v5
                 * (JVM::aload 4) */
                case com.ibm.toad.cfparse.instruction.JVMConstants.ALOAD:
                case com.ibm.toad.cfparse.instruction.JVMConstants.ASTORE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.ILOAD:
                case com.ibm.toad.cfparse.instruction.JVMConstants.ISTORE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.LLOAD:
                case com.ibm.toad.cfparse.instruction.JVMConstants.LSTORE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.FLOAD:
                case com.ibm.toad.cfparse.instruction.JVMConstants.FSTORE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.DLOAD:
                case com.ibm.toad.cfparse.instruction.JVMConstants.DSTORE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.BIPUSH:
                case com.ibm.toad.cfparse.instruction.JVMConstants.RET:
                case com.ibm.toad.cfparse.instruction.JVMConstants.SIPUSH:
                    buf.append(inst0.toString().replace("wide ", "").trim());
                    buf.deleteCharAt(inst0.toString().trim().indexOf(" ") + 1);
                    tmp.append(buf);
                    break;

                /* anewarray java.lang.String
                 * (JVM::anewarray (class "java.lang.String")) */
                case com.ibm.toad.cfparse.instruction.JVMConstants.ANEWARRAY:
                case com.ibm.toad.cfparse.instruction.JVMConstants.NEW:
                case com.ibm.toad.cfparse.instruction.JVMConstants.INSTANCEOF:
                    tok = new StringTokenizer(inst0.toString().trim());
                    buf.append(tok.nextToken());
                    String typeName = tok.nextToken();
                    buf.append(" " + makeClassCP(typeName, targetModel));
                    tmp.append(buf.toString());
                    break;

                /* getfield Model3D java.awt.Dimention.width
                 * (JVM::getfield (fieldCP "width" "java.awt.Dimention" (class "java.awt.Dimention.Model3D")) */
                case com.ibm.toad.cfparse.instruction.JVMConstants.GETFIELD:
                case com.ibm.toad.cfparse.instruction.JVMConstants.PUTFIELD:
                    tok = new StringTokenizer(inst0.toString().trim());
                    buf.append(tok.nextToken()); // the opcode

                    String fieldTypeName = tok.nextToken();             // throw away the data type
                    String field = tok.nextToken();
                    String fieldClassName = field.substring(0, field.lastIndexOf("."));
                    String fieldName = field.substring(field.lastIndexOf(".") + 1);
                    if (targetModel == Target.M5 && ACL2utils.NAME_AND_TYPE) {
                        byte[] instCode = inst0.getCode(null, 0);
                        assert instCode.length == 3;
                        int fieldRefOffs = ((instCode[1] & 0xFF) << 8) | (instCode[2] & 0xFF);
                        ConstantPool.CONSTANT_Fieldref_info infoFieldref
                                = (ConstantPool.CONSTANT_Fieldref_info) cf.constant_pool.get(fieldRefOffs);
                        String fieldType = cf.constant_pool.getNameAndTypeInfo(infoFieldref.name_and_type_index).getType();
                        fieldName += ":" + fieldType;
                    }

                    buf.append(" " + makeFieldCP(fieldName, fieldClassName, fieldTypeName, targetModel));

                    tmp.append(buf.toString());
                    break;

                /* goto TAG_9
                 * (JVM::goto ) */
                case com.ibm.toad.cfparse.instruction.JVMConstants.GOTO:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IF_ACMPEQ:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IF_ACMPNE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IF_ICMPEQ:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IF_ICMPNE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IF_ICMPLT:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IF_ICMPGE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IF_ICMPGT:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IF_ICMPLE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IFEQ:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IFNE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IFLT:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IFGE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IFGT:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IFLE:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IFNONNULL:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IFNULL:
                case com.ibm.toad.cfparse.instruction.JVMConstants.JSR:
                case com.ibm.toad.cfparse.instruction.JVMConstants.JSR_W:
                    buf.append(inst0.toString().trim());
                    switch (targetModel) {
                        case M5:
                            buf.insert(buf.toString().indexOf(" ") + 1, "LABEL::");
                            break;
                        case M6:
                            break;
                    }
                    tmp.append(buf.toString());
                    // leave the tag in the buf. 
                    break;

                /* newarray T_INT
                 * (JVM::newarray T_INT) */
                case com.ibm.toad.cfparse.instruction.JVMConstants.NEWARRAY:
                    String narray = inst0.toString().trim();
                    tmp.append(narray.substring(0, narray.indexOf(" "))); // op-code
                    tmp.append(" " + narray.substring(narray.lastIndexOf("_") + 1));
                    break;

                /* iinc v4 #1
                 * (JVM::iinc 4 1) */
                case com.ibm.toad.cfparse.instruction.JVMConstants.IINC:
                    buf.append(inst0.toString().replace("wide ", "").trim());
                    buf.deleteCharAt(inst0.toString().trim().indexOf(" ") + 1);
                    buf.deleteCharAt(buf.toString().lastIndexOf(" ") + 1);
                    tmp.append(buf);
                    break;

                /* multianewarray java.lang.Object[][][] #3
                 * (JVM::multianewarray 3) */
                case com.ibm.toad.cfparse.instruction.JVMConstants.MULTIANEWARRAY:
                    tok = new StringTokenizer(inst0.toString().trim());
                    buf.append(tok.nextToken()); // the opcode
                    String ArrayType = tok.nextToken();             // throw away the data type
                    buf.append(makeClassCP(ArrayType, targetModel));
                    buf.append(" ");
                    String dimCount = tok.nextToken();
                    buf.append(" ");
                    buf.append(dimCount.substring(1, dimCount.length()));
                    tmp.append(buf.toString());
                    break;

                /* invokevirtual int Alpha.fun2(java.lang.Object, int)
                 * (JVM::invokevirtual "Alpha" "fun2" 2) */
                case com.ibm.toad.cfparse.instruction.JVMConstants.INVOKEVIRTUAL:
                case com.ibm.toad.cfparse.instruction.JVMConstants.INVOKESTATIC:
                case com.ibm.toad.cfparse.instruction.JVMConstants.INVOKESPECIAL:
                    tok = new StringTokenizer(inst0.toString().trim());
                    tmp.append(tok.nextToken()); // opcode
                    String MethodReturnType = tok.nextToken(); // throw away the return type
                    String Method = tok.nextToken("(");
                    String MethodClassName = Method.substring(0, Method.lastIndexOf(".")).trim();
                    String MethodName = Method.substring(Method.lastIndexOf(".") + 1).trim();
                    if (targetModel == Target.M5 && ACL2utils.NAME_AND_TYPE) {
                        byte[] instCode = inst0.getCode(null, 0);
                        assert instCode.length == 3;
                        int methRefOffs = ((instCode[1] & 0xFF) << 8) | (instCode[2] & 0xFF);
                        ConstantPool.CPInfo methRef = cf.constant_pool.get(methRefOffs);
                        int name_and_type_index;
                        switch (methRef.getTag()) {
                            case ConstantPool.CONSTANT_Methodref:
                                name_and_type_index = ((ConstantPool.CONSTANT_Methodref_info) methRef).name_and_type_index;
                                break;
                            case ConstantPool.CONSTANT_InterfaceMethodref:
                                name_and_type_index = ((ConstantPool.CONSTANT_InterfaceMethodref_info) methRef).name_and_type_index;
                                break;
                            default:
                                throw new AssertionError();
                        }
                        String methType = cf.constant_pool.getNameAndTypeInfo(name_and_type_index).getType();
                        MethodName += ":" + methType;
                    }
                    Vector params = new Vector();
                    String rawparams = tok.nextToken("");

                    /* trim off the outer parens */
                    rawparams = rawparams.substring(1, rawparams.length() - 1);
                    if (rawparams.compareTo("") == 0) {
                    } else {
                        tok = new StringTokenizer(rawparams);
                        while (tok.hasMoreTokens()) {
                            params.addElement(tok.nextToken(",").trim());
                        };
                    }
                    switch (targetModel) {
                        case M5:
                            tmp.append(" " + makeMethodCP(MethodName, MethodClassName, params, MethodReturnType, targetModel));
                            break;
                        case M6:
                            tmp.append("\n\t\t\t\t\t" + makeMethodCP(MethodName, MethodClassName, params, MethodReturnType, targetModel) + ")");
                            break;
                    }
                    break;

                /* ldc "Hello, World!"
                 * ldc #65535
                 * (JVM::ldc 3)  3 points to the STRING object "Hello, World!"
                 * (JVM::ldc 5)  5 points to an INT object 65535 */
                case com.ibm.toad.cfparse.instruction.JVMConstants.LDC:
                case com.ibm.toad.cfparse.instruction.JVMConstants.LDC_W:
                    if (opCode == com.ibm.toad.cfparse.instruction.JVMConstants.LDC) {
                        tmp.append("ldc ");
                        byte cpidx1 = inst0.getCode(null, 1)[1];
                        int cpidx = (int) cpidx1 & 255;
                        info = cf.constant_pool.get(cpidx);
                        tag = info.getTag();
                    } else {
                        tmp.append("ldc_w ");

                        int cpidx1 = (int) inst0.getCode(null, 1)[1];
                        cpidx1 = cpidx1 & 255;
                        int cpidx2 = (int) inst0.getCode(null, 1)[2];
                        cpidx2 = cpidx2 & 255;

                        int ldc_idx = (cpidx1 << 8) | cpidx2;
                        info = cf.constant_pool.get(ldc_idx);
                        tag = info.getTag();
                    }

                    if (tag == ConstantPool.CONSTANT_Integer) {

                        Integer val = new Integer(((ConstantPool.CONSTANT_Integer_info) info).value);

                        if (constant_pool.contains(val)) {
                            tmp.append(constant_pool.indexOf(val));
                        } else {
                            constant_pool.add(val);
                            tmp.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(";;INT:: \"" + val + "\"");
                    } else if (tag == ConstantPool.CONSTANT_Float) {

                        Float val = new Float(((ConstantPool.CONSTANT_Float_info) info).value);

                        if (constant_pool.contains(val)) {
                            tmp.append(constant_pool.indexOf(val));
                        } else {
                            constant_pool.add(val);
                            tmp.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(";;FLOAT:: \"" + val + "\"");
                    } else if (tag == ConstantPool.CONSTANT_Double) {

                        Double val = new Double(((ConstantPool.CONSTANT_Double_info) info).value);

                        if (constant_pool.contains(val)) {
                            tmp.append(constant_pool.indexOf(val));
                        } else {
                            constant_pool.add(val);
                            tmp.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(";;Doulbe:: \"" + val + "\"");
                    } else if (tag == ConstantPool.CONSTANT_Long) {

                        Long val = new Long(((ConstantPool.CONSTANT_Long_info) info).value);

                        if (constant_pool.contains(val)) {
                            tmp.append(constant_pool.indexOf(val));
                        } else {
                            constant_pool.add(val);
                            tmp.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(";;Long:: \"" + val + "\"");
                    } else if (tag == ConstantPool.CONSTANT_Class) {
                        M6Class.ClassRef val = new M6Class.ClassRef(((ConstantPool.CONSTANT_Class_info) info).getName().replace('/', '.'));

                        if (constant_pool.contains(val)) {
                            tmp.append(constant_pool.indexOf(val));
                        } else {
                            constant_pool.add(val);
                            tmp.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(";;CLASS:: \"" + val + "\"");
                    } else {
                        M6Class.StringRef val = new M6Class.StringRef(((ConstantPool.CONSTANT_String_info) info).getString());

                        if (constant_pool.contains(val)) {
                            tmp.append(constant_pool.indexOf(val));
                        } else {
                            constant_pool.add(val);
                            tmp.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(";;STRING:: \"" + val.toString(targetModel == Target.M6) + "\"");
                    }
                    break;

                /* ldc2_w 837462398
                 * (JVM::ldc2_w 3)  3 points to the LONG object 837462398 */
                case com.ibm.toad.cfparse.instruction.JVMConstants.LDC2_W:

                    tmp.append("ldc2_w ");

                    int cpidx1 = (int) inst0.getCode(null, 1)[1];
                    cpidx1 = cpidx1 & 255;
                    int cpidx2 = (int) inst0.getCode(null, 1)[2];
                    cpidx2 = cpidx2 & 255;

                    int ldc_idx = (cpidx1 << 8) | cpidx2;

                    info = cf.constant_pool.get(ldc_idx);
                    tag = info.getTag();

                    if (tag == ConstantPool.CONSTANT_Long) {
                        Long val = new Long(((ConstantPool.CONSTANT_Long_info) info).value);

                        if (constant_pool.contains(val)) {
                            tmp.append(constant_pool.indexOf(val));
                        } else {
                            constant_pool.add(val);
                            tmp.append(constant_pool.size() - 1);  // zero-based indices
                        }
                        tmp2.append(";; LONG:: \"" + val + "\"");
                    } else {
                        Double val = new Double(((ConstantPool.CONSTANT_Double_info) info).value);

                        if (constant_pool.contains(val)) {
                            tmp.append(constant_pool.indexOf(val));
                        } else {
                            constant_pool.add(val);
                            tmp.append(constant_pool.size() - 1); // zero-based indices
                        }
                        tmp2.append(";; DOUBLE:: \"" + val + "\"");
                    }
                    break;


                /* Non-standard bytecode
                 * Varies machine to machine
                 * We throw them away */
                case com.ibm.toad.cfparse.instruction.JVMConstants.IMPDEP1:
                case com.ibm.toad.cfparse.instruction.JVMConstants.IMPDEP2:
                    System.out.println("IMPDEP!!!");
                    return null;

                /* following are added by hanbing */
                /* invokeinterface <returntype> <methodClass>.<methodname>(<param>*) */
                /* (invokeinterface byte[] com.ibm.toad.cfparse.instruction.BaseInstruction.getCode(int[], int) #3 #0 )) */
                /* (JVM::ldc2_w 3)  3 points to the LONG object 837462398 */
                case com.ibm.toad.cfparse.instruction.JVMConstants.INVOKEINTERFACE: {
                    tok = new StringTokenizer(inst0.toString().trim());
                    tmp.append(tok.nextToken()); // opcode
                    String iMethodReturnType = tok.nextToken();
                    String iMethod = tok.nextToken("(");
                    String iMethodClassName = iMethod.substring(0, iMethod.lastIndexOf(".")).trim();
                    String iMethodName = iMethod.substring(iMethod.lastIndexOf(".") + 1).trim();
                    if (targetModel == Target.M5 && ACL2utils.NAME_AND_TYPE) {
                        byte[] iinstCode = inst0.getCode(null, 0);
                        assert iinstCode.length == 5;
                        int imethRefOffs = ((iinstCode[1] & 0xFF) << 8) | (iinstCode[2] & 0xFF);
                        ConstantPool.CONSTANT_InterfaceMethodref_info infoInterfaceMethodref
                                = (ConstantPool.CONSTANT_InterfaceMethodref_info) cf.constant_pool.get(imethRefOffs);
                        String imethType = cf.constant_pool.getNameAndTypeInfo(infoInterfaceMethodref.name_and_type_index).getType();
                        iMethodName += ":" + imethType;
                    }
                    Vector iparams = new Vector();
                    String irawparams = tok.nextToken(")");
                    /* trim off the outer parens */

                    irawparams = irawparams.substring(1, irawparams.length());
                    if (irawparams.compareTo("") == 0) {
                    } else {
                        StringTokenizer itok = new StringTokenizer(irawparams);
                        while (itok.hasMoreTokens()) {
                            iparams.addElement(itok.nextToken(",").trim());
                        }
                    }

                    tok.nextToken("#"); // skip
                    String tc = tok.nextToken(" ");

                    switch (targetModel) {
                        case M5:
                            tmp.append(" " + makeMethodCP(iMethodName, iMethodClassName, iparams, iMethodReturnType, targetModel) + " " + tc.substring(1));
                            break;
                        case M6:
                            tmp.append("\n\t\t\t\t\t"
                                    + makeMethodCP(iMethodName, iMethodClassName, iparams, iMethodReturnType, targetModel)
                                    + ") " + tc.substring(1));
                            break;
                    }
                }
                break;

                case com.ibm.toad.cfparse.instruction.JVMConstants.CHECKCAST: {
                    tok = new StringTokenizer(inst0.toString().trim());
                    tmp.append(tok.nextToken());
                    String castType = tok.nextToken();
                    tmp.append(" " + ACL2utils.JavaTypeStrToACL2TypeStr(castType));
                }
                break;

                case com.ibm.toad.cfparse.instruction.JVMConstants.PUTSTATIC:
                case com.ibm.toad.cfparse.instruction.JVMConstants.GETSTATIC: {
                    tok = new StringTokenizer(inst0.toString().trim());
                    buf.append(tok.nextToken()); // the opcode

                    String sfieldTypeName = tok.nextToken();             // throw away the data type
                    String sfield = tok.nextToken();
                    String sfieldClassName = sfield.substring(0, sfield.lastIndexOf("."));
                    String sfieldName = sfield.substring(sfield.lastIndexOf(".") + 1);
                    if (targetModel == Target.M5 && ACL2utils.NAME_AND_TYPE) {
                        byte[] instCode = inst0.getCode(null, 0);
                        assert instCode.length == 3;
                        int sfieldRefOffs = ((instCode[1] & 0xFF) << 8) | (instCode[2] & 0xFF);
                        ConstantPool.CONSTANT_Fieldref_info infoFieldref
                                = (ConstantPool.CONSTANT_Fieldref_info) cf.constant_pool.get(sfieldRefOffs);
                        String sfieldType = cf.constant_pool.getNameAndTypeInfo(infoFieldref.name_and_type_index).getType();
                        sfieldName += ":" + sfieldType;
                    }

                    buf.append(" " + makeFieldCP(sfieldName, sfieldClassName, sfieldTypeName, targetModel));
                    tmp.append(buf.toString());
                }
                break;

                /*	  lookupswitch  <count>  <(key target)>* default */
                case com.ibm.toad.cfparse.instruction.JVMConstants.LOOKUPSWITCH: { /* for pupose of type checking, we only need to collect two list 
                     * key list and target list, we don't really care how those values are used.
                     * but have a execution model in ACL2 in mind. 
                     * I would chose to have a representation closer to the real JVM.  */

                    tok = new StringTokenizer(inst0.toString().trim());
                    tmp.append(tok.nextToken()); // the opcode

                    String defaultt = tok.nextToken().trim(); // the default target
                    buf.append(defaultt);

                    String count = tok.nextToken().substring(1).trim(); // the pair count
                    int c = Integer.parseInt(count);

                    buf.append(" " + count);

                    buf.append(" (");
                    for (int i = 0; i < c; i++) {
                        String curpair = tok.nextToken(")").trim() + ")";

                        int start = curpair.indexOf('#') + 1;
                        int end = curpair.indexOf(',');
                        String key = curpair.substring(start, end);

                        start = curpair.indexOf(' ') + 1;
                        end = curpair.indexOf(')');
                        String target = curpair.substring(start, end);
                        buf.append("(" + key + " . " + target + ") ");
                    }
                    buf.setCharAt(buf.length() - 1, ')');
                    tmp.append(" (lookupswitchinfo " + buf.toString() + ")");
                }
                break;

                case com.ibm.toad.cfparse.instruction.JVMConstants.TABLESWITCH: {
                    tok = new StringTokenizer(inst0.toString().trim());
                    tmp.append(tok.nextToken()); // the opcode

                    String tabledefault = tok.nextToken().trim(); // the default target
                    buf.append(tabledefault);

                    String range = tok.nextToken();

                    int s = range.indexOf('#') + 1;
                    int e = range.lastIndexOf('-');
                    String low = range.substring(s, e);

                    int start = range.lastIndexOf('#') + 1;
                    String high = range.substring(start);

                    int l = Integer.parseInt(low);
                    int h = Integer.parseInt(high);

                    buf.append(" (" + low + " . " + high + ")");

                    buf.append(" (");
                    for (int i = 0; i < h - l + 1; i++) {
                        String target = tok.nextToken().trim();
                        buf.append(target + " ");
                    }
                    buf.setCharAt(buf.length() - 1, ')');
                    tmp.append(" (tableswitchinfo " + buf.toString() + ")");
                }
                break;
                default:
                    tmp.append("<unsupported> " + inst0.toString());
            }

            switch (targetModel) {
                case M5:
                    tmp.append(")");
                    break;
                case M6:
                    tmp.append("))");
                    break;
            }
        }
        // round the line length to multiple of 20.
        if (!tmp2.equals("")) {
            int bc = 20 - tmp.length() % 20;
            for (int j = 0; j < bc; j++) {
                tmp.append(" ");
            }
            tmp.append(tmp2);
        }
        return tmp.toString().trim();
    }
}
