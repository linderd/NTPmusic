package rnp;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;

/**
 * Representation of a (S)NTP-Packet, as described in RFC 2030, based on the
 * official implementation as found on ntp.org ~DL
 */
public class NtpMessage {

	public byte leapIndicator = 0; //Schaltsekunde, für uns eigentlich unwichtig; daher zweckentfremdet als einfache Anzeige, ob der Server derzeit Musik spielt (1 = play, 0 = pause)
	public byte version = 3; //(S)NTP - Versionsnummer
	public byte mode = 0; //Modus; 3 für Client, 4 für Server
	public short stratum = 0; //Stratum; in unserem Fall zu vernachlässigen, da es nur einen Server gibt
	public byte pollInterval = 0; //Zeitintervall, in dem NTP-Anfragen nacheinander kommen können; zu vernachlässigen
	public byte precision = -20; //Präzision der Uhr, als Potenz von 2, wir geben -20 an, da wir in Mikrosekunden rechnen
	public double rootDelay = 0; //Zeigt das RoundtripDelay zur Hauptuhr an; zu vernachlässigen
	public double rootDispersion = 0; //Zeigt die Abweichung zur Hauptuhr an; zu vernachlässigen
	public byte[] referenceIdentifier = {0, 0, 0, 0}; //Ein Array, in dem die Namen unserer Quelluhren stehen; zu vernachlssigen

	/**
	 * Theoretisch sind dies die Zeitstempel, die die Sekunden vom 1.Jan 1900 bis zum
     * jeweiligen angegebenen Fall angeben, in unserem Fall handelt es sich einfach um
     * die Mikrosekunden seit dem Start des Songs.
	 */
	public double referenceTimestamp = 0; //Uhrzeit seit letztem 'Stellen' der Uhr; zu vernachlässigen
	public double originateTimestamp = 0; //Uhrzeit, an der der Client, die Nachricht an den Server abschickte; wird vom Server eingetragen
	public double receiveTimestamp = 0; //Uhrzeit, zu welcher der Server die Nachricht vom Client erhält
	public double transmitTimestamp = 0;//Uhrzeit, zu welcher das Paket abgeschickt wird

	//wir verwenden den messageDigest um einen 128bit MD5 hash (entspricht 32 stelligem UTF-8 Hexcode => 32byte) des Liedes statt der Message zu übertragen
	public String messageDigest = "";

    /**
     * Konstruktor zum erstellen eines NTP-Pakets, Variablen bleiben alle auf Standard, wie oben festgelegt.
     * Die aktuelle Position des Songs muss in Mikrosekunden übergeben werden. Ebenso wie der Modus des Paketabsenders,
     * 3 für Client, 4 für Server
     */
    public NtpMessage(double Microseconds, byte mode) {
        this.transmitTimestamp = Microseconds;
		this.mode = mode;
    }

	// Mit diesem Konstruktor lässt sich ein NTP-Paket aus einem Byte-Array auslesen
	public NtpMessage(byte[] array) {
		leapIndicator = (byte) ((array[0] >> 6) & 0x3);
		version = (byte) ((array[0] >> 3) & 0x7);
		mode = (byte) (array[0] & 0x7);
		stratum = unsignedByteToShort(array[1]);
		pollInterval = array[2];
		precision = array[3];
		
		rootDelay = (array[4] * 256.0) + 
			unsignedByteToShort(array[5]) +
			(unsignedByteToShort(array[6]) / 256.0) +
			(unsignedByteToShort(array[7]) / 65536.0);
		
		rootDispersion = (unsignedByteToShort(array[8]) * 256.0) + 
			unsignedByteToShort(array[9]) +
			(unsignedByteToShort(array[10]) / 256.0) +
			(unsignedByteToShort(array[11]) / 65536.0);
		
		referenceIdentifier[0] = array[12];
		referenceIdentifier[1] = array[13];
		referenceIdentifier[2] = array[14];
		referenceIdentifier[3] = array[15];
		
		referenceTimestamp = decodeTimestamp(array, 16);
		originateTimestamp = decodeTimestamp(array, 24);
		receiveTimestamp = decodeTimestamp(array, 32);
		transmitTimestamp = decodeTimestamp(array, 40);
		messageDigest = decodeMessageDigest(array, 48);
	}

	//Mit dieser Methode lässt sich ein Paket in ein 'rohes' Byte-Array umwandeln
	public byte[] toByteArray() {
		byte[] p = new byte[48 + 32]; //+32 für das messageDigest

		p[0] = (byte) (leapIndicator << 6 | version << 3 | mode);
		p[1] = (byte) stratum;
		p[2] = (byte) pollInterval;
		p[3] = (byte) precision;

		// Java stellt nicht alle benötigten Datentypen zur Verfügung..
		int l = (int) (rootDelay * 65536.0);
		p[4] = (byte) ((l >> 24) & 0xFF);
		p[5] = (byte) ((l >> 16) & 0xFF);
		p[6] = (byte) ((l >> 8) & 0xFF);
		p[7] = (byte) (l & 0xFF);
		
		// same here..
		long ul = (long) (rootDispersion * 65536.0);
		p[8] = (byte) ((ul >> 24) & 0xFF);
		p[9] = (byte) ((ul >> 16) & 0xFF);
		p[10] = (byte) ((ul >> 8) & 0xFF);
		p[11] = (byte) (ul & 0xFF);
		
		p[12] = referenceIdentifier[0];
		p[13] = referenceIdentifier[1];
		p[14] = referenceIdentifier[2];
		p[15] = referenceIdentifier[3];
		
		encodeTimestamp(p, 16, referenceTimestamp);
		encodeTimestamp(p, 24, originateTimestamp);
		encodeTimestamp(p, 32, receiveTimestamp);
		encodeTimestamp(p, 40, transmitTimestamp);

		encodeMessageDigest(p, 48);

		return p;
	}

	public String toString() {
		String precisionStr =
			new DecimalFormat("0.#E0").format(Math.pow(2, precision));
			
		return "Leap indicator: " + leapIndicator + "\n" +
			"Version: " + version + "\n" +
			"Mode: " + mode + "\n" +
			"Stratum: " + stratum + "\n" +
			"Poll: " + pollInterval + "\n" +
			"Precision: " + precision + " (" + precisionStr + " seconds)\n" + 
			"Root delay: " + new DecimalFormat("0.00").format(rootDelay*1000) + " ms\n" +
			"Root dispersion: " + new DecimalFormat("0.00").format(rootDispersion*1000) + " ms\n" +
			"Reference identifier: " + referenceIdentifier + "\n" +
			"Reference timestamp:  " + referenceTimestamp + "\n" +
			"Originate timestamp:  " + originateTimestamp + "\n" +
			"Receive timestamp:    " + receiveTimestamp + "\n" +
			"Transmit timestamp:   " + transmitTimestamp + "\n" +
			"Hash of song: " + messageDigest;
	}

	public static short unsignedByteToShort(byte b) {
	    //Für Java sind Bytes immer signed
		if((b & 0x80)==0x80) return (short) (128 + (b & 0x7f));
		else return (short) b;
	}

	//Liest 8 Bytes aus dem Zeitstempel, beginnend ab pointer, wird in RFC 2030 gefordert
	public static double decodeTimestamp(byte[] array, int pointer) {
		double r = 0.0;
		for(int i = 0; i < 8; i++) {
			r += unsignedByteToShort(array[pointer + i]) * Math.pow(2, (3 - i) * 8);
		}
		return r;
	}

	//Schreibt eine Zeitstempel in das array, beginnend beim Pointer
	public static void encodeTimestamp(byte[] array, int pointer, double timestamp) {
		for(int i = 0; i < 8; i++) {
			double base = Math.pow(2, (3-i)*8);
			array[pointer+i] = (byte) (timestamp / base);
			timestamp = timestamp - (double) (unsignedByteToShort(array[pointer + i]) * base);
		}
		array[7] = (byte) (Math.random() * 255.0);
	}

	//konvertiert den Teil des byteArrays, das den Hash enthält, wieder zu einem UTF8 String
	public static String decodeMessageDigest(byte[] array, int pointer) {
		byte[] messageDigestArray = new byte [32];
		for(int i = 0; i < 32; i++) {
			messageDigestArray[i] = array[pointer + i];
		}
		return new String(messageDigestArray, StandardCharsets.UTF_8);
	}

	//wandelt den hash des Liedes in ein byteArray um und schreibt es in das NTP byteArray
	public static void encodeMessageDigest(byte[] array, int pointer) {
    	if(Player.getHashToActualSong() != null) {
			byte[] hash = Player.getHashToActualSong().getBytes(StandardCharsets.UTF_8);
			for (int i = 0; i < hash.length; i++) {
				array[pointer + i] = hash[i];
			}
		}
	}
}   