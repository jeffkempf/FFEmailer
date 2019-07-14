package kempf.jeff.services;

import kempf.jeff.entities.FFEmail;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

public class TwilioService {
    private Properties prop;
    public static String ACCOUNT_SID;
    public static String AUTH_TOKEN;
    private static Logger logger;

    public TwilioService(Properties prop){
        logger = LogManager.getLogger(TwilioService.class.getName());
        this.prop = prop;
        ACCOUNT_SID = prop.getProperty("twilio.live.sid");
        AUTH_TOKEN = prop.getProperty("twilio.live.auth.token");
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
    }

    /*
    1st number is to, 2nd is from.
    Note: if just testing happy path, can replace live creds with test creds and number with test number.
    This won't send a text, but it also won't break the code.
     */
    public void sendText(FFEmail ffEmail){

        Message message = Message.creator(
                ACCOUNT_SID,
                new PhoneNumber(prop.getProperty("jeffs.cell")),
                new PhoneNumber(prop.getProperty("twilio.number")),
                ffEmail.getContent()
        ).create();

        logger.info("text sent. message sid: " + message.getSid());
    }
}
