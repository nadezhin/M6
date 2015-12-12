
import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Field;
import com.sun.tools.classfile.Instruction;
import com.sun.tools.classfile.Method;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author dn146861
 */
public class jvm2acl2x2 {

    private static String defaultpath = "/var/local/";

    private static String usage
            = "Usage: jvm2acl2x [-a] [-g] [-d default-path-prefix] <output directory> <file names>* [<directory names>*]\n";

    private static Map<String, M6Class> allClasses = new LinkedHashMap<>();
    private static List<M6Class> sortedClasses;

    private static String[] collectFileNames(String[] args) {
        Vector files = new Vector();
        String[] reslt = null;
        try {
            for (int i = 0; i < args.length; i++) {
                File cur = new File(args[i]);
                if (cur.isDirectory()) {
                    String[] nextlevel = cur.list();
                    Arrays.sort(nextlevel);
                    for (int j = 0; j < nextlevel.length; j++) {
                        nextlevel[j] = (cur.getPath() + "/" + nextlevel[j]);
                    }
                    String[] names = collectFileNames(nextlevel);
                    for (int j = 0; j < names.length; j++) {
                        files.addElement(names[j]);
                    }
                } else {
                    String curn = cur.getPath();
                    int e = curn.lastIndexOf('.');
                    if (e != -1) {
                        String suffix = curn.substring(e + 1);
                        if (suffix.equals("class")) {
                            files.addElement(curn);
                        }
                    }
                }
            }

            reslt = new String[files.size()];
            for (int i = 0; i < files.size(); i++) {
                reslt[i] = (String) files.get(i);
            }
        } catch (Exception e) {
            System.out.println("File reading error!");
        }
        return reslt;
    }

    public static void main(String[] args) throws ConstantPoolException {

        int start = 0;
        String output;

        System.err.println("JVM --> ACL2  An automatic M6 state generator.");

        if (args.length <= 1) {
            System.err.println(usage);
            System.exit(0);
        }

        start = 0;

        if (args[start].equals("-5")) {
            start++;
        }

        if (args[start].equals("-d")) {
            start++;
            if (args.length - start < 3) {
                System.err.println(usage);
                System.exit(0);
            }
            defaultpath = args[start];
            start++;

            if (defaultpath.charAt(defaultpath.length() - 1) != '/') {
                defaultpath = defaultpath + "/";
            }
        }

        output = args[start];

        String[] input = new String[args.length - start - 1];
        for (int i = start; i < args.length - 1; i++) {
            input[i - start] = args[i + 1];
        }

        processFiles(output, collectFileNames(input));
    }

    static int ncpUtf8,
            ncpInteger,
            ncpFloat,
            ncpLong,
            ncpDouble,
            ncpClass,
            ncpString,
            ncpFieldref,
            ncpMethodref,
            ncpInterfaceMethodref,
            ncpNameAndType,
            ncpMethodHandle,
            ncpMethodType,
            ncpInvokeDynamic;

    static Set<String> setUtf8 = new TreeSet<>();
    static Set<Integer> setInteger = new TreeSet<>();
    static Set<Float> setFloat = new TreeSet<>();
    static Set<Long> setLong = new TreeSet<>();
    static Set<Double> setDouble = new TreeSet<>();
    static Set<String> setClass = new TreeSet<>();
    static Set<String> setString = new TreeSet<>();
    static Set<String> setFieldref = new TreeSet<>();
    static Set<String> setMethodref = new TreeSet<>();
    static Set<String> setInterfaceMethodref = new TreeSet<>();
    static Set<String> setNameAndType = new TreeSet<>();
    static Set<String> setMethodHandle = new TreeSet<>();
    static Set<String> setMethodType = new TreeSet<>();
    static Set<String> setInvokeDynamic = new TreeSet<>();

    private static Field searchField(M6Class cls, String name, String type) throws ConstantPoolException {
        for (Field f : cls.cf.fields) {
            if (f.getName(cls.cf.constant_pool).equals(name)
                    && f.descriptor.getValue(cls.cf.constant_pool).equals(type)) {
                return f;
            }
        }
        return null;
    }

    private static Set<String> searchFieldInInterfaces(String classname, String name, String type) throws ConstantPoolException {
        M6Class cls = allClasses.get(classname);
        if (searchField(cls, name, type) != null) {
            return Collections.singleton(cls.cf.getName() + "." + name + ":" + type);
        }
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < cls.cf.interfaces.length; i++) {
            Set<String> ri = searchFieldInInterfaces(cls.cf.getInterfaceName(i), name, type);
            result.addAll(ri);
        }
        return result;
    }

    private static Method searchMethod(M6Class cls, String name, String type) throws ConstantPoolException {
        if (cls == null) {
            cls = cls;
        }
        for (Method m : cls.cf.methods) {
            if (m.getName(cls.cf.constant_pool).equals(name)
                    && m.descriptor.getValue(cls.cf.constant_pool).equals(type)) {
                return m;
            }
        }
        return null;
    }

    private static Set<String> searchMethodInInterfaces(String classname, String name, String type) throws ConstantPoolException {
        M6Class cls = allClasses.get(classname);
        if (searchMethod(cls, name, type) != null) {
            return Collections.singleton(cls.cf.getName() + "." + name + ":" + type);
        }
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < cls.cf.interfaces.length; i++) {
            Set<String> ri = searchMethodInInterfaces(cls.cf.getInterfaceName(i), name, type);
            result.addAll(ri);
        }
        if (cls.cf.super_class != 0) {
            Set<String> ri = searchMethodInInterfaces(cls.cf.getSuperclassName(), name, type);
            result.addAll(ri);
        }
        return result;
    }

    private static final ConstantPool.Visitor<Exception, ConstantPool> cpVisitor = new ConstantPool.Visitor<Exception, ConstantPool>() {

        @Override
        public Exception visitClass(ConstantPool.CONSTANT_Class_info c, ConstantPool cp) {
            ncpClass++;
            try {
                setClass.add(c.getName());
                String baseName = c.getBaseName();
                if (baseName != null) {
                    M6Class cls = allClasses.get(c.getBaseName());
                    assert cls != null;
                }
            } catch (ConstantPoolException e) {
            }
            return null;
        }

        @Override
        public Exception visitDouble(ConstantPool.CONSTANT_Double_info c, ConstantPool cp) {
            ncpDouble++;
            setDouble.add(c.value);
            return null;
        }

        @Override
        public Exception visitFieldref(ConstantPool.CONSTANT_Fieldref_info c, ConstantPool cp) {
            ncpFieldref++;
            try {
                ConstantPool.CONSTANT_NameAndType_info nt = c.getNameAndTypeInfo();
                setFieldref.add(c.getClassName() + "." + nt.getName() + ":" + nt.getType());
                M6Class cls = allClasses.get(c.getClassName());
                boolean found = false;
                String name = nt.getName();
                String type = nt.getType();
                while (!found && cls != null) {
                    Field f = searchField(cls, name, type);
                    if (f != null) {
                        found = true;
                    } else {
                        cls = cls.cf.getName().equals("java/lang/Object")
                                ? null
                                : allClasses.get(cls.cf.getSuperclassName());
                    }
                }
                if (!found) {
                    Set<String> fields = searchFieldInInterfaces(c.getClassName(), name, type);
                    assert fields.size() == 1;
                }
            } catch (ConstantPoolException e) {
                return e;
            }
            return null;
        }

        @Override

        public Exception visitFloat(ConstantPool.CONSTANT_Float_info c, ConstantPool cp) {
            ncpFloat++;
            setFloat.add(c.value);
            return null;
        }

        @Override
        public Exception visitInteger(ConstantPool.CONSTANT_Integer_info c, ConstantPool cp) {
            ncpInteger++;
            setInteger.add(c.value);
            return null;
        }

        @Override
        public Exception visitInterfaceMethodref(ConstantPool.CONSTANT_InterfaceMethodref_info c, ConstantPool cp) {
            ncpInterfaceMethodref++;
            try {
                ConstantPool.CONSTANT_NameAndType_info nt = c.getNameAndTypeInfo();
                setInterfaceMethodref.add(c.getClassName() + "." + nt.getName() + ":" + nt.getType());
            } catch (ConstantPoolException e) {
                return e;
            }
            return null;
        }

        @Override
        public Exception visitInvokeDynamic(ConstantPool.CONSTANT_InvokeDynamic_info c, ConstantPool cp) {
            ncpInvokeDynamic++;
            try {
                ConstantPool.CONSTANT_NameAndType_info nt = c.getNameAndTypeInfo();
                setInvokeDynamic.add(nt.getName() + ":" + nt.getType() + " " + c.bootstrap_method_attr_index);
            } catch (ConstantPoolException e) {
                return e;
            }
            return null;
        }

        @Override
        public Exception visitLong(ConstantPool.CONSTANT_Long_info c, ConstantPool cp) {
            ncpLong++;
            setLong.add(c.value);
            return null;
        }

        @Override
        public Exception visitNameAndType(ConstantPool.CONSTANT_NameAndType_info c, ConstantPool cp) {
            ncpNameAndType++;
            try {
                setNameAndType.add(c.getName() + ":" + c.getType());
            } catch (ConstantPoolException e) {
                return e;
            }
            return null;
        }

        @Override
        public Exception visitMethodref(ConstantPool.CONSTANT_Methodref_info c, ConstantPool cp) {
            ncpMethodref++;
            try {
                ConstantPool.CONSTANT_NameAndType_info nt = c.getNameAndTypeInfo();
                setMethodref.add(c.getClassName() + "." + nt.getName() + ":" + nt.getType());
                M6Class cls = allClasses.get(c.getClassName());
                boolean found = false;
                String name = nt.getName();
                String type = nt.getType();
                while (!found && cls != null) {
                    Method m = searchMethod(cls, name, type);
                    if (m != null) {
                        found = true;
                    } else {
                        cls = cls.cf.getName().equals("java/lang/Object")
                                ? null
                                : allClasses.get(cls.cf.getSuperclassName());
                    }
                }
                if (!found) {
                    String classname = c.getClassName();
                    if (classname.charAt(0) == '[' && name.equals("clone") && type.equals("()Ljava/lang/Object;")) {
                    } else if (classname.equals("java/lang/invoke/MethodHandle")
                            && (name.equals("invoke") || name.equals("invokeBasic") || name.equals("invokeExact"))) {
                    } else {
                        Set<String> methods = searchMethodInInterfaces(c.getClassName(), name, type);
                        assert !methods.isEmpty();
                        if (methods.size() > 1) {
                            System.out.println(classname + "." + name + ":" + type + " --> " + methods);
                        }
                    }
                }
            } catch (ConstantPoolException e) {
                return e;
            }
            return null;
        }

        @Override
        public Exception visitMethodHandle(ConstantPool.CONSTANT_MethodHandle_info c, ConstantPool cp) {
            ncpMethodHandle++;
            try {
                ConstantPool.CPRefInfo ref = c.getCPRefInfo();
                ConstantPool.CONSTANT_NameAndType_info nt = ref.getNameAndTypeInfo();
                setMethodHandle.add(c.reference_kind + " " + ref.getClassName() + "." + nt.getName() + ":" + nt.getType());
            } catch (ConstantPoolException e) {
                return e;
            }
            return null;
        }

        @Override
        public Exception visitMethodType(ConstantPool.CONSTANT_MethodType_info c, ConstantPool cp) {
            ncpMethodType++;
            try {
                setMethodType.add(c.getType());
            } catch (ConstantPoolException e) {
                return e;
            }
            return null;
        }

        @Override
        public Exception visitString(ConstantPool.CONSTANT_String_info c, ConstantPool cp) {
            ncpString++;
            try {
                setString.add(c.getString());
            } catch (ConstantPoolException e) {
                return e;
            }
            return null;
        }

        @Override
        public Exception visitUtf8(ConstantPool.CONSTANT_Utf8_info c, ConstantPool cp) {
            ncpUtf8++;
            setUtf8.add(c.value);
            return null;
        }
    };

    private static void checkClasses() throws ConstantPoolException {
        for (M6Class cls : allClasses.values()) {
            if (cls.cf.getName().equals("java/lang/Object")) {
                assert cls.cf.super_class == 0;
            } else {
                String superclass = cls.cf.getSuperclassName();
                M6Class superCls = allClasses.get(superclass);
                assert superCls.cf.isClass();
                if (cls.cf.isInterface()) {
                    assert superCls.cf.getName().equals("java/lang/Object");
                }
                for (int i = 0; i < cls.cf.interfaces.length; i++) {
                    M6Class intrf = allClasses.get(cls.cf.getInterfaceName(i));
                    assert intrf.cf.isInterface();
                }
            }
            for (Field f : cls.cf.fields) {
                if (cls.cf.isInterface()) {
                    assert f.access_flags.is(AccessFlags.ACC_STATIC)
                            && f.access_flags.is(AccessFlags.ACC_PUBLIC);
                }
            }
            for (Method m : cls.cf.methods) {
                String name = m.getName(cls.cf.constant_pool);
                if (cls.cf.isInterface()) {
                    if (m.getName(cls.cf.constant_pool).equals("<clinit>")
                            && m.descriptor.getValue(cls.cf.constant_pool).equals("()V")) {
                    } else {
                    }
                }
            }
            for (ConstantPool.CPInfo cp : cls.cf.constant_pool.entries()) {
                Exception e = cp.accept(cpVisitor, cls.cf.constant_pool);
                assert e == null;
            }
        }
        System.out.println(allClasses.size() + " classes");
        System.out.println(setUtf8.size() + " " + ncpUtf8 + " Utf8");
        System.out.println(setInteger.size() + " " + ncpInteger + " Integer");
        System.out.println(setFloat.size() + " " + ncpFloat + " Float");
        System.out.println(setLong.size() + " " + ncpLong + " Long");
        System.out.println(setDouble.size() + " " + ncpDouble + " Double");
        System.out.println(setClass.size() + " " + ncpClass + " Class");
        System.out.println(setString.size() + " " + ncpString + " String");
        System.out.println(setFieldref.size() + " " + ncpFieldref + " Fieldref");
        System.out.println(setMethodref.size() + " " + ncpMethodref + " Methodref");
        System.out.println(setInterfaceMethodref.size() + " " + ncpInterfaceMethodref + " InterfaceMethodRef");
        System.out.println(setNameAndType.size() + " " + ncpNameAndType + " NameAndType");
        System.out.println(setMethodHandle.size() + " " + ncpMethodHandle + " MethodHandle");
        System.out.println(setMethodType.size() + " " + ncpMethodType + " MethodType");
        System.out.println(setInvokeDynamic.size() + " " + ncpInvokeDynamic + " InvokeDynamic");
    }

    private static int getLevel(String classname, Map<String, Integer> visited) {
        Integer l = visited.get(classname);
        if (l != null) {
            return l;
        }
        M6Class cls = allClasses.get(classname);
        if (cls.isClass()) {
            cls.level = getLevel(cls.getJvmSuperclassName(), visited);
        } else {
            for (String intrfname : cls.interfaceNames) {
                cls.level = Math.max(cls.level, getLevel(intrfname, visited));
            }
        }
        cls.level++;
        visited.put(classname, cls.level);
        return cls.level;
    }

    private static void assignLevels() {
        Map<String, Integer> visited = new HashMap<>();
        visited.put("java/lang/Object", 0);
        for (String classname : allClasses.keySet()) {
            getLevel(classname, visited);
        }
        sortedClasses = new ArrayList<>(allClasses.values());
        Collections.sort(sortedClasses);
    }

    private static void collectSuperclasses(String classname, Set<M6Class> visited) {
        if (visited.contains(classname)) {
            return;
        }
        M6Class cls = allClasses.get(classname);
        visited.add(cls);
        if (cls.cf.super_class != 0) {
            collectSuperclasses(cls.getJvmSuperclassName(), visited);
        }
        for (String interf : cls.interfaceNames) {
            collectSuperclasses(interf, visited);
        }
    }

    private static void assignSuperclasses() {
        for (M6Class cls : allClasses.values()) {
            Set<M6Class> sups = new HashSet<>();
            collectSuperclasses(cls.getJvmName(), sups);
            sups.remove(cls);
            List<M6Class> sorted = new ArrayList<>(sups);
            Collections.sort(sorted);
            M6Class[] sortedA = new M6Class[sorted.size()];
            int i = sortedA.length;
            for (M6Class c : sorted) {
                sortedA[--i] = c;
            }
            cls.allSuperclasses = sortedA;
        }
    }

    private static void showSorted() {
        for (M6Class cls : sortedClasses) {
            System.out.println(cls.level + " " + cls.getJavaName());
            System.out.print("  ");
            for (M6Class sup : cls.allSuperclasses) {
                System.out.print(" " + sup.getJvmName());
            }
            System.out.println();
        }
    }

    private static class CPref {

        final String classname;
        final String name;
        final String type;

        CPref(String classname, String name, String type) {
            this.classname = classname;
            this.name = name;
            this.type = type;
        }

        CPref(String methodRef) {
            int ind1 = methodRef.indexOf('.');
            int ind2 = methodRef.indexOf(':');
            classname = methodRef.substring(0, ind1);
            name = methodRef.substring(ind1 + 1, ind2);
            type = methodRef.substring(ind2 + 1);
        }

        @Override
        public String toString() {
            return classname + '.' + name + ':' + type;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CPref) {
                CPref that = (CPref) o;
                return this.classname.equals(that.classname)
                        && this.name.equals(that.name)
                        && this.type.equals(that.type);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 37 * hash + Objects.hashCode(this.classname);
            hash = 37 * hash + Objects.hashCode(this.name);
            hash = 37 * hash + Objects.hashCode(this.type);
            return hash;
        }
    }

    private static Method searchMethodref(CPref methodRef) throws ConstantPoolException {
        M6Class cls = allClasses.get(methodRef.classname);
        return searchMethod(cls, methodRef.name, methodRef.type);
    }

    private static CPref lookupMethodref(CPref methodRef) throws ConstantPoolException {
        Method m = searchMethodref(methodRef);
        if (m != null) {
            return methodRef;
        }
        for (M6Class sup : allClasses.get(methodRef.classname).allSuperclasses) {
            m = searchMethod(sup, methodRef.name, methodRef.type);
            if (m != null) {
                return new CPref(sup.getJvmName(), methodRef.name, methodRef.type);
            }
        }
        return null;
    }

    private static CPref filterMethodref(ConstantPool cp, int index) {
        try {
            ConstantPool.CPInfo info = cp.get(index);
            switch (info.getTag()) {
                case ConstantPool.CONSTANT_Methodref:
                case ConstantPool.CONSTANT_InterfaceMethodref:
                    ConstantPool.CPRefInfo infoRef
                            = (ConstantPool.CPRefInfo) info;
                    String classname = infoRef.getClassName();
                    ConstantPool.CONSTANT_NameAndType_info nt
                            = infoRef.getNameAndTypeInfo();
                    return new CPref(classname, nt.getName(), nt.getType());
            }
        } catch (ConstantPoolException e) {
        }
        return null;
    }

    private static Set<CPref> getCalls(CPref methodRef) throws ConstantPoolException {
        ConstantPool cp = allClasses.get(methodRef.classname).cf.constant_pool;
        Method m = searchMethodref(methodRef);
        Instruction.KindVisitor<CPref, ConstantPool> visitor = new Instruction.KindVisitor<CPref, ConstantPool>() {

            @Override
            public CPref visitNoOperands(Instruction i, ConstantPool cp) {
                return null;
            }

            @Override
            public CPref visitArrayType(Instruction i, Instruction.TypeKind tk, ConstantPool cp) {
                return null;
            }

            @Override
            public CPref visitBranch(Instruction i, int i1, ConstantPool cp) {
                return null;
            }

            @Override
            public CPref visitConstantPoolRef(Instruction i, int i1, ConstantPool cp) {
                return filterMethodref(cp, i1);
            }

            @Override
            public CPref visitConstantPoolRefAndValue(Instruction i, int i1, int i2, ConstantPool cp) {
                return filterMethodref(cp, i1);
            }

            @Override
            public CPref visitLocal(Instruction i, int i1, ConstantPool cp) {
                return null;
            }

            @Override
            public CPref visitLocalAndValue(Instruction i, int i1, int i2, ConstantPool cp) {
                return null;
            }

            @Override
            public CPref visitLookupSwitch(Instruction i, int i1, int i2, int[] ints, int[] ints1, ConstantPool cp) {
                return null;
            }

            @Override
            public CPref visitTableSwitch(Instruction i, int i1, int i2, int i3, int[] ints, ConstantPool cp) {
                return null;
            }

            @Override
            public CPref visitValue(Instruction i, int i1, ConstantPool cp) {
                return null;
            }

            @Override
            public CPref visitUnknown(Instruction i, ConstantPool cp) {
                return null;
            }
        };
        Code_attribute cai = (Code_attribute) m.attributes.get(Attribute.Code);
        if (cai != null) {
            Set<CPref> res = new LinkedHashSet<CPref>();
            for (Instruction instr : cai.getInstructions()) {
                CPref called = instr.accept(visitor, cp);
                if (called != null && !called.classname.startsWith("[")) {
                    res.add(lookupMethodref(called));
                }
            }
            return res;
        } else {
            return Collections.emptySet();
        }
    }

    private static Set<CPref> searchAllSubs(Collection<CPref> from) throws ConstantPoolException {
        Set<CPref> queue = new LinkedHashSet<CPref>(from);
        Set<CPref> res = new LinkedHashSet<CPref>();
        while (!queue.isEmpty()) {
            Iterator<CPref> it = queue.iterator();
            CPref first = it.next();
            it.remove();
            boolean ok = res.add(first);
            assert ok;
            Set<CPref> called = getCalls(first);
            for (CPref c : called) {
                if (!res.contains(c)) {
                    queue.add(c);
                }
            }
        }
        return res;
    }

    private static void showCalls(String... from) throws ConstantPoolException {
        List<CPref> fromList = new ArrayList<>();
        for (String s : from) {
            CPref c = lookupMethodref(new CPref(s));
            assert c != null;
            System.out.println(c);
            fromList.add(c);
        }
        System.out.println("-->");
        Set<CPref> calls = searchAllSubs(fromList);
        for (CPref c : calls) {
            if (fromList.indexOf(c) == -1) {
                System.out.println(" " + c);
            }
        }
        System.out.println(calls.size() + " total");
        System.out.println("=================================================================");
    }

    private static void showCallsOfClasses(String... classnames) throws ConstantPoolException {
        List<String> methods = new ArrayList<>();
        for (String classname : classnames) {
            M6Class cls = allClasses.get(classname);
            ConstantPool cp = cls.cf.constant_pool;
            for (Method m : cls.cf.methods) {
                methods.add(classname + "." + m.getName(cp) + ":" + m.descriptor.getValue(cp));
            }
        }
        showCalls(methods.toArray(new String[methods.size()]));
    }

    private static void processFiles(String tname, String[] args) throws ConstantPoolException {
        String tablename, tablepath;

        if (tname.lastIndexOf('/') != -1) {
            tablename = tname.substring(tname.lastIndexOf('/') + 1);
            tablepath = tname.substring(0, tname.lastIndexOf('/'));
        } else {
            tablename = tname;
            tablepath = tname;
        }

        /* We parse each of the classfiles in order */
        for (int i = 0; i < args.length; i++) {
            try {
                M6Class cls = new M6Class(args[i]);
                String name = cls.cf.getName();
                assert args[i].endsWith(name + ".class");
                if (allClasses.put(name, cls) != null) {
                    assert false : name;
                }
            } catch (java.lang.Exception e) {
                System.err.println("Could not open file " + args[i] + " " + e);
                System.exit(0);
            }
        }
        assignLevels();
        assignSuperclasses();
        // checkClasses();
        // showSorted();
        showCalls("java/math/MutableBigInteger.inverseMod32:(I)I");

        showCalls("java/lang/Math.round:(F)I");

        showCalls("java/lang/Math.pow:(DD)D");
        showCalls("java/lang/Math.hypot:(DD)D");
        showCalls("java/lang/Math.cbrt:(D)D");

        showCalls(
                "java/lang/Math.pow:(DD)D",
                "java/lang/Math.hypot:(DD)D",
                "java/lang/Math.cbrt:(D)D");

        showCalls("java/math/BigInteger.modPow:(Ljava/math/BigInteger;Ljava/math/BigInteger;)Ljava/math/BigInteger;");
        showCalls("java/util/Formatter$FormatSpecifier.print:(Ljava/lang/StringBuilder;DLjava/util/Locale;Ljava/util/Formatter$Flags;CIZ)V");
        showCalls("java/math/BigDecimal.multiply:(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
        showCalls("java/math/BigDecimal.divide:(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
        showCalls("java/math/BigDecimal.multiply:(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;",
                "java/math/BigDecimal.divide:(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
        
        showCalls("sun/misc/FDBigInteger.rightInplaceSub:(Lsun/misc/FDBigInteger;)Lsun/misc/FDBigInteger;");

        showCallsOfClasses("java/lang/Character");
        showCallsOfClasses("java/lang/String");
        showCallsOfClasses("java/lang/Character", "java/lang/String");

        showCallsOfClasses("java/lang/Math");
        showCallsOfClasses("java/lang/StrictMath");
        showCallsOfClasses("java/lang/Math", "java/lang/StrictMath");
        
        showCallsOfClasses("java/math/BigDecimal");
        showCallsOfClasses("java/math/BigInteger");
        showCallsOfClasses("sun/misc/FDBigInteger");
    }
}
