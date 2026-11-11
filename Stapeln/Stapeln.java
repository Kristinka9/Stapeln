import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Das Spiel "Stapeln".
 *
 * Spielanleitung:
 * Ein Block bewegt sich von links nach rechts und zurück.
 * Mit einem Mausklick oder der Leertaste lässt man den Block fallen.
 * Alles, was über den unteren Block hinausragt, wird abgeschnitten.
 *
 * Die Klasse erbt von JPanel (Spielfläche) und implementiert drei Interfaces:
 * - ActionListener: Wird vom Timer ungefähr 60 Mal pro Sekunde aufgerufen.
 * - KeyListener: Reagiert auf Tastatureingaben.
 * - MouseListener: Reagiert auf Mausklicks.
 */
public class Stapeln extends JPanel implements ActionListener, KeyListener, MouseListener {

    // ---------- Konstanten ----------
    private static final int W = 360;          // Breite des Spielfeldes in Pixeln
    private static final int H = 560;          // Höhe des Spielfeldes
    private static final int BH = 26;          // Höhe eines Blocks
    private static final int BASE = H - 80;    // Y-Position des untersten Blocks
    private static final Color BG = new Color(0x12142b);    // Hintergrundfarbe
    private static final Color TEXT = new Color(0xeceefb);  // Textfarbe

    // Die drei Spielzustände: Start, laufendes Spiel und Spielende.
    private enum State { READY, PLAY, OVER }

    // Ein Block im Turm: x = linke Position, w = Breite.
    // Die Höhe wird anhand der Position in der Liste berechnet.
    private static class Block {
        double x, w;
        Block(double x, double w) { this.x = x; this.w = w; }
    }

    // Ein abgeschnittenes Blockstück, das nach unten fällt.
    // y = aktuelle Position, vy = Fallgeschwindigkeit, level = Ebene für die Farbe.
    private static class Debris {
        double x, w, y, vy;
        int level;
        Debris(double x, double w, double y, int level) {
            this.x = x; this.w = w; this.y = y; this.level = level;
        }
    }

    // ---------- Variablen für den Spielzustand ----------

    // Preferences speichert Daten dauerhaft, zum Beispiel den Rekord.
    // Unter Windows werden diese Daten in der Registry gespeichert.
    private final Preferences prefs = Preferences.userNodeForPackage(Stapeln.class);

    private final List<Block> blocks = new ArrayList<>();   // Alle platzierten Blöcke
    private final List<Debris> debris = new ArrayList<>();  // Herunterfallende Blockstücke
    private final Timer timer = new Timer(16, this);        // Timer für ungefähr 60 FPS

    private Block cur;                 // Aktueller beweglicher Block
    private State state = State.READY; // Aktueller Spielzustand
    private int dir = 1;               // Bewegungsrichtung: 1 = rechts, -1 = links
    private int score = 0;             // Punktzahl = Anzahl der platzierten Blöcke
    private int best;                  // Höchster Rekord
    private int combo = 0;             // Anzahl perfekter Treffer hintereinander
    private double cam = 0;            // Kameraverschiebung bei einem hohen Turm
    private long lastNanos = System.nanoTime(); // Zeitpunkt des letzten Frames
    private long overAt = 0;           // Zeitpunkt, an dem das Spiel beendet wurde
    private String message = "";       // Nachricht für den Spieler

    // Konstruktor: Erstellt das Spielfeld und startet den Timer.
    public Stapeln() {
        best = prefs.getInt("best", 0);              // Gespeicherten Rekord laden
        setPreferredSize(new Dimension(W, H));       // Größe des Spielfeldes festlegen
        setFocusable(true);                          // Aktiviert die Tastatureingaben
        addKeyListener(this);
        addMouseListener(this);
        blocks.add(new Block((W - 200) / 2.0, 200)); // Ersten Block in der Mitte platzieren
        timer.start();                               // Spiel-Timer starten
    }

    // ---------- Spiellogik ----------

    // Startet ein neues Spiel und setzt alle Werte zurück.
    private void start() {
        blocks.clear();
        debris.clear();
        blocks.add(new Block((W - 200) / 2.0, 200));
        score = 0;
        combo = 0;
        cam = 0;
        message = "";
        state = State.PLAY;
        spawn();
    }

    // Erstellt einen neuen beweglichen Block über dem Turm.
    // Der Block erscheint abwechselnd links und rechts.
    private void spawn() {
        Block top = blocks.get(blocks.size() - 1);
        boolean fromLeft = blocks.size() % 2 == 0;
        cur = new Block(fromLeft ? 0 : W - top.w, top.w);
        dir = fromLeft ? 1 : -1;
    }

    // Lässt den aktuellen Block auf den Turm fallen.
    private void drop() {
        // Im Startbildschirm und nach dem Spielende kann ein neues Spiel starten.
        if (state == State.READY) { start(); return; }
        if (state == State.OVER) {
            // Eine kurze Pause verhindert einen sofortigen Neustart.
            if (System.currentTimeMillis() - overAt > 500) start();
            return;
        }

        Block top = blocks.get(blocks.size() - 1);  // Oberster Block des Turms
        int level = blocks.size();                  // Nummer der neuen Ebene

        // Berechnet die Überlappung zwischen den beiden Blöcken.
        double left = Math.max(cur.x, top.x);
        double right = Math.min(cur.x + cur.w, top.x + top.w);
        double overlap = right - left;
        double y = BASE - level * BH;               // Y-Position des neuen Blocks

        // Wenn sich die Blöcke nicht überlappen, ist das Spiel vorbei.
        if (overlap <= 0) {
            debris.add(new Debris(cur.x, cur.w, y, level));
            gameOver();
            return;
        }

        // Bei einer Abweichung von weniger als 5 Pixeln gilt der Treffer als perfekt.
        // Der Block wird dann nicht abgeschnitten.
        if (Math.abs(cur.x - top.x) < 5) {
            cur.x = top.x;
            cur.w = top.w;
            combo++;
            message = combo > 1 ? "Perfekt! " + combo + " in Folge" : "Perfekt!";
        } else {
            // Ein Teil des Blocks wird abgeschnitten.
            combo = 0;
            message = "";

            if (cur.x < top.x) {
                // Der überstehende Teil befindet sich links.
                debris.add(new Debris(cur.x, top.x - cur.x, y, level));
            } else {
                // Der überstehende Teil befindet sich rechts.
                debris.add(new Debris(right, cur.x + cur.w - right, y, level));
            }

            cur.x = left;      // Nur der überlappende Teil bleibt übrig.
            cur.w = overlap;
        }

        blocks.add(new Block(cur.x, cur.w));  // Block zum Turm hinzufügen
        score++;                              // Punktzahl erhöhen
        spawn();                              // Nächsten Block erstellen
    }

    // Beendet das Spiel und speichert einen neuen Rekord.
    private void gameOver() {
        state = State.OVER;
        overAt = System.currentTimeMillis();
        cur = null;

        if (score > best) {
            best = score;
            prefs.putInt("best", best);   // Neuen Rekord speichern
            message = "Neuer Rekord: " + score + " Blöcke!";
        } else {
            message = "Du hast " + score + " Blöcke gestapelt.";
        }
    }

    // Aktualisiert die Spielwelt anhand der vergangenen Zeit (dt in Sekunden).
    // Dadurch bleibt die Bewegung auf verschiedenen Computern gleichmäßig.
    private void update(double dt) {
        if (state == State.PLAY && cur != null) {
            // Die Geschwindigkeit steigt mit jedem Block, maximal bis 430 Pixel pro Sekunde.
            double speed = Math.min(150 + score * 7, 430);
            cur.x += dir * speed * dt;

            // Der Block prallt an den linken und rechten Wänden ab.
            if (cur.x < 0) { cur.x = 0; dir = 1; }
            if (cur.x + cur.w > W) { cur.x = W - cur.w; dir = -1; }
        }

        // Die Kamera bewegt sich nach unten, wenn der Turm höher als 6 Blöcke wird.
        double target = Math.max(0, (blocks.size() - 6) * BH);
        cam += (target - cam) * Math.min(1, dt * 6);

        // Die abgeschnittenen Blockstücke fallen durch die Schwerkraft nach unten.
        for (Debris d : debris) {
            d.vy += 1400 * dt;
            d.y += d.vy * dt;
        }

        // Entfernt Blockstücke, die außerhalb des Spielfeldes gefallen sind.
        debris.removeIf(d -> d.y + cam > H + 60);
    }

    // ---------- Zeichnen des Spiels ----------

    // Die Farbe eines Blocks hängt von seiner Ebene ab.
    private Color color(int i) {
        return Color.getHSBColor(((i * 13 + 210) % 360) / 360f, 0.6f, 0.95f);
    }

    // Zeichnet einen abgerundeten Block.
    // alpha bestimmt die Transparenz (1 = vollständig sichtbar).
    private void drawBlock(Graphics2D g, double x, int level, double w, double y, float alpha) {
        Color c = color(level);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (alpha * 255)));
        g.fillRoundRect((int) x, (int) y, (int) w, BH - 3, 8, 8);
    }

    // Zeichnet einen Text horizontal in der Mitte des Spielfeldes.
    private void centered(Graphics2D g, String s, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (W - fm.stringWidth(s)) / 2, y);
    }

    // Zeichnet das gesamte Spielfeld neu.
    // Swing ruft diese Methode automatisch nach repaint() auf.
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;

        // Aktiviert Kantenglättung für bessere Darstellung von Text und Formen.
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Hintergrund zeichnen.
        g.setColor(BG);
        g.fillRect(0, 0, W, H);

        // Alle bereits platzierten Blöcke des Turms zeichnen.
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            drawBlock(g, b.x, i, b.w, BASE - i * BH + cam, 1f);
        }

        // Aktuellen beweglichen Block zeichnen.
        if (cur != null) {
            drawBlock(g, cur.x, blocks.size(), cur.w, BASE - blocks.size() * BH + cam, 1f);
        }

        // Herunterfallende Blockstücke mit etwas Transparenz zeichnen.
        for (Debris d : debris) {
            drawBlock(g, d.x, d.level, d.w, d.y + cam, 0.8f);
        }

        // Punktzahl und Rekord oben im Spielfeld anzeigen.
        g.setColor(TEXT);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("Punkte: " + score, 14, 28);
        String r = "Rekord: " + best;
        g.drawString(r, W - 14 - g.getFontMetrics().stringWidth(r), 28);

        g.setFont(new Font("SansSerif", Font.BOLD, 16));

        if (state == State.PLAY) {
            centered(g, message, 60);   // Nachricht bei einem perfekten Treffer
        } else {
            // Halbtransparenter Hintergrund für den Startbildschirm und Game Over.
            g.setColor(new Color(0, 0, 0, 140));
            g.fillRect(0, H / 2 - 70, W, 140);

            g.setColor(TEXT);
            g.setFont(new Font("SansSerif", Font.BOLD, 30));
            centered(g, state == State.READY ? "Stapeln" : "Game Over", H / 2 - 20);

            g.setFont(new Font("SansSerif", Font.PLAIN, 15));

            if (state == State.OVER) centered(g, message, H / 2 + 10);

            centered(g, "Klicke oder drücke die Leertaste", H / 2 + 38);
        }
    }

    // ---------- Spielzyklus und Eingaben ----------

    // Wird alle 16 Millisekunden vom Timer aufgerufen.
    // Aktualisiert die Spielwelt und startet die Neuzeichnung.
    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();

        // Begrenzt dt, damit die Blöcke nach einer Verzögerung nicht zu weit springen.
        double dt = Math.min((now - lastNanos) / 1e9, 0.05);
        lastNanos = now;

        update(dt);
        repaint();   // Spielfeld neu zeichnen
    }

    // Die Leertaste lässt den aktuellen Block fallen.
    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) drop();
    }

    // Ein Mausklick lässt den Block fallen.
    // requestFocusInWindow sorgt dafür, dass die Tastatur wieder funktioniert.
    @Override public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        drop();
    }

    // Diese Methoden werden vom Interface benötigt, aber hier nicht verwendet.
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    // Startpunkt des Programms.
    public static void main(String[] args) {

        // Swing-Fenster müssen im Event-Dispatch-Thread erstellt werden.
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Stapeln");                  // Spielfenster erstellen
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);  // Programm beim Schließen beenden
            f.setResizable(false);

            Stapeln game = new Stapeln();

            f.add(game);                                       // Spielfeld zum Fenster hinzufügen
            f.pack();                                          // Fenstergröße automatisch anpassen
            f.setLocationRelativeTo(null);                     // Fenster in der Bildschirmmitte platzieren
            f.setVisible(true);                                // Fenster anzeigen

            game.requestFocusInWindow();                       // Tastatureingaben aktivieren
        });
    }
}
