import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

// 引入 Pinyin4j 库，用于将中文转换为拼音
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.mozilla.universalchardet.UniversalDetector;


public class CppFileTypingSimulatorGUI extends Application implements NativeKeyListener {
    private static final Logger LOGGER = Logger.getLogger(CppFileTypingSimulatorGUI.class.getName());
    private static final Map<Character, Integer> keyCodeMap = new HashMap<>();
    private static final Map<Character, Integer> shiftKeyCodeMap = new HashMap<>();
    private static boolean shouldStop = false;
    private static boolean isChineseInput = false;
    private static int defaultWaitTime = 5000;
    private static int waitTime = defaultWaitTime;
    private static int keyDelay = 10;
    private static boolean shouldSkipFourSpaces = true; // 新增全局变量，用于控制是否跳过四个连续空格
    private static boolean shouldSkipTab = true; // 新增全局变量，用于控制是否跳过缩进符
    private static boolean shouldOutputRightBrackets = true; // 新增全局变量，用于控制)]}是否输出
    // 新增全局变量，用于控制是否处理特定代码逻辑
    private static boolean shouldHandleSpecificLogic = true;
    private static boolean isPaused = false; // 新增：用于标记程序是否暂停
    private String filePath;
    private ExecutorService executorService;
    private Label fileNameLabel;
    private TextField waitTimeField;
    private TextField keyDelayField;
    private Label countdownLabel;
    private Label statusLabel;
    private Timeline countdownTimeline; // 新增：用于存储倒计时的 Timeline
    private Button stopButton; // 声明为类成员变量
    private final ExecutorService stopExecutorService = Executors.newSingleThreadExecutor(); // 新增：用于执行停止逻辑的线程池
    private Stage primaryStage; // 新增：保存主舞台引用

    static {
        // 初始化基本键码映射
        for (char c = 'a'; c <= 'z'; c++) {
            keyCodeMap.put(c, KeyEvent.getExtendedKeyCodeForChar(c));
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            keyCodeMap.put(c, KeyEvent.getExtendedKeyCodeForChar(c));
        }
        for (char c = '0'; c <= '9'; c++) {
            keyCodeMap.put(c, KeyEvent.getExtendedKeyCodeForChar(c));
        }

        // 初始化需要Shift键的特殊字符映射
        shiftKeyCodeMap.put('!', KeyEvent.VK_1);
        shiftKeyCodeMap.put('@', KeyEvent.VK_2);
        shiftKeyCodeMap.put('#', KeyEvent.VK_3);
        shiftKeyCodeMap.put('$', KeyEvent.VK_4);
        shiftKeyCodeMap.put('%', KeyEvent.VK_5);
        shiftKeyCodeMap.put('^', KeyEvent.VK_6);
        shiftKeyCodeMap.put('&', KeyEvent.VK_7);
        shiftKeyCodeMap.put('*', KeyEvent.VK_8);
        shiftKeyCodeMap.put('(', KeyEvent.VK_9);
        shiftKeyCodeMap.put(')', KeyEvent.VK_0);
        shiftKeyCodeMap.put('_', KeyEvent.VK_MINUS);
        shiftKeyCodeMap.put('+', KeyEvent.VK_EQUALS);
        shiftKeyCodeMap.put('{', KeyEvent.VK_OPEN_BRACKET);
        shiftKeyCodeMap.put('}', KeyEvent.VK_CLOSE_BRACKET);
        shiftKeyCodeMap.put('|', KeyEvent.VK_BACK_SLASH);
        shiftKeyCodeMap.put(':', KeyEvent.VK_SEMICOLON);
        shiftKeyCodeMap.put('"', KeyEvent.VK_QUOTE);
        shiftKeyCodeMap.put('<', KeyEvent.VK_COMMA);
        shiftKeyCodeMap.put('>', KeyEvent.VK_PERIOD);
        shiftKeyCodeMap.put('?', KeyEvent.VK_SLASH);
        shiftKeyCodeMap.put('~', KeyEvent.VK_BACK_QUOTE);

        keyCodeMap.put('.', KeyEvent.VK_PERIOD);
        keyCodeMap.put(' ', KeyEvent.VK_SPACE);
        keyCodeMap.put(',', KeyEvent.VK_COMMA);
        keyCodeMap.put('-', KeyEvent.VK_MINUS);
        keyCodeMap.put(';', KeyEvent.VK_SEMICOLON);
        keyCodeMap.put('=', KeyEvent.VK_EQUALS);
        keyCodeMap.put('[', KeyEvent.VK_OPEN_BRACKET);
        keyCodeMap.put(']', KeyEvent.VK_CLOSE_BRACKET);
        keyCodeMap.put('`', KeyEvent.VK_BACK_QUOTE);
        keyCodeMap.put('/', KeyEvent.VK_SLASH);
        keyCodeMap.put('\\', KeyEvent.VK_BACK_SLASH);
        keyCodeMap.put('\t', KeyEvent.VK_TAB); // 新增：添加制表符的键码映射

    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage; // 保存主舞台引用
        // 设置系统属性，确保控制台输出使用UTF-8编码
        System.setProperty("file.encoding", "UTF-8");

        // 创建界面组件
        Button chooseFileButton = new Button("选择文件");
        fileNameLabel = new Label("未选择文件");
        Button settingsButton = new Button("设置");
        Button startButton = new Button("开始");
        stopButton = new Button("结束"); // 初始化成员变量
        Button exitButton = new Button("退出程序");
        countdownLabel = new Label();
        countdownLabel.setWrapText(true); // 设置标签支持自动换行
        countdownLabel.setMinWidth(300); // 设置标签最小宽度
        statusLabel = new Label();

        // 选择文件按钮事件处理
        chooseFileButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择文件");
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                filePath = selectedFile.getAbsolutePath();
                fileNameLabel.setText("已选择文件: " + selectedFile.getName());
            }
        });

        // 设置按钮事件处理
        settingsButton.setOnAction(e -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("设置");
            dialog.setHeaderText("设置等待时间、按键延迟和是否跳过四个连续空格以及缩进符");

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            waitTimeField = new TextField(String.valueOf(waitTime / 1000));
            keyDelayField = new TextField(String.valueOf(keyDelay));
            CheckBox skipFourSpacesCheckBox = new CheckBox("跳过四个连续空格");
            skipFourSpacesCheckBox.setSelected(shouldSkipFourSpaces);
            CheckBox skipTabCheckBox = new CheckBox("跳过缩进符");
            skipTabCheckBox.setSelected(shouldSkipTab);
            CheckBox outputRightBracketsCheckBox = new CheckBox("输出 )]} 括号");
            outputRightBracketsCheckBox.setSelected(shouldOutputRightBrackets);
            // 新增复选框，用于控制是否处理特定代码逻辑
            CheckBox handleSpecificLogicCheckBox = new CheckBox("处理特定代码逻辑");
            handleSpecificLogicCheckBox.setSelected(shouldHandleSpecificLogic);

            grid.add(new Label("等待时间 (秒):"), 0, 0);
            grid.add(waitTimeField, 1, 0);
            grid.add(new Label("按键延迟 (毫秒):"), 0, 1);
            grid.add(keyDelayField, 1, 1);
            grid.add(skipFourSpacesCheckBox, 0, 2, 2, 1);
            grid.add(skipTabCheckBox, 0, 3, 2, 1);
            grid.add(outputRightBracketsCheckBox, 0, 4, 2, 1);
            // 添加新增的复选框到网格布局
            grid.add(handleSpecificLogicCheckBox, 0, 5, 2, 1);

            dialog.getDialogPane().setContent(grid);

            ButtonType okButton = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(okButton, ButtonType.CANCEL);

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == okButton) {
                    try {
                        waitTime = Integer.parseInt(waitTimeField.getText()) * 1000;
                        defaultWaitTime = waitTime;
                        keyDelay = Integer.parseInt(keyDelayField.getText());
                        shouldSkipFourSpaces = skipFourSpacesCheckBox.isSelected(); // 更新全局变量
                        shouldSkipTab = skipTabCheckBox.isSelected(); // 更新全局变量
                        shouldOutputRightBrackets = outputRightBracketsCheckBox.isSelected(); // 更新全局变量
                        // 更新新增的全局变量
                        shouldHandleSpecificLogic = handleSpecificLogicCheckBox.isSelected();
                    } catch (NumberFormatException ex) {
                        // 处理输入无效的情况
                        LOGGER.log(Level.SEVERE, "输入的等待时间或按键延迟无效", ex);
                    }
                }
                return dialogButton;
            });

            dialog.showAndWait();
        });

        // 开始按钮事件处理
        startButton.setOnAction(e -> {
            shouldStop = false;
            if (filePath == null) {
                Alert alert = new Alert(AlertType.WARNING);
                alert.setTitle("警告");
                alert.setHeaderText(null);
                alert.setContentText("未选择文件，请先选择文件！");
                alert.showAndWait();
                return;
            }
            statusLabel.setText("");
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdownNow();
            }
            shouldStop = false;
            waitTime = defaultWaitTime;
            executorService = Executors.newSingleThreadExecutor();
            executorService.submit(() -> {
                try {
                    String content = readFile(filePath);

                    countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
                        waitTime -= 1000;
                        if (waitTime >= 0) {
                            String countdownText = "请在 " + waitTime / 1000 + " 秒内将光标移动到目标输入位置并选中";
                            countdownLabel.setText(countdownText);
                            // 动态调整窗口宽度
                            Platform.runLater(() -> {
                                double preferredWidth = countdownLabel.getLayoutBounds().getWidth() + 200;
                                primaryStage.setWidth(Math.max(preferredWidth, primaryStage.getWidth()));
                            });
                        } else {
                            countdownLabel.setText("");
                        }
                    }));
                    countdownTimeline.setCycleCount(waitTime / 1000 + 1);
                    countdownTimeline.play();
                    countdownTimeline.setOnFinished(event -> {
                        try {
                            typeContent(content);
                            // 新增：文件输入完毕后弹窗提醒
                            Platform.runLater(() -> {
                                Alert alert = new Alert(AlertType.INFORMATION);
                                alert.setTitle("完成");
                                alert.setHeaderText(null);
                                alert.setContentText("文件内容输入完成！");
                                alert.showAndWait();
                            });
                            statusLabel.setText("文件内容输入完成！");
                            waitTime = defaultWaitTime;
                        } catch (AWTException | InterruptedException ex) {
                            LOGGER.log(Level.SEVERE, "输入文件内容时发生错误", ex);
                        }
                    });
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "读取文件时发生错误", ex);
                }
            });
        });

        // 结束按钮事件处理
        stopButton.setOnAction(e -> stopExecutorService.submit(() -> {
            shouldStop = true;
            if (executorService != null) {
                executorService.shutdownNow();
            }
            if (countdownTimeline != null) {
                countdownTimeline.stop(); // 停止倒计时
            }
            Platform.runLater(() -> {
                countdownLabel.setText("");
                statusLabel.setText("");
                waitTime = defaultWaitTime; // 重置等待时间
            });
        }));

        // 退出程序按钮事件处理
        exitButton.setOnAction(e -> shutdownAndExit());

        // 添加使用说明标签
        Label instructionLabel = getLabel();

        // 布局按钮和倒计时、状态标签
        VBox buttonVBox = new VBox(10);
        buttonVBox.setPadding(new Insets(20));
        buttonVBox.getChildren().addAll(chooseFileButton, fileNameLabel, settingsButton, startButton, stopButton, exitButton, countdownLabel, statusLabel);

        // 使用 HBox 将按钮布局和说明标签并列
        HBox mainHBox = new HBox(20);
        mainHBox.setPadding(new Insets(20));
        mainHBox.getChildren().addAll(buttonVBox, instructionLabel);

        // 创建场景并显示
        Scene scene = new Scene(mainHBox, 800, 300);
        primaryStage.setTitle("模拟输入");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> shutdownAndExit()); // 新增：设置窗口关闭请求事件处理
        primaryStage.show();

        // 初始化JNativeHook
        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException ex) {
            LOGGER.log(Level.SEVERE, "注册全局键盘钩子时发生错误", ex);
            System.exit(1);
        }

        // 添加键盘监听器
        GlobalScreen.addNativeKeyListener(this);
    }

    private static Label getLabel() {
        Label instructionLabel = new Label();
        instructionLabel.setText("【输入法配置要求】\n" +
                "系统需配置两种输入法（且仅保留两种）\n" +
                "切换输入法快捷键设置为：Win键 + 空格键\n" +
                "【程序启动前准备】\n" +
                "请确保当前输入法为英文状态\n" +
                "建议关闭其他可能干扰的输入法\n" +
                "设置中可以配置是否要跳过连续的四个空格、缩进符和按键延迟\n"+
                "【程序功能说明】\n" +
                "汉字处理机制：\n" +
                "程序读取汉字时会自动转换为拼音，并模拟切换输入法\n" +
                "若转换结果不符合预期，可手动修正\n" +
                "【强制终止操作】\n" +
                "在程序运行过程中如需中止模拟写入，\n请按下 ESC 键\n" +
                "【暂停/恢复操作】\n" +
                "在程序运行过程中按下 F12 键可暂停/恢复模拟写入");
        return instructionLabel;
    }

    private String readFile(String filePath) throws IOException {
        byte[] buffer = new byte[4096];
        try (FileInputStream fis = new FileInputStream(filePath)) {
            UniversalDetector detector = new UniversalDetector(null);
            int nRead;
            while ((nRead = fis.read(buffer)) > 0 && !detector.isDone()) {
                detector.handleData(buffer, 0, nRead);
            }
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            detector.reset();

            if (encoding == null) {
                encoding = StandardCharsets.UTF_8.name();
            }

            return readFileWithEncoding(filePath, Charset.forName(encoding));
        }
    }

    private String readFileWithEncoding(String filePath, Charset encoding) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(filePath)), encoding))) {
            String line;
            boolean isFirstLine = true;
            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    // 去除 UTF - 8 BOM
                    line = removeUTF8BOM(line);
                    isFirstLine = false;
                }
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private String removeUTF8BOM(String s) {
        if (s != null && s.startsWith("\uFEFF")) {
            s = s.substring(1);
        }
        return s;
    }

    private void typeContent(String content) throws AWTException, InterruptedException {
        Robot robot = new Robot();
        robot.setAutoDelay(keyDelay); // 设置每次按键的延迟

        StringBuilder chineseSentence = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            // 新增：检查是否暂停
            while (isPaused && !shouldStop) {
                //noinspection BusyWait
                Thread.sleep(100);
            }
            if (shouldStop) {
                break;
            }
            char c = content.charAt(i);
            boolean isNextChinese = (i + 1 < content.length()) && isChineseCharacter(content.charAt(i + 1));



            //针对实验提交系统的特殊处理
            if (shouldHandleSpecificLogic) {
                // 新增情况：当遇到switch(......)\n\t{序列时，保持缩进
                if (i + 5 < content.length()) {
                    // 检查当前字符开始是否是 "switch"
                    if (c == 's' &&
                            i + 1 < content.length() && content.charAt(i + 1) == 'w' &&
                            i + 2 < content.length() && content.charAt(i + 2) == 'i' &&
                            i + 3 < content.length() && content.charAt(i + 3) == 't' &&
                            i + 4 < content.length() && content.charAt(i + 4) == 'c' &&
                            i + 5 < content.length() && content.charAt(i + 5) == 'h') {

                        int openParenIndex = i + 6;
                        // 查找 '('
                        while (openParenIndex < content.length() && content.charAt(openParenIndex) != '(') {
                            openParenIndex++;
                        }

                        if (openParenIndex < content.length()) {
                            // 查找 ')'
                            int closeParenIndex = openParenIndex + 1;
                            int parenCount = 1;
                            while (closeParenIndex < content.length() && parenCount > 0) {
                                char ch = content.charAt(closeParenIndex);
                                if (ch == '(') parenCount++;
                                if (ch == ')') parenCount--;
                                closeParenIndex++;
                            }

                            if (closeParenIndex < content.length()) {
                                // 检查 ')' 后是否是 \n
                                int nextIndex = closeParenIndex;
                                if (nextIndex < content.length() && content.charAt(nextIndex) == '\n') {
                                    int braceIndex = nextIndex + 1;
                                    // 检查换行后是否有制表符
                                    boolean hasTab = false;
                                    while (braceIndex < content.length() && content.charAt(braceIndex) == '\t') {
                                        hasTab = true;
                                        braceIndex++;
                                    }
                                    // 检查是否找到 {
                                    if (braceIndex < content.length() && content.charAt(braceIndex) == '{') {
                                        if (shouldOutputRightBrackets) {
                                            // 输入 "switch(...)"
                                            for (int j = i; j < closeParenIndex; j++) {
                                                typeCharacter(robot, content.charAt(j));
                                            }
                                            // 输入换行
                                            robot.keyPress(KeyEvent.VK_ENTER);
                                            robot.keyRelease(KeyEvent.VK_ENTER);
                                            // 不执行退格，保留自动缩进
                                            // 输入左大括号
                                            typeCharacter(robot, '{');
                                        }
                                        // 跳过 switch(...) 以及中间所有的制表符和 {
                                        i = braceIndex;
                                        continue;
                                    }
                                }
                            }
                        }
                    }
                }

                // 情况1：当遇到 )\n{ 或 )\n\t*{ 序列时，输入 )\n退格{
                if (c == ')') {
                    int nextIndex = i + 1;
                    // 检查是否是 )\n 开头
                    if (nextIndex < content.length() && content.charAt(nextIndex) == '\n') {
                        int braceIndex = nextIndex + 1;
                        // 查找后续第一个非制表符字符
                        while (braceIndex < content.length() && content.charAt(braceIndex) == '\t') {
                            braceIndex++;
                        }
                        // 检查是否找到 {
                        if (braceIndex < content.length() && content.charAt(braceIndex) == '{') {
                            if (shouldOutputRightBrackets) {
                                // 输入右括号
                                typeCharacter(robot, ')');
                                // 输入换行
                                robot.keyPress(KeyEvent.VK_ENTER);
                                robot.keyRelease(KeyEvent.VK_ENTER);
                                // 执行一次退格，消除自动缩进
                                robot.keyPress(KeyEvent.VK_BACK_SPACE);
                                robot.keyRelease(KeyEvent.VK_BACK_SPACE);
                                // 输入左大括号
                                typeCharacter(robot, '{');
                            }
                            // 跳过 )\n 以及中间所有的制表符和 {
                            i = braceIndex;
                            continue;
                        }
                    }
                }

                // 情况2：当遇到 \n} 或 \n\t*} 序列时，改为 \n退格}
                if (c == '\n') {
                    int braceIndex = i + 1;
                    // 查找后续第一个非制表符字符
                    while (braceIndex < content.length() && content.charAt(braceIndex) == '\t') {
                        braceIndex++;
                    }
                    // 检查是否找到 }
                    if (braceIndex < content.length() && content.charAt(braceIndex) == '}') {
                        if (shouldOutputRightBrackets) {
                            // 输入换行
                            robot.keyPress(KeyEvent.VK_ENTER);
                            robot.keyRelease(KeyEvent.VK_ENTER);
                            // 执行一次退格，消除自动缩进
                            robot.keyPress(KeyEvent.VK_BACK_SPACE);
                            robot.keyRelease(KeyEvent.VK_BACK_SPACE);
                            // 输入右大括号
                            typeCharacter(robot, '}');
                        }
                        // 跳过 \n 以及中间所有的制表符和 }
                        i = braceIndex;
                        continue;
                    }
                }

                // 情况3：当遇到 else\n{ 或 else\n\t*{ 序列时，输入 else\n退格{
                if (i + 4 < content.length()) {
                    // 检查当前字符开始是否是 "else"
                    if (c == 'e' &&
                            i + 1 < content.length() && content.charAt(i + 1) == 'l' &&
                            i + 2 < content.length() && content.charAt(i + 2) == 's' &&
                            i + 3 < content.length() && content.charAt(i + 3) == 'e') {

                        int nextIndex = i + 4;
                        // 检查 "else" 后是否是 \n
                        if (nextIndex < content.length() && content.charAt(nextIndex) == '\n') {
                            int braceIndex = nextIndex + 1;
                            // 查找后续第一个非制表符字符
                            while (braceIndex < content.length() && content.charAt(braceIndex) == '\t') {
                                braceIndex++;
                            }
                            // 检查是否找到 {
                            if (braceIndex < content.length() && content.charAt(braceIndex) == '{') {
                                if (shouldOutputRightBrackets) {
                                    // 输入 "else"
                                    typeCharacter(robot, 'e');
                                    typeCharacter(robot, 'l');
                                    typeCharacter(robot, 's');
                                    typeCharacter(robot, 'e');
                                    // 输入换行
                                    robot.keyPress(KeyEvent.VK_ENTER);
                                    robot.keyRelease(KeyEvent.VK_ENTER);
                                    // 执行一次退格，消除自动缩进
                                    robot.keyPress(KeyEvent.VK_BACK_SPACE);
                                    robot.keyRelease(KeyEvent.VK_BACK_SPACE);
                                    // 输入左大括号
                                    typeCharacter(robot, '{');
                                }
                                // 跳过 else\n 以及中间所有的制表符和 {
                                i = braceIndex;
                                continue;
                            }
                        }
                    }
                }

                // 情况4：当遇到 ;\n[ \t\n]*case 序列时，输入 ;\n退格case
                if (c == ';') {
                    int nextIndex = i + 1;
                    // 检查是否是 ;\n 开头
                    if (nextIndex < content.length() && content.charAt(nextIndex) == '\n') {
                        int caseIndex = nextIndex + 1;
                        // 查找后续第一个非空白字符（制表符、空格或换行符）
                        while (caseIndex < content.length() &&
                                (content.charAt(caseIndex) == '\t' ||
                                        content.charAt(caseIndex) == ' '  ||
                                        content.charAt(caseIndex) == '\n')) {
                            caseIndex++;
                        }
                        // 检查是否找到 "case"
                        if (caseIndex + 3 < content.length() &&
                                content.charAt(caseIndex) == 'c' &&
                                content.charAt(caseIndex + 1) == 'a' &&
                                content.charAt(caseIndex + 2) == 's' &&
                                content.charAt(caseIndex + 3) == 'e') {

                            if (shouldOutputRightBrackets) {
                                // 输入分号
                                typeCharacter(robot, ';');
                                // 输入换行
                                robot.keyPress(KeyEvent.VK_ENTER);
                                robot.keyRelease(KeyEvent.VK_ENTER);
                                // 执行一次退格，消除自动缩进
                                robot.keyPress(KeyEvent.VK_BACK_SPACE);
                                robot.keyRelease(KeyEvent.VK_BACK_SPACE);
                                // 输入 "case"
                                typeCharacter(robot, 'c');
                                typeCharacter(robot, 'a');
                                typeCharacter(robot, 's');
                                typeCharacter(robot, 'e');
                            }
                            // 跳过 ;\n 以及中间所有的空白字符和 "case"
                            i = caseIndex + 3;
                            continue;
                        }
                    }
                }
            }

            // 检查是否为连续的四个空格
            if (shouldSkipFourSpaces && c == ' ' && i + 3 < content.length() &&
                    content.charAt(i + 1) == ' ' &&
                    content.charAt(i + 2) == ' ' &&
                    content.charAt(i + 3) == ' ') {
                i += 3; // 跳过接下来的三个空格
                continue;
            }

            // 检查是否为缩进符
            if (shouldSkipTab && c == '\t') {
                continue;
            }

            if (c == '\n') {
                if (isChineseInput) {
                    switchInputMethod(robot);
                    isChineseInput = false;
                }
                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);
            } else if (isChineseCharacter(c)) {
                if (!isChineseInput) {
                    switchInputMethod(robot);
                    isChineseInput = true;
                }
                chineseSentence.append(c);
                if (!isNextChinese) {
                    try {
                        typeChineseSentence(robot, chineseSentence.toString());
                    } catch (BadHanyuPinyinOutputFormatCombination e) {
                        LOGGER.log(Level.SEVERE, "转换中文句子为拼音时发生错误", e);
                        throw new RuntimeException(e);
                    }
                    chineseSentence.setLength(0);
                }
            } else {
                if (isChineseInput && !isNextChinese) {
                    switchInputMethod(robot);
                    isChineseInput = false;
                }
                if (chineseSentence.length() > 0) {
                    try {
                        typeChineseSentence(robot, chineseSentence.toString());
                    } catch (BadHanyuPinyinOutputFormatCombination e) {
                        LOGGER.log(Level.SEVERE, "转换中文句子为拼音时发生错误", e);
                        throw new RuntimeException(e);
                    }
                    chineseSentence.setLength(0);
                }
                if (shouldOutputRightBrackets || (c != ')' && c != ']' && c != '}')) {
                    typeCharacter(robot, c);
                }
            }
        }

        // 处理最后一个中文句子
        if (chineseSentence.length() > 0) {
            try {
                typeChineseSentence(robot, chineseSentence.toString());
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                LOGGER.log(Level.SEVERE, "转换中文句子为拼音时发生错误", e);
                throw new RuntimeException(e);
            }
        }
    }


    private void typeCharacter(Robot robot, char c) {
        int keyCode = getKeyCode(c);
        if (keyCode != -1) {
            if (Character.isUpperCase(c) || shiftKeyCodeMap.containsKey(c)) {
                robot.keyPress(KeyEvent.VK_SHIFT);
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
                robot.keyRelease(KeyEvent.VK_SHIFT);
            } else {
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
            }
        } else {
            System.err.println("无法识别的字符: '" + c + "'");
        }
    }

    private int getKeyCode(char c) {
        if (keyCodeMap.containsKey(c)) {
            return keyCodeMap.get(c);
        } else if (shiftKeyCodeMap.containsKey(c)) {
            return shiftKeyCodeMap.get(c);
        }
        return -1;
    }

    private boolean isChineseCharacter(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    private void typeChineseSentence(Robot robot, String sentence) throws BadHanyuPinyinOutputFormatCombination {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        StringBuilder pinyinSentence = new StringBuilder();
        for (char c : sentence.toCharArray()) {
            String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
            if (pinyinArray != null && pinyinArray.length > 0) {
                // 手动将 "u" 替换为 "ü"
                String pinyin = pinyinArray[0].replace("u:", "v");
                pinyinSentence.append(pinyin);
            } else {
                LOGGER.log(Level.SEVERE, "无法获取字符 '" + c + "' 的拼音");
            }
        }
        String pinyin = pinyinSentence.toString();
        for (char p : pinyin.toCharArray()) {
            typeCharacter(robot, p);
        }
        // 模拟按下空格键确认输入
        robot.keyPress(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_SPACE);
    }

    private void switchInputMethod(Robot robot) {
        robot.keyPress(KeyEvent.VK_WINDOWS);
        robot.keyPress(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_WINDOWS);
        try {
            Thread.sleep(500); // 等待输入法切换完成
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "切换输入法时发生中断", e);
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeEvent) {
        // 检查是否按下了ESC键
        if (nativeEvent.getKeyCode() == NativeKeyEvent.VC_ESCAPE) {
            // 直接调用结束按钮的点击事件处理逻辑
            stopButton.fire();
        }
        // 检查是否按下了F12键
        if (nativeEvent.getKeyCode() == NativeKeyEvent.VC_F12) {
            isPaused = !isPaused;
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeEvent) {
        // 不需要处理释放事件
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeEvent) {
        // 不需要处理键入事件
    }

    private void shutdownAndExit() {
        shouldStop = true;
        if (executorService != null) {
            executorService.shutdownNow();
        }
        stopExecutorService.shutdownNow();
        if (countdownTimeline != null) {
            countdownTimeline.stop(); // 停止倒计时
        }
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException ex) {
            LOGGER.log(Level.SEVERE, "注销全局键盘钩子时发生错误", ex);
        }
        System.exit(0);
    }

    public static void main(String[] args) {
        //created by sutanm
        launch(args);
    }
}