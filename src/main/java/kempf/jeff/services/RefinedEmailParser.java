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
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
    private String templatePath;
    private static Logger logger;
    private HashMap<String, EmailThreshold> emailLimits; //for tracking sender thresholds
    private static final ArrayList<String> part1 = new ArrayList<>();
    private static final ArrayList<String> part2 = new ArrayList<>();
    private static final ArrayList<String> opponent = new ArrayList<>();
    private TwilioService twilioService;
    private NexmoService nexmoService;
    private Calendar startCal;
    private Calendar endCal;

    /**
     * switching config from pop to imap for testing purposes. If switching back, will want to update pop3
     * settings in gmail (enable for all, keep copy of email, and disable imap)
     *
     * @param prop
     */
    public RefinedEmailParser(Properties prop) {
        this.prop = prop;
//        host = prop.getProperty("mail.pop3s.host");
        host = prop.getProperty("mail.imap.host");
        emailUsername = prop.getProperty("mail.pop3s.user");
        emailPassword = prop.getProperty("mail.pop3s.password");
        templatePath = prop.getProperty("template.dir");
        emailLimits = new HashMap<>();
        logger = LogManager.getLogger(RefinedEmailParser.class.getName());
        twilioService = new TwilioService(prop);
        nexmoService = new NexmoService(prop);

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

        //it's usually Chad
        opponent.add("Kevin");
        opponent.add("Chad");
        opponent.add("Brad");
        opponent.add("Chris");
        opponent.add("Steve");
        opponent.add("Mike");
        opponent.add("Jeff");
        opponent.add("Chad");
        opponent.add("DeSean");
        opponent.add("Chazz");
        opponent.add("Chad");
        opponent.add("Chad");
        opponent.add("Kyle");
        opponent.add("Chad");
        opponent.add("Alex");
        opponent.add("Jose");

        //want to limit sending messages to between 9am and 9 pm
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));

            Date time11 = sdf.parse("09:00:00");
            startCal = Calendar.getInstance();
            startCal.setTime(time11);

            Date time2 = sdf.parse("21:00:00");
            endCal = Calendar.getInstance();
            endCal.setTime(time2);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public void fetch() {
        try {
            /*
            want to ensure that emails are only popped and sent between 9am and 9pm
             */
            if (timeCheck()) {
                //start with fresh hashmap for each iteration
                emailLimits.clear();

                Session emailSession = Session.getInstance(prop);
//			    emailSession.setDebug(true);

                // create the POP3 store object and connect with the pop server
//               Store store = emailSession.getStore("pop3s");
                //using imap for testing to reuse different message types
                Store store = emailSession.getStore("imaps");
                store.connect(host, emailUsername, emailPassword);

                // create the folder object and open it
                Folder emailFolder = store.getFolder("INBOX");
                emailFolder.open(Folder.READ_WRITE);


                /*
                 * If iteration starts just before 9pm and we have several emails to parse, want to
                 * ensure SMSs not sent throughout the middle of the night.
                 *
                 * If above situation occurs, want to keep any unsent
                 * messages, but don't resend emails multiple times.
                 */
                Message[] messages = emailFolder.getMessages();
                logger.info("messages.length in " + Thread.currentThread().getName() + ": " + messages.length);
                if (messages.length > 0) {
                    for (int i = 0; i < messages.length; i++) {
                        if(timeCheck()) {
                            FFEmail email = new FFEmail();
                            writeBody(messages[i], email);
                            spaceOutMessages();
                        } else {
                            break; //end for loop
                        }
                    }
                }
            } else {
                //if current time isn't within window, wait an hour and try again
                Thread.sleep(3600000L);
            }
        } catch(Exception e){
            logger.error("Something went wrong. If email credentials failed, log into Gmail account and ensure less secure apps can connect. " +
                    "Could also just make this app more secure..." , e);
        }
    }

    /**
     * returns true if current time between 9am and 9pm.
     * This method must return true before we read emails from inbox
     *
     * @return
     */
    private boolean timeCheck(){
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));

        String nowStr = sdf.format(System.currentTimeMillis());
        Calendar now = Calendar.getInstance();

        logger.info("start: " + startCal.getTime().toString() + ", end: " + endCal.getTime().toString());
        logger.info("now: " + nowStr);
        try {
            Date date = new SimpleDateFormat("HH:mm:ss").parse(nowStr);
            now.setTime(date);
        } catch (ParseException e) {
           logger.error("Error performing time check", e);
        }

        if(now.after(startCal) && now.before(endCal)){
            return true;
        }
        return false;
    }

    /**
     * want to space out SMSs. Will wait 1 - 60 min in full min increments.
     */
    private void spaceOutMessages(){
        Random random = new Random();
        long wait = (random.nextInt(60) + 1);
        try {
            logger.info("waiting " + wait + " minutes before sending text.");
//            Thread.sleep(wait * 1000);
            Thread.sleep(1);
        } catch (InterruptedException e) {
            logger.error("Error sleeping app.", e);
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
//                logger.info("frm size: " + frm.length);
                email.setOriginalAddress(frm[0]);
                String[] sub = p.getHeader("Subject");
//                logger.info("sub size: " + sub.length);
                email.setContentType(sub[0]);

                Multipart mp = (Multipart) p.getContent();
                int count = mp.getCount();
                for (int i = 0; i < count; i++) {
                    writePart(mp.getBodyPart(i), email);
                }
            } else if(p.isMimeType("text/plain")){
                logger.info("this is plain text");
                Object o = p.getContent();
                if (o instanceof String) {
                    //try getting original address and forwarded date from body
                    if (o != null) {
                        JSONObject jsonObject = new JSONObject(o);
                        String tempContent = (String) o;
//                        logger.info("forwareded email: " + tempContent);

                        //this only works for manually forwarded emails. Try using Return-Path header.
                        if (tempContent.contains("From: Yahoo Sports <sports-fantasy-replies@sports.yahoo.com>")) {
                        int innerIndex = tempContent.lastIndexOf("---------- Forwarded message ---------");
                        String trueContent = tempContent.substring(innerIndex);
                        logger.info("true content: " + trueContent);
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

                        //got to be a better way than this
                        getSpecifics(trueContent, email);
                    }
                }
            } else if(p.isMimeType("text/html")){
                logger.info("this is html");
            } else {
                logger.info("this is something else");
                logger.info(p.getContentType());
            }
        } catch (MessagingException e) {
            logger.error("Error parsing message in writePart", e);
        } catch (IOException e) {
            logger.error("IOException in writePart", e);
        }

    }

    private void getSpecifics(String trueContent, FFEmail email) {

        //skip invalid message types
        if(email.getContentType() != MessageType.INVALID) {
            email.setRawContent(trueContent);
            logger.info("email object: " + email.toString());
            String greeting = generateGreeting();
//            logger.info("Email obj: " + email.toString());
            JSONObject metaData = populateMetaData(trueContent, email);
            metaData.put("GREETING", greeting);
            populateMessage(metaData, email);

//        nexmoService.sendSMS(email);
            //time to send a text
        twilioService.sendText(email);
        }
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
//                    logger.info("bp content: " + bp.getContent());
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

    /**
     * regex parsing is poor method of getting details. Will want to find better way after a working POC created.
     *
     * @param originalStr
     * @param email
     * @return
     */
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
                logger.info("Coming soon: RECAP");
                break;
            case WAIVERSUCCESS:
            case WAIVERFAIL:
                for(String line : lines){
                    if(line.startsWith("Player Dropped:")){
                        //sometimes player added is on same line as player dropped
                        if(line.contains("Player Added")){
                            int addStart = line.lastIndexOf(":") + 1;
                            int dropEnd = line.indexOf("Player Added");
                            i1 = line.indexOf(":") + 1;
                            String playerDrop = line.substring(i1, dropEnd);
                            metaData.put("PLAYERDROPPED", playerDrop.trim());
                            String playerAdd = line.substring(addStart);
                            metaData.put("PLAYERADDED", playerAdd.trim());
                            logger.info("dropped player: " + playerDrop);
                            logger.info("added player: " + playerAdd);
                        } else {
                            i1 = line.indexOf(":") + 1;
                            String player = line.substring(i1);
                            metaData.put("PLAYERDROPPED", player.trim());
                            logger.info("dropped player: " + player);
                        }
                    } else if(line.startsWith("Player Dropped :")){
                        if(line.contains("Player Added")){
                            int addStart = line.lastIndexOf(":") + 1;
                            int dropEnd = line.indexOf("Player Added");
                            i1 = line.indexOf(":") + 1;
                            String playerDrop = line.substring(i1, dropEnd);
                            metaData.put("PLAYERDROPPED", playerDrop.trim());
                            String playerAdd = line.substring(addStart);
                            metaData.put("PLAYERADDED", playerAdd.trim());
                            logger.info("dropped player: " + playerDrop);
                            logger.info("added player: " + playerAdd);
                        } else {
                            i1 = line.indexOf(":") + 1;
                            String player = line.substring(i1);
                            metaData.put("PLAYERDROPPED", player.trim());
                            logger.info("dropped player: " + player);
                        }
                    }
                    if(line.startsWith("Player Added:")){
                        i1 = line.indexOf(":") + 1;
                        String player = line.substring(i1);
                        metaData.put("PLAYERADDED", player.trim());
                        logger.info("added player: " + player);
                    } else if(line.startsWith("Player Added :")){
                        i1 = line.indexOf(":") + 1;
                        String player = line.substring(i1);
                        metaData.put("PLAYERADDED", player.trim());
                        logger.info("added player: " + player);
                    }
                }
                break;
            case MOCK:
//                logger.info("MOCK original str: " + originalStr);
                int start = originalStr.indexOf("Your Team");
                int end = originalStr.indexOf("Round by Round results*");

                //1st iteration is text, 2nd is html. skip html to avoid this issue
                originalStr = originalStr.substring(start, end); //exception coming from here on 2nd iteration of 1st email
                lines = originalStr.split("\\r?\\n");
                for(String line : lines){
                    line = line.trim();
                    if(!line.isEmpty()) {
                        if (Character.isDigit(line.charAt(0))) {
                            int dlmt = line.indexOf(". ");
                            metaData.put(line.substring(0, dlmt), line.substring(dlmt + 2, line.indexOf("(")).trim());
                            logger.info(line.substring(0, dlmt) + ", " + line.substring(dlmt + 2, line.indexOf("(")).trim());
                        }
                    }
                }
                break;
            //need to separate. Order is reversed
            case TRADEPROPOSED:

                int occurence = 0;
                //line 6 is when trade starts. Skipping over line ending with : that causes problems
                for(int i = 5; i < lines.length; i++){
                    if(lines[i].endsWith(":")){
                        //need next line
                        int j = i + 1;
                        String player = "";
                        while(!lines[j].trim().isEmpty()){
                            player = player + lines[j].trim() + " and ";
                            j++;
                        }
                        if(player.endsWith(" and ")) {
                            int e = player.lastIndexOf(" and ");
                            player = player.substring(0, e);
                        }

                        if (occurence == 0){
                            metaData.put("PLAYERGAINED", player);
                            occurence++;
                        } else if(occurence == 1){
                            metaData.put("PLAYERGAVE", player);
                            occurence++;
                        }
                    }
                }
                break;
            case TRADEREJECTED:
                occurence = 0;
                for(int i = 0; i < lines.length; i++){
                    if(lines[i].endsWith(":")){
                        //need next line
                        String player = lines[i+1].trim();
                        if (occurence == 1){
                            metaData.put("PLAYERGAINED", player);
                            occurence++;
                        } else {
                            metaData.put("PLAYERGAVE", player);
                            occurence++;
                        }
                    }
                }
                break;

            //each side can have 1+ players in the trade. Need to account for this.
            /*
            trade review emails sent to everyone, so 1st player listing won't always be the players
            you're trading, but we don't care about that. The idea that multiple people would be texting
            Jeff with claims of making the same trade only adds to entertainment factor.
             */
            case TRADEREVIEW:
                occurence = 0;
                for(int i = 0; i < lines.length; i++){
                    if(lines[i].endsWith(":")){
                        //need next line
                        int j = i + 1;
                        String player = "";
                        while(!lines[j].trim().isEmpty()){
                            player = player + lines[j].trim() + " and ";
                            j++;
                        }
                        if(player.endsWith(" and ")) {
                            int e = player.lastIndexOf(" and ");
                            player = player.substring(0, e);
                        }

                        if (occurence == 1){
                            metaData.put("PLAYERGAINED", player);
                            occurence++;
                        } else {
                            metaData.put("PLAYERGAVE", player);
                            occurence++;
                        }
                    }
                }
                break;
        }
        return metaData;
    }

    private void populateMessage(JSONObject metaData, FFEmail email){
        try {
            String placeHolder = null;
            switch (email.getContentType()) {
                case RECAP:
                    logger.info("Coming soon: RECAP");
                    break;
                case WAIVERSUCCESS:
                    placeHolder = new String(Files.readAllBytes(Paths.get(templatePath + "WaiverSuccess.txt")),
                            StandardCharsets.UTF_8);
//                    logger.info("placeHolder str: " + placeHolder);
                    break;

                case WAIVERFAIL:
                    placeHolder = new String(Files.readAllBytes(Paths.get(templatePath + "WaiverFailure.txt")),
                            StandardCharsets.UTF_8);
//                    logger.info("placeHolder str: " + placeHolder);
                    break;
                case TRADEPROPOSED:
                    placeHolder = new String(Files.readAllBytes(Paths.get(templatePath + "TradeProposed.txt")),
                            StandardCharsets.UTF_8);
//                    logger.info("placeHolder str: " + placeHolder);
                    break;
                case TRADEREJECTED:
                    placeHolder = new String(Files.readAllBytes(Paths.get(templatePath + "TradeRejected.txt")),
                            StandardCharsets.UTF_8);
//                    logger.info("placeHolder str: " + placeHolder);
                    break;
                case TRADEREVIEW: //will use trade accepted template for trade accepted and trade review emails
                    placeHolder = new String(Files.readAllBytes(Paths.get(templatePath + "TradeAccepted.txt")),
                            StandardCharsets.UTF_8);
//                    logger.info("placeHolder str: " + placeHolder);
                    break;
                case MOCK:
                    placeHolder = new String(Files.readAllBytes(Paths.get(templatePath + "MockResults.txt")),
                            StandardCharsets.UTF_8);
//                    logger.info("placeHolder str: " + placeHolder);
                    placeHolder = placeHolder.replaceAll("GREETING", metaData.getString("GREETING"));
                    Random rand = new Random();
                    int r = rand.nextInt(15) + 1;
                    Object value = metaData.get(r+"");
                    placeHolder = placeHolder.replaceAll("PLAYER1", value+"");
                    placeHolder = placeHolder.replaceAll("ROUND1", r+"");
                    int s = rand.nextInt(15) + 1;
                    while(s == r){
                        s = rand.nextInt(15) + 1;
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
            logger.error("Error populating message", e);
        }
    }


}
