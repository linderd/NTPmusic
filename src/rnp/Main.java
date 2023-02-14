package rnp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.util.Duration;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Main extends Application {
    public static Boolean isServer = false;
    private String serverIP;
    private static int serverPort = 55555;
    private UDPServer udpServer; //wird von Intellij als nicht benötigt angezeigt, wird es aber!
    private static UDPClient udpClient;
    private static Player player = new Player();
    private static Button synchronizeButton = new Button(); //Button to start synchronization
    private static TextField synchronizeField = new TextField();
    private static CheckBox synchronizeCheckBox = new CheckBox();
    private static Button playButton = new Button(); //button to play music
    private static Button playNextButton = new Button();
    private static Button selectMusicButton = new Button(); //Button to load music file
    private static Button selectDirectoryButton = new Button();
    private static Button serverButton = new Button();
    private static Button clientButton = new Button();
    private static Button startButton = new Button();
    private static Button abortSearchDir = new Button();
    private static Slider slider = new Slider();
    private static TextField ipField = new TextField();
    private static TextField portField = new TextField();
    private static TextArea infoArea = new TextArea();
    private Stage stage;
    private static boolean isPlayingMusic;
    public static Label playTime = new Label();
    private static Label yourIp = new Label();
    private static Label yourPort = new Label();
    private static Label serverClient = new Label();
    private static Label song = new Label();
    private static Button feelGoodButton = new Button(); //button to choose song feel_good
    private static Button hellButton = new Button(); //button to choose song hell
    private static String actualSongName;
    private static boolean mac = false;
    private static boolean windows = false;
    public static boolean sliderWasDisabled = false;
    public static int synchronizeTime;
    public static boolean synchronizingActive = false;
    public static boolean autoSynchronizingActive = false;
    public static double lastTimeSynced = 0;


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        String os = System.getProperties().getProperty("os.name"); //Mac OS X/Linux/Windows
        System.out.println("You are running this application on " + os);
        if(os.contains("Mac")) {
            mac = true;
        } else if (os.contains("Windows")) {
            windows = true;
        }

        //add default songs in jar to musicFilesInDir
        Player.addDefaultSongs();

        //initialize settings for all Buttons
        startupView();

        //set EventHandler and Listener to the buttons
        setEventHandlerAndListener();

        Group root = new Group(serverButton, clientButton, startButton, serverClient, abortSearchDir,selectMusicButton,
                selectDirectoryButton, synchronizeButton, playButton, slider, ipField, infoArea, yourIp, playTime,
                portField, yourPort, song, hellButton, feelGoodButton, synchronizeCheckBox, synchronizeField, playNextButton);
        if(windows) {
            primaryStage.setScene(new Scene(root, 510, 275));
        } else{
            primaryStage.setScene(new Scene(root, 520, 275));
        }
        primaryStage.show();
        primaryStage.getIcons().add(new Image(Main.class.getResourceAsStream("/" + "icon.png")));
        primaryStage.setResizable(false);
    }

    @Override
    public void stop() {
        System.out.println("NetworkMusic will close");
        //to end the threads 100%
        System.exit(0);
    }

    public static Boolean getWindows() {
        return windows;
    }

    public static Boolean getMac() { return mac; }

    public static double getMusicTime() {
        return player.getPosition();
    }

    public static void setMusicTime(double value) {
        player.jumpToPosition(value);
    }

    public static void setInfoText(String text) {
        infoArea.setText(text);
    }

    public static void setActualSong(String text) {
        song.setText(text);
        actualSongName = text;
        infoArea.setText("Song "+ text + " selected");
    }

    public static void chooseSong(String hashOfSong, File file) {
        player.chooseSong(hashOfSong, file);
    }

    public static void setPlay() {
        player.play();
    }

    public static String getActualSong() {
        return actualSongName;
    }

    public static String getActualSongHash() {

        return Player.getHashToActualSong();
    }

    public static void setSynchronizeActive(Boolean bool) {
        synchronizeButton.setDisable(bool);
        slider.setDisable(bool);
        playButton.setDisable(bool);
        hellButton.setDisable(bool);
        feelGoodButton.setDisable(bool);
        selectMusicButton.setDisable(bool);
        selectDirectoryButton.setDisable(bool);
        synchronizeButton.setDisable(bool);
        synchronizeCheckBox.setDisable(bool);
        synchronizeField.setDisable(bool);
        playNextButton.setDisable(bool);
    }

    public static int getServerPort() {
        return serverPort;
    }

    public static void setPlayingActive(Boolean bool) {
        if(bool) {
            playButton.setText("Pause");
            isPlayingMusic = true;
        } else {
            playButton.setText("Play");
            isPlayingMusic = false;
        }
    }

    //byte als Datentyp, da der leapIndicator auch ein byte ist..
    public static byte getIsPlayingMusic(){
        if (isPlayingMusic){
            return 1;
        }else{
            return 0;
        }
    }

    public static void updateSlider() {
        if (playTime != null) {
            Platform.runLater(new Runnable() {
                public void run() {
                    playTime.setText("" + convertTimeStamp(player.getPosition()));
                    slider.setDisable(player.getDuration().isUnknown());
                    if (!slider.isDisabled() && player.getDuration().greaterThan(Duration.ZERO) && !slider.isValueChanging()) {
                        slider.setValue(player.getPosition() / player.getDuration().toMillis() * 100.0);
                    }
                    if(sliderWasDisabled) {
                        slider.setDisable(true);
                    }
                }
            });
        }
    }

    public static void setSliderToZero() {
        slider.setValue(0);
    }

    public static String getMD5Checksum(String filename, Boolean internalMedia) throws Exception {
        byte[] b = createChecksum(filename, internalMedia);
        String result = "";
        for (int i=0; i < b.length; i++) {
            result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
        }
        return result;
    }

    private static byte[] createChecksum(String filename, Boolean internalMedia) throws Exception {
        MessageDigest complete = MessageDigest.getInstance("MD5");
        InputStream fis;
        if(internalMedia) {
            fis = Main.class.getResourceAsStream("/" + filename);
        } else {
            fis = new FileInputStream(filename);
        }
        byte[] buffer = new byte[1024];
        int numRead;
        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                complete.update(buffer, 0, numRead);
            }
        } while (numRead != -1);
        fis.close();
        searchDirView(false);
        return complete.digest();
    }

    private void setEventHandlerAndListener() {
        slider.valueProperty().addListener(new InvalidationListener() {
            public void invalidated(Observable ov) {
                if (slider.isValueChanging()) {
                    player.jumpToPercentualPosition((int) (slider.getValue()));
                }
            }
        });

        playButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(!isPlayingMusic) {
                    if(actualSongName == null) {
                        setActualSong(Player.getASongOfMap()[2]);
                        player.chooseSong(Player.getASongOfMap()[0], null);
                    }
                    player.play();
                } else {
                    player.pause();
                }
            }
        });

        playNextButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                player.next();
            }
        });

        selectMusicButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final FileChooser fileChooser = new FileChooser();
                File file = fileChooser.showOpenDialog(stage);
                if (file != null) {
                    player.chooseSong(null, file);
                }
            }
        });

        selectDirectoryButton.setOnAction(new EventHandler<ActionEvent>() {
            final DirectoryChooser directoryChooser = new DirectoryChooser();
            @Override
            public void handle(ActionEvent event) {
                searchDirView(true);
                File directory = directoryChooser.showDialog(stage);
                if(directory != null) {
                    player.searchDir(directory);
                }
                searchDirView(false);
                player.abortSearchDir = false;
            }
        });

        synchronizeButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                synchronize();
            }
        });

        synchronizeCheckBox.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(!autoSynchronizingActive && checkSynchronizedField()) {
                    autoSynchronizingActive = true;
                    synchronizeCheckBox.setSelected(true);
                } else {
                    autoSynchronizingActive = false;
                    synchronizeCheckBox.setSelected(false);
                    lastTimeSynced = 0;
                }
            }
        });

        synchronizeField.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(autoSynchronizingActive) {
                    setInfoText("You have to reselect the Checkbox to adjust the new time!");
                }
            }
        });

        hellButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                player.chooseSong("3b27cac5562c314d2c6402ba8a100732", null);
            }
        });

        feelGoodButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                player.chooseSong("d050c088d0e2b14cc944efa105a03f95", null);
            }
        });

        serverButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                isServer = true;
                secondView();
            }
        });

        clientButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                isServer = false;
                secondView();
            }
        });

        startButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(checkIPField() && checkPortField()) {
                    if (isServer) {
                        infoArea.setText("Server started successfully");
                        udpServer = new UDPServer();
                    } else {
                        infoArea.setText("Client started successfully");
                        udpClient = new UDPClient(serverIP, true);
                        synchronizeButton.setVisible(true);
                        synchronizeCheckBox.setVisible(true);
                        synchronizeField.setVisible(true);
                        synchronizeField.setDisable(false);
                    }
                    playingView();
                }
            }
        });

        abortSearchDir.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                player.abortSearchDir = true;
                searchDirView(false);
            }
        });
    }

    private void startupView() {
        abortSearchDir.setVisible(false);
        slider.setVisible(false);
        playButton.setVisible(false);
        playNextButton.setVisible(false);
        selectMusicButton.setVisible(false);
        selectDirectoryButton.setVisible(false);
        synchronizeButton.setVisible(false);
        synchronizeField.setVisible(false);
        synchronizeCheckBox.setVisible(false);
        hellButton.setVisible(false);
        feelGoodButton.setVisible(false);
        ipField.setVisible(false);
        portField.setVisible(false);
        infoArea.setVisible(false);
        stage.setTitle("NetworkMusic - Music synchronization over SNTP");
        startButton.setVisible(false);
        serverClient.setVisible(true);

        serverClient.setText("Do you want to be a server or a client?");
        serverClient.setLayoutX(140);
        serverClient.setLayoutY(100);

        serverButton.setLayoutX(140);
        serverButton.setLayoutY(140);
        serverButton.setText("Server");
        serverButton.setPrefWidth(100);

        clientButton.setLayoutX(260);
        clientButton.setLayoutY(140);
        clientButton.setText("Client");
        clientButton.setPrefWidth(100);
    }

    private void secondView() {
        if(isServer) {
            stage.setTitle("NetworkMusic - Server");
            serverClient.setText("Change the IP and the Port of the Server if you like to");
            serverClient.setLayoutX(110);
            startButton.setText("Start UDP Server");
        } else {
            stage.setTitle("NetworkMusic - Client");
            serverClient.setText("Enter the IP and the Port of the Server");
            startButton.setText("Start UDP Client");
        }

        showIPofHost();
        ipField.setLayoutX(50);
        ipField.setLayoutY(140);
        ipField.setPrefWidth(120);

        portField.setLayoutX(305);
        portField.setLayoutY(140);
        portField.setPrefWidth(60);
        portField.setPromptText("" + serverPort);

        clientButton.setVisible(false);
        serverButton.setVisible(false);
        ipField.setVisible(true);
        portField.setVisible(true);

        startButton.setVisible(true);
        startButton.setPrefWidth(150);
        startButton.setLayoutX(175);
        startButton.setLayoutY(180);

        infoArea.setVisible(true);
        infoArea.setLayoutX(5);
        infoArea.setLayoutY(215);
        infoArea.setPrefWidth(510);
        infoArea.setEditable(false);
        infoArea.setPrefRowCount(2);
        infoArea.setWrapText(true);
    }

    private void playingView() {
        slider.setVisible(true);
        playButton.setVisible(true);
        hellButton.setVisible(true);
        feelGoodButton.setVisible(true);
        yourIp.setVisible(true);
        yourPort.setVisible(true);
        ipField.setDisable(true);
        portField.setDisable(true);
        selectDirectoryButton.setVisible(true);
        selectMusicButton.setVisible(true);
        playNextButton.setVisible(true);
        startButton.setVisible(false);
        serverClient.setVisible(false);

        song.setLayoutX(5);
        song.setLayoutY(5);

        slider.setLayoutX(5);
        slider.setLayoutY(30);
        slider.setMin(0);
        slider.setMax(100);

        playTime.setLayoutX(155);
        playTime.setLayoutY(30);
        playTime.setPrefWidth(100);

        playButton.setText("Play");
        playButton.setLayoutX(5);
        playButton.setLayoutY(50);
        playButton.setPrefWidth(60);

        playNextButton.setText("Next");
        playNextButton.setLayoutX(75);
        playNextButton.setLayoutY(50);
        playNextButton.setPrefWidth(60);

        selectMusicButton.setText("Select music file");
        selectMusicButton.setLayoutX(360);
        selectMusicButton.setLayoutY(50);
        selectMusicButton.setPrefWidth(150);

        selectDirectoryButton.setText("Select directory");
        selectDirectoryButton.setLayoutX(360);
        selectDirectoryButton.setLayoutY(80);
        selectDirectoryButton.setPrefWidth(150);

        synchronizeButton.setText("Start Synchronization");
        synchronizeButton.setLayoutX(175);
        synchronizeButton.setLayoutY(50);

        synchronizeField.setLayoutX(175);
        synchronizeField.setLayoutY(90);
        synchronizeField.setPrefWidth(60);

        synchronizeCheckBox.setLayoutX(245);
        synchronizeCheckBox.setLayoutY(92);

        yourIp.setLayoutX(50);
        yourIp.setLayoutY(150);
        yourIp.setPrefWidth(150);
        yourIp.setText("Server's IP:");

        ipField.setLayoutY(170);
        portField.setLayoutY(170);

        yourPort.setLayoutX(305);
        yourPort.setLayoutY(150);
        yourPort.setPrefWidth(150);
        yourPort.setText("Server's Port:");

        hellButton.setText("Hell");
        hellButton.setLayoutX(360);
        hellButton.setLayoutY(20);
        hellButton.setPrefWidth(50);

        feelGoodButton.setText("Feel Good");
        feelGoodButton.setLayoutX(420);
        feelGoodButton.setLayoutY(20);
        feelGoodButton.setPrefWidth(90);
    }

    private static void searchDirView(Boolean visible) {
        playTime.setVisible(!visible);
        slider.setVisible(!visible);
        song.setVisible(!visible);
        playButton.setVisible(!visible);
        selectDirectoryButton.setVisible(!visible);
        selectMusicButton.setVisible(!visible);
        if (!isServer){
            synchronizeButton.setVisible(!visible);
            synchronizeCheckBox.setVisible(!visible);
            synchronizeField.setVisible(!visible);
        }
        hellButton.setVisible(!visible);
        feelGoodButton.setVisible(!visible);
        ipField.setVisible(!visible);
        portField.setVisible(!visible);
        yourIp.setVisible(!visible);
        yourPort.setVisible(!visible);
        infoArea.setVisible(!visible);
        playNextButton.setVisible(!visible);

        /*
        abortSearchDir.setVisible(visible);
        abortSearchDir.setLayoutX(180);
        abortSearchDir.setLayoutY(100);
        abortSearchDir.setText("Click to abort the indexing");
        */
        serverClient.setVisible(visible);
        serverClient.setText("Waiting for end of indexing...");
    }

    private void showIPofHost() {
        try {
            //this method only works with a connection to google.com connected
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("google.com", 80));
            ipField.setPromptText(socket.getLocalAddress().getHostAddress());
            socket.close();
            //this method has problems with multiple network adapters
            //textField.setText(InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            System.out.println("Error in changeView: " + e);
        }
    }

    public static String convertTimeStamp(double t) {
        long time = Math.round(t);
        //long hours = TimeUnit.MILLISECONDS.toHours(time) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(time) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(time) % 60;
        long milliseconds = TimeUnit.MILLISECONDS.toMillis(time) % 1000 / 100;

        //String hoursString;
        String minutesString;
        String secondsString;
        if(time >= 0) {
            //hoursString = "" + hours;
            minutesString = "" + minutes;
            secondsString = "" + seconds;
        } else {
            //hoursString = "" + -hours;
            minutesString = "" + -minutes;
            secondsString = "" + -seconds;
            milliseconds = -milliseconds;
        }

        /*
        if(hours < 10 && hours > 0 || hours < 0 && hours > -10) {
            hoursString = "0" + hoursString;
        }*/
        if(minutes < 10 && minutes >= 0 || minutes <= 0 && minutes > -10) {
            minutesString = "0" + minutesString;
        }
        if(seconds < 10 && seconds >= 0 || seconds <= 0 && seconds > -10) {
            secondsString = "0" + secondsString;
        }

        if(time >= 0) {
            return /*hoursString + ":" + */minutesString + ":" + secondsString + ":" + milliseconds;
        } else {
            return "-" + /*hoursString + ":" + */minutesString + ":" + secondsString + ":" + milliseconds;
        }
    }

    private boolean checkIPField() {
        String ip = ipField.getText();
        int counter = 0;
        //String must only contain max 12 numbers and three points
        if (ip.matches("[0-9.]+") && ip.length() <= 15) {
            String[] array = ip.split(Pattern.quote("."));
            char[] charArray = ip.toCharArray();
            //check if four segments exist and if the first and the last char of the ip is not a point
            if (array.length == 4 && charArray[0] != '.' && charArray[charArray.length - 1] != '.') {
                for (int i = 0; i < array.length; i++) {
                    //the segment must only contain max three numbers
                    if (array[i].length() <= 3) {
                        counter += 1;
                    }
                }
            }
        }
        if(ip.equals("")) {
            return true;
        } else if (counter != 4) {
            infoArea.setText("You entered an invalid IP");
            return false;
        }else {
            serverIP = ip;
            return true;
        }
    }

    private boolean checkPortField() {
        String port = portField.getText();
        if(port.matches("[0-9]+")) {
            serverPort = Integer.parseInt(port);
            portField.setText("" + serverPort);
            return true;
        } else if(port.equals("")) {
            return true;
        } else {
            infoArea.setText("You entered an invalid Port");
            return false;
        }
    }

    private boolean checkSynchronizedField() {
        String time = synchronizeField.getText();
        if(time.matches("[0-9]+") && !time.equals("") && Integer.parseInt(time) >= 20) {
            //player is using milliseconds
            synchronizeTime = Integer.parseInt(time) * 1000;
            return true;
        } else {
            infoArea.setText("You have to enter a number greater than 20. The number represents the synchronizing time in seconds.");
            return false;
        }
    }

    public static void setPlayerPlaying (byte indicator){
        if (indicator == 1){
            player.play();
        }else if(indicator == 0){
            player.pause();
        }
    }

    public static void synchronize() {
        infoArea.setText("Synchronization started!");
        sliderWasDisabled = true;
        setSynchronizeActive(true);
        //send MusicTime to server
        udpClient.sendSNTPMessage();
    }
}