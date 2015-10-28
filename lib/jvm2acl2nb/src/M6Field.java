
import com.sun.tools.classfile.ClassFile;
import java.util.Vector;

public class M6Field {

    private String name;
    private String desc;
    private String type;
    private M6AccessFlags accessflags;
    private com.ibm.toad.cfparse.FieldInfo field;
    private int index; // index into the constant pool

    public M6Field(com.ibm.toad.cfparse.ClassFile cp0, ClassFile cp, com.ibm.toad.cfparse.FieldInfo f) {
        field = f;
        name = f.getName();
        desc = f.getDesc();
        type = ACL2utils.JavaTypeStrToACL2TypeStr(f.getType());
        accessflags = new M6AccessFlags(f.getAccess());
        index = -1;
    }

    public void processField(Vector cp) {
        // need to add entries to constant_pool,
        // those string values.

        index = -1;
        com.ibm.toad.cfparse.attributes.AttrInfoList al = field.getAttrs();
        if (al == null) {
            return;
        }

        com.ibm.toad.cfparse.attributes.ConstantValueAttrInfo va = (com.ibm.toad.cfparse.attributes.ConstantValueAttrInfo) al.get("ConstantValue");
        if (va == null) {
            return;
        }

        int entType = va.getType();
        System.out.println(va.get());
        String value = va.get();

        if (entType == com.ibm.toad.cfparse.ConstantPool.CONSTANT_Integer) {
            Integer val = Integer.valueOf(value);
            if (cp.contains(val)) {
                index = cp.indexOf(val);
            } else {
                cp.addElement(val);
                index = cp.size() - 1;
            }
        } else if (entType == com.ibm.toad.cfparse.ConstantPool.CONSTANT_Float) {
            Float val = Float.valueOf(value);
            if (cp.contains(val)) {
                index = cp.indexOf(val);
            } else {
                cp.addElement(val);
                index = cp.size() - 1;
            }
        } else if (entType == com.ibm.toad.cfparse.ConstantPool.CONSTANT_Double) {
            Double val = Double.valueOf(value);
            if (cp.contains(val)) {
                index = cp.indexOf(val);
            } else {
                cp.addElement(val);
                index = cp.size() - 1;
            }
        } else if (entType == com.ibm.toad.cfparse.ConstantPool.CONSTANT_Long) {
            Long val = Long.valueOf(value);
            if (cp.contains(val)) {
                index = cp.indexOf(val);
            } else {
                cp.addElement(val);
                index = cp.size() - 1;
            }
        } else if (entType == com.ibm.toad.cfparse.ConstantPool.CONSTANT_String) {
            M6Class.StringRef val = new M6Class.StringRef(value, value);
            if (cp.contains(val)) {
                index = cp.indexOf(val);
            } else {
                cp.addElement(val);
                index = cp.size() - 1;
            }
        } else {
            System.out.println("Incompatible ConstantPool ConstantValue type!");
            System.exit(-1);
        }

        field = null; // to release some memory..
    }

    public M6AccessFlags getAccessFlags() {
        return accessflags;
    }

    /**
     * returns a pretty-printed String representing this field.
     *
     * @param lmargin	The number of spaces of indentation to place before the
     * output
     * @param target Target JVM model
     * @return	A pretty-printed String representing this field.
     */
    public String toString(int lmargin, Target target) {
        StringBuffer padb = new StringBuffer();
        for (int i = 0; i < lmargin; i++) {
            padb.append(" ");
        }
        switch (target) {
            case M5:
                if (ACL2utils.NAME_AND_TYPE) {
                    padb.append("\"" + name + ":" + desc + "\"");
                } else {
                    padb.append("\"" + name + "\"");
                }
                break;
            case M6:
                padb.append("(field \"" + name + "\" " + type + " " + accessflags.toString(0) + " " + index + ")");
                break;
        }
        return padb.toString();
    }
}
