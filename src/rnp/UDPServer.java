package rnp;

import java.io.IOException;
import java.net.*;

public class UDPServer {

    private int clientPort;
    private DatagramSocket serverSocket;
    private InetAddress clientIPAddress;
    private byte[] receiveData = new byte[1024];
    private byte[] sendData = new byte[1024];
    private Thread thread;
    private boolean isRunning = true;
    private double receiveTimestamp;

    public UDPServer() {
        startUDPServer();
    }

    public void startUDPServer() {
        if(serverSocket == null) {
            try {
                serverSocket = new DatagramSocket(Main.getServerPort());
            } catch (Exception e) {
                System.out.println("UDPServer creating serverSocket: " + e);
                if(e.toString().equals("java.net.BindException: Address already in use: Cannot bind")) {
                    Main.setInfoText("An error occurred while creating the UDPServerSocket, because the port " +
                            Main.getServerPort() + " is already in use. Restart the Application and select another port!");
                }
            }
        }
        thread = new Thread() {
            public void run() {
                System.out.println("UDPServer Thread started");
                while (isRunning && serverSocket != null) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    try {
                        serverSocket.receive(receivePacket);
                        receiveTimestamp = Main.getMusicTime();
                        NtpMessage ntp = new NtpMessage(receivePacket.getData());
                        System.out.println("Received from Client: " + ntp.toString());
                        clientIPAddress = receivePacket.getAddress();
                        Main.setInfoText("Received Message from Client " + clientIPAddress.getHostAddress()
                                + " at music time " + Main.convertTimeStamp(receiveTimestamp));
                        clientPort = receivePacket.getPort();
                        sendMusicTime(ntp);
                    } catch (Exception e) {
                        System.out.println("UDPServer in while Loop: " + e);
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.start();
    }

    public void sendUDP(String string) {
        sendData = string.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIPAddress, clientPort);
        try {
            serverSocket.send(sendPacket);
        } catch (Exception e) {
            System.out.println("UDPServer sendUDP: " + e);
        }
    }

    public void closeSocket() {
        System.out.println("Closing ServerSocket");
        if(serverSocket != null) {
            serverSocket.close();
            serverSocket.disconnect();
            serverSocket = null;
        }
    }

    public void closeThread() {
        if(thread != null) {
            isRunning = false;
            thread.interrupt();
            thread = null;
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    private void sendMusicTime(NtpMessage ntp) {
        ntp.mode = 4;
        ntp.originateTimestamp = ntp.transmitTimestamp;
        ntp.receiveTimestamp = receiveTimestamp;
        ntp.messageDigest = Player.getHashToActualSong();
        ntp.transmitTimestamp = Main.getMusicTime();
        ntp.leapIndicator = Main.getIsPlayingMusic();
        byte [] buf = ntp.toByteArray();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, clientIPAddress, clientPort);
        try {
            System.out.println("Sende Paket zurück an " + clientIPAddress.getHostAddress() + ", " + clientPort);
            serverSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}