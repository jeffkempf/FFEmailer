package kempf.jeff.services;

import kempf.jeff.entities.EmailThreshold;
import kempf.jeff.entities.FFEmail;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.mail.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
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
    private String templatePath;
    private static Logger logger;
    private boolean textIsHtml = false;
    private HashMap<String, EmailThreshold> emailLimits; //for tracking sender thresholds
    private static final ArrayList<String> part1 = new ArrayList<>();
    private static final ArrayList<String> part2 = new ArrayList<>();
    private static final ArrayList<String> opponent = new ArrayList<>();
    private TwilioService twilioService;

    public EmailParser(Properties prop) {
        this.prop = prop;
        host = prop.getProperty("mail.pop3s.host");
        emailUsername = prop.getProperty("mail.pop3s.user");
        emailPassword = prop.getProperty("mail.pop3s.password");
        filePath = prop.getProperty("file.dir");
        templatePath = prop.getProperty("template.dir");
        email = new FFEmail();
        emailLimits = new HashMap<>();
        logger = LogManager.getLogger(EmailParser.class.getName());
        twilioService = new TwilioService(prop);

        //generate greetings
        part1.add("Yo");
        part1.add("Sup");
        part1.add("Hey");
        part1.add("");
        part2.add("dawg");
        part2.add("dude");
        part2.add("bro");
        part2.add("man");
        part2.add("fam");

        opponent.add("Kevin");
        opponent.add("Chad");
        opponent.add("Brad");
        opponent.add("Chris");
        opponent.add("Steve");
        opponent.add("Mike");
        opponent.add("Jeff");
        opponent.add("Bro Dawg");
        opponent.add("DeSean");
        opponent.add("LeRoy");
        opponent.add("Chazz");
        opponent.add("Will");
        opponent.add("Sammy Boy");
        opponent.add("Pubert");
        opponent.add("Austin");
        opponent.add("Andy");
        opponent.add("Alex");
        opponent.add("Jose");
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
                //try getting original address and forwarded date from body
                if(o != null){
                    JSONObject jsonObject = new JSONObject(o);
                    String tempContent = (String) o;
                    int innerIndex = tempContent.lastIndexOf("---------- Forwarded message ---------");
                    String trueContent = tempContent.substring(innerIndex);

                    //got to be a better way than this
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
        email.setRawContent(trueContent.substring(i2)); //this is pretty close
        logger.info("email object: " + email.toString());
        String greeting = generateGreeting();
        System.out.println("Email obj: " + email.toString());
        JSONObject metaData = populateMetaData(trueContent);
        metaData.put("GREETING", greeting);
        populateMessage(metaData);

        //time to send a text
        twilioService.sendText(email);
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

    private String getText(Part p) throws MessagingException, IOException {
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
        Random rand = new Random();
        int r1 = rand.nextInt(part1.size());
        int r2 = rand.nextInt(part2.size());
        String greeting = part1.get(r1) + " " + part2.get(r2);
        return greeting;
    }

    private JSONObject populateMetaData(String originalStr){
        JSONObject metaData = new JSONObject();

        //generate guy's name
        Random rand = new Random();
        int r = rand.nextInt(opponent.size());
        metaData.put("OPPONENT", opponent.get(r));

        String lines[] = originalStr.split("\\r?\\n");
        int i1;
        int i2;
        switch (email.getContentType()) {
            case RECAP:
                System.out.println("Coming soon: RECAP");
                break;
            case WAIVERSUCCESS:
            case WAIVERFAIL:
                for(String line : lines){
                    if(line.startsWith("Player Dropped:")){
                        i1 = line.indexOf(":") + 1;
                        String player = line.substring(i1);
                        metaData.put("PLAYERDROPPED", player.trim());
                        System.out.println("dropped player: " + player);
                    } else if(line.startsWith("Player Dropped :")){
                        i1 = line.indexOf(":") + 1;
                        String player = line.substring(i1);
                        metaData.put("PLAYERDROPPED", player.trim());
                        System.out.println("dropped player: " + player);
                    }
                    if(line.startsWith("Player Added:")){
                        i1 = line.indexOf(":") + 1;
                        String player = line.substring(i1);
                        metaData.put("PLAYERADDED", player.trim());
                        System.out.println("added player: " + player);
                    } else if(line.startsWith("Player Added :")){
                        i1 = line.indexOf(":") + 1;
                        String player = line.substring(i1);
                        metaData.put("PLAYERADDED", player.trim());
                        System.out.println("added player: " + player);
                    }
                }
                break;
        }
        return metaData;
    }

    private void populateMessage(JSONObject metaData){
        try {
            String placeHolder = null;
            switch (email.getContentType()) {
                case RECAP:
                    System.out.println("Coming soon: RECAP");
                    break;
                case WAIVERSUCCESS:
                    placeHolder = new String(Files.readAllBytes(Paths.get(templatePath + "WaiverSuccess.txt")),
                            StandardCharsets.UTF_8);
                    logger.info("placeHolder str: " + placeHolder);
                    break;

                case WAIVERFAIL:
                    placeHolder = new String(Files.readAllBytes(Paths.get(templatePath + "WaiverFailure.txt")),
                            StandardCharsets.UTF_8);
                    logger.info("placeHolder str: " + placeHolder);
                    break;
                default:
                    logger.warn("Unsupported message type");
                    break;
            }
            Iterator<String> tags = metaData.keys();
            while(tags.hasNext()) {
                String tag = tags.next();
                Object value = metaData.get(tag.toString()); //not all values are strings

                if(placeHolder.contains(tag)) {
                    placeHolder = placeHolder.replaceAll(tag, value+""); //not all metadata values exist in messagebody. check if exists first.
                }
            }
            logger.info("formatted message: " + placeHolder);
            email.setContent(placeHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
