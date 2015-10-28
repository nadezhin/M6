
import java.io.*;

public class M6StackMapAttrInfo {

    private int entryCount;
    private M6StackMapFrame[] maps;
    private byte[] bytes;
    private DataInputStream file;

    public M6StackMapAttrInfo(com.ibm.toad.cfparse.attributes.AttrInfo unknown, com.ibm.toad.cfparse.ConstantPool cp, com.ibm.toad.cfparse.instruction.ImmutableCodeSegment imc, int fs) throws IOException {
        bytes = ((com.ibm.toad.cfparse.attributes.UnknownAttrInfo) unknown).get();
        file = new DataInputStream(new ByteArrayInputStream(bytes));

        entryCount = file.readUnsignedShort();
        maps = new M6StackMapFrame[entryCount];

        for (int i = 0; i < entryCount; i++) {
            maps[i] = new M6StackMapFrame(file, cp, imc, fs);
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
