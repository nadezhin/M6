
class M6AccessFlags {

    int f;
    StringBuffer flags = null;

    public M6AccessFlags(int f) {
        this.f = f;
        flags = new StringBuffer("(accessflags ");
        if (com.ibm.toad.cfparse.utils.Access.isAbstract(f)) {
            flags.append(" *abstract* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isClass(f)) {
            flags.append(" *class* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isFinal(f)) {
            flags.append(" *final* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isInterface(f)) {
            flags.append(" *interface* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isNative(f)) {
            flags.append(" *native* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isPrivate(f)) {
            flags.append(" *private* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isProtected(f)) {
            flags.append(" *protected* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isPublic(f)) {
            flags.append(" *public* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isStatic(f)) {
            flags.append(" *static* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isStrict(f)) {
            flags.append(" *strict* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isSuper(f)) {
            flags.append(" *super* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isSynchronized(f)) {
            flags.append(" *synchronized* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isTransient(f)) {
            flags.append(" *transient* ");
        }
        if (com.ibm.toad.cfparse.utils.Access.isVolatile(f)) {
            flags.append(" *volatile* ");
        }
        flags.append(")");
        //System.out.print("FLAGS" + flags);
    }

    public String toString(int lmargin) {
        StringBuffer padb = new StringBuffer();
        for (int i = 0; i < lmargin; i++) {
            padb.append(" ");
        }
        padb.append(flags.toString());
        return padb.toString();
    }
}
