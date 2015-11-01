
import com.sun.tools.classfile.AccessFlags;

class M6AccessFlags {

    StringBuffer flags = null;

    public M6AccessFlags(AccessFlags af) {
        flags = new StringBuffer("(accessflags ");
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
    }

    public String toString(int lmargin) {
        StringBuilder padb = new StringBuilder();
        for (int i = 0; i < lmargin; i++) {
            padb.append(" ");
        }
        padb.append(flags.toString());
        return padb.toString();
    }
}
