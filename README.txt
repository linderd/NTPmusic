NetworkMusic - a simple music player to play music on different devices with synchronous song position

System Requirements (tested on):

- Windows 7
- macOS 10.13
- Ubuntu 16.04 LTS
- Debian 10 (MP3 Support not working)
    
- Installed Java 8 with JavaFX
    For Ubuntu:
        sudo add-apt-repository ppa:webupd8team/java              
        sudo apt-get update
        sudo apt-get install oracle-java8-installer

    Debian -> OpenJDK

- Installed mp3 Codec pack
    Ubuntu: sudo apt-get install libav-tools

    Debian: not working (thanks to non-free licenses etc.)

        
- Firewall must allow incoming UDP Port for the PC running the server (the UDP port can be specified in the application, default is 55555)   
    Workaround for Debian:
        stop and disable firewalld and the "other firewall" (we are using 'ufw' for that; available via apt repository), if you want to use a Debian-PC as server (as root):
        systemctl stop firewalld.service
        systemctl disable firewalld.service
        ufw disable
    
All Devices must be in one Local Area Network.
One Device is the Server and all other Devices are Clients, which get synchronized to the server.
Open the NetworkMusic.jar, decide if you want to be a Server or a Client.

Server:

You can enter a specific Port or maybe another IP-Address than the one of your default eth-Device, but normally the default values should be suitable.
After clicking 'Start UDP-Server' you are ready to listen to your favourite Music.
With the two buttons on the right you can choose one of our really good example songs.
With the 'Select music file button' you can choose a Song from your own hard drive. (Tested with MP3s, MP4s and .wav audio, maybe also working with other formats..)
With 'Select directory' you choose the directory, where the music is chosen from (if you have a big directory with all music, which is also on your client, choose that one).
'Play'/'Pause' and 'Next' are doing the same like on your CD-Player ;)
You can scroll through the song by moving the little ball.
The Position in your song and the song by itself can be synced by all clients in the same network.

Client:

At first you have to fill out the field with the IP-address of the server and maybe a specific port. Then you can click 'Start UDP Client'.
With the two buttons on the right you can choose one of our really good example songs.
With 'Select directory' you choose the directory, where the music is chosen from (if you have a big directory with all music, which is also on your server, choose that one).
'Play'/'Pause' and 'Next' are doing the same like on your CD-Player ;)
By clicking 'Start Synchonization' the client sends eight SNTP-packets to the server to synchronise the position of the played song. There will also be a hash sent, to check if the same song is played an if not, the right song will be chosen, if available.
If you set the checkbox under the synchronize-button, the client will synchronize automatically in time period, which you can choose by yourself.

(c) by Daniel Leimig & David Linder - RNP WS17/18