package it.fileconcat_java_edition;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class BusinessLogic {

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Scansiona le sorgenti (cartelle, file, zip) e restituisce
     * la lista delle estensioni trovate (senza punto, lowercase), ordinata.
     */
    public static List<String> scanExtensions(List<String> sources) {
        Set<String> found = new HashSet<String>();
        for (String source : sources) {
            try {
                scanPath(source, found);
            } catch (IOException e) {
                // ignora errori durante la scansione
            }
        }
        List<String> result = new ArrayList<String>(found);
        Collections.sort(result);
        return result;
    }

    /**
     * Unisce tutti i file corrispondenti a {@code extensions} trovati in
     * {@code sources}. Le sorgenti possono essere cartelle, file singoli o .zip.
     * Restituisce il percorso assoluto del file generato.
     */
    public static String mergeFiles(List<String> sources,
                                    List<String> extensions,
                                    String outputName) throws IOException {

        // Percorsi assoluti delle sorgenti
        List<String> absSources = new ArrayList<String>();
        for (String s : sources) {
            absSources.add(new File(s).getAbsolutePath());
        }

        // Cartelle parent di ogni sorgente
        List<Path> dirs = new ArrayList<Path>();
        for (String s : absSources) {
            File f = new File(s);
            dirs.add(Paths.get(f.isDirectory() ? s : f.getParent()));
        }

        // Cartella base comune
        Path baseDir = (dirs.size() == 1) ? dirs.get(0) : commonPath(dirs);

        // Se baseDir coincide con una delle cartelle sorgente, sali di un livello
        for (String s : absSources) {
            File f = new File(s);
            if (f.isDirectory() && f.toPath().equals(baseDir)) {
                Path parent = baseDir.getParent();
                if (parent != null) {
                    baseDir = parent;
                }
                break;
            }
        }

        File outputFile  = baseDir.resolve(outputName).toFile();
        String outputAbs = outputFile.getAbsolutePath();

        List<File>   tempDirs     = new ArrayList<File>();
        List<String> filesToMerge = new ArrayList<String>();

        for (String source : sources) {
            collectFiles(source, extensions, filesToMerge, tempDirs);
        }

        // Deduplicazione — esclude il file di output stesso
        LinkedHashSet<String> seen        = new LinkedHashSet<String>();
        List<String>          uniqueFiles = new ArrayList<String>();
        for (String fp : filesToMerge) {
            String abs = new File(fp).getAbsolutePath();
            if (!abs.equals(outputAbs) && seen.add(abs)) {
                uniqueFiles.add(fp);
            }
        }

        try {
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"));
            try {
                out.println(repeat("=", 72));
                out.println("  FileFusion  |  " + uniqueFiles.size() + " file");
                out.println(repeat("=", 72));
                out.println();

                for (String fp : uniqueFiles) {
                    // Percorso relativo per l'intestazione del blocco
                    String rel;
                    try {
                        rel = baseDir.relativize(Paths.get(fp)).toString();
                    } catch (Exception e) {
                        rel = fp;
                    }

                    // Box drawing (┌─┐ │ └─┘)
                    out.println("\u250c" + repeat("\u2500", 70) + "\u2510");
                    out.printf( "\u2502  %-68s\u2502%n", rel);
                    out.println("\u2514" + repeat("\u2500", 70) + "\u2518");

                    // Contenuto del file
                    try {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(new FileInputStream(fp), "UTF-8"));
                        try {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                out.println(line);
                            }
                        } finally {
                            reader.close();
                        }
                    } catch (IOException e) {
                        out.println("[ERRORE LETTURA: " + e.getMessage() + "]");
                    }
                    out.println();
                }

                out.println();
                out.println(repeat("=", 72));
                out.println("  " + uniqueFiles.size() + " file uniti");
                out.println(repeat("=", 72));

            } finally {
                out.close();
            }
        } finally {
            for (File d : tempDirs) {
                deleteRecursive(d);
            }
        }

        return outputAbs;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /** Scansiona un singolo percorso (cartella / zip / file) raccogliendo estensioni. */
    private static void scanPath(String path, Set<String> found) throws IOException {
        File f = new File(path);
        if (f.isDirectory()) {
            walkDirForScan(f, found);
        } else if (isZip(f)) {
            scanZip(f, found);
        } else if (f.isFile()) {
            String ext = extension(f.getName());
            if (!ext.isEmpty()) found.add(ext);
        }
    }

    private static void walkDirForScan(File dir, Set<String> found) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                walkDirForScan(child, found);
            } else if (child.getName().toLowerCase().endsWith(".zip")) {
                scanZip(child, found);
            } else {
                String ext = extension(child.getName());
                if (!ext.isEmpty()) found.add(ext);
            }
        }
    }

    private static void scanZip(File zip, Set<String> found) {
        try {
            ZipFile zf = new ZipFile(zip);
            try {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String ext = extension(new File(entry.getName()).getName());
                    if (!ext.isEmpty()) found.add(ext);
                }
            } finally {
                zf.close();
            }
        } catch (Exception e) {
            // zip non valido, ignora
        }
    }

    /** Raccoglie ricorsivamente i file corrispondenti alle estensioni. */
    private static void collectFiles(String path,
                                     List<String> extensions,
                                     List<String> out,
                                     List<File> tempDirs) throws IOException {
        File f = new File(path);
        if (f.isDirectory()) {
            collectFromDir(f, extensions, out, tempDirs);
        } else if (isZip(f)) {
            File tmp = Files.createTempDirectory("unisci_").toFile();
            tempDirs.add(tmp);
            extractZip(f, tmp);
            collectFromDir(tmp, extensions, out, tempDirs);
        } else if (f.isFile()) {
            if (matchesExtension(f.getName(), extensions)) {
                out.add(path);
            }
        }
    }

    private static void collectFromDir(File dir,
                                       List<String> extensions,
                                       List<String> out,
                                       List<File> tempDirs) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children); // ordine alfabetico come in Python (sorted)
        for (File child : children) {
            if (child.isDirectory()) {
                collectFromDir(child, extensions, out, tempDirs);
            } else if (child.getName().toLowerCase().endsWith(".zip")) {
                collectFiles(child.getAbsolutePath(), extensions, out, tempDirs);
            } else if (matchesExtension(child.getName(), extensions)) {
                out.add(child.getAbsolutePath());
            }
        }
    }

    /** Controlla il magic number PK (0x504B0304) per rilevare file ZIP. */
    private static boolean isZip(File f) {
        if (!f.isFile() || f.length() < 4) return false;
        try {
            InputStream in = new FileInputStream(f);
            try {
                byte[] magic = new byte[4];
                if (in.read(magic) != 4) return false;
                return magic[0] == 0x50 && magic[1] == 0x4B
                    && magic[2] == 0x03 && magic[3] == 0x04;
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static void extractZip(File zipFile, File destDir) throws IOException {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        try {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    out.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(out);
                    try {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = zis.read(buf)) != -1) {
                            fos.write(buf, 0, n);
                        }
                    } finally {
                        fos.close();
                    }
                }
                zis.closeEntry();
            }
        } finally {
            zis.close();
        }
    }

    private static boolean matchesExtension(String filename, List<String> extensions) {
        String lower = filename.toLowerCase();
        for (String ext : extensions) {
            if (lower.endsWith("." + ext)) return true;
        }
        return false;
    }

    /** Percorso comune a una lista di Path (equivalente di os.path.commonpath). */
    private static Path commonPath(List<Path> paths) {
        if (paths.isEmpty()) return Paths.get("");
        Path common = paths.get(0).toAbsolutePath().normalize();
        for (int i = 1; i < paths.size(); i++) {
            Path other = paths.get(i).toAbsolutePath().normalize();
            while (!other.startsWith(common)) {
                common = common.getParent();
                if (common == null) return Paths.get("");
            }
        }
        return common;
    }

    private static String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1).toLowerCase();
    }

    /** Stringa ripetuta n volte (rimpiazza String.repeat() di Java 11+). */
    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        f.delete();
    }
}
