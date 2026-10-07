package com.lumen.agent;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiniShell {

    private final File root;
    private File cwd;

    public MiniShell(File root) {
        this.root = root;
        if (!root.exists()) root.mkdirs();
        this.cwd = root;
    }

    public File getRoot() { return root; }
    public File getCwd() { return cwd; }

    public String run(String command) {
        String cmd = command == null ? "" : command.trim();
        if (cmd.isEmpty()) return "";

        
        for (String sep : new String[]{"&&", ";"}) {
            List<String> parts = splitTop(cmd, sep);
            if (parts.size() > 1) {
                StringBuilder out = new StringBuilder();
                for (String p : parts) {
                    String r = run(p.trim());
                    if (!r.isEmpty()) out.append(r);
                    if (sep.equals("&&") && r.startsWith("ошибка:")) break;
                }
                return out.toString();
            }
        }

        String[] tokens = tokenize(cmd);
        if (tokens.length == 0) return "";
        String name = tokens[0];
        List<String> args = new ArrayList<>(Arrays.asList(tokens).subList(1, tokens.length));

        try {
            switch (name) {
                case "help":     return help();
                case "pwd":      return cwd.getAbsolutePath() + "\n";
                case "cd":       return cd(args);
                case "ls":       return ls(args);
                case "cat":      return cat(args);
                case "echo":     return echo(args);
                case "mkdir":    return mkdir(args);
                case "touch":    return touch(args);
                case "rm":       return rm(args);
                case "mv":       return copyOrMove(args, true);
                case "cp":       return copyOrMove(args, false);
                case "head":     return headTail(args, true);
                case "tail":     return headTail(args, false);
                case "wc":       return wc(args);
                case "grep":     return grep(args);
                case "find":     return find(args);
                case "date":     return new java.text.SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy", java.util.Locale.US).format(new java.util.Date()) + "\n";
                case "whoami":   return "u0_a" + (10000 + new java.util.Random().nextInt(900)) + "\n";
                case "uname":    return "Android " + android.os.Build.VERSION.RELEASE + " " + android.os.Build.SUPPORTED_ABIS[0] + " (mini-shell)\n";
                case "du":       return du(args);
                default:
                    return "ошибка: команда '" + name + "' не входит во встроенный шелл.\n"
                         + "Доступно: " + "pwd ls cd cat echo mkdir touch rm mv cp head tail wc grep find date whoami uname du help\n"
                         + "Для настоящего bash включи Termux (Настройки → Движок → Termux) или установи его: pkg install termux-tools\n";
            }
        } catch (Exception e) {
            return "ошибка: " + e.getMessage() + "\n";
        }
    }

    

    private String help() {
        return "Встроенный мини-шелл Lumen Agent (песочница: " + root.getAbsolutePath() + ")\n\n"
             + "  pwd                 текущая папка\n"
             + "  ls [-l] [путь]      список файлов\n"
             + "  cd <путь>           перейти в папку\n"
             + "  cat <файл>          показать файл\n"
             + "  echo <текст> [> ф]  вывод / запись в файл (>> — добавить)\n"
             + "  mkdir [-p] <путь>   создать папку\n"
             + "  touch <файл>        создать пустой файл\n"
             + "  rm [-r] <путь>      удалить\n"
             + "  mv / cp <a> <b>     переместить / скопировать\n"
             + "  head/tail [-n N] <файл>\n"
             + "  wc [-l] <файл>      строки/слова/символы\n"
             + "  grep [-i] <шаблон> <файл>\n"
             + "  find [имя]          поиск файлов по имени\n"
             + "  date, whoami, uname, du, help\n\n"
             + "Для настоящего bash/Termux-команд: Настройки → Движок → Termux.\n";
    }

    private File resolve(String path) {
        if (path == null || path.isEmpty() || path.equals("~")) return cwd;
        File f = new File(path);
        if (!f.isAbsolute()) f = new File(cwd, path);
        try {
            f = f.getCanonicalFile();
        } catch (IOException e) {
            f = f.getAbsoluteFile();
        }
        return f;
    }

    private void ensureInsideSandbox(File f) throws IOException {
        String p = f.getCanonicalPath();
        String r = root.getCanonicalPath();
        if (!p.equals(r) && !p.startsWith(r + File.separator)) {
            throw new IOException("путь вне песочницы приложения: " + p + "\nПесочница: " + r
                    + "\nДля доступа к /sdcard включи движок Termux в настройках.");
        }
    }

    private String cd(List<String> args) throws IOException {
        File target = resolve(args.isEmpty() ? "." : args.get(0));
        if (!target.isDirectory()) return "ошибка: нет такой папки: " + target.getPath() + "\n";
        ensureInsideSandbox(target);
        cwd = target;
        return cwd.getAbsolutePath() + "\n";
    }

    private String ls(List<String> args) throws IOException {
        boolean longFmt = false;
        String path = ".";
        for (String a : args) {
            if (a.equals("-l") || a.equals("-la") || a.equals("-al")) longFmt = true;
            else if (!a.startsWith("-")) path = a;
        }
        File dir = resolve(path);
        if (!dir.exists()) return "ошибка: нет такого пути: " + dir.getPath() + "\n";
        if (dir.isFile()) return dir.getName() + "  (" + dir.length() + " б)\n";
        File[] items = dir.listFiles();
        if (items == null) return "ошибка: не могу прочитать папку\n";
        Arrays.sort(items, Comparator.comparing(f -> (f.isFile() ? "1" : "0") + f.getName().toLowerCase()));
        StringBuilder sb = new StringBuilder();
        String now = new java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US).format(new java.util.Date());
        for (File f : items) {
            if (longFmt) {
                String date = new java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US).format(new java.util.Date(f.lastModified()));
                sb.append(f.isDirectory() ? "d" : "-").append(" ").append(String.format("%8d", f.length())).append(" ").append(date).append(" ").append(f.getName()).append("\n");
            } else {
                sb.append(f.isDirectory() ? "📁 " : "📄 ").append(f.getName());
                if (f.isFile()) sb.append("  (").append(f.length()).append(" б)");
                sb.append("\n");
            }
        }
        if (sb.length() == 0) sb.append("(пусто)\n");
        return sb.toString();
    }

    private String cat(List<String> args) throws IOException {
        if (args.isEmpty()) return "ошибка: cat <файл>\n";
        File f = resolve(args.get(0));
        ensureInsideSandbox(f);
        if (!f.isFile()) return "ошибка: файл не найден: " + f.getPath() + "\n";
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        return clip(text);
    }

    private String echo(List<String> args) throws IOException {
        int redir = -1;
        boolean append = false;
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(">") || args.get(i).equals(">>")) { redir = i; append = args.get(i).equals(">>"); break; }
        }
        String text = String.join(" ", redir >= 0 ? args.subList(0, redir) : args);
        if (redir >= 0 && redir + 1 < args.size()) {
            File f = resolve(args.get(redir + 1));
            ensureInsideSandbox(f);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            String prev = (append && f.isFile()) ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
            Files.write(f.toPath(), (prev + text + "\n").getBytes(StandardCharsets.UTF_8));
            return "";
        }
        return text + "\n";
    }

    private String mkdir(List<String> args) throws IOException {
        boolean parents = args.size() > 1 && args.get(0).equals("-p");
        String p = parents ? args.get(1) : (args.isEmpty() ? "" : args.get(0));
        if (p.isEmpty()) return "ошибка: mkdir <путь>\n";
        File f = resolve(p);
        ensureInsideSandbox(f);
        boolean ok = parents ? f.mkdirs() : f.mkdir();
        return ok ? "" : "ошибка: не удалось создать " + f.getPath() + "\n";
    }

    private String touch(List<String> args) throws IOException {
        if (args.isEmpty()) return "ошибка: touch <файл>\n";
        File f = resolve(args.get(0));
        ensureInsideSandbox(f);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        if (f.exists()) f.setLastModified(System.currentTimeMillis());
        else Files.write(f.toPath(), new byte[0]);
        return "";
    }

    private String rm(List<String> args) throws IOException {
        boolean recursive = false;
        List<String> paths = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("-") && a.contains("r")) recursive = true;
            else if (!a.startsWith("-")) paths.add(a);
        }
        if (paths.isEmpty()) return "ошибка: rm [-r] <путь>\n";
        StringBuilder sb = new StringBuilder();
        for (String p : paths) {
            File f = resolve(p);
            ensureInsideSandbox(f);
            if (f.getCanonicalPath().equals(root.getCanonicalPath())) return "ошибка: нельзя удалить корень песочницы\n";
            if (!f.exists()) { sb.append("ошибка: нет такого пути: ").append(p).append("\n"); continue; }
            if (f.isDirectory() && !recursive) { sb.append("ошибка: ").append(f.getName()).append(" — папка (нужен -r)\n"); continue; }
            if (!deleteRecursive(f)) sb.append("ошибка: не удалось удалить ").append(p).append("\n");
        }
        return sb.toString();
    }

    private boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        return f.delete();
    }

    private String copyOrMove(List<String> args, boolean move) throws IOException {
        if (args.size() < 2) return "ошибка: " + (move ? "mv" : "cp") + " <откуда> <куда>\n";
        File src = resolve(args.get(0));
        File dst = resolve(args.get(1));
        ensureInsideSandbox(src);
        ensureInsideSandbox(dst);
        if (!src.exists()) return "ошибка: нет такого пути: " + src.getPath() + "\n";
        if (dst.isDirectory()) dst = new File(dst, src.getName());
        if (src.isDirectory()) {
            if (!copyDir(src, dst)) return "ошибка: не удалось скопировать папку\n";
            if (move) deleteRecursive(src);
            return "";
        }
        if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
        Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (move) src.delete();
        return "";
    }

    private boolean copyDir(File src, File dst) throws IOException {
        if (!dst.exists() && !dst.mkdirs()) return false;
        File[] kids = src.listFiles();
        if (kids != null) {
            for (File k : kids) {
                File target = new File(dst, k.getName());
                if (k.isDirectory()) copyDir(k, target);
                else Files.copy(k.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return true;
    }

    private String headTail(List<String> args, boolean head) throws IOException {
        int n = 10;
        String path = null;
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-n") && i + 1 < args.size()) { n = parseInt(args.get(i + 1), 10); i++; }
            else if (a.matches("^-\\d+$")) n = parseInt(a.substring(1), 10);
            else if (!a.startsWith("-")) path = a;
        }
        if (path == null) return "ошибка: " + (head ? "head" : "tail") + " <файл>\n";
        File f = resolve(path);
        ensureInsideSandbox(f);
        if (!f.isFile()) return "ошибка: файл не найден: " + path + "\n";
        List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        List<String> part = head
                ? lines.subList(0, Math.min(n, lines.size()))
                : lines.subList(Math.max(0, lines.size() - n), lines.size());
        return String.join("\n", part) + "\n";
    }

    private String wc(List<String> args) throws IOException {
        boolean onlyLines = false;
        String path = null;
        for (String a : args) {
            if (a.equals("-l")) onlyLines = true;
            else if (!a.startsWith("-")) path = a;
        }
        if (path == null) return "ошибка: wc [-l] <файл>\n";
        File f = resolve(path);
        ensureInsideSandbox(f);
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        long lines = text.isEmpty() ? 0 : text.split("\n", -1).length;
        long words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        if (onlyLines) return lines + "\n";
        return lines + " " + words + " " + text.length() + " " + f.getName() + "\n";
    }

    private String grep(List<String> args) throws IOException {
        boolean ignoreCase = false;
        List<String> rest = new ArrayList<>();
        for (String a : args) {
            if (a.equals("-i")) ignoreCase = true;
            else rest.add(a);
        }
        if (rest.size() < 2) return "ошибка: grep [-i] <шаблон> <файл>\n";
        String needle = rest.get(0);
        File f = resolve(rest.get(1));
        ensureInsideSandbox(f);
        if (!f.isFile()) return "ошибка: файл не найден: " + rest.get(1) + "\n";
        StringBuilder sb = new StringBuilder();
        List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            boolean hit = ignoreCase ? l.toLowerCase().contains(needle.toLowerCase()) : l.contains(needle);
            if (hit) sb.append(String.format("%4d│ ", i + 1)).append(l).append("\n");
        }
        return sb.length() == 0 ? "" : sb.toString();
    }

    private String find(List<String> args) throws IOException {
        String needle = args.isEmpty() ? "" : args.get(args.size() - 1);
        StringBuilder sb = new StringBuilder();
        walk(root, needle, sb);
        return sb.length() == 0 ? "" : sb.toString();
    }

    private void walk(File dir, String needle, StringBuilder sb) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            String rel = root.toURI().relativize(k.toURI()).getPath();
            if (k.isDirectory()) walk(k, needle, sb);
            else if (needle.isEmpty() || k.getName().toLowerCase().contains(needle.toLowerCase()))
                sb.append(rel).append("  (").append(k.length()).append(" б)\n");
        }
    }

    private String du(List<String> args) throws IOException {
        File dir = resolve(args.isEmpty() ? "." : args.get(0));
        ensureInsideSandbox(dir);
        long total = sizeOf(dir);
        return human(total) + "\t" + dir.getPath() + "\n";
    }

    private long sizeOf(File f) {
        if (f.isFile()) return f.length();
        long sum = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) sum += sizeOf(k);
        return sum;
    }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " б";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f Кб", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.1f Мб", bytes / 1048576.0);
    }

    

    static String clip(String text) {
        if (text.length() <= 20000) return text;
        return text.substring(0, 20000) + "\n… [вывод обрезан, всего " + text.length() + " символов]\n";
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    
    private static List<String> splitTop(String s, String sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                cur.append(c);
                continue;
            }
            if (c == '"' || c == '\'') { quote = c; cur.append(c); continue; }
            if (sep.length() == 1 ? c == sep.charAt(0) : s.startsWith(sep, i)) {
                out.add(cur.toString());
                cur.setLength(0);
                if (sep.length() > 1) i++;
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    
    static String[] tokenize(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        boolean has = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) { quote = 0; }
                else cur.append(c);
                has = true;
                continue;
            }
            if (c == '"' || c == '\'') { quote = c; has = true; continue; }
            if (Character.isWhitespace(c)) {
                if (has) { out.add(cur.toString()); cur.setLength(0); has = false; }
                continue;
            }
            if (c == '>' ) {
                if (has) { out.add(cur.toString()); cur.setLength(0); has = false; }
                if (i + 1 < s.length() && s.charAt(i + 1) == '>') { out.add(">>"); i++; }
                else out.add(">");
                continue;
            }
            cur.append(c);
            has = true;
        }
        if (has) out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    
    public String toolResult(String name, JSONObject args) {
        try {
            switch (name) {
                case "bash":
                    return run(args.optString("command", ""));
                case "read_file": {
                    File f = resolve(args.optString("path", ""));
                    ensureInsideSandbox(f);
                    if (!f.isFile()) return "ошибка: файл не найден: " + f.getPath();
                    List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                    int from = Math.max(1, args.optInt("start_line", 1));
                    int to = args.optInt("end_line", 0);
                    if (to <= 0) to = lines.size();
                    StringBuilder sb = new StringBuilder(f.getPath() + " (строки " + from + "-" + Math.min(to, lines.size()) + " из " + lines.size() + "):\n");
                    for (int i = from; i <= Math.min(to, lines.size()); i++) sb.append(String.format("%5d│ %s\n", i, lines.get(i - 1)));
                    return clip(sb.toString());
                }
                case "write_file": {
                    File f = resolve(args.optString("path", ""));
                    ensureInsideSandbox(f);
                    if (f.getParentFile() != null) f.getParentFile().mkdirs();
                    boolean existed = f.exists();
                    String content = args.optString("content", "");
                    Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
                    return (existed ? "перезаписан " : "создан ") + f.getPath() + " (" + content.length() + " символов)";
                }
                case "edit_file": {
                    File f = resolve(args.optString("path", ""));
                    ensureInsideSandbox(f);
                    if (!f.isFile()) return "ошибка: файл не найден: " + f.getPath();
                    String oldS = args.optString("old_string", "");
                    String newS = args.optString("new_string", "");
                    if (oldS.isEmpty()) return "ошибка: пустой old_string";
                    String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    int idx = text.indexOf(oldS);
                    if (idx < 0) return "ошибка: фрагмент old_string не найден — прочитай файл заново";
                    if (text.indexOf(oldS, idx + 1) >= 0) return "ошибка: фрагмент встречается несколько раз — уточни old_string";
                    Files.write(f.toPath(), text.replaceFirst(Pattern.quote(oldS), Matcher.quoteReplacement(newS)).getBytes(StandardCharsets.UTF_8));
                    return "изменён " + f.getPath() + " (" + oldS.length() + " → " + newS.length() + " символов)";
                }
                case "ls": {
                    List<String> list = new ArrayList<>();
                    list.add(args.optString("path", "."));
                    return ls(list);
                }
                default:
                    return "ошибка: неизвестный инструмент " + name;
            }
        } catch (Exception e) {
            return "ошибка: " + e.getMessage();
        }
    }

    public String cwdPath() { return cwd.getAbsolutePath(); }
}
