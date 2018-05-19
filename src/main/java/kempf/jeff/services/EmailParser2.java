package kempf.jeff.services;

import kempf.jeff.entities.FFEmail;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.simplejavamail.converter.internal.mimemessage.MimeMessageParser;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Properties;

public class EmailParser2 {
    private Properties prop;
    private String host;
    private String emailUsername;
    private String emailPassword;
    private FFEmail email;
    private static Logger logger;


    public EmailParser2(Properties prop) {
        this.prop = prop;
        host = prop.getProperty("mail.pop3s.host");
        emailUsername = prop.getProperty("mail.pop3s.user");
        emailPassword = prop.getProperty("mail.pop3s.password");
        email = new FFEmail();
        logger = LogManager.getLogger(EmailParser.class.getName());
    }

    public void fetch(){
        try {
            Session emailSession = Session.getInstance(prop);
//			emailSession.setDebug(true);

            // create the POP3 store object and connect with the pop server
            Store store = emailSession.getStore("pop3s");
            store.connect(host, emailUsername, emailPassword);

            // create the folder object and open it
            Folder emailFolder = store.getFolder("INBOX");
            emailFolder.open(Folder.READ_WRITE);
            MimeMessage message = new MimeMessage(emailSession);
            MimeMessageParser parser = new MimeMessageParser(message);

            //get subject
            String subject = parser.parse().getSubject();
            email.setContentType(subject);

            //get from address
//            String fromAddress = parser.parse().getFrom();
            InternetAddress[] froms = parser.parse().getFrom().getGroup(false);
            for(InternetAddress ia : froms){
                System.out.println("from address: " + ia);
            }
            String plain = parser.parse().getPlainContent();
            email.setContent(plain);
            logger.info("what we've got: " + email.toString());


        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
