package rnp;

import javafx.application.Platform;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class UDPClient {

    private DatagramSocket clientSocket;
    private InetAddress serverIpAddress;
    private byte[] sendData = new byte[1024];
    private byte[] receiveData = new byte[1024];
    private Thread thread;
    private boolean isRunning;
    private double timeOffset;
    private double roundTripDelay;
    private double destinationTimestamp;
    private Map<Double, Double> roundTripDelayMap = new HashMap<>();
    private int messageCounter;

    public UDPClient(String serverIP, Boolean isRunning) {
        this.isRunning = isRunning;
        startUDPClient(serverIP);
    }

    public void startUDPClient(String serverIP) {
        if(clientSocket == null) {
            try {
                clientSocket = new DatagramSocket();
                serverIpAddress = InetAddress.getByName(serverIP);
            } catch (Exception e) {
                Main.setInfoText("An error occurred in the UDPClient: " + e.toString());
            }
        }
        thread = new Thread() {
            public void run() {
                System.out.println("UDPClient Thread started");
                while(isRunning && clientSocket != null) {
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        clientSocket.receive(receivePacket);
                        destinationTimestamp = Main.getMusicTime();
                        NtpMessage ntpMessage = new NtpMessage(receivePacket.getData());
                        System.out.println("Received from Server: " + ntpMessage.toString());
                        /*System.out.println("O:" + ntpMessage.originateTimestamp + ", R:" + ntpMessage.receiveTimestamp +
                                ", T:" + ntpMessage.transmitTimestamp);*/
                        adjustTime(ntpMessage);
                    } catch (Exception e) {
                        System.out.println("UDPClient Thread: " + e);
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.start();
    }

    public void sendUDP(String string) {
        sendData = string.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverIpAddress, Main.getServerPort());
        try {
            clientSocket.send(sendPacket);
        } catch (Exception e) {
            System.out.println("UDPClient sendUDP: " + e);
        }
    }

    public void closeSocket() {
        System.out.println("Closing ClientSocket");
        if(clientSocket != null) {
            clientSocket.close();
            clientSocket.disconnect();
            clientSocket = null;
        }
    }

    public void closeThread() {
        if(thread != null) {
            isRunning = false;
            thread.interrupt();
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    public void setIpAddress(String ipAddress) {
        try {
            this.serverIpAddress = InetAddress.getByName(ipAddress);
        } catch (Exception e) {
            System.out.println("UDPClient setIpAddress: " + e);
        }
    }

    private void calculateTimeOffset(NtpMessage ntpMessage) {
        timeOffset = ((ntpMessage.receiveTimestamp - ntpMessage.originateTimestamp) + (ntpMessage.transmitTimestamp - destinationTimestamp)) / 2;
    }

    private void calculateRoundTripDelay(NtpMessage ntpMessage) {
        roundTripDelay = (destinationTimestamp - ntpMessage.originateTimestamp) - (ntpMessage.transmitTimestamp - ntpMessage.receiveTimestamp);
    }

    private void adjustTime(NtpMessage ntpMessage) {
        //check if actual song on client and server is the same
        if(!(Main.getActualSongHash()).equals(ntpMessage.messageDigest)) {
            //check is client has the song of the server in his library
            if (Player.getSongNameFromHash(ntpMessage.messageDigest).equals("")) {
                Main.setInfoText("You must select the same song or the directory containing the same song that is playing on the server!");
                Main.setSynchronizeActive(false);
            } else {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        String hash = ntpMessage.messageDigest;
                        String songName = Player.getSongNameFromHash(hash);
                        Main.setActualSong(songName);
                        Main.chooseSong(hash, null);
                        Main.setPlay();
                        sendSNTPMessage();
                        Main.setInfoText("Changed song to " + songName + ". Synchronising the playTime has started");
                    }
                });
            }
        } else if(ntpMessage.leapIndicator != Main.getIsPlayingMusic()) {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        Main.setPlayerPlaying(ntpMessage.leapIndicator);
                        sendSNTPMessage();
                    }
                });
        } else {
            calculateRoundTripDelay(ntpMessage);
            calculateTimeOffset(ntpMessage);
            //save roundTripDelay and timeOffset in HashMap
            roundTripDelayMap.put(roundTripDelay, timeOffset);

            messageCounter += 1;
            //get the lowest roundTripDelay because this has the most precise timeOffSet
            if (messageCounter == 8) {
                for (Map.Entry<Double, Double> entry : roundTripDelayMap.entrySet()) {
                    if (entry.getKey() < roundTripDelay) {
                        roundTripDelay = entry.getKey();
                        timeOffset = entry.getValue();
                    }
                }
                messageCounter = 0;
                roundTripDelayMap.clear();
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        Main.setMusicTime(Main.getMusicTime() + timeOffset);
                        Main.setInfoText("Time adjusted by " + Main.convertTimeStamp(timeOffset));
                        Main.setSynchronizeActive(false);
                        Main.lastTimeSynced = Main.getMusicTime();
                        Main.synchronizingActive = false;
                        if(Main.getIsPlayingMusic() == 0) {
                            Main.updateSlider();
                        }
                        Main.sliderWasDisabled = false;
                    }
                });
            } else {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        Main.setInfoText("Got Response of server at music time " + Main.convertTimeStamp(Main.getMusicTime())
                                + ", waiting for response number " + (messageCounter + 1));
                    }
                });
                //wait 500ms for sending the next SNTPMessage
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                sendSNTPMessage();
            }
        }
    }

    public void sendSNTPMessage() {
        System.out.println("musicTime:" + Main.convertTimeStamp(Main.getMusicTime()));
        byte[] buf = new NtpMessage(Main.getMusicTime(), (byte) 3).toByteArray();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, serverIpAddress, Main.getServerPort());

        try {
            clientSocket.send(packet);
        } catch (Exception e) {
            Main.setInfoText("Error sendSNTPMessage in UDPClient: " + e);
        }
    }
}