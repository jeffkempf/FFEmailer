package kempf.jeff.services;

import kempf.jeff.entities.EmailThreshold;
import kempf.jeff.entities.FFEmail;
import kempf.jeff.entities.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.mail.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Auto-forwarded emails have diff format than manually forwarded.
 * This class has been created to parse auto-forwarded emails (correct type)
 */
public class RefinedEmailParser {
    private Properties prop;
    private String host;
    private String emailUsername;
    private String emailPassword;
//    private FFEmail email; //shouldn't be class level.
    private String templatePath;
    private static Logger logger;
    private HashMap<String, EmailThreshold> emailLimits; //for tracking sender thresholds
    private static final ArrayList<String> part1 = new ArrayList<>();
    private static final ArrayList<String> part2 = new ArrayList<>();
    private static final ArrayList<String> opponent = new ArrayList<>();
    private TwilioService twilioService;

    public RefinedEmailParser(Properties prop) {
        this.prop = prop;
        host = prop.getProperty("mail.pop3s.host");
        emailUsername = prop.getProperty("mail.pop3s.user");
        emailPassword = prop.getProperty("mail.pop3s.password");
        templatePath = prop.getProperty("template.dir");
//        email = new FFEmail();
        emailLimits = new HashMap<>();
        logger = LogManager.getLogger(RefinedEmailParser.class.getName());
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
        part2.add("brah");

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
        opponent.add("Chad");
        opponent.add("Pewbert");
        opponent.add("Austin");
        opponent.add("Chad");
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
//            int cutoffSize = Integer.parseInt(prop.getProperty("email.max.size"));

            Message[] messages = emailFolder.getMessages();
            logger.info("messages.length in " + Thread.currentThread().getName() + ": " + messages.length);
            if(messages.length > 0) {
                for (int i = 0; i < messages.length; i++) {
                    FFEmail email = new FFEmail();
                    writeBody(messages[i], email);
                }
            }
        } catch (Exception e) {
            logger.error(e);
        }
    }

    /**
     * will probably want to remove some of these mimeType checks once confirm a design
     *
     * @param p
     * @throws Exception
     */
    public void writePart(Part p, FFEmail email) {
        try {
            //check if the content has attachment
            if (p.isMimeType("multipart/*")) {
                String[] frm = p.getHeader("From");
                System.out.println("frm size: " + frm.length);
                email.setOriginalAddress(frm[0]);
                String[] sub = p.getHeader("Subject");
                System.out.println("sub size: " + sub.length);
                email.setContentType(sub[0]);

//            Enumeration<Header> headers = p.getAllHeaders();
//            while(headers.hasMoreElements()){
//                Header header = headers.nextElement();
//                if(header.getName().equalsIgnoreCase("From")){
//                    String value = header.getValue();
//                    System.out.println("header name: " + header.getName() + ", header value: " + value);
//                    if(value.equalsIgnoreCase("<sports-fantasy-replies@sports.yahoo.com>")){
//                        email.setOriginalAddress(value);
//                        Multipart mp = (Multipart) p.getContent();
//                        int count = mp.getCount();
//                        for (int i = 0; i < count; i++) {
//                            writePart(mp.getBodyPart(i), email);
//                        }
//                    }
//                }
//            }

                Multipart mp = (Multipart) p.getContent();
                int count = mp.getCount();
                for (int i = 0; i < count; i++) {
                    writePart(mp.getBodyPart(i), email);
                }
            } else {
                Object o = p.getContent();
                if (o instanceof String) {
                    //try getting original address and forwarded date from body
                    if (o != null) {
                        JSONObject jsonObject = new JSONObject(o);
                        String tempContent = (String) o;
                        System.out.println("tempContent: " + tempContent);

                        //at this point, only ~2 headers exist. Try Return-Path check in if
//                    Enumeration<Header> headers = p.getAllHeaders();
//                    while(headers.hasMoreElements()){
//                        Header header = headers.nextElement();
//                        if(header.getName().equalsIgnoreCase("Return-Path")){
//                            String value = header.getValue();
//                            System.out.println("header name: " + header.getName() + ", header value: " + value);
//                            if(value.equalsIgnoreCase("<sports-fantasy-replies@sports.yahoo.com>")){
//                                int innerIndex = tempContent.lastIndexOf("---------- Forwarded message ---------");
//                                String trueContent = tempContent.substring(innerIndex);
//                                logger.info("true content: " + trueContent);
//
//                                //got to be a better way than this
//                                getSpecifics(trueContent);
//                            }
//                        }
//                    }

                        //this only works for manually forwarded emails. Try using Return-Path header.
                        if (tempContent.contains("From: Yahoo Sports <sports-fantasy-replies@sports.yahoo.com>")) {
//                        int innerIndex = tempContent.lastIndexOf("---------- Forwarded message ---------");
//                        String trueContent = tempContent.substring(innerIndex);
//                        logger.info("true content: " + trueContent);
//
//                        //got to be a better way than this
//                        getSpecifics(trueContent);
                        }

                        String trueContent = null;
                        if (tempContent.contains("---------- Forwarded message ---------")) {
                            int innerIndex = tempContent.lastIndexOf("---------- Forwarded message ---------");
                            trueContent = tempContent.substring(innerIndex);
                        } else {
                            trueContent = tempContent;
                        }
//                    int innerIndex = tempContent.lastIndexOf("---------- Forwarded message ---------");
//                    String trueContent = tempContent.substring(innerIndex);
                        logger.info("true content: " + trueContent);

                        //got to be a better way than this
                        getSpecifics(trueContent, email);
                    }
                }
            }
        } catch (MessagingException e) {
            logger.error("Error parsing message in writePart", e);
        } catch (IOException e) {
            logger.error("IOException in writePart", e);
        }

    }

    private void getSpecifics(String trueContent, FFEmail email) {
//        int i1 = trueContent.indexOf("From: ") + 6;
//        int i2 = trueContent.indexOf("Date: ");
//        String originallyFrom = trueContent.substring(i1, i2);
//        email.setOriginalAddress(originallyFrom);
//        i1 = trueContent.indexOf("Subject: ");
//        i2 = trueContent.indexOf("To: ");
//        String subject = trueContent.substring(i1, i2);
//        email.setContentType(subject);
//        email.setRawContent(trueContent.substring(i2)); //this is pretty close
        email.setRawContent(trueContent);
        logger.info("email object: " + email.toString());
        String greeting = generateGreeting();
        System.out.println("Email obj: " + email.toString());
        JSONObject metaData = populateMetaData(trueContent, email);
        metaData.put("GREETING", greeting);
        populateMessage(metaData, email);

        //time to send a text
//        twilioService.sendText(email);
    }


    /**
     * deleted 2nd param tstamp
     * @param message
     * @throws Exception
     */
    public void writeBody(Message message, FFEmail email) {
        writePart(message, email);

        try {
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
        } catch (MessagingException e) {
            logger.error("Error parsing message in writeBody", e);
        } catch (IOException e) {
            logger.error("IOException in writeBody", e);
        }

    }

    private String generateGreeting(){
        Random rand = new Random();
        int r1 = rand.nextInt(part1.size());
        int r2 = rand.nextInt(part2.size());
        String greeting = part1.get(r1) + " " + part2.get(r2);
        return greeting;
    }

    private JSONObject populateMetaData(String originalStr, FFEmail email){
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
            case MOCK:
                System.out.println("MOCK original str: " + originalStr);
                int start = originalStr.indexOf("Your Team");
                int end = originalStr.indexOf("Round by Round results*");
                originalStr = originalStr.substring(start, end);
                lines = originalStr.split("\\r?\\n");
                for(String line : lines){
                    line = line.trim();
                    if(!line.isEmpty()) {
                        if (Character.isDigit(line.charAt(0))) {
                            int dlmt = line.indexOf(". ");
                            metaData.put(line.substring(0, dlmt), line.substring(dlmt + 2, line.indexOf("(")).trim());
                            System.out.println(line.substring(0, dlmt) + ", " + line.substring(dlmt + 2, line.indexOf("(")).trim());
                        }
                    }
                }
        }
        return metaData;
    }

    private void populateMessage(JSONObject metaData, FFEmail email){
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
                case MOCK:
                    placeHolder = new String(Files.readAllBytes(Paths.get(templatePath + "MockResults.txt")),
                            StandardCharsets.UTF_8);
                    logger.info("placeHolder str: " + placeHolder);
                    placeHolder = placeHolder.replaceAll("GREETING", metaData.getString("GREETING"));
                    Random rand = new Random();
                    int r = rand.nextInt(15);
                    Object value = metaData.get(r+"");
                    placeHolder = placeHolder.replaceAll("PLAYER1", value+"");
                    placeHolder = placeHolder.replaceAll("ROUND1", r+"");
                    int s = rand.nextInt(15);
                    while(s == r){
                        s = rand.nextInt(15);
                    }
                    value = metaData.get(s+"");
                    placeHolder = placeHolder.replaceAll("PLAYER2", value+"");
                    placeHolder = placeHolder.replaceAll("ROUND2", s+"");
                    break;
                default:
                    logger.warn("Unsupported message type");
                    break;
            }
            Iterator<String> tags = metaData.keys();
            if(email.getContentType() != MessageType.MOCK) {
                while (tags.hasNext()) {
                    String tag = tags.next();
                    Object value = metaData.get(tag.toString()); //not all values are strings

                    if (placeHolder.contains(tag)) {
                        placeHolder = placeHolder.replaceAll(tag, value + ""); //not all metadata values exist in messagebody. check if exists first.
                    }
                }
            }
            logger.info("formatted message: " + placeHolder);
            email.setContent(placeHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
