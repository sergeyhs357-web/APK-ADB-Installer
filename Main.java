package apk.adb.installer;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Date;

// ============================================================
// СВЕТЛЫЙ МИНИМАЛИСТИЧНЫЙ СТИЛЬ
// ============================================================
class ModernStyle {
    public static final Color WINDOW_BG = Color.WHITE;                  // Чисто белый закругленный фон
    public static final Color CARD_BG = new Color(245, 247, 250);       // Светло-серые подложки панелей
    public static final Color TEXT_MAIN = new Color(33, 37, 41);        // Темный основной текст
    public static final Color TEXT_MUTED = new Color(108, 117, 125);    // Приглушенный серый текст

    // Элементы воды и акцентов
    public static final Color WATER_COLOR = new Color(0, 123, 255);     // Насыщенная синяя вода
    public static final Color WATER_BG = new Color(230, 242, 255);      // Светлая основа круглой шкалы

    // Статусы
    public static final Color SUCCESS = new Color(40, 167, 69);         // Зеленый
    public static final Color WARNING = new Color(255, 193, 7);         // Желтый
    public static final Color ERROR = new Color(220, 53, 69);           // Красный
}

// ============================================================
// КАСТОМНАЯ КРУГЛАЯ ШКАЛА С ЗАПОЛНЕНИЕМ ВОДОЙ
// ============================================================
class WaterProgressBar extends JComponent {
    private int value = 0;

    public void setValue(int newValue) {
        this.value = Math.max(0, Math.min(100, newValue));
        repaint();
    }

    public int getValue() {
        return this.value;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int size = Math.min(getWidth(), getHeight()) - 4;
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;

        // Рисуем задний фон круга (куда наливается вода)
        g2.setColor(ModernStyle.WATER_BG);
        g2.fill(new Ellipse2D.Float(x, y, size, size));

        // Вычисляем уровень воды по высоте
        int waterHeight = (int) (size * (value / 100.0));
        int waterY = y + size - waterHeight;

        if (waterHeight > 0) {
            // Создаем область отсечения, чтобы вода не выливалась за границы круглого стакана
            Shape clip = g2.getClip();
            Area circleArea = new Area(new Ellipse2D.Float(x, y, size, size));
            g2.setClip(circleArea);

            // Рисуем синюю воду
            g2.setColor(ModernStyle.WATER_COLOR);
            g2.fillRect(x, waterY, size, waterHeight);

            // Восстанавливаем исходную область отрисовки
            g2.setClip(clip);
        }

        // Рисуем аккуратную внешнюю границу круга
        g2.setColor(new Color(200, 215, 235));
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new Ellipse2D.Float(x, y, size, size));

        // Отрисовка процентов по центру круга
        String text = value + "%";
        g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
        FontMetrics fm = g2.getFontMetrics();
        int textX = x + (size - fm.stringWidth(text)) / 2;
        int textY = y + (size - fm.getHeight()) / 2 + fm.getAscent();

        // Если вода поднялась выше текста, меняем цвет шрифта на белый для читаемости
        if (waterY <= textY - fm.getAscent() / 2) {
            g2.setColor(Color.WHITE);
        } else {
            g2.setColor(ModernStyle.TEXT_MAIN);
        }
        g2.drawString(text, textX, textY);

        g2.dispose();
    }
}
// ============================================================
// КАСТОМНАЯ КРУГЛАЯ КНОПКА ДОБАВЛЕНИЯ APK
// ============================================================
class RoundAddButton extends JButton {
    private boolean isHovered = false;

    public RoundAddButton() {
        super();
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(45, 45));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
            @Override
            public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int size = Math.min(getWidth(), getHeight()) - 2;
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;

        // Отрисовка круглого фона кнопки
        if (isEnabled()) {
            g2.setColor(isHovered ? ModernStyle.WATER_COLOR.darker() : ModernStyle.WATER_COLOR);
        } else {
            g2.setColor(new Color(200, 205, 210));
        }
        g2.fill(new Ellipse2D.Float(x, y, size, size));

        // Рисуем белый знак плюс по центру
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int pad = size / 3;
        int cx = x + size / 2;
        int cy = y + size / 2;
        g2.drawLine(cx - pad + 2, cy, cx + pad - 2, cy); // Горизонтальная линия
        g2.drawLine(cx, cy - pad + 2, cx, cy + pad - 2); // Вертикальная линия

        g2.dispose();
        super.paintComponent(g);
    }
}

// ============================================================
// ADB MANAGER - ЯДРО УПРАВЛЕНИЯ СВЯЗЬЮ
// ============================================================
class ADBManager {
    private String ADB_PATH = "adb";
    private String deviceSerial = null;
    private final List<String> connectedDevices = new ArrayList<>();

    public ADBManager(String adbPath) {
        if (adbPath != null && !adbPath.isEmpty()) {
            this.ADB_PATH = adbPath;
        }
    }

    public boolean isADBInstalled() {
        try {
            Process process = Runtime.getRuntime().exec(ADB_PATH + " version");
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> getDevices() {
        connectedDevices.clear();
        try {
            Process process = Runtime.getRuntime().exec(ADB_PATH + " devices");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().endsWith("device")) {
                    String serial = line.split("\\s+")[0];
                    if (!serial.isEmpty()) connectedDevices.add(serial);
                }
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return connectedDevices;
    }

    public void setDevice(String serial) {
        this.deviceSerial = serial;
    }

    // Внутреннее переподключение устройства
    private boolean reconnectDevice(ProgressCallback callback) {
        try {
            if (callback != null) {
                callback.onProgress("Переподключение устройства...");
            }

            Runtime.getRuntime().exec(ADB_PATH + " kill-server").waitFor();
            Thread.sleep(1000);

            Runtime.getRuntime().exec(ADB_PATH + " start-server").waitFor();
            Thread.sleep(1500);

            int attempts = 0;
            while (attempts < 10) {
                List<String> devices = getDevices();
                if (!devices.isEmpty()) {
                    deviceSerial = devices.get(0);
                    if (callback != null) {
                        callback.onProgress("Устройство переподключено: " + deviceSerial);
                    }
                    return true;
                }
                Thread.sleep(1000);
                attempts++;
                if (callback != null) {
                    callback.onProgress("Ожидание устройства... " + attempts + "/10");
                }
            }
            return false;
        } catch (Exception e) {
            if (callback != null) {
                callback.onProgress("Ошибка переподключения: " + e.getMessage());
            }
            return false;
        }
    }
    // Проверка доступности устройства с повторными попытками
    private boolean isDeviceAvailable(ProgressCallback callback) {
        if (deviceSerial == null) {
            return false;
        }

        try {
            String command = String.format("%s -s %s shell echo 1", ADB_PATH, deviceSerial);
            Process process = Runtime.getRuntime().exec(command);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            int exitCode = process.waitFor();

            if (exitCode == 0 && "1".equals(line)) {
                return true;
            }

            if (callback != null) {
                callback.onProgress("Устройство недоступно, пробуем переподключить...");
            }

            Runtime.getRuntime().exec(ADB_PATH + " disconnect " + deviceSerial).waitFor();
            Thread.sleep(500);
            Runtime.getRuntime().exec(ADB_PATH + " connect " + deviceSerial).waitFor();
            Thread.sleep(1000);

            process = Runtime.getRuntime().exec(command);
            exitCode = process.waitFor();

            return exitCode == 0;

        } catch (Exception e) {
            return false;
        }
    }

    public String installAPK(String apkPath, ProgressCallback callback) {
        if (deviceSerial == null) {
            List<String> devices = getDevices();
            if (devices.isEmpty()) {
                if (!reconnectDevice(callback)) {
                    return "Ошибка: Нет устройств";
                }
            } else {
                deviceSerial = devices.get(0);
            }
        }

        if (!isDeviceAvailable(callback)) {
            if (!reconnectDevice(callback)) {
                return "Ошибка: Устройство недоступно";
            }
            if (!isDeviceAvailable(callback)) {
                return "Ошибка: Устройство не отвечает";
            }
        }

        String[] installCommands = {
                String.format("%s -s %s install -r \"%s\"", ADB_PATH, deviceSerial, apkPath),
                String.format("%s -s %s install -r -d \"%s\"", ADB_PATH, deviceSerial, apkPath),
                String.format("%s -s %s install -r -d -t \"%s\"", ADB_PATH, deviceSerial, apkPath)
        };

        for (int attempt = 0; attempt < installCommands.length; attempt++) {
            try {
                if (callback != null) {
                    callback.onProgress(String.format("Попытка %d/%d", attempt + 1, installCommands.length));
                }

                String command = installCommands[attempt];
                Process process = Runtime.getRuntime().exec(command);

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                String line;
                StringBuilder output = new StringBuilder();
                boolean success = false;

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (callback != null) callback.onProgress("   " + line);
                    if (line.contains("Success") || line.contains("success")) {
                        success = true;
                    }
                }

                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");
                    if (callback != null) callback.onProgress("Предупреждение: " + line);
                    if (line.contains("Success") || line.contains("success")) {
                        success = true;
                    }
                }

                int exitCode = process.waitFor();

                if (success || exitCode == 0) {
                    return "Успешно установлен";
                }

                if (output.toString().contains("INSTALL_FAILED_ALREADY_EXISTS")) {
                    return "Уже установлен";
                }

                if (attempt < installCommands.length - 1) {
                    if (callback != null) {
                        callback.onProgress("Ошибка, пробуем другой метод...");
                    }
                    Thread.sleep(1000);
                }

            } catch (Exception e) {
                if (callback != null) {
                    callback.onProgress("Ошибка: " + e.getMessage());
                }
                if (attempt < installCommands.length - 1) {
                    continue;
                }
                return "Исключение: " + e.getMessage();
            }
        }

        return "Не удалось установить (все методы)";
    }

    public void installMultipleAPKs(List<String> apkPaths, ProgressCallback callback) {
        if (apkPaths.size() > 15) {
            throw new IllegalArgumentException("Максимум 15 APK файлов");
        }

        int successful = 0;
        int failed = 0;
        int alreadyInstalled = 0;

        for (int i = 0; i < apkPaths.size(); i++) {
            String path = apkPaths.get(i);
            String fileName = new File(path).getName();

            if (callback != null) {
                callback.onProgress(String.format("Файл [%d/%d] %s", i + 1, apkPaths.size(), fileName));
            }

            String result = installAPK(path, callback);

            if (callback != null) {
                callback.onProgress("Результат: " + result);
                callback.onFileComplete(path, result);
            }

            if (result.contains("Успешно")) {
                successful++;
            } else if (result.contains("Уже")) {
                alreadyInstalled++;
            } else {
                failed++;
            }

            try { Thread.sleep(1000); } catch (InterruptedException e) {}

            if ((i + 1) % 2 == 0) {
                if (callback != null) {
                    callback.onProgress("Проверка подключения...");
                }
                if (!isDeviceAvailable(callback)) {
                    if (callback != null) {
                        callback.onProgress("Устройство отключилось, переподключаем...");
                    }
                    reconnectDevice(callback);
                }
            }
        }

        if (callback != null) {
            callback.onProgress(String.format("ИТОГО: Успешно %d, Уже установлены %d, Ошибок %d",
                    successful, alreadyInstalled, failed));
        }
    }

    public interface ProgressCallback {
        void onProgress(String message);
        void onFileComplete(String path, String result);
    }
}
// ============================================================
// МОДЕЛЬ ТАБЛИЦЫ ДАННЫХ
// ============================================================
class APKTableModel extends AbstractTableModel {
    private final List<Object[]> data = new ArrayList<>();
    private final String[] columns = {"Имя файла", "Размер", "Статус", "Путь к файлу"};

    public void addAPK(String name, String path, String size) {
        data.add(new Object[]{name, size, "Ожидает", path});
        fireTableRowsInserted(data.size() - 1, data.size() - 1);
    }

    public void removeAPK(int index) {
        if (index >= 0 && index < data.size()) {
            data.remove(index);
            fireTableRowsDeleted(index, index);
        }
    }

    public void clear() {
        data.clear();
        fireTableDataChanged();
    }

    public void updateStatus(int index, String status) {
        if (index >= 0 && index < data.size()) {
            data.get(index)[2] = status;
            fireTableCellUpdated(index, 2);
        }
    }

    public String getPath(int index) {
        return (String) data.get(index)[3];
    }

    public String getName(int index) {
        return (String) data.get(index)[0];
    }

    public int getSize() {
        return data.size();
    }

    @Override public int getRowCount() { return data.size(); }
    @Override public int getColumnCount() { return columns.length; }
    @Override public String getColumnName(int col) { return columns[col]; }
    @Override public Object getValueAt(int row, int col) { return data.get(row)[col]; }
}

// ============================================================
// ВСПОМОГАТЕЛЬНЫЕ ЗАКРУГЛЕННЫЕ КНОПКИ УПРАВЛЕНИЯ
// ============================================================
class ModernButton extends JButton {
    private final int radius;
    private final Color baseColor;
    private boolean isHovered = false;

    public ModernButton(String text, Color accentColor, int radius) {
        super(text);
        this.radius = radius;
        this.baseColor = accentColor;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setForeground(ModernStyle.TEXT_MAIN);
        setFont(new Font("Segoe UI", Font.BOLD, 13));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
            @Override
            public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (isEnabled()) {
            // Подсветка кнопки при наведении мыши
            g2.setColor(isHovered ? baseColor.brighter() : baseColor);
            if (baseColor.equals(ModernStyle.CARD_BG)) {
                setForeground(ModernStyle.TEXT_MAIN);
            } else {
                setForeground(Color.WHITE); // Белый текст для цветных кнопок
            }
        } else {
            g2.setColor(new Color(230, 235, 240));
            setForeground(ModernStyle.TEXT_MUTED);
        }

        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
        g2.dispose();
        super.paintComponent(g);
    }
}
// ============================================================
// ГЛАВНОЕ ОКНО ПРОГРАММЫ В СВЕТЛОМ МИНИМАЛИСТИЧНОМ СТИЛЕ
// ============================================================
public class Main extends JFrame {
    private ADBManager adbManager;
    private APKTableModel tableModel;
    private JTable table;
    private JTextArea logArea;
    private JButton installButton, removeButton, clearButton;
    private RoundAddButton addButton; // Наша новая круглая кнопка добавления
    private JComboBox<String> deviceComboBox;
    private JLabel statusLabel, deviceStatusLabel;
    private WaterProgressBar progressBar; // Наша новая круглая шкала с водой
    private JLabel progressLabel;
    private javax.swing.Timer refreshTimer;
    private JPanel mainPanel;

    private Point initialClick;
    private static final int MAX_APK = 15;
    private static final String LINE_SEPARATOR = "──────────────────────────────────────────────────────";

    public Main() {
        String adbPath = findAdbPath();
        adbManager = new ADBManager(adbPath);

        if (!adbManager.isADBInstalled()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "ADB не найден!\nХотите указать путь вручную?",
                    "Настройка системы", JOptionPane.YES_NO_OPTION);

            if (choice == JOptionPane.YES_OPTION) {
                String newPath = chooseAdbPath();
                if (newPath != null && !newPath.isEmpty()) {
                    adbManager = new ADBManager(newPath);
                }
            }
        }

        initUI();
        refreshDevices();

        refreshTimer = new javax.swing.Timer(3000, e -> refreshDevices());
        refreshTimer.start();
    }

    private String chooseAdbPath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Выберите adb.exe");
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile().getAbsolutePath();
        }
        return null;
    }

    private void initUI() {
        setTitle("APK ADB Installer PRO");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 850);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        // Ищем icon.png строго в одной папке рядом с запускаемым файлом
        try {
            String currentFolder = getJarDirectory();
            File localIcon = new File(currentFolder + File.separator + "icon.png");
            if (localIcon.exists()) {
                setIconImage(Toolkit.getDefaultToolkit().getImage(localIcon.getAbsolutePath()));
            } else {
                System.out.println("Файл icon.png не найден в папке с программой: " + currentFolder);
            }
        } catch (Exception e) {
            System.out.println("Ошибка при установке локальной иконки: " + e.getMessage());
        }

        mainPanel = new JPanel(new BorderLayout(15, 15)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ModernStyle.WINDOW_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
                g2.dispose();
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        mainPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                initialClick = e.getPoint();
                getComponentAt(initialClick);
            }
        });

        mainPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int thisX = getLocation().x;
                int thisY = getLocation().y;
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                setLocation(thisX + xMoved, thisY + yMoved);
            }
        });

        add(mainPanel);
        mainPanel.add(createTopBar(), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(15, 15));
        centerPanel.setOpaque(false);
        centerPanel.add(createDevicePanel(), BorderLayout.NORTH);
        centerPanel.add(createTablePanel(), BorderLayout.CENTER);
        centerPanel.add(createLogPanel(), BorderLayout.SOUTH);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        setVisible(true);
    }

    private JPanel createTopBar() {
        JPanel topBar = new JPanel(new BorderLayout(15, 0));
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setOpaque(false);

        JLabel title = new JLabel("APK ADB Installer PRO");
        title.setFont(new Font("Comic Sans MS", Font.BOLD, 20)); // Неровный шрифт
        title.setForeground(ModernStyle.TEXT_MAIN);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);

        // Кнопка сворачивания с белым минусом
        ModernButton minBtn = new ModernButton("—", ModernStyle.WATER_COLOR, 30);
        minBtn.setPreferredSize(new Dimension(35, 35));
        minBtn.setFont(new Font("Comic Sans MS", Font.BOLD, 14));
        minBtn.addActionListener(e -> setState(JFrame.ICONIFIED));

        // Прям ярко-красная кнопка закрытия с белым крестиком
        ModernButton closeBtn = new ModernButton("✕", Color.RED, 30);
        closeBtn.setPreferredSize(new Dimension(35, 35));
        closeBtn.setFont(new Font("Comic Sans MS", Font.BOLD, 14));
        closeBtn.addActionListener(e -> System.exit(0));

        rightPanel.add(minBtn);
        rightPanel.add(closeBtn);

        leftPanel.add(title);
        topBar.add(leftPanel, BorderLayout.WEST);
        topBar.add(rightPanel, BorderLayout.EAST);

        return topBar;
    }


    private JPanel createDevicePanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ModernStyle.CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));

        JPanel leftSide = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftSide.setOpaque(false);

        JLabel deviceLabel = new JLabel("Устройство:");
        deviceLabel.setFont(new Font("Comic Sans MS", Font.BOLD, 14));
        deviceLabel.setForeground(ModernStyle.TEXT_MUTED);

        deviceComboBox = new JComboBox<>();
        deviceComboBox.setPreferredSize(new Dimension(260, 36));
        deviceComboBox.setBackground(Color.WHITE);
        deviceComboBox.setForeground(ModernStyle.TEXT_MAIN);
        deviceComboBox.setFont(new Font("Comic Sans MS", Font.PLAIN, 13));
        deviceComboBox.setBorder(BorderFactory.createLineBorder(new Color(210, 215, 225), 1));

        deviceComboBox.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton btn = super.createArrowButton();
                btn.setBackground(Color.WHITE);
                btn.setBorder(BorderFactory.createEmptyBorder());
                return btn;
            }
        });

        ModernButton refreshBtn = new ModernButton("Обновить", ModernStyle.CARD_BG, 12);
        refreshBtn.setPreferredSize(new Dimension(110, 36));
        refreshBtn.setFont(new Font("Comic Sans MS", Font.BOLD, 13));
        refreshBtn.addActionListener(e -> refreshDevices());

        leftSide.add(deviceLabel);
        leftSide.add(deviceComboBox);
        leftSide.add(refreshBtn);

        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightSide.setOpaque(false);

        deviceStatusLabel = new JLabel("ОЖИДАНИЕ");
        deviceStatusLabel.setFont(new Font("Comic Sans MS", Font.BOLD, 13));
        deviceStatusLabel.setForeground(ModernStyle.TEXT_MUTED);

        statusLabel = new JLabel("ГОТОВ К РАБОТЕ");
        statusLabel.setFont(new Font("Comic Sans MS", Font.BOLD, 13));
        statusLabel.setForeground(ModernStyle.SUCCESS);

        rightSide.add(deviceStatusLabel);
        rightSide.add(statusLabel);

        panel.add(leftSide, BorderLayout.WEST);
        panel.add(rightSide, BorderLayout.EAST);

        return panel;
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 12));
        panel.setOpaque(false);

        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setOpaque(false);

        JLabel listLabel = new JLabel("Список файлов APK для установки (Макс. 15)");
        listLabel.setFont(new Font("Comic Sans MS", Font.BOLD, 15));
        listLabel.setForeground(ModernStyle.TEXT_MAIN);

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnGroup.setOpaque(false);

        addButton = new RoundAddButton();
        addButton.addActionListener(e -> addAPKFiles());

        removeButton = new ModernButton("Удалить файл", new Color(255, 235, 230), 12);
        removeButton.setPreferredSize(new Dimension(130, 38));
        removeButton.setFont(new Font("Comic Sans MS", Font.BOLD, 13));
        removeButton.setForeground(ModernStyle.ERROR);
        removeButton.addActionListener(e -> removeSelectedAPK());
        removeButton.setEnabled(false);

        clearButton = new ModernButton("Очистить список", ModernStyle.CARD_BG, 12);
        clearButton.setPreferredSize(new Dimension(140, 38));
        clearButton.setFont(new Font("Comic Sans MS", Font.BOLD, 13));
        clearButton.addActionListener(e -> clearAPKList());

        btnGroup.add(addButton);
        btnGroup.add(removeButton);
        btnGroup.add(clearButton);

        controlPanel.add(listLabel, BorderLayout.WEST);
        controlPanel.add(btnGroup, BorderLayout.EAST);

        tableModel = new APKTableModel();
        table = new JTable(tableModel);
        table.setFont(new Font("Comic Sans MS", Font.PLAIN, 13));
        table.setRowHeight(38);
        table.setBackground(Color.WHITE);
        table.setForeground(ModernStyle.TEXT_MAIN);
        table.setGridColor(new Color(230, 235, 245));
        table.setSelectionBackground(new Color(0, 123, 255, 30));
        table.setSelectionForeground(ModernStyle.TEXT_MAIN);
        table.setShowVerticalLines(false);

        JTableHeader header = table.getTableHeader();
        header.setBackground(ModernStyle.CARD_BG);
        header.setForeground(ModernStyle.TEXT_MUTED);
        header.setFont(new Font("Comic Sans MS", Font.BOLD, 13));
        header.setPreferredSize(new Dimension(0, 35));

        table.getSelectionModel().addListSelectionListener(e -> {
            removeButton.setEnabled(table.getSelectedRow() >= 0);
        });

        table.getColumnModel().getColumn(0).setPreferredWidth(280);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(350);

        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (c instanceof JLabel && value != null) {
                    String text = value.toString();
                    JLabel label = (JLabel) c;
                    label.setText(text);
                    label.setFont(new Font("Comic Sans MS", Font.BOLD, 13));
                    label.setHorizontalAlignment(SwingConstants.CENTER);

                    if (text.contains("Успешно") || text.contains("Установлен")) {
                        label.setForeground(ModernStyle.SUCCESS);
                    } else if (text.contains("Ошибка")) {
                        label.setForeground(ModernStyle.ERROR);
                    } else if (text.contains("Уже") || text.contains("Предупреждение")) {
                        label.setForeground(ModernStyle.WARNING);
                    } else {
                        label.setForeground(ModernStyle.TEXT_MAIN);
                    }
                }
                if (isSelected) {
                    c.setBackground(new Color(0, 123, 255, 45));
                } else {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 252, 255));
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 235), 1));

        panel.add(controlPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 8));
        panel.setOpaque(false);

        JPanel progressPanel = new JPanel(new BorderLayout(20, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ModernStyle.CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.dispose();
            }
        };
        progressPanel.setOpaque(false);
        progressPanel.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));

        progressBar = new WaterProgressBar();
        progressBar.setPreferredSize(new Dimension(80, 80));

        installButton = new ModernButton("Запустить установку", ModernStyle.WATER_COLOR, 12);
        installButton.setPreferredSize(new Dimension(190, 40));
        installButton.setFont(new Font("Comic Sans MS", Font.BOLD, 14));
        installButton.setEnabled(false);
        installButton.addActionListener(e -> startInstallation());

        ModernButton clearLogBtn = new ModernButton("Очистить лог", ModernStyle.CARD_BG, 12);
        clearLogBtn.setPreferredSize(new Dimension(120, 40));
        clearLogBtn.setFont(new Font("Comic Sans MS", Font.BOLD, 13));
        clearLogBtn.addActionListener(e -> logArea.setText(""));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 18));
        rightPanel.setOpaque(false);
        rightPanel.add(installButton);
        rightPanel.add(clearLogBtn);

        progressPanel.add(progressBar, BorderLayout.WEST);
        progressPanel.add(rightPanel, BorderLayout.EAST);

        logArea = new JTextArea();
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setEditable(false);
        logArea.setBackground(new Color(248, 249, 250));
        logArea.setForeground(new Color(40, 44, 52));
        logArea.setCaretColor(ModernStyle.WATER_COLOR);
        logArea.setText("Система интерфейса успешно загружена\n" + LINE_SEPARATOR + "\n");
        logArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane logScroll = new JScrollPane(logArea) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(248, 249, 250));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        logScroll.setPreferredSize(new Dimension(0, 140));
        logScroll.setOpaque(false);
        logScroll.getViewport().setOpaque(false);
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 235), 1));

        panel.add(progressPanel, BorderLayout.NORTH);
        panel.add(logScroll, BorderLayout.CENTER);

        return panel;
    }


    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel info = new JLabel("Подсказка: Поддерживается пакетная загрузка до 15 файлов APK за одну сессию");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        info.setForeground(ModernStyle.TEXT_MUTED);
        bottom.add(info);

        return bottom;
    }

    // ============================================================
    // ИСПРАВЛЕНИЕ: МЕТОДЫ УПРАВЛЕНИЯ СПИСКОМ APK И КНОПКАМИ
    // ============================================================

    private void addAPKFiles() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "APK файлы (*.apk)", "apk"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] files = fileChooser.getSelectedFiles();
            for (File file : files) {
                if (tableModel.getSize() >= MAX_APK) {
                    JOptionPane.showMessageDialog(this,
                            "Достигнут максимальный лимит в " + MAX_APK + " файлов",
                            "Предупреждение", JOptionPane.WARNING_MESSAGE);
                    break;
                }
                if (file.getName().toLowerCase().endsWith(".apk")) {
                    String size = formatSize(file.length());
                    tableModel.addAPK(file.getName(), file.getAbsolutePath(), size);
                    logArea.append("Добавлен файл: " + file.getName() + " (" + size + ")\n");
                }
            }
            updateButtonsState();
        }
    }

    private void removeSelectedAPK() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            String name = tableModel.getName(row);
            tableModel.removeAPK(row);
            logArea.append("Удален из списка: " + name + "\n");
            updateButtonsState();
        }
    }

    private void clearAPKList() {
        if (tableModel.getSize() > 0) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Вы действительно хотите очистить весь список?", "Подтверждение",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                tableModel.clear();
                logArea.append("Список файлов полностью очищен\n");
                updateButtonsState();
            }
        }
    }

    private void updateButtonsState() {
        boolean hasItems = tableModel.getSize() > 0;
        installButton.setEnabled(hasItems);
        removeButton.setEnabled(table.getSelectedRow() >= 0);
    }


    private void refreshDevices() {
        new Thread(() -> {
            try {
                List<String> devices = adbManager.getDevices();
                SwingUtilities.invokeLater(() -> {
                    deviceComboBox.removeAllItems();
                    if (devices.isEmpty()) {
                        deviceComboBox.addItem("  Нет подключенных устройств  ");
                        deviceStatusLabel.setText("ПОДКЛЮЧЕНИЕ ОТСУТСТВУЕТ");
                        deviceStatusLabel.setForeground(ModernStyle.ERROR);
                        statusLabel.setText("ОЖИДАНИЕ ТЕЛЕФОНА");
                        statusLabel.setForeground(ModernStyle.WARNING);
                    } else {
                        for (String device : devices) {
                            deviceComboBox.addItem(device);
                        }
                        deviceComboBox.setSelectedIndex(0);
                        adbManager.setDevice(devices.get(0));
                        deviceStatusLabel.setText("Активен: " + devices.get(0));
                        deviceStatusLabel.setForeground(ModernStyle.SUCCESS);
                        statusLabel.setText("УСТРОЙСТВО ГОТОВО");
                        statusLabel.setForeground(ModernStyle.SUCCESS);
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    deviceStatusLabel.setText("ОШИБКА ПОДКЛЮЧЕНИЯ");
                    deviceStatusLabel.setForeground(ModernStyle.ERROR);
                });
                e.printStackTrace();
            }
        }).start();
    }
    private void startInstallation() {
        if (tableModel.getSize() == 0) {
            JOptionPane.showMessageDialog(this,
                    "Пожалуйста, добавьте хотя бы один файл в таблицу",
                    "Предупреждение", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedItem = (String) deviceComboBox.getSelectedItem();
        if (selectedItem == null || selectedItem.contains("Нет подключенных")) {
            JOptionPane.showMessageDialog(this,
                    "Активные устройства не обнаружены\nВключите отладку по USB на телефоне.",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        adbManager.setDevice(selectedItem);

        List<String> paths = new ArrayList<>();
        for (int i = 0; i < tableModel.getSize(); i++) {
            paths.add(tableModel.getPath(i));
        }

        installButton.setEnabled(false);
        addButton.setEnabled(false);
        removeButton.setEnabled(false);
        clearButton.setEnabled(false);
        progressBar.setValue(0);
        logArea.append("\n" + LINE_SEPARATOR + "\n");
        logArea.append("ЗАПУСК ПРОЦЕССА УСТАНОВКИ: " +
                new SimpleDateFormat("HH:mm:ss").format(new Date()) + "\n");
        logArea.append(LINE_SEPARATOR + "\n");

        for (int i = 0; i < tableModel.getSize(); i++) {
            tableModel.updateStatus(i, "Ожидает");
        }

        new Thread(() -> {
            try {
                final int total = paths.size();
                final int[] current = {0};
                final int[] successful = {0};
                final int[] alreadyInstalled = {0};
                final int[] failed = {0};

                ADBManager.ProgressCallback callback = new ADBManager.ProgressCallback() {
                    @Override
                    public void onProgress(String message) {
                        SwingUtilities.invokeLater(() -> {
                            logArea.append(message + "\n");
                            logArea.setCaretPosition(logArea.getDocument().getLength());
                        });
                    }

                    @Override
                    public void onFileComplete(String path, String result) {
                        SwingUtilities.invokeLater(() -> {
                            for (int i = 0; i < tableModel.getSize(); i++) {
                                if (tableModel.getPath(i).equals(path)) {
                                    if (result.contains("Успешно")) {
                                        tableModel.updateStatus(i, "Установлен");
                                        successful[0]++;
                                    } else if (result.contains("Уже")) {
                                        tableModel.updateStatus(i, "Уже установлен");
                                        alreadyInstalled[0]++;
                                    } else {
                                        tableModel.updateStatus(i, "Ошибка");
                                        failed[0]++;
                                    }
                                    break;
                                }
                            }
                            current[0]++;
                            // Передаем процент выполнения в нашу круглую шкалу с синей водой
                            int progress = (int) (((double) current[0] / total) * 100);
                            progressBar.setValue(progress);

                            if (current[0] == total) {
                                statusLabel.setText(String.format("Успешно: %d | Пропущено: %d | Ошибок: %d",
                                        successful[0], alreadyInstalled[0], failed[0]));
                                if (failed[0] > 0) {
                                    statusLabel.setForeground(ModernStyle.ERROR);
                                } else {
                                    statusLabel.setForeground(ModernStyle.SUCCESS);
                                }
                            }
                        });
                    }
                };

                adbManager.installMultipleAPKs(paths, callback);

                SwingUtilities.invokeLater(() -> {
                    logArea.append(LINE_SEPARATOR + "\n");
                    logArea.append(String.format("ИТОГОВАЯ СТАТИСТИКА: Успешно %d, Пропущено %d, Ошибок %d\n",
                            successful[0], alreadyInstalled[0], failed[0]));
                    logArea.append("СЕССИЯ УСТАНОВКИ ЗАВЕРШЕНА: " +
                            new SimpleDateFormat("HH:mm:ss").format(new Date()) + "\n\n");

                    installButton.setEnabled(true);
                    addButton.setEnabled(true);
                    clearButton.setEnabled(true);
                    updateButtonsState();
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("Ошибка процесса: " + e.getMessage() + "\n");
                    installButton.setEnabled(true);
                    addButton.setEnabled(true);
                    clearButton.setEnabled(true);
                    updateButtonsState();
                });
            }
        }).start();
    }

    private String formatSize(long bytes) {
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        double size = bytes;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", size, units[unitIndex]);
    }

    private String findAdbPath() {
        String currentFolder = getJarDirectory();
        String[] possiblePaths = {
                // Ищем в папке "adb" в одной директории с EXE
                currentFolder + File.separator + "adb" + File.separator + "adb.exe",
                currentFolder + File.separator + "adb" + File.separator + "adb",

                // Ищем в папке "adbfastboot" в одной директории с EXE
                currentFolder + File.separator + "adbfastboot" + File.separator + "adb.exe",
                currentFolder + File.separator + "adbfastboot" + File.separator + "adb",

                // На случай, если adb.exe положили прямо в корень рядом с EXE
                currentFolder + File.separator + "adb.exe",
                currentFolder + File.separator + "adb"
        };

        for (String path : possiblePaths) {
            if (new File(path).exists()) {
                System.out.println("ADB успешно обнаружен локально: " + path);
                return path;
            }
        }

        // Если ничего не нашли, отдаем системную команду по умолчанию
        System.out.println("Локальный ADB не найден в папке " + currentFolder + ", откат на глобальный вызов");
        return "adb";
    }


    private String getJarDirectory() {
        try {
            String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            String decoded = java.net.URLDecoder.decode(path, "UTF-8");
            File jarFile = new File(decoded);
            return jarFile.isFile() ? jarFile.getParent() : System.getProperty("user.dir");
        } catch (Exception e) {
            return System.getProperty("user.dir");
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> new Main());
    }
}
