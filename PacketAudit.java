import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * PacketAudit — a static, offline audit of this client's packet handling.
 *
 * <p>It never launches Minecraft and never resolves Minecraft/Fabric types: it reads every
 * {@code .java} file under {@code src/main/java} (or a directory passed as the first argument) and
 * reports source patterns that violate the vanilla 1.21.11 packet rules for this codebase.
 *
 * <p>This file lives in the project root and carries no package declaration on purpose, so it is
 * not compiled into the mod. Run it with the JDK's source launcher:
 *
 * <pre>
 *   java PacketAudit.java                 # scans ./src/main/java
 *   java PacketAudit.java path/to/java    # scans another source root
 * </pre>
 *
 * <p>Exit codes: {@code 0} clean, {@code 1} violations found, {@code 2} the scan could not run.
 *
 * <p>Path conventions are matched case-insensitively and tolerate both {@code module/} and
 * {@code modules/} roots, because the rules are written against {@code /modules/} while this tree
 * uses {@code odinclient/module}. A "module file" is any file below such a directory. Utility
 * exemptions are by file name ({@code RotationUtils.java}, {@code WorldUtils.java},
 * {@code PacketUtils.java}, {@code InventoryUtils.java}) wherever they live.
 *
 * <p>Comments and string/char literal contents are blanked before matching, so prose in comments and
 * log messages cannot raise findings, while line numbers stay exact.
 */
public final class PacketAudit {

    // ---------------------------------------------------------------------------------------
    // Rule constants
    // ---------------------------------------------------------------------------------------

    /** Maximum permitted yaw step for rotation utils. */
    private static final float YAW_CAP_LIMIT = 26.0f;

    /** Maximum permitted pitch step for rotation utils. */
    private static final float PITCH_CAP_LIMIT = 10.0f;

    /** How many lines around a placement call count as "near" for the placement guard check. */
    private static final int PLACEMENT_WINDOW = 12;

    private static final int MAX_SNIPPET = 118;

    private static final String[] SECTION_TITLES = {
        "",
        "Rotation violations",
        "Interaction violations",
        "Hotbar & slot violations",
        "State & input violations",
        "Multi-action & timing violations",
        "Placement violations",
        "1.21.11 API existence"
    };

    // ---------------------------------------------------------------------------------------
    // Patterns (matched against masked source, i.e. code only)
    // ---------------------------------------------------------------------------------------

    private static final Pattern P_SET_ROTATION =
            Pattern.compile("\\.set(?:Yaw|Pitch)\\s*\\(");
    private static final Pattern P_MOVE_PACKET =
            Pattern.compile("\\bPlayerMoveC2SPacket\\b");
    private static final Pattern P_INTERACTION_MANAGER =
            Pattern.compile("\\b(?:mc|client)\\s*\\.\\s*interactionManager\\s*\\.");
    private static final Pattern P_SEND_PACKET =
            Pattern.compile("\\.sendPacket\\s*\\(");
    private static final Pattern P_SELECTED_SLOT =
            Pattern.compile("\\.selectedSlot\\s*=(?!=)");
    private static final Pattern P_CLICK_SLOT_PACKET =
            Pattern.compile("\\bClickSlotC2SPacket\\b");
    private static final Pattern P_HARDCODED_SYNC_ID =
            Pattern.compile("\\bsyncId\\s*=\\s*0\\b|\\bsyncId\\s*,\\s*0\\s*,");
    private static final Pattern P_STATE_WRITE =
            Pattern.compile("\\.(?:setVelocity|setPos|setOnGround)\\s*\\(");
    private static final Pattern P_MOVEMENT_KEY =
            Pattern.compile("\\boptions\\s*\\.\\s*(?:forwardKey|backKey|jumpKey)\\s*\\.\\s*setPressed\\b");
    private static final Pattern P_THREAD_SLEEP =
            Pattern.compile("\\bThread\\s*\\.\\s*sleep\\s*\\(");
    private static final Pattern P_WORLD_UTILS =
            Pattern.compile("\\bWorldUtils\\s*\\.");
    private static final Pattern P_HOTBAR_CALL =
            Pattern.compile("\\bWorldUtils\\s*\\.\\s*setHotbarSlot\\s*\\(");
    private static final Pattern P_WORLD_ACTION_CALL =
            Pattern.compile("\\bWorldUtils\\s*\\.\\s*(?:hitEntity|interactBlock)\\s*\\(");
    private static final Pattern P_CRYSTAL_PLACEMENT =
            Pattern.compile("\\bWorldUtils\\s*\\.\\s*(?:interactBlock|placeBlock)\\s*\\(");
    private static final Pattern P_AIR_OR_REPLACEABLE =
            Pattern.compile("\\bis(?:Air|Replaceable)\\s*\\(");
    private static final Pattern P_POSITION_ABOVE = Pattern.compile(
            "\\.up\\s*\\(|\\babove\\b|\\.add\\s*\\(\\s*0\\s*,\\s*1\\s*,\\s*0\\s*\\)|\\.offset\\s*\\(");
    private static final Pattern P_PLACEMENT_GUARD_CALL =
            Pattern.compile("\\b(canPlace\\w*|isPlace\\w*Valid|isValidPlacement)\\s*\\(");
    private static final Pattern P_VEC3D_IS_NAN =
            Pattern.compile("\\bVec3d\\s*\\.\\s*isNaN\\s*\\(");
    private static final Pattern P_LOOK_AND_ON_GROUND =
            Pattern.compile("\\bPlayerMoveC2SPacket\\s*\\.\\s*LookAndOnGround\\b");
    private static final Pattern P_METHOD =
            Pattern.compile("\\b(onTick\\w*)\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\{");
    private static final Pattern P_GUARD =
            Pattern.compile("\\breturn\\b|\\bif\\s*\\([^)]*\\b(?:phase|state|stage|step)\\b");

    // ---------------------------------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------------------------------

    public static void main(String[] args) {
        if (args.length > 1 || (args.length == 1 && (args[0].equals("-h") || args[0].equals("--help")))) {
            System.out.println("usage: java PacketAudit.java [sourceRoot]");
            System.out.println("       sourceRoot defaults to src/main/java");
            System.exit(args.length > 1 ? 2 : 0);
        }

        Path root = Path.of(args.length == 1 ? args[0] : "src/main/java");
        if (!Files.isDirectory(root)) {
            System.err.println("PacketAudit: source root not found or not a directory: " + root.toAbsolutePath());
            System.err.println("Run it from the project root, or pass the java source root explicitly.");
            System.exit(2);
            return;
        }

        List<SourceFile> files;
        try {
            files = load(root);
        } catch (IOException error) {
            System.err.println("PacketAudit: could not walk " + root + ": " + error.getMessage());
            System.exit(2);
            return;
        }

        Audit audit = new Audit(root, files);
        audit.run();
        audit.print();
        System.exit(audit.violationCount() > 0 ? 1 : 0);
    }

    // ---------------------------------------------------------------------------------------
    // Source loading
    // ---------------------------------------------------------------------------------------

    private static List<SourceFile> load(Path root) throws IOException {
        List<SourceFile> loaded = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("PacketAudit.java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();

            for (Path path : javaFiles) {
                String text;
                try {
                    text = Files.readString(path, StandardCharsets.UTF_8);
                } catch (IOException failure) {
                    System.err.println("PacketAudit: unreadable file skipped: " + path + " ("
                            + failure.getMessage() + ")");
                    continue;
                }
                String relative = root.relativize(path).toString().replace('\\', '/');
                loaded.add(SourceFile.of(relative, path.getFileName().toString(), text));
            }
        }
        return loaded;
    }

    // ---------------------------------------------------------------------------------------
    // Model
    // ---------------------------------------------------------------------------------------

    enum Severity {
        VIOLATION,
        WARNING
    }

    record Finding(int section, String rule, Severity severity, String file, int line,
                   String message, String snippet) {

        static Finding violation(int section, String rule, String file, int line, String message,
                                 String snippet) {
            return new Finding(section, rule, Severity.VIOLATION, file, line, message, snippet);
        }

        static Finding warning(int section, String rule, String file, int line, String message,
                               String snippet) {
            return new Finding(section, rule, Severity.WARNING, file, line, message, snippet);
        }
    }

    private record Hit(int line, int column, String lineText) {
    }

    /** What a delegated {@code canPlace*}/{@code isPlace*Valid} helper verifies. */
    private record GuardCheck(boolean air, boolean above) {
    }

    /** A parsed Java source file: raw lines for snippets, masked lines for matching. */
    private static final class SourceFile {
        final String relativePath;
        final String simpleName;
        final List<String> lines;
        final List<String> code;
        final String masked;
        final Set<String> directorySegments;
        final boolean moduleFile;

        private SourceFile(String relativePath, String simpleName, List<String> lines,
                           List<String> code, String masked) {
            this.relativePath = relativePath;
            this.simpleName = simpleName;
            this.lines = lines;
            this.code = code;
            this.masked = masked;

            Set<String> segments = new LinkedHashSet<>();
            int slash = relativePath.lastIndexOf('/');
            String directory = slash < 0 ? "" : relativePath.substring(0, slash);
            for (String segment : directory.split("/")) {
                if (!segment.isEmpty()) {
                    segments.add(segment.toLowerCase(Locale.ROOT));
                }
            }
            this.directorySegments = segments;
            this.moduleFile = segments.contains("module") || segments.contains("modules");
        }

        static SourceFile of(String relativePath, String simpleName, String text) {
            String masked = mask(text);
            return new SourceFile(relativePath, simpleName, splitLines(text), splitLines(masked), masked);
        }

        boolean isIn(String directory) {
            return directorySegments.contains(directory);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Audit engine
    // ---------------------------------------------------------------------------------------

    private static final class Audit {
        private final Path root;
        private final List<SourceFile> files;
        private final List<Finding> findings = new ArrayList<>();
        private final Set<String> reported = new HashSet<>();
        private final String startedAt;

        Audit(Path root, List<SourceFile> files) {
            this.root = root;
            this.files = files;
            this.startedAt = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        void run() {
            auditRotation();
            auditInteraction();
            auditHotbarAndSlots();
            auditStateAndInput();
            auditTiming();
            auditPlacement();
            auditApiExistence();

            findings.sort(Comparator
                    .comparingInt(Finding::section)
                    .thenComparing(Finding::file)
                    .thenComparingInt(Finding::line)
                    .thenComparing(Finding::rule));
        }

        long violationCount() {
            return findings.stream().filter(f -> f.severity() == Severity.VIOLATION).count();
        }

        long warningCount() {
            return findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
        }

        private long violationsIn(int section) {
            return findings.stream()
                    .filter(f -> f.section() == section && f.severity() == Severity.VIOLATION)
                    .count();
        }

        private long warningsIn(int section) {
            return findings.stream()
                    .filter(f -> f.section() == section && f.severity() == Severity.WARNING)
                    .count();
        }

        // ------------------------------------ section 1 -----------------------------------

        private void auditRotation() {
            for (SourceFile file : files) {
                if (!file.moduleFile) {
                    continue;
                }
                for (Hit hit : findAll(file, P_SET_ROTATION)) {
                    add(Finding.violation(1, "ROT-01", file.relativePath, hit.line(),
                            "setYaw()/setPitch() inside a module; rotation must be routed through RotationUtils",
                            hit.lineText()));
                }
                for (Hit hit : findAll(file, P_MOVE_PACKET)) {
                    add(Finding.violation(1, "ROT-02", file.relativePath, hit.line(),
                            "PlayerMoveC2SPacket used inside a module; rotation packets must be sent by "
                                    + "RotationUtils",
                            hit.lineText()));
                }
            }

            List<SourceFile> rotationUtils = files.stream()
                    .filter(f -> f.simpleName.equals("RotationUtils.java"))
                    .toList();
            if (rotationUtils.isEmpty()) {
                add(Finding.warning(1, "ROT-03", "RotationUtils.java", 0,
                        "RotationUtils.java was not found under the scan root, so YAW_CAP/PITCH_CAP "
                                + "could not be verified",
                        ""));
            } else {
                for (SourceFile file : rotationUtils) {
                    checkRotationCap(file, "YAW_CAP", YAW_CAP_LIMIT);
                    checkRotationCap(file, "PITCH_CAP", PITCH_CAP_LIMIT);
                }
            }
        }

        private void checkRotationCap(SourceFile file, String constant, float limit) {
            Pattern pattern = Pattern.compile("\\b" + constant + "\\s*=\\s*(-?\\d+(?:\\.\\d+)?)[fFdD]?");
            Matcher matcher = pattern.matcher(file.masked);
            if (!matcher.find()) {
                add(Finding.warning(1, "ROT-03", file.relativePath, 0,
                        constant + " is not declared in RotationUtils.java; expected a value <= "
                                + formatFloat(limit),
                        ""));
                return;
            }

            int line = new LineIndex(file.masked).lineOf(matcher.start());
            float value;
            try {
                value = Float.parseFloat(matcher.group(1));
            } catch (NumberFormatException malformed) {
                add(Finding.warning(1, "ROT-03", file.relativePath, line,
                        constant + " could not be parsed", lineAt(file, line)));
                return;
            }

            if (value > limit) {
                add(Finding.violation(1, "ROT-03", file.relativePath, line,
                        constant + " = " + formatFloat(value) + " exceeds the allowed cap of "
                                + formatFloat(limit),
                        lineAt(file, line)));
            }
        }

        // ------------------------------------ section 2 -----------------------------------

        private void auditInteraction() {
            for (SourceFile file : files) {
                if (!file.moduleFile) {
                    continue;
                }
                for (Hit hit : findAll(file, P_INTERACTION_MANAGER)) {
                    add(Finding.violation(2, "INT-01", file.relativePath, hit.line(),
                            "direct interactionManager call inside a module; interactions must go "
                                    + "through WorldUtils",
                            hit.lineText()));
                }
                for (Hit hit : findAll(file, P_SEND_PACKET)) {
                    add(Finding.violation(2, "INT-02", file.relativePath, hit.line(),
                            "sendPacket() inside a module; packets must be sent through "
                                    + "WorldUtils or PacketUtils",
                            hit.lineText()));
                }
            }
        }

        // ------------------------------------ section 3 -----------------------------------

        private void auditHotbarAndSlots() {
            for (SourceFile file : files) {
                if (file.moduleFile) {
                    for (Hit hit : findAll(file, P_SELECTED_SLOT)) {
                        add(Finding.violation(3, "HTR-01", file.relativePath, hit.line(),
                                "direct .selectedSlot assignment inside a module; use "
                                        + "WorldUtils.setHotbarSlot()",
                                hit.lineText()));
                    }
                    for (Hit hit : findAll(file, P_CLICK_SLOT_PACKET)) {
                        add(Finding.violation(3, "HTR-02", file.relativePath, hit.line(),
                                "ClickSlotC2SPacket used inside a module; slot clicks must go "
                                        + "through InventoryUtils",
                                hit.lineText()));
                    }
                }
                // Hardcoded sync ids are unsafe anywhere, not only in modules.
                for (Hit hit : findAll(file, P_HARDCODED_SYNC_ID)) {
                    add(Finding.violation(3, "HTR-03", file.relativePath, hit.line(),
                            "hardcoded sync id (0); use currentScreenHandler.syncId",
                            hit.lineText()));
                }
            }
        }

        // ------------------------------------ section 4 -----------------------------------

        private void auditStateAndInput() {
            for (SourceFile file : files) {
                if (file.moduleFile) {
                    boolean combatLike = file.isIn("combat") || file.isIn("crystal") || file.isIn("mace");
                    if (combatLike) {
                        for (Hit hit : findAll(file, P_STATE_WRITE)) {
                            add(Finding.violation(4, "STA-01", file.relativePath, hit.line(),
                                    "combat module writes player position/velocity; combat modules "
                                            + "must not set position or velocity",
                                    hit.lineText()));
                        }
                    }
                }

                for (Hit hit : findAll(file, P_MOVEMENT_KEY)) {
                    add(Finding.violation(4, "STA-02", file.relativePath, hit.line(),
                            "manual movement key press; forward/back/jump keys must stay vanilla "
                                    + "(only the sprint key is allowed)",
                            hit.lineText()));
                }
                for (Hit hit : findAll(file, P_THREAD_SLEEP)) {
                    add(Finding.violation(4, "STA-03", file.relativePath, hit.line(),
                            "Thread.sleep() on the client; schedule work from tick events instead",
                            hit.lineText()));
                }
            }
        }

        // ------------------------------------ section 5 -----------------------------------

        private void auditTiming() {
            for (SourceFile file : files) {
                if (!file.moduleFile) {
                    continue;
                }
                LineIndex index = new LineIndex(file.masked);
                Matcher method = P_METHOD.matcher(file.masked);
                while (method.find()) {
                    int openBrace = method.end() - 1;
                    int closeBrace = matchingBrace(file.masked, openBrace);
                    if (closeBrace < 0) {
                        continue;
                    }
                    String methodName = method.group(1);
                    int firstLine = index.lineOf(openBrace);
                    int lastLine = index.lineOf(closeBrace);

                    // 5.1 — more than one WorldUtils call with no return/state check between them.
                    List<Integer> worldUtilsLines = new ArrayList<>();
                    for (int line = firstLine; line <= lastLine; line++) {
                        if (P_WORLD_UTILS.matcher(lineAt(file, line)).find()) {
                            worldUtilsLines.add(line);
                        }
                    }
                    for (int i = 0; i + 1 < worldUtilsLines.size(); i++) {
                        int previous = worldUtilsLines.get(i);
                        int next = worldUtilsLines.get(i + 1);
                        if (hasGuard(file, previous + 1, next)) {
                            continue;
                        }
                        add(Finding.violation(5, "TMR-01", file.relativePath, next,
                                "WorldUtils. called more than once in " + methodName
                                        + "() with no return or state check between the calls; "
                                        + "possible same-tick double action",
                                lineAt(file, next)));
                    }

                    // 5.2 — hotbar swap immediately followed by an action.
                    int hotbarLine = firstLineMatching(file, firstLine, lastLine, P_HOTBAR_CALL);
                    if (hotbarLine > 0) {
                        for (int line = hotbarLine + 1; line <= lastLine; line++) {
                            Matcher action = P_WORLD_ACTION_CALL.matcher(lineAt(file, line));
                            if (!action.find()) {
                                continue;
                            }
                            if (!hasGuard(file, hotbarLine + 1, line)) {
                                add(Finding.violation(5, "TMR-02", file.relativePath, line,
                                        "WorldUtils.setHotbarSlot followed by " + action.group().trim()
                                                + " in " + methodName + "() with no phase/state "
                                                + "machine; hotbar swap and action may land in the "
                                                + "same tick",
                                        lineAt(file, line)));
                            }
                            break;
                        }
                    }
                }
            }
        }

        private boolean hasGuard(SourceFile file, int fromLine, int toLine) {
            for (int line = fromLine; line <= toLine && line <= file.code.size(); line++) {
                if (P_GUARD.matcher(lineAt(file, line)).find()) {
                    return true;
                }
            }
            return false;
        }

        private int firstLineMatching(SourceFile file, int fromLine, int toLine, Pattern pattern) {
            for (int line = fromLine; line <= toLine && line <= file.code.size(); line++) {
                if (pattern.matcher(lineAt(file, line)).find()) {
                    return line;
                }
            }
            return -1;
        }

        // ------------------------------------ section 6 -----------------------------------

        private void auditPlacement() {
            for (SourceFile file : files) {
                // 6.1 — crystal placement must guard the block above the target.
                if (file.moduleFile && file.isIn("crystal")) {
                    for (Hit hit : findAll(file, P_CRYSTAL_PLACEMENT)) {
                        int from = Math.max(1, hit.line() - PLACEMENT_WINDOW);
                        int to = Math.min(file.code.size(), hit.line() + PLACEMENT_WINDOW);
                        boolean hasAirCheck = false;
                        boolean hasAboveCheck = false;
                        for (int line = from; line <= to; line++) {
                            String text = lineAt(file, line);
                            if (P_AIR_OR_REPLACEABLE.matcher(text).find()) {
                                hasAirCheck = true;
                            }
                            if (P_POSITION_ABOVE.matcher(text).find()) {
                                hasAboveCheck = true;
                            }
                        }
                        if (!hasAirCheck) {
                            add(Finding.violation(6, "PLC-01", file.relativePath, hit.line(),
                                    "crystal placement without an isAir()/isReplaceable() check on "
                                            + "the block above the target",
                                    hit.lineText()));
                        } else if (!hasAboveCheck) {
                            add(Finding.warning(6, "PLC-01", file.relativePath, hit.line(),
                                    "placement has an isAir()/isReplaceable() check but no evidence it "
                                            + "targets the position above (pos.up())",
                                    hit.lineText()));
                        }
                    }
                }

                // 6.2 — EndCrystalEntity placement requires an air check above the base block.
                if (!file.masked.contains("EndCrystalEntity")) {
                    continue;
                }
                int placementLine = firstEndCrystalPlacementLine(file);
                if (placementLine < 0) {
                    continue;
                }

                int from = Math.max(1, placementLine - PLACEMENT_WINDOW);
                int to = Math.min(file.code.size(), placementLine + PLACEMENT_WINDOW);
                boolean localAirCheck = P_AIR_OR_REPLACEABLE.matcher(file.masked).find();
                boolean aboveEvidence = P_POSITION_ABOVE.matcher(file.masked).find();
                boolean delegatedAirCheck = false;
                Matcher guard = P_PLACEMENT_GUARD_CALL.matcher(file.masked);
                while (guard.find()) {
                    GuardCheck resolved = resolveGuard(guard.group(1));
                    if (resolved != null && resolved.air()) {
                        delegatedAirCheck = true;
                        aboveEvidence |= resolved.above();
                    }
                }

                boolean covered = aboveEvidence && (localAirCheck || delegatedAirCheck);
                if (!covered && !localAirCheck && !delegatedAirCheck) {
                    add(Finding.violation(6, "PLC-02", file.relativePath, placementLine,
                            "EndCrystalEntity placement without a getBlockState(pos.up()).isAir() guard",
                            lineAt(file, placementLine)));
                } else if (!covered) {
                    add(Finding.warning(6, "PLC-02", file.relativePath, placementLine,
                            "EndCrystalEntity placement checks isAir()/isReplaceable() but not clearly "
                                    + "on the position above (pos.up() or pos.add(0, 1, 0))",
                            lineAt(file, placementLine)));
                }
            }
        }

        /**
         * Looks up a {@code canPlace*}-style helper in any scanned file and reports whether its body
         * contains an air/replaceable check (and whether that check targets the position above).
         */
        private GuardCheck resolveGuard(String methodName) {
            Pattern declaration = Pattern.compile("\\b(?:boolean|void)\\s+" + Pattern.quote(methodName)
                    + "\\s*\\(");
            for (SourceFile candidate : files) {
                Matcher matcher = declaration.matcher(candidate.masked);
                if (!matcher.find()) {
                    continue;
                }
                int openBrace = candidate.masked.indexOf('{', matcher.end());
                int statementEnd = candidate.masked.indexOf(';', matcher.end());
                if (openBrace < 0 || (statementEnd >= 0 && statementEnd < openBrace)) {
                    continue;
                }
                int closeBrace = matchingBrace(candidate.masked, openBrace);
                if (closeBrace < 0) {
                    continue;
                }
                String body = candidate.masked.substring(openBrace, closeBrace);
                return new GuardCheck(P_AIR_OR_REPLACEABLE.matcher(body).find(),
                        P_POSITION_ABOVE.matcher(body).find());
            }
            return null;
        }

        /** First line that actually places/spawns a crystal base, or -1 when the file only reads crystals. */
        private int firstEndCrystalPlacementLine(SourceFile file) {
            for (int line = 1; line <= file.code.size(); line++) {
                String text = lineAt(file, line);
                if (text.contains("Items.END_CRYSTAL")
                        || text.contains("new EndCrystalEntity")
                        || text.contains("spawnEntity")) {
                    return line;
                }
            }
            return -1;
        }

        // ------------------------------------ section 7 -----------------------------------

        private void auditApiExistence() {
            for (SourceFile file : files) {
                LineIndex index = new LineIndex(file.masked);

                for (Hit hit : findAll(file, P_VEC3D_IS_NAN)) {
                    add(Finding.violation(7, "VER-01", file.relativePath, hit.line(),
                            "Vec3d.isNaN() does not exist in 1.21.11 Yarn; check the components "
                                    + "(Double.isNaN(v.x) && ...) instead",
                            hit.lineText()));
                }

                Matcher matcher = P_LOOK_AND_ON_GROUND.matcher(file.masked);
                while (matcher.find()) {
                    int paren = file.masked.indexOf('(', matcher.end());
                    if (paren < 0 || paren - matcher.end() > 8) {
                        continue; // import/reference, not a constructor call
                    }
                    int arguments = countArguments(file.masked, paren);
                    int line = index.lineOf(matcher.start());
                    if (arguments == 4) {
                        add(Finding.violation(7, "VER-02", file.relativePath, line,
                                "PlayerMoveC2SPacket.LookAndOnGround called with 4 arguments; 1.21.11 "
                                        + "declares only (boolean onGround, float yaw, float pitch)",
                                lineAt(file, line)));
                    } else if (arguments < 0) {
                        add(Finding.warning(7, "VER-02", file.relativePath, line,
                                "could not determine the PlayerMoveC2SPacket.LookAndOnGround argument "
                                        + "count; verify it has 3 arguments",
                                lineAt(file, line)));
                    }
                }
            }
        }

        // ---------------------------------------------------------------------------------------
        // Reporting
        // ---------------------------------------------------------------------------------------

        void print() {
            String bar = "=".repeat(84);
            String thin = "-".repeat(84);
            StringBuilder out = new StringBuilder();

            out.append(bar).append('\n');
            out.append(" PACKET AUDIT  |  Minecraft 1.21.11  |  Fabric  |  Java 21  |  Yarn mappings\n");
            out.append(bar).append('\n');
            out.append("Scan root : ").append(root.toAbsolutePath().normalize()).append('\n');
            out.append("Started   : ").append(startedAt).append('\n');
            out.append("Sources   : ").append(files.size()).append(" .java files");
            long moduleFiles = files.stream().filter(f -> f.moduleFile).count();
            out.append(" (").append(moduleFiles).append(" under a module directory)\n");
            out.append("Utilities : ");
            out.append("RotationUtils.java ").append(presence("RotationUtils.java")).append("  ");
            out.append("WorldUtils.java ").append(presence("WorldUtils.java")).append("  ");
            out.append("PacketUtils.java ").append(presence("PacketUtils.java")).append("  ");
            out.append("InventoryUtils.java ").append(presence("InventoryUtils.java")).append('\n');

            for (int section = 1; section < SECTION_TITLES.length; section++) {
                long violations = violationsIn(section);
                long warnings = warningsIn(section);
                String status = violations > 0 ? "FAIL" : "PASS";

                out.append('\n').append(thin).append('\n');
                out.append('[').append(section).append("] ").append(SECTION_TITLES[section])
                        .append("  --  ").append(status);
                if (violations > 0 || warnings > 0) {
                    out.append(" (");
                    if (violations > 0) {
                        out.append(violations).append(violations == 1 ? " violation" : " violations");
                        if (warnings > 0) {
                            out.append(", ");
                        }
                    }
                    if (warnings > 0) {
                        out.append(warnings).append(warnings == 1 ? " warning" : " warnings");
                    }
                    out.append(')');
                }
                out.append('\n').append(thin).append('\n');

                boolean any = false;
                for (Finding finding : findings) {
                    if (finding.section() != section) {
                        continue;
                    }
                    any = true;
                    boolean violation = finding.severity() == Severity.VIOLATION;
                    String marker = violation ? "[X]" : "[!]";
                    String location = finding.line() > 0
                            ? finding.file() + ":" + finding.line()
                            : finding.file();
                    out.append("  ").append(marker).append(' ').append(finding.rule())
                            .append("  ").append(location).append('\n');
                    out.append("      ").append(finding.message()).append('\n');
                    if (!finding.snippet().isBlank()) {
                        out.append("      > ").append(finding.snippet()).append('\n');
                    }
                }
                if (!any) {
                    out.append("  (no violations detected)\n");
                }
            }

            long totalViolations = violationCount();
            long totalWarnings = warningCount();
            Set<String> touched = new HashSet<>();
            for (Finding finding : findings) {
                touched.add(finding.file());
            }

            out.append('\n').append(bar).append('\n');
            out.append("SUMMARY\n");
            out.append(bar).append('\n');
            for (int section = 1; section < SECTION_TITLES.length; section++) {
                long violations = violationsIn(section);
                long warnings = warningsIn(section);
                out.append(String.format("  [%d] %-28s %-5s %d violation%s, %d warning%s%n",
                        section,
                        SECTION_TITLES[section],
                        violations > 0 ? "FAIL" : "PASS",
                        violations, violations == 1 ? "" : "s",
                        warnings, warnings == 1 ? "" : "s"));
            }
            out.append('\n');
            out.append("  Files with findings : ").append(touched.size()).append('\n');
            out.append("  Total violations    : ").append(totalViolations).append('\n');
            out.append("  Total warnings      : ").append(totalWarnings).append('\n');
            out.append("  Result              : ")
                    .append(totalViolations == 0 ? "PASS - no packet-rule violations found"
                            : "FAIL - " + totalViolations + " packet-rule violation"
                              + (totalViolations == 1 ? "" : "s") + " found")
                    .append('\n');
            out.append(bar).append('\n');

            System.out.print(out);
        }

        private String presence(String simpleName) {
            boolean found = files.stream().anyMatch(f -> f.simpleName.equals(simpleName));
            return found ? "[found]" : "[MISSING]";
        }

        private void add(Finding finding) {
            // One finding per rule per line: a match may repeat on the same line (declaration + call).
            String key = finding.rule() + "|" + finding.file() + "|" + finding.line();
            if (reported.add(key)) {
                findings.add(finding);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Source helpers
    // ---------------------------------------------------------------------------------------

    private static List<Hit> findAll(SourceFile file, Pattern pattern) {
        List<Hit> hits = new ArrayList<>();
        for (int line = 1; line <= file.code.size(); line++) {
            Matcher matcher = pattern.matcher(file.code.get(line - 1));
            while (matcher.find()) {
                hits.add(new Hit(line, matcher.start(), lineAt(file, line)));
            }
        }
        return hits;
    }

    private static String lineAt(SourceFile file, int line) {
        if (line < 1 || line > file.lines.size()) {
            return "";
        }
        String text = file.lines.get(line - 1).strip();
        if (text.length() > MAX_SNIPPET) {
            return text.substring(0, MAX_SNIPPET - 3) + "...";
        }
        return text;
    }

    private static int matchingBrace(String text, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Counts top-level arguments of the call whose opening parenthesis is at {@code openParen}. */
    private static int countArguments(String text, int openParen) {
        int depth = 0;
        int commas = 0;
        boolean content = false;
        for (int i = openParen; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')') {
                depth--;
                if (depth == 0) {
                    return content ? commas + 1 : 0;
                }
                content = true;
                continue;
            }
            if (c == ',' && depth == 1) {
                commas++;
                content = false;
                continue;
            }
            if (depth >= 1 && !Character.isWhitespace(c)) {
                content = true;
            }
        }
        return -1;
    }

    private static final class LineIndex {
        private final int[] starts;

        LineIndex(String text) {
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    offsets.add(i + 1);
                }
            }
            starts = offsets.stream().mapToInt(Integer::intValue).toArray();
        }

        int lineOf(int offset) {
            int low = 0;
            int high = starts.length - 1;
            while (low < high) {
                int mid = (low + high + 1) / 2;
                if (starts[mid] <= offset) {
                    low = mid;
                } else {
                    high = mid - 1;
                }
            }
            return low + 1;
        }
    }

    private static String formatFloat(float value) {
        return value == Math.round(value) ? String.valueOf((int) value) : Float.toString(value);
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                int end = i > start && text.charAt(i - 1) == '\r' ? i - 1 : i;
                lines.add(text.substring(start, end));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        } else if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    /**
     * Replaces comments and string/char literal contents with spaces, preserving length and newlines.
     */
    static String mask(String source) {
        char[] out = source.toCharArray();
        int i = 0;
        int n = out.length;
        while (i < n) {
            char c = out[i];
            if (c == '/' && i + 1 < n && out[i + 1] == '/') {
                while (i < n && out[i] != '\n') {
                    out[i] = ' ';
                    i++;
                }
            } else if (c == '/' && i + 1 < n && out[i + 1] == '*') {
                out[i] = ' ';
                out[i + 1] = ' ';
                i += 2;
                while (i < n && !(out[i] == '*' && i + 1 < n && out[i + 1] == '/')) {
                    if (out[i] != '\n') {
                        out[i] = ' ';
                    }
                    i++;
                }
                if (i < n) {
                    out[i] = ' ';
                    if (i + 1 < n) {
                        out[i + 1] = ' ';
                    }
                    i += 2;
                }
            } else if (c == '"' && i + 2 < n && out[i + 1] == '"' && out[i + 2] == '"') {
                out[i] = ' ';
                out[i + 1] = ' ';
                out[i + 2] = ' ';
                i += 3;
                while (i < n) {
                    if (out[i] == '"' && i + 2 < n && out[i + 1] == '"' && out[i + 2] == '"') {
                        out[i] = ' ';
                        out[i + 1] = ' ';
                        out[i + 2] = ' ';
                        i += 3;
                        break;
                    }
                    if (out[i] != '\n') {
                        out[i] = ' ';
                    }
                    i++;
                }
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out[i] = ' ';
                i++;
                while (i < n) {
                    char d = out[i];
                    if (d == '\\') {
                        out[i] = ' ';
                        if (i + 1 < n) {
                            if (out[i + 1] != '\n') {
                                out[i + 1] = ' ';
                            }
                            i += 2;
                        } else {
                            i++;
                        }
                        continue;
                    }
                    if (d == quote) {
                        out[i] = ' ';
                        i++;
                        break;
                    }
                    if (d == '\n') {
                        break;
                    }
                    out[i] = ' ';
                    i++;
                }
            } else {
                i++;
            }
        }
        return new String(out);
    }
}
