import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.TreeGenerators;
import com.github.gumtreediff.matchers.Mapping;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.Tree;
import com.github.gumtreediff.tree.TreeContext;

import java.io.BufferedOutputStream;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class GumtreeBatch {
    static boolean statementLike(String type) {
        return type.endsWith("statement") || type.endsWith("declaration") || type.endsWith("definition")
                || type.equals("compound_statement") || type.equals("else_clause");
    }

    static String clean(String s) {
        return s.replace("\t", "_").replace("\n", "_").replace("\r", "_");
    }

    public static void main(String[] args) throws Exception {
        Run.initGenerators();
        Run.initMatchers();
        Path dir = Paths.get(args[0]);
        String generator = args[1];
        String[] matchers = args[2].split(",");
        boolean all = args.length > 3 && args[3].equals("all");
        List<String> ids = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.a")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                ids.add(name.substring(0, name.length() - 2));
            }
        }
        Collections.sort(ids);
        PrintStream out = new PrintStream(new BufferedOutputStream(System.out, 1 << 20), false, "UTF-8");
        for (String id : ids) {
            TreeContext src;
            TreeContext dst;
            try {
                src = TreeGenerators.getInstance().getTree(dir.resolve(id + ".a").toString(), generator);
                dst = TreeGenerators.getInstance().getTree(dir.resolve(id + ".b").toString(), generator);
            } catch (Throwable e) {
                out.println("E\t" + id + "\t-\t" + String.valueOf(e).replace('\n', ' '));
                continue;
            }
            for (String name : matchers) {
                try {
                    Matcher matcher = Matchers.getInstance().getMatcher(name);
                    long start = System.nanoTime();
                    MappingStore mappings = matcher.match(src.getRoot(), dst.getRoot());
                    long millis = (System.nanoTime() - start) / 1_000_000;
                    out.println("T\t" + id + "\t" + name + "\t" + millis);
                    for (Mapping m : mappings) {
                        Tree a = m.first;
                        Tree b = m.second;
                        String ta = clean(a.getType().name);
                        String tb = clean(b.getType().name);
                        boolean leaf = a.isLeaf();
                        if (!all && !leaf && !statementLike(ta)) {
                            continue;
                        }
                        out.println("M\t" + id + "\t" + name + "\t" + (leaf ? "L" : statementLike(ta) ? "S" : "N") + "\t" + a.getPos() + "\t"
                                + a.getEndPos() + "\t" + ta + "\t" + b.getPos() + "\t" + b.getEndPos() + "\t" + tb);
                    }
                } catch (Throwable e) {
                    out.println("E\t" + id + "\t" + name + "\t" + String.valueOf(e).replace('\n', ' '));
                }
            }
        }
        out.flush();
    }
}
