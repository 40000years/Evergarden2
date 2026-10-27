import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

/** Short-lived compiler process; the process owns and releases all archive handles.
 * Avoids JDK25 Windows ZipFS failures in javac's file-manager close path.
 * Actual compilation diagnostics and the task's success result remain authoritative.
 */
public final class CompileJava {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Expected compiler argument file");
        var options=new ArrayList<String>();var sources=new ArrayList<String>();
        for(String line:Files.readAllLines(Path.of(args[0]),StandardCharsets.UTF_8)){
            String value=line.strip();if(value.isEmpty())continue;
            if(value.startsWith("\"")&&value.endsWith("\""))value=value.substring(1,value.length()-1);
            if(value.endsWith(".java"))sources.add(value);else options.add(value);
        }
        var compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("A full JDK is required");
        var manager=compiler.getStandardFileManager(null,Locale.ROOT,StandardCharsets.UTF_8);
        boolean passed=compiler.getTask(null,manager,null,options,null,manager.getJavaFileObjectsFromStrings(sources)).call();
        System.exit(passed?0:1);
    }
}
