
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.StackMap_attribute;

public class M6StackMapAttrInfo {

    private int entryCount;
    private M6StackMapFrame[] maps;

    public M6StackMapAttrInfo(StackMap_attribute sm, ConstantPool cp, int fs) throws ConstantPoolException {

        entryCount = sm.number_of_entries;
        maps = new M6StackMapFrame[entryCount];

        for (int i = 0; i < entryCount; i++) {
            StackMap_attribute.stack_map_frame smf = (StackMap_attribute.stack_map_frame) sm.entries[i];
            maps[i] = new M6StackMapFrame(smf, cp, fs);
        }
    }

    public M6StackMapFrame[] getStackMapFrames() {
        return maps;
    }

    public String toString(int lmargin) {
        StringBuffer pdb = new StringBuffer();
        String pad;
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < lmargin; i++) {
            pdb.append(" ");
        }
        pad = pdb.toString();

        buf.append(pad + "(StackMap \n");
        for (int i = 0; i < entryCount; i++) {
            buf.append(maps[i].toString(lmargin + 5));
            if (i < entryCount - 1) {
                buf.append("\n");
            }
        }
        buf.append(")");
        return buf.toString();
    }
}
