
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;


public class M6ExceptionHandler {

    private int start_pc;
    private int end_pc;
    private int handler_pc;
    private String type;

    public M6ExceptionHandler(Code_attribute.Exception_data n, ConstantPool cp) throws ConstantPoolException {
        start_pc = n.start_pc;
        end_pc = n.end_pc;
        handler_pc = n.handler_pc;
        String tmp = n.catch_type != 0 ? cp.getClassInfo(n.catch_type).getName().replace('/','.') : "java.lang.Throwable";
        type = ACL2utils.JavaTypeStrToACL2TypeStr(tmp);
    }

    public String toString() {
        return "(handler " + start_pc + " " + end_pc + "  " + handler_pc + " " + type + ")";
    }
}
