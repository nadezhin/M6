
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.StackMapTable_attribute;
import com.sun.tools.classfile.StackMap_attribute;

public class M6StackMapFrame {

    private int pc;
    private int num_locals;
    private M6StackMapType[] types_of_locals;
    private int num_stack_items;
    private M6StackMapType[] types_of_stack_items;
    private String flags;

    public M6StackMapFrame(StackMap_attribute.stack_map_frame smf,
            ConstantPool cp,
            int FrameSize)
            throws ConstantPoolException {
        pc = smf.offset_delta;
        num_locals = smf.number_of_locals;
        types_of_locals = new M6StackMapType[num_locals];
        for (int i = 0; i < num_locals; i++) {
            StackMapTable_attribute.verification_type_info type_info
                    = (StackMapTable_attribute.verification_type_info) smf.locals[i];
            types_of_locals[i] = new M6StackMapType(type_info, cp);
        }

        flags = stackFrameDefaultFlags(types_of_locals);

        types_of_locals = ACL2utils.expandTypeList(types_of_locals);
        types_of_locals = ACL2utils.expandToLength(types_of_locals, FrameSize);
        num_locals = FrameSize;

        num_stack_items = smf.number_of_stack_items;
        types_of_stack_items = new M6StackMapType[num_stack_items];
        for (int i = 0; i < num_stack_items; i++) {
            StackMapTable_attribute.verification_type_info type_info
                    = (StackMapTable_attribute.verification_type_info) smf.stack[i];
            types_of_stack_items[i] = new M6StackMapType(type_info, cp);
        };

        types_of_stack_items = ACL2utils.expandTypeList(types_of_stack_items);
        num_stack_items = types_of_stack_items.length;

        reverse(types_of_stack_items);
    }

    private String stackFrameDefaultFlags(M6StackMapType[] types_of_locals) {
        String rflags;
        rflags = "nil";
        for (int i = 0; i < types_of_locals.length; i++) {
            if (types_of_locals[i].toString().compareToIgnoreCase("uninitializedThis") == 0) {
                rflags = "(flagThisUninit)";
            }
        }
        return rflags;
    }

    private void reverse(M6StackMapType[] l) {
        int i = 0;
        int j = l.length - 1;

        M6StackMapType t;

        while (i < j) {
            t = l[i];
            l[i] = l[j];
            l[j] = t;
            i++;
            j--;
        }
    }

    public String toString(int lmargin) {
        StringBuffer pdb = new StringBuffer();
        String pad;
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < lmargin; i++) {
            pdb.append(" ");
        }
        pad = pdb.toString();

        buf.append(pad + "(" + pc);
        buf.append(" (frame\n");
        buf.append(pad + "    " + "(locals ");
        for (int i = 0; i < num_locals; i++) {
            if (i > 0) {
                buf.append("\n" + pad + "         ");
            }
            buf.append(types_of_locals[i].toString());
        }
        if (num_locals == 0) {
            buf.append("");
        }
        buf.append(")\n");
        buf.append(pad + "    " + "(stack ");
        for (int i = 0; i < num_stack_items; i++) {
            if (i > 0) {
                buf.append("\n" + pad + "         ");
            }
            buf.append(types_of_stack_items[i].toString());
        }
        if (num_stack_items == 0) {
            buf.append("");
        }
        buf.append(")\n");
        buf.append(pad + "    " + flags + "))");
        // corrected April 15
        return buf.toString();
    }
}
