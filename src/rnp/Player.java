package rnp;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Einfacher Player zum Abspielen von Audio-Dateien
 *
 * Wurde zunächst mit javax.sound.sampled.clip gelöst, hat sich allerdings als ziemliche Krücke erwiesen, darum nun
 * javafx.scene.media.MediaPlayer, was mit Wave und MP3s umgehen kann und auch sonst flüssig läuft.
 *
 * Millisekundenwerte werden alle als double ausgegeben, um kompatibel zu (S)NTP zu sein
 */
public class Player {
    private MediaPlayer mp;
    private static String hash = "";
    private static LinkedHashMap<String, String[]> musicFilesInDir = new LinkedHashMap<>();
    public Boolean abortSearchDir = false;
    private static int mapEntry = 0;

    public void chooseSong(String hashOfSong, File file) {
        if (mp != null) {
            mp.stop();
            mp = null;
            Main.setPlayingActive(false);
        }
        if(hashOfSong != null) {
            mp = new MediaPlayer(new Media(musicFilesInDir.get(hashOfSong)[0]));
            Main.setActualSong(musicFilesInDir.get(hashOfSong)[1]);
            hash = hashOfSong;
        } else {
            try {
                hash = Main.getMD5Checksum(file.getPath(), false);
            } catch (Exception e) {
                System.out.println("Error in getMD5: " + e.toString());
            }
            mp = new MediaPlayer(new Media(file.toURI().toString()));
            Main.setActualSong(file.getName());
        }
        Main.setSliderToZero();
        Main.lastTimeSynced = 0;
        mp.currentTimeProperty().addListener(new InvalidationListener() {
            public void invalidated(Observable ov) {
                Main.updateSlider();
                //autoSynchronization on client side
                if(!Main.isServer && Main.autoSynchronizingActive && !Main.synchronizingActive) {
                    if((getPosition() - Main.lastTimeSynced) > Main.synchronizeTime) {
                        Main.synchronizingActive = true;
                        Main.synchronize();
                    } else if(getPosition() < 20000) {
                        Main.synchronizingActive = true;
                        Main.synchronize();
                    }
                }
            }
        });
        mp.setOnEndOfMedia(new Runnable() {
            public void run() {
                next();
            }
        });
    }

    //Spielt den Song ab, chooseSong muss vorher aufgerufen werden
    public void play() {
        if (mp != null) {
            mp.play();
            Main.setPlayingActive(true);
            Main.setInfoText("Song " + Main.getActualSong() + " is playing");
        } else {
            Main.setInfoText("You need to choose a song first!");
        }
    }

    public void pause() {
        if (mp != null) {
            mp.pause();
            Main.setPlayingActive(false);
            Main.lastTimeSynced = getPosition();
            Main.playTime.setText(Main.convertTimeStamp(getPosition()));
        }
    }

    //Springt an eine Stelle des Songs, Angabe der Stelle in Mikrosekunden
    public void jumpToPosition(double milliseconds) {
        if (mp != null) {
            Duration d = Duration.millis(milliseconds);
            mp.seek(d);
        }
    }

    /**
     * Springt an eine Stelle des Songs, Angabe der Stelle in Prozent
     * @param percent Prozent, angegeben von 0-100
     */
    public void jumpToPercentualPosition(int percent) {
        if (percent > 100 || percent < 0) {
            System.out.println("Wähle natürliche Zahlen zwischen 0 und 100!");
            return;
        }
        if (mp != null) {
            double position = (percent * getSongLength() / 100);
            jumpToPosition(position);
        }
    }

    //return trackPosition
    public double getPosition() {
        if (mp != null) {
            return mp.getCurrentTime().toMillis();
        } else {
            return 0;
        }
    }

    public double getPercentalPosition() {
        if (mp != null) {
            double position = mp.getCurrentTime().toMillis() / (mp.getCycleDuration().toMillis());
            return (position * 100);
        } else {
            return 0;
        }
    }

    public static String getHashToActualSong(){
        return hash;
    }

    public double getSongLength() {
        return mp.getCycleDuration().toMillis();
    }

    public Duration getDuration() {
        return mp.getMedia().getDuration();
    }

    public void searchDir(File folder) {
        try {
            List<File> filesInFolder = Files.walk(Paths.get(folder.getPath()))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
            for (File file : filesInFolder) {
                //only Windows and Mac support mp3 natively, Linux does not
                if ((Main.getMac() || Main.getWindows()) && (file.getName().contains(".mp3") || file.getName().contains(".wav"))
                        || file.getName().contains(".wav")) {
                    String[] musicInfo = new String[2];
                    musicInfo[0] = new File(file.getPath()).toURI().toString();
                    musicInfo[1] = file.getName();
                    musicFilesInDir.put(Main.getMD5Checksum(file.getPath(), false), musicInfo);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getSongNameFromHash(String hash) {
        if(musicFilesInDir.get(hash) == null) {
            Main.setInfoText("Could not find the song running on the server!");
            return "";
        } else {
            return musicFilesInDir.get(hash)[1];
        }
    }

    public static String getSongPathFromHash(String hash) {
        return musicFilesInDir.get(hash)[0];
    }

    //[0] contains hash, [1] contains path, [2] contains title
    public static String[] getASongOfMap() {
        String[] musicInfo = new String[3];
        musicInfo[0] = (String) musicFilesInDir.keySet().toArray()[mapEntry];
        if(mapEntry < (musicFilesInDir.size() - 1)) {
            mapEntry++;
        } else {
            mapEntry = 0;
        }
        //check if next song to play is actual song and skip it if so
        if(hash.equals(musicInfo[0])) {
            musicInfo[0] = (String) musicFilesInDir.keySet().toArray()[mapEntry];
        }
        musicInfo[1] = musicFilesInDir.get(musicInfo[0])[0];
        musicInfo[2] = musicFilesInDir.get(musicInfo[0])[1];
        return musicInfo;
    }

    public static void next(){
        String[] song = getASongOfMap();
        Main.setActualSong(song[2]);
        Main.chooseSong(song[0], null);
        Main.setPlay();
    }

    public static void printMusicFilesInDir() {
        for(Map.Entry<String, String[]> entry : musicFilesInDir.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue()[0] + ": " + entry.getValue()[1]);
        }
    }

    public static void addDefaultSongs() {
        String[] feelGood = new String[0];
        String[] hell = new String[0];
        String[] satisfaction = new String[0];
        String[] thc = new String[0];
        try {
            feelGood = new String[]{Main.class.getResource("/feel_good.mp3").toURI().toString(), "feel_good.mp3"};
            hell = new String[]{Main.class.getResource("/hell.m4a").toURI().toString(), "hell.m4a"};
            satisfaction = new String[]{Main.class.getResource("/satisfaction.mp3").toURI().toString(), "satisfaction.mp3"};
            thc = new String[]{Main.class.getResource("/thc.mp3").toURI().toString(), "thc.mp3"};
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        try {
            musicFilesInDir.put(Main.getMD5Checksum("hell.m4a", true), hell);
            musicFilesInDir.put(Main.getMD5Checksum("satisfaction.mp3", true), satisfaction);
            musicFilesInDir.put(Main.getMD5Checksum("thc.mp3", true), thc);
            musicFilesInDir.put(Main.getMD5Checksum("feel_good.mp3", true), feelGood);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}