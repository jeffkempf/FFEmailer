package kempf.jeff.services;

import kempf.jeff.entities.EmailThreshold;
import kempf.jeff.entities.FFEmail;
import kempf.jeff.util.DateUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.simplejavamail.converter.internal.mimemessage.MimeMessageParser;

import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Pattern;

public class EmailParser {
    private Properties prop;
    private String host;
    private String emailUsername;
    private String emailPassword;
    private FileWriter fw;
    private BufferedWriter bw;
    private FFEmail email;
    private String filePath;
    private static Logger logger;
    private HashMap<String, EmailThreshold> emailLimits; //for tracking sender thresholds
    private boolean textIsHtml = false;

    public EmailParser(Properties prop) {
        this.prop = prop;
        host = prop.getProperty("mail.pop3s.host");
        emailUsername = prop.getProperty("mail.pop3s.user");
        emailPassword = prop.getProperty("mail.pop3s.password");
        filePath = prop.getProperty("file.dir");
        email = new FFEmail();
        emailLimits = new HashMap<>();
        logger = LogManager.getLogger(EmailParser.class.getName());
    }

    public void fetch() {
        try {
            //start with fresh hashmap for each iteration
            emailLimits.clear();

            Session emailSession = Session.getInstance(prop);
//			emailSession.setDebug(true);

            // create the POP3 store object and connect with the pop server
            Store store = emailSession.getStore("pop3s");
            store.connect(host, emailUsername, emailPassword);

            // create the folder object and open it
            Folder emailFolder = store.getFolder("INBOX");
            emailFolder.open(Folder.READ_WRITE);


            /*
             * issue: ignored emails are getting put back into the messages array and getting parsed in
             * the next iteration.  Once a message initially gets ignored, it should no longer exist.
             */
            int cutoffSize = Integer.parseInt(prop.getProperty("email.max.size"));

            Message[] messages = emailFolder.getMessages();
            logger.info("messages.length in " + Thread.currentThread().getName() + ": " + messages.length);
            if(messages.length > 0) {
                for (int i = 0; i < messages.length; i++) {
                    writeBody(messages[i]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * will probably want to remove some of these mimeType checks once confirm a design
     *
     * @param p
     * @throws Exception
     */
    public void writePart(Part p) throws Exception {
//        System.out.println("Call to writeEnvelope at start of writePart: ");
//        System.out.println("getText results: " + getText(p));
        if (p instanceof Message) {
//            writeEnvelope((Message) p);
        }

        //check if the content has attachment
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++) {
                writePart(mp.getBodyPart(i));
            }
        }
        else {
            Object o = p.getContent();
            if (o instanceof String) {
//                System.out.println("o is a string");

                //try getting original address and forwarded date from body
                if(o != null){
                    logger.info("o object: " + (String) o);
                    JSONObject jsonObject = new JSONObject(o);
//                    email.setContent((String) o);
                    String tempContent = (String) o;
                    int innerIndex = tempContent.lastIndexOf("---------- Forwarded message ---------");
                    String trueContent = tempContent.substring(innerIndex);
                    logger.info("true content: " + trueContent);
                    email.setRawContent(trueContent);
//                    logger.info("write envelope after getting true content:");
//                    writeEnvelope((Message) p);
                    //got to be a better way than ths
                    getSpecifics(trueContent);

                }
            }
        }

    }

    private void getSpecifics(String trueContent) {
        int i1 = trueContent.indexOf("From: ") + 6;
        int i2 = trueContent.indexOf("Date: ");
        String originallyFrom = trueContent.substring(i1, i2);
        email.setOriginalAddress(originallyFrom);
        i1 = trueContent.indexOf("Subject: ");
        i2 = trueContent.indexOf("To: ");
        String subject = trueContent.substring(i1, i2);
        email.setContentType(subject);
        email.setContent(trueContent.substring(i2)); //this is pretty close
        logger.info("email object: " + email.toString());
        String greeting = generateGreeting();
        System.out.println("Email obj: " + email.toString());
        if(email.getContentType().equalsIgnoreCase("waiverSuccess")) {

        }
    }


    /**
     * deleted 2nd param tstamp
     * @param message
     * @throws Exception
     */
    public void writeBody(Message message) throws Exception {
        writePart(message);

        Object content = message.getContent();
        if (content instanceof Multipart) {
            Multipart mp = (Multipart) content;
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (Pattern.compile(Pattern.quote("text/html"),
                        Pattern.CASE_INSENSITIVE).matcher(bp.getContentType()).find()) {
                    // found html part
//                    logger.info("content: " + bp.getContent());
                } else {
                    logger.warn("Email doesn't contain an HTML multipart. Will disregard message.");
                }
            }
        }

    }


    public void writeEnvelope(Message m) throws Exception {
        System.out.println("This is the message envelope");
        System.out.println("---------------------------");
        Address[] a;

        // FROM
        if ((a = m.getFrom()) != null) {
            for (int j = 0; j < a.length; j++)
                System.out.println("FROM: " + a[j].toString());
        }

        // TO
        if ((a = m.getRecipients(Message.RecipientType.TO)) != null) {
            for (int j = 0; j < a.length; j++)
                System.out.println("TO: " + a[j].toString());
        }

        // SUBJECT
        if (m.getSubject() != null)
            System.out.println("SUBJECT: " + m.getSubject());

    }

    private String getText(Part p) throws
            MessagingException, IOException {
        if (p.isMimeType("text/*")) {
            String s = (String)p.getContent();
            textIsHtml = p.isMimeType("text/html");
            return s;
        }

        if (p.isMimeType("multipart/alternative")) {
            // prefer html text over plain text
            Multipart mp = (Multipart)p.getContent();
            String text = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain")) {
                    if (text == null)
                        text = getText(bp);
                    continue;
                } else if (bp.isMimeType("text/html")) {
                    String s = getText(bp);
                    if (s != null)
                        return s;
                } else {
                    return getText(bp);
                }
            }
            return text;
        } else if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart)p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getText(mp.getBodyPart(i));
                if (s != null)
                    return s;
            }
        }

        return null;
    }

    private String generateGreeting(){
        return "Yo";
    }

}
