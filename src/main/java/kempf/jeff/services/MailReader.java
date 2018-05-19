package kempf.jeff.services;

import kempf.jeff.entities.EmailThreshold;
import kempf.jeff.util.DateUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.mail.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Pattern;

public class MailReader {

    private Properties prop;
    private String host;
    private String emailUsername;
    private String emailPassword;
    private FileWriter fw;
    private BufferedWriter bw;
    private String filePath;
    private static Logger logger;
    private HashMap<String, EmailThreshold> emailLimits; //for tracking sender thresholds

    public MailReader(Properties prop) {
        this.prop = prop;
        host = prop.getProperty("mail.pop3s.host");
        emailUsername = prop.getProperty("mail.pop3s.user");
        emailPassword = prop.getProperty("mail.pop3s.password");
        filePath = prop.getProperty("file.dir");
        emailLimits = new HashMap<String, EmailThreshold>();
        logger = LogManager.getLogger(MailReader.class.getName());
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
                    Message message = messages[i];
                    int messageSize = message.getSize();

                    //message must pass size check before file creation
                    System.out.println("message size: " + message.getSize());
                    if(messageSize < cutoffSize) {
                        long tstamp = System.currentTimeMillis();
                        File file = new File(filePath + tstamp + ".txt");
                        fw = new FileWriter(file);
                        logger.info("file name: " + filePath + tstamp + ".txt");
                        bw = new BufferedWriter(fw);
                        bw.write("Contents of Itinerary Email\n");

                        //writeHeaders is first time we can get from address and tstamps
                        EmailThreshold et = writeHeaders(message);


                        /*
                         * now that we have the address and tstamps, we can determine if we should continue writing file
                         * (ie: threshold not met) or stop writing and delete file (threshold exceeded)
                         */

                        //if address has already been processed during this iteration
                        if(emailLimits.containsKey(et.getAddress())) {
                            et = emailLimits.get(et.getAddress());
                            logger.info(et.getAddress() + " count: " + et.getCount() + ", cutoff: " + et.getCutoffCount());
                            et.increment();
                            logger.info("ET obj after incrementing: " + et.toString()); //need both logging statements?

                            //if address has exceeded threshold, stop writing and delete file
                            if(et.shouldIgnore()) {
                                logger.info("ignoring " + tstamp + ".txt");
                                bw.close();
                                fw.close();
                                file.delete();

                                //need to prevent message from coming up next iteration
                                message.setFlag(Flags.Flag.DELETED, true);
                            } else {
                                writeBody(message, tstamp);
                            }

                            //if 1st time writing file for address
                        } else {
                            logger.info(et.getAddress() + " count: " + et.getCount() + ", cutoff: " + et.getCutoffCount());
                            emailLimits.put(et.getAddress(), et);
                            writeBody(message, tstamp);
                        }

                        //if message too large
                    } else {
                        message.setFlag(Flags.Flag.DELETED, true);
                    }
                } //end for loop

                // close the store and folder objects
                emailFolder.close(true);
                store.close();
                System.out.println("end of iteration");
            }

        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
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

        //check if the content has attachment
        if (p.isMimeType("multipart/*")) {
            bw.write("\nThis is a Multipart");
            Multipart mp = (Multipart) p.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++) {
                writePart(mp.getBodyPart(i));
            }
        }
        else {
            Object o = p.getContent();
            if (o instanceof String) {
                bw.write("\nThis is a string");
                bw.write("\n---------------------------");
                bw.write("\n" + (String) o);
            }
        }

    }

    public void writeBody(Message message, long tstamp) throws Exception {
        bw.write("\nBody Content\n");
        bw.write("---------------------------------\n");
        writePart(message);

        Object content = message.getContent();
        if (content instanceof Multipart) {
            Multipart mp = (Multipart) content;
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (Pattern.compile(Pattern.quote("text/html"),
                        Pattern.CASE_INSENSITIVE).matcher(bp.getContentType()).find()) {
                    // found html part
                    bw.write((String) bp.getContent());
                } else {
                    logger.info(tstamp + ".txt doesn't contain an HTML multipart. Will disregard message.");
                }
            }
        }

        bw.close();
        fw.close();

    }
    /**
     * will need to parse out from and date headers for creating EmailThreshold entities
     *
     * @param m
     * @throws Exception
     */
    public EmailThreshold writeHeaders(Message m) throws Exception {
        /*
         * this gets all headers, which is more than we probably want.
         * Once we decide which headers to record, will probably want to switch
         * to direct calls (ie: getSubject, getRecipients, etc)
         */
//        bw.write("Header Content\n");
        Enumeration headers = m.getAllHeaders();

        //get values for ET obj creation
        String address = null;
        String date = null;

        while (headers.hasMoreElements()) {
            Header h = (Header) headers.nextElement();
//            bw.write("\n" + h.getName() + ": " + h.getValue());
            if(h.getName().equals("From")) {
                address = h.getValue();
            }
            if(h.getName().equals("Date")) {
                date = h.getValue();
            }

        }
        EmailThreshold et =  new EmailThreshold(address, DateUtil.convertToMilliseconds(date), prop);
        return et;
    }
}

