package cursevoicelib;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;

import cursevoicelib.exceptions.AuthenticationFailedException;
import cursevoicelib.restapi.RestApi;
import cursevoicelib.restapi.beans.LoginAnswerBean;
import cursevoicelib.restapi.beans.SessionAnswerBean;
import cursevoicelib.restapi.beans.SessionRequestBean;
import cursevoicelib.util.log.Log;
import cursevoicelib.util.log.SimpleFormatter;
import cursevoicelib.wsclient.Client;
import cursevoicelib.wsclient.ClientListener;
import cursevoicelib.wsclient.beans.SendMessageBean;

/***
 * API access to the curse client systems
 * 
 * @author AlexMog
 *
 */
public class CurseClient {
    public static final String USERDATA_PATH = System.getProperty("user.home") + File.separator + "curseclientlib" + File.separator;
    public static final String CLIENT_VERSION = "7.0.32";
    private final RestApi mApi = new RestApi();
    private String mUsername, mPassword, mSessionId, mMachineKey;
    private int mUserId;
    private Client mClient;
    
    private static final String MachineKeyTemplate = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx";
    
    public CurseClient(String username, String password) throws SecurityException, IOException, KeyManagementException, NoSuchAlgorithmException, URISyntaxException {
        this();
        mUsername = username;
        mPassword = password;
        mClient = new Client(new URI("wss://notifications-v1.curseapp.net/"), this);
    }
    
    /**
     * ATTETION: can be dangerous to use this object if you don't know what you are doing.
     * @return
     */
    public RestApi getRestApi() {
        return mApi;
    }
    
    private CurseClient() throws SecurityException, IOException {
        File f = new File(USERDATA_PATH + "logs");
        if (!f.exists()) f.mkdirs();
        
        ConsoleHandler ch = new ConsoleHandler();
        ch.setFormatter(new SimpleFormatter());
        FileHandler fh = new FileHandler(USERDATA_PATH + "logs" + File.separator + "%u.%g.log",
                1024 * 1024, 10, false);
        fh.setFormatter(new SimpleFormatter());

        Log.getLogger().getLogger().setUseParentHandlers(false);
        Log.getLogger().getLogger().addHandler(fh);
        Log.getLogger().getLogger().addHandler(ch);
        Log.info("CurseClient initialised.");
    }
    
    /**
     * Used to generate a new pseudo-random machine key
     * @return
     */
    public static String generateMachineKey() {
        StringBuilder ret = new StringBuilder(MachineKeyTemplate);
        for (int i = 0; i < ret.length(); ++i) {
            char c = ret.charAt(i);
            if (c != 'y' && c != 'x') continue;
            int random = (int) (Math.random() * 16);
            int val = 0;
            if (c == 'y') {
                val = random;
            } else if (c == 'x') {
                val = (random & 0x3 | 0x8);
            }
            ret.setCharAt(i, Integer.toHexString(val).toCharArray()[0]);
        }
        return ret.toString();
    }
    
    /**
     * Authenticate the client and generates a session ID, machine key and a connection token
     * @return
     * @throws IOException
     * @throws URISyntaxException
     * @throws AuthenticationFailedException
     */
    public LoginAnswerBean authenticate() throws IOException, URISyntaxException, AuthenticationFailedException {
        LoginAnswerBean bean = mApi.getLoginAccessor().authenticate(mUsername, mPassword);
        
        mMachineKey = generateMachineKey();
        SessionAnswerBean sessionBean = mApi.getSessionsAccessor().generateSession(new SessionRequestBean(mMachineKey, null, null, 7));
        mSessionId = sessionBean.SessionID;
        mUserId = sessionBean.User.UserID;
//        Log.info("Machine Key: " + mMachineKey + " - SessionID: " + mSessionId + " - UserID: " + mUserId);
        mClient.setCredentials(mMachineKey, mSessionId, mUserId);
        return bean;
    }
    
    /**
     * Connect to the WebSocket server for the notification listener
     * @throws IOException
     * @throws URISyntaxException
     * @throws AuthenticationFailedException
     */
    public void connectWS() throws IOException, URISyntaxException, AuthenticationFailedException {
        if (!mApi.getLoginAccessor().isAuthenticated()) authenticate();
        mClient.connect();
    }
    
    /**
     * Close the connection of the WebSocket
     */
    public void disconnectWS() {
        mClient.close();
    }
    
    /**
     * Reconnects the WebSocket
     * @throws IOException
     * @throws URISyntaxException
     * @throws AuthenticationFailedException
     */
    public void reconnectWS() throws IOException, URISyntaxException, AuthenticationFailedException {
        disconnectWS();
        connectWS();
    }
   
    /***
     * Sends a message to a conversation, with an attachement ID
     * @param conversationId Id of the conversation
     * @param message message to send
     * @param attachmentId Attachement ID (if no attachment, use: "00000000-0000-0000-0000-000000000000")
     */
    public void sendMessage(String conversationId, String message, String attachmentId) {
        SendMessageBean bean = new SendMessageBean();
        bean.Body.AttachmentID = attachmentId;
        bean.Body.ClientID = generateMachineKey();
        bean.Body.ConversationID = conversationId;
        bean.Body.Message = message;
        mClient.sendPacket(bean);
    }
    
    /**
     * Send message to a conversation
     * @param conversationId conversation id
     * @param message message to send
     */
    public void sendMessage(String conversationId, String message) {
        sendMessage(conversationId, message, "00000000-0000-0000-0000-000000000000");
    }
    
    /**
     * Add a listener to the client
     * @param listener
     */
    public void addListener(ClientListener listener) {
        mClient.addListener(listener);
    }
    
    /**
     * Removes a listener from the client
     * @param listener
     */
    public void removeListener(ClientListener listener) {
        mClient.removeListener(listener);
    }
}
