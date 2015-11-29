
import java.util.*;
import java.io.*;

public class jvm2acl2a {

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
                System.out.println(reslt[i]);
            }
        } catch (Exception e) {
            System.out.println("File reading error!");
        }
        return reslt;
    }

    public static void main(String[] args) {
        System.err.println("JVM --> ACL2  An automatic M6 state generator.");
        if (args.length <= 1) {
            System.err.println("Usage: jvm2acl2 <output> <file names>* [<directory names>*]\n");
            System.exit(0);
        }

        String[] input = new String[args.length - 1];
        for (int i = 0; i < args.length - 1; i++) {
            input[i] = args[i + 1];
        }

        processFiles(args[0], collectFileNames(input));
    }

    private static String processOneFile(String classfn, String pathn, StringBuffer table) {
        try {
            M6Class curClass = new M6Class(classfn);
            curClass.processClassFile(Target.M6);

            StringBuffer ctBuf = new StringBuffer();
            String classname = curClass.getJavaName();

            ctBuf.append("(defconst *" + classname + "*\n");
            ctBuf.append(" (make-class-def\n");
            ctBuf.append("  ");
            ctBuf.append(curClass.toString(4, Target.M6));
            ctBuf.append("))\n\n");

            writeToFile(pathn + "/*" + classname + "*.lisp", ctBuf);
            return classname;
        } catch (Exception e) {
            System.err.println(e);
            System.exit(-1);
        }
        return null;
    }

    private static void processFiles(String tname, String[] args) {
        StringBuffer ctBuf = new StringBuffer();
        StringBuffer ctBuf2 = new StringBuffer();

        String tablename = tname.substring(tname.lastIndexOf('/') + 1);
        String tablepath = tname.substring(0, tname.lastIndexOf('/'));
        ctBuf.append(";; load all class description as  Lisp constants\n");

        ctBuf2.append("(defconst *" + tablename + "-class-table*\n");
        ctBuf2.append("  (make-class-table \n");

        /* We parse each of the classfiles in order */
        for (int i = 0; i < args.length; i++) {
            try {
                String classname = processOneFile(args[i], tablepath, ctBuf2);
                ctBuf.append("(ld \"*" + classname + "*.lisp\")\n");
                ctBuf2.append("   *" + classname + "*");
                if (i < args.length - 1) {
                    ctBuf2.append("\n");
                }
            } catch (java.lang.Exception e) {
                System.err.println("Could not open file " + args[0] + " " + e);
                System.exit(0);
            }
        }
        ctBuf2.append("))\n\n");
        ctBuf.append("\n\n" + ctBuf2);
        writeToFile(tablepath + '/' + tablename + "-class-table.lisp", ctBuf);
    }

    private static void writeToFile(String fn, StringBuffer ctBuf) {
        System.out.println("Writing state to file " + fn);
        try {
            FileWriter outfile = new FileWriter("/var/local/hbl/classes/" + fn);

            outfile.write("; " + fn + "\n");
            outfile.write("; Automatically generated by jvm2acl2 ");

            Calendar rightNow = Calendar.getInstance();

            outfile.write("on " + rightNow.getTime().toString() + ".\n;\n\n");

            outfile.write(ctBuf.toString() + "\n");
            outfile.write("\n");
            outfile.flush();
            outfile.close();
            System.out.println("done!");
        } catch (IOException e) {
            System.out.println("\nError writing to " + fn);
            System.exit(1);
        }
    }
}
