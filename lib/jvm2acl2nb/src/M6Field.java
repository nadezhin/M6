
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Attributes;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ConstantValue_attribute;
import com.sun.tools.classfile.DescriptorException;
import com.sun.tools.classfile.Field;
import java.util.List;

public class M6Field {

    private final String name;
    private final String desc;
    private final String type;
    private final M6AccessFlags accessflags;
    private final ClassFile cf;
    final Field field;
    private int index; // index into the constant pool

    public M6Field(ClassFile cf, Field f) throws ConstantPoolException, DescriptorException {
        this.cf = cf;
        this.field = f;
        name = f.getName(cf.constant_pool);
        desc = f.descriptor.getValue(cf.constant_pool);
        type = ACL2utils.JavaTypeStrToACL2TypeStr(f.descriptor.getFieldType(cf.constant_pool));
        accessflags = new M6AccessFlags(f.access_flags);
        index = -1;
    }

    public void processField(List cp) throws ConstantPoolException {
        // need to add entries to constant_pool,
        // those string values.

        index = -1;
        Attributes al = field.attributes;
        if (al == null) {
            return;
        }

        ConstantValue_attribute va = (ConstantValue_attribute) al.get(Attribute.ConstantValue);
        if (va == null) {
            return;
        }

        ConstantPool.CPInfo info = cf.constant_pool.get(va.constantvalue_index);
        int tag = info.getTag();

        if (tag == ConstantPool.CONSTANT_Integer) {
            ConstantPool.CONSTANT_Integer_info infoInteger = (ConstantPool.CONSTANT_Integer_info) info;
            Integer val = Integer.valueOf(infoInteger.value);
            System.out.println(val);
            if (cp.contains(val)) {
                index = cp.indexOf(val);
            } else {
                cp.add(val);
                index = cp.size() - 1;
            }
        } else if (tag == ConstantPool.CONSTANT_Float) {
            ConstantPool.CONSTANT_Float_info infoFloat = (ConstantPool.CONSTANT_Float_info) info;
            Float val = Float.valueOf(infoFloat.value);
            System.out.println(val);
            if (cp.contains(val)) {
                index = cp.indexOf(val);
            } else {
                cp.add(val);
                index = cp.size() - 1;
            }
        } else if (tag == ConstantPool.CONSTANT_Double) {
            ConstantPool.CONSTANT_Double_info infoDouble = (ConstantPool.CONSTANT_Double_info) info;
            Double val = Double.valueOf(infoDouble.value);
            System.out.println(val);
            if (cp.contains(val)) {
                index = cp.indexOf(val);
            } else {
                cp.add(val);
                index = cp.size() - 1;
            }
        } else if (tag == ConstantPool.CONSTANT_Long) {
            ConstantPool.CONSTANT_Long_info infoLong = (ConstantPool.CONSTANT_Long_info) info;
            Long val = Long.valueOf(infoLong.value);
            System.out.println(val);
            if (cp.contains(val)) {
                index = cp.indexOf(val);
            } else {
                cp.add(val);
                index = cp.size() - 1;
            }
        } else if (tag == ConstantPool.CONSTANT_String) {
            ConstantPool.CONSTANT_String_info infoString = (ConstantPool.CONSTANT_String_info) info;
            String str = infoString.getString();
            System.out.println(str);
            M6Class.StringRef val = new M6Class.StringRef(str);
            if (cp.contains(val)) {
                index = cp.indexOf(val);
            } else {
                cp.add(val);
                index = cp.size() - 1;
            }
        } else {
            System.out.println("Incompatible ConstantPool ConstantValue type!");
            System.exit(-1);
        }
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
