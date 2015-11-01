
import java.util.Vector;

public class ACL2utils {

    public static final boolean NAME_AND_TYPE = true;

    public static String ClassInfoToACL2TypeStr(String descriptor) {
        //jst do simple changes
        // 1. enclose class name with " ", change into (class "<classname>") form
        // 2. change <anystr>[] into (array trans(<anystr>))

        switch (descriptor.charAt(0)) {
            case '[':
                return "(array " + ClassInfoToACL2TypeStr(descriptor.substring(1)) + ")";
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
                return ClassInfoToACL2TypeStr(descriptor.substring(1, descriptor.length() - 1));
            default:
                return "(class \"" + descriptor.replace('/', '.') + "\")";
        }
    }

    public static String JavaTypeStrToACL2TypeStr(String jtype) {
        //jst do simple changes
        // 1. enclose class name with " ", change into (class "<classname>") form
        // 2. change <anystr>[] into (array trans(<anystr>))

        int i = jtype.lastIndexOf("[]");
        if (i != -1) {
            return "(array " + JavaTypeStrToACL2TypeStr(jtype.substring(0, i)) + ")";
        } else if (isPrimitiveType(jtype)) {
            return jtype;
        } else {
            return "(class \"" + jtype + "\")";
        }
    }

    private static boolean isPrimitiveType(String jtype) {
        if (jtype.equals("byte") || jtype.equals("int")
                || jtype.equals("char") || jtype.equals("double")
                || jtype.equals("float") || jtype.equals("long")
                || jtype.equals("boolean") || jtype.equals("short")
                || jtype.equals("void")) {
            return true;
        }
        return false;
    }

    public static int typeSize(String typename) {
        if (typename.equals("long") || (typename.equals("double"))) {
            return 2;
        } else {
            return 1;
        }
    }

    public static M6StackMapType[] expandTypeList(M6StackMapType[] l) {
        Vector nl = new Vector();
        M6StackMapType top = M6StackMapType.getTopType();

        for (int i = 0; i < l.length; i++) {
            nl.addElement(l[i]);
            if (typeSize(l[i].toString()) == 2) {
                nl.addElement(top);
            }
        }
        Object[] ll = nl.toArray();
        M6StackMapType[] ret = new M6StackMapType[ll.length];
        for (int i = 0; i < ll.length; i++) {
            ret[i] = (M6StackMapType) ll[i];
        }
        return ret;
    }

    public static M6StackMapType[] expandToLength(M6StackMapType[] l, int len) {
        M6StackMapType[] nl = new M6StackMapType[len];

        int i = 0;
        try {
            for (; i < l.length; i++) {
                nl[i] = l[i];
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("M6StackMapType!");
        }
        for (; i < len; i++) {
            nl[i] = M6StackMapType.getTopType();
        }
        return nl;
    }
}
