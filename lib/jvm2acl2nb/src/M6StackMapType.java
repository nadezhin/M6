
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.StackMapTable_attribute;

public class M6StackMapType {

    private byte type;
    private String type_name;
    private static String[] type_names
            = {"topx", "int", "float", "double",
                "long", "null", "uninitializedThis", "cpindex",
                "uninitilizedoffset", "returnaddress"};

    public M6StackMapType(StackMapTable_attribute.verification_type_info typeInfo, ConstantPool cp)
            throws ConstantPoolException {
        type = (byte) typeInfo.tag;

        switch (type) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
                type_name = type_names[type];
                break;
            case 7: {
                StackMapTable_attribute.Object_variable_info typeInfoObject
                        = (StackMapTable_attribute.Object_variable_info) typeInfo;
                int index = typeInfoObject.cpool_index;
                ConstantPool.CONSTANT_Class_info info = cp.getClassInfo(index);
                type_name = ACL2utils.ClassInfoToACL2TypeStr(info.getName());
            }
            break;
            case 8: {
                StackMapTable_attribute.Uninitialized_variable_info typeInfoUninitialized
                        = (StackMapTable_attribute.Uninitialized_variable_info) typeInfo;
                int offset = typeInfoUninitialized.offset;
                type_name = "(uninitialized " + offset + ")";
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

    private M6StackMapType(String n) {
        type_name = n;
    }

    public static M6StackMapType getTopType() {
        return new M6StackMapType("topx");
    }

    public String toString() {
        return type_name;
    }
}
