package kempf.jeff.services;

import com.nexmo.client.NexmoClient;
import com.nexmo.client.NexmoClientException;
import com.nexmo.client.auth.AuthMethod;
import com.nexmo.client.auth.TokenAuthMethod;
import com.nexmo.client.sms.SmsSubmissionResult;
import com.nexmo.client.sms.messages.TextMessage;
import kempf.jeff.entities.FFEmail;

import java.io.IOException;
import java.util.Properties;
import java.util.Random;

public class NexmoService {
    private Properties prop;
    private static String NEXMO_API_KEY;
    private static String NEXMO_API_SECRET;
    private NexmoClient client;

    public NexmoService(Properties prop){
        this.prop = prop;
        NEXMO_API_KEY = prop.getProperty("nexmo.api.key");
        NEXMO_API_SECRET = prop.getProperty("nexmo.api.secret");
        AuthMethod auth = new TokenAuthMethod(NEXMO_API_KEY, NEXMO_API_SECRET);
        client = new NexmoClient(auth);
    }

    public void sendSMS(FFEmail email){

        //can't do it this way. Probably need to buy numbers.
        Random random = new Random();
        String fromNum = "1";
        for(int i = 0; i < 10; i++){
            fromNum = fromNum + random.nextInt(9);
        }
        System.out.println("from Num: " + fromNum);
        try {
            SmsSubmissionResult[] responses = client.getSmsClient().submitMessage(new TextMessage(
                    "12018577657",
                    "19196091015",
                    email.getContent()));
            for (SmsSubmissionResult response : responses) {
                System.out.println(response);
            }
        } catch (NexmoClientException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
