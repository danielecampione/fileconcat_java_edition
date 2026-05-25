package it.fileconcat_java_edition;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.*;
import javafx.util.Duration;
import java.io.File;
import java.util.*;

public class MergeGUI {

    // ── HiDPI ─────────────────────────────────────────────────────────────────
    private static final double BASE_DPI = 96.0;
    private static final double SCALE;
    static {
        double dpi;
        try { dpi = javafx.stage.Screen.getPrimary().getDpi(); }
        catch (Exception e) { dpi = BASE_DPI; }
        SCALE = Math.min(3.0, Math.max(1.0, dpi / BASE_DPI));
    }
    private static int    s(int v)    { return (int) Math.round(v * SCALE); }
    private static double sd(double v) { return v * SCALE; }

    // ── Estensioni ────────────────────────────────────────────────────────────
    private static final String[] EXTENSIONS = {
        "txt",  "md",    "rst",  "tex",  "html",  "htm",
        "css",  "xml",   "yaml", "yml",  "toml",  "ini",
        "json", "csv",
        "sh",   "bash",  "zsh",  "bat",  "cmd",   "ps1",
        "py",   "pyw",
        "js",   "ts",    "jsx",  "tsx",  "vue",   "svelte",
        "java", "kt",    "scala","groovy",
        "c",    "cpp",   "cc",   "cxx",  "h",     "hpp",
        "cs",   "vb",
        "sql",
        "tf",   "hcl",   "env",  "dockerfile",
        "go",   "rs",    "swift","dart",
        "rb",   "php",   "pl",   "pm",
        "r",    "lua",   "ex",   "exs",
        "conf", "cfg",   "properties", "prefs"
    };
    private static final Set<String> DEFAULT_ON =
            new HashSet<String>(Arrays.asList("txt", "md"));

    // ── Colori ────────────────────────────────────────────────────────────────
    private static final String C_BG      = "#f7f5f2";
    private static final String C_SURFACE = "#ffffff";
    private static final String C_ACCENT  = "#e05c2a";
    private static final String C_ACCH    = "#c94d1f";
    private static final String C_DROP    = "#fff8f5";
    private static final String C_DROP_A  = "#fde8df";
    private static final String C_OK      = "#2d9e6b";
    private static final String C_ERR     = "#d63b3b";
    private static final String C_TEXT    = "#1a1a1a";
    private static final String C_MUTED   = "#888077";
    private static final String C_BORDER  = "#ddd8d0";
    private static final String C_SECBTN  = "#ddd8d0";

    // ── Stato ─────────────────────────────────────────────────────────────────
    private Stage   stage;
    private BorderPane root;                          // tenuto per l'animazione di uscita
    private List<String>          sources  = new ArrayList<String>();
    private Map<String, CheckBox> extBoxes = new LinkedHashMap<String, CheckBox>();
    private boolean               running  = false;

    // ── Widget ────────────────────────────────────────────────────────────────
    private Label       dropLabel;
    private VBox        dropPane;
    private TextField   outputField;
    private ProgressBar progressBar;
    private Label       statusLabel;
    private Button      runButton;

    // ── Animazione status (interrompibile) ────────────────────────────────────
    private Animation statusAnim;

    // ── Costruttore ───────────────────────────────────────────────────────────
    public MergeGUI(Stage stage) {
        this.stage = stage;
    }

    // =========================================================================
    //  AVVIO
    // =========================================================================
    public void show() {
        stage.setTitle("Unisci File");
        stage.setMinWidth(s(560));
        stage.setMinHeight(s(720));

        root = new BorderPane();
        root.setStyle("-fx-background-color: " + C_BG + ";");
        root.setTop(buildHeader());

        ScrollPane scroll = new ScrollPane(buildBody());
        scroll.setFitToWidth(true);
        scroll.setStyle(
            "-fx-background-color: " + C_BG + ";" +
            "-fx-border-color: transparent;"
        );
        root.setCenter(scroll);

        // Stato iniziale per l'animazione d'ingresso
        root.setOpacity(0);
        root.setScaleX(0.92);
        root.setScaleY(0.92);
        root.setTranslateY(s(28));

        Scene scene = new Scene(root, s(640), s(840));
        stage.setScene(scene);
        stage.show();

        playEntrance();
        hookCloseAnimation();
    }

    // =========================================================================
    //  ANIMAZIONI GLOBALI
    // =========================================================================

    /** Animazione d'ingresso: fade + scala + scivolata verso l'alto. */
    private void playEntrance() {
        FadeTransition      fade  = fade(root, 0, 1,    420, Interpolator.EASE_OUT);
        ScaleTransition     scale = scale(root, 0.92, 1.0, 420, Interpolator.EASE_OUT);
        TranslateTransition slide = slideY(root, s(28), 0,  400, Interpolator.EASE_OUT);

        ParallelTransition entrance = new ParallelTransition(fade, scale, slide);
        entrance.play();
    }

    /**
     * Intercetta la chiusura della finestra: fade + rimpicciolimento + rotazione,
     * poi termina davvero l'applicazione.
     */
    private void hookCloseAnimation() {
        stage.setOnCloseRequest(event -> {
            event.consume();   // blocca la chiusura immediata

            FadeTransition      fade   = fade(root, 1, 0,    480, Interpolator.EASE_IN);
            ScaleTransition     shrink = scale(root, 1.0, 0.12, 480, Interpolator.EASE_IN);
            RotateTransition    spin   = rotate(root, 0, 18,    480, Interpolator.EASE_IN);

            ParallelTransition exit = new ParallelTransition(fade, shrink, spin);
            exit.setOnFinished(e -> Platform.exit());
            exit.play();
        });
    }

    // =========================================================================
    //  HEADER
    // =========================================================================
    private HBox buildHeader() {
        HBox hdr = new HBox(s(16));
        hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setPadding(new Insets(s(14), s(24), s(14), s(24)));
        hdr.setStyle(
            "-fx-background-color: " + C_SURFACE + ";" +
            "-fx-border-color: transparent transparent " + C_BORDER + " transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );

        Label title = new Label("\uD83D\uDCC4  Unisci File");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, sd(24)));
        title.setStyle("-fx-text-fill: " + C_ACCENT + ";");

        Label sub = new Label("Raccoglie i tuoi file di testo in uno solo");
        sub.setFont(Font.font("SansSerif", sd(14)));
        sub.setStyle("-fx-text-fill: " + C_MUTED + ";");

        hdr.getChildren().addAll(title, sub);
        return hdr;
    }

    // =========================================================================
    //  BODY
    // =========================================================================
    private VBox buildBody() {
        VBox body = new VBox();
        body.setPadding(new Insets(s(16), s(24), s(24), s(24)));
        body.setStyle("-fx-background-color: " + C_BG + ";");

        body.getChildren().add(sectionLabel("Trascina qui file, cartelle o archivi .zip"));
        body.getChildren().add(buildDropZone());
        body.getChildren().add(spacer(s(8)));
        body.getChildren().add(buildBrowseRow());
        body.getChildren().add(spacer(s(14)));

        body.getChildren().add(sectionLabel("Nome del file da creare"));
        body.getChildren().add(buildOutputCard());
        body.getChildren().add(spacer(s(14)));

        body.getChildren().add(sectionLabel("Che tipo di file vuoi raccogliere?"));
        body.getChildren().add(buildExtCard());
        body.getChildren().add(spacer(s(14)));

        progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(s(8));
        progressBar.setStyle("-fx-accent: " + C_ACCENT + ";");
        body.getChildren().add(progressBar);

        statusLabel = new Label(" ");
        statusLabel.setFont(Font.font("SansSerif", sd(14)));
        statusLabel.setStyle("-fx-text-fill: " + C_MUTED + ";");
        statusLabel.setPadding(new Insets(s(4), 0, s(4), 0));
        body.getChildren().add(statusLabel);

        runButton = buildRunButton();
        body.getChildren().add(runButton);

        return body;
    }

    // =========================================================================
    //  DROP ZONE
    // =========================================================================
    private VBox buildDropZone() {
        dropPane = new VBox();
        dropPane.setAlignment(Pos.CENTER);
        dropPane.setPrefHeight(s(120));
        dropPane.setMaxWidth(Double.MAX_VALUE);
        dropPane.setPadding(new Insets(s(16)));
        setDropStyle(false);

        dropLabel = new Label("\u2B07  Trascina qui\nfile  \u00B7  cartelle  \u00B7  .zip");
        dropLabel.setFont(Font.font("SansSerif", sd(16)));
        dropLabel.setStyle("-fx-text-fill: " + C_MUTED + ";");
        dropLabel.setTextAlignment(TextAlignment.CENTER);
        dropLabel.setWrapText(true);
        dropLabel.setAlignment(Pos.CENTER);
        dropPane.getChildren().add(dropLabel);

        dropPane.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                setDropStyle(true);
            }
            event.consume();
        });
        dropPane.setOnDragExited(event -> {
            setDropStyle(false);
            event.consume();
        });
        dropPane.setOnDragDropped(event -> {
            setDropStyle(false);
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<String> paths = new ArrayList<String>();
                for (File f : db.getFiles()) paths.add(f.getAbsolutePath());
                setSources(paths);
                // Piccolo "rimbalzo" della drop zone al rilascio
                pulseNode(dropPane, 1.025);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        return dropPane;
    }

    private void setDropStyle(boolean active) {
        String bg     = active ? C_DROP_A : C_DROP;
        String border = active ? C_ACCENT  : C_BORDER;
        dropPane.setStyle(
            "-fx-background-color: " + bg + ";" +
            "-fx-border-color: " + border + ";" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;"
        );
    }

    // =========================================================================
    //  RIGA PULSANTI SFOGLIA
    // =========================================================================
    private HBox buildBrowseRow() {
        HBox row = new HBox(s(8));

        Button folderBtn = secondaryButton("Scegli cartella\u2026");
        folderBtn.setOnAction(e -> chooseFolder());

        Button filesBtn = secondaryButton("Scegli file\u2026");
        filesBtn.setOnAction(e -> chooseFiles());

        row.getChildren().addAll(folderBtn, filesBtn);
        return row;
    }

    // =========================================================================
    //  CARD NOME OUTPUT
    // =========================================================================
    private VBox buildOutputCard() {
        VBox card = new VBox();
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(
            "-fx-background-color: " + C_SURFACE + ";" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;"
        );

        outputField = new TextField("output.txt");
        outputField.setFont(Font.font("SansSerif", sd(16)));
        outputField.setMaxWidth(Double.MAX_VALUE);
        outputField.setStyle(
            "-fx-text-fill: " + C_TEXT + ";" +
            "-fx-background-color: transparent;" +
            "-fx-background-insets: 0;" +
            "-fx-padding: " + s(10) + " " + s(14) + " " + s(10) + " " + s(14) + ";" +
            "-fx-border-color: transparent;"
        );

        // Micro-scala al focus
        outputField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), card);
            st.setToX(isFocused ? 1.008 : 1.0);
            st.setToY(isFocused ? 1.008 : 1.0);
            st.setInterpolator(Interpolator.EASE_BOTH);
            st.play();
        });

        card.getChildren().add(outputField);
        return card;
    }

    // =========================================================================
    //  CARD ESTENSIONI
    // =========================================================================
    private VBox buildExtCard() {
        VBox card = new VBox();
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(
            "-fx-background-color: " + C_SURFACE + ";" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;"
        );

        // GridPane: 4 colonne di larghezza percentuale identica → incolonnamento perfetto
        GridPane grid = new GridPane();
        grid.setHgap(s(4));
        grid.setVgap(s(6));
        grid.setPadding(new Insets(s(12), s(14), s(8), s(14)));
        grid.setStyle("-fx-background-color: " + C_SURFACE + ";");

        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);          // 4 colonne esattamente uguali
            cc.setHalignment(HPos.LEFT);
            cc.setFillWidth(true);           // ogni checkbox occupa tutta la cella
            grid.getColumnConstraints().add(cc);
        }

        int col = 0, row = 0;
        for (String ext : EXTENSIONS) {
            final CheckBox cb = new CheckBox("." + ext);
            cb.setSelected(DEFAULT_ON.contains(ext));
            cb.setFont(Font.font("SansSerif", sd(14)));
            cb.setStyle("-fx-text-fill: " + C_TEXT + ";");
            cb.setMaxWidth(Double.MAX_VALUE);  // riempie la cella in larghezza
            attachCheckBoxAnimations(cb);
            extBoxes.put(ext, cb);
            grid.add(cb, col, row);
            if (++col == 4) { col = 0; row++; }
        }

        ScrollPane extScroll = new ScrollPane(grid);
        extScroll.setFitToWidth(true);
        extScroll.setPrefHeight(s(200));
        extScroll.setStyle(
            "-fx-background-color: " + C_SURFACE + ";" +
            "-fx-border-color: transparent;"
        );

        HBox ctrl = new HBox(s(8));
        ctrl.setPadding(new Insets(s(8), s(14), s(8), s(14)));
        ctrl.setStyle(
            "-fx-border-color: " + C_BORDER + " transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;" +
            "-fx-background-color: " + C_SURFACE + ";"
        );

        Button selAll   = secondaryButton("Seleziona tutti");
        Button deselAll = secondaryButton("Deseleziona tutti");
        selAll.setOnAction(e   -> setAll(true));
        deselAll.setOnAction(e -> setAll(false));

        ctrl.getChildren().addAll(selAll, deselAll);
        card.getChildren().addAll(extScroll, ctrl);
        return card;
    }

    /**
     * Hover: leggera scala verso l'alto.
     * Click (selezione/deselezione): micro-rimbalzo.
     */
    private void attachCheckBoxAnimations(CheckBox cb) {
        // Hover: scala su
        cb.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(130), cb);
            st.setToX(1.10); st.setToY(1.10);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
        // Hover: scala giù
        cb.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), cb);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_IN);
            st.play();
        });
        // Click: rimbalzino
        cb.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(80), cb);
            st.setToX(0.90); st.setToY(0.90);
            st.setInterpolator(Interpolator.EASE_IN);
            st.play();
        });
        cb.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(180), cb);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
    }

    // =========================================================================
    //  BOTTONE PRINCIPALE
    // =========================================================================
    private Button buildRunButton() {
        final Button btn = new Button("Unisci i file  \u2192");
        btn.setFont(Font.font("SansSerif", FontWeight.BOLD, sd(17)));
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(s(60));
        btn.setCursor(Cursor.HAND);
        applyAccentStyle(btn, false);

        // Hover: colore + leggera elevazione
        btn.setOnMouseEntered(e -> {
            if (!running) {
                applyAccentStyle(btn, true);
                TranslateTransition tt = slideY(btn, 0, -s(2), 100, Interpolator.EASE_OUT);
                tt.play();
            }
        });
        btn.setOnMouseExited(e -> {
            if (!running) {
                applyAccentStyle(btn, false);
                TranslateTransition tt = slideY(btn, btn.getTranslateY(), 0, 120, Interpolator.EASE_IN);
                tt.play();
            }
        });

        // Press: schiacciamento
        btn.setOnMousePressed(e -> {
            if (!running) {
                ScaleTransition st = scale(btn, 1.0, 0.96, 80, Interpolator.EASE_IN);
                st.play();
            }
        });
        btn.setOnMouseReleased(e -> {
            if (!running) {
                ScaleTransition st = scale(btn, btn.getScaleX(), 1.0, 160, Interpolator.EASE_OUT);
                st.play();
            }
        });

        btn.setOnAction(e -> runMerge());
        return btn;
    }

    private void applyAccentStyle(Button btn, boolean hover) {
        String bg = hover ? C_ACCH : C_ACCENT;
        btn.setStyle(
            "-fx-background-color: " + bg + ";" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 6;" +
            "-fx-border-radius: 6;"
        );
    }

    // =========================================================================
    //  WIDGET HELPERS
    // =========================================================================
    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Georgia", sd(16)));
        l.setStyle("-fx-text-fill: " + C_TEXT + ";");
        l.setPadding(new Insets(s(6), 0, s(4), 0));
        return l;
    }

    /** Pulsante secondario con hover (sollevamento) e press (schiacciamento). */
    private Button secondaryButton(String text) {
        final Button b = new Button(text);
        b.setFont(Font.font("SansSerif", sd(14)));
        b.setCursor(Cursor.HAND);
        b.setPadding(new Insets(s(6), s(12), s(6), s(12)));
        b.setStyle(
            "-fx-background-color: " + C_SECBTN + ";" +
            "-fx-text-fill: " + C_TEXT + ";" +
            "-fx-background-radius: 4;" +
            "-fx-border-radius: 4;"
        );

        b.setOnMouseEntered(e -> {
            TranslateTransition tt = slideY(b, 0, -s(2), 100, Interpolator.EASE_OUT);
            tt.play();
        });
        b.setOnMouseExited(e -> {
            TranslateTransition tt = slideY(b, b.getTranslateY(), 0, 110, Interpolator.EASE_IN);
            tt.play();
        });
        b.setOnMousePressed(e -> {
            ScaleTransition st = scale(b, 1.0, 0.94, 70, Interpolator.EASE_IN);
            st.play();
        });
        b.setOnMouseReleased(e -> {
            ScaleTransition st = scale(b, b.getScaleX(), 1.0, 150, Interpolator.EASE_OUT);
            st.play();
        });

        return b;
    }

    private Region spacer(int height) {
        Region r = new Region();
        r.setMinHeight(height);
        return r;
    }

    // =========================================================================
    //  LOGICA SELEZIONE SORGENTI
    // =========================================================================
    private void setSources(final List<String> paths) {
        this.sources = paths;

        final String text;
        if (paths.size() == 1) {
            String name = new File(paths.get(0)).getName();
            text = "\u2713  " + (name.isEmpty() ? paths.get(0) : name);
        } else {
            text = "\u2713  " + paths.size() + " elementi selezionati";
        }
        // Crossfade del testo nella drop zone
        crossfadeLabel(dropLabel, text, "-fx-text-fill: " + C_OK + ";");
        setStatus("Scansione estensioni\u2026", C_MUTED);

        final List<String> pathsCopy = new ArrayList<String>(paths);
        new Thread(() -> {
            final List<String> found = BusinessLogic.scanExtensions(pathsCopy);
            Platform.runLater(() -> applyFoundExtensions(found));
        }).start();
    }

    private void applyFoundExtensions(List<String> found) {
        Set<String> foundSet = new HashSet<String>(found);
        for (Map.Entry<String, CheckBox> e : extBoxes.entrySet())
            e.getValue().setSelected(foundSet.contains(e.getKey()));

        List<String> matched = new ArrayList<String>();
        for (String ext : EXTENSIONS)
            if (foundSet.contains(ext)) matched.add(ext);

        if (!matched.isEmpty()) {
            StringBuilder sb = new StringBuilder("Trovate: ");
            for (int i = 0; i < matched.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(".").append(matched.get(i));
            }
            setStatus(sb.toString(), C_OK);
        } else {
            setStatus("Nessuna estensione riconosciuta. Seleziona manualmente.", C_MUTED);
        }
    }

    // =========================================================================
    //  DIALOG SFOGLIA
    // =========================================================================
    private void chooseFolder() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Scegli una cartella");
        File dir = dc.showDialog(stage);
        if (dir != null) setSources(Collections.singletonList(dir.getAbsolutePath()));
    }

    private void chooseFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Scegli uno o più file");
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            List<String> paths = new ArrayList<String>();
            for (File f : files) paths.add(f.getAbsolutePath());
            setSources(paths);
        }
    }

    // =========================================================================
    //  ESTENSIONI
    // =========================================================================
    private void setAll(boolean selected) {
        for (CheckBox cb : extBoxes.values()) cb.setSelected(selected);
    }

    // =========================================================================
    //  STATUS (con crossfade animato e interrompibile)
    // =========================================================================
    private void setStatus(final String msg, final String color) {
        if (statusAnim != null) statusAnim.stop();

        FadeTransition fadeOut = fade(statusLabel, statusLabel.getOpacity(), 0, 90, Interpolator.EASE_IN);
        fadeOut.setOnFinished(e -> {
            statusLabel.setText(msg);
            statusLabel.setStyle("-fx-text-fill: " + color + ";");
            FadeTransition fadeIn = fade(statusLabel, 0, 1, 160, Interpolator.EASE_OUT);
            statusAnim = fadeIn;
            fadeIn.play();
        });
        statusAnim = fadeOut;
        fadeOut.play();
    }

    // =========================================================================
    //  ESECUZIONE MERGE
    // =========================================================================
    private void runMerge() {
        if (running) return;

        if (sources.isEmpty()) {
            setStatus("\u26A0  Scegli o trascina almeno un file o cartella.", C_ERR);
            shakeNode(statusLabel);
            return;
        }

        final List<String> selected = new ArrayList<String>();
        for (Map.Entry<String, CheckBox> e : extBoxes.entrySet())
            if (e.getValue().isSelected()) selected.add(e.getKey());

        if (selected.isEmpty()) {
            setStatus("\u26A0  Scegli almeno un tipo di file.", C_ERR);
            shakeNode(statusLabel);
            return;
        }

        String outName = outputField.getText().trim();
        if (outName.isEmpty()) outName = "output.txt";
        final String finalOutName = outName;

        running = true;
        runButton.setDisable(true);
        applyAccentStyle(runButton, true);
        runButton.setText("\u231B  Un momento\u2026");
        progressBar.setVisible(true);
        progressBar.setProgress(-1.0);
        setStatus("Raccolta file in corso\u2026", C_MUTED);

        // Ingresso animato della progress bar
        progressBar.setOpacity(0);
        fade(progressBar, 0, 1, 300, Interpolator.EASE_OUT).play();

        final List<String> srcCopy = new ArrayList<String>(sources);
        new Thread(() -> {
            try {
                final String path = BusinessLogic.mergeFiles(srcCopy, selected, finalOutName);
                Platform.runLater(() -> onSuccess(path));
            } catch (final Exception ex) {
                Platform.runLater(() -> onError(ex.getMessage()));
            }
        }).start();
    }

    private void onSuccess(String path) {
        progressBar.setProgress(1.0);
        double sizeKb = new File(path).length() / 1024.0;
        setStatus(String.format("\u2713  Fatto! Salvato: %s  (%.1f KB)", path, sizeKb), C_OK);
        // Piccolo "festa" sul bottone: rimbalzo
        pulseNode(runButton, 1.04);
        resetRunButton();
    }

    private void onError(String msg) {
        progressBar.setProgress(0);
        setStatus("\u2717  Errore: " + msg, C_ERR);
        shakeNode(statusLabel);
        resetRunButton();
    }

    private void resetRunButton() {
        running = false;
        runButton.setDisable(false);
        applyAccentStyle(runButton, false);
        runButton.setText("Unisci i file  \u2192");
    }

    // =========================================================================
    //  ANIMAZIONI — helpers riutilizzabili
    // =========================================================================

    /** Crossfade del testo su una Label (fade out → swap testo → fade in). */
    private void crossfadeLabel(Label label, String newText, String newStyle) {
        FadeTransition out = fade(label, label.getOpacity(), 0, 110, Interpolator.EASE_IN);
        out.setOnFinished(e -> {
            label.setText(newText);
            label.setStyle(newStyle);
            fade(label, 0, 1, 180, Interpolator.EASE_OUT).play();
        });
        out.play();
    }

    /**
     * Vibrazione orizzontale (errore).
     * 5 mezzi cicli a 55 ms l'uno → durata totale ≈ 275 ms.
     */
    private void shakeNode(Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(55), node);
        tt.setFromX(0);
        tt.setByX(s(7));
        tt.setCycleCount(5);
        tt.setAutoReverse(true);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.setOnFinished(e -> node.setTranslateX(0));
        tt.play();
    }

    /**
     * Rimbalzo rapido: scala fino a {@code peak} e torna a 1.0.
     * Usato su drop avvenuto con successo e su merge completato.
     */
    private void pulseNode(Node node, double peak) {
        ScaleTransition grow = scale(node, 1.0, peak, 160, Interpolator.EASE_OUT);
        grow.setOnFinished(e -> scale(node, peak, 1.0, 200, Interpolator.EASE_IN).play());
        grow.play();
    }

    // ── Primitivi animazione ──────────────────────────────────────────────────

    private FadeTransition fade(Node n, double from, double to, int ms, Interpolator interp) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setFromValue(from);
        ft.setToValue(to);
        ft.setInterpolator(interp);
        return ft;
    }

    private ScaleTransition scale(Node n, double from, double to, int ms, Interpolator interp) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), n);
        st.setFromX(from); st.setFromY(from);
        st.setToX(to);     st.setToY(to);
        st.setInterpolator(interp);
        return st;
    }

    private TranslateTransition slideY(Node n, double from, double to, int ms, Interpolator interp) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), n);
        tt.setFromY(from);
        tt.setToY(to);
        tt.setInterpolator(interp);
        return tt;
    }

    private RotateTransition rotate(Node n, double from, double to, int ms, Interpolator interp) {
        RotateTransition rt = new RotateTransition(Duration.millis(ms), n);
        rt.setFromAngle(from);
        rt.setToAngle(to);
        rt.setInterpolator(interp);
        return rt;
    }
}
