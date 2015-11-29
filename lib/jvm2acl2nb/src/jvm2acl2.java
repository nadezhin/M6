
import com.sun.tools.classfile.ConstantPoolException;
import java.util.*;
import java.io.*;

public class jvm2acl2 {

    private static int level = 0;

    private static String[] collectFileNames(String[] args) {
        Vector files = new Vector();
        String[] reslt = null;
        try {
            for (int i = 0; i < args.length; i++) {
                File cur = new File(args[i]);
                System.out.println(args[i]);
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
                    files.addElement(cur.getPath());
                }
            }

            reslt = new String[files.size()];
            for (int i = 0; i < files.size(); i++) {
                reslt[i] = (String) files.get(i);
                System.out.println(reslt[i]);
            }
        } catch (Exception e) {
            System.out.println("File reading error!");
        }
        return reslt;
    }

    public static void main(String[] args) throws ConstantPoolException {

        System.err.println("JVM --> ACL2  An automatic M6 state generator.");
        if (args.length <= 1) {
            System.err.println("Usage: jvm2acl2 <output> <file names>* [<directory names>*]\n");
            System.exit(0);
        }

        String[] input = new String[args.length - 1];
        for (int i = 0; i < args.length - 1; i++) {
            input[i] = args[i + 1];
        }

        processFiles(args[0], collectFileNames(input), Target.M5);
    }

    private static void processFiles(String tablename, String[] args, Target target)
            throws ConstantPoolException {
        M6Class[] classes = new M6Class[args.length];

        /* We parse each of the classfiles in order */
        for (int i = 0; i < args.length; i++) {
            try {
                classes[i] = new M6Class(args[i]);
                classes[i].processClassFile(target);
            } catch (java.lang.Exception e) {
                System.err.println("Could not open file " + args[i] + " " + e);
                System.exit(0);
            }
        }

        StringBuffer ctBuf = new StringBuffer();
        StringBuffer ctBuf2 = new StringBuffer();
        StringBuffer mapName2P = new StringBuffer();

        switch (target) {
            case M5:
                ctBuf2.append("(defconst *" + tablename + "-class-table-in-tagged-form*\n");
                ctBuf2.append("  (make-class-def\n");
                ctBuf2.append("    (list\n");
                break;
            case M6:
                ctBuf2.append("(defconst *" + tablename + "-class-table*\n");
                ctBuf2.append("  (make-static-class-decls \n");
                break;
        }
        mapName2P.append("(defconst *package-name-map* \n");
        /* The class table */
        for (int i = 0; i < classes.length; i++) {

            String classname = classes[i].getJavaName();
            switch (target) {
                case M5:
                    ctBuf.append("(defconst *" + classname + "-class-decl*\n");
                    ctBuf.append(classes[i].toString(4, target));
                    ctBuf.append(")\n\n");
                    ctBuf2.append("     *" + classname + "-class-decl*");
                    break;
                case M6:
                    ctBuf.append("(defconst *" + classname + "*\n");
                    ctBuf.append(" (make-class-def\n");
                    ctBuf.append("  ");
                    ctBuf.append(classes[i].toString(4, target));
                    ctBuf.append("))\n\n");
                    ctBuf2.append("   *" + classname + "*");
                    break;
            }

            int offset = classname.lastIndexOf('.');
            String packagename = null;
            if (offset == -1) {
                packagename = "";
            } else {
                packagename = classname.substring(0, offset);
            }

            mapName2P.append("  (\"" + classname + "\" . \"" + packagename + "\")");

            if (i < classes.length - 1) {
                mapName2P.append("\n");
                ctBuf2.append("\n");
            }
        }
        switch (target) {
            case M5:
                ctBuf2.append(")))\n");
                break;
            case M6:
                ctBuf2.append("))\n");
                break;
        }
        mapName2P.append(")");

        /* We now output the state to stdout */
        System.out.println(ctBuf.toString());
        System.out.println(ctBuf2.toString());
        System.out.println(mapName2P.toString());

        /* And to a file called "tablename-state.lisp" */
        System.out.println("");

        String outFileName = null;
        switch (target) {
            case M5:
                outFileName = tablename + "-classtableM5.lisp";
                break;
            case M6:
                outFileName = tablename + "-classtable.lisp";
        }
        System.out.print("Writing state to file " + outFileName + "...");
        try {
            FileWriter outfile = new FileWriter(outFileName);

            outfile.write("; " + outFileName + "\n");
            outfile.write("; Automatically generated by jvm2acl2 ");

            Calendar rightNow = Calendar.getInstance();

            outfile.write("on " + rightNow.getTime().toString() + ".\n;\n\n");

            switch (target) {
                case M5:
                    outfile.write("(in-package \"M5\")\n");
                    outfile.write("(include-book \"models/jvm/m5/utilities\" :dir :system)\n\n");
                    break;
                case M6:
                    break;
            }

            outfile.write(ctBuf.toString() + "\n");
            outfile.write(ctBuf2.toString() + "\n");
            switch (target) {
                case M5:
                    break;
                case M6:
                    outfile.write(mapName2P.toString() + "\n");
                    break;
            }
            outfile.write("\n");
            outfile.flush();
            outfile.close();

            System.out.println("done!");

        } catch (IOException e) {
            System.out.println("\nError writing to " + tablename + "-classtable.lisp");
            System.exit(1);
        }
    }
}
