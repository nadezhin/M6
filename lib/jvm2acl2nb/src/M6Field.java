
import com.sun.tools.classfile.AccessFlags;
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

    private final M6Class cls;
    private final Field field;
    private final String name;
    private final String desc;
    int index; // index into the constant pool

    public M6Field(M6Class cls, Field f) throws ConstantPoolException, DescriptorException {
        this.cls = cls;
        ClassFile cf = cls.cf;
        this.field = f;
        name = field.getName(cf.constant_pool);
        desc = field.descriptor.getValue(cf.constant_pool);
        index = -1;
    }

    public String getName() {
        return name;
    }

    public String getDesc() throws ConstantPoolException {
        return desc;
    }

    public AccessFlags getAccessFlags() {
        return field.access_flags;
    }

    public boolean isStatic() {
        return field.access_flags.is(AccessFlags.ACC_STATIC);
    }

    public void processField(List<M6Class.CPEntry> cp) throws ConstantPoolException {
        // need to add entries to constant_pool,
        // those string values.

        index = -1;

        ConstantValue_attribute va
                = (ConstantValue_attribute) field.attributes.get(Attribute.ConstantValue);
        if (va == null) {
            return;
        }

        M6Class.CPEntry cpe = cls.constantPool[va.constantvalue_index];
        if (cp.contains(cpe)) {
            index = cp.indexOf(cpe);
        } else {
            cp.add(cpe);
            index = cp.size() - 1;
        }
    }
}
