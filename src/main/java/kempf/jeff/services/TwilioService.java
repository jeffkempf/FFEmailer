package kempf.jeff.services;

import kempf.jeff.entities.FFEmail;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import java.util.Properties;

public class TwilioService {
    private Properties prop;
    public static String ACCOUNT_SID;
    public static String AUTH_TOKEN;

    public TwilioService(Properties prop){
        this.prop = prop;
        ACCOUNT_SID = prop.getProperty("twilio.test.sid");
        AUTH_TOKEN = prop.getProperty("twilio.test.auth.token");
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
    }

    public void sendText(FFEmail ffEmail){
        Message message = Message.creator(new PhoneNumber("+19196091015"),
                new PhoneNumber(prop.getProperty("+15005550006")),
                ffEmail.getContent()).create();

//        Message message = Message.creator(new PhoneNumber(prop.getProperty("twilio.number")),
//                new PhoneNumber("+19196091015"),
//                ffEmail.getContent()).create();

        System.out.println(message.getSid());
    }
}
