
import com.sun.tools.classfile.AccessFlags;

public class ACL2utils {

    public static final boolean NAME_AND_TYPE = true;

    public static String descToACL2TypeStr(String descriptor) {
        switch (descriptor.charAt(0)) {
            case '[':
                return "(array " + descToACL2TypeStr(descriptor.substring(1)) + ")";
            case 'V':
                assert descriptor.length() == 1;
                return "void";
            case 'Z':
                assert descriptor.length() == 1;
                return "boolean";
            case 'B':
                assert descriptor.length() == 1;
                return "byte";
            case 'C':
                assert descriptor.length() == 1;
                return "char";
            case 'S':
                assert descriptor.length() == 1;
                return "short";
            case 'I':
                assert descriptor.length() == 1;
                return "int";
            case 'J':
                assert descriptor.length() == 1;
                return "long";
            case 'F':
                assert descriptor.length() == 1;
                return "float";
            case 'D':
                assert descriptor.length() == 1;
                return "double";
            case 'L':
                assert descriptor.endsWith(";");
                return "(class \"" +  descriptor.substring(1, descriptor.length() - 1).replace('/', '.') + "\")";
            default:
                throw new AssertionError();
        }
    }

    public static String classNameToACL2TypeStr(String descriptor) {
        return descriptor.charAt(0) == '['
                ? descToACL2TypeStr(descriptor)
                : "(class \"" + descriptor.replace('/', '.') + "\")";
    }

    public static String accessFlagsToString(AccessFlags af) {
        StringBuilder flags = new StringBuilder("(accessflags ");
        if (af.is(AccessFlags.ACC_ABSTRACT)) {
            flags.append(" *abstract* ");
        }
        if (!af.is(AccessFlags.ACC_INTERFACE)) {
            flags.append(" *class* ");
        }
        if (af.is(AccessFlags.ACC_FINAL)) {
            flags.append(" *final* ");
        }
        if (af.is(AccessFlags.ACC_INTERFACE)) {
            flags.append(" *interface* ");
        }
        if (af.is(AccessFlags.ACC_NATIVE)) {
            flags.append(" *native* ");
        }
        if (af.is(AccessFlags.ACC_PRIVATE)) {
            flags.append(" *private* ");
        }
        if (af.is(AccessFlags.ACC_PROTECTED)) {
            flags.append(" *protected* ");
        }
        if (af.is(AccessFlags.ACC_PUBLIC)) {
            flags.append(" *public* ");
        }
        if (af.is(AccessFlags.ACC_STATIC)) {
            flags.append(" *static* ");
        }
        if (af.is(AccessFlags.ACC_STRICT)) {
            flags.append(" *strict* ");
        }
        // super and synchronized are the same bit
        if (af.is(AccessFlags.ACC_SUPER)) {
            flags.append(" *super* ");
        }
        if (af.is(AccessFlags.ACC_SYNCHRONIZED)) {
            flags.append(" *synchronized* ");
        }
        if (af.is(AccessFlags.ACC_TRANSIENT)) {
            flags.append(" *transient* ");
        }
        if (af.is(AccessFlags.ACC_VOLATILE)) {
            flags.append(" *volatile* ");
        }
        flags.append(")");
        //System.out.print("FLAGS" + flags);
        return flags.toString();
    }
}
